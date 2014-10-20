package jp.co.bizreach.play2stub

import com.fasterxml.jackson.databind.ObjectMapper
import jp.co.bizreach.play2handlebars.HBS
import play.api.Logger
import play.api.libs.ws.WS
import play.api.mvc._
import play.mvc.Http.{HeaderNames, MimeTypes}
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object StubController extends StubController

trait StubController extends Controller {

  /**
   * Generates an `Action` that serves a static resource.
   *
   */
  def at(path: String) = Action.async { implicit request =>

    process(path)

  }


  /**
   * Execute before-filters, main process and after-filters
   */
  protected def process(path: String)(implicit firstReq: Request[AnyContent]): Future[Result] = {

    val request =
      Stub.beforeFilters.foldLeft(firstReq) { case (filteredReq, filter) =>
        filter.process(filteredReq)
      }

    Stub.process.getOrElse(Future { NotFound }).map(firstRes =>

      Stub.afterFilters.foldLeft(firstRes) { case (filteredRes, filter) =>
        filter.process(request, filteredRes)
      }
    )
  }


//  /**
//   * Main processing part.
//   */
//  protected def processMain(path: String)(implicit request: Request[AnyContent]): Future[Result] =
//    Stub.route(request).map { route =>
//      htmlFirstOr(route.path) {
//        requestToProxyOr(route) {
//          Future {
//            routeResponse(route)
//          }
//        }
//      }
//    }.getOrElse(
//        htmlFirstOr(request.path) {
//          Future {
//            simpleResponse(request.path)
//          }
//        }
//      )
//
//
//
//  /**
//   * Check if HTML file exists first, or execute a function
//   */
//  protected def htmlFirstOr(path: String)(f: => Future[Result]): Future[Result] =
//    Stub.html(path) match {
//      case Some(html) =>
//        Future {
//          Ok(html).withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.HTML)
//        }
//      case None =>
//        f
//    }
//
//
//  /**
//   * https://github.com/playframework/playframework/issues/2239
//   * https://www.playframework.com/documentation/2.3.5/ScalaWS
//   */
//  protected def requestToProxyOr(route: StubRoute)(f: => Future[Result])
//                            (implicit request:Request[AnyContent]): Future[Result] = {
//    route.proxyUrl match {
//      case Some(url) =>
//        val holder = WS.url(url)
//          //.withRequestTimeout(10000)
//          .withFollowRedirects(follow = false)
//          .withHeaders(request.headers.toSimpleMap.toSeq:_*)
//          .withQueryString(request.queryString.mapValues(_.headOption.getOrElse("")).toSeq:_*)
//          .withBody(request.body.asText.getOrElse(""))
//          .withMethod(request.method)
//
//        route.template(request) match {
//          case Some(t) =>
//            holder.execute().map { response =>
//              Logger.debug(s"ROUTE: Proxy:$url, Template:${t.path}")
//
//              if (response.status < 300) {
//                // Convert to Jackson node instead of Play.api.Json currently
//                val jsonNode = new ObjectMapper().readTree(
//                  response.underlying[com.ning.http.client.Response].getResponseBodyAsBytes)
//
//                Ok(HBS(t.path, route.flatParams ++ extraParamsForTemplate + ("res" -> jsonNode)))
//                  .withHeaders((response.allHeaders.mapValues(_.headOption.getOrElse("")) -
//                  HeaderNames.CONTENT_LENGTH - HeaderNames.CONTENT_TYPE).toSeq: _*)
//                  .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.HTML) // TODO think again
//
//              } else
//                Status(response.status)(response.body)
//                  .withHeaders(response.allHeaders.mapValues(_.headOption.getOrElse("")).toSeq:_*)
//            }
//
//          case None =>
//            holder.stream().map { case (response, body) =>
//              Logger.debug(s"ROUTE: Proxy:$url, Stream")
//
//              Status(response.status)
//                  .chunked(body)
//                  .withHeaders(response.headers.mapValues(_.headOption.getOrElse("")).toSeq:_*)
//            }
//        }
//
//      case None =>
//        f
//    }
//  }
//
//
//  /**
//   * When route is defined
//   */
//  protected def routeResponse(route: StubRoute)
//                           (implicit request:Request[AnyContent]): Result =
//    Stub.json(route) match {
//      case Some(d) =>
//        route.template(request) match {
//          case Some(Template(path, "hbs")) =>
//            Ok(HBS.any(path, d))
//          case Some(Template(path, engine)) =>
//            BadRequest(s"The engine: [$engine] is not supported for the request: [${route.path}]")
//          case None =>
//            Ok(d.toString)
//        }
//
//      case None =>
//        route.template(request) match {
//          case Some(Template(path, "hbs")) =>
//            Ok(HBS(path))
//          case Some(Template(path, engine)) =>
//            BadRequest(s"The engine: [$engine] is not supported for the request: [${route.path}]")
//          case None =>
//            Ok(HBS(route.path))
//        }
//    }
//
//
//  /**
//   * When route is not defined
//   */
//  protected def simpleResponse(path: String): Result = {
//    (Stub.json(path), Stub.exists(Template(path, "hbs"))) match {
//      case (Some(d), true) =>
//        Ok(HBS.any(path, d))
//      case (Some(d), false) =>
//        Ok(d.toString).withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
//      case (None, true) =>
//        Ok(HBS(path))
//      case (None, false) =>
//        NotFound("Neither template file nor data file are found.")
//    }
//  }
//
//
//  /**
//   * Pass extra information as params only for template rendering
//   */
//  protected def extraParamsForTemplate(implicit request:Request[AnyContent]) =
//    Map("rawQueryString" -> request.rawQueryString, "path" -> request.path)
}
