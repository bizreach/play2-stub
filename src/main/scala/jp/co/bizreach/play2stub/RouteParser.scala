package jp.co.bizreach.play2stub


import java.net.URI

import play.api.Logger

import scala.io.Codec
import scala.util.DynamicVariable
import scala.util.control.Exception
import scala.util.parsing.input._
import scala.util.parsing.combinator._
import java.io.File
import org.apache.commons.io.FileUtils

import scala.language.postfixOps


/**
 * captures URL parts
 */
trait PathPart

/**
 * Because of Positional, DynamicPart cannot be replaced by play.core.DynamicPart
 */
case class DynamicPart(name: String, constraint: String, encodeable: Boolean) extends PathPart with Positional {
  override def toString = """DynamicPart("""" + name + "\", \"\"\"" + constraint + "\"\"\"," + encodeable + ")" //"
}

/**
 * Because of Positional, StaticPart cannot be replaced by play.core.StaticPart
 */
case class StaticPart(value: String) extends PathPart {
  override def toString = """StaticPart("""" + value + """")"""
}

/**
 * Because of Positional, PathPattern cannot be replaced by play.core.PathPattern
 */
case class PathPattern(parts: Seq[PathPart]) {
  def has(key: String): Boolean = parts.exists {
    case DynamicPart(name, _, _) if name == key => true
    case _ => false
  }

  override def toString = parts.map {
    case DynamicPart(name, constraint, encode) => "$" + name + "<" + constraint + ">"
    case StaticPart(path) => path
  }.mkString


  ////////////////////////////////////
  // Copied from play.core.PathPattern
  ////////////////////////////////////
  import java.util.regex._

  private def decodeIfEncoded(decode: Boolean, groupCount: Int): Matcher => Either[Throwable, String] = matcher =>
    Exception.allCatch[String].either {
      if (decode) {
        val group = matcher.group(groupCount)
        // If param is not correctly encoded, get path will return null, so we prepend a / to it
        new URI("/" + group).getPath.drop(1)
      } else
        matcher.group(groupCount)
    }

  lazy val (regex, groups) = {
    //スラッシュ入れた。。。
    //Some(parts.foldLeft("", Map.empty[String, Matcher => Either[Throwable, String]], 0) { (s, e) =>
    Some(parts.foldLeft("/", Map.empty[String, Matcher => Either[Throwable, String]], 0) { (s, e) =>
      e match {
        case StaticPart(p) => ((s._1 + Pattern.quote(p)), s._2, s._3)
        case DynamicPart(k, r, encodeable) => {
          ((s._1 + "(" + r + ")"),
            (s._2 + (k -> decodeIfEncoded(encodeable, s._3 + 1))),
            s._3 + 1 + Pattern.compile(r).matcher("").groupCount)
        }
      }
    }).map {
      case (r, g, _) => Pattern.compile("^" + r + "$") -> g
    }.get
  }


  def apply(path: String): Option[Map[String, Either[Throwable, String]]] = {
    val matcher = regex.matcher(path)
    if (matcher.matches) {
      Some(groups.map {
        case (name, g) => name -> g(matcher)
      }.toMap)
    } else {
      None
    }
  }


}

/**
 * provides a compiler for routes
 *
 * The source derives from
 *   https://github.com/playframework/playframework/tree/2.3.3/framework/src/routes-compiler/src/main/scala/play/router
 *
 */
object RoutesCompiler {
  val logger = Logger("play2stub.RoutesCompiler")

  case class HttpVerb(value: String) {
    override def toString = value
  }

  case class HandlerCall(packageName: String, controller: String, instantiate: Boolean, method: String, parameters: Option[Seq[Parameter]]) extends Positional {
    val dynamic = if (instantiate) "@" else ""
    override def toString = dynamic + packageName + "." + controller + dynamic + "." + method + parameters.map { params =>
      "(" + params.mkString(", ") + ")"
    }.getOrElse("")
  }

  case class Parameter(name: String, typeName: String, fixed: Option[String], default: Option[String]) extends Positional {
    override def toString = name + ":" + typeName + fixed.map(" = " + _).getOrElse("") + default.map(" ?= " + _).getOrElse("")
  }

  sealed trait Rule extends Positional

//  case class Route(verb: HttpVerb, path: PathPattern, call: HandlerCall, comments: List[Comment] = List()) extends Rule
  case class Route(verb: HttpVerb, path: PathPattern, comments: List[Comment] = List()) extends Rule

  case class Include(prefix: String, router: String) extends Rule

  case class Comment(comment: String)

  object Hash {

    def apply(routesFile: File, imports: Seq[String]): String = {
      import java.security.MessageDigest
      val digest = MessageDigest.getInstance("SHA-1")
      digest.reset()
      digest.update(FileUtils.readFileToByteArray(routesFile) ++ imports.flatMap(_.getBytes))
      digest.digest().map(0xFF & _).map { "%02x".format(_) }.foldLeft("") { _ + _ }
    }

  }

  // --- Parser


  class RouteFileParser extends JavaTokenParsers {

