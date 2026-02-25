#ifndef APB_H
#define APB_H

#include <systemc.h>

//----------------------------------------------------------------
// Interface (master)
//----------------------------------------------------------------
class apb_master {
public:
  // Members
  sc_uint<1> PSEL;
  sc_uint<1> PENABLE;
  sc_uint<1> PWRITE;
  sc_uint<32> PADDR;
  sc_uint<32> PWDATA;
  sc_uint<4> PSTRB;
  sc_uint<3> PPROT;

  // Construction
  apb_master() { init(); }

  void init(void) {
    PSEL = 0;
    PENABLE = 0;
    PWRITE = 0;
    PADDR = 0;
    PWDATA = 0;
    PSTRB = 0;
    PPROT = 0;
  }

  bool operator==(const apb_master &v) const {
    bool eq = true;
    eq &= (PSEL == v.PSEL);
    eq &= (PENABLE == v.PENABLE);
    eq &= (PWRITE == v.PWRITE);
    eq &= (PADDR == v.PADDR);
    eq &= (PWDATA == v.PWDATA);
    eq &= (PSTRB == v.PSTRB);
    eq &= (PPROT == v.PPROT);
    return eq;
  }

  friend void sc_trace(sc_trace_file *tf, const apb_master &v,
                       const std::string &path) {
    sc_trace(tf, v.PSEL, path + "/psel");
    sc_trace(tf, v.PENABLE, path + "/penable");
    sc_trace(tf, v.PWRITE, path + "/pwrite");
    sc_trace(tf, v.PADDR, path + "/paddr");
    sc_trace(tf, v.PWDATA, path + "/pwdata");
    sc_trace(tf, v.PSTRB, path + "/pstrb");
    sc_trace(tf, v.PPROT, path + "/pprot");
  }

  friend ostream &operator<<(ostream &os, apb_master const &v) {
    os << hex << "PSEL: " << v.PSEL << " ";
    os << hex << "PENABLE: " << v.PENABLE << " ";
    os << hex << "PWRITE: " << v.PWRITE << " ";
    os << hex << "PADDR: " << v.PADDR << " ";
    os << hex << "PWDATA: " << v.PWDATA << " ";
    os << hex << "PSTRB: " << v.PSTRB << " ";
    os << hex << "PPROT: " << v.PPROT << " ";
    return os;
  }

  friend istream &operator>>(istream &is, apb_master &val) {
    // Not implemented
    return is;
  }
};

#define MEMBER_COPY_APB_MASTER(s, d)                                           \
  do {                                                                         \
    s.PSEL = d.PSEL;                                                           \
    s.PENABLE = d.PENABLE;                                                     \
    s.PWRITE = d.PWRITE;                                                       \
    s.PADDR = d.PADDR;                                                         \
    s.PWDATA = d.PWDATA;                                                       \
    s.PSTRB = d.PSTRB;                                                         \
    s.PPROT = d.PPROT;                                                         \
  } while (0)

//----------------------------------------------------------------
// Interface (slave)
//----------------------------------------------------------------
class apb_slave {
public:
  // Members
  sc_uint<1> PREADY;
  sc_uint<32> PRDATA;
  sc_uint<1> PSLVERR;

  // Construction
  apb_slave() { init(); }

  void init(void) {
    PREADY = 0;
    PRDATA = 0;
    PSLVERR = 0;
  }

  bool operator==(const apb_slave &v) const {
    bool eq = true;
    eq &= (PREADY == v.PREADY);
    eq &= (PRDATA == v.PRDATA);
    eq &= (PSLVERR == v.PSLVERR);
    return eq;
  }

  friend void sc_trace(sc_trace_file *tf, const apb_slave &v,
                       const std::string &path) {
    sc_trace(tf, v.PREADY, path + "/pready");
    sc_trace(tf, v.PRDATA, path + "/prdata");
    sc_trace(tf, v.PSLVERR, path + "/pslverr");
  }

  friend ostream &operator<<(ostream &os, apb_slave const &v) {
    os << hex << "PREADY: " << v.PREADY << " ";
    os << hex << "PRDATA: " << v.PRDATA << " ";
    os << hex << "PSLVERR: " << v.PSLVERR << " ";
    return os;
  }

  friend istream &operator>>(istream &is, apb_slave &val) {
    // Not implemented
    return is;
  }
};

#define MEMBER_COPY_APB_SLAVE(s, d)                                            \
  do {                                                                         \
    s.PREADY = d.PREADY;                                                       \
    s.PRDATA = d.PRDATA;                                                       \
    s.PSLVERR = d.PSLVERR;                                                     \
  } while (0)

#endif
