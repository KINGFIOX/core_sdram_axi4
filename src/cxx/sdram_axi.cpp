
#include "sdram_axi.h"
#include "VSDRAMSimTop.h"

#if VM_TRACE
#include "verilated.h"
#include "verilated_vcd_sc.h"
#endif

//-------------------------------------------------------------
// Constructor
//-------------------------------------------------------------
sdram_axi::sdram_axi(sc_module_name name) : sc_module(name) {
  m_rtl = new VSDRAMSimTop("VSDRAMSimTop");

  m_rtl->clock(m_clk_in);
  m_rtl->reset(m_rst_in);

  // AW channel
  m_rtl->in_aw_valid(m_in_aw_valid);
  m_rtl->in_aw_bits_addr(m_in_aw_bits_addr);
  m_rtl->in_aw_bits_id(m_in_aw_bits_id);
  m_rtl->in_aw_bits_len(m_in_aw_bits_len);
  m_rtl->in_aw_bits_size(m_in_aw_bits_size);
  m_rtl->in_aw_bits_burst(m_in_aw_bits_burst);
  m_rtl->in_aw_ready(m_in_aw_ready);

  // W channel
  m_rtl->in_w_valid(m_in_w_valid);
  m_rtl->in_w_bits_data(m_in_w_bits_data);
  m_rtl->in_w_bits_strb(m_in_w_bits_strb);
  m_rtl->in_w_bits_last(m_in_w_bits_last);
  m_rtl->in_w_ready(m_in_w_ready);

  // B channel
  m_rtl->in_b_ready(m_in_b_ready);
  m_rtl->in_b_valid(m_in_b_valid);
  m_rtl->in_b_bits_resp(m_in_b_bits_resp);
  m_rtl->in_b_bits_id(m_in_b_bits_id);

  // AR channel
  m_rtl->in_ar_valid(m_in_ar_valid);
  m_rtl->in_ar_bits_addr(m_in_ar_bits_addr);
  m_rtl->in_ar_bits_id(m_in_ar_bits_id);
  m_rtl->in_ar_bits_len(m_in_ar_bits_len);
  m_rtl->in_ar_bits_size(m_in_ar_bits_size);
  m_rtl->in_ar_bits_burst(m_in_ar_bits_burst);
  m_rtl->in_ar_ready(m_in_ar_ready);

  // R channel
  m_rtl->in_r_ready(m_in_r_ready);
  m_rtl->in_r_valid(m_in_r_valid);
  m_rtl->in_r_bits_data(m_in_r_bits_data);
  m_rtl->in_r_bits_resp(m_in_r_bits_resp);
  m_rtl->in_r_bits_id(m_in_r_bits_id);
  m_rtl->in_r_bits_last(m_in_r_bits_last);

  // SDRAM
  m_rtl->sdram_clk(m_sdram_clk);
  m_rtl->sdram_cke(m_sdram_cke);
  m_rtl->sdram_cs(m_sdram_cs);
  m_rtl->sdram_ras(m_sdram_ras);
  m_rtl->sdram_cas(m_sdram_cas);
  m_rtl->sdram_we(m_sdram_we);
  m_rtl->sdram_dqm(m_sdram_dqm);
  m_rtl->sdram_addr(m_sdram_addr);
  m_rtl->sdram_ba(m_sdram_ba);
  m_rtl->sdram_dq(m_sdram_dq);

  // 这里使用 sc_method, 而不是 sc_cthread
  // verilated sdram_axi, 他内部有自己维护的 sc_cthread 之类的
  // 我们这里只用做连线就好了: 将结构化的 axi4_master, axi4_slave 转换成 一根根的 sc_signal
  // 因为 sram_axi 对外的信号线是一根根的 sc_signal, 而不是我们这里包装的 axi4_master, axi4_slave
  // 连线是 纯组合逻辑, 所以用 sc_method
  // 因为是纯组合逻辑, 所以就需要将所有的信号都加入到敏感列表中, 类似于 always @(*)
  SC_METHOD(async_outputs);
  sensitive << clk_in;
  sensitive << rst_in;
  sensitive << inport_in;
  sensitive << sdram_in;
  sensitive << m_in_aw_ready;
  sensitive << m_in_w_ready;
  sensitive << m_in_b_valid;
  sensitive << m_in_b_bits_resp;
  sensitive << m_in_b_bits_id;
  sensitive << m_in_ar_ready;
  sensitive << m_in_r_valid;
  sensitive << m_in_r_bits_data;
  sensitive << m_in_r_bits_resp;
  sensitive << m_in_r_bits_id;
  sensitive << m_in_r_bits_last;
  sensitive << m_sdram_clk;
  sensitive << m_sdram_cke;
  sensitive << m_sdram_cs;
  sensitive << m_sdram_ras;
  sensitive << m_sdram_cas;
  sensitive << m_sdram_we;
  sensitive << m_sdram_dqm;
  sensitive << m_sdram_addr;
  sensitive << m_sdram_ba;

#if VM_TRACE
  m_vcd = NULL;
  m_delay_waves = false;
#endif
}
//-------------------------------------------------------------
// trace_enable
//-------------------------------------------------------------
void sdram_axi::trace_enable(VerilatedVcdSc *p) {
#if VM_TRACE
  m_vcd = p;
  m_rtl->trace(m_vcd, 99);
#endif
}
void sdram_axi::trace_enable(VerilatedVcdSc *p, sc_core::sc_time start_time) {
#if VM_TRACE
  m_vcd = p;
  m_delay_waves = true;
  m_waves_start = start_time;
#endif
}
//-------------------------------------------------------------
// async_outputs
//-------------------------------------------------------------
void sdram_axi::async_outputs(void) {
  m_clk_in.write(clk_in.read());
  m_rst_in.write(rst_in.read());

  axi4_master inport_i = inport_in.read();

  // AW channel
  m_in_aw_valid.write(inport_i.AWVALID);
  m_in_aw_bits_addr.write(inport_i.AWADDR);
  m_in_aw_bits_id.write(inport_i.AWID);
  m_in_aw_bits_len.write(inport_i.AWLEN);
  m_in_aw_bits_burst.write(inport_i.AWBURST);
  m_in_aw_bits_size.write(2);
  // W channel
  m_in_w_valid.write(inport_i.WVALID);
  m_in_w_bits_data.write(inport_i.WDATA);
  m_in_w_bits_strb.write(inport_i.WSTRB);
  m_in_w_bits_last.write(inport_i.WLAST);

  // B channel
  m_in_b_ready.write(inport_i.BREADY);

  // AR channel
  m_in_ar_valid.write(inport_i.ARVALID);
  m_in_ar_bits_addr.write(inport_i.ARADDR);
  m_in_ar_bits_id.write(inport_i.ARID);
  m_in_ar_bits_len.write(inport_i.ARLEN);
  m_in_ar_bits_burst.write(inport_i.ARBURST);
  m_in_ar_bits_size.write(2);
  // R channel
  m_in_r_ready.write(inport_i.RREADY);

  // AXI slave outputs
  axi4_slave inport_o;
  inport_o.AWREADY = m_in_aw_ready.read();
  inport_o.WREADY = m_in_w_ready.read();
  inport_o.BVALID = m_in_b_valid.read();
  inport_o.BRESP = m_in_b_bits_resp.read();
  inport_o.BID = m_in_b_bits_id.read();
  inport_o.ARREADY = m_in_ar_ready.read();
  inport_o.RVALID = m_in_r_valid.read();
  inport_o.RDATA = m_in_r_bits_data.read();
  inport_o.RRESP = m_in_r_bits_resp.read();
  inport_o.RID = m_in_r_bits_id.read();
  inport_o.RLAST = m_in_r_bits_last.read();
  inport_out.write(inport_o);

  // SDRAM: simulate tristate bus
  // devide tri-state dq into dq_out dq_en,
  sdram_io_slave sdram_i = sdram_in.read();
  uint16_t dq_out = m_rtl->sdram_dq__out;
  uint16_t dq_en = m_rtl->sdram_dq__en;

  if (dq_en != 0) m_sdram_dq.write((sc_uint<16>)dq_out);
  else m_sdram_dq.write(sdram_i.DATA_INPUT);

  // SDRAM outputs
  sdram_io_master sdram_o;
  sdram_o.CLK = m_sdram_clk.read();
  sdram_o.CKE = m_sdram_cke.read();
  sdram_o.CS = m_sdram_cs.read();
  sdram_o.RAS = m_sdram_ras.read();
  sdram_o.CAS = m_sdram_cas.read();
  sdram_o.WE = m_sdram_we.read();
  sdram_o.DQM = m_sdram_dqm.read();
  sdram_o.ADDR = m_sdram_addr.read();
  sdram_o.BA = m_sdram_ba.read();
  sdram_o.DATA_OUTPUT = dq_out;
  sdram_o.DATA_OUT_EN = (dq_en != 0) ? 1 : 0;
  sdram_out.write(sdram_o);
}
