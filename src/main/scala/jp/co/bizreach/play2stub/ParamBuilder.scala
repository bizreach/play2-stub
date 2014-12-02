package jp.co.bizreach.play2stub

import play.api.libs.ws.WSResponse
import play.api.mvc.{AnyContent, Request}


/**
 * Created by scova0731 on 10/19/14.
 */
trait ParamBuilder {

  def build(response: Option[WSResponse])(implicit request: Request[AnyContent],
            route: Option[StubRoute]): Map[String, Any]
}


class PathAndQueryStringParamBuilder extends ParamBuilder {
  def build(response: Option[WSResponse])(implicit request: Request[AnyContent],
            route: Option[StubRoute]): Map[String, Any] =
    Map("rawQueryString" -> request.rawQueryString, "path" -> request.path)
}