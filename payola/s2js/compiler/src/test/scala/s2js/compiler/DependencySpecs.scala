package s2js.compiler

class DependencySpecs extends CompilerIndependentSpec
{
    "Requires" should "avoid requiring deep packages" in {
        compileScalaCode(
            """
                package a.b.c {
                    object d {
                        def m1() {}
                    }
                }
            """,
            "avoid-deep-packages-test"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('a.b.c.d');
                s2js.runtime.client.core.get().mixIn(a.b.c.d, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.m1 = function() { var self = this; };
                    obj.__class__ = new s2js.runtime.client.core.Class('a.b.c.d', []);
                    return obj;
                }), true);
            """
        }
    }

    it should "add require for used classes" in {
        compileScalaCode(
            """
                package foo {
                    import java.util.ArrayList
                    import java.util.Calendar

                    object a {
                        val x = new java.util.Date
                        def m1() {
                            val y = new java.util.Random
                            val z = new ArrayList[String]
                        }
                    }
                }
            """,
            "require-used-classes-test"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('foo.a');
                s2js.runtime.client.core.get().classLoader.require('java.util.ArrayList');
                s2js.runtime.client.core.get().classLoader.require('java.util.Date');
                s2js.runtime.client.core.get().classLoader.require('java.util.Random');
                s2js.runtime.client.core.get().mixIn(foo.a, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.x = new java.util.Date();
                    obj.m1 = function() { var self = this; var y = new java.util.Random();
                    var z = new java.util.ArrayList(); };
                    obj.__class__ = new s2js.runtime.client.core.Class('foo.a', []);
                    return obj;
                }), true);
            """
        }
    }

    it should "support implicit browser imports" in {
        compileScalaCode(
            """
                import s2js.adapters.browser._

                object o1 {
                    val f1 = "aaaa"
                    def m1() {
                        window.alert(f1)
                    }
                }
            """,
            "implicit-browser-imports-test"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('o1');
                s2js.runtime.client.core.get().mixIn(o1, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.f1 = 'aaaa';
                    obj.m1 = function() { var self = this; window.alert(o1.get().f1); };
                    obj.__class__ = new s2js.runtime.client.core.Class('o1', []);
                    return obj;
                }), true);
            """
        }
    }

    it should "support explicit browser imports" in {
        compileScalaCode(
            """
                import s2js.adapters.browser._

                object o1 {
                    val f1 = window.location
                }
            """,
            "explicit-browser-imports-test"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('o1');
                s2js.runtime.client.core.get().mixIn(o1, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.f1 = window.location; obj.__class__ = new s2js.runtime.client.core.Class('o1', []);
                    return obj;
                }), true);
            """
        }
    }
}
