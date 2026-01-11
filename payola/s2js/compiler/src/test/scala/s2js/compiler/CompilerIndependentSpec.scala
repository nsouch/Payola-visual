package s2js.compiler

import java.io.File
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import scala.tools.nsc.io
import scala.io.Source

/**
 * Base class for independent tests (like DependencyManagerSpec).
 * Each test creates its own isolated compiler instance.
 */
abstract class CompilerIndependentSpec extends AnyFlatSpec with should.Matchers
{
    private def getWorkingDirectory: File = {
        val baseDir = new File("target/tests/" + this.getClass.getName)
        baseDir.mkdirs()
        baseDir
    }

    private def getCompiler(testName: String): ScalaToJsCompiler = {
        val workingDirectory = new File(getWorkingDirectory, testName)
        workingDirectory.mkdirs()

        val targetDirectory = new File(workingDirectory, "target")
        targetDirectory.mkdirs()

        // Use minimal classpath to avoid annotation loading issues
        // Get only essential Scala libraries
        val scalaLib = classOf[List[_]].getProtectionDomain.getCodeSource.getLocation.getPath
        val scalaReflect = classOf[scala.reflect.api.TypeCreator].getProtectionDomain.getCodeSource.getLocation.getPath
        val scalaCompiler = classOf[scala.tools.nsc.Global].getProtectionDomain.getCodeSource.getLocation.getPath
        
        // Add adapters for tests that need them (browser, html, etc.)
        val adaptersPath = new File("s2js/adapters/target/scala-2.12/classes").getAbsolutePath
        
        val classpath = Seq(scalaLib, scalaReflect, scalaCompiler, adaptersPath).mkString(File.pathSeparator)
        
        new ScalaToJsCompiler(
            classpath,
            targetDirectory.getAbsolutePath,
            workingDirectory.getAbsolutePath,
            false
        )
    }

    def compileScalaCode(scalaSource: String, testName: String): Expector = {
        val compiler = getCompiler(testName)
        val workingDirectory = new File(getWorkingDirectory, testName)
        val fileName = workingDirectory.getAbsolutePath + "/Test"

        val scalaFile = new File(fileName + ".scala")
        io.File(scalaFile).writeAll(scalaSource)
        compiler.compileFiles(List(scalaFile.getAbsolutePath))

        val compiled = Source.fromFile(fileName + ".js").mkString
        new Expector(compiled)
    }

    class Expector(val actual: String)
    {
        def shouldCompileTo(expected: String) {
            if (normalizeWhiteSpace(actual) != normalizeWhiteSpace(expected)) {
                println(">>>EXPECTED>>>" + normalizeWhiteSpace(expected) + "<<<")
                println(">>>ACTUAL  >>>" + normalizeWhiteSpace(actual) + "<<<")
                assert(false)
            }
        }

        def shouldExactlyCompileTo(expected: String) {
            if (actual != expected) {
                println(">>>EXPECTED>>>" + expected + "<<<")
                println(">>>ACTUAL  >>>" + actual + "<<<")
                assert(false)
            }
        }

        private def normalizeWhiteSpace(text: String) = {
            text.replaceAll("""([ ]+|[\n\r\t])""", " ").replaceAll("""[ ]+""", " ").replaceAll("""^[ ]""", "")
        }
    }
}
