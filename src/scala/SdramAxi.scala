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

class SDRAMIO(val p: SdramParams = SdramParams()) extends Bundle {
  val clk = Output(Bool())
  val cke = Output(Bool())
  val cs = Output(Bool())
  val ras = Output(Bool())
  val cas = Output(Bool())
  val we = Output(Bool())
  val addr = Output(UInt(p.rowW.W))
  val ba = Output(UInt(p.bankW.W))
  val dqm = Output(UInt(p.dqmW.W))
}

class SdramAxiTop(
  sdramParams: SdramParams = SdramParams(),
  axiParams: AXI4BundleParameters = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)
) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AXI4Bundle(axiParams))
    val sdram = new SDRAMIO(sdramParams)
  })
  val pmem = Module(new SdramAxiPmem(axiParams))
  val core = Module(new SdramCore(sdramParams))

  pmem.io.axi <> io.axi

  core.io.inportWr := pmem.io.ram.wstrb
  core.io.inportRd := pmem.io.ram.rd
  core.io.inportLen := pmem.io.ram.len
  core.io.inportAddr := pmem.io.ram.addr
  core.io.inportWriteData := pmem.io.ram.writeData
  pmem.io.ram.accept := core.io.inportAccept
  pmem.io.ram.ack := core.io.inportAck
  pmem.io.ram.error := core.io.inportError
  pmem.io.ram.readData := core.io.inportReadData

  io.sdram <> core.io.sdram
  val sdram_dq = IO(Analog(sdramParams.dataW.W))
  attach(sdram_dq, core.sdram_dq)
}

class SdramInterleaveTop(
  sdramParams: SdramParams = SdramParams(),
  axiParams: AXI4BundleParameters = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)
) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AXI4Bundle(axiParams))
    val sdram0 = new SDRAMIO(sdramParams)
    val sdram1 = new SDRAMIO(sdramParams)
  })
  val sdram_dq0 = IO(Analog(sdramParams.dataW.W))
  val sdram_dq1 = IO(Analog(sdramParams.dataW.W))

  val pmem = Module(new SdramAxiPmem(axiParams))
  val core0 = Module(new SdramCore(sdramParams))
  val core1 = Module(new SdramCore(sdramParams))

  pmem.io.axi <> io.axi

  // --- Interleave routing ---
  // addr(2) indicate the word-address
  val chipSel = pmem.io.ram.addr(2)
  // sdram is byte-addressable
  val coreAddr = Cat(
    0.U((32 - sdramParams.addrW - 1).W),
    pmem.io.ram.addr(sdramParams.addrW + 1, 3),
    pmem.io.ram.addr(1, 0)
  )

  // Request routing: gate rd/wstrb so only the target core sees a request
  core0.io.inportAddr := coreAddr
  core0.io.inportRd := pmem.io.ram.rd && !chipSel
  core0.io.inportWr := Mux(!chipSel, pmem.io.ram.wstrb, 0.U)
  core0.io.inportWriteData := pmem.io.ram.writeData
  core0.io.inportLen := pmem.io.ram.len

  core1.io.inportAddr := coreAddr
  core1.io.inportRd := pmem.io.ram.rd && chipSel
  core1.io.inportWr := Mux(chipSel, pmem.io.ram.wstrb, 0.U)
  core1.io.inportWriteData := pmem.io.ram.writeData
  core1.io.inportLen := pmem.io.ram.len

  // Accept from the targeted core
  pmem.io.ram.accept := Mux(chipSel, core1.io.inportAccept, core0.io.inportAccept)

  // Pending FIFO: track which core each accepted request went to
  val pendingQ = Module(new Queue(Bool(), 4))
  val reqActive = pmem.io.ram.rd || pmem.io.ram.wstrb =/= 0.U
  pendingQ.io.enq.valid := pmem.io.ram.accept && reqActive
  pendingQ.io.enq.bits := chipSel

  val ackCore = pendingQ.io.deq.bits
  val core0Ack = core0.io.inportAck
  val core1Ack = core1.io.inportAck
  val routedAck = Mux(ackCore, core1Ack, core0Ack)

  pmem.io.ram.ack := routedAck && pendingQ.io.deq.valid
  pmem.io.ram.readData := Mux(ackCore, core1.io.inportReadData, core0.io.inportReadData)
  pmem.io.ram.error := Mux(ackCore, core1.io.inportError, core0.io.inportError)
  pendingQ.io.deq.ready := routedAck && pendingQ.io.deq.valid

  // SDRAM IO
  io.sdram0 <> core0.io.sdram
  io.sdram1 <> core1.io.sdram
  attach(sdram_dq0, core0.sdram_dq)
  attach(sdram_dq1, core1.sdram_dq)
}

class AXI4SDRAM(address: Seq[AddressSet], sdramParams: SdramParams = SdramParams())(implicit p: Parameters) extends LazyModule {
  val beatBytes = 4
  val node = AXI4SlaveNode(
    Seq(
      AXI4SlavePortParameters(
        Seq(
          AXI4SlaveParameters(
            address = address,
            executable = true,
            supportsRead = TransferSizes(1, beatBytes),
            supportsWrite = TransferSizes(1, beatBytes),
            interleavedId = Some(0)
          )
        ),
        beatBytes = beatBytes
      )
    )
  )

  lazy val module = new Impl

  class Impl extends LazyModuleImp(this) {
    val (in, edge) = node.in(0)
    val sdram_bundle0 = IO(new SDRAMIO(sdramParams))
    val sdram_dq0 = IO(Analog(sdramParams.dataW.W))
    val sdram_bundle1 = IO(new SDRAMIO(sdramParams))
    val sdram_dq1 = IO(Analog(sdramParams.dataW.W))
    val ctrl = Module(new SdramInterleaveTop(sdramParams, edge.bundle))
    ctrl.io.axi <> in
    sdram_bundle0 <> ctrl.io.sdram0
    sdram_bundle1 <> ctrl.io.sdram1
    attach(sdram_dq0, ctrl.sdram_dq0)
    attach(sdram_dq1, ctrl.sdram_dq1)
  }
}
