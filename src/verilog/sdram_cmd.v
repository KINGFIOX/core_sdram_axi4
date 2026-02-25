import "DPI-C" function void sdram_read(input int addr, output short data);
import "DPI-C" function void sdram_write(input int addr, input short data);

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
        sdram_read(addr, rdata);
      end
    end
  end
endmodule
