{ lib, newScope }:
lib.makeScope newScope (
  scope:
  let
    designTarget = "SDRAMApbSimTop";
  in
  {
    dependencies = scope.callPackage ../dependencies { };

    sdram-compiled = scope.callPackage ./sdram.nix { target = designTarget; };
  }
)
