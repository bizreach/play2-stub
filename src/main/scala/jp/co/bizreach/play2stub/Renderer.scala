package jp.co.bizreach.play2stub

import jp.co.bizreach.play2handlebars.HBS
import play.api.mvc.{Controller, Result, AnyContent, Request}


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

    val canRender = route
      .map(resolveTemplateWithRoute)
      .getOrElse(Some(Template(path, "hbs")))
      .exists(Stub.exists)

    if (canRender)
      Some(renderHbs(path, params))
    else
      None

//    route.map(r =>
//      r.template(request) match {
//        case Some(t) if t.engine == "hbs" && Stub.exists(t) =>
//          Some(renderHbs(path, params))
//        case _ =>
//          None
//      }
//    ).getOrElse(
//        if (Stub.exists(Template(path, "hbs")))
//          Some(renderHbs(path, params))
//        else
//          None
//      )
  }
}

