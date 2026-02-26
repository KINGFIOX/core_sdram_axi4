import "DPI-C" function shortint sdram_read(int addr);
import "DPI-C" function void sdram_write(int addr, byte data);

module sdram_cmd #(
  parameter idx = 0
)(
  input clock,
  input valid,
  input wen,
  input [1:0] dqm_n,
  input [31:0] addr,
  input [15:0] wdata,
  output reg [15:0] rdata
);
  wire [31:0] addrIdx = addr + (idx << 1);
  always@(posedge clock) begin
    rdata <= 0;
    if (valid) begin
      if (wen) begin
        if (!dqm_n[0]) begin
          sdram_write(addrIdx, wdata[7:0]);
        end
        if (!dqm_n[1]) begin
          sdram_write(addrIdx + 1, wdata[15:8]);
        end
      end else begin
        rdata <= sdram_read(addrIdx);
      end
    end
  end
endmodule
