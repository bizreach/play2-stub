package jp.co.bizreach.play2stub

import play.api.mvc.{Request, AnyContent}

trait TemplateResolver {

  def resolve(request:Request[AnyContent], route:StubRoute):Option[Template]

}


class DefaultTemplateResolver extends TemplateResolver {

  def resolve(request: Request[AnyContent], route: StubRoute): Option[Template] = {
    route.conf.template.map{t =>
      val filledPath = route.flatParams.foldLeft(t.path) { (filled, param) =>
        filled.replace(":" + param._1, param._2)
      }
      t.copy(path = filledPath)
    }
  }
}
