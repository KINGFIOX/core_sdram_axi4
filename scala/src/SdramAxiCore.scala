package org.chipsalliance.sdram

import chisel3._
import chisel3.util._

class SdramAxiCoreIO extends Bundle {
  val inportWr        = Input(UInt(4.W))
  val inportRd        = Input(Bool())
  val inportLen       = Input(UInt(8.W))
  val inportAddr      = Input(UInt(32.W))
  val inportWriteData = Input(UInt(32.W))
  val sdramDataInput  = Input(UInt(16.W))

  val inportAccept   = Output(Bool())
  val inportAck      = Output(Bool())
  val inportError    = Output(Bool())
  val inportReadData = Output(UInt(32.W))

  val sdramClk       = Output(Bool())
  val sdramCke       = Output(Bool())
  val sdramCs        = Output(Bool())
  val sdramRas       = Output(Bool())
  val sdramCas       = Output(Bool())
  val sdramWe        = Output(Bool())
  val sdramDqm       = Output(UInt(2.W))
  val sdramAddr      = Output(UInt(13.W))
  val sdramBa        = Output(UInt(2.W))
  val sdramDataOutput = Output(UInt(16.W))
  val sdramDataOutEn  = Output(Bool())
}

class SdramAxiCore extends Module {
  val io = IO(new SdramAxiCoreIO)

  // --- Parameters (hardcoded as in original Verilog) ---
  val SDRAM_MHZ          = 50
  val SDRAM_ADDR_W       = 24
  val SDRAM_COL_W        = 9
  val SDRAM_READ_LATENCY = 2

  val SDRAM_BANK_W       = 2
  val SDRAM_DQM_W        = 2
  val SDRAM_BANKS        = 1 << SDRAM_BANK_W  // 4
  val SDRAM_ROW_W        = SDRAM_ADDR_W - SDRAM_COL_W - SDRAM_BANK_W // 13
  val SDRAM_REFRESH_CNT  = 1 << SDRAM_ROW_W  // 8192
  val SDRAM_START_DELAY  = 100000 / (1000 / SDRAM_MHZ)  // 5000
  val SDRAM_REFRESH_CYCLES = (64000 * SDRAM_MHZ) / SDRAM_REFRESH_CNT - 1  // 389

  val CMD_W         = 4
  val CMD_NOP       = "b0111".U(CMD_W.W)
  val CMD_ACTIVE    = "b0011".U(CMD_W.W)
  val CMD_READ      = "b0101".U(CMD_W.W)
  val CMD_WRITE     = "b0100".U(CMD_W.W)
  val CMD_TERMINATE = "b0110".U(CMD_W.W)
  val CMD_PRECHARGE = "b0010".U(CMD_W.W)
  val CMD_REFRESH   = "b0001".U(CMD_W.W)
  val CMD_LOAD_MODE = "b0000".U(CMD_W.W)

  // Mode: Burst Length = 2 (sequential), CAS=2
  // {3'b000, 1'b0, 2'b00, 3'b010, 1'b0, 3'b001} = 13'h0021
  val MODE_REG = "h0021".U(SDRAM_ROW_W.W)

  // States
  val STATE_INIT      = 0.U(4.W)
  val STATE_DELAY     = 1.U(4.W)
  val STATE_IDLE      = 2.U(4.W)
  val STATE_ACTIVATE  = 3.U(4.W)
  val STATE_READ      = 4.U(4.W)
  val STATE_READ_WAIT = 5.U(4.W)
  val STATE_WRITE0    = 6.U(4.W)
  val STATE_WRITE1    = 7.U(4.W)
  val STATE_PRECHARGE = 8.U(4.W)
  val STATE_REFRESH   = 9.U(4.W)

  val AUTO_PRECHARGE = 10
  val ALL_BANKS      = 10

  val SDRAM_DATA_W   = 16
  val CYCLE_TIME_NS  = 1000 / SDRAM_MHZ  // 20

