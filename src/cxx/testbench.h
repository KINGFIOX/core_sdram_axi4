#include "testbench_vbase.h"
#include <cstring>
#include <systemc.h>

#include "tb_mem_test.h"
#include "tb_memory.h"

#ifdef BUS_APB
#include "tb_apb_driver.h"
#include "sdram_apb.h"
#else
#include "tb_axi4_driver.h"
#include "sdram_axi.h"
#endif

#define MEM_BASE 0x00000000
#define MEM_SIZE (512 * 1024)

//-----------------------------------------------------------------
// Module
//-----------------------------------------------------------------
class testbench : public testbench_vbase {
public:
#ifdef BUS_APB
  tb_apb_driver *m_driver;
  sdram_apb *m_dut;
  sc_signal<apb_master> bus_m;
  sc_signal<apb_slave> bus_s;
#else
  tb_axi4_driver *m_driver;
  sdram_axi *m_dut;
  sc_signal<axi4_master> bus_m;
  sc_signal<axi4_slave> bus_s;
#endif

  tb_mem_test *m_sequencer;
  int m_num_iterations;

  void set_iterations(int iterations) { m_num_iterations = iterations; }

  //-----------------------------------------------------------------
  // process: Drive input sequence
  //-----------------------------------------------------------------
  void process(void) {
    // reset: do nothing
    wait();

    m_driver->enable_delays(true);

    m_sequencer->add_region(MEM_BASE, MEM_SIZE);
    m_sequencer->trace_access(true);

    memset(m_sequencer->get_array(MEM_BASE), 0, MEM_SIZE);

    m_sequencer->start(m_num_iterations);
    m_sequencer->wait_complete();
    sc_stop();
  }

  void init_trace(void) {
    verilator_trace_enable("verilator.vcd", m_dut);
  }

  SC_HAS_PROCESS(testbench);
  testbench(sc_module_name name) : testbench_vbase(name) {
#ifdef BUS_APB
    m_driver = new tb_apb_driver("DRIVER");
    m_driver->apb_out(bus_m);
    m_driver->apb_in(bus_s);

    m_sequencer = new tb_mem_test("SEQ", m_driver, 4);

    m_dut = new sdram_apb("MEM");
#else
    m_driver = new tb_axi4_driver("DRIVER");
    m_driver->axi_out(bus_m);
    m_driver->axi_in(bus_s);

    m_sequencer = new tb_mem_test("SEQ", m_driver, 32);

    m_dut = new sdram_axi("MEM");
#endif
    m_sequencer->clk_in(clk);
    m_sequencer->rst_in(rst);

    m_dut->clk_in(clk);
    m_dut->rst_in(rst);
    m_dut->inport_in(bus_m);
    m_dut->inport_out(bus_s);
  }
};
