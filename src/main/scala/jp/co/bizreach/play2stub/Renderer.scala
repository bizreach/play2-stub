package jp.co.bizreach.play2stub

import com.fasterxml.jackson.databind.ObjectMapper
import jp.co.bizreach.play2handlebars.HBS
import play.api.http.{MimeTypes, HeaderNames}
import play.api.libs.ws.{WSResponse, WS}
import play.api.Logger
import play.api.Play.current
import play.api.mvc.{Controller, Result, AnyContent, Request}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait Renderer {

  def render(implicit request: Request[AnyContent],
             route:Option[StubRoute]): Option[Future[Result]]

}


/**
 * Check if HTML file exists in both cases
 */
class StaticHtmlRenderer extends Controller with Renderer {

  def render(implicit request: Request[AnyContent],
             route:Option[StubRoute]): Option[Future[Result]] = {

    val path = route.map(_.path).getOrElse(request.path)
    Stub.html(path).map(html =>
      Future {
        Ok(html).withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.HTML)
      }
    )
  }
}


/**
 *
 */
class HandlebarsWithRouteRenderer extends Controller with Renderer {

  def render(implicit request: Request[AnyContent],
             route: Option[StubRoute]): Option[Future[Result]] =

    route map { r =>
      Future {
        (Stub.json(r), r.template(request)) match {
          case (Some(d), Some(Template(path, "hbs"))) =>
            Ok(HBS.any(path, d))
          case (Some(d), _) =>
            Ok(d.toString) // TODO add "application/json"
          case (None, Some(Template(path, "hbs"))) =>
            Ok(HBS(path))
          case (None, _) =>
            Ok(HBS(r.path))
        }
      }
    }
}


class HandlebarsWithPathRenderer extends Controller with Renderer {

  def render(implicit request: Request[AnyContent],
             route: Option[StubRoute]): Option[Future[Result]] =

    Some(Future{
      (Stub.json(request.path), Stub.exists(Template(request.path, "hbs"))) match {
        case (Some(d), true) =>
          Ok(HBS.any(request.path, d))
        case (Some(d), false) =>
          Ok(d.toString).withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        case (None, true) =>
          Ok(HBS(request.path))
        case (None, false) =>
          NotFound("Neither template file nor data file are found.")
      }
    })
}


class ProxyAndHandlebarsRenderer extends Controller with Renderer {

  def render(implicit request: Request[AnyContent],
             route: Option[StubRoute]): Option[Future[Result]] = {

    def buildHolder(url: String) = WS.url(url)
      //.withRequestTimeout(10000)
      .withFollowRedirects(follow = false)
      .withHeaders(request.headers.toSimpleMap.toSeq: _*)
      .withQueryString(request.queryString.mapValues(_.headOption.getOrElse("")).toSeq: _*)
      .withBody(request.body.asText.getOrElse(""))
      .withMethod(request.method)

    // Convert to Jackson node instead of Play.api.Json currently
    def toJson(response: WSResponse) = new ObjectMapper().readTree(
      response.underlying[com.ning.http.client.Response].getResponseBodyAsBytes)

    route.flatMap { r => r.proxyUrl map { url =>

      r.template(request) match {
        case Some(t) =>
          buildHolder(url).execute().map { response =>
            Logger.debug(s"ROUTE: Proxy:$url, Template:${t.path}")

            if (response.status < 300)
              Ok(HBS(t.path, r.flatParams + ("res" -> toJson(response))))
                .withHeaders((response.allHeaders.mapValues(_.headOption.getOrElse("")) -
                   HeaderNames.CONTENT_LENGTH - HeaderNames.CONTENT_TYPE).toSeq: _*)
                .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.HTML) // TODO think again

            else
              Status(response.status)(response.body)
                .withHeaders(response.allHeaders.mapValues(_.headOption.getOrElse("")).toSeq: _*)
          }

        case None =>
          buildHolder(url).stream().map { case (response, body) =>
            Logger.debug(s"ROUTE: Proxy:$url, Stream")

            Status(response.status)
              .chunked(body)
              .withHeaders(response.headers.mapValues(_.headOption.getOrElse("")).toSeq: _*)
          }
      }
    }
    }
  }

}