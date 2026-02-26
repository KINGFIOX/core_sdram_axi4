package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.amba.apb._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.diplomacy.{AddressSet}
import org.chipsalliance.cde.config.Parameters
import os.read

class SdramApbTop(
  sdramParams: SdramParams = SdramParams(),
  apbParams: APBBundleParameters = APBBundleParameters(addrBits = 32, dataBits = 32),
  numSdram: Int = 1
) extends Module {
  val io = IO(new Bundle {
    val apb   = Flipped(new APBBundle(apbParams))
    val sdram = new SDRAMIO(sdramParams, numSdram)
  })

  // --- modules ----
  private val core = Module(new SdramCore(sdramParams, numSdram))
  core.io.inportLen := 0.U // always: burst len = 0
  core.io.inportWr := 0.U // default
  core.io.inportRd := false.B
  core.io.inportAddr := io.apb.paddr
  core.io.inportWriteData := io.apb.pwdata

  // --- status ----
  private val stateQ = RegInit(State.idle)
  private val rdataQ  = Reg(UInt(32.W))
  private val slverrQ = RegInit(false.B)

  // --- wires ----
  private val reqAcceptW = core.io.inportAccept // accept request
  private val reqAckW = core.io.inportAck // finish
  private val apbSetupW = io.apb.psel

  // APB standard state machine
  object State extends ChiselEnum {
    val idle, setup, access, ready = Value
  }

  switch(stateQ) {
    is(State.idle) {
      when(apbSetupW) {
        stateQ := State.setup
      }
    }
    is(State.setup) {
      when(io.apb.pwrite) {
        core.io.inportWr := io.apb.pstrb
      } .otherwise {
        core.io.inportRd := true.B
      }
      when(reqAcceptW) { stateQ := State.access }
    }
    is(State.access) {
      when(reqAckW) {
        stateQ := State.ready
        rdataQ  := core.io.inportReadData
        slverrQ := core.io.inportError
      }
    }
    is(State.ready) {
      assert(io.apb.psel, "impossible: APB psel is not asserted in ready state")
      when(io.apb.penable) {
        stateQ := State.idle
        slverrQ := false.B // reset
        rdataQ := 0.U
      }
    }
  }

  // APB slave outputs
  io.apb.pready  := stateQ === State.ready
  io.apb.pslverr := slverrQ
  io.apb.prdata  := rdataQ

  // SDRAM outputs
  io.sdram <> core.io.sdram
  val sdram_dq = IO(Vec(numSdram, Analog(sdramParams.dataW.W)))
  for (i <- 0 until numSdram) {
    sdram_dq(i) <> core.sdram_dq(i)
  }
}

class APBSDRAM(address: Seq[AddressSet], sdramParams: SdramParams = SdramParams())(implicit p: Parameters) extends LazyModule {
  val node = APBSlaveNode(
    Seq(
      APBSlavePortParameters(
        Seq(
          APBSlaveParameters(
            address = address,
            supportsRead  = true,
            supportsWrite = true
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
    val ctrl = Module(new SdramApbTop(sdramParams, edge.bundle))
    ctrl.io.apb <> in
    sdram_bundle <> ctrl.io.sdram
    attach(sdram_dq, ctrl.sdram_dq(0))
  }
}
