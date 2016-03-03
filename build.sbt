import org.scalajs.sbtplugin.cross.CrossType
import sbt.Keys._

val commonSettings: Seq[Setting[_]] = Seq(
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
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  sonatypeProfileName := "com.codemettle"
)

lazy val root = project in file(".") settings (commonSettings ++ Seq(
  publishArtifact := false,
  publishArtifact in Compile := false
)) aggregate(jsactor, sharedJVM, sharedJS, bridgeServer, bridgeClient, jsactorLoglevel, sharedUPickleJVM,
  sharedUPickleJS, bridgeServerUPickle, bridgeClientUPickle, sharedCirceJVM, sharedCirceJS, bridgeServerCirce,
  bridgeClientCirce, sharedBooPickleJVM, sharedBooPickleJS, bridgeServerBooPickle, bridgeClientBooPickle)

lazy val jsactor = project in file("jsactor") settings (commonSettings ++ Seq(
  name := "jsactor",
  persistLauncher in Compile := true,
  persistLauncher in Test := false,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.0"
  )
)) enablePlugins ScalaJSPlugin

lazy val jsactorBridgeShared = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared"
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedJVM = jsactorBridgeShared.jvm
lazy val sharedJS = jsactorBridgeShared.js

lazy val jsactorBridgeSharedUPickle = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared-upickle")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-upickle",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "upickle" % "0.3.7"
  )
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedUPickleJVM = jsactorBridgeSharedUPickle.jvm dependsOn sharedJVM
lazy val sharedUPickleJS = jsactorBridgeSharedUPickle.js dependsOn sharedJS

lazy val jsactorBridgeSharedCirce = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared-circe")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-circe",
  libraryDependencies ++= Seq(
    "io.circe" %%% "circe-core" % "0.3.0",
    "io.circe" %%% "circe-generic" % "0.3.0",
    "io.circe" %%% "circe-parser" % "0.3.0"
  )
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedCirceJVM = jsactorBridgeSharedCirce.jvm dependsOn sharedJVM
lazy val sharedCirceJS = jsactorBridgeSharedCirce.js dependsOn sharedJS

lazy val jsactorBridgeSharedBooPickle = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared-boopickle")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-boopickle",
  libraryDependencies ++= Seq(
    "me.chrons" %%% "boopickle" % "1.1.2"
  )
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedBooPickleJVM = jsactorBridgeSharedBooPickle.jvm dependsOn sharedJVM
lazy val sharedBooPickleJS = jsactorBridgeSharedBooPickle.js dependsOn sharedJS

lazy val bridgeServer = project in file("jsactor-bridge-server") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-server",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.10"
  )
)) dependsOn sharedJVM

lazy val bridgeServerUPickle = project in file ("jsactor-bridge-server-upickle") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-server-upickle"
)) dependsOn (bridgeServer, sharedUPickleJVM)

lazy val bridgeServerCirce = project in file("jsactor-bridge-server-circe") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-server-circe"
)) dependsOn (bridgeServer, sharedCirceJVM)

lazy val bridgeServerBooPickle = project in file("jsactor-bridge-server-boopickle") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-server-boopickle"
)) dependsOn (bridgeServer, sharedBooPickleJVM)

lazy val bridgeClient = project in file("jsactor-bridge-client") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client"
)) dependsOn (sharedJS, jsactor) enablePlugins ScalaJSPlugin

lazy val bridgeClientUPickle = project in file("jsactor-bridge-client-upickle") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client-upickle"
)) dependsOn (bridgeClient, sharedUPickleJS) enablePlugins ScalaJSPlugin

lazy val bridgeClientCirce = project in file("jsactor-bridge-client-circe") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client-circe"
)) dependsOn (bridgeClient, sharedCirceJS) enablePlugins ScalaJSPlugin

lazy val bridgeClientBooPickle = project in file("jsactor-bridge-client-boopickle") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client-boopickle"
)) dependsOn (bridgeClient, sharedBooPickleJS) enablePlugins ScalaJSPlugin

lazy val jsactorLoglevel = project in file("jsactor-loglevel") settings (commonSettings ++ Seq(
  name := "jsactor-loglevel",
  libraryDependencies += "com.codemettle.scalajs" %%% "loglevel" % "1.0.0"
)) dependsOn jsactor enablePlugins ScalaJSPlugin
