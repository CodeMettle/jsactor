enablePlugins(ScalaJSPlugin)

organization := "com.codemettle.jsactor"

name := "jsactor"

scalaVersion := "2.11.6"

scalacOptions := Seq("-Xlint", "-unchecked", "-deprecation", "-feature")

persistLauncher in Compile := true

persistLauncher in Test := false

resolvers ++= Seq(
  Resolver.mavenLocal,
  "cm" at "http://maven.codemettle.com/archiva/repository/internal",
  "cm/snaps" at "http://maven.codemettle.com/archiva/repository/snapshots"
)

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.8.0",
  "com.codemettle.weblogging" %%% "weblogging" % "0.1.0"
)

// Publish / Release
releaseSettings

scmInfo := Some(
  ScmInfo(url("http://git.codemettle.com/steven/jsactor"),
    "scm:git:http://git.codemettle.com/steven/jsactor.git",
    Some("scm:git:git@git.codemettle.com:steven/jsactor.git")))

pomExtra := {
  <developers>
    <developer>
      <name>Steven Scott</name>
      <email>steven@codemettle.com</email>
      <url>http://git.codemettle.com/u/steven</url>
    </developer>
  </developers>
}

publishMavenStyle := true

publishTo := Some(Resolver.ssh("CodeMettle Maven", "maven.codemettle.com",
  s"archiva/data/repositories/${if (isSnapshot.value) "snapshots" else "internal"}/") as
  ("archiva", Path.userHome / ".ssh" / "id_rsa"))
