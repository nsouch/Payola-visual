package s2js.compiler

class FunctionSpecs extends CompilerIndependentSpec
{
    "Functions" should "support higher-ordered functions" in {
        compileScalaCode(
            """
                import s2js.adapters.browser._

                class F {
                    val v1 = "v1"
                    def f1(x: String) = v1 + x.toUpperCase
                }

                object o {
                    def f2(f: (String) => String) {
                        window.alert(f("m1"))
                    }

                    def f3(x: String) = "what" + x

                    def start() {
                        val x = new F
                        f2(x.f1)
                        f2(f3)
                        f2 { (x: String) => "no" + x }
                    }
                }
            """,
            "higher-ordered-functions"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('F');
                s2js.runtime.client.core.get().classLoader.provide('o');

                F = function() { var self = this; self.v1 = 'v1'; };
                F.prototype.f1 = function(x) { var self = this; return (self.v1 + x.toUpperCase()); };
                F.prototype.__class__ = new s2js.runtime.client.core.Class('F', []);

                s2js.runtime.client.core.get().mixIn(o, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.f2 = function(f) { var self = this; window.alert(f('m1')); };
                    obj.f3 = function(x) { var self = this; return ('what' + x); };
                    obj.start = function() {
                        var self = this;
                        var x = new F();
                        self.f2(function($x) { return x.f1($x); });
                        self.f2(function($x) { return self.f3($x); });
                        self.f2(function($x) { return ('no' + $x); });
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('o', []);
                    return obj;
                }), true);
            """
        }
    }

    it should "allow anonymous functions assigned to variables" in {
        compileScalaCode(
            """
                import s2js.adapters.browser._

                object a {
                    val x = (y: String) => { window.alert(y) }
                }
            """,
            "anonymous-function-assignment"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('a');
                s2js.runtime.client.core.get().mixIn(a, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.x = function($y) { window.alert($y); };
                    obj.__class__ = new s2js.runtime.client.core.Class('a', []);
                    return obj;
                }), true);
            """
        }
    }

    it should "support methods with default parameters" in {
        compileScalaCode(
            """
                object o {
                    def m(a: Int = 10, b: String = "test") = a
                    def run() = m(5)
                }
            """,
            "default-parameters"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('o');
                s2js.runtime.client.core.get().mixIn(o, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.m = function(a, b) {
                            var self = this;
                            if (typeof(a) === 'undefined') { a = self.m$default$1(); }
                            if (typeof(b) === 'undefined') { b = self.m$default$2(); }
                            return a;
                    };
                    obj.m$default$1 = function() { var self = this; return 10; };
                    obj.m$default$2 = function() { var self = this; return 'test'; };
                    obj.run = function() {
                        var self = this;
                        return self.m(5, undefined);
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('o', []);
                    return obj;
                }), true);
            """
        }
    }

    "Internal references" should "use 'self' for both the object itself and its members" in {
        compileScalaCode(
            """
                package p
                object o {
                    def f(s: String) = s
                    
                    def start() {
                        val member_ref = f _
                        val self_ref = o
                    }
                }
            """,
            "internal-references"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('p.o');
                s2js.runtime.client.core.get().mixIn(p.o, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.f = function(s) {
                        var self = this;
                        return s;
                    };
                    obj.start = function() {
                        var self = this;
                        var member_ref = function($s) { return self.f($s); }
                        ;
                        var self_ref = self;
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('p.o', []);
                    return obj;
                }), true);
            """
        }
    }

    "Nested functions" should "propagate self and maintain local member access" in {
        compileScalaCode(
            """
                package p
                object o {
                    def m(x: Int) = x + 1

                    def start() {
                        val f = (x: Int) => m(x)
                    }
                }
            """,
            "nested-functions"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('p.o');
                s2js.runtime.client.core.get().mixIn(p.o, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.m = function(x) {
                        var self = this;
                        return (x + 1);
                    };
                    obj.start = function() {
                        var self = this;
                        var f = function($x) {
                            return self.m($x);
                        };
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('p.o', []);
                    return obj;
                }), true);
            """
        }
    }

    "Nested lazy access" should "call the lazy getter via self inside a function" in {
        compileScalaCode(
            """
                package p
                object o {
                    lazy val x = 42
                    
                    def test() {
                        val f = () => x
                    }
                }
            """,
            "nested-lazy-access"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('p.o');
                s2js.runtime.client.core.get().mixIn(p.o, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.$x = function() {
                        return obj.x$lzycompute();
                    };
                    obj.bitmap$0 = 0;
                    obj.x$lzycompute = function() {
                        var self = this;
                        self.$synchronized(function() {
                            if ((! self.bitmap$0)) {
                                self.lazyval_x = s2js.runtime.client.core.get().asInstanceOf(42, 'scala.Int');
                                self.bitmap$0 = true;
                            } else {
                                undefined;
                            }
                        });
                        return self.lazyval_x;
                    };
                    obj.test = function() {
                        var self = this;
                        var f = function() {
                            return self.$x();
                        };
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('p.o', []);
                    return obj;
                }), true);
            """
        }
    }
}
