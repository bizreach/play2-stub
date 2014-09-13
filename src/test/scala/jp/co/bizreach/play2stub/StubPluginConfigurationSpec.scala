package jp.co.bizreach.play2stub


import org.scalatest.{FunSpec, Matchers}
import play.api.test.{FakeHeaders, FakeRequest}
import play.test.Helpers


class StubPluginConfigurationSpec extends FunSpec with Matchers with FakePlayHelper {

  describe("route file parser") {

    it("should read application.conf") {
      runApp(PlayApp()) { app =>
        assert(app.plugin[StubPlugin] !== None)

        val plugin = app.plugin[StubPlugin].get
        assert(plugin.holder.routes.size === 7)
      }
    }

    it("should select proper routes") {
      runApp(PlayApp()) { app =>

        // Pattern 1 - path definition only
        val fakeRequest1 = FakeRequest(
          Helpers.GET,
          "pattern1")//,
          //FakeHeaders(),
          //""" {"name": "New Group", "collabs": ["foo", "asdf"]} """)
        val route1 = Stub.route(fakeRequest1)
        assert(route1.isDefined)
        assert(route1.get.path.toString === "pattern1")
        assert(route1.get.pathPattern.parts.size === 1)

        val data1 = route1.get.data()
        assert(data1.isDefined)
        assert(data1.get.get("who") !== null)
        assert(data1.get.get("who").asText() === "Play 2 Stub")


        // Pattern 2 - path definition with a parameter
        val fakeRequest2 = FakeRequest(Helpers.GET, "pattern2/Dr.")
        val route2 = Stub.route(fakeRequest2)
        val data2 = route2.get.data()
        assert(data2.get.get("who") === null)
        assert(data2.get.get("title").asText() === "Dr.")


        // Pattern 3 - path definition with a parameter, with data
        val fakeRequest3 = FakeRequest(Helpers.GET, "pattern3/Dr.")
        val route3 = Stub.route(fakeRequest3)
        val data3 = route3.get.data()
        assert(data3.get.get("who").asText() === "Play 2 Stub")
        assert(data3.get.get("title").asText() === "Dr.")


      }
    }

    it("should recognize parameters") {
      runApp(PlayApp()) { app =>
        val fakeRequest3 = FakeRequest(Helpers.GET, "author1/kobayashi/books")
        val route3 = Stub.route(fakeRequest3)

        assert(route3.isDefined)
        assert(route3.get.pathPattern.toString === "author1/$authorId<[^/]+>/books")
        assert(route3.get.pathPattern.parts.size === 3)
        assert(route3.get.pathPattern.parts(0) === StaticPart("author1/"))
        assert(route3.get.params.path("authorId") === Right("kobayashi"))
        assert(route3.get.pathPattern.parts(2) === StaticPart("/books"))
      }
    }
  }
}
