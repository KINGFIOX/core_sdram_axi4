{
  description = "core_sdram_axi4 开发环境 ( Mill 0.12、Chisel、Verilator、SystemC )";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";

  outputs = { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
      pkgsFor = system: import nixpkgs {
        inherit system;
        config = { };
        overlays = [
          (import ./nix/overlay.nix)
        ];
      };
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = pkgsFor system;
          verilator = pkgs.verilator;
          systemc = pkgs.systemc;
        in
        {
          default = pkgs.mkShell {
            name = "core-sdram-axi4";
            nativeBuildInputs = with pkgs; [
              # chisel
              mill_0_12_4
              jdk17
              metals
              circt

              # gcc
              verilator
              systemc
              ccache
              gcc
              gdb

              #
              gtkwave
            ];
            shellHook = ''
              export VERILATOR_SRC="${verilator}/share/verilator/include"
              export SYSTEMC_HOME="${systemc}"
              export SYSTEMC_LIBDIR="${systemc}/lib"
            '';
          };
        });
    };
}
