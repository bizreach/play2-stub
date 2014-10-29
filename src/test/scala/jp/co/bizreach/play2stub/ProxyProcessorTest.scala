package jp.co.bizreach.play2stub

import org.scalatest.{Matchers, FunSpec}

/**
 * Created by satoshi.kobayashi on 2014/10/29.
 */
class ProxyProcessorTest extends FunSpec with Matchers {

  describe("proxy processor") {

    it("should de-normalize headers") {

      val processor = new ProxyProcessor
      val headers:Map[String, Seq[String]] = Map(
        "Content-Type" -> Seq("application/json"),
        "Set-Cookie" -> Seq("cookie", "donut"))

      val result = processor.deNormalizedHeaders(headers)
      println(result)


    }
  }

}
