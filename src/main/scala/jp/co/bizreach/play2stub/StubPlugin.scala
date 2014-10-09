package jp.co.bizreach.play2stub

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import jp.co.bizreach.play2stub.RoutesCompiler.Route
import org.apache.commons.io.{FilenameUtils, FileUtils}
import play.api.Play._
import play.api.mvc.{Result, RequestHeader}
import play.api.{Configuration, Logger, Application, Plugin}
import play.core.Router.RouteParams
import scala.collection.JavaConverters._


class StubPlugin(app: Application) extends Plugin {

  private lazy val logger = Logger("jp.co.bizreach.play2stub.StubPlugin")
  private val basePath = "play2stub"

  val engineConf = app.configuration.getString(basePath + ".engine").getOrElse("hbs")
  val dataRootConf = app.configuration.getString(basePath + ".data-root").getOrElse("/app/data")
  val viewRootConf = app.configuration.getString(basePath + ".view-root").getOrElse("/app/views")
  val proxyRootConf = app.configuration.getString(basePath + ".proxy-root")

  trait RouteHolder {
    val routes: Seq[StubRouteConfig]
    val engine: String = engineConf
    val dataRoot: String = dataRootConf
    val viewRoot: String = viewRootConf
    val proxyRoot: Option[String] =proxyRootConf
    val isProxyEnabled: Boolean = proxyRootConf.isDefined
  }


  lazy val holder = new RouteHolder {

    // TODO  Load filters

    private val routeList =
      current.configuration.getConfigList(basePath + ".routes")
        .map(_.asScala).getOrElse(Seq.empty)
    
    override val routes = routeList.map{ route =>
      val path = route.subKeys.mkString

      route.getConfig(path).map { inner =>
        StubRouteConfig(
          route = parseRoute(path.replace("~", ":")),
          proxy = inner.getString("proxy"),
          template = toTemplate(inner),
          data = inner.getString("data"),
          status = inner.getInt("status"),
          noResponse = inner.getBoolean("noResponse").getOrElse(false),
          headers = toMap(inner.getConfig("headers")),
          params = toMap(inner.getConfig("params"))
        )
      }.get
    }
  }


  /**
   *  Instantiate stub configuration holder on starting up
   */
  override def onStart(): Unit = {
    holder
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


  private def toTemplate(inner: Configuration): Option[Template] =
    if (inner.subKeys.contains("template")) {
      val path =
        if (inner.keys.contains("template.path"))
          inner.getString("template.path").get
        else
          inner.getString("template").get
      val engine =
        if (inner.keys.contains("template.engine"))
          inner.getString("template.engine").get
        else
          engineConf

      Some(Template(path, engine))

    } else
      None


  private def toMap(conf: Option[Configuration]): Map[String, String] =
    conf.map(_.entrySet
      .map(e => e._1 -> e._2.render()))
      .getOrElse(Map.empty).toMap
}


object Stub {

  def template(params:Map[String, String]) = {}

  def addHeaders(result:Result):Result = {
    result.withHeaders("Content-Type" -> "application/json")
  }


  /**
   *
   *
   */
  def route(request: RequestHeader):Option[StubRoute] =
    config.routes
      .find(conf => conf.route.verb.value == request.method
                 && conf.route.path(request.path).isDefined)
      .map { conf =>
        conf.route.path(request.path).map { groups =>
          StubRoute( 
            conf = conf,
            params = RouteParams(groups, request.queryString), 
            path = request.path)
        }.get
      }
      //.map(r => RouteParams(r.path, request.queryString))


  def exists(t: Template): Boolean =
    pathWithExtension(t.path, t.engine, isData = false).exists



  /**
   * Read json data file and merge parameters into the json
   */
  def json(path:String, origParams:Map[String, String] = Map.empty,
           extraParams:Map[String, String] = Map.empty):Option[JsonNode] = {
    val params = origParams ++ extraParams
    val jsonFile = pathWithExtension(path, "json", params)

    if (jsonFile.exists()) {
      val json = new ObjectMapper().readTree(FileUtils.readFileToString(jsonFile, "UTF-8"))
      json match {
        case node: ObjectNode =>
          params.foreach { case (k, v) => node.put(k, v)}
        case _ =>
      }
      Some(json)

    } else if (params.nonEmpty) {
      val node = new ObjectMapper().createObjectNode()
      params.foreach{ case (k, v) => node.put(k, v) }
      Some(node)

    } else
      None
  }


  /**
   * Read static html file
   */
  def html(path:String):Option[String] = {
    val htmlFile = pathWithExtension(path, "html", isData = false)

    if (htmlFile.exists())
      Some(FileUtils.readFileToString(htmlFile, "UTF-8"))
    else
      None
  }


  // TODO implement later
  def proxyUrl(proxyPath:Option[String]):Option[String] =
    config.proxyRoot.map(_ + proxyPath.getOrElse(""))

  
  private[play2stub] def config = current.plugin[StubPlugin].map(_.holder)
    .getOrElse(throw new IllegalStateException("StubPlugin is not installed"))


  private[play2stub] def pathWithExtension(
    path: String, ext: String, params: Map[String, String] = Map.empty, isData: Boolean = true) = {
    val rootDir =
      if (isData) Stub.config.dataRoot
      else Stub.config.viewRoot

    val filledPath = params.foldLeft(path){ (filled, param) =>
      filled.replace(":" + param._1, param._2)
    }

    val pathWithExt = 
      if (FilenameUtils.getExtension(filledPath).isEmpty) filledPath + "." + ext
      else filledPath

    FileUtils.getFile(
      System.getProperty("user.dir"),
      rootDir, pathWithExt)
  }
}


case class StubRoute(
  conf: StubRouteConfig,
  params: RouteParams,
  path: String) {

  def verb = conf.route.verb
  def pathPattern = conf.route.path
  def dataPath = conf.data.getOrElse(path)
  def proxyUrl = Stub.proxyUrl(conf.proxy)


  /**
   * Get parameter maps from both url path part and query string part
   */
  def flatParams: Map[String, String] = {
    val fromPath = params.path
      .withFilter(_._2.isRight).map(p => p._1 -> p._2.right.get)
    val fromQuery = params.queryString
      .withFilter(_._2.length > 0).map(q => q._1 -> q._2(0))
    fromPath ++ fromQuery
  }


  /**
   *
   */
  def template: Option[Template] =
    conf.template.map{t =>
      val filledPath = flatParams.foldLeft(t.path) { (filled, param) =>
        filled.replace(":" + param._1, param._2)
      }
      t.copy(path = filledPath)
    }


  /**
   * Read json data file and merge parameters into the json
   */
  def json(extraParams:Map[String, String] = Map.empty):Option[JsonNode] =
    Stub.json(dataPath, flatParams, extraParams)

}


case class Stub(
  filters: Seq[StubFilter] = Seq.empty,
  routes: Seq[StubRouteConfig] = Seq.empty
                 )

case class StubFilter(
  headers: Map[String, String] = Map.empty
                       )

case class StubRouteConfig(
  route: Route,
  template: Option[Template] = None,
  proxy: Option[String] = None,
  data: Option[String] = None,
  status: Option[Int] = None,
  noResponse: Boolean = false,
  headers: Map[String, String] = Map.empty,
  params: Map[String, String] = Map.empty)


case class Template(path:String, engine:String)