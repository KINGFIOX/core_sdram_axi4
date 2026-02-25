{
  description = "SDRAM AXI4 Controller - Chisel/SystemC Simulation";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts = {
      url = "github:hercules-ci/flake-parts";
      inputs.nixpkgs-lib.follows = "nixpkgs";
    };
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    mill-ivy-fetcher.url = "github:Avimitin/mill-ivy-fetcher";
  };

  outputs =
    inputs@{
      self,
      nixpkgs,
      flake-parts,
      ...
    }:
    let
      overlay = import ./nix/overlay.nix;
    in
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];

      imports = [
        inputs.treefmt-nix.flakeModule
      ];

      flake.overlays.default = overlay;

      perSystem =
        { system, pkgs, ... }:
        {
          _module.args.pkgs = import nixpkgs {
            inherit system;
            overlays = with inputs; [
              mill-ivy-fetcher.overlays.default
              overlay
            ];
          };

          legacyPackages = pkgs;

          treefmt = {
            projectRootFile = "flake.nix";
            programs.scalafmt = {
              enable = true;
              includes = [ "*.mill" ];
            };
            programs.nixfmt = {
              enable = true;
              excludes = [ "*/generated.nix" ];
            };
          };

          devShells.default =
            with pkgs;
            mkShell (
              {
                inputsFrom = [ sdram.sdram-compiled ];
                packages = [
                  nixd
                  nvfetcher
                  metals

                  systemc
                  verilator
                  gnumake
                  gcc
                  ccache
                  gtkwave
                  clang-tools
                  bear
                  gdb
                  python3
                  python3Packages.pygments
                  curl
                ];

                CC = "ccache gcc";
                CXX = "ccache g++";
                VERILATOR_SRC = "${verilator}/share/verilator/include";
                SYSTEMC_HOME = "${systemc}";
                SYSTEMC_LIBDIR = "${systemc}/lib";
              }
              // sdram.sdram-compiled.env
              // {
                shellHook = ''
                  if [[ ! -f "build.mill" && ! -f "build.sc" ]]; then
                    echo "No build.mill or build.sc file found, exit" >&2
                    return 1
                  fi
                  mkdir -p out
                  NIX_BUILD_TOP="$(realpath out)"
                  runHook preUnpack
                  runHook postUnpack

                  export LD_LIBRARY_PATH="$PWD/build/lib:${systemc}/lib:''${LD_LIBRARY_PATH:-}"

                  GDB_DASHBOARD="$PWD/.gdb-dashboard"
                  if [ ! -f "$GDB_DASHBOARD" ]; then
                    echo "Downloading gdb-dashboard..."
                    curl -sL https://raw.githubusercontent.com/cyrus-and/gdb-dashboard/master/.gdbinit -o "$GDB_DASHBOARD"
                  fi

                  echo "=== SDRAM AXI4 仿真环境已就绪 ==="
                  echo "  Mill version: $(mill --version 2>&1 | head -1)"
                  echo "  make elaborate   — Chisel 生成 SystemVerilog"
                  echo "  make run BUS=apb — 编译并运行 APB 仿真"
                  echo "  make run BUS=axi — 编译并运行 AXI4 仿真"
                  echo "  make run         — 默认编译运行 APB 仿真"
                  echo "  make gdb         — 用 GDB + Dashboard 调试"
                  echo "  make view        — 用 GTKWave 查看波形"
                '';
              }
            );
        };
    };
}
