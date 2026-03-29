package sdram

import chisel3.RawModule

object Elaborate extends App {
  val targetDir = if (args.length > 0) args(0) else "build/rtl"

  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new SDRAMAxiSimTop,
    args = Array("--target-dir", targetDir),
    firtoolOpts = Array(
      "-O=release",
      "--preserve-values=all",
      "--lowering-options=verifLabels,omitVersionComment",
      "--strip-debug-info"
    )
  )
}
