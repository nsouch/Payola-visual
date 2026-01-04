package cz.payola.domain.entities.plugins.compiler

import tools.nsc.{Global, Settings}
import java.util.UUID
import java.util.Properties
import scala.tools.nsc.io._
import scala.tools.nsc.reporters._

/**
  * A compiler of the analytical plugins.
  * @param libDirectory The directory with all libraries that may be used in the plugins.
  * @param pluginClassDirectory The output directory where the plugin class files are stored.
  */
class PluginCompiler(val libDirectory: java.io.File, val pluginClassDirectory: java.io.File)
{
    private def buildClasspath(): String = {
        // 1. Get libraries from lib directory
        val libJars = new Directory(libDirectory).files
            .map(_.path)
            .toSeq
        
        // 2. Get Scala standard libraries (scala-library, scala-reflect, scala-compiler)
        val scalaLibraries = Seq(
            classOf[List[_]],           // scala-library
            classOf[scala.reflect.api.TypeCreator], // scala-reflect
            classOf[Global]              // scala-compiler
        ).map { clazz =>
            val location = clazz.getProtectionDomain.getCodeSource.getLocation
            new java.io.File(location.toURI).getAbsolutePath
        }
        
        // 3. Get current runtime classpath (includes already compiled domain classes)
        val runtimeClasspath = System.getProperty("java.class.path")
            .split(java.io.File.pathSeparator)
            .toSeq
        
        // 4. Combine all classpaths and remove duplicates
        (libJars ++ scalaLibraries ++ runtimeClasspath)
            .distinct
            .mkString(java.io.File.pathSeparator)
    }

    private val settings = new Settings()
    settings.classpath.value = buildClasspath()
    settings.outdir.value = pluginClassDirectory.getAbsolutePath
    // Disable warnings for better compatibility with Java 11+
    settings.nowarn.value = true
    // Enable better error messages
    settings.deprecation.value = false
    settings.feature.value = false
    // Do NOT use usejavacp - it causes classfile parsing conflicts

    private val compiler = new InternalCompiler(settings, new ExceptionReporter)

    /**
      * Compiles the specified plugin from the source code.
      * @param pluginSourceCode The plugin scala source code.
      * @return Information about the compiled plugin.
      */
    def compile(pluginSourceCode: String): PluginInfo = {
        // Create the temporary plugin source file.
        val pluginFileName = UUID.randomUUID.toString + ".scala"
        val pluginFile = new java.io.File(pluginFileName)
        new File(pluginFile).writeAll(pluginSourceCode)

        try {
            val run = new compiler.Run()
            run.compile(List(pluginFileName))
            new PluginInfo(compiler.pluginVerifier.pluginName.get, compiler.pluginVerifier.pluginClassName.get)
        } finally {
            pluginFile.delete()
        }
    }

    /**
      * A compiler that is actually used to compile the plugins. Adds all the phases of the plugin verifier to the
      * standard set of the compiler phases.
      * @param settings Settings of the compiler.
      * @param reporter An error reporter.
      */
    private class InternalCompiler(settings: Settings, reporter: Reporter) extends Global(settings, reporter)
    {
        val pluginVerifier = new PluginVerifier(this)

        /** Add the compiler phases to the phases set. */
        override protected def computeInternalPhases() {
            super.computeInternalPhases()
            pluginVerifier.components.foreach(phasesSet += _)
        }
    }
}
