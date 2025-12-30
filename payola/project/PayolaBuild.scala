import scala.collection.mutable
import mutable.ListBuffer
import scala.io.Source
import scala.tools.nsc.io
import sbt._
import sbt.Keys._
import play.Play.autoImport._
import play.PlayImport._
import scala.util.matching.Regex

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

    lazy val webServerProject = Project(
        "server", file("web/server")
        ).enablePlugins(play.PlayScala).settings(
        version := PayolaSettings.version,
        javaHome := Some(file(System.getenv("JAVA_HOME"))),
        libraryDependencies += "org.scala-lang" % "scala-actors" % scalaVersion.value,
        // javacOptions in Compile ++= Seq("-source", "1.7", "-target", "1.7"),
        compileAndPackage := {
            val jarFile = (packageBin in Compile).value
            // Retrieve the dependencies.
            val dependencyExtensions = List("js", "css")
            val dependencyDirectory = new io.Directory(WebSettings.dependencyDir)
            val files = dependencyDirectory.deepFiles.filter(f => dependencyExtensions.contains(f.extension))
                .filterNot(f => f.path.contains("javascripts/lib"))

            val symbolFiles = new mutable.HashMap[String, String]
            val fileProvides = new mutable.HashMap[String, mutable.ArrayBuffer[String]]
            val fileDeclarationRequires = new mutable.HashMap[String, mutable.ArrayBuffer[String]]
            val fileRuntimeRequires = new mutable.HashMap[String, mutable.ArrayBuffer[String]]

            def classLoaderCallRegex(methodName: String): Regex = {
                """s2js\.runtime\.client\.core\.get\(\)\.classLoader\.%s\(\s*['\"]([^'\"]+)['\"]\s*\);""".format(
                    methodName
                ).r
            }
            val provideRegex = classLoaderCallRegex("provide")
            val declarationRequireRegex = classLoaderCallRegex("declarationRequire")
            val runtimeRequireRegex = classLoaderCallRegex("require")

            files.foreach { file =>
                val path = file.toAbsolute.path.toString.replace("\\", "/")
                val fileContent = Source.fromFile(path).getLines.mkString

                provideRegex.findAllIn(fileContent).matchData.foreach {m =>
                    fileProvides.getOrElseUpdate(path, mutable.ArrayBuffer.empty[String]) += m.group(1)
                    symbolFiles += m.group(1) -> path
                }
                declarationRequireRegex.findAllIn(fileContent).matchData.foreach {
                    fileDeclarationRequires.getOrElseUpdate(path, mutable.ArrayBuffer.empty[String]) += _.group(1)
                }
                runtimeRequireRegex.findAllIn(fileContent).matchData.foreach {
                    fileRuntimeRequires.getOrElseUpdate(path, mutable.ArrayBuffer.empty[String]) += _.group(1)
                }
            }

            // Check whether all required symbols are provided.
            val errorFile = (fileDeclarationRequires ++ fileRuntimeRequires).find(_._2.exists(!symbolFiles.contains(_)))
            errorFile.foreach { f =>
                throw new Exception("Dependency '%s' declared in the file '%s' wasn't found.".format(
                    f._2.find(file => !symbolFiles.contains(file)).get.toString,
                    f._1.toString
                ))
            }

            // Create the dependency file.
            val dependencyFile = WebSettings.dependencyFile
            val dependencyBuffer = ListBuffer.empty[String]
            fileProvides.keys.foreach{file =>
                dependencyBuffer += "'%s': [".format(file)
                dependencyBuffer += fileProvides.get(file).map(_.mkString(",")).getOrElse("")
                dependencyBuffer += "] ["
                dependencyBuffer += fileDeclarationRequires.get(file).map(_.mkString(",")).getOrElse("")
                dependencyBuffer += "] ["
                dependencyBuffer += fileRuntimeRequires.get(file).map(_.mkString(",")).getOrElse("")
                dependencyBuffer += "]\n"
            }

            new io.File(dependencyFile).writeAll(dependencyBuffer.mkString)

            jarFile
        },
        clean := {
            val c = clean.value
            // Delete the dependency file.
            new io.File(WebSettings.dependencyFile).delete()
            c
        }
    )
}