  val SDRAM_TRCD_CYCLES = (20 + (CYCLE_TIME_NS - 1)) / CYCLE_TIME_NS  // 1
  val SDRAM_TRP_CYCLES  = (20 + (CYCLE_TIME_NS - 1)) / CYCLE_TIME_NS  // 1
  val SDRAM_TRFC_CYCLES = (60 + (CYCLE_TIME_NS - 1)) / CYCLE_TIME_NS  // 3

  val DELAY_W = 4
  val REFRESH_CNT_W = 17

  // --- External interface aliases ---
  val ramAddrW      = io.inportAddr
  val ramWrW        = io.inportWr
  val ramRdW        = io.inportRd
  val ramWriteDataW = io.inportWriteData
  val ramReqW       = ramWrW =/= 0.U || ramRdW

  // --- Address bit extraction ---
  // addr_col_w = {{(ROW_W-COL_W){1'b0}}, ram_addr_w[COL_W:2], 1'b0}
  val addrColW = Cat(0.U((SDRAM_ROW_W - SDRAM_COL_W).W), ramAddrW(SDRAM_COL_W, 2), 0.U(1.W))
  // addr_row_w = ram_addr_w[SDRAM_ADDR_W : SDRAM_COL_W+2+1]
  val addrRowW = ramAddrW(SDRAM_ADDR_W, SDRAM_COL_W + 3)
  // addr_bank_w = ram_addr_w[SDRAM_COL_W+2 : SDRAM_COL_W+1]
  val addrBankW = ramAddrW(SDRAM_COL_W + 2, SDRAM_COL_W + 1)

  // --- Registers ---
  val commandQ    = RegInit(CMD_NOP)
  val addrQ       = RegInit(0.U(SDRAM_ROW_W.W))
  val dataQ       = RegInit(0.U(SDRAM_DATA_W.W))
  val dataRdEnQ   = RegInit(true.B)
  val dqmQ        = RegInit(0.U(SDRAM_DQM_W.W))
  val ckeQ        = RegInit(false.B)
  val bankQ       = RegInit(0.U(SDRAM_BANK_W.W))
  val dataBufferQ = RegInit(0.U(SDRAM_DATA_W.W))
  val dqmBufferQ  = RegInit(0.U(SDRAM_DQM_W.W))
  val refreshQ    = RegInit(false.B)

  val rowOpenQ    = RegInit(0.U(SDRAM_BANKS.W))
  val activeRowQ  = RegInit(VecInit(Seq.fill(SDRAM_BANKS)(0.U(SDRAM_ROW_W.W))))

  val stateQ       = RegInit(STATE_INIT)
  val targetStateQ = RegInit(STATE_IDLE)
  val delayStateQ  = RegInit(STATE_IDLE)

  val delayQ = RegInit(0.U(DELAY_W.W))

  // --- State Machine (combinational) ---
  val nextStateR   = WireDefault(stateQ)
  val targetStateR = WireDefault(targetStateQ)

