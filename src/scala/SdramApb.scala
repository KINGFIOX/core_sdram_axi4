package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, attach}
import freechips.rocketchip.amba.apb._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.diplomacy.{AddressSet}
import org.chipsalliance.cde.config.Parameters

class SdramApbTop(apbParams: APBBundleParameters = APBBundleParameters(addrBits = 32, dataBits = 32)) extends Module {
  val io = IO(new Bundle {
    val apb   = Flipped(new APBBundle(apbParams))
    val sdram = new SDRAMIO
  })

  val core = Module(new SdramAxiCore)

  // APB standard state machine
  object State extends ChiselEnum {
    val idle, setup, access, ready = Value
  }

  val state = RegInit(State.idle)
  val reqAccept = core.io.inportAccept

  switch(state) {
    is(State.idle) {
      when(io.apb.psel) {
        state := Mux(reqAccept, State.ready, State.setup)
      }
    }
    is(State.setup) {
      state := Mux(reqAccept, State.ready, State.access)
    }
    is(State.access) {
      when(reqAccept) {
        state := State.ready
      }
    }
    is(State.ready) {
      when(core.io.inportAck) {
        state := State.idle
      }
    }
  }

  // idle 检测到 APB setup phase 时组合发起请求，setup/access 阶段持续发起直到 core accept
  val isWrite = (state === State.setup || state === State.access) && io.apb.pwrite

  // SdramAxiCore connections
  core.io.inportWr        := Mux(isWrite, io.apb.pstrb, 0.U)
  core.io.inportRd        := (state === State.setup || state === State.access) && !io.apb.pwrite
  core.io.inportLen       := 0.U
  core.io.inportAddr      := io.apb.paddr
  core.io.inportWriteData := io.apb.pwdata

  // APB slave outputs
  io.apb.pready  := core.io.inportAck
  io.apb.pslverr := core.io.inportError
  io.apb.prdata  := core.io.inportReadData

  // SDRAM outputs
  io.sdram <> core.io.sdram
  val sdram_dq = IO(Analog(16.W))
  attach(sdram_dq, core.sdram_dq)
}

class APBSDRAM(address: Seq[AddressSet])(implicit p: Parameters) extends LazyModule {
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
    val sdram_bundle = IO(new SDRAMIO)
    val sdram_dq = IO(Analog(16.W))
    val ctrl = Module(new SdramApbTop(edge.bundle))
    ctrl.io.apb <> in
    sdram_bundle <> ctrl.io.sdram
    attach(sdram_dq, ctrl.sdram_dq)
  }
}
