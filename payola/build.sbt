import scala.collection.mutable
import mutable.ListBuffer
import scala.io.Source
import scala.tools.nsc.io
import sbt._
import Keys._
import scala.util.matching.Regex
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.SbtNativePackager.autoImport._


val compileAndPackage = TaskKey[File]("compile-and-package", "Compiles and packages the project in one step.")

val cleanBeforeTests = TaskKey[Unit]("clean-before-tests", "Cleans the test target directories.")

/** Common settings of all projects. */
val scalaVersionSetting = "2.12.20"
val scalaBinaryVersion = "2.12"
val libDir = file("lib")
val targetDir = file("lib")

/** Common settings of the S2Js projects. */
val s2jsVersion = "0.2"
val compilerJarName = s"compiler_${scalaBinaryVersion}-${s2jsVersion}.jar"
val compilerTestsTarget = file("s2js/compiler/target/tests")
lazy val compilerTestsClassPath = {
    import java.io.{File => JFile}
    List(libDir, targetDir).flatMap { dir =>
        val files = Option(dir.listFiles()).getOrElse(Array.empty[JFile])
        files.filter(_.isFile).map(_.getAbsolutePath)
    }.mkString(";")
}

/** Common settings of the Payola projects. */
val payolaVersion = "1.0"
val payolaOrganization = "Payola"

/** Settings of the web project. */
val serverBaseDir = file("web/server")
val dependencyDir = serverBaseDir / "public"
val dependencyFile = dependencyDir / "dependencies"
val javaScriptsDir = dependencyDir / "javascripts"

/** Common default settings of all projects. */
val defaultSettings = Seq(
    javaHome := Some(file(System.getenv("JAVA_HOME"))),
    scalaVersion := scalaVersionSetting,
    scalacOptions ++= Seq(
        "-deprecation",
        "-unchecked",
        "-encoding", "utf8"
    ),
    libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.2.19" % "test"
    ),
    resolvers ++= Seq(
        DefaultMavenRepository
    ),
    compileAndPackage := {
        val jarFile = (packageBin in Compile).value
        IO.copyFile(jarFile, targetDir / jarFile.name)
        jarFile
    },
    (test in Test) := (test in Test).dependsOn(compileAndPackage).value
)

/** Common default settings of the S2Js projects. */
val s2JsSettings = defaultSettings ++ Seq(
    version := s2jsVersion
)

/** Common settings of the Payola projects. */
val payolaSettings = defaultSettings ++ Seq(
    version := payolaVersion,
    organization := payolaOrganization
)

/**
  * The Payola solution. All projects have to be listed in the aggregate method.
  */
lazy val payolaProject = Project(
    "payola", file(".")
    ).enablePlugins(JavaAppPackaging)
    .settings(payolaSettings)
    .aggregate(
        s2JsProject, scala2JsonProject, commonProject, domainProject, dataProject, modelProject, webProject
    )

lazy val s2JsProject = Project(
    "s2js", file("s2js")
).settings(s2JsSettings)
.aggregate(
    s2JsAdaptersProject, s2JsCompilerProject, s2JsRuntimeProject
)

lazy val s2JsAdaptersProject = Project(
    "adapters", file("s2js/adapters")
).settings(s2JsSettings)

lazy val s2JsCompilerProject = Project(
    "compiler", file("s2js/compiler")
).settings(s2JsSettings)
.settings(
    libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersionSetting
    ),
    testOptions ++= Seq(
        Tests.Argument("-Dwd=" + compilerTestsTarget.absolutePath),
        Tests.Argument("-Dcp=" + compilerTestsClassPath)
    ),
    cleanBeforeTests := {
        IO.delete(compilerTestsTarget)
    },
    (test in Test) := (test in Test).dependsOn(cleanBeforeTests).value
).dependsOn(
    s2JsAdaptersProject
)

/** A project that is compiled to JavaScript using Scala to JavaScript compiler (beside standard compilation). */
val compilerJar = targetDir / compilerJarName

def scalaToJsProjectRaw(name: String, path: String, outputDir: File, projectSettings: Seq[Def.Setting[_]], adapters: Project, compiler: Project): Project = {
    Project(
        name, file(path)
    ).settings(projectSettings)
    .settings(
        scalacOptions ++= Seq(
            "-Xplugin:" + compilerJar.absolutePath,
            "-P:s2js:outputDirectory:" + (outputDir / path).absolutePath
        ),
        clean := {
            // Utilisation de l'API sbt IO plutôt que scala.reflect.io
            IO.delete(outputDir / path)
        }
    ).dependsOn(
        adapters, compiler
    )
}

def scalaToJsProject(name: String, path: String, outputDir: File, settings: Seq[Def.Setting[_]]): Project = {
    scalaToJsProjectRaw(name, path, outputDir, settings, s2JsAdaptersProject, s2JsCompilerProject).dependsOn(
        s2JsRuntimeClientProject
    )
}

