import org.scalajs.sbtplugin.cross.CrossType
import sbt.Keys._

val commonSettings: Seq[Setting[_]] = Seq(
  organization := "com.codemettle.jsactor",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  scalaVersion := crossScalaVersions.value.last,
  scalacOptions := Seq("-Xlint", "-unchecked", "-deprecation", "-feature"),
  scalacOptions += {
    CrossVersion partialVersion scalaVersion.value match {
      case Some((x, y)) if x >= 2 && y >= 12 ⇒ "-target:jvm-1.8"
      case _ ⇒ "-target:jvm-1.6"
    }
  },
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
  releaseCrossBuild := true,
  publishMavenStyle := true,
  sonatypeProfileName := "com.codemettle"
)

lazy val jsactorProject = project in file(".") settings (commonSettings ++ Seq(
  publishArtifact := false,
  publishArtifact in Compile := false
)) aggregate(sharedJVM, sharedJS, bridgeServer, bridgeClient, sharedUPickleJVM,
  sharedUPickleJS, bridgeServerUPickle, bridgeClientUPickle, sharedCirceJVM, sharedCirceJS, bridgeServerCirce,
  bridgeClientCirce, sharedBooPickleJVM, sharedBooPickleJS, bridgeServerBooPickle, bridgeClientBooPickle,
  sharedModelScalaPBJVM, sharedModelScalaPBJS, sharedScalaPBJVM, sharedScalaPBJS, bridgeServerScalaPB, bridgeClientScalaPB)

lazy val jsactorBridgeShared = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared"
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedJVM = jsactorBridgeShared.jvm
lazy val sharedJS = jsactorBridgeShared.js

lazy val jsactorBridgeSharedUPickle = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared-upickle")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-upickle",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "upickle" % "0.4.4"
  )
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedUPickleJVM = jsactorBridgeSharedUPickle.jvm dependsOn sharedJVM
lazy val sharedUPickleJS = jsactorBridgeSharedUPickle.js dependsOn sharedJS

lazy val jsactorBridgeSharedCirce = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared-circe")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-circe",
  libraryDependencies ++= Seq(
    "io.circe" %%% "circe-core" % "0.6.1",
    "io.circe" %%% "circe-generic" % "0.6.1",
    "io.circe" %%% "circe-parser" % "0.6.1"
  )
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedCirceJVM = jsactorBridgeSharedCirce.jvm dependsOn sharedJVM
lazy val sharedCirceJS = jsactorBridgeSharedCirce.js dependsOn sharedJS

lazy val jsactorBridgeSharedBooPickle = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared-boopickle")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-boopickle",
  libraryDependencies ++= Seq(
    "io.suzaku" %%% "boopickle" % "1.2.6"
  )
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedBooPickleJVM = jsactorBridgeSharedBooPickle.jvm dependsOn sharedJVM
lazy val sharedBooPickleJS = jsactorBridgeSharedBooPickle.js dependsOn sharedJS

lazy val jsactorBridgeSharedScalaPBModel = (crossProject crossType CrossType.Pure in file ("jsactor-bridge-shared-scalapb-model")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-scalapb-model",
  libraryDependencies ++= Seq(
    "com.thesamet.scalapb" %%% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion
  ),
  PB.targets in Compile := Seq(
    scalapb.gen(flatPackage = true) → (sourceManaged in Compile).value
  ),
  PB.protoSources in Compile := Seq(file("jsactor-bridge-shared-scalapb-model") / "src" / "main" / "protobuf")
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedModelScalaPBJVM = jsactorBridgeSharedScalaPBModel.jvm
lazy val sharedModelScalaPBJS = jsactorBridgeSharedScalaPBModel.js

lazy val jsactorBridgeSharedScalaPB = (crossProject crossType CrossType.Pure in file("jsactor-bridge-shared-scalapb")) settings (commonSettings ++ Seq(
  name := "jsactor-bridge-shared-scalapb"
): _*) jsConfigure (_ enablePlugins ScalaJSPlugin)

lazy val sharedScalaPBJVM = jsactorBridgeSharedScalaPB.jvm dependsOn (sharedJVM, sharedModelScalaPBJVM)
lazy val sharedScalaPBJS = jsactorBridgeSharedScalaPB.js dependsOn (sharedJS, sharedModelScalaPBJS)

lazy val bridgeServer = project in file("jsactor-bridge-server") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-server",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.5.11"
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

lazy val bridgeServerScalaPB = project in file("jsactor-bridge-server-scalapb") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-server-scalapb"
)) dependsOn (bridgeServer, sharedScalaPBJVM)

lazy val bridgeClient = project in file("jsactor-bridge-client") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client",
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.3",
    "org.akka-js" %%% "akkajsactor" % "1.2.5.11"
  )
)) dependsOn sharedJS enablePlugins ScalaJSPlugin

lazy val bridgeClientUPickle = project in file("jsactor-bridge-client-upickle") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client-upickle"
)) dependsOn (bridgeClient, sharedUPickleJS) enablePlugins ScalaJSPlugin

lazy val bridgeClientCirce = project in file("jsactor-bridge-client-circe") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client-circe"
)) dependsOn (bridgeClient, sharedCirceJS) enablePlugins ScalaJSPlugin

lazy val bridgeClientBooPickle = project in file("jsactor-bridge-client-boopickle") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client-boopickle"
)) dependsOn (bridgeClient, sharedBooPickleJS) enablePlugins ScalaJSPlugin

lazy val bridgeClientScalaPB = project in file("jsactor-bridge-client-scalapb") settings (commonSettings ++ Seq(
  name := "jsactor-bridge-client-scalapb"
)) dependsOn (bridgeClient, sharedScalaPBJS) enablePlugins ScalaJSPlugin
