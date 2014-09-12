package jp.co.bizreach.play2stub

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import jp.co.bizreach.play2stub.RoutesCompiler.{Comment, Include, Route}
import org.apache.commons.io.{FilenameUtils, FileUtils}
import play.api.Play._
import play.api.mvc.{Result, RequestHeader}
import play.api.{Configuration, Logger, Application, Plugin}
import scala.collection.JavaConverters._


class StubPlugin(app: Application) extends Plugin {

  private lazy val logger = Logger("jp.co.bizreach.play2stub.StubPlugin")
  private val confBasePath = "play2stub"

  trait RouteHolder {
    // route information from application.conf
    val routes:Seq[StubRoute] = Seq.empty
    val dataPath = app.configuration.getString(confBasePath + ".data-root").getOrElse("/app/data")

  }

  lazy val holder = new RouteHolder {

    // TODO  Load filters

    private val routeList =
      current.configuration.getConfigList(confBasePath + ".routes")
        .map(_.asScala).getOrElse(Seq.empty)
    override val routes = routeList.map{ route =>
      val path = route.subKeys.mkString

      route.getConfig(path).map { inner =>
        StubRoute(
          route = parseRoute(path.replace("~", ":")),
          template = toTemplate(inner),
          data = inner.getString("data"),
          status = inner.getInt("status"),
          headers = toMap(inner.getConfig("headers")),
          params = toMap(inner.getConfig("params"))
        )
      }.get
    }
  }


  private def parseRoute(path: String): Route = {
    RoutesCompiler.parse(path) match {
      case Right(r: Route) => r
      case Right(unexpected) =>
        throw new RuntimeException(unexpected.toString)
      case Left(err) =>
        throw new RuntimeException(err)
    }
  }

  private def toTemplate(inner: Configuration): Option[Template] = try {
    inner.getConfig("template").map(c =>
      Some(Template(c.getString("path").getOrElse(""), c.getString("engine")))
    ).getOrElse(
        inner.getString("template").map(s => Template(s))
      )
  } catch {
    case ex:Throwable =>
      // TODO again
      //case ex: ConfigException.WrongType =>
      inner.getString("template").map(s => Template(s))
  }


  private def toMap(conf: Option[Configuration]): Map[String, String] =
    conf.map(_.entrySet
      .map(e => e._1 -> e._2.render()))
      .getOrElse(Map.empty).toMap


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
  headers: Map[String, String] = Map.empty
                       )

case class StubRoute(
  route: Route,
  template: Option[Template] = None,
  data: Option[String] = None,
  status: Option[Int] = None,
  headers: Map[String, String] = Map.empty,
  params: Map[String, String] = Map.empty) {

  def verb = route.verb
  def path = route.path
}


case class Template(path:String, engine:Option[String] = None)