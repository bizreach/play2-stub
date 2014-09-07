package jp.co.bizreach.play2stub

import jp.co.bizreach.play2stub.RoutesCompiler._
import org.scalatest.{Matchers, FunSpec}
import java.io.File

/**
 * The source derives from
 *   https://github.com/playframework/playframework/tree/2.3.3/framework/src/routes-compiler/src/main/scala/play/router
 */
class StubRouteSpec extends FunSpec with Matchers {

  describe("route file parser") {

    def parseRoute(line: String) = {
      val rule = parseRule(line)
      assert(rule.isInstanceOf[Route])
      rule.asInstanceOf[Route]
    }

    def parseRule(line: String) = {
      val parser = new RouteFileParser
      val result = parser.parse(line)
//      def describeResult[T](result: parser.ParseResult[T]) = result match {
//        case parser.NoSuccess(msg, _) => msg
//        case _ => "successful"
//      }
      //result.successful aka describeResult(result) === true
      assert(result.get.size === 1)
      result.get.head
    }

    def parseError(line: String) = {
      val parser = new RouteFileParser
      val result = parser.parse(line)
      assert(result.isInstanceOf[parser.NoSuccess])
    }

    it("parse the HTTP method") {
//      assert(parseRoute("GET /s p.c.m").verb === HttpVerb("GET"))
      assert(parseRoute("GET /s ").verb === HttpVerb("GET"))
    }

    it("parse the HTTP method without controllers") {
//      assert(parseRoute("GET /s").verb === HttpVerb("GET"))
      assert(parseRoute("GET /s").verb === HttpVerb("GET"))
    }

    it("parse a static path") {
//      assert(parseRoute("GET /s p.c.m").path === PathPattern(Seq(StaticPart("s"))))
      assert(parseRoute("GET /s").path === PathPattern(Seq(StaticPart("s"))))
    }

    it("parse a path with dynamic parts and it should be encodeable") {
//      assert(parseRoute("GET /s/:d/s p.c.m").path === PathPattern(Seq(StaticPart("s/"), DynamicPart("d", "[^/]+", true), StaticPart("/s"))))
      assert(parseRoute("GET /s/:d/s").path === PathPattern(Seq(StaticPart("s/"), DynamicPart("d", "[^/]+", true), StaticPart("/s"))))
    }

    it("parse a path with multiple dynamic parts and it should not be encodeable") {
//      assert(parseRoute("GET /s/*e p.c.m").path === PathPattern(Seq(StaticPart("s/"), DynamicPart("e", ".+", false))))
      assert(parseRoute("GET /s/*e ").path === PathPattern(Seq(StaticPart("s/"), DynamicPart("e", ".+", false))))
    }

    it("path with regex should not be encodeable") {
//      assert(parseRoute("GET /s/$id<[0-9]+> p.c.m").path === PathPattern(Seq(StaticPart("s/"), DynamicPart("id", "[0-9]+", false))))
      assert(parseRoute("GET /s/$id<[0-9]+>").path === PathPattern(Seq(StaticPart("s/"), DynamicPart("id", "[0-9]+", false))))
    }

//    it("parse a single element package") {
//      assert(parseRoute("GET /s p.c.m").call.packageName === "p")
//    }
//
//    it("parse a multiple element package") {
//      assert(parseRoute("GET /s p1.p2.c.m").call.packageName === "p1.p2")
//    }
//
//    it("parse a controller") {
//      assert(parseRoute("GET /s p.c.m").call.controller === "c")
//    }
//
//    it("parse a method") {
//      assert(parseRoute("GET /s p.c.m").call.method === "m")
//    }
//
//    it("parse a parameterless method") {
//      assert(parseRoute("GET /s p.c.m").call.parameters === None)
//    }
//
//    it("parse a zero argument method") {
//      parseRoute("GET /s p.c.m()").call.parameters === Some(Seq())
//    }
//
//    it("parse method with arguments") {
//      assert(parseRoute("GET /s p.c.m(s1, s2)").call.parameters === Some(Seq(Parameter("s1", "String", None, None), Parameter("s2", "String", None, None))))
//    }
//
//    it("parse argument type") {
//      assert(parseRoute("GET /s p.c.m(i: Int)").call.parameters.get.head.typeName === "Int")
//    }
//
//    it("parse argument default value") {
//      assert(parseRoute("GET /s p.c.m(i: Int ?= 3)").call.parameters.get.head.default === Some("3"))
//    }
//
//    it("parse argument fixed value") {
//      assert(parseRoute("GET /s p.c.m(i: Int = 3)").call.parameters.get.head.fixed === Some("3"))
//    }
//
//    it("parse a non instantiating route") {
//      assert(parseRoute("GET /s p.c.m").call.instantiate === false)
//    }
//
//    it("parse an instantiating route") {
//      assert(parseRoute("GET /s @p.c.m").call.instantiate === true)
//    }

//    it("parse an include") {
//      val rule = parseRule("-> /s someFile")
//      assert(rule.isInstanceOf[Include])
//      assert(rule.asInstanceOf[Include].router === "someFile")
//      assert(rule.asInstanceOf[Include].prefix === "s")
//    }

    it("parse a comment with a route") {
//      assert(parseRoute("# some comment\nGET /s p.c.m").comments === Seq(Comment(" some comment")))
      assert(parseRoute("# some comment\nGET /s ").comments === Seq(Comment(" some comment")))
    }

    it("throw an error for an unexpected line") {parseError("foo")}

//    it("throw an error for an invalid path") {parseError("GET s p.c.m")}
    it("throw an error for an invalid path") {parseError("GET s ")}

    it("throw an error for no path") { parseError("GET")}

//    it("throw an error for no method") { parseError("GET /s") }

//    it("throw an error if no method specified") { parseError("GET /s p.c") }

//    it("throw an error for an invalid include path") { parseError("-> s someFile") }

//    it("throw an error if no include file specified") { parseError("-> /s") }
  }

