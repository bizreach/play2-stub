package jp.co.bizreach.play2stub

import java.io.File
import java.net.{URL, URI}

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{ObjectMapper, JsonNode}
import jp.co.bizreach.play2stub.RoutesCompiler.Route
import org.apache.commons.io.{FilenameUtils, FileUtils}
import play.api.Play._
import play.api.libs.ws.WSResponse
import play.api.mvc.{Result, Request, AnyContent, RequestHeader}
import play.core.Router.RouteParams
import play.utils.UriEncoding

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
        ((conf.route.verb.value == request.method) || (conf.route.verb.value == "GET" && request.method == "HEAD"))
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
      if (result.isEmpty)
        processor.process
      else
        result
    }
  }

  def params(response: Option[WSResponse] = None)(implicit request: Request[AnyContent]): Map[String, Any] = {
    implicit val route = Stub.route(request)
    holder.paramBuilders.foldLeft(Map[String, Any]()){ case (map, builder) =>
      map ++ builder.build(response)
    }
  }

  /**
   *
   */
  def exists(t: Template): Boolean =
    pathWithExtension(t.path, t.engine, isData = false).isDefined


  /**
   * Read json data file and merge parameters into the json
   *   To escape method overload issue, many json() methods are needed.
   */
  def json(route: StubRoute):Option[JsonNode] =
    makeJson(route.dataPath: String, route.flatParams, requireFile = false)


  /**
   * Read json data file and merge parameters into the json
   */
  def json(route: StubRoute, extraParams:Map[String, Any]):Option[JsonNode] =
    makeJson(route.dataPath, route.flatParams ++ extraParams, requireFile = false)


  /**
   * Read json data file and merge parameters into the json
   */
  def json(route: StubRoute, requireFile: Boolean):Option[JsonNode] =
    makeJson(route.dataPath: String, route.flatParams, requireFile)


  /**
   * Read json data file and merge parameters into the json
   */
  def json(route: StubRoute, extraParams:Map[String, Any], requireFile: Boolean):Option[JsonNode] =
    makeJson(route.dataPath, route.flatParams ++ extraParams, requireFile)


  /**
   * Read json data file and merge parameters into the json
   */
  def json(path:String): Option[JsonNode] =
    makeJson(path, Map[String, String](), requireFile = false)


  /**
   * Read json data file and merge parameters into the json
   */
  def json(path:String, requireFile: Boolean = false): Option[JsonNode] =
    makeJson(path, Map[String, String](), requireFile)


  /**
   * Read json data file and merge parameters into the json
   */
  private def makeJson(path:String, params: Map[String, Any], requireFile: Boolean): Option[JsonNode] = {
    val jsonFile = pathWithExtension(path, "json", params)

    if (jsonFile.isDefined) {
      val json = new ObjectMapper().readTree(
        FileUtils.readFileToString(new File(jsonFile.get.toURI), "UTF-8"))
      json match {
        case node: ObjectNode =>
          params.foreach { case (k, v) => node.putPOJO(k, v)}
        case _ =>
      }
      Some(json)

    } else if (!requireFile && params.nonEmpty) {
      val node = new ObjectMapper().createObjectNode()
      params.foreach{ case (k, v) => node.putPOJO(k, v) }
      Some(node)

    } else
      None
  }


  /**
   * Read static html file
   */
  def html(path:String): Option[String] = {
    pathWithExtension(path, "html", isData = false)
      .map(url => FileUtils.readFileToString(new File(url.toURI), "UTF-8"))
  }


  // TODO check if the path start with http(s)://
  def proxyUrl(proxyPath: Option[String], params: Map[String, String]): Option[String] =
    ((holder.proxyRoot, holder.isProxyEnabled) match {
      case (Some(root), true) => proxyPath.map(root + _)
      case (None, true) => proxyPath
      case (_, false) => None
    }).map(path => params.foldLeft(path){ (filled, param) =>
      filled.replace(":" + param._1, param._2)
    })


  private[play2stub] lazy val holder = current.plugin[StubPlugin].map(_.holder)
    .getOrElse(throw new IllegalStateException("StubPlugin is not installed"))


  private[play2stub] def pathWithExtension(
    path: String, extension: String,
    params: Map[String, Any] = Map.empty, isData: Boolean = true): Option[URL] = {

    val filledPath = params.foldLeft(path){ (filled, param) =>
      filled.replace(":" + param._1, param._2.toString)
    }

    val pathWithExt =
      if (FilenameUtils.getExtension(filledPath).isEmpty) filledPath + "." + extension
      else filledPath

    holder.fileLoader.load(pathWithExt, isData)
  }
}


case class StubRoute(
                      conf: StubRouteConfig,
                      params: RouteParams,
                      path: String) {

  def verb = conf.route.verb
  def pathPattern = conf.route.path
  def dataPath = conf.data.getOrElse(path)
  def proxyUrl = Stub.proxyUrl(conf.proxy, flatParamsEncoded)
  def redirectUrl = conf.redirect


  /**
   * Get parameter maps from both url path part and query string part
   */
  lazy val flatParams: Map[String, String] = {
    val fromPath = params.path
      .withFilter(_._2.isRight).map(p => p._1 -> p._2.right.get)
    val fromQuery = params.queryString
      .withFilter(_._2.length > 0).map(q => q._1 -> q._2(0))
    fromPath ++ fromQuery ++ conf.params
  }

  lazy val flatParamsEncoded: Map[String, String] = {
    def isEncodeable(keyName: String): Boolean =
      conf.route.path.parts.exists {
        case DynamicPart(name, _, encodeable) => keyName == name && encodeable
        case _=> false
      }

    val fromPath = params.path
      .withFilter(_._2.isRight).map(p => p._1 ->
        (if(isEncodeable(p._1)) UriEncoding.encodePathSegment(p._2.right.get, "UTF-8") else p._2.right.get))
    val fromQuery = params.queryString
      .withFilter(_._2.length > 0).map(q => q._1 -> UriEncoding.encodePathSegment(q._2(0), "UTF-8"))

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
  redirect: Option[String] = None,
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