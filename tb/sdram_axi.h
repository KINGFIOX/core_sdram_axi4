
#ifndef SDRAM_AXI_H
#define SDRAM_AXI_H
#include <systemc.h>

#include "axi4.h"
#include "sdram_io.h"

class VSDRAMSimTop;
class VerilatedVcdSc;

//-------------------------------------------------------------
// sdram_axi: RTL wrapper class
//-------------------------------------------------------------
class sdram_axi : public sc_module {
public:
  sc_in<bool> clk_in;
  sc_in<bool> rst_in;

  sc_in<axi4_master> inport_in;
  sc_out<axi4_slave> inport_out;
  sc_in<sdram_io_slave> sdram_in;
  sc_out<sdram_io_master> sdram_out;

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
    TRACE_SIGNAL(sdram_in);
    TRACE_SIGNAL(sdram_out);

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

  // SDRAM input
  sc_signal<sc_uint<16>> m_sdram_data_input;

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

  // SDRAM outputs
  sc_signal<bool> m_sdram_clk;
  sc_signal<bool> m_sdram_cke;
  sc_signal<bool> m_sdram_cs;
  sc_signal<bool> m_sdram_ras;
  sc_signal<bool> m_sdram_cas;
  sc_signal<bool> m_sdram_we;
  sc_signal<sc_uint<2>> m_sdram_dqm;
  sc_signal<sc_uint<13>> m_sdram_addr;
  sc_signal<sc_uint<2>> m_sdram_ba;
  sc_signal<sc_uint<16>> m_sdram_data_output;
  sc_signal<bool> m_sdram_data_out_en;

public:
  VSDRAMSimTop *m_rtl;
#if VM_TRACE
  VerilatedVcdSc *m_vcd;
  bool m_delay_waves;
  sc_core::sc_time m_waves_start;
#endif
};

#endif
