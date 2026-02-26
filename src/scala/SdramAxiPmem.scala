package sdram

import chisel3._
import chisel3.util._
import freechips.rocketchip.amba.axi4._

class RamIO extends Bundle {
  val wr = Output(UInt(4.W))
  val rd = Output(Bool())
  val len = Output(UInt(8.W))
  val addr = Output(UInt(32.W))
  val writeData = Output(UInt(32.W))
  val accept = Input(Bool())
  val ack = Input(Bool())
  val error = Input(Bool())
  val readData = Input(UInt(32.W))
}

class SdramAxiPmem(axiParams: AXI4BundleParameters = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AXI4Bundle(axiParams))
    val ram = new RamIO
  })

  def calculateAddrNext(addr: UInt, axtype: UInt, axlen: UInt): UInt = {
    val result = WireDefault(addr + 4.U)
    val mask = WireDefault(0.U(32.W))

    switch(axtype) {
      is(0.U) {
        result := addr
      }
      is(2.U) {
        switch(axlen) {
          is(0.U) {
            mask := "h03".U
          }
          is(1.U) {
            mask := "h07".U
          }
          is(3.U) {
            mask := "h0F".U
          }
          is(7.U) {
            mask := "h1F".U
          }
          is(15.U) {
            mask := "h3F".U
          }
        }
        when(axtype === 2.U) {
          result := (addr & ~mask) | ((addr + 4.U) & mask)
        }
      }
    }
    result
  }

  // --- Registers ---
  val reqLenQ = RegInit(0.U(8.W))
  val reqAddrQ = RegInit(0.U(32.W))
  val reqRdQ = RegInit(false.B)
  val reqWrQ = RegInit(false.B)
  val reqIdQ = RegInit(0.U(4.W))
  val reqAxburstQ = RegInit(0.U(2.W))
  val reqAxlenQ = RegInit(0.U(8.W))
  val reqPrioQ = RegInit(false.B)
  val reqHoldRdQ = RegInit(false.B)
  val reqHoldWrQ = RegInit(false.B)

  val ramWrO = Wire(UInt(4.W))
  val ramRdO = Wire(Bool())

  // --- Request tracking FIFO ---
  val reqFifo = Module(new SdramAxiPmemFifo(width = 6))
  val reqFifoAcceptW = reqFifo.io.accept

  // --- Response buffering FIFO ---
  val respFifo = Module(new SdramAxiPmemFifo(width = 32))

  // --- Priority arbitration ---
  val writePrioW = (reqPrioQ && !reqHoldRdQ) || reqHoldWrQ
  val readPrioW = (!reqPrioQ && !reqHoldWrQ) || reqHoldRdQ

  val writeActiveW = (io.axi.aw.valid || reqWrQ) && !reqRdQ && reqFifoAcceptW && (writePrioW || reqWrQ || !io.axi.ar.valid)
  val readActiveW = (io.axi.ar.valid || reqRdQ) && !reqWrQ && reqFifoAcceptW && (readPrioW || reqRdQ || !io.axi.aw.valid)

  io.axi.aw.ready := writeActiveW && !reqWrQ && io.ram.accept && reqFifoAcceptW
  io.axi.w.ready := writeActiveW && io.ram.accept && reqFifoAcceptW
  io.axi.ar.ready := readActiveW && !reqRdQ && io.ram.accept && reqFifoAcceptW

  val addrW = Mux(reqWrQ || reqRdQ, reqAddrQ, Mux(writeActiveW, io.axi.aw.bits.addr, io.axi.ar.bits.addr))

  val wrW = writeActiveW && io.axi.w.valid
  val rdW = readActiveW

  ramWrO := Mux(wrW, io.axi.w.bits.strb, 0.U)
  ramRdO := rdW

  io.ram.addr := addrW
  io.ram.writeData := io.axi.w.bits.data
  io.ram.rd := ramRdO
  io.ram.wr := ramWrO
  io.ram.len := Mux(io.axi.aw.valid, io.axi.aw.bits.len,
    Mux(io.axi.ar.valid, io.axi.ar.bits.len, 0.U))

  // --- Sequential: burst tracking ---
  when((ramWrO =/= 0.U || ramRdO) && io.ram.accept) {
    when(reqLenQ === 0.U) {
      reqRdQ := false.B
      reqWrQ := false.B
    }.otherwise {
      reqAddrQ := calculateAddrNext(reqAddrQ, reqAxburstQ, reqAxlenQ)
      reqLenQ := reqLenQ - 1.U
    }
  }

  when(io.axi.aw.fire) {
    when(io.axi.w.fire) {
      reqWrQ := !io.axi.w.bits.last
      reqLenQ := io.axi.aw.bits.len - 1.U
      reqIdQ := io.axi.aw.bits.id
      reqAxburstQ := io.axi.aw.bits.burst
      reqAxlenQ := io.axi.aw.bits.len
      reqAddrQ := calculateAddrNext(io.axi.aw.bits.addr, io.axi.aw.bits.burst, io.axi.aw.bits.len)
    }.otherwise {
      reqWrQ := true.B
      reqLenQ := io.axi.aw.bits.len
      reqIdQ := io.axi.aw.bits.id
      reqAxburstQ := io.axi.aw.bits.burst
      reqAxlenQ := io.axi.aw.bits.len
      reqAddrQ := io.axi.aw.bits.addr
    }
    reqPrioQ := !reqPrioQ
  }.elsewhen(io.axi.ar.fire) {
    reqRdQ := io.axi.ar.bits.len =/= 0.U
    reqLenQ := io.axi.ar.bits.len - 1.U
    reqAddrQ := calculateAddrNext(io.axi.ar.bits.addr, io.axi.ar.bits.burst, io.axi.ar.bits.len)
    reqIdQ := io.axi.ar.bits.id
    reqAxburstQ := io.axi.ar.bits.burst
    reqAxlenQ := io.axi.ar.bits.len
    reqPrioQ := !reqPrioQ
  }

  // --- Hold logic ---
  when(ramRdO && !io.ram.accept) {
    reqHoldRdQ := true.B
  }.elsewhen(io.ram.accept) {
    reqHoldRdQ := false.B
  }

  when(ramWrO.orR && !io.ram.accept) {
    reqHoldWrQ := true.B
  }.elsewhen(io.ram.accept) {
    reqHoldWrQ := false.B
  }

  // --- Request tracking ---
  val reqPushW = (ramRdO || (ramWrO =/= 0.U)) && io.ram.accept

  val reqInR = WireDefault(Cat(ramRdO, reqLenQ === 0.U, reqIdQ))

  when(io.axi.ar.fire) {
    reqInR := Cat(1.U(1.W), io.axi.ar.bits.len === 0.U, io.axi.ar.bits.id)
  }.elsewhen(io.axi.aw.fire) {
    reqInR := Cat(0.U(1.W), io.axi.aw.bits.len === 0.U, io.axi.aw.bits.id)
  }

  // --- Response accept ---
  val reqOutW = reqFifo.io.dataOut
  val reqOutValidW = reqFifo.io.valid

  val respIsWriteW = reqOutValidW && !reqOutW(5)
  val respIsReadW = reqOutValidW && reqOutW(5)
  val respIsLastW = reqOutW(4)
  val respIdW = reqOutW(3, 0)

  val respValidW = respFifo.io.valid

  val respAcceptW = io.axi.r.fire || io.axi.b.fire || (respValidW && respIsWriteW && !respIsLastW)

  // --- Wire up request FIFO ---
  reqFifo.io.dataIn := reqInR
  reqFifo.io.push := reqPushW
  reqFifo.io.pop := respAcceptW

  // --- Wire up response FIFO ---
  respFifo.io.dataIn := io.ram.readData
  respFifo.io.push := io.ram.ack
  respFifo.io.pop := respAcceptW

  // --- Response outputs ---
  io.axi.b.valid := respValidW & respIsWriteW & respIsLastW
  io.axi.b.bits.resp := 0.U
  io.axi.b.bits.id := respIdW

  io.axi.r.valid := respValidW & respIsReadW
  io.axi.r.bits.data := respFifo.io.dataOut
  io.axi.r.bits.resp := 0.U
  io.axi.r.bits.id := respIdW
  io.axi.r.bits.last := respIsLastW
}
