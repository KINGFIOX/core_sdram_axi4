package sdram

import chisel3.RawModule

object Elaborate extends App {
  val targetDir = if (args.length > 0) args(0) else "build/rtl"
  val bus       = if (args.length > 1) args(1) else "apb"

  val gen: () => RawModule = bus match {
    case "apb" => () => new SDRAMApbSimTop
    case "axi" => () => new SDRAMAxiSimTop
    case other => throw new IllegalArgumentException(s"Unknown bus type: $other (use 'axi' or 'apb')")
  }

  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    gen(),
    args = Array("--target-dir", targetDir),
    firtoolOpts = Array(
      "-O=release",
      "--preserve-values=all",
      "--lowering-options=verifLabels,omitVersionComment",
      "--strip-debug-info"
    )
  )
}
