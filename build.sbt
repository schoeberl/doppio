
// scalaVersion := "3.0.0"
scalaVersion := "2.13.10"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
)

/*
val chiselVersion = "3.5.5"
addCompilerPlugin("edu.berkeley.cs" %% "chisel3-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "edu.berkeley.cs" %% "chisel3" % chiselVersion
*/
scalaVersion := "2.13.16"
val chiselVersion = "7.0.0-RC3"
addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
