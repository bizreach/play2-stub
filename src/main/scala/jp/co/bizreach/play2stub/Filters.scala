package jp.co.bizreach.play2stub

import play.api.mvc.{AnyContent, Request, Result}

trait BeforeFilter {

  def process(request:Request[AnyContent]):Request[AnyContent]

}


/**
 * Do something after result is
 */
trait AfterFilter {

  def process(request:Request[AnyContent], result:Result):Result

}