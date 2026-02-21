package org.chipsalliance.sdram

import chisel3._
import chisel3.experimental._
import freechips.rocketchip.amba.axi4._

class SDRAMSimTopInterface extends Bundle {
  val clock      = Input(Clock())
  val reset      = Input(Bool())
  val in         = Flipped(new AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)))
  val sdram      = new SDRAMIO
}

class SDRAMSimTop
    extends FixedIORawModule(new SDRAMSimTopInterface)
    with ImplicitClock
    with ImplicitReset {

  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val ctrl = Module(new SdramAxiTop)

  // AW channel
  ctrl.io.axi.awvalid := io.in.aw.valid
  io.in.aw.ready      := ctrl.io.axi.awready
  ctrl.io.axi.awaddr  := io.in.aw.bits.addr
  ctrl.io.axi.awid    := io.in.aw.bits.id
  ctrl.io.axi.awlen   := io.in.aw.bits.len
  ctrl.io.axi.awburst := io.in.aw.bits.burst

  // W channel
  ctrl.io.axi.wvalid := io.in.w.valid
  io.in.w.ready      := ctrl.io.axi.wready
  ctrl.io.axi.wdata  := io.in.w.bits.data
  ctrl.io.axi.wstrb  := io.in.w.bits.strb
  ctrl.io.axi.wlast  := io.in.w.bits.last

  // B channel
  io.in.b.valid     := ctrl.io.axi.bvalid
  io.in.b.bits.resp := ctrl.io.axi.bresp
  io.in.b.bits.id   := ctrl.io.axi.bid
  ctrl.io.axi.bready := io.in.b.ready

  // AR channel
  ctrl.io.axi.arvalid := io.in.ar.valid
  io.in.ar.ready      := ctrl.io.axi.arready
  ctrl.io.axi.araddr  := io.in.ar.bits.addr
  ctrl.io.axi.arid    := io.in.ar.bits.id
  ctrl.io.axi.arlen   := io.in.ar.bits.len
  ctrl.io.axi.arburst := io.in.ar.bits.burst

  // R channel
  io.in.r.valid     := ctrl.io.axi.rvalid
  io.in.r.bits.data := ctrl.io.axi.rdata
  io.in.r.bits.resp := ctrl.io.axi.rresp
  io.in.r.bits.id   := ctrl.io.axi.rid
  io.in.r.bits.last := ctrl.io.axi.rlast
  ctrl.io.axi.rready := io.in.r.ready

  // SDRAM physical interface
  io.sdram.clk         := ctrl.io.sdram.clk
  io.sdram.cke         := ctrl.io.sdram.cke
  io.sdram.cs          := ctrl.io.sdram.cs
  io.sdram.ras         := ctrl.io.sdram.ras
  io.sdram.cas         := ctrl.io.sdram.cas
  io.sdram.we          := ctrl.io.sdram.we
  io.sdram.addr        := ctrl.io.sdram.addr
  io.sdram.ba          := ctrl.io.sdram.ba
  io.sdram.dqm         := ctrl.io.sdram.dqm
  io.sdram.data_output := ctrl.io.sdram.data_output
  io.sdram.data_out_en := ctrl.io.sdram.data_out_en
  ctrl.io.sdram.data_input := io.sdram.data_input
}
