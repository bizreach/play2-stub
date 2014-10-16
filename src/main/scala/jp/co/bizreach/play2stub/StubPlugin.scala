package jp.co.bizreach.play2stub

import jp.co.bizreach.play2stub.RoutesCompiler.Route
import play.api.Play._
import play.api.{Configuration, Logger, Application, Plugin}
import scala.collection.JavaConverters._
import scala.reflect.ClassTag


class StubPlugin(app: Application) extends Plugin {

  private val logger = Logger("jp.co.bizreach.play2stub.StubPlugin")
  private val basePath = "play2stub"

  val engineConf = app.configuration.getString(basePath + ".engine").getOrElse("hbs")
  val dataRootConf = app.configuration.getString(basePath + ".data-root").getOrElse("/app/data")
  val viewRootConf = app.configuration.getString(basePath + ".view-root").getOrElse("/app/views")
  val proxyRootConf = app.configuration.getString(basePath + ".proxy-root")
  val enableProxyConf = app.configuration.getBoolean(basePath + ".enable-proxy")
  val beforePluginList = app.configuration.getStringSeq(basePath + "filters.before").getOrElse(Seq.empty)
  val afterPluginList = app.configuration.getStringSeq(basePath + "filters.after").getOrElse(Seq.empty)
  val templateResolverConf = app.configuration.getString(basePath + ".template-resolver")

  trait RouteHolder {
    val routes: Seq[StubRouteConfig]
    val engine: String = engineConf
    val dataRoot: String = dataRootConf
    val viewRoot: String = viewRootConf
    val proxyRoot: Option[String] =proxyRootConf
    val isProxyEnabled: Boolean = enableProxyConf.getOrElse(false)
    val beforeFilters: Seq[BeforeFilter] = loadFilters[BeforeFilter](beforePluginList)
    val afterFilters: Seq[AfterFilter] = loadFilters[AfterFilter](afterPluginList)
    val templateResolver: TemplateResolver = loadTemplateResolver(templateResolverConf)
  }


  lazy val holder = new RouteHolder {

    private val routeList =
      current.configuration.getConfigList(basePath + ".routes")
        .map(_.asScala).getOrElse(Seq.empty)
    
    override val routes = routeList.map{ route =>
      val path = route.subKeys.mkString

      route.getConfig(path).map { inner =>
        StubRouteConfig(
          route = parseRoute(path.replace("~", ":")),
          proxy = inner.getString("proxy"),
          template = toTemplate(inner),
          data = inner.getString("data"),
          status = inner.getInt("status"),
          noResponse = inner.getBoolean("noResponse").getOrElse(false),
          headers = toMap(inner.getConfig("headers")),
          params = toMap(inner.getConfig("params"))
        )
      }.get
    }
  }


  /**
   *  Instantiate stub configuration holder on starting up
   */
  override def onStart(): Unit = {
    logger.debug("Initializing Play2Stub ...")
    holder
    logger.debug("Play2Stub is initialized !")
  }


  private def loadFilters[T](filters:Seq[String])(implicit ct: ClassTag[T]): Seq[T] =
    filters.map(f => app.classloader.loadClass(f).newInstance().asInstanceOf[T])


  private def loadTemplateResolver(conf: Option[String]): TemplateResolver =
    conf.map(app.classloader.loadClass(_).newInstance()
      .asInstanceOf[TemplateResolver]).getOrElse(new DefaultTemplateResolver)


  private def parseRoute(path: String): Route = {
    RoutesCompiler.parse(path) match {
      case Right(r: Route) => r
      case Right(unexpected) =>
        throw new RuntimeException(unexpected.toString)
      case Left(err) =>
        throw new RuntimeException(err)
    }
  }


  private def toTemplate(inner: Configuration): Option[Template] =
    if (inner.subKeys.contains("template")) {
      val path =
        if (inner.keys.contains("template.path"))
          inner.getString("template.path").get
        else
          inner.getString("template").get
      val engine =
        if (inner.keys.contains("template.engine"))
          inner.getString("template.engine").get
        else
          engineConf

      Some(Template(path, engine))

    } else
      None


  private def toMap(conf: Option[Configuration]): Map[String, String] =
    conf.map(_.entrySet
      .map(e => e._1 -> e._2.render()))
      .getOrElse(Map.empty).toMap
}


