package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

class SdramAxiCoreIO extends Bundle {
  val inportWr = Input(UInt(4.W)) // strb
  val inportRd = Input(Bool())
  val inportLen = Input(UInt(8.W))
  val inportAddr = Input(UInt(32.W))
  val inportWriteData = Input(UInt(32.W))

  val inportAccept = Output(Bool())
  val inportAck = Output(Bool())
  val inportError = Output(Bool())
  val inportReadData = Output(UInt(32.W))

  val sdram = new SDRAMIO
}

class SdramAxiCore extends Module {
  val io = IO(new SdramAxiCoreIO)

  // --- Parameters (hardcoded as in original Verilog) ---
  val SDRAM_MHZ = 50
  val SDRAM_ADDR_W = 24
  val SDRAM_COL_W = 9
  val SDRAM_READ_LATENCY = 2

  val SDRAM_BANK_W = 2
  val SDRAM_DQM_W = 2
  val SDRAM_BANKS = 1 << SDRAM_BANK_W // 4
  val SDRAM_ROW_W = SDRAM_ADDR_W - SDRAM_COL_W - SDRAM_BANK_W // 13
  val SDRAM_REFRESH_CNT = 1 << SDRAM_ROW_W // 8192
  val SDRAM_START_DELAY = 100000 / (1000 / SDRAM_MHZ) // 5000
  val SDRAM_REFRESH_CYCLES = (64000 * SDRAM_MHZ) / SDRAM_REFRESH_CNT - 1 // 389

  val CMD_W = 4
  val CMD_INHIBIT = "b1111".U(CMD_W.W)
  val CMD_NOP = "b0111".U(CMD_W.W)
  val CMD_ACTIVE = "b0011".U(CMD_W.W)
  val CMD_READ = "b0101".U(CMD_W.W)
  val CMD_WRITE = "b0100".U(CMD_W.W)
  val CMD_TERMINATE = "b0110".U(CMD_W.W)
  val CMD_PRECHARGE = "b0010".U(CMD_W.W)
  val CMD_REFRESH = "b0001".U(CMD_W.W)
  val CMD_LOAD_MODE = "b0000".U(CMD_W.W)

  // Mode: Burst Length = 2 (sequential), CAS=2
  // {3'b000, 1'b0, 2'b00, 3'b010, 1'b0, 3'b001} = 13'h0021
  val MODE_REG = "h0021".U(SDRAM_ROW_W.W)

  // States
  object State extends ChiselEnum {
    //   0      1     2       3       4        5        6       7        8         9
    val init, delay, idle, activate, read, read_wait, write0, write1, precharge, refresh = Value
  }
  State.all.foreach { s => println(s"State: ${s} = ${s.litValue}") }

  val AUTO_PRECHARGE = 10
  val ALL_BANKS = 10

  val SDRAM_DATA_W = 16
  val CYCLE_TIME_NS = 1000 / SDRAM_MHZ // 20

  val SDRAM_TRCD_CYCLES = (20 + (CYCLE_TIME_NS - 1)) / CYCLE_TIME_NS // 1
  val SDRAM_TRP_CYCLES = (20 + (CYCLE_TIME_NS - 1)) / CYCLE_TIME_NS // 1
  val SDRAM_TRFC_CYCLES = (60 + (CYCLE_TIME_NS - 1)) / CYCLE_TIME_NS // 3

  val DELAY_W = 4
  val REFRESH_CNT_W = 17

  // --- External interface aliases (RamIO) ---
  val ramAddrW = io.inportAddr
  val ramWrW = io.inportWr
  val ramRdW = io.inportRd
  val ramWriteDataW = io.inportWriteData
  val ramReqW = ramWrW =/= 0.U || ramRdW

  // --- Address bit extraction ---
  // addr_col_w = {{(ROW_W-COL_W){1'b0}}, ram_addr_w[COL_W:2], 1'b0}
  val addrColW = Cat(0.U((SDRAM_ROW_W - SDRAM_COL_W).W), ramAddrW(SDRAM_COL_W, 2), 0.U(1.W))
  // addr_row_w = ram_addr_w[SDRAM_ADDR_W : SDRAM_COL_W+2+1]
  val addrRowW = ramAddrW(SDRAM_ADDR_W, SDRAM_COL_W + 3)
  // addr_bank_w = ram_addr_w[SDRAM_COL_W+2 : SDRAM_COL_W+1]
  val addrBankW = ramAddrW(SDRAM_COL_W + 2, SDRAM_COL_W + 1)

