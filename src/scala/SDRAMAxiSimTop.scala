package sdram

import chisel3._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.amba.axi4._

class SDRAMAxi4OnlyInterface extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
}

class SDRAMAxiSimTop extends FixedIORawModule(new SDRAMAxi4OnlyInterface)
    with ImplicitClock with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val sdramParams = SdramParams()

  val ctrl = Module(new SdramAxiTop(sdramParams))
  val mem  = Module(new SdramMem(sdramParams))

  ctrl.io.axi <> io.in
  mem.io <> ctrl.io.sdram
  attach(ctrl.sdram_dq, mem.sdram_dq)
}
