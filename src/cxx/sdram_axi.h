
#ifndef SDRAM_AXI_H
#define SDRAM_AXI_H
#include <systemc.h>

#include "axi4.h"

class VSDRAMAxiSimTop;

class VerilatedVcdSc;

//-------------------------------------------------------------
// sdram_axi: RTL wrapper class (AXI4 only, SDRAM is internal)
//-------------------------------------------------------------
class sdram_axi : public sc_module {
public:
  sc_in<bool> clk_in;
  sc_in<bool> rst_in;

  sc_in<axi4_master> inport_in;
  sc_out<axi4_slave> inport_out;

  //-------------------------------------------------------------
  // Constructor
  //-------------------------------------------------------------
  SC_HAS_PROCESS(sdram_axi);
  sdram_axi(sc_module_name name);

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

  // AW channel
  sc_signal<bool> m_in_aw_valid;
  sc_signal<sc_uint<32>> m_in_aw_bits_addr;
  sc_signal<sc_uint<4>> m_in_aw_bits_id;
  sc_signal<sc_uint<8>> m_in_aw_bits_len;
  sc_signal<sc_uint<3>> m_in_aw_bits_size;
  sc_signal<sc_uint<2>> m_in_aw_bits_burst;

  // W channel
  sc_signal<bool> m_in_w_valid;
  sc_signal<sc_uint<32>> m_in_w_bits_data;
  sc_signal<sc_uint<4>> m_in_w_bits_strb;
  sc_signal<bool> m_in_w_bits_last;

  // B channel
  sc_signal<bool> m_in_b_ready;

  // AR channel
  sc_signal<bool> m_in_ar_valid;
  sc_signal<sc_uint<32>> m_in_ar_bits_addr;
  sc_signal<sc_uint<4>> m_in_ar_bits_id;
  sc_signal<sc_uint<8>> m_in_ar_bits_len;
  sc_signal<sc_uint<3>> m_in_ar_bits_size;
  sc_signal<sc_uint<2>> m_in_ar_bits_burst;

  // R channel
  sc_signal<bool> m_in_r_ready;

  // Outputs: AW ready
  sc_signal<bool> m_in_aw_ready;
  // W ready
  sc_signal<bool> m_in_w_ready;
  // B channel
  sc_signal<bool> m_in_b_valid;
  sc_signal<sc_uint<2>> m_in_b_bits_resp;
  sc_signal<sc_uint<4>> m_in_b_bits_id;
  // AR ready
  sc_signal<bool> m_in_ar_ready;
  // R channel
  sc_signal<bool> m_in_r_valid;
  sc_signal<sc_uint<32>> m_in_r_bits_data;
  sc_signal<sc_uint<2>> m_in_r_bits_resp;
  sc_signal<sc_uint<4>> m_in_r_bits_id;
  sc_signal<bool> m_in_r_bits_last;

public:
  VSDRAMAxiSimTop *m_rtl;
#if VM_TRACE
  VerilatedVcdSc *m_vcd;
  bool m_delay_waves;
  sc_core::sc_time m_waves_start;
#endif
};

#endif
