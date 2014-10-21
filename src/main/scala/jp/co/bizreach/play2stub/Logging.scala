package jp.co.bizreach.play2stub

import play.api.Logger

/**
 * Play logging trait
 */
trait Logging[A] {
  self: A =>

  val log = Logger(self.getClass).logger
}
