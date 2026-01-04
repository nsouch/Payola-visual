package cz.payola.domain.test

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cz.payola.domain.entities.plugins.compiler._
import cz.payola.domain.entities.plugins.PluginClassLoader

class PluginCompilerSpec extends AnyFlatSpec with Matchers
{
    val libDirectory = new java.io.File("lib")

    val scalaVersion = scala.util.Properties.versionNumberString
    val scalaBinaryVersion = scalaVersion.split('.').take(2).mkString(".")
    val pluginClassDirectory = new java.io.File(s"domain/target/scala-$scalaBinaryVersion/test-classes")

    val compiler = new PluginCompiler(libDirectory, pluginClassDirectory)

    val loader = new PluginClassLoader(pluginClassDirectory, getClass.getClassLoader)

    "Plugin compiler" should "compile simple plugins" in {
        /** Reading the source code from file and compiling it ensures that the code is valid Scala code.
          */
        val sourceCode = scala.io.Source.fromFile("domain/src/test/scala/cz/payola/domain/test/MyCustomPlugin.scala").mkString
        val pluginInfo = compiler.compile(sourceCode)

        val plugin = loader.instantiatePlugin(pluginInfo.className)
        assert(pluginInfo.name == "Time Delay in seconds", "The plugin name is invalid.")
        assert(pluginInfo.name == plugin.name, "The plugin name doesn't match the name in the plugin info.")
        assert(plugin.inputCount == 1, "The plugin input count is invalid.")
        assert(plugin.parameters.length == 1, "The plugin parameter count is invalid.")
        assert(plugin.parameters.head.name == "Delay", "The plugin parameter is invalid.")
    }

    it should "throw exceptions when the compilation fails" in {
        try {
            val pluginInfo = compiler.compile(
                """
                    package my.custom.plugin

                    class MyPlugin(
                """)
            fail("The PluginCompilationException wasn't thrown.")
        } catch {
            case _: PluginCompilationException => // NOOP
            case _: Throwable => fail("The PluginCompilationException wasn't thrown.")
        }
    }
}

