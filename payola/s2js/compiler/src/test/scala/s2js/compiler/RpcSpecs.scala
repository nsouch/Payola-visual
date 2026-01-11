package s2js.compiler

class RpcSpecs extends CompilerIndependentSpec
{
    "Remote objects" should "not be compiled" in {
        compileScalaCode(
            """
                package server {
                    @s2js.compiler.remote object o {
                        def foo(bar: Int, baz: String): Int = bar * baz.length
                    }
                }
            """,
            "remote-objects-not-compiled"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('server.o');
            """
        }
    }

    "Synchronous remote method calls" should "be translated into synchronous rpc calls" in {
        compileScalaCode(
            """
                package server {
                    @s2js.compiler.remote object o {
                        def foo(bar: Int, baz: String): Int = bar * baz.length
                    }
                }

                object client {
                    def main() {
                        val fooValue = server.o.foo(2, "xyz")
                    }
                }
            """,
            "synchronous-remote-method-call"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('client');
                s2js.runtime.client.core.get().classLoader.provide('server.o');
                s2js.runtime.client.core.get().mixIn(client, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.main = function() {
                        var self = this;
                        var fooValue = s2js.runtime.client.rpc.Wrapper.get().callSync('server.o.foo', [2, 'xyz'],
                            ['scala.Int', 'java.lang.String']);
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('client', []);
                    return obj;
                }), true);
            """
        }
    }

    "Parameters of collection types" should "be supported" in {
        compileScalaCode(
            """
                package server {
                    @s2js.compiler.remote object o {
                        def foo(bar: List[Int], baz: List[String], bat: List[Double]): Int = {
                            bar.length + baz.length + bat.length
                        }
                    }
                }

                object client {
                    def main() {
                        val fooValue = server.o.foo(List(1, 2, 3), List("aaa", "bbb", "ccc"), List(1.1, 2.2, 3.0))
                    }
                }
            """,
            "collection-type-parameters"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('client');
                s2js.runtime.client.core.get().classLoader.provide('server.o');
                s2js.runtime.client.core.get().classLoader.require('scala.collection.immutable.List');
                s2js.runtime.client.core.get().mixIn(client, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.main = function() {
                        var self = this;
                        var fooValue = s2js.runtime.client.rpc.Wrapper.get().callSync('server.o.foo',
                            [scala.collection.immutable.List.get().$apply(1, 2, 3),
                            scala.collection.immutable.List.get().$apply('aaa', 'bbb', 'ccc'),
                            scala.collection.immutable.List.get().$apply(1.1, 2.2, 3.0)],
                            ['scala.collection.immutable.List[scala.Int]',
                                'scala.collection.immutable.List[java.lang.String]',
                                'scala.collection.immutable.List[scala.Double]']);
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('client', []);
                    return obj;
                }), true);
            """
        }
    }

    "Asynchronous remote method calls" should "be translated into asynchronous rpc calls" in {
        compileScalaCode(
            """
                import s2js.compiler.async

                package server {
                    @s2js.compiler.remote object o {
                        @async def foo(bar: String)(successCallback: Int => Unit)(errorCallback: Throwable => Unit) {
                            successCallback(bar.length)
                        }

                        @async def bar()(successCallback: () => Unit)(errorCallback: Throwable => Unit) {
                            successCallback()
                        }
                    }
                }

                object client {
                    def main() {
                        var x = 0
                        server.o.foo("xyz") {i => x = i} {e => x = -1}
                        server.o.bar() { () => x = 1 } { e => x = 0 }
                    }
                }
            """,
            "asynchronous-remote-method-call"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('client');
                s2js.runtime.client.core.get().classLoader.provide('server.o');
                s2js.runtime.client.core.get().mixIn(client, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.main = function() {
                        var self = this;
                        var x = 0;
                        s2js.runtime.client.rpc.Wrapper.get().callAsync('server.o.foo', ['xyz'],
                            ['java.lang.String'], function(i) { x = i; }, function(e) { x = -1; });
                        s2js.runtime.client.rpc.Wrapper.get().callAsync('server.o.bar', [], [],
                            function() { x = 1; }, function(e) { x = 0; });
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('client', []);
                    return obj;
                }), true);
            """
        }
    }

    "Secured remote methods" should "support synchronous and asynchronous modes" in {
        compileScalaCode(
            """
                import s2js.compiler._

                package server
                {
                    @s2js.compiler.remote object o
                    {
                        @secured def foo(bar: Int, securityContext: AnyRef = null): Int = bar * 42

                        @secured @async def bar(bar: String, securityContext: AnyRef = null)
                            (successCallback: (Int => Unit))(errorCallback: (Throwable => Unit)) {
                            successCallback(bar.length)
                        }
                    }
                }

                object client {
                    def main() {
                        server.o.foo(123)
                        var x = 0
                        server.o.bar("xyz") {i => x = i} {e => x = -1}
                    }
                }
            """,
            "secured-remote-methods"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('client');
                s2js.runtime.client.core.get().classLoader.provide('server.o');
                s2js.runtime.client.core.get().mixIn(client, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.main = function() {
                        var self = this;
                        s2js.runtime.client.rpc.Wrapper.get().callSync('server.o.foo', [123], ['scala.Int']);
                        var x = 0; s2js.runtime.client.rpc.Wrapper.get().callAsync('server.o.bar', ['xyz'],
                            ['java.lang.String'], function(i) { x = i; }, function(e) { x = -1; });
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('client', []);
                    return obj;
                }), true);
            """
        }
    }

    "Secured remote objects" should "be supported" in {
        compileScalaCode(
            """
                import s2js.compiler._

                package server
                {
                    @s2js.compiler.remote @secured object o
                    {
                        def foo(bar: Int, securityContext: AnyRef = null): Int = bar * 42

                        @async def bar(bar: String, securityContext: AnyRef = null)
                            (successCallback: (Int => Unit))(errorCallback: (Throwable => Unit)) {
                            successCallback(bar.length)
                        }
                    }
                }

                object client {
                    def main() {
                        server.o.foo(123)
                        var x = 0
                        server.o.bar("xyz") {i => x = i} {e => x = -1}
                    }
                }
            """,
            "secured-remote-objects"
        ) shouldCompileTo {
            """
                s2js.runtime.client.core.get().classLoader.provide('client');
                s2js.runtime.client.core.get().classLoader.provide('server.o');
                s2js.runtime.client.core.get().mixIn(client, new s2js.runtime.client.core.Lazy(function() {
                    var obj = {};
                    obj.main = function() {
                        var self = this;
                        s2js.runtime.client.rpc.Wrapper.get().callSync('server.o.foo', [123], ['scala.Int']);
                        var x = 0;
                        s2js.runtime.client.rpc.Wrapper.get().callAsync('server.o.bar', ['xyz'],
                            ['java.lang.String'], function(i) { x = i; }, function(e) { x = -1; });
                    };
                    obj.__class__ = new s2js.runtime.client.core.Class('client', []);
                    return obj;
                }), true);
            """
        }
    }
}
