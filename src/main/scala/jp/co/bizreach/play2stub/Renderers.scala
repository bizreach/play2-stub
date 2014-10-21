package jp.co.bizreach.play2stub

import jp.co.bizreach.play2handlebars.HBS
import play.api.mvc.{Controller, Result, AnyContent, Request}

/**
 * Renderer generates some Result, usually with template files
 *   When judging the request doesn't matter with it, return None.
 */
trait Renderer {

  def render(path: String, route: Option[StubRoute] = None, params: Option[Any] = None)
            (implicit request: Request[AnyContent]): Option[Result]

}




/**
 * Render Handlebars template on top of Play2handlebars
 */
class HandlebarsRenderer extends Controller with Renderer {

  def render(path: String, route: Option[StubRoute] = None, params: Option[Any] = None)
            (implicit request: Request[AnyContent]): Option[Result] = {

    def renderHbs(path: String, params: Option[Any] = None): Result =
      Ok(params
        .map(p => HBS.any(path, p))
        .getOrElse(HBS(path)))

    def resolveTemplateWithRoute(r: StubRoute): Option[Template] =
      r.template(request) match {
        case Some(t) if t.engine == "hbs" => Some(t)
        case _=> None
      }

    val template = route
      .flatMap(resolveTemplateWithRoute)
      .getOrElse(Template(path, "hbs"))

    if (Stub.exists(template))
      Some(renderHbs(template.path, params))
    else
      None

  }
}

