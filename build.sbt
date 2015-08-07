import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import org.scalajs.sbtplugin.cross.CrossType
import sbt.Keys._
import sbtrelease.ReleasePlugin.ReleaseKeys.publishArtifactsAction
import xerial.sbt.Sonatype.SonatypeKeys.profileName

val commonSettings: Seq[Setting[_]] = releaseSettings ++ sonatypeSettings ++ Seq(
  organization := "com.codemettle.jsactor",
  scalaVersion := "2.11.6",
  scalacOptions := Seq("-Xlint", "-unchecked", "-deprecation", "-feature"),
  startYear := Some(2015),
  homepage := Some(url("https://github.com/CodeMettle/jsactor")),
  organizationName := "CodeMettle, LLC",
  organizationHomepage := Some(url("http://www.codemettle.com")),
  licenses += ("Apache License, Version 2.0" → url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/CodeMettle/jsactor"),
      "scm:git:git@github.com:CodeMettle/jsactor.git",
      Some("scm:git:git@github.com:CodeMettle/jsactor.git"))
  ),
  pomExtra := {
    <developers>
      <developer>
        <name>Steven Scott</name>
        <email>steven@codemettle.com</email>
        <url>https://github.com/codingismy11to7/</url>
      </developer>
    </developers>
  },
  publishArtifactsAction := publishSigned.value,
  publishMavenStyle := true,
  profileName := "com.codemettle"
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
    "com.lihaoyi" %%% "upickle" % "0.3.5"
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
