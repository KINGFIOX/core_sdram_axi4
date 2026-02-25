#include "tb_apb_driver.h"

//-----------------------------------------------------------------
// apb_write: Single APB4 write transaction
//-----------------------------------------------------------------
void tb_apb_driver::apb_write(uint32_t addr, uint32_t data, uint8_t strb) {
  apb_master apb_o;

  // 随机插入空闲周期
  while (delay_cycle()) {
    apb_out.write(apb_o);
    wait();
  }

  // Setup phase: PSEL=1, PENABLE=0
  apb_o.PSEL = 1;
  apb_o.PENABLE = 0;
  apb_o.PWRITE = 1;
  apb_o.PADDR = addr;
  apb_o.PWDATA = data;
  apb_o.PSTRB = strb;

  apb_out.write(apb_o);
  m_resp_pending += 1;
  wait();

  // Access phase: PENABLE=1, 等待 PREADY=1
  apb_o.PENABLE = 1;
  apb_out.write(apb_o);
  wait();

  while (!apb_in.read().PREADY) {
    apb_out.write(apb_o);
    wait();
  }

  sc_assert(!apb_in.read().PSLVERR);
  m_resp_pending -= 1;

  // 回到 IDLE
  apb_o.init();
  apb_out.write(apb_o);
}
//-----------------------------------------------------------------
// apb_read: Single APB4 read transaction
//-----------------------------------------------------------------
uint32_t tb_apb_driver::apb_read(uint32_t addr) {
  apb_master apb_o;

  // 随机插入空闲周期
  while (delay_cycle()) {
    apb_out.write(apb_o);
    wait();
  }

  // Setup phase: PSEL=1, PENABLE=0
  apb_o.PSEL = 1;
  apb_o.PENABLE = 0;
  apb_o.PWRITE = 0;
  apb_o.PADDR = addr;

  apb_out.write(apb_o);
  m_resp_pending += 1;
  wait();

  // Access phase: PENABLE=1, 等待 PREADY=1
  apb_o.PENABLE = 1;
  apb_out.write(apb_o);
  wait();

  while (!apb_in.read().PREADY) {
    apb_out.write(apb_o);
    wait();
  }

  sc_assert(!apb_in.read().PSLVERR);
  uint32_t rdata = (uint32_t)apb_in.read().PRDATA;
  m_resp_pending -= 1;

  // 回到 IDLE
  apb_o.init();
  apb_out.write(apb_o);

  return rdata;
}
//-----------------------------------------------------------------
// write_internal: Write a block to a target
//-----------------------------------------------------------------
void tb_apb_driver::write_internal(uint32_t addr, uint8_t *data, int length,
                                   uint8_t initial_mask) {
  sc_assert(initial_mask == 0xF || length == 4);

  while (length > 0) {
    uint32_t addr_offset = addr & 3;
    int size = (4 - addr_offset);
    if (size > length)
      size = length;

    sc_uint<32> word_data = 0;
    sc_uint<4> word_strb = 0;

    for (int x = 0; x < size; x++) {
      word_data.range(((addr_offset + x) * 8) + 7,
                      ((addr_offset + x) * 8)) = *data++;
      word_strb[addr_offset + x] = (initial_mask >> x) & 1;
    }

    apb_write(addr & ~3, (uint32_t)word_data, (uint8_t)word_strb);

    addr += size;
    length -= size;
  }
}
//-----------------------------------------------------------------
// write: Write a block to a target
//-----------------------------------------------------------------
void tb_apb_driver::write(uint32_t addr, uint8_t *data, int length) {
  write_internal(addr, data, length, 0xF);
}
//-----------------------------------------------------------------
// read: Read a block from a target
//-----------------------------------------------------------------
void tb_apb_driver::read(uint32_t addr, uint8_t *data, int length) {
  while (length > 0) {
    uint32_t addr_offset = addr & 3;
    int size = (4 - addr_offset);
    if (size > length)
      size = length;

    uint32_t resp_data = apb_read(addr & ~3);

    for (int x = 0; x < size; x++)
      *data++ = resp_data >> (8 * (addr_offset + x));

    addr += size;
    length -= size;
  }
}
//-----------------------------------------------------------------
// write32: Write a 32-bit word (must be aligned)
//-----------------------------------------------------------------
void tb_apb_driver::write32(uint32_t addr, uint32_t data) {
  uint8_t arr[4];

  for (int i = 0; i < 4; i++)
    arr[i] = (data >> (i * 8)) & 0xFF;

  sc_assert(!(addr & 3));
  write(addr, arr, 4);
}
//-----------------------------------------------------------------
// write32: Write a 32-bit word with mask (must be aligned)
//-----------------------------------------------------------------
void tb_apb_driver::write32(uint32_t addr, uint32_t data, uint8_t mask) {
  uint8_t arr[4];

  for (int i = 0; i < 4; i++)
    arr[i] = (data >> (i * 8)) & 0xFF;

  sc_assert(!(addr & 3));
  write_internal(addr, arr, 4, mask);
}
//-----------------------------------------------------------------
// read32: Read a 32-bit word (must be aligned)
//-----------------------------------------------------------------
uint32_t tb_apb_driver::read32(uint32_t addr) {
  uint8_t data[4];
  uint32_t resp_word = 0;

  sc_assert(!(addr & 3));
  read(addr, data, 4);

  resp_word = data[3];
  resp_word <<= 8;
  resp_word |= data[2];
  resp_word <<= 8;
  resp_word |= data[1];
  resp_word <<= 8;
  resp_word |= data[0];

  return resp_word;
}
//-----------------------------------------------------------------
// write: Write a byte
//-----------------------------------------------------------------
void tb_apb_driver::write(uint32_t addr, uint8_t data) {
  write(addr, &data, 1);
}
//-----------------------------------------------------------------
// read: Read a byte
//-----------------------------------------------------------------
uint8_t tb_apb_driver::read(uint32_t addr) {
  uint8_t data = 0;
  read(addr, &data, 1);
  return data;
}