lazy val s2JsRuntimeProject = Project(
    "runtime", file("s2js/runtime")
).settings(s2JsSettings)
.aggregate(
    s2JsRuntimeSharedProject, s2JsRuntimeClientProject
)

lazy val s2JsRuntimeSharedProject = scalaToJsProjectRaw(
    "runtime-shared", "s2js/runtime/shared", javaScriptsDir, s2JsSettings, s2JsAdaptersProject, s2JsCompilerProject
)

lazy val s2JsRuntimeClientProject = scalaToJsProjectRaw(
    "runtime-client", "s2js/runtime/client", javaScriptsDir, s2JsSettings, s2JsAdaptersProject, s2JsCompilerProject
).dependsOn(
    s2JsRuntimeSharedProject
)

lazy val scala2JsonProject = Project(
    "scala2json", file("scala2json")
).settings(payolaSettings)
.enablePlugins(net.virtualvoid.sbt.graph.DependencyGraphPlugin)

lazy val commonProject = scalaToJsProject(
    "common", "common", javaScriptsDir, payolaSettings
).dependsOn(scala2JsonProject)

lazy val domainProject = Project(
    "domain", file("domain")
).settings(payolaSettings)
.settings(
    libraryDependencies ++= Seq(
        "org.apache.jena" % "jena-core" % "2.11.1",
        "org.apache.jena" % "jena-arq" % "2.11.1",
        "org.apache.jena" % "jena" % "2.11.0",
        "org.apache.httpcomponents" % "httpclient" % "4.2.4",
        "commons-io" % "commons-io" % "2.4",
        "commons-lang" % "commons-lang" % "2.4",
        "com.typesafe.akka" %% "akka-actor" % "2.5.32"
    )
).dependsOn(
    commonProject
)

lazy val dataProject = Project(
    "data", file("data")
).settings(payolaSettings)
.settings(
    libraryDependencies ++= Seq(
        "org.squeryl" %% "squeryl" % "0.9.5-7",
        "com.h2database" % "h2" % "1.4.200",
        "mysql" % "mysql-connector-java" % "8.0.33",
        "org.postgresql" % "postgresql" % "42.7.4",
        "org.apache.derby" % "derby" % "10.14.2.0",
        "org.scalaj" %% "scalaj-http" % "2.4.2"
    )
).dependsOn(
    commonProject, domainProject
)

lazy val modelProject = Project(
    "model", file("model")
).settings(payolaSettings)
.settings(
    libraryDependencies ++= Seq(
        "org.apache.commons" % "commons-lang3" % "3.1",
        "com.fasterxml.jackson.core" % "jackson-core" % "2.3.0-rc1",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.0-rc1",
        "com.fasterxml.jackson.core" % "jackson-annotations" % "2.3.0-rc1",
        "com.typesafe.akka" %% "akka-actor" % "2.5.32"
    )
).dependsOn(
    commonProject, domainProject, dataProject
)

lazy val webProject = Project(
    "web", file("web")
).settings(payolaSettings)
.aggregate(
    webSharedProject, webClientProject, webInitializerProject, webServerProject
)

lazy val webSharedProject = scalaToJsProject(
    "shared", "web/shared", javaScriptsDir,
    payolaSettings ++ Seq(
        libraryDependencies ++= Seq(
            "com.typesafe" % "config" % "0.5.0",
            "org.apache.commons" % "commons-email" % "1.2"
        )
    )
).dependsOn(
    commonProject, modelProject
)

lazy val webClientProject = scalaToJsProject(
    "client", "web/client", javaScriptsDir, payolaSettings
).dependsOn(
    commonProject, webSharedProject
)

lazy val webInitializerProject = Project(
    "initializer", file("web/initializer")
).settings(payolaSettings)
.dependsOn(
    domainProject, dataProject, webSharedProject
)

lazy val webRunnerProject = Project(
    "runner", file("web/runner")
).settings(payolaSettings)
.dependsOn(
    webSharedProject
)

lazy val webServerProject = Project(
      "server", file("web/server")
    ).enablePlugins(PlayScala)
    .settings(
      version := payolaVersion,
      javaHome := Some(file(System.getenv("JAVA_HOME"))),
      libraryDependencies ++= Seq(guice),
      compileAndPackage := {
        val jarFile = (packageBin in Compile).value
        // Retrieve the dependencies.
        val dependencyExtensions = List("js", "css")
        val dependencyDirectory = new io.Directory(dependencyDir)
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
        val depFile = dependencyFile
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

        new io.File(depFile).writeAll(dependencyBuffer.mkString)

        jarFile
      },
      clean := {
          val c = clean.value
              // Delete the dependency file.
              new io.File(dependencyFile).delete()
              c
      }
).dependsOn(
    commonProject, domainProject, dataProject, modelProject, scala2JsonProject, webSharedProject, webClientProject
)
