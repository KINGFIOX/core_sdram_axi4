{ lib, newScope }:
lib.makeScope newScope (
  scope:
  let
    designTarget = "SDRAMSimTop";
  in
  {
    dependencies = scope.callPackage ../dependencies { };

    sdram-compiled = scope.callPackage ./sdram.nix { target = designTarget; };
  }
)
