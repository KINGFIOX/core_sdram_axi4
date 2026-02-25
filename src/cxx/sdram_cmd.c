#include <stdint.h>
#include <stdio.h>

#define MEM_SIZE (512 * 1024)

static uint8_t sdram_mem[MEM_SIZE];

uint16_t sdram_read(uint32_t addr) {
  uint8_t half_1 = sdram_mem[addr];
  uint8_t half_2 = sdram_mem[addr + 1];
  uint16_t half = (half_2 << 8) | half_1;
  printf("sdram_read: addr = %08x, data = %04x\n", addr, half);
  return half;
}

void sdram_write(uint32_t addr, uint8_t byte) {
  sdram_mem[addr] = byte;
  printf("sdram_write: addr = %08x, data = %02x\n", addr, byte);
}