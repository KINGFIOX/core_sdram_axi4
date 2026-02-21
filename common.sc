import mill._
import mill.scalalib._

trait HasChisel extends ScalaModule {
  def chiselModule: Option[ScalaModule]

  override def moduleDeps = super.moduleDeps ++ chiselModule

  def chiselPluginJar: T[Option[PathRef]]

  override def scalacOptions = T(
    super.scalacOptions() ++ chiselPluginJar().map(path => s"-Xplugin:${path.path}") ++ Seq(
      "-Ymacro-annotations",
      "-deprecation",
      "-feature",
      "-language:reflectiveCalls",
      "-language:existentials",
      "-language:implicitConversions"
    )
  )

  override def scalacPluginClasspath: T[Agg[PathRef]] = T(super.scalacPluginClasspath() ++ chiselPluginJar())

  def chiselIvy: Option[Dep]

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)

  def chiselPluginIvy: Option[Dep]

  override def scalacPluginIvyDeps: T[Agg[Dep]] = T(
    super.scalacPluginIvyDeps() ++ chiselPluginIvy.map(Agg(_)).getOrElse(Agg.empty[Dep])
  )
}

trait ElaboratorModule extends ScalaModule with HasChisel {
  def generators:       Seq[ScalaModule]
  def mlirInstallPath:  T[os.Path]
  def circtInstallPath: T[os.Path]
  override def moduleDeps = super.moduleDeps ++ generators
  def mainargsIvy: Dep
  override def ivyDeps      = T(super.ivyDeps() ++ Seq(mainargsIvy))
  override def javacOptions = T(super.javacOptions() ++ Seq("--enable-preview", "--release", "21"))
  def libraryPaths          = T(Seq(mlirInstallPath() / "lib", circtInstallPath() / "lib").map(PathRef(_)))
  override def forkArgs: T[Seq[String]] = T(
    super.forkArgs() ++ Seq(
      "--enable-native-access=ALL-UNNAMED",
      "--enable-preview",
      s"-Djava.library.path=${libraryPaths().map(_.path).mkString(":")}"
    )
  )
}
