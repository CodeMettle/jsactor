/*
 * JsDeadLettersActor.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import com.codemettle.weblogging.WebLogging

/**
 * @author steven
 *
 */
private[jsactor] class JsDeadLettersActor extends JsActor with WebLogging {
    override def receive: Receive = {
        case JsDeadLetter(msg, sender, recip) ⇒ logger.info(s"Message [${msg.getClass.getName}] from $sender to $recip was not delivered.")
        case msg ⇒ logger.info(s"Message [${msg.getClass.getName}] from ${sender()} was not delivered.")
    }
}

