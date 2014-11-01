package jp.co.bizreach.play2stub

import play.api.http.HeaderNames
import play.api.mvc.{ResponseHeader, AnyContent, Request, Result}
import play.mvc.Http.Status

/**
 * Do something before the request is processed.
 */
trait BeforeFilter {

  def process(request:Request[AnyContent]):Request[AnyContent]

}


/**
 * Do something after result is generated.
 */
trait AfterFilter {

  def process(request:Request[AnyContent], result:Result):Result

}


/**
 * Set redirect status when redirect url is specified.
 *   When normal statuses or not-found status returns and "redirect" path is specified,
 *   set SEE OTHER (303) with the specified url and the passed headers.
 */
class RedirectFilter extends AfterFilter {

  def process(request: Request[AnyContent], result: Result): Result = {
    if (result.header.status < 300 || result.header.status == 404) {

      Stub.route(request).flatMap { r => r.redirectUrl.map { url =>

          result.copy(
            header = ResponseHeader(
              status = Status.SEE_OTHER,
              headers = result.header.headers
            )
          ).withHeaders(HeaderNames.LOCATION -> url)
        }
      }.getOrElse(
        result
      )
    } else
      result
  }
}