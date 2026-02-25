package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

class SdramCoreIO(val p: SdramParams) extends Bundle {
  val inportWr = Input(UInt(4.W)) // strb
  val inportRd = Input(Bool())
  val inportLen = Input(UInt(8.W)) // burst len = 0
  val inportAddr = Input(UInt(32.W))
  val inportWriteData = Input(UInt(32.W))

  val inportAccept = Output(Bool())
  val inportAck = Output(Bool())
  val inportError = Output(Bool())
  val inportReadData = Output(UInt(32.W))

  val sdram = new SDRAMIO(p)
}

class SdramCore(val p: SdramParams = SdramParams()) extends Module {
  val io = IO(new SdramCoreIO(p))

  // --- SDRAM command encodings ---
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
  val MODE_REG = "h0021".U(p.rowW.W)

  val AUTO_PRECHARGE = 10
  val ALL_BANKS = 10

  val DELAY_W = 4
  val REFRESH_CNT_W = log2Ceil(p.startDelay + 101) + 1

  // --- External interface aliases (RamIO) ---
  val ramAddrW = io.inportAddr
  val ramWrW = io.inportWr
  val ramRdW = io.inportRd
  val ramWriteDataW = io.inportWriteData
  val ramReqW = ramWrW =/= 0.U || ramRdW

  // --- Address bit extraction ---
  val addrColW = Cat(0.U((p.rowW - p.colW).W), ramAddrW(p.colW, 2), 0.U(1.W))
  val addrRowW = ramAddrW(p.addrW, p.colW + 3)
  val addrBankW = ramAddrW(p.colW + 2, p.colW + 1)

   // States
  object State extends ChiselEnum {
    //   0      1     2       3       4        5        6       7        8         9
    val init, delay, idle, activate, read, read_wait, write0, write1, precharge, refresh = Value
  }
  State.all.foreach { s => println(s"State: ${s} = ${s.litValue}") } // print during elaboration

  // --- state machine ---
  val stateQ = RegInit(State.init)
  val targetStateQ = RegInit(State.idle)
  val delayStateQ = RegInit(State.idle)
  val delayQ = RegInit(0.U(DELAY_W.W))

  // --- outputs: sdram ---
  val commandQ = RegInit(CMD_INHIBIT)
  io.sdram.cs := commandQ(3)
  io.sdram.ras := commandQ(2)
  io.sdram.cas := commandQ(1)
  io.sdram.we := commandQ(0)
  val addrQ = RegInit(0.U(p.rowW.W)); io.sdram.addr := addrQ
  val dqmQ = RegInit(0.U(p.dqmW.W)); io.sdram.dqm := dqmQ
  val ckeQ = RegInit(false.B); io.sdram.cke := ckeQ
  val bankQ = RegInit(0.U(p.bankW.W)); io.sdram.ba := bankQ
  val dataOutQ = RegInit(0.U(p.dataW.W))
  // --- tri-state ---
  val sdram_dq = IO(Analog(p.dataW.W))
  val dataInW = TriStateInBuf(sdram_dq, dataOutQ, RegNext( stateQ === State.write0 || stateQ === State.write1 ))

  // --- outputs: bus ---
  io.inportAccept := stateQ === State.read || stateQ === State.write0
  io.inportError := false.B

  // --- row open ---
  val rowOpenQ = RegInit(0.U(p.banks.W))
  val activeRowQ = RegInit(VecInit(Seq.fill(p.banks)(0.U(p.rowW.W))))

  // --- Periodic refresh (after init) ---
  val (_, refreshTick) = Counter(stateQ =/= State.init, p.refreshCycles + 1)
  val refreshQ = RegInit(false.B)
  when(refreshTick) {
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
      val initTimerQ = RegInit((p.startDelay + 100).U(REFRESH_CNT_W.W)) // init timer
      initTimerQ := initTimerQ - 1.U

      when(initTimerQ === 50.U) {
        ckeQ := true.B
      }.elsewhen(initTimerQ === 40.U) {
        commandQ := CMD_PRECHARGE
        addrQ := withBit(0.U(p.rowW.W), ALL_BANKS, true.B)
      }.elsewhen(initTimerQ === 20.U || initTimerQ === 30.U) {
        commandQ := CMD_REFRESH
      }.elsewhen(initTimerQ === 10.U) {
        commandQ := CMD_LOAD_MODE
        addrQ := MODE_REG
      }.elsewhen(initTimerQ === 0.U) {
        stateQ := State.idle
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

      when(refreshQ) { // refresh come first
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
      gotoDelay(targetStateQ, p.trcdCycles) // read/write

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

      gotoDelay(State.idle, p.casLatency) // default
      when(!refreshQ && ramReqW && ramRdW) { // burst from axi4
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          stateQ := State.read // renew state instead of delay
        }
      }
    }

    is(State.write0) {
      stateQ := State.write1

      commandQ := CMD_WRITE
      addrQ := withBit(addrColW, AUTO_PRECHARGE, false.B)
      bankQ := addrBankW
      dataOutQ := ramWriteDataW(15, 0)
      dqmQ := ~ramWrW(1, 0)
    }

    is(State.write1) {
      stateQ := State.idle
      when(!refreshQ && ramReqW && (ramWrW =/= 0.U)) {
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          stateQ := State.write0
        }
      }

      commandQ := CMD_NOP
      dataOutQ := RegNext(ramWriteDataW(31, 16))
      addrQ := withBit(addrQ, AUTO_PRECHARGE, false.B)
      dqmQ := RegNext(~ramWrW(3, 2))
    }

    is(State.precharge) {
      when(targetStateQ === State.refresh) {
        gotoDelay(State.refresh, p.trpCycles)
        commandQ := CMD_PRECHARGE
        addrQ := withBit(0.U(p.rowW.W), ALL_BANKS, true.B)
        rowOpenQ := 0.U
      }.otherwise {
        gotoDelay(State.activate, p.trpCycles)
        commandQ := CMD_PRECHARGE
        addrQ := withBit(0.U(p.rowW.W), ALL_BANKS, false.B)
        bankQ := addrBankW
        rowOpenQ := rowOpenQ & ~(1.U << addrBankW)
      }
    }

    is(State.refresh) {
      gotoDelay(State.idle, p.trfcCycles)

      commandQ := CMD_REFRESH
      addrQ := 0.U
      bankQ := 0.U
    }

    is(State.delay) {
      commandQ := CMD_NOP
      addrQ := 0.U
      bankQ := 0.U

      delayQ := delayQ - 1.U
      when(delayQ === 1.U) { stateQ := delayStateQ }
    }
  }

  // --- Read data pipeline ---
  val sampleDataQ = ShiftRegister(dataInW, 2, 0.U(p.dataW.W), true.B)
  val rdDelayed = ShiftRegister(stateQ === State.read, p.casLatency + 2, false.B, true.B)
  io.inportReadData := Cat(sampleDataQ, RegNext(sampleDataQ))
  io.inportAck := RegNext( stateQ === State.write1 || rdDelayed )

  io.sdram.clk := (~clock.asUInt)
}
