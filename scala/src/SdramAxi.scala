package org.chipsalliance.sdram

import chisel3._
import chisel3.experimental._
import freechips.rocketchip.amba.axi4._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.diplomacy.{AddressSet, TransferSizes}
import org.chipsalliance.cde.config.Parameters

class SDRAMIO extends Bundle {
  val clk         = Output(Bool())
  val cke         = Output(Bool())
  val cs          = Output(Bool())
  val ras         = Output(Bool())
  val cas         = Output(Bool())
  val we          = Output(Bool())
  val addr        = Output(UInt(13.W))
  val ba          = Output(UInt(2.W))
  val dqm         = Output(UInt(2.W))
  val data_output = Output(UInt(16.W))
  val data_out_en = Output(Bool())
  val data_input  = Input(UInt(16.W))
}

class SdramAxiTop extends Module {
  val io = IO(new Bundle {
    val axi  = new AxiSlaveIO
    val sdram = new SDRAMIO
  })

  val pmem = Module(new SdramAxiPmem)
  val core = Module(new SdramAxiCore)

  // AXI <-> PMEM
  pmem.io.axi <> io.axi

  // PMEM RAM interface <-> Core
  core.io.inportWr        := pmem.io.ram.wr
  core.io.inportRd        := pmem.io.ram.rd
  core.io.inportLen       := pmem.io.ram.len
  core.io.inportAddr      := pmem.io.ram.addr
  core.io.inportWriteData := pmem.io.ram.writeData
  pmem.io.ram.accept      := core.io.inportAccept
  pmem.io.ram.ack         := core.io.inportAck
  pmem.io.ram.error       := core.io.inportError
  pmem.io.ram.readData    := core.io.inportReadData

  // SDRAM physical interface
  io.sdram.clk         := core.io.sdramClk
  io.sdram.cke         := core.io.sdramCke
  io.sdram.cs          := core.io.sdramCs
  io.sdram.ras         := core.io.sdramRas
  io.sdram.cas         := core.io.sdramCas
  io.sdram.we          := core.io.sdramWe
  io.sdram.addr        := core.io.sdramAddr
  io.sdram.ba          := core.io.sdramBa
  io.sdram.dqm         := core.io.sdramDqm
  io.sdram.data_output := core.io.sdramDataOutput
  io.sdram.data_out_en := core.io.sdramDataOutEn
  core.io.sdramDataInput := io.sdram.data_input
}

class AXI4SDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = AXI4SlaveNode(
    Seq(
      AXI4SlavePortParameters(
        Seq(
          AXI4SlaveParameters(
            address       = address,
            executable    = true,
            supportsRead  = TransferSizes(1, 256),
            supportsWrite = TransferSizes(1, 256)
          )
        ),
        beatBytes = 4
      )
    )
  )

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val (in, _)      = node.in(0)
    val sdram_bundle = IO(new SDRAMIO)

    val ctrl = Module(new SdramAxiTop)

    // AW channel
    ctrl.io.axi.awvalid := in.aw.valid
    in.aw.ready         := ctrl.io.axi.awready
    ctrl.io.axi.awaddr  := in.aw.bits.addr
    ctrl.io.axi.awid    := in.aw.bits.id
    ctrl.io.axi.awlen   := in.aw.bits.len
    ctrl.io.axi.awburst := in.aw.bits.burst

    // W channel
    ctrl.io.axi.wvalid := in.w.valid
    in.w.ready         := ctrl.io.axi.wready
    ctrl.io.axi.wdata  := in.w.bits.data
    ctrl.io.axi.wstrb  := in.w.bits.strb
    ctrl.io.axi.wlast  := in.w.bits.last

    // B channel
    in.b.valid     := ctrl.io.axi.bvalid
    in.b.bits.resp := ctrl.io.axi.bresp
    in.b.bits.id   := ctrl.io.axi.bid
    ctrl.io.axi.bready := in.b.ready

    // AR channel
    ctrl.io.axi.arvalid := in.ar.valid
    in.ar.ready         := ctrl.io.axi.arready
    ctrl.io.axi.araddr  := in.ar.bits.addr
    ctrl.io.axi.arid    := in.ar.bits.id
    ctrl.io.axi.arlen   := in.ar.bits.len
    ctrl.io.axi.arburst := in.ar.bits.burst

    // R channel
    in.r.valid     := ctrl.io.axi.rvalid
    in.r.bits.data := ctrl.io.axi.rdata
    in.r.bits.resp := ctrl.io.axi.rresp
    in.r.bits.id   := ctrl.io.axi.rid
    in.r.bits.last := ctrl.io.axi.rlast
    ctrl.io.axi.rready := in.r.ready

    // SDRAM physical interface
    sdram_bundle.clk         := ctrl.io.sdram.clk
    sdram_bundle.cke         := ctrl.io.sdram.cke
    sdram_bundle.cs          := ctrl.io.sdram.cs
    sdram_bundle.ras         := ctrl.io.sdram.ras
    sdram_bundle.cas         := ctrl.io.sdram.cas
    sdram_bundle.we          := ctrl.io.sdram.we
    sdram_bundle.addr        := ctrl.io.sdram.addr
    sdram_bundle.ba          := ctrl.io.sdram.ba
    sdram_bundle.dqm         := ctrl.io.sdram.dqm
    sdram_bundle.data_output := ctrl.io.sdram.data_output
    sdram_bundle.data_out_en := ctrl.io.sdram.data_out_en
    ctrl.io.sdram.data_input := sdram_bundle.data_input
  }
}
