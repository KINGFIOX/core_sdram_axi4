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

  val ctrl = Module(new SdramInterleaveTop(sdramParams))
  val mem0 = Module(new SdramMem(sdramParams))
  val mem1 = Module(new SdramMem(sdramParams))

  ctrl.io.axi <> io.in
  mem0.io <> ctrl.io.sdram0
  attach(ctrl.sdram_dq0, mem0.sdram_dq)
  mem1.io <> ctrl.io.sdram1
  attach(ctrl.sdram_dq1, mem1.sdram_dq)
}