    private lazy val lastNoSuccessVar = new DynamicVariable[Option[NoSuccess]](None)

    override def skipWhitespace = false

    override val whiteSpace = """[ \t]+""".r

    override def phrase[T](p: Parser[T]) = new Parser[T] {
//      lastNoSuccess = null
//      def apply(in: Input) = p(in) match {
//        case s @ Success(out, in1) =>
//          if (in1.atEnd)
//            s
//          else if (lastNoSuccess == null || lastNoSuccess.next.pos < in1.pos)
//            Failure("end of input expected", in1)
//          else
//            lastNoSuccess
//        case _ => lastNoSuccess
//      }
      def apply(in: Input) = lastNoSuccessVar.withValue(None) {
        p(in) match {
          case s @ Success(out, in1) =>
            if (in1.atEnd)
              s
            else
              lastNoSuccessVar.value filterNot { _.next.pos < in1.pos } getOrElse Failure("end of input expected", in1)
          case ns => lastNoSuccessVar.value.getOrElse(ns)
        }
      }
    }

    def EOF: util.matching.Regex = "\\z".r

    def namedError[A](p: Parser[A], msg: String): Parser[A] = Parser[A] { i =>
      p(i) match {
        case Failure(_, in) => Failure(msg, in)
        case o => o
      }
    }

    def several[T](p: => Parser[T]): Parser[List[T]] = Parser { in =>
      import scala.collection.mutable.ListBuffer
      val elems = new ListBuffer[T]
      def continue(in: Input): ParseResult[List[T]] = {
        val p0 = p // avoid repeatedly re-evaluating by-name parser
        @scala.annotation.tailrec
        def applyp(in0: Input): ParseResult[List[T]] = p0(in0) match {
            case Success(x, rest) =>
              elems += x; applyp(rest)
            case Failure(_, _) => Success(elems.toList, in0)
            case err: Error => err
          }
        applyp(in)
      }
      continue(in)
    }

    def separator: Parser[String] = namedError(whiteSpace, "Whitespace expected")

    def ignoreWhiteSpace: Parser[Option[String]] = opt(whiteSpace)

    // This won't be needed when we upgrade to Scala 2.11, we will then be able to use JavaTokenParser.ident:
    // https://github.com/scala/scala/pull/1466
    def javaIdent: Parser[String] = """\p{javaJavaIdentifierStart}\p{javaJavaIdentifierPart}*""".r

    def identifier: Parser[String] = namedError(javaIdent, "Identifier expected")

    def end: util.matching.Regex = """\s*""".r

    def comment: Parser[Comment] = "#" ~> ".*".r ^^ {
      case c => Comment(c)
    }

    def newLine: Parser[String] = namedError(("\r"?) ~> "\n", "End of line expected")

    def blankLine: Parser[Unit] = ignoreWhiteSpace ~> newLine ^^ { case _ => () }

    def parentheses: Parser[String] = {
      "(" ~ several(parentheses | not(")") ~> """.""".r) ~ commit(")") ^^ {
        case p1 ~ charList ~ p2 => p1 + charList.mkString + p2
      }
    }

    def brackets: Parser[String] = {
      "[" ~ several(parentheses | not("]") ~> """.""".r) ~ commit("]") ^^ {
        case p1 ~ charList ~ p2 => p1 + charList.mkString + p2
      }
    }

    def string: Parser[String] = {
      "\"" ~ several(parentheses | not("\"") ~> """.""".r) ~ commit("\"") ^^ {
        case p1 ~ charList ~ p2 => p1 + charList.mkString + p2
      }
    }

    def multiString: Parser[String] = {
      "\"\"\"" ~ several(parentheses | not("\"\"\"") ~> """.""".r) ~ commit("\"\"\"") ^^ {
        case p1 ~ charList ~ p2 => p1 + charList.mkString + p2
      }
    }

    def httpVerb: Parser[HttpVerb] = namedError("GET" | "POST" | "PUT" | "PATCH" | "HEAD" | "DELETE" | "OPTIONS", "HTTP Verb expected") ^^ {
      case v => HttpVerb(v)
    }

    def singleComponentPathPart: Parser[DynamicPart] = (":" ~> identifier) ^^ {
      case name => DynamicPart(name, """[^/]+""", encodeable = true)
    }

    def multipleComponentsPathPart: Parser[DynamicPart] = ("*" ~> identifier) ^^ {
      case name => DynamicPart(name, """.+""", encodeable = false)
    }

    def regexComponentPathPart: Parser[DynamicPart] = "$" ~> identifier ~ ("<" ~> (not(">") ~> """[^\s]""".r +) <~ ">" ^^ { case c => c.mkString }) ^^ {
      case name ~ regex => DynamicPart(name, regex, encodeable = false)
    }

    def staticPathPart: Parser[StaticPart] = (not(":") ~> not("*") ~> not("$") ~> """[^\s]""".r +) ^^ {
      case chars => StaticPart(chars.mkString)
    }

    def path: Parser[PathPattern] = "/" ~ ((positioned(singleComponentPathPart) | positioned(multipleComponentsPathPart) | positioned(regexComponentPathPart) | staticPathPart) *) ^^ {
      case _ ~ parts => PathPattern(parts)
    }

