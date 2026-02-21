package sdram

import chisel3._
import chisel3.util._

class SdramAxiPmemFifo(val width: Int, val depth: Int = 4, val addrW: Int = 2) extends Module {
  val io = IO(new Bundle {
    val dataIn = Input(UInt(width.W))
    val push = Input(Bool())
    val pop = Input(Bool())
    val dataOut = Output(UInt(width.W))
    val accept = Output(Bool())
    val valid = Output(Bool())
  })

  val countW = addrW + 1

  val ram = Reg(Vec(depth, UInt(width.W)))
  val rdPtr = RegInit(0.U(addrW.W))
  val wrPtr = RegInit(0.U(addrW.W))
  val count = RegInit(0.U(countW.W))

  val doPush = io.push & io.accept
  val doPop = io.pop & io.valid

  when(doPush) {
    ram(wrPtr) := io.dataIn
    wrPtr := wrPtr + 1.U
  }

  when(doPop) {
    rdPtr := rdPtr + 1.U
  }

  when(doPush && !doPop) {
    count := count + 1.U
  }.elsewhen(!doPush && doPop) {
    count := count - 1.U
  }

  io.accept := count =/= depth.U
  io.valid := count =/= 0.U
  io.dataOut := ram(rdPtr)
}
