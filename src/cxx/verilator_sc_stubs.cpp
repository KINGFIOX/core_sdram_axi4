// Verilator + SystemC --trace expects a global sc_time_stamp() (seconds).
#include <systemc>

__attribute__((weak))
double sc_time_stamp() {
  return sc_core::sc_time_stamp().to_seconds();
}
