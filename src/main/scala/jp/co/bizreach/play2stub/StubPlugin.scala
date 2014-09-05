package jp.co.bizreach.play2stub

import java.io.File

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import jp.co.bizreach.play2stub.RoutesCompiler.Route
import org.apache.commons.io.{FilenameUtils, FileUtils}
import play.api.Play._
import play.api.http.Status
import play.api.mvc.{Result, RequestHeader}
import play.api.{Configuration, Logger, Application, Plugin}

/**
 * Created by scova0731 on 8/31/14.
 */
class StubPlugin(app: Application) extends Plugin {

  private lazy val logger = Logger("jp.co.bizreach.play2stub.StubPlugin")
  private val confBasePath = "play2stub"

  trait RouteHolder {
    // route information from apolication.conf
    def routes:Seq[StubRoute] = Seq.empty
    val dataPath = app.configuration.getString(confBasePath + ".data-root").getOrElse("/app/data")

  }

  lazy val holder = new RouteHolder {

    //   Load common
    val routeConfig = current.configuration.getConfig(confBasePath + ".routes").get
    routeConfig.subKeys.map { path =>
      //   Load routes
      val route = RoutesCompiler.parse(path) match {
        case Some(r:Route) => r
        case _ => throw new NoSuchElementException
      }
      val template = routeConfig.getConfig(path + ".template")
      val data = routeConfig.getString(path + ".data")
      val params = routeConfig.getConfig(path + ".params")
      val headers = routeConfig.getConfig(path + ".headers")
      val status = routeConfig.getInt(path + ".status")

      //   Load filters

    }


  }


  override def onStart(): Unit = {
    current.configuration
    // Load application.conf

    holder
  }



  override def onStop(): Unit = super.onStop()



  override def enabled: Boolean = super.enabled
}

object Stub {

  def template(params:Map[String, String]) = {}

  def addHeaders(result:Result):Result = {
    result.withHeaders("Content-Type" -> "application/json")
  }


  /**
   *
   */
  // TODO merger params into JsonNode
  def data(path: String, params:Map[String, String] = Map.empty):Option[JsonNode] = {
    val pathWithExtension = if (FilenameUtils.getExtension(path).isEmpty) path + ".json" else path
    val file = FileUtils.getFile(System.getProperty("user.dir"), holder.dataPath, pathWithExtension)
    if (file.exists()) {
      val mapper = new ObjectMapper()
      Some(mapper.readTree(FileUtils.readFileToString(file)))
    } else
      None
  }


  def route(request: RequestHeader):Option[StubRoute] =
    holder.routes
      .find(r => r.verb.value == request.method && r.path.has(request.path))
      //.map(r => RouteParams(r.path, request.queryString))


  private[play2stub] def holder = current.plugin[StubPlugin].map(_.holder)
    .getOrElse(throw new IllegalStateException("StubPlugin is not installed"))

}

object StubRoute {



  def init(): StubRoute = {

    val configRoot = current.configuration.getConfig("playt2stub")


    StubRoute(null)
  }

}

case class Stub(
  filters: Seq[StubFilter] = Seq.empty,
  routes: Seq[StubRoute] = Seq.empty
                 )

case class StubFilter(
  headers: Seq[(String, String)] = Seq.empty
                       )

case class StubRoute(
  route: Route,
  template: Option[Template] = None,
  data: Option[String] = None,
  status: Option[Status] = None,
  headers: Seq[(String, String)] = Seq.empty,
  params: Seq[(String, String)] = Seq.empty) {

  def verb = route.verb
  def path = route.path
}


case class Template(path:String, engine:String)