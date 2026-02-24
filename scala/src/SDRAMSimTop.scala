package sdram

import chisel3._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.amba.axi4._

class SDRAMAxi4Interface extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
  val sdram = new SDRAMIO
  val sdram_dq = Analog(16.W)
}

class SDRAMSimTop extends FixedIORawModule(new SDRAMAxi4Interface)
    with ImplicitClock with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset
  val ctrl = Module(new SdramAxiTop)
  ctrl.io.axi <> io.in
  io.sdram <> ctrl.io.sdram
  attach(io.sdram_dq, ctrl.sdram_dq)
}
