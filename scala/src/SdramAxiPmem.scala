package org.chipsalliance.sdram

import chisel3._
import chisel3.util._

class AxiSlaveIO extends Bundle {
  val awvalid = Input(Bool())
  val awaddr  = Input(UInt(32.W))
  val awid    = Input(UInt(4.W))
  val awlen   = Input(UInt(8.W))
  val awburst = Input(UInt(2.W))
  val wvalid  = Input(Bool())
  val wdata   = Input(UInt(32.W))
  val wstrb   = Input(UInt(4.W))
  val wlast   = Input(Bool())
  val bready  = Input(Bool())
  val arvalid = Input(Bool())
  val araddr  = Input(UInt(32.W))
  val arid    = Input(UInt(4.W))
  val arlen   = Input(UInt(8.W))
  val arburst = Input(UInt(2.W))
  val rready  = Input(Bool())

  val awready = Output(Bool())
  val wready  = Output(Bool())
  val bvalid  = Output(Bool())
  val bresp   = Output(UInt(2.W))
  val bid     = Output(UInt(4.W))
  val arready = Output(Bool())
  val rvalid  = Output(Bool())
  val rdata   = Output(UInt(32.W))
  val rresp   = Output(UInt(2.W))
  val rid     = Output(UInt(4.W))
  val rlast   = Output(Bool())
}

class RamIO extends Bundle {
  val wr        = Output(UInt(4.W))
  val rd        = Output(Bool())
  val len       = Output(UInt(8.W))
  val addr      = Output(UInt(32.W))
  val writeData = Output(UInt(32.W))
  val accept    = Input(Bool())
  val ack       = Input(Bool())
  val error     = Input(Bool())
  val readData  = Input(UInt(32.W))
}

class SdramAxiPmem extends Module {
  val io = IO(new Bundle {
    val axi = new AxiSlaveIO
    val ram = new RamIO
  })

  // --- calculate_addr_next ---
  def calculateAddrNext(addr: UInt, axtype: UInt, axlen: UInt): UInt = {
    val result = WireDefault(addr + 4.U)
    val mask   = WireDefault(0.U(32.W))

    switch(axtype) {
      is(0.U) { // FIXED
        result := addr
      }
      is(2.U) { // WRAP
        switch(axlen) {
          is(0.U)  { mask := "h03".U }
          is(1.U)  { mask := "h07".U }
          is(3.U)  { mask := "h0F".U }
          is(7.U)  { mask := "h1F".U }
          is(15.U) { mask := "h3F".U }
        }
        when(axtype === 2.U) {
          result := (addr & ~mask) | ((addr + 4.U) & mask)
        }
      }
    }
    result
  }

  // --- Registers ---
  val reqLenQ     = RegInit(0.U(8.W))
  val reqAddrQ    = RegInit(0.U(32.W))
  val reqRdQ      = RegInit(false.B)
  val reqWrQ      = RegInit(false.B)
  val reqIdQ      = RegInit(0.U(4.W))
  val reqAxburstQ = RegInit(0.U(2.W))
  val reqAxlenQ   = RegInit(0.U(8.W))
  val reqPrioQ    = RegInit(false.B)
  val reqHoldRdQ  = RegInit(false.B)
  val reqHoldWrQ  = RegInit(false.B)

  // Forward-declare wires that participate in combinational loops
  val ramWrO  = Wire(UInt(4.W))
  val ramRdO  = Wire(Bool())

  // --- Request tracking FIFO ---
  val reqFifo = Module(new SdramAxiPmemFifo(width = 6))
  val reqFifoAcceptW = reqFifo.io.accept

  // --- Response buffering FIFO ---
  val respFifo = Module(new SdramAxiPmemFifo(width = 32))

  // --- Priority arbitration ---
  val writePrioW = (reqPrioQ && !reqHoldRdQ) || reqHoldWrQ
  val readPrioW  = (!reqPrioQ && !reqHoldWrQ) || reqHoldRdQ

  val writeActiveW = (io.axi.awvalid || reqWrQ) && !reqRdQ && reqFifoAcceptW &&
    (writePrioW || reqWrQ || !io.axi.arvalid)
  val readActiveW = (io.axi.arvalid || reqRdQ) && !reqWrQ && reqFifoAcceptW &&
    (readPrioW || reqRdQ || !io.axi.awvalid)