  switch(stateQ) {
    is(STATE_INIT) {
      when(refreshQ) {
        nextStateR := STATE_IDLE
      }
    }
    is(STATE_IDLE) {
      when(refreshQ) {
        when(rowOpenQ.orR) {
          nextStateR := STATE_PRECHARGE
        }.otherwise {
          nextStateR := STATE_REFRESH
        }
        targetStateR := STATE_REFRESH
      }.elsewhen(ramReqW) {
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          when(!ramRdW) {
            nextStateR := STATE_WRITE0
          }.otherwise {
            nextStateR := STATE_READ
          }
        }.elsewhen(rowOpenQ(addrBankW)) {
          nextStateR := STATE_PRECHARGE
          when(!ramRdW) {
            targetStateR := STATE_WRITE0
          }.otherwise {
            targetStateR := STATE_READ
          }
        }.otherwise {
          nextStateR := STATE_ACTIVATE
          when(!ramRdW) {
            targetStateR := STATE_WRITE0
          }.otherwise {
            targetStateR := STATE_READ
          }
        }
      }
    }
    is(STATE_ACTIVATE) {
      nextStateR := targetStateR
    }
    is(STATE_READ) {
      nextStateR := STATE_READ_WAIT
    }
    is(STATE_READ_WAIT) {
      nextStateR := STATE_IDLE
      when(!refreshQ && ramReqW && ramRdW) {
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          nextStateR := STATE_READ
        }
      }
    }
    is(STATE_WRITE0) {
      nextStateR := STATE_WRITE1
    }
    is(STATE_WRITE1) {
      nextStateR := STATE_IDLE
      when(!refreshQ && ramReqW && (ramWrW =/= 0.U)) {
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          nextStateR := STATE_WRITE0
        }
      }
    }
    is(STATE_PRECHARGE) {
      when(targetStateR === STATE_REFRESH) {
        nextStateR := STATE_REFRESH
      }.otherwise {
        nextStateR := STATE_ACTIVATE
      }
    }
    is(STATE_REFRESH) {
      nextStateR := STATE_IDLE
    }
    is(STATE_DELAY) {
      nextStateR := delayStateQ
    }
  }

  // --- Delay logic (combinational) ---
  val delayR = WireDefault(0.U(DELAY_W.W))

  switch(stateQ) {
    is(STATE_ACTIVATE) {
      delayR := SDRAM_TRCD_CYCLES.U
    }
    is(STATE_READ_WAIT) {
      delayR := SDRAM_READ_LATENCY.U
      when(!refreshQ && ramReqW && ramRdW) {
        when(rowOpenQ(addrBankW) && addrRowW === activeRowQ(addrBankW)) {
          delayR := 0.U
        }
      }
    }
    is(STATE_PRECHARGE) {
      delayR := SDRAM_TRP_CYCLES.U
    }
    is(STATE_REFRESH) {
      delayR := SDRAM_TRFC_CYCLES.U
    }
    is(STATE_DELAY) {
      delayR := delayQ - 1.U
    }
  }

  // Record target state
  targetStateQ := targetStateR

  // Record delayed state
  when(stateQ =/= STATE_DELAY && delayR =/= 0.U) {
    delayStateQ := nextStateR
  }

  // Update actual state
  when(delayR =/= 0.U) {
    stateQ := STATE_DELAY
  }.otherwise {
    stateQ := nextStateR
  }

  // Update delay flops
  delayQ := delayR

  // --- Refresh counter ---
  val refreshTimerQ = RegInit((SDRAM_START_DELAY + 100).U(REFRESH_CNT_W.W))

  when(refreshTimerQ === 0.U) {
    refreshTimerQ := SDRAM_REFRESH_CYCLES.U
  }.otherwise {
    refreshTimerQ := refreshTimerQ - 1.U
  }

  when(refreshTimerQ === 0.U) {
    refreshQ := true.B
  }.elsewhen(stateQ === STATE_REFRESH) {
    refreshQ := false.B
  }

  // --- Input sampling (two-stage pipeline) ---
  val sampleData0Q = RegInit(0.U(SDRAM_DATA_W.W))
  val sampleDataQ  = RegInit(0.U(SDRAM_DATA_W.W))
  sampleData0Q := io.sdramDataInput
  sampleDataQ  := sampleData0Q

  // --- Command Output (sequential) ---
  // Helper to produce addr with a single bit set/cleared at position
  def withBit(base: UInt, pos: Int, value: Bool): UInt = {
    val w = base.getWidth
    val bits = Wire(Vec(w, Bool()))
    for (i <- 0 until w) {
      if (i == pos) bits(i) := value
      else bits(i) := base(i)
    }
    bits.asUInt
  }

  switch(stateQ) {
    is(STATE_INIT) {
      when(refreshTimerQ === 50.U) {
        ckeQ := true.B
      }.elsewhen(refreshTimerQ === 40.U) {
        commandQ := CMD_PRECHARGE
        addrQ := withBit(0.U(SDRAM_ROW_W.W), ALL_BANKS, true.B)
      }.elsewhen(refreshTimerQ === 20.U || refreshTimerQ === 30.U) {
        commandQ := CMD_REFRESH
      }.elsewhen(refreshTimerQ === 10.U) {
        commandQ := CMD_LOAD_MODE
        addrQ    := MODE_REG
      }.otherwise {
        commandQ := CMD_NOP
        addrQ    := 0.U
        bankQ    := 0.U
      }
    }
    is(STATE_ACTIVATE) {
      commandQ := CMD_ACTIVE
      addrQ    := addrRowW
      bankQ    := addrBankW
      activeRowQ(addrBankW) := addrRowW
      rowOpenQ := rowOpenQ | (1.U << addrBankW)
    }
    is(STATE_PRECHARGE) {
      when(targetStateR === STATE_REFRESH) {
        commandQ := CMD_PRECHARGE
        addrQ    := withBit(0.U(SDRAM_ROW_W.W), ALL_BANKS, true.B)
        rowOpenQ := 0.U
      }.otherwise {
        commandQ := CMD_PRECHARGE
        addrQ    := withBit(0.U(SDRAM_ROW_W.W), ALL_BANKS, false.B)
        bankQ    := addrBankW
        rowOpenQ := rowOpenQ & ~(1.U << addrBankW)
      }
    }
    is(STATE_REFRESH) {
      commandQ := CMD_REFRESH
      addrQ    := 0.U
      bankQ    := 0.U
    }
    is(STATE_READ) {
      commandQ := CMD_READ
      addrQ    := withBit(addrColW, AUTO_PRECHARGE, false.B)
      bankQ    := addrBankW
      dqmQ     := 0.U
    }
    is(STATE_WRITE0) {
      commandQ    := CMD_WRITE
      addrQ       := withBit(addrColW, AUTO_PRECHARGE, false.B)
      bankQ       := addrBankW
      dataQ       := ramWriteDataW(15, 0)
      dqmQ        := ~ramWrW(1, 0)
      dqmBufferQ  := ~ramWrW(3, 2)
      dataRdEnQ   := false.B
    }
    is(STATE_WRITE1) {
      commandQ := CMD_NOP
      dataQ    := dataBufferQ
      addrQ    := withBit(addrQ, AUTO_PRECHARGE, false.B)
      dqmQ     := dqmBufferQ
    }
  }

  // Default for states not explicitly listed (IDLE, DELAY, etc.)
  val isDefaultState = !(stateQ === STATE_INIT || stateQ === STATE_ACTIVATE ||
    stateQ === STATE_PRECHARGE || stateQ === STATE_REFRESH ||
    stateQ === STATE_READ || stateQ === STATE_WRITE0 ||
    stateQ === STATE_WRITE1)

  when(isDefaultState) {
    commandQ    := CMD_NOP
    addrQ       := 0.U
    bankQ       := 0.U
    dataRdEnQ   := true.B
  }

  // --- Record read events ---
  val rdQ = RegInit(0.U((SDRAM_READ_LATENCY + 2).W))
  rdQ := Cat(rdQ(SDRAM_READ_LATENCY, 0), (stateQ === STATE_READ))

  // --- Data Buffer ---
  when(stateQ === STATE_WRITE0) {
    dataBufferQ := ramWriteDataW(31, 16)
  }.elsewhen(rdQ(SDRAM_READ_LATENCY + 1)) {
    dataBufferQ := sampleDataQ
  }

  val ramReadDataW = Cat(sampleDataQ, dataBufferQ)

  // --- ACK ---
  val ackQ = RegInit(false.B)
  when(stateQ === STATE_WRITE1) {
    ackQ := true.B
  }.elsewhen(rdQ(SDRAM_READ_LATENCY + 1)) {
    ackQ := true.B
  }.otherwise {
    ackQ := false.B
  }

  // Accept command in READ or WRITE0 states
  val ramAcceptW = stateQ === STATE_READ || stateQ === STATE_WRITE0

  // --- Output assignments ---
  io.inportAccept   := ramAcceptW
  io.inportAck      := ackQ
  io.inportError    := false.B
  io.inportReadData := ramReadDataW

  io.sdramClk       := (~clock.asUInt)(0)
  io.sdramDataOutEn := !dataRdEnQ
  io.sdramDataOutput := dataQ
  io.sdramCke       := ckeQ
  io.sdramCs        := commandQ(3)
  io.sdramRas       := commandQ(2)
  io.sdramCas       := commandQ(1)
  io.sdramWe        := commandQ(0)
  io.sdramDqm       := dqmQ
  io.sdramBa        := bankQ
  io.sdramAddr      := addrQ
}
