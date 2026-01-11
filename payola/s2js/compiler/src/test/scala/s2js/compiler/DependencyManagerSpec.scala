package s2js.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import scala.reflect.internal.util.BatchSourceFile
import scala.tools.nsc.io.AbstractFile
import s2js.compiler.components.{DependencyManager, PackageDefCompiler}
import java.io.File

/**
 * Test for DependencyManager to verify pattern matching works correctly
 * with different AST node types in Scala 2.12.20
 * 
 * This test validates that retrieveStructure() correctly processes different
 * types of AST nodes and builds the dependency graph.
 */
class DependencyManagerSpec extends AnyFlatSpec with should.Matchers {
  
  // Helper to compile code and extract the dependency manager from the AST
  private def compileAndGetDependencyManager(code: String, testName: String): DependencyManager = {
    val targetDir = new File(s"target/test-dependency-manager/$testName")
    targetDir.mkdirs()
    
    // Create compiler settings with minimal classpath
    val settings = new scala.tools.nsc.Settings()
    // Use bootclasspath to avoid loading all runtime dependencies
    settings.usejavacp.value = false
    settings.bootclasspath.value = System.getProperty("sun.boot.class.path", "")
    
    // Add only the essential libraries
    val scalaLib = classOf[List[_]].getProtectionDomain.getCodeSource.getLocation.getPath
    val scalaReflect = classOf[scala.reflect.api.TypeCreator].getProtectionDomain.getCodeSource.getLocation.getPath
    val scalaCompiler = classOf[scala.tools.nsc.Global].getProtectionDomain.getCodeSource.getLocation.getPath
    
    settings.classpath.value = Seq(scalaLib, scalaReflect, scalaCompiler).mkString(File.pathSeparator)
    settings.outputDirs.setSingleOutput(AbstractFile.getDirectory(targetDir))
    
    // Disable features that might cause issues
    settings.nowarn.value = true
    settings.deprecation.value = false
    
    val reporter = new scala.tools.nsc.reporters.StoreReporter(settings)
    val global = new scala.tools.nsc.Global(settings, reporter)
    
    val batchSource = new BatchSourceFile(s"<test-$testName>", code)
    val run = new global.Run
    run.compileSources(List(batchSource))
    
    if (reporter.hasErrors) {
      val errors = reporter.infos.map(info => s"${info.severity}: ${info.msg}").mkString("\n")
      fail(s"Compilation failed:\n$errors")
    }
    
    // Get the compilation unit and extract the PackageDef
    if (!global.currentRun.units.hasNext) {
      fail("No compilation units found")
    }
    
    val unit = global.currentRun.units.next()
    unit.body match {
      case pd: global.PackageDef =>
        val pkgDefCompiler = new PackageDefCompiler(global, unit.source.file, pd)
        pkgDefCompiler.dependencies
      case _ =>
        fail(s"Expected PackageDef at root of compilation unit, got: ${unit.body.getClass}")
    }
  }
  
  "DependencyManager AST" should "handle PackageDef nodes" in {
    // PackageDef is matched in: case packageDef: Global#PackageDef
    // This represents: package x.y.z { ... }
    
    val testCode = """
      package test.example
      
      class MyClass
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "packagedef-test")
    
    // Document: PackageDef contains child nodes (classes, objects, traits, etc.)
    // When matched, it recursively calls retrieveStructure on each child
    val structure = dependencyManager.getPackageDefStructure
    
    // Should have processed the PackageDef and found the class
    structure.classDefMap should not be empty
    structure.classDefMap.keys.exists(_.contains("MyClass")) should be (true)
  }
  
  it should "handle ClassDef nodes for regular classes" in {
    // ClassDef is matched in: case classDef: Global#ClassDef
    // This represents: class ClassName { ... }
    
    val testCode = """
      package test
      
      class SimpleClass
      class ClassWithBody { def method() = 1 }
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "classdef-test")
    
    // Document: ClassDef triggers retrieveClassDefStructure()
    // which adds the class to the dependency graph
    val structure = dependencyManager.getPackageDefStructure
    