  io.axi.awready := writeActiveW && !reqWrQ && io.ram.accept && reqFifoAcceptW
  io.axi.wready  := writeActiveW && io.ram.accept && reqFifoAcceptW
  io.axi.arready := readActiveW && !reqRdQ && io.ram.accept && reqFifoAcceptW

  val addrW = Mux(reqWrQ || reqRdQ, reqAddrQ,
    Mux(writeActiveW, io.axi.awaddr, io.axi.araddr))

  val wrW = writeActiveW && io.axi.wvalid
  val rdW = readActiveW

  ramWrO := Mux(wrW, io.axi.wstrb, 0.U)
  ramRdO := rdW

  io.ram.addr      := addrW
  io.ram.writeData := io.axi.wdata
  io.ram.rd        := ramRdO
  io.ram.wr        := ramWrO
  io.ram.len       := Mux(io.axi.awvalid, io.axi.awlen,
    Mux(io.axi.arvalid, io.axi.arlen, 0.U))

  // --- Sequential: burst tracking ---
  when((ramWrO =/= 0.U || ramRdO) && io.ram.accept) {
    when(reqLenQ === 0.U) {
      reqRdQ := false.B
      reqWrQ := false.B
    }.otherwise {
      reqAddrQ := calculateAddrNext(reqAddrQ, reqAxburstQ, reqAxlenQ)
      reqLenQ  := reqLenQ - 1.U
    }
  }

  when(io.axi.awvalid && io.axi.awready) {
    when(io.axi.wvalid && io.axi.wready) {
      reqWrQ      := !io.axi.wlast
      reqLenQ     := io.axi.awlen - 1.U
      reqIdQ      := io.axi.awid
      reqAxburstQ := io.axi.awburst
      reqAxlenQ   := io.axi.awlen
      reqAddrQ    := calculateAddrNext(io.axi.awaddr, io.axi.awburst, io.axi.awlen)
    }.otherwise {
      reqWrQ      := true.B
      reqLenQ     := io.axi.awlen
      reqIdQ      := io.axi.awid
      reqAxburstQ := io.axi.awburst
      reqAxlenQ   := io.axi.awlen
      reqAddrQ    := io.axi.awaddr
    }
    reqPrioQ := !reqPrioQ
  }.elsewhen(io.axi.arvalid && io.axi.arready) {
    reqRdQ      := io.axi.arlen =/= 0.U
    reqLenQ     := io.axi.arlen - 1.U
    reqAddrQ    := calculateAddrNext(io.axi.araddr, io.axi.arburst, io.axi.arlen)
    reqIdQ      := io.axi.arid
    reqAxburstQ := io.axi.arburst
    reqAxlenQ   := io.axi.arlen
    reqPrioQ    := !reqPrioQ
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

  when(io.axi.arvalid && io.axi.arready) {
    reqInR := Cat(1.U(1.W), io.axi.arlen === 0.U, io.axi.arid)
  }.elsewhen(io.axi.awvalid && io.axi.awready) {
    reqInR := Cat(0.U(1.W), io.axi.awlen === 0.U, io.axi.awid)
  }

  // --- Response accept ---
  val reqOutW      = reqFifo.io.dataOut
  val reqOutValidW = reqFifo.io.valid

  val respIsWriteW = reqOutValidW && !reqOutW(5)
  val respIsReadW  = reqOutValidW && reqOutW(5)
  val respIsLastW  = reqOutW(4)
  val respIdW      = reqOutW(3, 0)

  val respValidW = respFifo.io.valid

  val respAcceptW = (io.axi.rvalid && io.axi.rready) ||
    (io.axi.bvalid && io.axi.bready) ||
    (respValidW && respIsWriteW && !respIsLastW)

  // --- Wire up request FIFO ---
  reqFifo.io.dataIn := reqInR
  reqFifo.io.push   := reqPushW
  reqFifo.io.pop    := respAcceptW

  // --- Wire up response FIFO ---
  respFifo.io.dataIn := io.ram.readData
  respFifo.io.push   := io.ram.ack
  respFifo.io.pop    := respAcceptW

  // --- Response outputs ---
  io.axi.bvalid := respValidW & respIsWriteW & respIsLastW
  io.axi.bresp  := 0.U
  io.axi.bid    := respIdW

  io.axi.rvalid := respValidW & respIsReadW
  io.axi.rdata  := respFifo.io.dataOut
  io.axi.rresp  := 0.U
  io.axi.rid    := respIdW
  io.axi.rlast  := respIsLastW
}
