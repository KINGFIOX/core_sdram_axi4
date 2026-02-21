{
  lib,
  callPackage,
  newScope,
  writeShellApplication,
  runCommand,
  publishMillJar,
  git,
  mill,
  mill-ivy-fetcher,
  ...
}:
let
  dependencies = callPackage ./_sources/generated.nix { };
in
lib.makeScope newScope (scope: {
  ivy-chisel = publishMillJar {
    name = "chisel-snapshot";
    src = dependencies.chisel.src;

    lockFile = ./locks/chisel-lock.nix;

    publishTargets = [
      "unipublish"
    ];

    nativeBuildInputs = [
      git
    ];

    passthru.bump = writeShellApplication {
      name = "bump-chisel-mill-lock";

      runtimeInputs = [
        mill
        mill-ivy-fetcher
      ];

      text = ''
        mif run -p "${dependencies.chisel.src}" -o ./nix/dependencies/locks/chisel-lock.nix "$@"
      '';
    };
  };

  ivyLocalRepo =
    runCommand "build-coursier-env"
      {
        buildInputs = with scope; [
          ivy-chisel.setupHook
        ];
      }
      ''
        runHook preUnpack
        runHook postUnpack
        cp -r "$NIX_COURSIER_DIR" "$out"
      '';
})
