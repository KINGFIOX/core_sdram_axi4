import "DPI-C" function void sdram_read(input int addr, output byte data);
import "DPI-C" function void sdram_write(input int addr, input byte data);

module psram_cmd(
  input clock,
  input valid,
  input wen,
  input [31:0] addr,
  input [15:0] wdata,
  output reg [15:0] rdata
);
  always@(posedge clock) begin
    if (valid) begin
      if (wen) begin
        sdram_write(addr, wdata);
      end else begin
        sdram_read(addr, rdata);
      end
    end
  end
endmodule