  describe("route file compiler") {

    def withTempDir[T](block: File => T) = {
      val tmp = File.createTempFile("RoutesCompilerSpec", "")
      tmp.delete()
      tmp.mkdir()
      try {
        block(tmp)
      } finally {
        def rm(file: File): Unit = file match {
          case dir if dir.isDirectory =>
            dir.listFiles().foreach(rm)
            dir.delete()
          case f => f.delete()
        }
        rm(tmp)
      }
    }

//    describe("not generate reverse ref routing if its disabled") withTempDir { tmp =>
//      val f = new File(this.getClass.getClassLoader.getResource("generating.routes").toURI)
//      RoutesCompiler.compile(f, tmp, Seq.empty, generateReverseRouter = true, generateRefReverseRouter = false)
//
//      val generatedJavaRoutes = new File(tmp, "controllers/routes.java")
//      val contents = scala.io.Source.fromFile(generatedJavaRoutes).getLines().mkString("")
//      contents.contains("public static class ref") must beFalse
//    }
//
//    "generate routes classes for route definitions that pass the checks" in withTempDir { tmp =>
//      val file = new File(this.getClass.getClassLoader.getResource("generating.routes").toURI)
//      RoutesCompiler.compile(file, tmp, Seq.empty)
//
//      val generatedRoutes = new File(tmp, "generating/routes_routing.scala")
//      generatedRoutes.exists() must beTrue
//
//      val generatedReverseRoutes = new File(tmp, "generating/routes_reverseRouting.scala")
//      generatedReverseRoutes.exists() must beTrue
//    }
//
//    describe("check if there are no routes using overloaded handler methods") withTempDir { tmp =>
//      val file = new File(this.getClass.getClassLoader.getResource("duplicateHandlers.routes").toURI)
//      RoutesCompiler.compile(file, tmp, Seq.empty) must beLeft
//    }
//
//    describe("check if routes with type projection are compiled") withTempDir { tmp =>
//      val file = new File(this.getClass.getClassLoader.getResource("complexTypes.routes").toURI)
//      object A {
//        type B = Int
//      }
//      RoutesCompiler.compile(file, tmp, Seq.empty) must beRight
//    }
  }

}