  // --- Registers ---
  val commandQ = RegInit(CMD_INHIBIT)
  val addrQ = RegInit(0.U(SDRAM_ROW_W.W))
  val dataQ = RegInit(0.U(SDRAM_DATA_W.W))
  val dataRdEnQ = RegInit(true.B)
  val dqmQ = RegInit(0.U(SDRAM_DQM_W.W))
  val ckeQ = RegInit(false.B)
  val bankQ = RegInit(0.U(SDRAM_BANK_W.W))
  val dataBufferQ = RegInit(0.U(SDRAM_DATA_W.W))
  val dqmBufferQ = RegInit(0.U(SDRAM_DQM_W.W))
  val refreshQ = RegInit(false.B)

  val rowOpenQ = RegInit(0.U(SDRAM_BANKS.W))
  val activeRowQ = RegInit(VecInit(Seq.fill(SDRAM_BANKS)(0.U(SDRAM_ROW_W.W))))

  val stateQ = RegInit(State.init)
  val targetStateQ = RegInit(State.idle)
  val delayStateQ = RegInit(State.idle)

  val delayQ = RegInit(0.U(DELAY_W.W))

  // --- tri-state ---
  val sdram_dq = IO(Analog(16.W))
  val data_input = TriStateInBuf(sdram_dq, dataQ, !dataRdEnQ)

  // --- Refresh counter ---
  val refreshTimerQ = RegInit((SDRAM_START_DELAY + 100).U(REFRESH_CNT_W.W))

  when(refreshTimerQ === 0.U) {
    refreshTimerQ := SDRAM_REFRESH_CYCLES.U
  }.otherwise {
    refreshTimerQ := refreshTimerQ - 1.U
  }

  when(refreshTimerQ === 0.U) {
    refreshQ := true.B
  }.elsewhen(stateQ === State.refresh) {
    refreshQ := false.B
  }

  // base(pos) = value
  def withBit(base: UInt, pos: Int, value: Bool): UInt = {
    val w = base.getWidth
    val bits = Wire(Vec(w, Bool()))
    for (i <- 0 until w) {
      if (i == pos) bits(i) := value
      else bits(i) := base(i)
    }
    bits.asUInt
  }

  def gotoDelay(dest: State.Type, cycles: Int): Unit = {
    stateQ := State.delay
    delayStateQ := dest
    delayQ := cycles.U
  }

  // --- State Machine ---
  switch(stateQ) {
    is(State.init) {
      when(refreshQ) { stateQ := State.idle }

      when(refreshTimerQ === 50.U) {
        ckeQ := true.B
      }.elsewhen(refreshTimerQ === 40.U) {
        commandQ := CMD_PRECHARGE
        addrQ := withBit(0.U(SDRAM_ROW_W.W), ALL_BANKS, true.B)
      }.elsewhen(refreshTimerQ === 20.U || refreshTimerQ === 30.U) {
        commandQ := CMD_REFRESH
      }.elsewhen(refreshTimerQ === 10.U) {
        commandQ := CMD_LOAD_MODE
        addrQ := MODE_REG
      }.otherwise {
        commandQ := CMD_NOP
        addrQ := 0.U
        bankQ := 0.U
      }
    }

    is(State.idle) {
      commandQ := CMD_NOP
      addrQ := 0.U
      bankQ := 0.U
      dataRdEnQ := true.B

      when(refreshQ) {
        when(rowOpenQ.orR) { stateQ := State.precharge }
          .otherwise        { stateQ := State.refresh }
        targetStateQ := State.refresh
      }.elsewhen(ramReqW) {
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          stateQ := Mux(ramRdW, State.read, State.write0)
        }.elsewhen(rowOpenQ(addrBankW)) {
          stateQ := State.precharge
          targetStateQ := Mux(ramRdW, State.read, State.write0)
        }.otherwise {
          stateQ := State.activate
          targetStateQ := Mux(ramRdW, State.read, State.write0)
        }
      }
    }

