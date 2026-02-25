#include "tb_sdram_mem.h"
#include <queue>

#define SDRAM_COL_W 9
#define SDRAM_BANK_W 2
#define SDRAM_ROW_W 13
#define NUM_ROWS (1 << SDRAM_ROW_W)

// REF:
// https://www.micron.com/~/media/documents/products/data-sheet/dram/128mb_x4x8x16_ait-aat_sdram.pdf

#define MAX_ROW_OPEN_TIME sc_time(35, SC_US)
#define MIN_ACTIVE_TO_ACTIVE sc_time(60, SC_NS)
#define MIN_ACTIVE_TO_ACCESS sc_time(15, SC_NS)

//-----------------------------------------------------------------
// process: Handle requests
//-----------------------------------------------------------------
void tb_sdram_mem::process(void) {
  typedef enum {
    SDRAM_CMD_INHIBIT = 0,
    SDRAM_CMD_NOP,
    SDRAM_CMD_ACTIVE,
    SDRAM_CMD_READ,
    SDRAM_CMD_WRITE,
    SDRAM_CMD_BURST_TERM,
    SDRAM_CMD_PRECHARGE,
    SDRAM_CMD_REFRESH,
    SDRAM_CMD_LOAD_MODE
  } t_sdram_cmd;

  // reset
  sc_uint<SDRAM_COL_W> col = 0;
  sc_uint<SDRAM_ROW_W> row = 0;
  sc_uint<SDRAM_BANK_W> bank = 0;
  sc_uint<32> addr = 0;
  // Clear response pipeline
  uint16_t resp_data[3];
  for (int i = 0; i < sizeof(resp_data) / sizeof(resp_data[0]); i++)
    resp_data[i] = 0;

  while (1) {
    sdram_io_master sdram_i = sdram_in.read();
    sdram_io_slave sdram_o = sdram_out.read();

    t_sdram_cmd new_cmd;

    // Command decoder
    if (sdram_i.CS) {
      new_cmd = SDRAM_CMD_INHIBIT;
    } else {
      if (sdram_i.RAS && sdram_i.CAS && sdram_i.WE) // 111
        new_cmd = SDRAM_CMD_NOP;
      else if (!sdram_i.RAS && sdram_i.CAS && sdram_i.WE) // 011
        new_cmd = SDRAM_CMD_ACTIVE;
      else if (sdram_i.RAS && !sdram_i.CAS && sdram_i.WE) // 101
        new_cmd = SDRAM_CMD_READ;
      else if (sdram_i.RAS && !sdram_i.CAS && !sdram_i.WE) // 100
        new_cmd = SDRAM_CMD_WRITE;
      else if (sdram_i.RAS && sdram_i.CAS && !sdram_i.WE) // 110
        new_cmd = SDRAM_CMD_BURST_TERM;
      else if (!sdram_i.RAS && sdram_i.CAS && !sdram_i.WE) // 010
        new_cmd = SDRAM_CMD_PRECHARGE;
      else if (!sdram_i.RAS && !sdram_i.CAS && sdram_i.WE) // 001
        new_cmd = SDRAM_CMD_REFRESH;
      else if (!sdram_i.RAS && !sdram_i.CAS && !sdram_i.WE) // 000
        new_cmd = SDRAM_CMD_LOAD_MODE;
      else
        sc_assert(0); // NOT SURE...
    }

    // Configure SDRAM
    if (new_cmd == SDRAM_CMD_LOAD_MODE) {
      m_burst_type = (tBurstType)(int)sdram_i.ADDR[3];
      m_write_burst_en = (bool)!sdram_i.ADDR[9];
      m_burst_length = (tBurstLength)(int)sdram_i.ADDR.range(2, 0);
      printf("burst_length: %d, write_burst_en: %d\n", m_burst_length, m_write_burst_en);
      m_cas_latency = (int)sdram_i.ADDR.range(6, 4);

      // 绝大多数场景, burst_type 都是 sequential, interleaved
      // 是一个历史遗留特性
      sc_assert(m_burst_type == BURST_TYPE_SEQUENTIAL);
    }
    // Auto refresh
    else if (new_cmd == SDRAM_CMD_REFRESH) // 指的是 sdram 控制器定期给出刷新信号
    {
      // Check no rows open..
      for (unsigned b = 0; b < NUM_BANKS; b++) sc_assert(m_active_row[b] == -1);
    }
    // Row is activated and copied into the row buffer of the bank
    else if (new_cmd == SDRAM_CMD_ACTIVE) {

      bank = sdram_i.BA;
      row = sdram_i.ADDR;

      // A row should not be open
      sc_assert(m_active_row[bank] == -1);

      // Mark row as open
      m_active_row[bank] = row;
    }
    // Read command
    else if (new_cmd == SDRAM_CMD_READ) {
      col = sdram_i.ADDR;
      bank = sdram_i.BA;
      row = m_active_row[bank];
      sc_assert(m_active_row[bank] != -1); // A row should be open
      sc_assert(sdram_i.DQM == 0x0); // DQM expected to be low
      // Address = RBC
      addr.range(SDRAM_COL_W, 1) = col.range(SDRAM_COL_W - 1, 0);
      addr.range(SDRAM_COL_W + SDRAM_BANK_W, SDRAM_COL_W + 1) = bank;
      addr.range(31, SDRAM_COL_W + SDRAM_BANK_W + 1) = row;
      resp_data[m_cas_latency - 2] = read16((uint32_t)addr);
      addr += 2;
      switch (m_burst_length) {
      default:
      case BURST_LEN_1:
        m_burst_read = 1 - 1;
        break;
      case BURST_LEN_2:
        m_burst_read = 2 - 1;
        break;
      case BURST_LEN_4:
        m_burst_read = 4 - 1;
        break;
      case BURST_LEN_8:
        m_burst_read = 8 - 1;
        break;
      }
    }
    // Write command
    else if (new_cmd == SDRAM_CMD_WRITE) {

      col = sdram_i.ADDR;
      bank = sdram_i.BA;
      row = m_active_row[bank];

      sc_assert(m_active_row[bank] != -1);

      // Address = RBC
      addr.range(SDRAM_COL_W, 1) = col.range(SDRAM_COL_W - 1, 0);
      addr.range(SDRAM_COL_W + SDRAM_BANK_W, SDRAM_COL_W + 1) = bank;
      addr.range(31, SDRAM_COL_W + SDRAM_BANK_W + 1) = row;

      write16((uint32_t)addr, (uint16_t)sdram_i.DATA_OUTPUT, (uint8_t)sdram_i.DQM);
      addr += 2;

      // Configure remaining burst length
      if (m_write_burst_en) {
        switch (m_burst_length) {
        default:
        case BURST_LEN_1:
          m_burst_write = 1 - 1;
          break;
        case BURST_LEN_2:
          m_burst_write = 2 - 1;
          break;
        case BURST_LEN_4:
          m_burst_write = 4 - 1;
          break;
        case BURST_LEN_8:
          m_burst_write = 8 - 1;
          break;
        }
      } else
        m_burst_write = 0;

    }
    // Row is precharged and stored back into the memory array
    else if (new_cmd == SDRAM_CMD_PRECHARGE) {
      // All banks
      if (sdram_i.ADDR[10]) {
        // Close rows
        for (unsigned i = 0; i < NUM_BANKS; i++)
          m_active_row[i] = -1;
      }
      // Specified bank
      else {
        bank = sdram_i.BA;

        // Close specific row
        m_active_row[bank] = -1;
      }
    }
    // Terminate read or write burst
    else if (new_cmd == SDRAM_CMD_BURST_TERM) {
      m_burst_write = 0;
      m_burst_read = 0;

    }

    // WRITE: Burst continuation...
    if (m_burst_write > 0 && new_cmd == SDRAM_CMD_NOP) {
      write16((uint32_t)addr, (uint16_t)sdram_i.DATA_OUTPUT, (uint8_t)sdram_i.DQM);
      addr += 2;
      m_burst_write -= 1;
    }
    // READ: Burst continuation
    else if (m_burst_read > 0 && new_cmd == SDRAM_CMD_NOP) {
      resp_data[m_cas_latency - 2] = read16((uint32_t)addr);
      addr += 2;
      m_burst_read -= 1;
    }

    sdram_o.DATA_INPUT = resp_data[0];

    // Shuffle read data
    for (int i = 1; i < sizeof(resp_data) / sizeof(resp_data[0]); i++)
      resp_data[i - 1] = resp_data[i];

    sdram_out.write(sdram_o);
    wait();
  }
}
//-----------------------------------------------------------------
// write16: Write a 16-bit word to memory with DQM masking
//-----------------------------------------------------------------
void tb_sdram_mem::write16(uint32_t addr, uint16_t data, uint8_t dqm) {
  if (!(dqm & 1))
    tb_memory::write(addr, data & 0xFF);
  if (!(dqm & 2))
    tb_memory::write(addr + 1, (data >> 8) & 0xFF);
}
//-----------------------------------------------------------------
// read16: Read a 16-bit word from memory
//-----------------------------------------------------------------
uint16_t tb_sdram_mem::read16(uint32_t addr) {
  uint16_t data = 0;
  data |= tb_memory::read(addr);
  data |= ((uint16_t)tb_memory::read(addr + 1)) << 8;
  return data;
}
//-----------------------------------------------------------------
// write: Byte write
//-----------------------------------------------------------------
void tb_sdram_mem::write(uint32_t addr, uint8_t data) {
  tb_memory::write(addr, data);
}
//-----------------------------------------------------------------
// read: Byte read
//-----------------------------------------------------------------
uint8_t tb_sdram_mem::read(uint32_t addr) { return tb_memory::read(addr); }
