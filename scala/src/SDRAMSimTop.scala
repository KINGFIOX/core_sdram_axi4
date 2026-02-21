package sdram

import chisel3._
import chisel3.experimental._
import freechips.rocketchip.amba.axi4._

class SDRAMSimTopInterface extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
  val sdram = new SDRAMIO
}

class SDRAMSimTop
  extends FixedIORawModule(new SDRAMSimTopInterface)
    with ImplicitClock
    with ImplicitReset {

  override protected def implicitClock: Clock = io.clock

  override protected def implicitReset: Reset = io.reset

  val ctrl = Module(new SdramAxiTop)

  ctrl.io.axi <> io.in
  io.sdram <> ctrl.io.sdram
}
