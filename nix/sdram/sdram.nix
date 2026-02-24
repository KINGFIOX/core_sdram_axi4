{
  lib,
  stdenv,
  makeWrapper,
  writeShellApplication,
  jdk21,
  git,

  mill,
  circt,

  dependencies,
  mill-ivy-fetcher,

  target,
}:

let
  self = stdenv.mkDerivation rec {
    name = "sdram";

    src =
      with lib.fileset;
      toSource {
        root = ./../..;
        fileset = unions [
          ./../../build.sc
          ./../../common.sc
          ./../../src/scala
        ];
      };

    buildInputs = with dependencies; [
      ivy-chisel.setupHook
    ];

    nativeBuildInputs = [
      makeWrapper
      mill
      circt
      git
    ];

    passthru = {
      bump = writeShellApplication {
        name = "bump-sdram-mill-lock";
        runtimeInputs = [
          mill
          mill-ivy-fetcher
        ];
        text = ''
          ivyLocal="${dependencies.ivyLocalRepo}"
          export JAVA_TOOL_OPTIONS="''${JAVA_TOOL_OPTIONS:-} -Dcoursier.ivy.home=$ivyLocal -Divy.home=$ivyLocal"

          mif run -p "${src}" -o ./nix/dependencies/locks/sdram-lock.nix "$@"
        '';
      };
      inherit target;
      inherit env;
    };

    shellHook = ''
      if [[ ! -f "build.mill" && ! -f "build.sc" ]]; then
        echo "No build.mill or build.sc file found, exit" >&2
        return 1
      fi

      mkdir -p out
      NIX_BUILD_TOP="$(realpath out)"

      runHook preUnpack
      runHook postUnpack
    '';

    env = { };

    buildPhase = ''
      mill -i scala.compile
    '';

    installPhase = ''
      mkdir -p $out
    '';
  };
in
self
