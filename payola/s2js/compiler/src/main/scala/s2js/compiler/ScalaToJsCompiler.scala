package s2js.compiler

import tools.nsc.{Global, Settings}
import scala.tools.nsc.io.Directory

/**
  * A Scala to JavaScript compiler.
  */
class ScalaToJsCompiler(
    val classPath: String,
    val targetDirectory: String,
    val javaScriptDirectory: String,
    val createPackageStructure: Boolean = true)
{
    private val options = List(
        "outputDirectory:" + javaScriptDirectory,
        "createPackageStructure:" + createPackageStructure.toString
    )

    private def buildClasspath(): String = {
        // 1. Get Scala standard libraries (scala-library, scala-reflect, scala-compiler)
        val scalaLibraries = Seq(
            classOf[List[_]],           // scala-library
            classOf[scala.reflect.api.TypeCreator], // scala-reflect
            classOf[Global]              // scala-compiler
        ).map { clazz =>
            val location = clazz.getProtectionDomain.getCodeSource.getLocation
            new java.io.File(location.toURI).getAbsolutePath
        }
        
        // 2. Get current runtime classpath
        val runtimeClasspath = System.getProperty("java.class.path")
            .split(java.io.File.pathSeparator)
            .toSeq
        
        // 3. Combine provided classpath with Scala libraries and runtime classpath
        val providedClasspath = if (classPath != null && classPath.nonEmpty) {
            classPath.split(java.io.File.pathSeparator).toSeq
        } else {
            Seq.empty
        }
        
        // 4. Combine all classpaths and remove duplicates
        (providedClasspath ++ scalaLibraries ++ runtimeClasspath)
            .distinct
            .mkString(java.io.File.pathSeparator)
    }

    private val settings = new Settings()
    settings.classpath.value = buildClasspath()
    settings.outdir.value = targetDirectory
    // Disable warnings for better compatibility
    settings.nowarn.value = true
    settings.deprecation.value = false
    settings.feature.value = false

    /**
      * Compiles the specified Scala source files into JavaScript.
      * @param sourceFiles The Scala files to compile.
      */
    def compileFiles(sourceFiles: List[String]) {
        val compiler = new InternalCompiler(settings, options)
        val run = new compiler.Run()
        run.compile(sourceFiles)
    }

    /** An internal compiler that adds the ScalaToJsPlugin phases to the set of phases. */
    private class InternalCompiler(settings: Settings, val options: List[String]) extends Global(settings)
    {
        /** Add the compiler phases to the phases set. */
        override protected def computeInternalPhases() {
            super.computeInternalPhases()
            val scalaToJsPlugin = new ScalaToJsPlugin(this)
            scalaToJsPlugin.processOptions(options, s => ())
            scalaToJsPlugin.components.foreach(phasesSet += _)
        }
    }

}
