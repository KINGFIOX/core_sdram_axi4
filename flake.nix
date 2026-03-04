{
  description = "core_sdram_axi4 开发环境（Mill 0.12、Chisel、Verilator、SystemC）";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";

  outputs = { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
      pkgsFor = system: import nixpkgs {
        inherit system;
        config = { };
        overlays = [ (final: prev: {
          # Nixpkgs 中 mill 默认为 1.x，本项目需要 0.12
          # Nixpkgs 默认 Mill 为 1.x；0.12.14 起 GitHub 不再提供 assembly，故使用 0.12.0
          mill = final.stdenv.mkDerivation rec {
            pname = "mill";
            version = "0.12.0";
            assemblyName = "0.12.0-11-7e7a54-assembly";
            src = final.fetchurl {
              url = "https://github.com/com-lihaoyi/mill/releases/download/${version}/${assemblyName}";
              hash = "sha256-k/qYHEaSLYKMWKXOtv6PjiFt1a9oqL7ZlIkdUk139e8=";
            };
            nativeBuildInputs = [ final.makeWrapper ];
            dontUnpack = true;
            dontConfigure = true;
            dontBuild = true;
            preferLocalBuild = true;
            installPhase = ''
              runHook preInstall
              install -Dm555 "$src" "$out/bin/.mill-wrapped"
              makeWrapper "$out/bin/.mill-wrapped" "$out/bin/mill" \
                --prefix PATH : "${final.jdk17}/bin" \
                --set JAVA_HOME "${final.jdk17}"
              runHook postInstall
            '';
            doInstallCheck = true;
            installCheckPhase = ''
              $out/bin/mill --help > /dev/null
            '';
            meta = with final.lib; {
              homepage = "https://com-lihaoyi.github.io/mill/";
              license = licenses.mit;
              description = "Mill build tool (0.12.x for Scala/Chisel)";
              mainProgram = "mill";
              platforms = final.lib.platforms.all;
            };
          };
        }) ];
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
              mill
              jdk17
              verilator
              systemc
              ccache
              gcc
              gdb
              gtkwave
              git
            ];
            shellHook = ''
              export VERILATOR_SRC="${verilator}/share/verilator/include"
              export SYSTEMC_HOME="${systemc}"
              export SYSTEMC_LIBDIR="${systemc}/lib"
              export MILL_WORKSPACE_ROOT="$(pwd)"
              echo "开发环境已加载: Mill 0.12, JDK 17, Verilator, SystemC, ccache, gtkwave, gdb"
            '';
          };
        });

      packages = forAllSystems (system:
        let pkgs = pkgsFor system;
        in {
          mill = pkgs.mill;
        });
    };
}
