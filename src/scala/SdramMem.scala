package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog
import freechips.rocketchip.rocket.CSR.C

class SdramMem extends RawModule {
  val io = IO(Flipped(new SDRAMIO))
  val sdram_dq = IO(Analog(16.W))
  val clock = io.clk.asClock
  val reset = ( io.cs ).asAsyncReset
  val module = withClockAndReset(clock, reset) { Module(new SdramMemImpl) }
  module.io.cke_n := io.cke
  module.io.ras_n := io.ras
  module.io.cas_n := io.cas
  module.io.we_n  := io.we
  module.io.addr  := io.addr
  module.io.ba    := io.ba
  module.io.dqm_n := io.dqm
  module.io.data_input := TriStateInBuf( sdram_dq, module.io.data_output, module.io.data_out_en )
}

class SdramMemImpl extends Module {
  private val WIDTH_BANK  = 2
  private val WIDTH_COLS  = 9
  private val WIDTH_ROWS  = 13
  private val NUM_BANKS   = 1 << WIDTH_BANK
  private val NUM_ROWS    = 1 << WIDTH_ROWS

  val io = IO(new Bundle {
    val ras_n       = Input(Bool())
    val cke_n       = Input(Bool())
    val cas_n       = Input(Bool())
    val we_n        = Input(Bool())
    val addr        = Input(UInt(WIDTH_ROWS.W))
    val ba          = Input(UInt(WIDTH_BANK.W))
    val dqm_n       = Input(UInt(2.W))
    val data_output = Output(UInt(16.W))
    val data_out_en = Output(Bool())
    val data_input  = Input(UInt(16.W))
  })

  // --- states ---
  private val activeRowQ     = RegInit(VecInit(Seq.fill(NUM_BANKS)(0.U(WIDTH_ROWS.W))))
  private val activeEnRowQ   = RegInit(VecInit(Seq.fill(NUM_BANKS)(false.B)))
  private val burstEnQ  = RegInit(false.B)
  private val burstLenQ = RegInit(0.U(3.W))

  // --- wires ---
  private val bankW = io.ba
  private val rowW  = io.addr

  object Command extends ChiselEnum {
    val nop, active, read, write, brust_terminate, precharge, refresh, load_mode = Value
  }
  val commandW = MuxCase(Command.nop, Seq(
    ( io.ras_n && io.cas_n && io.we_n ) -> Command.nop, // 111 (7)
    ( !io.ras_n && io.cas_n && io.we_n ) -> Command.active, // 011 (3)
    ( io.ras_n && !io.cas_n && io.we_n ) -> Command.read, // 101 (5)
    ( io.ras_n && !io.cas_n && !io.we_n ) -> Command.write, // 100 (4)
    ( io.ras_n && io.cas_n && !io.we_n ) -> Command.brust_terminate, // 110 (6)
    ( !io.ras_n && io.cas_n && !io.we_n ) -> Command.precharge, // 010 (2)
    ( !io.ras_n && !io.cas_n && io.we_n ) -> Command.refresh, // 001 (1)
    ( !io.ras_n && !io.cas_n && !io.we_n ) -> Command.load_mode, // 000 (0)
  ))
  switch(commandW) {
    is(Command.load_mode) {
      val burstType = io.addr(3)
      assert( burstType === 0.U, "burst type must be sequential" )
      burstEnQ  := ! io.addr(9)
      burstLenQ := io.addr(2, 0)
    }
    is(Command.refresh) {
      for (i <- 0 until NUM_BANKS) {
        assert( activeEnRowQ(i) === false.B, "no row should be active" )
      }
    }
    is(Command.active) {
      activeRowQ(bankW) := rowW
      activeEnRowQ(bankW) := true.B
    }
    is(Command.read) {

    }
  }
}
