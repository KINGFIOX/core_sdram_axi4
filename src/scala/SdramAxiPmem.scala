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

class SdramAxiPmem(
    axiParams: AXI4BundleParameters =
      AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 4)
) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AXI4Bundle(axiParams))
    val ram = new RamIO
  })

  val QUEUE_DEPTH = 4

  def calculateAddrNext(addr: UInt, axtype: UInt, axlen: UInt): UInt = {
    val result = WireDefault(addr + 4.U)
    val mask = WireDefault(0.U(32.W))
    switch(axtype) {
      is(0.U) { result := addr }
      is(2.U) {
        switch(axlen) {
          is(0.U) { mask := "h03".U }
          is(1.U) { mask := "h07".U }
          is(3.U) { mask := "h0F".U }
          is(7.U) { mask := "h1F".U }
          is(15.U) { mask := "h3F".U }
        }
        result := (addr & ~mask) | ((addr + 4.U) & mask)
      }
    }
    result
  }

  // ==================== Read FSM ====================
  object RState extends ChiselEnum {
    val rIdle, rBurst = Value
  }

  val rState = RegInit(RState.rIdle)
  val rAddr = Reg(UInt(32.W))
  val rId = Reg(UInt(axiParams.idBits.W))
  val rBurstType = Reg(UInt(2.W))
  val rBurstLen = Reg(UInt(8.W))
  val rReqCnt = Reg(UInt(8.W))
  val rRespCnt = Reg(UInt(8.W))
  val rAllReqsSent = RegInit(true.B)

  val rDataQ = Module(new Queue(UInt(axiParams.dataBits.W), QUEUE_DEPTH))

  // ==================== Write FSM ====================
  object WState extends ChiselEnum {
    val wIdle, wBurst, wResp = Value
  }

  val wState = RegInit(WState.wIdle)
  val wAddr = Reg(UInt(32.W))
  val wId = Reg(UInt(axiParams.idBits.W))
  val wBurstType = Reg(UInt(2.W))
  val wBurstLen = Reg(UInt(8.W))
  val wReqCnt = Reg(UInt(8.W))
  val wAllReqsSent = RegInit(true.B)

  // ==================== Ack Pending & Flow Control ====================
  val rAckPending = RegInit(0.U(4.W))
  val wAckPending = RegInit(0.U(4.W))
  val rOutstanding = RegInit(0.U(4.W))

  // ==================== Arbiter ====================
  val rReqRam = rState === RState.rBurst && !rAllReqsSent && rOutstanding < QUEUE_DEPTH.U
  val wReqRam = wState === WState.wBurst && !wAllReqsSent && io.axi.w.valid

  val arbiter = Module(new RRArbiter(Bool(), 2))
  arbiter.io.in(0).valid := rReqRam && wAckPending === 0.U
  arbiter.io.in(0).bits := DontCare
  arbiter.io.in(1).valid := wReqRam && rAckPending === 0.U
  arbiter.io.in(1).bits := DontCare
  arbiter.io.out.ready := io.ram.accept

  val grantRead = arbiter.io.out.valid && arbiter.io.chosen === 0.U
  val grantWrite = arbiter.io.out.valid && arbiter.io.chosen === 1.U

  // ==================== Ack Routing ====================
  val ackToRead = io.ram.ack && rAckPending > 0.U
  val ackToWrite = io.ram.ack && wAckPending > 0.U

  rAckPending := rAckPending + (grantRead && io.ram.accept).asUInt - ackToRead.asUInt
  wAckPending := wAckPending + (grantWrite && io.ram.accept).asUInt - ackToWrite.asUInt
  rOutstanding := rOutstanding + (grantRead && io.ram.accept).asUInt - io.axi.r.fire.asUInt

  // ==================== RAM Interface Mux ====================
  io.ram.addr := MuxCase(
    0.U,
    Seq(
      grantRead -> rAddr,
      grantWrite -> wAddr
    )
  )
  io.ram.rd := grantRead
  io.ram.wr := Mux(grantWrite, io.axi.w.bits.strb, 0.U)
  io.ram.writeData := Mux(grantWrite, io.axi.w.bits.data, 0.U)
  io.ram.len := MuxCase(
    0.U,
    Seq(
      grantRead -> rBurstLen,
      grantWrite -> wBurstLen
    )
  )

  // ==================== Read Data Queue ====================
  rDataQ.io.enq.valid := ackToRead
  rDataQ.io.enq.bits := io.ram.readData
  rDataQ.io.deq.ready := io.axi.r.ready && rState === RState.rBurst

  // ==================== AXI Read Response ====================
  io.axi.r.valid := rDataQ.io.deq.valid && rState === RState.rBurst
  io.axi.r.bits.data := rDataQ.io.deq.bits
  io.axi.r.bits.resp := 0.U
  io.axi.r.bits.id := rId // same id in one burst
  io.axi.r.bits.last := rRespCnt === 0.U

  // ==================== AXI Write/Other Defaults ====================
  io.axi.ar.ready := rState === RState.rIdle
  io.axi.aw.ready := wState === WState.wIdle
  io.axi.w.ready := false.B
  io.axi.b.valid := false.B
  io.axi.b.bits.resp := 0.U
  io.axi.b.bits.id := wId

  // ==================== Read FSM Logic ====================
  switch(rState) {
    is(RState.rIdle) {
      when(io.axi.ar.fire) {
        rAddr := io.axi.ar.bits.addr // latch
        rId := io.axi.ar.bits.id
        rBurstType := io.axi.ar.bits.burst
        rBurstLen := io.axi.ar.bits.len
        rReqCnt := io.axi.ar.bits.len // counter
        rRespCnt := io.axi.ar.bits.len
        rAllReqsSent := false.B
        rState := RState.rBurst
      }
    }
    is(RState.rBurst) {
      when(grantRead && io.ram.accept) { // pop from queue
        when(rReqCnt === 0.U) {
          rAllReqsSent := true.B
        }.otherwise {
          rReqCnt := rReqCnt - 1.U
          rAddr := calculateAddrNext(rAddr, rBurstType, rBurstLen)
        }
      }

      when(io.axi.r.fire) {
        when(rRespCnt === 0.U) {
          rState := RState.rIdle
        }.otherwise {
          rRespCnt := rRespCnt - 1.U
        }
      }
    }
  }

  // ==================== Write FSM Logic ====================
  switch(wState) {
    is(WState.wIdle) {
      when(io.axi.aw.fire) {
        wAddr := io.axi.aw.bits.addr
        wId := io.axi.aw.bits.id
        wBurstType := io.axi.aw.bits.burst
        wBurstLen := io.axi.aw.bits.len
        wReqCnt := io.axi.aw.bits.len
        wAllReqsSent := false.B
        wState := WState.wBurst
      }
    }
    is(WState.wBurst) {
      io.axi.w.ready := grantWrite && io.ram.accept
      when(io.axi.w.fire) {
        when(wReqCnt === 0.U) {
          wAllReqsSent := true.B
        }.otherwise {
          wReqCnt := wReqCnt - 1.U
          wAddr := calculateAddrNext(wAddr, wBurstType, wBurstLen)
        }
      }

      when(wAllReqsSent && wAckPending === 0.U) {
        wState := WState.wResp
      }
    }
    is(WState.wResp) {
      io.axi.b.valid := true.B
      when(io.axi.b.fire) {
        wState := WState.wIdle
      }
    }
  }
}
