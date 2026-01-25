package s2js.compiler

class SuperCallSpecs extends CompilerIndependentSpec
{
    "Super calls" should "support basic class inheritance" in {
        compileScalaCode(
            """
                class A {
                    def m(x: Int) = x + 1
                }

                class B extends A {
                    override def m(x: Int) = super.m(x) * 2
                }
            """,
            "basic-super-call"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('A');
                s2js.runtime.client.core.get().classLoader.provide('B');

                A = function() { var self = this; };
                A.prototype.m = function(x) { var self = this; return (x + 1); };
                A.prototype.__class__ = new s2js.runtime.client.core.Class('A', []);

                B = function() { 
                    var self = this;
                    A.call(this); 
                };
                s2js.runtime.client.core.get().inherit(B, A);
                B.prototype.m = function(x) {
                    var self = this;
                    return (A.prototype.m.call(self, x) * 2);
                };
                B.prototype.__class__ = new s2js.runtime.client.core.Class('B', [A]);
            """
        }
    }
}