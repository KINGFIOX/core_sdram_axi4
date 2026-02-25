#include <stdint.h>

#define MEM_SIZE (512 * 1024)

static uint8_t sdram_mem[MEM_SIZE];

void sdram_read(uint32_t addr, uint16_t *half) {
  uint8_t half_1 = sdram_mem[addr];
  uint8_t half_2 = sdram_mem[addr + 1];
  *half = (half_2 << 8) | half_1;
}

void sdram_write(uint32_t addr, uint16_t half) {
  uint8_t half_1 = half & 0xFF;
  uint8_t half_2 = (half >> 8) & 0xFF;
  sdram_mem[addr] = half_1;
  sdram_mem[addr + 1] = half_2;
}