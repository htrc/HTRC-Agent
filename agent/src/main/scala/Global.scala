/**
 * Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 */

import akka.kernel.Bootable

object Global extends com.typesafe.play.mini.Setup(htrcagent.PlayRest) with Bootable {

  def startup = { println("starting") }
  def shutdown = { println("stopping") }

}
