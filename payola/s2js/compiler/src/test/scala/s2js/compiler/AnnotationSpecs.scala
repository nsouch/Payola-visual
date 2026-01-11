package s2js.compiler

class AnnotationSpecs extends CompilerIndependentSpec
{
    "Annotations" should "support native class implementation" in {
        compileScalaCode(
            """
                @s2js.compiler.javascript(""" + "\"\"\"" + """
                    A = function() {
                        this.x = 'foo';
                        window.alert('a created');
                    }
                """ + "\"\"\"" + """)
                class A
            """,
            "native-class-implementation"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('A');

                A = function() {
                    this.x = 'foo';
                    window.alert('a created');
                }
            """
        }
    }

    it should "support native method implementation" in {
        compileScalaCode(
            """
                class A {
                    val x = "foo"
                    val y = 123

                    @s2js.compiler.javascript(""" + "\"\"\"" + """
                        console.log(self.x + self.y.toString + x);
                    """ + "\"\"\"" + """)
                    def m(x: String) {}
                }
            """,
            "native-method-implementation"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('A');

                A = function() {
                    var self = this;
                    self.x = 'foo';
                    self.y = 123;
                };
                A.prototype.m = function(x) {
                    var self = this;
                    console.log(self.x + self.y.toString + x);
                };
                A.prototype.__class__ = new s2js.runtime.client.core.Class('A', []);
            """
        }
    }

    it should "support native val value" in {
        compileScalaCode(
            """
                class A {
                    @s2js.compiler.javascript("[1, 2, 3]")
                    val x = ""
                }
            """,
            "native-val-value"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('A');

                A = function() {
                    var self = this;
                    self.x = [1, 2, 3];
                };
                A.prototype.__class__ = new s2js.runtime.client.core.Class('A', []);
            """
        }
    }
}
