#ifndef TB_APB_DRIVER_H
#define TB_APB_DRIVER_H

#include "apb.h"
#include "tb_driver_api.h"

//-------------------------------------------------------------
// tb_apb_driver: APB4 driver interface
//-------------------------------------------------------------
class tb_apb_driver : public sc_module, public tb_driver_api {
public:
  //-------------------------------------------------------------
  // Interface I/O
  //-------------------------------------------------------------
  sc_out<apb_master> apb_out;
  sc_in<apb_slave> apb_in;

  //-------------------------------------------------------------
  // Constructor
  //-------------------------------------------------------------
  SC_HAS_PROCESS(tb_apb_driver);
  tb_apb_driver(sc_module_name name) : sc_module(name) {
    m_enable_delays = true;
    m_resp_pending = 0;
  }

  //-------------------------------------------------------------
  // Trace
  //-------------------------------------------------------------
  void add_trace(sc_trace_file *vcd, std::string prefix) {
#undef TRACE_SIGNAL
#define TRACE_SIGNAL(s) sc_trace(vcd, s, prefix + #s)

    TRACE_SIGNAL(apb_out);
    TRACE_SIGNAL(apb_in);
    TRACE_SIGNAL(m_resp_pending);

#undef TRACE_SIGNAL
  }

  //-------------------------------------------------------------
  // API
  //-------------------------------------------------------------
  void enable_delays(bool enable) { m_enable_delays = enable; }

  void write(uint32_t, uint8_t data);
  uint8_t read(uint32_t addr);

  void write32(uint32_t addr, uint32_t data);
  void write32(uint32_t addr, uint32_t data, uint8_t mask);
  uint32_t read32(uint32_t addr);

  void write(uint32_t addr, uint8_t *data, int length);
  void read(uint32_t addr, uint8_t *data, int length);

  bool delay_cycle(void) { return m_enable_delays ? rand() & 1 : 0; }

protected:
  void write_internal(uint32_t addr, uint8_t *data, int length,
                      uint8_t initial_mask);
  void apb_write(uint32_t addr, uint32_t data, uint8_t strb);
  uint32_t apb_read(uint32_t addr);

  //-------------------------------------------------------------
  // Members
  //-------------------------------------------------------------
  bool m_enable_delays;

  uint32_t m_resp_pending;
};

#endif
