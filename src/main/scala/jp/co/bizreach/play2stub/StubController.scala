package jp.co.bizreach.play2stub

import jp.co.bizreach.play2handlebars.HBS
import play.api.mvc._
import play.mvc.Http.{HeaderNames, MimeTypes}

object StubController extends StubController

trait StubController extends Controller {

  /**
   * Generates an `Action` that serves a static resource.
   *
   */
  def at(path: String): Action[AnyContent] = Action { request =>

    Stub.route(request).map { route =>
      htmlFirstOr(route.path) {
        routeResponse(route)
      }
    }.getOrElse(
      htmlFirstOr(request.path) {
        simpleResponse(request.path)
      }
    )
  }


  /**
   * Check if HTML file exists first, or execute a function
   */
  private def htmlFirstOr(path: String)(f: => Result): Result =
    Stub.html(path) match {
      case Some(html) =>
        Ok(html).withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.HTML)
      case None =>
        f
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
    (Stub.json(path, Map.empty), Stub.exists(Template(path, "hbs"))) match {
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
