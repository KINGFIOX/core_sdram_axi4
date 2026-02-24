import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._

import $file.common
import $file.`rocket-chip`.dependencies.hardfloat.{common => hardfloatCommon}
import $file.`rocket-chip`.dependencies.cde.{common => cdeCommon}
import $file.`rocket-chip`.dependencies.diplomacy.{common => diplomacyCommon}
import $file.`rocket-chip`.{common => rocketChipCommon}

val defaultScalaVersion = "2.13.18"
val pwd = os.Path(sys.env("MILL_WORKSPACE_ROOT"))

object v {
  val chisel       = ivy"org.chipsalliance::chisel:7.6.0"
  val chiselPlugin = ivy"org.chipsalliance:chisel-plugin_${defaultScalaVersion}:7.6.0"
}

trait HasThisChisel extends SbtModule {
  def chiselModule: Option[ScalaModule] = None
  def chiselPluginJar: T[Option[PathRef]] = T(None)
  def chiselIvy: Option[Dep] = Some(v.chisel)
  def chiselPluginIvy: Option[Dep] = Some(v.chiselPlugin)
  override def scalaVersion = defaultScalaVersion
  override def scalacOptions = T(
    super.scalacOptions() ++ Seq(
      "-language:reflectiveCalls",
      "-Ymacro-annotations",
      "-Ytasty-reader",
    )
  )
  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)
  override def scalacPluginIvyDeps = T(
    super.scalacPluginIvyDeps() ++ chiselPluginIvy.map(Agg(_)).getOrElse(Agg.empty[Dep])
  )
}

object rocketchip extends RocketChip
trait RocketChip extends rocketChipCommon.RocketChipModule with HasThisChisel {
  override def scalaVersion: T[String] = T(defaultScalaVersion)
  override def millSourcePath = pwd / "rocket-chip"
  def dependencyPath = pwd / "rocket-chip" / "dependencies"
  def macrosModule = macros
  def hardfloatModule = hardfloat
  def cdeModule = cde
  def diplomacyModule = diplomacy
  def diplomacyIvy = None
  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.5.0"
  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.5"

  object macros extends Macros
  trait Macros extends rocketChipCommon.MacrosModule with SbtModule {
    def scalaVersion: T[String] = T(defaultScalaVersion)
    def scalaReflectIvy = ivy"org.scala-lang:scala-reflect:${defaultScalaVersion}"
  }

  object hardfloat extends Hardfloat
  trait Hardfloat extends hardfloatCommon.HardfloatModule with HasThisChisel {
    override def scalaVersion: T[String] = T(defaultScalaVersion)
    override def millSourcePath = dependencyPath / "hardfloat" / "hardfloat"
  }

  object cde extends CDE
  trait CDE extends cdeCommon.CDEModule with ScalaModule {
    def scalaVersion: T[String] = T(defaultScalaVersion)
    override def millSourcePath = dependencyPath / "cde" / "cde"
  }

  object diplomacy extends Diplomacy
  trait Diplomacy extends diplomacyCommon.DiplomacyModule with ScalaModule {
    override def scalaVersion: T[String] = T(defaultScalaVersion)
    override def millSourcePath = dependencyPath / "diplomacy" / "diplomacy"
    def chiselModule: Option[ScalaModule] = None
    def chiselPluginJar: T[Option[PathRef]] = T(None)
    def chiselIvy: Option[Dep] = Some(v.chisel)
    def chiselPluginIvy: Option[Dep] = Some(v.chiselPlugin)
    def cdeModule = cde
    def sourcecodeIvy = ivy"com.lihaoyi::sourcecode:0.3.1"
  }
}

object scala extends SDRAMDesign
trait SDRAMDesign extends common.HasChisel with ScalafmtModule {
  def scalaVersion = T(defaultScalaVersion)

  override def sources = T.sources(pwd / "src" / "scala")

  def chiselModule    = None
  def chiselPluginJar = T(None)
  def chiselPluginIvy = Some(v.chiselPlugin)
  def chiselIvy       = Some(v.chisel)

  override def moduleDeps = super.moduleDeps ++ Seq(rocketchip)
}
