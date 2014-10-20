package jp.co.bizreach.play2stub

import java.io.File

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}
import jp.co.bizreach.play2stub.RoutesCompiler.Route
import org.apache.commons.io.{FilenameUtils, FileUtils}
import play.api.Play._
import play.api.mvc.{Result, Request, AnyContent, RequestHeader}
import play.core.Router.RouteParams

import scala.concurrent.Future

object Stub {

  def beforeFilters: Seq[BeforeFilter] = holder.beforeFilters
  def afterFilters: Seq[AfterFilter] = holder.afterFilters
  def templateResolver: TemplateResolver = holder.templateResolver


  /**
   *
   */
  def route(request: RequestHeader): Option[StubRoute] =
    holder.routes
      .find(conf =>
        conf.route.verb.value == request.method &&
          conf.route.path(request.path).isDefined)
      .map { conf =>
        conf.route.path(request.path).map { groups =>
          StubRoute(
            conf = conf,
            params = RouteParams(groups, request.queryString),
            path = request.path)
      }.get
    }
  //.map(r => RouteParams(r.path, request.queryString))


  def render(path: String, route: Option[StubRoute] = None, params: Option[Any] = None)
            (implicit request: Request[AnyContent]): Option[Result] =
    holder.renderers.foldLeft(None:Option[Result]) { case (result, renderer) =>
      if (result.isEmpty)
        renderer.render(path, route, params)
      else
        result
    }


  def process(implicit request: Request[AnyContent]):Option[Future[Result]] = {
    implicit val route = Stub.route(request)
    holder.processors.foldLeft(None:Option[Future[Result]]) { case (result, processor) =>
      processor.process
    }
  }

  def params(implicit request: Request[AnyContent]): Map[String, Any] = {
    implicit val route = Stub.route(request)
    holder.paramBuilders.foldLeft(Map[String, Any]()){ case (map, builder) =>
      builder.build
    }
  }

  /**
   *
   */
  def exists(t: Template): Boolean =
    pathWithExtension(t.path, t.engine, isData = false).exists


  /**
   * Read json data file and merge parameters into the json
   */
  def json(route: StubRoute, extraParams:Map[String, String] = Map.empty):Option[JsonNode] =
    json(route.dataPath, route.flatParams ++ extraParams)


  /**
   * Read json data file and merge parameters into the json
   */
  def json(path:String): Option[JsonNode] =
    json(path, Map[String, String]())


  /**
   * Read json data file and merge parameters into the json
   */
  def json(path:String, params: Map[String, String]): Option[JsonNode] = {
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
  def html(path:String): Option[String] = {
    val htmlFile = pathWithExtension(path, "html", isData = false)

    if (htmlFile.exists())
      Some(FileUtils.readFileToString(htmlFile, "UTF-8"))
    else
      None
  }


  // TODO check if the path start with http(s)://
  def proxyUrl(proxyPath: Option[String]): Option[String] =
    (holder.proxyRoot, holder.isProxyEnabled) match {
      case (Some(root), true) => proxyPath.map(root + _)
      case (None, true) => proxyPath
      case (_, false) => None
    }


  private[play2stub] lazy val holder = current.plugin[StubPlugin].map(_.holder)
    .getOrElse(throw new IllegalStateException("StubPlugin is not installed"))


  private[play2stub] def pathWithExtension(
    path: String, extension: String,
    params: Map[String, String] = Map.empty, isData: Boolean = true): File = {

    val rootDir =
      if (isData) holder.dataRoot
      else holder.viewRoot

    val filledPath = params.foldLeft(path){ (filled, param) =>
      filled.replace(":" + param._1, param._2)
    }

    val pathWithExt =
      if (FilenameUtils.getExtension(filledPath).isEmpty) filledPath + "." + extension
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
  def template(request: Request[AnyContent]): Option[Template] =
    Stub.templateResolver.resolve(request, this)
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
  params: Map[String, String] = Map.empty
)


case class Template(
  path:String,
  engine:String
)