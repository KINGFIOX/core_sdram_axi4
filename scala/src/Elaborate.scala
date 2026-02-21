package org.chipsalliance.sdram

object Elaborate extends App {
  val targetDir = if (args.length > 0) args(0) else "build/rtl"
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new SDRAMSimTop,
    args = Array("--target-dir", targetDir),
    firtoolOpts = Array(
      "-O=release",
      "--preserve-values=all",
      "--lowering-options=verifLabels,omitVersionComment",
      "--strip-debug-info"
    )
  )
}