    structure.classDefMap.size should be >= 2
    structure.classDefMap.keys.exists(_.contains("SimpleClass")) should be (true)
    structure.classDefMap.keys.exists(_.contains("ClassWithBody")) should be (true)
  }
  
  it should "handle ClassDef nodes for objects (module classes)" in {
    // Objects results in two trees:
    // 1. ModuleDef (the object 'term')
    // 2. ClassDef (the object 'module class') - synthetically generated
    
    // The DependencyManager currently only matches ClassDef, so it processes the module class.
    // The ModuleDef falls into the default case (ignored).
    
    val testCode = """
      package test
      
      object SingletonObject
      object ObjectWithMembers { val x = 42 }
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "object-test")
    
    // Document: Objects are handled via their synthetic ClassDef (where symbol.isModuleClass == true)
    val structure = dependencyManager.getPackageDefStructure
    
    structure.classDefMap.size should be >= 2
    structure.classDefMap.keys.exists(key => key.startsWith("object") && key.contains("SingletonObject")) should be (true)
    structure.classDefMap.keys.exists(key => key.startsWith("object") && key.contains("ObjectWithMembers")) should be (true)
  }
  
  it should "handle ClassDef nodes for traits" in {
    // Traits are also represented as ClassDef nodes
    
    val testCode = """
      package test
      
      trait MyTrait
      trait TraitWithMembers { def abstractMethod(): Int }
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "trait-test")
    
    // Document: Traits are ClassDef nodes that can be distinguished via symbol flags
    val structure = dependencyManager.getPackageDefStructure
    
    structure.classDefMap.size should be >= 2
    structure.classDefMap.keys.exists(_.contains("MyTrait")) should be (true)
    structure.classDefMap.keys.exists(_.contains("TraitWithMembers")) should be (true)
  }
  
  it should "handle nested ClassDef nodes" in {
    // Nested classes and objects are also ClassDef
    
    val testCode = """
      package test
      
      class Outer {
        class Inner
        object InnerObject
        trait InnerTrait
      }
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "nested-test")
    
    // Document: Nested classes have symbol.owner pointing to the outer class
    // The dependency manager tracks this relationship
    val structure = dependencyManager.getPackageDefStructure
    
    structure.classDefMap.keys.exists(_.contains("Outer")) should be (true)
    structure.classDefMap.keys.exists(_.contains("Inner")) should be (true)
    structure.classDefMap.keys.exists(key => key.startsWith("object") && key.contains("InnerObject")) should be (true)
    structure.classDefMap.keys.exists(_.contains("InnerTrait")) should be (true)
    
    // Check dependencies: Inner types should depend on Outer
    val innerDeps = structure.classDefDependencyGraph.filter(_._1.contains("Inner"))
    innerDeps should not be empty
  }
  
  it should "fall through to default case for other node types" in {
    // The default case (case _ =>) handles:
    // - DefDef (method definitions)
    // - ValDef (value and variable definitions)
    // - TypeDef (type aliases)
    // - Import statements
    // - Template nodes
    // - Expression nodes (Apply, Select, Ident, etc.)
    
    
    val examplesOfIgnoredNodes = """
      DefDef:    def method() = 1
      ValDef:    val x = 42
      ValDef:    var y = "test"
      TypeDef:   type MyType = Int
      Import:    import scala.collection._
      Apply:     println("hello")
      Select:    obj.field
      Ident:     variableName
    """
    
    val testCode = """
      package test
      
      class TestClass {
        def method() = 1
        val x = 42
        var y = "test"
        type MyType = Int
      }
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "ignored-nodes-test")
    
    // Document: These nodes are intentionally ignored by retrieveStructure
    // because only PackageDef and ClassDef are needed for dependency management
    val structure = dependencyManager.getPackageDefStructure
    
    // Only the class should be in the structure, not the internal members
    structure.classDefMap.size should be (1)
    structure.classDefMap.keys.head should include ("TestClass")
  }
  
  "Pattern matching type differences" should "document Global# vs global. issue" in {
    // In Scala 2.9: Global#Tree pattern matching worked with Global#PackageDef, Global#ClassDef
    // In Scala 2.12+: Need to use concrete global instance: global.Tree, global.PackageDef, global.ClassDef
    
    // The fix: private val global = packageDefCompiler.global
    // Then use: global.Tree, global.PackageDef, global.ClassDef
    
    // Why? Path-dependent types with projection (Global#X) don't match properly
    // in pattern matching against concrete instances in Scala 2.12+
    
    val testCode = """
      package test
      class TestClass
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "pattern-matching-test")
    
    // Verify that pattern matching works with the fixed implementation
    noException should be thrownBy {
      val structure = dependencyManager.getPackageDefStructure
      structure.classDefMap should not be empty
    }
  }
  
  "AST node type identification" should "verify types in actual compilation" in {
    // This documents the actual tree types encountered during compilation:
    
    val testCode = """
      package x
      class C
      object O
      trait T
    """
    
    val dependencyManager = compileAndGetDependencyManager(testCode, "ast-types-test")
    
    val scalaSourceTypes = Map(
      "package x" -> "PackageDef",
      "class C" -> "ClassDef (isModuleClass = false)",
      "object O" -> "ClassDef (isModuleClass = true)", 
      "trait T" -> "ClassDef (isTrait = true)",
      "def m() = 1" -> "DefDef (ignored by retrieveStructure)",
      "val v = 1" -> "ValDef (ignored by retrieveStructure)",
      "var v = 1" -> "ValDef (ignored by retrieveStructure)",
      "type T = Int" -> "TypeDef (ignored by retrieveStructure)"
    )
    
    val structure = dependencyManager.getPackageDefStructure
    
    // Verify we found classes, object, and trait
    structure.classDefMap.size should be (3)
    structure.classDefMap.keys.exists(key => key.contains("C") && key.startsWith("class")) should be (true)
    structure.classDefMap.keys.exists(key => key.contains("O") && key.startsWith("object")) should be (true)
    structure.classDefMap.keys.exists(key => key.contains("T") && key.startsWith("class")) should be (true)
    
    scalaSourceTypes.size should be > 0
  }
  
  "retrieveStructure recursion" should "process trees hierarchically" in {
    // PackageDef.children.foreach(retrieveStructure) - processes all top-level definitions
    // ClassDef.impl.body.foreach(retrieveStructure) - processes nested classes/objects
    
    val hierarchyExample = """
      package root {
        class TopLevel {
          class Nested {
            object DeepNested
          }
          def method() = 1
          val field = 42
        }
        object TopLevelObject {
          val value = "test"
        }
      }
    """
    
    val dependencyManager = compileAndGetDependencyManager(hierarchyExample, "hierarchy-test")
    
    val structure = dependencyManager.getPackageDefStructure
    
    // The recursion should find all nested classes/objects
    structure.classDefMap.keys.exists(_.contains("TopLevel")) should be (true)
    structure.classDefMap.keys.exists(_.contains("Nested")) should be (true)
    structure.classDefMap.keys.exists(key => key.startsWith("object") && key.contains("DeepNested")) should be (true)
    structure.classDefMap.keys.exists(key => key.startsWith("object") && key.contains("TopLevelObject")) should be (true)
    
    // The recursion stops at DefDef, ValDef, etc. (default case does nothing)
    // So we should have exactly 4 items (TopLevel, Nested, DeepNested, TopLevelObject)
    structure.classDefMap.size should be (4)
  }
}
