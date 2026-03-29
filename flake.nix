{
  description = "core_sdram_axi4 开发环境 ( Mill 0.12、Chisel、Verilator、SystemC )";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = { };
          overlays = [
            (import ./nix/overlay.nix)
          ];
        };
      in
      {
        devShells.default = pkgs.mkShell {
          name = "core-sdram-axi4";
          packages = with pkgs; [
            # chisel
            mill_0_12_4
            jdk21
            metals
            circt

            # gcc
            verilator
            systemc

            #
            gtkwave
          ];

          VERILATOR_SRC = "${pkgs.verilator}/share/verilator/include";
          SYSTEMC_HOME = "${pkgs.systemc}";
          SYSTEMC_LIBDIR = "${pkgs.systemc}/lib";
        };
      }
    );
}