    def parameterType: Parser[String] = ":" ~> ignoreWhiteSpace ~> rep1sep(identifier, ".") ~ opt(brackets) ^^ {
      case t ~ g => t.mkString(".") + g.getOrElse("")
    }

    def expression: Parser[String] = ((multiString | string | parentheses | brackets | """[^),?=\n]""".r) +) ^^ {
      case p => p.mkString
    }

    def parameterFixedValue: Parser[String] = "=" ~ ignoreWhiteSpace ~ expression ^^ {
      case a ~ _ ~ b => a + b
    }

    def parameterDefaultValue: Parser[String] = "?=" ~ ignoreWhiteSpace ~ expression ^^ {
      case a ~ _ ~ b => a + b
    }

    def parameter: Parser[Parameter] = (identifier <~ ignoreWhiteSpace) ~ opt(parameterType) ~ (ignoreWhiteSpace ~> opt(parameterDefaultValue | parameterFixedValue)) ^^ {
      case name ~ t ~ d => Parameter(name, t.getOrElse("String"), d.filter(_.startsWith("=")).map(_.drop(1)), d.filter(_.startsWith("?")).map(_.drop(2)))
    }

    def parameters: Parser[List[Parameter]] = "(" ~> repsep(ignoreWhiteSpace ~> positioned(parameter) <~ ignoreWhiteSpace, ",") <~ ")"

    // Absolute method consists of a series of Java identifiers representing the package name, controller and method.
    // Since the Scala parser is greedy, we can't easily extract this out, so just parse at least 3
    def absoluteMethod: Parser[List[String]] = namedError(javaIdent ~ "." ~ javaIdent ~ "." ~ rep1sep(javaIdent, ".") ^^ {
      case first ~ _ ~ second ~ _ ~ rest => first :: second :: rest
    }, "Controller method call expected")


    def router: Parser[String] = rep1sep(identifier, ".") ^^ {
      case parts => parts.mkString(".")
    }

    def route = httpVerb ~! separator ~ path ~ ignoreWhiteSpace ^^ {
      case v ~ _ ~ p ~ _ => Route(v, p)
    }


    def sentence: Parser[Product with Serializable] = namedError(comment | positioned(route),
      "HTTP Verb (GET, POST, ...) or comment (#) expected") <~ (newLine | EOF)

    def parser: Parser[List[Rule]] = phrase(((blankLine | sentence) *) <~ end) ^^ {
      case routes =>
        routes.reverse.foldLeft(List[(Option[Rule], List[Comment])]()) {
          case (s, r @ Route(_, _, _)) => (Some(r), List()) :: s
          case (s, c @ ()) => (None, List()) :: s
          case ((r, comments) :: others, c @ Comment(_)) => (r, c :: comments) :: others
          case (s, _) => s
        }.collect {
          case (Some(r @ Route(_, _, _)), comments) => r.copy(comments = comments).setPos(r.pos)
        }
    }

    def parse(text: String): ParseResult[List[Rule]] = {
      parser(new CharSequenceReader(text))
    }
  }

  import java.io.File

  case class RoutesCompilationError(source: File, message: String, line: Option[Int], column: Option[Int]) extends RuntimeException(message)

  case class GeneratedSource(file: File) {

    val lines = if (file.exists) FileUtils.readFileToString(file, implicitly[Codec].name).split('\n').toList else Nil

    val source = lines.find(_.startsWith("// @SOURCE:")).map(m => new File(m.trim.drop(11)))

    def isGenerated: Boolean = source.isDefined

    def sync(): Boolean = {
      if (!source.exists(_.exists)) file.delete() else false
    }

    def needsRecompilation(imports: Seq[String]): Boolean = {
      val hash = lines.find(_.startsWith("// @HASH:")).map(m => m.trim.drop(9)).getOrElse("")
      source.filter(_.exists).map { p =>
        Hash(p, imports) != hash
      }.getOrElse(true)
    }

    def mapLine(generatedLine: Int): Option[Int] = {
      lines.take(generatedLine).reverse.collect {
        case l if l.startsWith("// @LINE:") => Integer.parseInt(l.trim.drop(9))
      }.headOption
    }

  }


  val parser = new RouteFileParser

  def parse(text: String): Either[String, Rule] = {
    val result = parser.parse(text)
    result match {
      case parser.Success(parsed:List[Rule], _) if parsed.size == 1 =>
        Right(parsed(0))
      case err:parser.Error =>
        Left(err.toString())
      case _ =>
        Left("No stub routes has been parsed")
    }
  }


  def parse(file: File): List[Rule] = {

    val parser = new RouteFileParser
    val routeFile = file.getAbsoluteFile
    val routesContent = FileUtils.readFileToString(routeFile)

    parser.parse(routesContent) match {
      case parser.Success(parsed:List[Rule], _) =>
        parsed
      case parser.NoSuccess(message, in) =>
        logger.info("No stub routes has been parsed")
        List.empty
    }

  }

}