    is(State.activate) {
      gotoDelay(targetStateQ, SDRAM_TRCD_CYCLES) // read/write

      commandQ := CMD_ACTIVE
      addrQ := addrRowW
      bankQ := addrBankW
      activeRowQ(addrBankW) := addrRowW
      rowOpenQ := rowOpenQ | (1.U << addrBankW)
    }

    is(State.read) {
      stateQ := State.read_wait

      commandQ := CMD_READ
      addrQ := withBit(addrColW, AUTO_PRECHARGE, false.B)
      bankQ := addrBankW
      dqmQ := 0.U
    }

    is(State.read_wait) {
      commandQ := CMD_NOP
      addrQ := 0.U
      bankQ := 0.U
      dataRdEnQ := true.B

      gotoDelay(State.idle, SDRAM_READ_LATENCY)
      when(!refreshQ && ramReqW && ramRdW) { // 连续读 -> 流水线
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          stateQ := State.read
        }
      }
    }

    is(State.write0) {
      stateQ := State.write1

      commandQ := CMD_WRITE
      addrQ := withBit(addrColW, AUTO_PRECHARGE, false.B)
      bankQ := addrBankW
      dataQ := ramWriteDataW(15, 0)
      dqmQ := ~ramWrW(1, 0)
      dqmBufferQ := ~ramWrW(3, 2)
      dataRdEnQ := false.B
    }

    is(State.write1) {
      stateQ := State.idle
      when(!refreshQ && ramReqW && (ramWrW =/= 0.U)) {
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          stateQ := State.write0
        }
      }

      commandQ := CMD_NOP
      dataQ := dataBufferQ
      addrQ := withBit(addrQ, AUTO_PRECHARGE, false.B)
      dqmQ := dqmBufferQ
    }

    is(State.precharge) {
      when(targetStateQ === State.refresh) {
        gotoDelay(State.refresh, SDRAM_TRP_CYCLES)
        commandQ := CMD_PRECHARGE
        addrQ := withBit(0.U(SDRAM_ROW_W.W), ALL_BANKS, true.B)
        rowOpenQ := 0.U
      }.otherwise {
        gotoDelay(State.activate, SDRAM_TRP_CYCLES)
        commandQ := CMD_PRECHARGE
        addrQ := withBit(0.U(SDRAM_ROW_W.W), ALL_BANKS, false.B)
        bankQ := addrBankW
        rowOpenQ := rowOpenQ & ~(1.U << addrBankW)
      }
    }

    is(State.refresh) {
      gotoDelay(State.idle, SDRAM_TRFC_CYCLES)

      commandQ := CMD_REFRESH
      addrQ := 0.U
      bankQ := 0.U
    }

    is(State.delay) {
      commandQ := CMD_NOP
      addrQ := 0.U
      bankQ := 0.U
      dataRdEnQ := true.B

      delayQ := delayQ - 1.U
      when(delayQ === 1.U) { stateQ := delayStateQ }
    }
  }

  // --- Read data pipeline ---
  val sampleDataQ = ShiftRegister(data_input, 2, 0.U(SDRAM_DATA_W.W), true.B)
  val rdDelayed = ShiftRegister(stateQ === State.read, SDRAM_READ_LATENCY + 2, false.B, true.B)

  when(stateQ === State.write0) {
    dataBufferQ := ramWriteDataW(31, 16)
  }.elsewhen(rdDelayed) {
    dataBufferQ := sampleDataQ
  }

  val ramReadDataW = Cat(sampleDataQ, dataBufferQ)

  // --- ACK ---
  val ackQ = RegInit(false.B)
  when(stateQ === State.write1 || rdDelayed) {
    ackQ := true.B
  }.otherwise {
    ackQ := false.B
  }

  // Accept command in READ or WRITE0 states
  val ramAcceptW = stateQ === State.read || stateQ === State.write0

  // --- Output assignments ---
  io.inportAccept := ramAcceptW
  io.inportAck := ackQ
  io.inportError := false.B
  io.inportReadData := ramReadDataW

  io.sdram.clk := (~clock.asUInt)
  io.sdram.cke := ckeQ
  io.sdram.cs := commandQ(3)
  io.sdram.ras := commandQ(2)
  io.sdram.cas := commandQ(1)
  io.sdram.we := commandQ(0)
  io.sdram.dqm := dqmQ
  io.sdram.ba := bankQ
  io.sdram.addr := addrQ
}
