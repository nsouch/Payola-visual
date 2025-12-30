import scala.collection.mutable
import mutable.ListBuffer
import scala.io.Source
import scala.tools.nsc.io
import sbt._
import sbt.Keys._
import play.Play.autoImport._
import play.PlayImport._

object PayolaBuild extends Build
{
    val compileAndPackage = TaskKey[File]("compile-and-package", "Compiles and packages the project in one step.")

    val cleanBeforeTests = TaskKey[Unit]("clean-before-tests", "Cleans the test target directories.")

    /** Common settings of all projects. */
    object Settings
    {
        val scalaVersion = "2.10.7"

        val libDir = file("lib")

        val targetDir = file("lib")
    }

    /** Common settings of the S2Js projects. */
    object S2JsSettings
    {
        val version = "0.2"

        val compilerJarName = "compiler_2.10-%s.jar".format(version)

        val compilerTestsTarget = file("s2js/compiler/target/tests")

        val compilerTestsClassPath = List(Settings.libDir, Settings.targetDir).flatMap { dir =>
            new io.Directory(dir).files.map(_.path)
        }.mkString(";")
    }

    /** Common settings of the Payola projects. */
    object PayolaSettings
    {
        val version = "1.0"

        val organization = "Payola"
    }

    /** Settings of the web project. */
    object WebSettings
    {
        val serverBaseDir = file("web/server")

        val dependencyDir = serverBaseDir / "public"

        val dependencyFile = dependencyDir / "dependencies"

        val javaScriptsDir = dependencyDir / "javascripts"
    }

    /** Common default settings of all projects. */
    val defaultSettings = Defaults.coreDefaultSettings ++ Seq(
        //javaHome := Some(file(System.getenv("JAVA_HOME"))),
        javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
        scalaVersion := Settings.scalaVersion,
        scalacOptions ++= Seq(
            "-deprecation",
            "-unchecked",
            "-encoding", "utf8"
        ),
        libraryDependencies ++= Seq(
            "org.scalatest" %% "scalatest" % "1.9.2" % "test"
        ),
        resolvers ++= Seq(
            DefaultMavenRepository
        ),
        compileAndPackage := {
            val jarFile = (packageBin in Compile).value
            IO.copyFile(jarFile, Settings.targetDir / jarFile.name)
            jarFile
        },
        (test in Test) := (test in Test).dependsOn(compileAndPackage).value
    )

    /** Common default settings of the S2Js projects. */
    val s2JsSettings = defaultSettings ++ Seq(
        version := S2JsSettings.version
    )

    /** Common settings of the Payola projects. */
    val payolaSettings = defaultSettings ++ Seq(
        version := PayolaSettings.version,
        organization := PayolaSettings.organization
    )

    lazy val s2JsAdaptersProject = Project(
        "adapters", file("s2js/adapters"), settings = s2JsSettings
    )

    lazy val s2JsCompilerProject = Project(
        "compiler", file("s2js/compiler"),
        settings = s2JsSettings ++ Seq(
            libraryDependencies ++= Seq(
                "org.scala-lang" % "scala-compiler" % Settings.scalaVersion
            ),
            testOptions ++= Seq(
                Tests.Argument("-Dwd=" + S2JsSettings.compilerTestsTarget.absolutePath),
                Tests.Argument("-Dcp=" + S2JsSettings.compilerTestsClassPath)
            ),
            cleanBeforeTests := {
                new io.Directory(S2JsSettings.compilerTestsTarget).deleteRecursively()
            },
            (test in Test) := (test in Test).dependsOn(cleanBeforeTests).value
        )
    ).dependsOn(
        s2JsAdaptersProject
    )

    /** A project that is compiled to JavaScript using Scala to JavaScript compiler (beside standard compilation). */
    object ScalaToJsProject
    {
        val compilerJar = Settings.targetDir / S2JsSettings.compilerJarName

        def apply(name: String, path: String, outputDir: File, settings: Seq[Def.Setting[_]]) = {
            raw(name, path, outputDir, settings).dependsOn(
                s2JsRuntimeClientProject
            )
        }

        def raw(name: String, path: String, outputDir: File, projectSettings: Seq[Def.Setting[_]]) = {
            Project(
                name, file(path),
                settings = projectSettings ++ Seq(
                    scalacOptions ++= Seq(
                        "-Xplugin:" + compilerJar.absolutePath,
                        "-P:s2js:outputDirectory:" + (outputDir / path).absolutePath
                    ),
                    clean := {
                        // Utilisation de l'API sbt IO plutôt que scala.reflect.io
                        IO.delete(outputDir / path)
                    }
                )
            ).dependsOn(
                s2JsAdaptersProject, s2JsCompilerProject
            )
        }
    }

    lazy val s2JsRuntimeProject = Project(
        "runtime", file("s2js/runtime"), settings = s2JsSettings
    ).aggregate(
        s2JsRuntimeSharedProject, s2JsRuntimeClientProject
    )

    lazy val s2JsRuntimeSharedProject = ScalaToJsProject.raw(
        "runtime-shared", "s2js/runtime/shared", WebSettings.javaScriptsDir, s2JsSettings
    )

    lazy val s2JsRuntimeClientProject = ScalaToJsProject.raw(
        "runtime-client", "s2js/runtime/client", WebSettings.javaScriptsDir, s2JsSettings
    ).dependsOn(
        s2JsRuntimeSharedProject
    )
}
