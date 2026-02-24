package sdram

import chisel3._
import chisel3.util._

class SdramMem extends RawModule {
  val io = IO(Flipped(new SDRAMIO))
  val clock = io.clk.asClock
  val reset = ( io.cs ).asAsyncReset
  val module = withClockAndReset(clock, reset) { Module(new Impl) }
  class Impl extends Module {
    val io = IO(new Bundle {
      val ras = Input(Bool())
      val cas = Input(Bool())
      val we = Input(Bool())
      val addr = Input(UInt(13.W))
      val ba = Input(UInt(2.W))
      val dqm = Input(UInt(2.W))
      val data_output = Input(UInt(16.W))
      val data_out_en = Input(Bool())
      val data_input = Output(UInt(16.W))
    })
  }
}
