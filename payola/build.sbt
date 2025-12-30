import scala.collection.mutable
import mutable.ListBuffer
import scala.io.Source
import scala.tools.nsc.io
import sbt._
import Keys._
import play.Play.autoImport._
import PayolaBuild._

/**
  * The Payola solution. All projects have to be listed in the aggregate method.
  */
lazy val payolaProject = Project(
    "payola", file("."), settings = payolaSettings
).aggregate(
    s2JsProject, scala2JsonProject, commonProject, domainProject, dataProject, modelProject, webProject
)

lazy val domainProject = Project(
    "domain", file("domain"),
    settings = payolaSettings ++ Seq(
        libraryDependencies ++= Seq(
            "org.scala-lang" % "scala-actors" % scalaVersion.value,
            "org.apache.jena" % "jena-core" % "2.11.1",
            "org.apache.jena" % "jena-arq" % "2.11.1",
            "org.apache.jena" % "jena" % "2.11.0",
            "org.apache.httpcomponents" % "httpclient" % "4.2.4",
            "commons-io" % "commons-io" % "2.4",
            "commons-lang" % "commons-lang" % "2.4"
        )
    )
).dependsOn(
    commonProject
)

lazy val dataProject = Project(
    "data", file("data"),
    settings = payolaSettings ++ Seq(
        libraryDependencies ++= Seq(
            "org.scala-lang" % "scala-actors" % scalaVersion.value,
            "org.squeryl" %% "squeryl" % "0.9.5-7",
            "com.h2database" % "h2" % "1.3.165",
            "mysql" % "mysql-connector-java" % "5.1.18",
            "postgresql" % "postgresql" % "9.1-901.jdbc4",
            "org.apache.derby" % "derby" % "10.8.2.2",
            "org.scalaj" %% "scalaj-http" % "0.3.16"
        )
    )
).dependsOn(
    commonProject, domainProject
)

lazy val modelProject = Project(
    "model", file("model"),
    settings = payolaSettings ++ Seq(
        libraryDependencies ++= Seq(
            "org.scala-lang" % "scala-actors" % scalaVersion.value,
            "org.apache.commons" % "commons-lang3" % "3.1",
            "com.fasterxml.jackson.core" % "jackson-core" % "2.3.0-rc1",
            "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.0-rc1",
            "com.fasterxml.jackson.core" % "jackson-annotations" % "2.3.0-rc1"
        )
    )
).dependsOn(
    commonProject, domainProject, dataProject
)

lazy val webProject = Project(
    "web", file("web"), settings = payolaSettings
).aggregate(
    webSharedProject, webClientProject, webInitializerProject, webServerProject
)

lazy val scala2JsonProject = Project(
    "scala2json", file("scala2json"), settings = payolaSettings
)

lazy val commonProject = ScalaToJsProject(
    "common", "common", WebSettings.javaScriptsDir, payolaSettings
).dependsOn(scala2JsonProject)

lazy val s2JsProject = Project(
    "s2js", file("s2js"), settings = s2JsSettings
).aggregate(
    s2JsAdaptersProject, s2JsCompilerProject, s2JsRuntimeProject
)

lazy val webSharedProject = ScalaToJsProject(
    "shared", "web/shared", WebSettings.javaScriptsDir,
    settings = payolaSettings ++ Seq(
        libraryDependencies ++= Seq(
            "org.scala-lang" % "scala-actors" % scalaVersion.value,
            "com.typesafe" % "config" % "0.5.0",
            "org.apache.commons" % "commons-email" % "1.2"
        )
    )
).dependsOn(
    commonProject, modelProject
)

lazy val webClientProject = ScalaToJsProject(
    "client", "web/client", WebSettings.javaScriptsDir, payolaSettings
).dependsOn(
    commonProject, webSharedProject
)

lazy val webInitializerProject = Project(
    "initializer", file("web/initializer"), settings = payolaSettings
).dependsOn(
    domainProject, dataProject, webSharedProject
)

lazy val webRunnerProject = Project(
    "runner", file("web/runner"), settings = payolaSettings
).dependsOn(
    webSharedProject
)

lazy val webServerProject = PayolaBuild.webServerProject.dependsOn(
    commonProject, domainProject, dataProject, modelProject, scala2JsonProject, webSharedProject, webClientProject
)