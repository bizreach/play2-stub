package jp.co.bizreach.play2stub

import jp.co.bizreach.play2handlebars.HBS
import play.api.mvc.{AnyContent, Action, Controller}

object StubController extends StubController

trait StubController extends Controller {

  /**
   * Generates an `Action` that serves a static resource.
   *
   * @param path the file part extracted from the URL. May be URL encoded (note that %2F decodes to literal /).
   */
  def at(path: String): Action[AnyContent] = Action { request =>

    val path2 = path
    val params = request.queryString

//    val stub = Stub.route(request).get
    val filePath = "test1"
    val data = Stub.data("test1")//stub.data.get)



    //
    //
    //

    data match {
      case Some(d) =>
        Ok(HBS.any("test1", d))
      case None =>
        BadRequest(s"[$filePath] was not found")
    }
  }
}
