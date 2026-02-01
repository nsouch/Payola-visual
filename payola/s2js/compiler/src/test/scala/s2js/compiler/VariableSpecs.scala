package s2js.compiler

class VariableSpecs extends CompilerIndependentSpec
{
    "Variables" should "have literal values" in {
        compileScalaCode(
            """
                package foo

                class A {
                    def m1() {
                        val a = "foo"
                        val b = 1
                        val c = true
                        val d = 1.0
                    }
                }
            """,
            "literal-values"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('foo.A');

                foo.A = function() {
                    var self = this;
                };

                foo.A.prototype.m1 = function() {
                    var self = this;
                    var a = 'foo';
                    var b = 1;
                    var c = true;
                    var d = 1.0;
                };
                foo.A.prototype.__class__ = new s2js.runtime.client.core.Class('foo.A', []);
            """
        }
    }

    it should "have instance values" in {
        compileScalaCode(
            """
                package foo {
                    class A {
                        def m1() {
                            val a = new B
                        }
                    }
                    
                    class B
                }
            """,
            "instance-values"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('foo.A');
                s2js.runtime.client.core.get().classLoader.provide('foo.B');

                foo.A = function() {
                    var self = this;
                };
                foo.A.prototype.m1 = function() {
                    var self = this;
                    var a = new foo.B();
                };
                foo.A.prototype.__class__ = new s2js.runtime.client.core.Class('foo.A', []);
                
                foo.B = function() {
                    var self = this;
                };
                foo.B.prototype.__class__ = new s2js.runtime.client.core.Class('foo.B', []);
            """
        }
    }

    it should "have parameter values" in {
        compileScalaCode(
            """
                package foo {
                    class A {
                        def m1(y: String) {
                            val a = y
                        }
                    }
                }
            """,
            "parameter-values"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('foo.A');

                foo.A = function() {
                    var self = this;
                };
                foo.A.prototype.m1 = function(y) {
                    var self = this;
                    var a = y;
                };
                foo.A.prototype.__class__ = new s2js.runtime.client.core.Class('foo.A', []);
            """
        }
    }

    it should "have function return values" in {
        compileScalaCode(
            """
                package foo {
                    class A {
                        def m1() = "foo"
                        def m2() {
                            var a = m1();
                        }
                    }
                }
            """,
            "function-return-values"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('foo.A');

                foo.A = function() {
                    var self = this;
                };
                foo.A.prototype.m1 = function() {
                    var self = this;
                    return 'foo';
                };
                foo.A.prototype.m2 = function() {
                    var self = this;
                    var a = self.m1();
                };
                foo.A.prototype.__class__ = new s2js.runtime.client.core.Class('foo.A', []);
            """
        }
    }

    it should "have expression values" in {
        compileScalaCode(
            """
                package foo {
                    class A {
                        def m1(x: Int) {
                            var a = x + 5
                            var b = x == 5
                            var c = ((9 * a) / (2 + a))
                        }
                    }
                }
            """,
            "expression-values"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('foo.A');

                foo.A = function() {
                    var self = this;
                };
                foo.A.prototype.m1 = function(x) {
                    var self = this;
                    var a = (x + 5);
                    var b = (x == 5);
                    var c = ((9 * a) / (2 + a));
                };
                foo.A.prototype.__class__ = new s2js.runtime.client.core.Class('foo.A', []);
            """
        }
    }

    it should "have function values" in {
        compileScalaCode(
            """
                package foo {
                    class A {
                        def m1() {
                            val a = (b: String) => { "foo" + b }
                        }
                    }
                }
            """,
            "function-values"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('foo.A');

                foo.A = function() {
                    var self = this;
                };
                foo.A.prototype.m1 = function() {
                    var self = this;
                    var a = function($b) { return ('foo' + $b); };
                };
                foo.A.prototype.__class__ = new s2js.runtime.client.core.Class('foo.A', []);
            """
        }
    }

    it should "support lazy vals" in {
        compileScalaCode(
            """
                object o {
                    lazy val x = 1 + 1
                }
            """,
            "lazy-val"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('o');
                s2js.runtime.client.core.get().mixIn(o, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.$x = function () {
                        return obj.x$lzycompute();
                    };
                    obj.bitmap$0 = 0;
                    obj.x$lzycompute = function() {
                        var self = this;
                        obj.$synchronized(function() {
                            if ((! obj.bitmap$0)) {
                                obj.lazyval_x = s2js.runtime.client.core.get().asInstanceOf(2, 'scala.Int');
                                obj.bitmap$0 = true;
                            } else {
                                undefined;
                            }
                        });
                        return obj.lazyval_x;
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('o', []);
                    return obj;
                }), true);
            """
        }
    }
}

