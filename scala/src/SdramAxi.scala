package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.amba.axi4._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.diplomacy.{AddressSet, TransferSizes}
import org.chipsalliance.cde.config.Parameters

class SDRAMIO extends Bundle {
  val clk = Output(Bool())
  val cke = Output(Bool())
  val cs = Output(Bool())
  val ras = Output(Bool())
  val cas = Output(Bool())
  val we = Output(Bool())
  val addr = Output(UInt(13.W))
  val ba = Output(UInt(2.W))
  val dqm = Output(UInt(2.W))
}

class SdramAxiTop(axiParams: AXI4BundleParameters = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AXI4Bundle(axiParams))
    val sdram = new SDRAMIO
  })
  val pmem = Module(new SdramAxiPmem(axiParams))
  val core = Module(new SdramAxiCore)

  pmem.io.axi <> io.axi

  core.io.inportWr := pmem.io.ram.wr
  core.io.inportRd := pmem.io.ram.rd
  core.io.inportLen := pmem.io.ram.len
  core.io.inportAddr := pmem.io.ram.addr
  core.io.inportWriteData := pmem.io.ram.writeData
  pmem.io.ram.accept := core.io.inportAccept
  pmem.io.ram.ack := core.io.inportAck
  pmem.io.ram.error := core.io.inportError
  pmem.io.ram.readData := core.io.inportReadData

  io.sdram <> core.io.sdram
  val sdram_dq = IO(Analog(16.W))
  attach(sdram_dq, core.sdram_dq)
}

class AXI4SDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
  val node = AXI4SlaveNode(
    Seq(
      AXI4SlavePortParameters(
        Seq(
          AXI4SlaveParameters(
            address = address,
            executable = true,
            supportsRead = TransferSizes(1, 256),
            supportsWrite = TransferSizes(1, 256)
          )
        ),
        beatBytes = 4
      )
    )
  )

  lazy val module = new Impl

  class Impl extends LazyModuleImp(this) {
    val (in, edge) = node.in(0)
    val sdram_bundle = IO(new SDRAMIO)

    val sdram_dq = IO(Analog(16.W))

    val ctrl = Module(new SdramAxiTop(edge.bundle))
    ctrl.io.axi <> in
    sdram_bundle <> ctrl.io.sdram
    attach(sdram_dq, ctrl.sdram_dq)
  }
}
