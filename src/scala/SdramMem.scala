package sdram

import chisel3._
import chisel3.util._
import chisel3.experimental.Analog

class SdramMem(val p: SdramParams = SdramParams()) extends RawModule {
  val io = IO(Flipped(new SDRAMIO(p)))
  val sdram_dq = IO(Analog(p.dataW.W))
  val clock = ( ~ io.clk.asBool ).asClock
  val reset = ( io.cs ).asAsyncReset
  val module = withClockAndReset(clock, reset) { Module(new SdramMemImpl) }
  module.io.cke_n := io.cke
  module.io.ras_n := io.ras
  module.io.cas_n := io.cas
  module.io.we_n  := io.we
  module.io.addr  := io.addr
  module.io.ba    := io.ba
  module.io.dqm_n := io.dqm
  module.io.data_input := TriStateInBuf( sdram_dq, module.io.data_output, module.io.data_out_en )
}

class SdramMemImpl extends Module with RequireAsyncReset {
  private val WIDTH_BANK  = 2
  private val WIDTH_COLS  = 9
  private val WIDTH_ROWS  = 13
  private val NUM_BANKS   = 1 << WIDTH_BANK
  private val ADDR_BITS   = WIDTH_ROWS + WIDTH_BANK + WIDTH_COLS

  val io = IO(new Bundle {
    val ras_n       = Input(Bool())
    val cke_n       = Input(Bool())
    val cas_n       = Input(Bool())
    val we_n        = Input(Bool())
    val addr        = Input(UInt(WIDTH_ROWS.W))
    val ba          = Input(UInt(WIDTH_BANK.W))
    val dqm_n       = Input(UInt(2.W))
    val data_output = Output(UInt(16.W))
    val data_out_en = Output(Bool())
    val data_input  = Input(UInt(16.W))
  })

  // --- settings ---
  private val writeBurstEnQ  = RegInit(false.B)
  private val burstLenQ = RegInit(0.U(3.W))

  // --- states ---
  private val activeRowQ   = RegInit(VecInit(Seq.fill(NUM_BANKS)(0.U(WIDTH_ROWS.W))))
  private val activeEnRowQ = RegInit(VecInit(Seq.fill(NUM_BANKS)(false.B)))
  private val burstReadCountQ = RegInit(0.U(3.W))
  private val burstWriteCountQ = RegInit(0.U(3.W))
  private val burstAddrQ = RegInit(0.U(32.W))

  // --- SyncReadMem storage (replaces DPI-C sdram_cmd) ---
  private val mem = SyncReadMem(1 << ADDR_BITS, Vec(2, UInt(8.W)))
  private val memRdEn = WireInit(false.B)
  private val memWrEn = WireInit(false.B)
  private val memAddr = WireInit(0.U(ADDR_BITS.W))
  private val memWdata = WireInit(VecInit(0.U(8.W), 0.U(8.W)))
  private val memWmask = WireInit(VecInit(false.B, false.B))

  private val memRdata = mem.read(memAddr, memRdEn)
  when(memWrEn) {
    mem.write(memAddr, memWdata, memWmask)
  }

  // --- output ---
  private val next_data_out_en = WireInit(false.B)
  private val data_out_en_reg = RegNext(next_data_out_en)
  io.data_out_en := data_out_en_reg
  io.data_output := Cat(memRdata(1), memRdata(0))

  private def memRead(byteAddr: UInt): Unit = {
    memRdEn := true.B
    memAddr := byteAddr(ADDR_BITS, 1)
  }

  private def memWrite(byteAddr: UInt, wdata: UInt, dqm_n: UInt): Unit = {
    memWrEn := true.B
    memAddr := byteAddr(ADDR_BITS, 1)
    memWdata := VecInit(wdata(7, 0), wdata(15, 8))
    memWmask := VecInit(!dqm_n(0), !dqm_n(1))
  }

  object Command extends ChiselEnum {
    val nop, active, read, write, burst_terminate, precharge, refresh, load_mode = Value
  }
  private val commandW = MuxCase(Command.nop, Seq(
    ( io.ras_n && io.cas_n && io.we_n ) -> Command.nop,
    ( !io.ras_n && io.cas_n && io.we_n ) -> Command.active,
    ( io.ras_n && !io.cas_n && io.we_n ) -> Command.read,
    ( io.ras_n && !io.cas_n && !io.we_n ) -> Command.write,
    ( io.ras_n && io.cas_n && !io.we_n ) -> Command.burst_terminate,
    ( !io.ras_n && io.cas_n && !io.we_n ) -> Command.precharge,
    ( !io.ras_n && !io.cas_n && io.we_n ) -> Command.refresh,
    ( !io.ras_n && !io.cas_n && !io.we_n ) -> Command.load_mode,
  ))
  switch(commandW) {
    is(Command.load_mode) {
      writeBurstEnQ  := ! io.addr(9)
      burstLenQ := io.addr(2, 0)
    }
    is(Command.refresh) {
      for (i <- 0 until NUM_BANKS) {
        assert( activeEnRowQ(i) === false.B, "no row should be active" )
      }
    }
    is(Command.active) {
      val baW = io.ba
      val rowW = io.addr
      assert( !activeEnRowQ(baW) || (activeRowQ(baW) === rowW) , "row should not be active or should be the same row")

      activeRowQ(baW) := rowW
      activeEnRowQ(baW) := true.B
    }
    is(Command.read) {
      val baW = io.ba
      assert(activeEnRowQ(baW), "row should be active")
      val rowW = activeRowQ(baW)
      val colW = io.addr(WIDTH_COLS - 1, 0)
      val addrW = Cat( rowW, baW, colW, 0.U(1.W) )

      memRead(addrW)
      next_data_out_en := true.B

      burstReadCountQ := (1.U << burstLenQ) - 1.U
      burstAddrQ := addrW + 2.U
    }
    is(Command.write) {
      val baW = io.ba
      assert(activeEnRowQ(baW), "row should be active")
      val rowW = activeRowQ(baW)
      val colW = io.addr(WIDTH_COLS - 1, 0)
      val addrW = Cat( rowW, baW, colW, 0.U(1.W) )

      memWrite(addrW, io.data_input, io.dqm_n)

      burstWriteCountQ := Mux( writeBurstEnQ, (1.U << burstLenQ) - 1.U, 0.U )
      burstAddrQ := addrW + 2.U
    }
    is(Command.nop) {
      when( burstReadCountQ > 0.U ) {
        memRead(burstAddrQ)
        next_data_out_en := true.B

        burstReadCountQ := burstReadCountQ - 1.U
        burstAddrQ := burstAddrQ + 2.U
      }
      when( burstWriteCountQ > 0.U ) {
        memWrite(burstAddrQ, io.data_input, io.dqm_n)

        burstWriteCountQ := burstWriteCountQ - 1.U
        burstAddrQ := burstAddrQ + 2.U
      }
    }
    is(Command.burst_terminate) {
      burstReadCountQ := 0.U
      burstWriteCountQ := 0.U
    }
    is(Command.precharge) {
      val all_banks = io.addr(10)
      when( all_banks ) {
        for (i <- 0 until NUM_BANKS) {
          activeEnRowQ(i) := false.B
        }
      } .otherwise {
        val baW = io.ba
        activeEnRowQ(baW) := false.B
      }
    }
  }
}
