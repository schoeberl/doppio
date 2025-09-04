
// scalaVersion := "3.0.0"
scalaVersion := "2.13.10"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
)

val chiselVersion = "3.5.5"
addCompilerPlugin("edu.berkeley.cs" %% "chisel3-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "edu.berkeley.cs" %% "chisel3" % chiselVersion

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
