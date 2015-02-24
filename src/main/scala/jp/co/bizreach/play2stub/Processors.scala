package jp.co.bizreach.play2stub

import java.io.ByteArrayOutputStream

import com.fasterxml.jackson.databind.ObjectMapper
import com.ning.http.client.providers.netty.NettyResponse
import play.api.Play.current
import play.api.Logger
import play.api.http.{ContentTypeOf, Writeable, MimeTypes, HeaderNames}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.{MimeTypes => LibMimeTypes}
import play.api.libs.ws.{WSRequestHolder, WSCookie, WSResponse, WS}
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
    route
      .map(r => Stub.render(r.path, Some(r), Stub.json(r, Stub.params())))
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
  protected def wsThenImmediateResponse(url: String, r: StubRoute, t: Template)
                        (implicit request: Request[AnyContent]): Future[Result] =
    buildWS(url).execute().map { response =>
      log.debug(s"ROUTE: Proxy:$url, with template:${t.path}")
      log.trace(s"ROUTE: Request Body:${request.body.asJson.getOrElse(Json.obj())}")
      log.trace(s"ROUTE: Response Body:${response.body}")


      if (response.status < 300)
        Stub.render(t.path, None, Some(r.flatParams ++ Stub.params(Some(response)) + ("res" -> toJson(response))))
          .map(_
            .withHeaders(deNormalizedHeaders(response.allHeaders -
                         HeaderNames.CONTENT_LENGTH - HeaderNames.CONTENT_TYPE - HeaderNames.SET_COOKIE):_*)
            .withHeaders(HeaderNames.CONTENT_TYPE -> HTML)
            .withCookies(convertCookies(response.cookies):_*)
          ).getOrElse(
            resultAsIs(response)
          )

      else
        resultAsIs(response)
    }


  /**
   * Return all body and headers as is
   */
  protected def resultAsIs(response:WSResponse) = writeBody(response)
    .withHeaders(deNormalizedHeaders(response.allHeaders - HeaderNames.SET_COOKIE):_*)
    .withCookies(convertCookies(response.cookies):_*)


  /**
   * If it's text, get as text or get as binary
   */
  protected def writeBody(response: WSResponse) = {
    if (response.header(HeaderNames.CONTENT_TYPE).exists(LibMimeTypes.isText))
      Status(response.status)(response.body)
    else
      Status(response.status)(response.underlying[NettyResponse].getResponseBodyAsBytes)
  }


  /**
   * De-normalize headers especially for Set-Cookie header
   */
  protected def deNormalizedHeaders(headers:Map[String, Seq[String]]): Seq[(String, String)] =
    headers.toSeq.flatMap {case (key, values) => values.map(value => (key, value)) }


  /**
   * Convert WSCookie to Cookie
   *   Cookie domain should be removed (TODO set cookie domain from configuration, TODO http only settings)
   */
  protected def convertCookies(cookies:Seq[WSCookie]): Seq[Cookie] =
    cookies.map(ws => Cookie(
      name = ws.name.getOrElse(""),
      value = ws.value.getOrElse(""),
      maxAge = ws.maxAge,
      path = ws.path,
      domain = None, //Some(ws.domain),
      secure = ws.secure,
      httpOnly = true))


  /**
   * Return streaming result when template is not defined
   */
  protected def wsThenStreamResponse(url: String)
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
  protected def buildWS(url: String)(implicit request: Request[AnyContent]): WSRequestHolder =
    withBody(url)
      .withFollowRedirects(follow = false)
      .withHeaders(request.headers.toSimpleMap.toSeq: _*)
      .withQueryString(request.queryString.mapValues(_.headOption.getOrElse("")).toSeq: _*)
      .withMethod(resolveMethod(request))


  /**
   * JSON or Multi-part body
   */
  protected def withBody(url: String)(implicit request: Request[AnyContent]): WSRequestHolder =
    request.body.asMultipartFormData match {
      case Some(mp) => withMultiPart(url, mp)
      case None => WS.url(url).withBody(request.body.asJson.getOrElse(Json.obj()))
    }


  /**
   * Convert multi-part body via AsyncHttpClient
   *  See https://github.com/playframework/playframework/issues/902 also.
   */
  protected def withMultiPart(url: String, multiPart: MultipartFormData[TemporaryFile])(implicit request: Request[AnyContent]):WSRequestHolder = {
    import com.ning.http.client.FluentCaseInsensitiveStringsMap
    import com.ning.http.multipart._

    // TODO Set correct value delimiter. Currently, semi-colon is set
    val dataParts = multiPart.asFormUrlEncoded.map { case (key, values) => new StringPart(key, values.mkString(";"))}
    val fileParts = multiPart.files.map(f => new FilePart(
      f.key,
      f.filename,
      f.ref.file,
      f.contentType.getOrElse(FilePart.DEFAULT_CONTENT_TYPE),
      null
    ))

    val mpre = new MultipartRequestEntity((dataParts ++ fileParts).toArray, new FluentCaseInsensitiveStringsMap)
    val baos = new ByteArrayOutputStream
    mpre.writeRequest(baos)
    val bytes = baos.toByteArray
    val contentType = mpre.getContentType

    WS.url(url)
      .withBody(bytes)(Writeable.wBytes, ContentTypeOf(Some(contentType)))
  }


  /**
   * Resolve a HTTP method
   */
  protected def resolveMethod(request: Request[AnyContent]): String =
    if (request.method == "HEAD") "GET" else request.method


  /**
   * Convert to JSON
   *   converted to Jackson node instead of JsValue currently
   *   because play2handlebars doesn't accept JsValue
   */
  protected def toJson(response: WSResponse) =
    if (response.underlying[com.ning.http.client.Response].hasResponseBody)
      new ObjectMapper().readTree(
        response.underlying[com.ning.http.client.Response].getResponseBodyAsBytes)
    else
      new ObjectMapper().createObjectNode()
}
