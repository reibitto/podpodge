import sbt._

object CompilerPlugins {

  lazy val BaseCompilerPlugins = Seq(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )
}
