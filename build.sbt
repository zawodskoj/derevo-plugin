import org.jetbrains.sbtidea.Keys._

lazy val plugin =
  project.in(file("."))
    .enablePlugins(SbtIdeaPlugin)
    .settings(
      version := "0.0.1-SNAPSHOT",
      scalaVersion := "2.13.2",
      ThisBuild / intellijPluginName := "Derevo Implicits Injector",
      ThisBuild / intellijBuild      := "211.7442.40",
      ThisBuild / intellijPlatform   := IntelliJPlatform.IdeaCommunity,
      Global    / intellijAttachSources := true,
      Compile / javacOptions ++= "--release" :: "11" :: Nil,
      intellijPlugins += "com.intellij.properties".toPlugin,
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
      unmanagedResourceDirectories in Test    += baseDirectory.value / "testResources",
      intellijPlugins += "org.intellij.scala".toPlugin,
    )
