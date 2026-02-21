{
  lib,
  stdenv,
  rtl,
  verilator,
  zlib,
  python3,
  thread-num ? 8,
}:
let
  vName = "V${rtl.target}";
  sdramVerilog = ./../../sdram;
  csrc = ./../../csrc;
in
stdenv.mkDerivation {
  name = "verilated";

  src = rtl;

  nativeBuildInputs = [
    verilator
    python3
  ];

  passthru = {
    inherit (rtl) target;
  };

  meta.mainProgram = vName;

  buildPhase = ''
    runHook preBuild

    echo "[nix] running verilator"
    verilator \
      --timing \
      --threads ${toString thread-num} \
      -O1 \
      --trace \
      --exe \
      --cc -f filelist.f \
      ${sdramVerilog}/sdram_top_apb.v \
      ${sdramVerilog}/core_sdram_axi4/sdram_axi_core.v \
      ${csrc}/sim_main.cpp \
      --top ${rtl.target}

    echo "[nix] building verilated C lib"

    # backup srcs
    mkdir -p $out/share
    cp -r obj_dir $out/share/verilated_src

    # We can't use -C here because the Makefile is generated with relative path
    cd obj_dir
    make -j "$NIX_BUILD_CORES" -f ${vName}.mk ${vName}

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/{include,lib,bin}
    cp *.h $out/include
    cp *.a $out/lib
    cp ${vName} $out/bin

    runHook postInstall
  '';

  # nix fortify hardening add `-O2` gcc flag,
  # we'd like verilator to controll optimization flags, so disable it.
  # `-O2` will make gcc build time in verilating extremely long
  hardeningDisable = [ "fortify" ];
}
