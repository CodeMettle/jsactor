/*
 * JsDeadLettersActor.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import jsactor.logging.JsActorLogging

/**
 * @author steven
 *
 */
private[jsactor] class JsDeadLettersActor extends JsActor with JsActorLogging {
    override def receive: Receive = {
        case JsDeadLetter(msg, sender, recip) ⇒ log.info(s"Message [${msg.getClass.getName}] from $sender to $recip was not delivered.")
        case msg ⇒ log.info(s"Message [${msg.getClass.getName}] from ${sender()} was not delivered.")
    }
}

