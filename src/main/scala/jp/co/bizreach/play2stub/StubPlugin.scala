package jp.co.bizreach.play2stub

import java.net.URL

import jp.co.bizreach.play2stub.RoutesCompiler.Route
import org.apache.commons.io.FileUtils
import play.api.Play._
import play.api._
import scala.collection.JavaConverters._
import scala.reflect.ClassTag


/**
 *
 */
class StubPlugin(app: Application) extends Plugin {

  private val logger = Logger("jp.co.bizreach.play2stub.StubPlugin")
  private val basePath = "play2stub"

  val engineConf = app.configuration.getString(basePath + ".engine").getOrElse("hbs")
  val dataRootConf = app.configuration.getString(basePath + ".data-root").getOrElse("/app/data")
  val viewRootConf = app.configuration.getString(basePath + ".view-root").getOrElse("/app/views")
  val proxyRootConf = app.configuration.getString(basePath + ".proxy-root")
  val enableProxyConf = app.configuration.getBoolean(basePath + ".enable-proxy")
  val beforePluginList = app.configuration.getStringSeq(basePath + ".filters.before").getOrElse(Seq.empty)
  val afterPluginList = app.configuration.getStringSeq(basePath + ".filters.after")
  val rendererList = app.configuration.getStringSeq(basePath + ".renderers")
  val processorList = app.configuration.getStringSeq(basePath + ".processors")
  val paramBuilderList = app.configuration.getStringSeq(basePath + ".param-builders")
  val templateResolverConf = app.configuration.getString(basePath + ".template-resolver")
  val loadClassPathConf = app.configuration.getBoolean(basePath + ".loadClassPath").getOrElse(Play.isProd(current))
  val parameterSymbol = app.configuration.getString(basePath + ".syntax.parameter").getOrElse("~")
  val wildcardSymbol = app.configuration.getString(basePath + ".syntax.wildcard").getOrElse("~~")

  private def defaultRenderers =
    Seq(new HandlebarsRenderer)
  private def defaultProcessors =
    Seq(new ProxyProcessor, new TemplateProcessor, new StaticHtmlProcessor, new JsonProcessor)
  private def defaultParamBuilders =
    Seq(new PathAndQueryStringParamBuilder)
  private def defaultAfterFilters =
    Seq(new RedirectFilter)

  trait RouteHolder {
    val routes: Seq[StubRouteConfig]
    val engine: String = engineConf
    val dataRoot: String = dataRootConf
    val viewRoot: String = viewRootConf
    val proxyRoot: Option[String] =proxyRootConf
    val isProxyEnabled: Boolean = enableProxyConf.getOrElse(false)
    val beforeFilters: Seq[BeforeFilter] = loadFilters[BeforeFilter](beforePluginList)
    val afterFilters: Seq[AfterFilter] = afterPluginList.map(loadFilters[AfterFilter]).getOrElse(defaultAfterFilters)
    val renderers: Seq[Renderer] = rendererList.map(loadFilters[Renderer]).getOrElse(defaultRenderers)
    val processors: Seq[Processor] = processorList.map(loadFilters[Processor]).getOrElse(defaultProcessors)
    val paramBuilders: Seq[ParamBuilder] = paramBuilderList.map(loadFilters[ParamBuilder]).getOrElse(defaultParamBuilders)
    val templateResolver: TemplateResolver = loadTemplateResolver(templateResolverConf)
    val fileLoader: FileLoader = new FileLoader(dataRootConf, viewRootConf, loadClassPathConf)
  }


  /**
   * Holds stub configuration values
   */
  lazy val holder = new RouteHolder {

    private val routeList =
      current.configuration.getConfigList(basePath + ".routes")
        .map(_.asScala).getOrElse(Seq.empty)
    
    override val routes = routeList.map{ route =>
      val path = route.subKeys.mkString

      route.getConfig(path).map { inner =>
        StubRouteConfig(
          route = parseRoute(inner.getString("path").getOrElse(path).replace(wildcardSymbol, "*").replace(parameterSymbol, ":")),
          template = toTemplate(inner),
          proxy = inner.getString("proxy"),
          redirect = inner.getString("redirect"),
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


  /**
   *
   */
  private def loadFilters[T](filters: Seq[String])(implicit ct: ClassTag[T]): Seq[T] =
    filters.map(f => app.classloader.loadClass(f).newInstance().asInstanceOf[T])


  /**
   *
   */
  private def loadTemplateResolver(conf: Option[String]): TemplateResolver =
    conf.map(app.classloader.loadClass(_).newInstance()
      .asInstanceOf[TemplateResolver]).getOrElse(new DefaultTemplateResolver)


  /**
   *
   */
  private def parseRoute(path: String): Route =
    RoutesCompiler.parse(path) match {
      case Right(r: Route) => r
      case Right(unexpected) =>
        throw new RuntimeException(unexpected.toString)
      case Left(err) =>
        throw new RuntimeException(err)
    }


  /**
   *
   */
  private def toTemplate(c: Configuration): Option[Template] =
    if (c.subKeys.contains("template")) {
      val path =
        if (c.keys.contains("template.path"))
          c.getString("template.path").get
        else
          c.getString("template").get
      val engine =
        if (c.keys.contains("template.engine"))
          c.getString("template.engine").get
        else
          engineConf

      Some(Template(path, engine))

    } else
      None


  /**
   *
   */
  private def toMap(conf: Option[Configuration]): Map[String, String] =
    conf.map(_.entrySet
      .map(e => e._1 -> e._2.render()))
      .getOrElse(Map.empty).toMap
}


class FileLoader(
  dataRoot: String, viewRoot: String, loadClassPath: Boolean) {

  def load(pathWithExt: String, isData: Boolean = false): Option[URL] =
    if (loadClassPath)
      loadByClassPath(pathWithExt, isData)
    else
      loadByFilePath(pathWithExt, isData)

  def loadByClassPath(pathWithExt: String, isData: Boolean): Option[URL] =
    Option(getClass.getResource(concat(rootDir(isData), pathWithExt)))

  def loadByFilePath(pathWithExt: String, isData: Boolean): Option[URL] = {
    val file = FileUtils.getFile(
      System.getProperty("user.dir"), rootDir(isData), pathWithExt)
    if (file.exists())
      Some(file.toURI.toURL)
    else
      None
  }

  def rootDir(isData: Boolean): String =
    if (isData) dataRoot else viewRoot

  def concat(path1: String, path2 :String): String =
    (if (path1.endsWith("/")) path1 else path1 + "/") +
      (if (path2.startsWith("/")) path2.substring(1) else path2)

}