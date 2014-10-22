package jp.co.bizreach.play2stub

import com.fasterxml.jackson.databind.ObjectMapper
import play.api.Play.current
import play.api.Logger
import play.api.http.{MimeTypes, HeaderNames}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.libs.ws.{WSResponse, WS}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
 * Processor processes a request then produces result as a future
 *   When it doesn't process, return None and sent to succeeding processors
 */
trait Processor {

  def process(implicit request: Request[AnyContent],
             route:Option[StubRoute]): Option[Future[Result]]

  def processSync(implicit request: Request[AnyContent],
              route:Option[StubRoute]): Option[Result] = None
}




/**
 * Process template rendering
 *
 * Currently, only string value is supported for extra parameters
 */
class TemplateProcessor extends Results with Processor {
  def process(implicit request: Request[AnyContent],
                       route: Option[StubRoute]): Option[Future[Result]] = {

    processSync.map(result => Future { result })

  }


  override def processSync(implicit request: Request[AnyContent],
              route: Option[StubRoute]): Option[Result] = {
    val params = Stub.params.map{ case (k, v) => k -> v.toString }

    route
      .map(r => Stub.render(r.path, Some(r), Stub.json(r, params)))
      .getOrElse( Stub.render(request.path, None, Stub.json(request.path)))
  }
}




/**
 * Processor to return static HTML files
 */
class StaticHtmlProcessor extends Controller with Processor {

  val logger = Logger(classOf[StaticHtmlProcessor])

  def process(implicit request: Request[AnyContent],
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
 * Processor to build json with parameters and static json files
 */
class JsonProcessor extends Results with Processor {
  override def process(implicit request: Request[AnyContent],
                       route: Option[StubRoute]): Option[Future[Result]] = {

    route
      .map(r => Stub.json(r, requireFile = true).map(j => Future { Ok(j.toString)} ))
      .getOrElse( Stub.json(request.path, requireFile = true).map(j => Future { Ok(j.toString)} ))
  }
}




/**
 * Processor to access to other servers.
 */
class ProxyProcessor 
  extends Controller with Processor with Logging[ProxyProcessor] {
  
  def process(implicit request: Request[AnyContent],
             route: Option[StubRoute]): Option[Future[Result]] = 

    route.flatMap { r => r.proxyUrl map { url =>
      r.template(request) match {
          case Some(t) => wsThenImmediateResponse(url, r, t)
          case None => wsThenStreamResponse(url)
        }
      }
    }


  /**
   * Return rendered html text when template is defined
   *   as long as the response status is normal
   */
  private[play2stub] def wsThenImmediateResponse(url: String, r: StubRoute, t: Template)
                        (implicit request: Request[AnyContent]): Future[Result] =
    buildWS(url).execute().map { response =>
      log.debug(s"ROUTE: Proxy:$url, with template:${t.path}")
      log.trace(s"ROUTE: Request Body:${request.body.asJson.getOrElse(Json.obj())}")
      log.trace(s"ROUTE: Response Body:${response.body}")


      if (response.status < 300)
        Stub.render(t.path, None, Some(r.flatParams ++ Stub.params + ("res" -> toJson(response))))
          .map(_
            .withHeaders((response.allHeaders.mapValues(_.headOption.getOrElse("")) -
                          HeaderNames.CONTENT_LENGTH - HeaderNames.CONTENT_TYPE).toSeq: _*)
            .withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.HTML))
          .getOrElse(
            resultAsIs(response)
          )

      else
        resultAsIs(response)
    }


  def resultAsIs(response:WSResponse) = Status(response.status)(response.body)
    .withHeaders(response.allHeaders.mapValues(_.headOption.getOrElse("")).toSeq: _*)


  /**
   * Return streaming result when template is not defined
   */
  private[play2stub] def wsThenStreamResponse(url: String)
                         (implicit request: Request[AnyContent]): Future[Result] =
    
//    buildWS(url).stream().map { case (response, body) =>
//      log.debug(s"ROUTE: Proxy:$url, Stream")
//      log.trace(s"ROUTE: Request Body:${request.body.asJson.getOrElse(Json.obj())}")
//
//      Status(response.status)
//        .chunked(body)
//        .withHeaders(response.headers.mapValues(_.headOption.getOrElse("")).toSeq: _*)
//    }

    buildWS(url).execute().map { response =>
      log.debug(s"ROUTE: Proxy:$url, without template")
      log.trace(s"ROUTE: Request Body:${request.body.asJson.getOrElse(Json.obj())}")
      log.trace(s"ROUTE: Response Body:${response.body}")

      resultAsIs(response)
    }

  /**
   * Build web client using WS 
   */
  private[play2stub] def buildWS(url: String)(implicit request: Request[AnyContent]) =
    WS.url(url)
      .withFollowRedirects(follow = false)
      .withHeaders(request.headers.toSimpleMap.toSeq: _*)
      .withQueryString(request.queryString.mapValues(_.headOption.getOrElse("")).toSeq: _*)
      .withBody(request.body.asJson.getOrElse(Json.obj())) // TODO accept other formats for file upload and others
      .withMethod(request.method)


  /**
   * Convert to JSON
   *   converted to Jackson node instead of JsValue currently
   *   because play2handlebars doesn't accept JsValue
   */
  private[play2stub] def toJson(response: WSResponse) =
    if (response.underlying[com.ning.http.client.Response].hasResponseBody)
      new ObjectMapper().readTree(
        response.underlying[com.ning.http.client.Response].getResponseBodyAsBytes)
    else
      new ObjectMapper().createObjectNode()
}
