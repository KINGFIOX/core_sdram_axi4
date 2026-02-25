#ifndef SDRAM_APB_H
#define SDRAM_APB_H
#include <systemc.h>

#include "apb.h"

class VSDRAMApbSimTop;
class VerilatedVcdSc;

//-------------------------------------------------------------
// sdram_apb: RTL wrapper class (APB only, SDRAM is internal)
//-------------------------------------------------------------
class sdram_apb : public sc_module {
public:
  sc_in<bool> clk_in;
  sc_in<bool> rst_in;

  sc_in<apb_master> inport_in;
  sc_out<apb_slave> inport_out;

  //-------------------------------------------------------------
  // Constructor
  //-------------------------------------------------------------
  SC_HAS_PROCESS(sdram_apb);
  sdram_apb(sc_module_name name);

  //-------------------------------------------------------------
  // Trace
  //-------------------------------------------------------------
  virtual void add_trace(sc_trace_file *vcd, std::string prefix) {
#undef TRACE_SIGNAL
#define TRACE_SIGNAL(s) sc_trace(vcd, s, prefix + #s)

    TRACE_SIGNAL(clk_in);
    TRACE_SIGNAL(rst_in);
    TRACE_SIGNAL(inport_in);
    TRACE_SIGNAL(inport_out);

#undef TRACE_SIGNAL
  }

  void async_outputs(void);
  void trace_rtl(void);
  void trace_enable(VerilatedVcdSc *p);
  void trace_enable(VerilatedVcdSc *p, sc_core::sc_time start_time);

  //-------------------------------------------------------------
  // Signals
  //-------------------------------------------------------------
private:
  sc_signal<bool> m_clk_in;
  sc_signal<bool> m_rst_in;

  // APB master -> slave (inputs to DUT)
  sc_signal<bool> m_in_psel;
  sc_signal<bool> m_in_penable;
  sc_signal<bool> m_in_pwrite;
  sc_signal<sc_uint<32>> m_in_paddr;
  sc_signal<sc_uint<3>> m_in_pprot;
  sc_signal<sc_uint<32>> m_in_pwdata;
  sc_signal<sc_uint<4>> m_in_pstrb;

  // APB slave -> master (outputs from DUT)
  sc_signal<bool> m_in_pready;
  sc_signal<sc_uint<32>> m_in_prdata;
  sc_signal<bool> m_in_pslverr;

public:
  VSDRAMApbSimTop *m_rtl;
#if VM_TRACE
  VerilatedVcdSc *m_vcd;
  bool m_delay_waves;
  sc_core::sc_time m_waves_start;
#endif
};

#endif
