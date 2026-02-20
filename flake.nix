{
  description = "SDRAM AXI4 Controller - Verilator/SystemC Simulation";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            systemc
            verilator
            gnumake
            gcc
            ccache
            gtkwave
            clang-tools
            bear
          ];

          VERILATOR_SRC = "${pkgs.verilator}/share/verilator/include";
          SYSTEMC_HOME = "${pkgs.systemc}";
          SYSTEMC_LIBDIR = "${pkgs.systemc}/lib";
          CXX = "ccache g++";

          shellHook = ''
            export LD_LIBRARY_PATH="$PWD/tb/lib:${pkgs.systemc}/lib:$LD_LIBRARY_PATH"

            echo "=== SDRAM AXI4 仿真环境已就绪 ==="
            echo "  cd tb && make run   — 编译并运行仿真"
            echo "  cd tb && make view  — 用 GTKWave 查看波形"
          '';
        };
      });
}
