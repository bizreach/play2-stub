  package controllers


  import play.api._
  import play.api.mvc._
  import jp.co.bizreach.play2handlebars.HBS

  class Application extends Controller {

    def index = Action {
      Ok(views.html.index("Your new application is ready."))
    }


    def indexHbs = Action {
      Ok(HBS("index",
        "welcome" -> HBS.safeString(views.html.play20.welcome("Your new application is ready.")),
        "title" -> "Wlecome to Play"))
    }


    def simple = Action {
      Ok(HBS("simple", "who" -> "World"))
    }


    def upload = Action(parse.maxLength(5 * 1024 * 1024, parse.multipartFormData)) { implicit req =>
      req.body match {
        case Left(MaxSizeExceeded(maxLength)) =>
          Logger.info("Request max size exceeded.")
          EntityTooLarge(s"Data length is too large: ${}")
        case Right(data) =>
          Logger.info(s"The file was uploaded successfully.")
          Logger.info(data.toString)
          Ok
      }
    }

  }