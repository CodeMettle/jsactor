import org.scalajs.sbtplugin.cross.CrossType
import sbt.Keys._

val commonSettings: Seq[Setting[_]] = releaseSettings ++ Seq(
  organization := "com.codemettle.jsactor",
  scalaVersion := "2.11.6",
  scalacOptions := Seq("-Xlint", "-unchecked", "-deprecation", "-feature"),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "cm" at "http://maven.codemettle.com/archiva/repository/internal",
    "cm/snaps" at "http://maven.codemettle.com/archiva/repository/snapshots"
  ),
  scmInfo := Some(
    ScmInfo(url("http://git.codemettle.com/steven/jsactor"),
      "scm:git:http://git.codemettle.com/steven/jsactor.git",
      Some("scm:git:git@git.codemettle.com:steven/jsactor.git"))
  ),
  pomExtra := {
    <developers>
      <developer>
        <name>Steven Scott</name>
        <email>steven@codemettle.com</email>
        <url>http://git.codemettle.com/u/steven</url>
      </developer>
    </developers>
  },
  publishMavenStyle := true,
  publishTo := Some(
    Resolver.ssh("CodeMettle Maven", "maven.codemettle.com",
      s"archiva/data/repositories/${if (isSnapshot.value) "snapshots" else "internal"}/") as
        ("archiva", Path.userHome / ".ssh" / "id_rsa")
  )
)

lazy val root = project in file(".") settings (commonSettings ++ Seq(
  publishArtifact := false,
  publishArtifact in Compile := false
)) aggregate (jsactor, sharedJVM, sharedJS, bridgeServer, bridgeClient, jsactorLoglevel)

lazy val jsactor = project in file("jsactor") settings (commonSettings ++ Seq(
  name := "jsactor",
  persistLauncher in Compile := true,
  persistLauncher in Test := false,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.0"
  )
)) enablePlugins ScalaJSPlugin

lazy val jsactorBridgeShared = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "upickle" % "0.2.8"
  )
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedJVM = jsactorBridgeShared.jvm
lazy val sharedJS = jsactorBridgeShared.js

lazy val bridgeServer = project in file("jsactor-bridge-server") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-server",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.10"
  )
)) dependsOn sharedJVM

lazy val bridgeClient = project in file("jsactor-bridge-client") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client"
)) dependsOn (sharedJS, jsactor) enablePlugins ScalaJSPlugin

lazy val jsactorLoglevel = project in file("jsactor-loglevel") settings (commonSettings ++ Seq(
  name := "jsactor-loglevel",
  libraryDependencies += "com.codemettle.scalajs" %%% "loglevel" % "1.0.0"
)) dependsOn jsactor enablePlugins ScalaJSPlugin
