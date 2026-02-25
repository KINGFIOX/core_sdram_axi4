import "DPI-C" function shortint sdram_read(int addr);
import "DPI-C" function void sdram_write(int addr, shortint data);

module sdram_cmd(
  input clock,
  input valid,
  input wen,
  input [31:0] addr,
  input [15:0] wdata,
  output reg [15:0] rdata
);
  always@(posedge clock) begin
    rdata <= 0;
    if (valid) begin
      if (wen) begin
        sdram_write(addr, wdata);
      end else begin
        rdata <= sdram_read(addr);
      end
    end
  end
endmodule
