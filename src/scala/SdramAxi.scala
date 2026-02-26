package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.amba.axi4._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.diplomacy.{AddressSet, TransferSizes}
import org.chipsalliance.cde.config.Parameters

case class SdramParams(
  mhz: Int = 50,
  addrW: Int = 24,
  colW: Int = 9,
  bankW: Int = 2,
  dataW: Int = 16,
  casLatency: Int = 2,
  tRCD_ns: Int = 20,
  tRP_ns: Int = 20,
  tRFC_ns: Int = 60
) {
  val dqmW = dataW / 8
  val rowW = addrW - colW - bankW
  val banks = 1 << bankW
  val refreshCnt = 1 << rowW
  val startDelay = 100000 / (1000 / mhz)
  val refreshCycles = (64000 * mhz) / refreshCnt - 1
  val cycleTimeNs = 1000 / mhz
  val trcdCycles = (tRCD_ns + (cycleTimeNs - 1)) / cycleTimeNs
  val trpCycles = (tRP_ns + (cycleTimeNs - 1)) / cycleTimeNs
  val trfcCycles = (tRFC_ns + (cycleTimeNs - 1)) / cycleTimeNs
}

class SDRAMIO(val p: SdramParams = SdramParams(), val numSdram: Int = 1) extends Bundle {
  val clk = Output(Bool())
  val cke = Output(Bool())
  val cs = Output(Bool())
  val ras = Output(Bool())
  val cas = Output(Bool())
  val we = Output(Bool())
  val addr = Output(UInt(p.rowW.W))
  val ba = Output(UInt(p.bankW.W))
  val dqm = Output(Vec(numSdram, UInt(p.dqmW.W)))
}

class SdramAxiTop(
  sdramParams: SdramParams = SdramParams(),
  axiParams: AXI4BundleParameters = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4),
  numSdram: Int = 1
) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AXI4Bundle(axiParams))
    val sdram = new SDRAMIO(sdramParams, numSdram)
  })
  val pmem = Module(new SdramAxiPmem(axiParams))
  val core = Module(new SdramCore(sdramParams, numSdram))

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
  val sdram_dq = IO(Vec(numSdram, Analog(sdramParams.dataW.W)))
  for (i <- 0 until numSdram) {
    sdram_dq(i) <> core.sdram_dq(i)
  }
}

class AXI4SDRAM(address: Seq[AddressSet], sdramParams: SdramParams = SdramParams())(implicit p: Parameters) extends LazyModule {
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
    val sdram_bundle = IO(new SDRAMIO(sdramParams))
    val sdram_dq = IO(Analog(sdramParams.dataW.W))
    val ctrl = Module(new SdramAxiTop(sdramParams, edge.bundle))
    ctrl.io.axi <> in
    sdram_bundle <> ctrl.io.sdram
    attach(sdram_dq, ctrl.sdram_dq(0))
  }
}
