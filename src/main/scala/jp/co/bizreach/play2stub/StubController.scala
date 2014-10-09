package jp.co.bizreach.play2stub

import jp.co.bizreach.play2handlebars.HBS
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws.{WSResponseHeaders, WS, WSResponse}
import play.api.mvc._
import play.mvc.Http.{HeaderNames, MimeTypes}
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object StubController extends StubController

trait StubController extends Controller {

//  implicit def withHC(result: Future[Result]): Future[Result] = {
//
//    result.map(_
//      .withHeaders()
//      .withCookies())
//  }

  /**
   * Generates an `Action` that serves a static resource.
   *
   */
  def at(path: String) = Action.async { implicit request =>
    Stub.route(request).map { route =>
      htmlFirstOr(route.path) {
        requestToProxyOr(route) {
          Future {
            routeResponse(route)
          }
        }
      }
    }.getOrElse(
      htmlFirstOr(request.path) {
        Future {
          simpleResponse(request.path)
        }
      }
    )
  }




  /**
   * Check if HTML file exists first, or execute a function
   */
  private def htmlFirstOr(path: String)(f: => Future[Result]): Future[Result] =
    Stub.html(path) match {
      case Some(html) =>
        Future {
          Ok(html).withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.HTML)
        }
      case None =>
        f
    }


  /**
   * https://github.com/playframework/playframework/issues/2239
   * https://www.playframework.com/documentation/2.3.5/ScalaWS
   */
  private def requestToProxyOr(route: StubRoute)(f: => Future[Result])
                            (implicit request:Request[AnyContent]): Future[Result] = {
    route.proxyUrl match {
      case Some(url) =>
        WS.url(url)
          //.withRequestTimeout(10000)
          .withFollowRedirects(follow = false)
          .withHeaders(request.headers.toSimpleMap.toSeq:_*)
          .withQueryString(request.queryString.mapValues(_.headOption.getOrElse("")).toSeq:_*)
          .withBody(request.body.asText.getOrElse(""))
          .withMethod(request.method)
          .getStream().map {
          case (response, body) =>
            Result(ResponseHeader(
              response.status,
              //flatten(response.headers)),
              response.headers.mapValues(_.headOption.getOrElse(""))),
              body)
        }
      case None =>
        f
    }
  }


  /**
   * When route is defined
   */
  private def routeResponse(route: StubRoute): Result =
    route.json() match {
      case Some(d) =>
        route.template match {
          case Some(Template(path, "hbs")) =>
            Ok(HBS.any(path, d))
          case Some(Template(path, engine)) =>
            BadRequest(s"The engine: [$engine] is not supported for the request: [${route.path}]")
          case None =>
            Ok(d.toString)
        }

      case None =>
        route.template match {
          case Some(Template(path, "hbs")) =>
            Ok(HBS(path))
          case Some(Template(path, engine)) =>
            BadRequest(s"The engine: [$engine] is not supported for the request: [${route.path}]")
          case None =>
            Ok(HBS(route.path))
        }
    }


  /**
   * When route is not defined
   */
  private def simpleResponse(path: String): Result = {
    (Stub.json(path), Stub.exists(Template(path, "hbs"))) match {
      case (Some(d), true) =>
        Ok(HBS.any(path, d))
      case (Some(d), false) =>
        Ok(d.toString).withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
      case (None, true) =>
        Ok(HBS(path))
      case (None, false) =>
        NotFound("Neither template file nor data file are found.")
    }
  }
}
