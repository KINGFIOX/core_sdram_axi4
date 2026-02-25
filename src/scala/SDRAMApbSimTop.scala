package sdram

import chisel3._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.amba.apb._

class SDRAMApbOnlyInterface extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val in = Flipped(new APBBundle(APBBundleParameters(addrBits = 32, dataBits = 32)))
}

class SDRAMApbSimTop extends FixedIORawModule(new SDRAMApbOnlyInterface)
    with ImplicitClock with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val sdramParams = SdramParams()

  val ctrl = Module(new SdramApbTop(sdramParams))
  val mem  = Module(new SdramMem(sdramParams))

  ctrl.io.apb <> io.in
  mem.io <> ctrl.io.sdram
  attach(ctrl.sdram_dq, mem.sdram_dq)
}
