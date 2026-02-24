#define MEM_SIZE (512 * 1024)

static char sdram_mem[MEM_SIZE];

void sdram_read(int addr, char *data) {
  char byte = sdram_mem[addr];
  *data = byte;
}

void sdram_write(int addr, char data) {
  sdram_mem[addr] = data;
}