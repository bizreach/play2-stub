package jp.co.bizreach.play2stub

import jp.co.bizreach.play2handlebars.HBS
import play.api.mvc.{AnyContent, Action, Controller}

object StubController extends StubController

trait StubController extends Controller {

  /**
   * Generates an `Action` that serves a static resource.
   *
   */
  def at(): Action[AnyContent] = Action { request =>

    Stub.route(request).map { route =>
      route.data() match {
        case Some(d) =>
          route.template match {
            case Template(path, "hbs") =>
              Ok(HBS.any(path, d))
            case Template(path, engine) =>
              BadRequest(s"The engine: [$engine] is not supported for the request: [${route.path}]")
          }
          
        case None =>
          Ok(HBS(route.path))
          //BadRequest(s"[${route.path}] was not found")
        }

      }.getOrElse(
        // Just try to apply HBS template at the same path
        Ok(HBS(request.path))
      )
  }
}
