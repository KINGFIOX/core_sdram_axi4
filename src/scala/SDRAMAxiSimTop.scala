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

  def connectMem(ctrl: SdramAxiTop, mem: SdramMem) = {
    val idx: Int = mem.idx
    mem.io.clk := ctrl.io.sdram.clk
    mem.io.cke := ctrl.io.sdram.cke
    mem.io.ras := ctrl.io.sdram.ras
    mem.io.cas := ctrl.io.sdram.cas
    mem.io.we := ctrl.io.sdram.we
    mem.io.addr := ctrl.io.sdram.addr
    mem.io.ba := ctrl.io.sdram.ba
    mem.io.dqm(0) := ctrl.io.sdram.dqm(idx)
    mem.io.cs := ctrl.io.sdram.cs
    mem.sdram_dq <> ctrl.sdram_dq(idx)
  }

  val ctrlParams = SdramParams()

  val ctrl = Module(new SdramAxiTop(ctrlParams, numSdram = 2))
  val mem0 = Module(new SdramMem(0))
  val mem1 = Module(new SdramMem(1))

  ctrl.io.axi <> io.in
  connectMem(ctrl, mem0)
  connectMem(ctrl, mem1)
}
