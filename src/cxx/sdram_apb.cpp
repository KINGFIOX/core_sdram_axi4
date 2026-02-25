#include "sdram_apb.h"
#include "VSDRAMApbSimTop.h"

#if VM_TRACE
#include "verilated.h"
#include "verilated_vcd_sc.h"
#endif

//-------------------------------------------------------------
// Constructor
//-------------------------------------------------------------
sdram_apb::sdram_apb(sc_module_name name) : sc_module(name) {
  m_rtl = new VSDRAMApbSimTop("VSDRAMApbSimTop");

  m_rtl->clock(m_clk_in);
  m_rtl->reset(m_rst_in);

  // APB master -> slave
  m_rtl->in_psel(m_in_psel);
  m_rtl->in_penable(m_in_penable);
  m_rtl->in_pwrite(m_in_pwrite);
  m_rtl->in_paddr(m_in_paddr);
  m_rtl->in_pprot(m_in_pprot);
  m_rtl->in_pwdata(m_in_pwdata);
  m_rtl->in_pstrb(m_in_pstrb);

  // APB slave -> master
  m_rtl->in_pready(m_in_pready);
  m_rtl->in_prdata(m_in_prdata);
  m_rtl->in_pslverr(m_in_pslverr);

  SC_METHOD(async_outputs);
  sensitive << clk_in;
  sensitive << rst_in;
  sensitive << inport_in;
  sensitive << m_in_pready;
  sensitive << m_in_prdata;
  sensitive << m_in_pslverr;

#if VM_TRACE
  m_vcd = NULL;
  m_delay_waves = false;
#endif
}
//-------------------------------------------------------------
// trace_enable
//-------------------------------------------------------------
void sdram_apb::trace_enable(VerilatedVcdSc *p) {
#if VM_TRACE
  m_vcd = p;
  m_rtl->trace(m_vcd, 99);
#endif
}
void sdram_apb::trace_enable(VerilatedVcdSc *p, sc_core::sc_time start_time) {
#if VM_TRACE
  m_vcd = p;
  m_delay_waves = true;
  m_waves_start = start_time;
#endif
}
//-------------------------------------------------------------
// async_outputs
//-------------------------------------------------------------
void sdram_apb::async_outputs(void) {
  m_clk_in.write(clk_in.read());
  m_rst_in.write(rst_in.read());

  apb_master inport_i = inport_in.read();

  // APB master -> slave
  m_in_psel.write(inport_i.PSEL);
  m_in_penable.write(inport_i.PENABLE);
  m_in_pwrite.write(inport_i.PWRITE);
  m_in_paddr.write(inport_i.PADDR);
  m_in_pprot.write(inport_i.PPROT);
  m_in_pwdata.write(inport_i.PWDATA);
  m_in_pstrb.write(inport_i.PSTRB);

  // APB slave -> master
  apb_slave inport_o;
  inport_o.PREADY = m_in_pready.read();
  inport_o.PRDATA = m_in_prdata.read();
  inport_o.PSLVERR = m_in_pslverr.read();
  inport_out.write(inport_o);
}
