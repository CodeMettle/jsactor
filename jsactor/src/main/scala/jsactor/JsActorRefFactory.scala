/*
 * JsActorRefFactory.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import scala.concurrent.ExecutionContextExecutor

/**
 * @author steven
 *
 */
trait JsActorRefFactory {
    def actorOf(props: JsProps): JsActorRef
    def actorOf(props: JsProps, name: String): JsActorRef

    /* *
     * Construct an [[akka.actor.ActorSelection]] from the given path, which is
     * parsed for wildcards (these are replaced by regular expressions
     * internally). No attempt is made to verify the existence of any part of
     * the supplied path, it is recommended to send a message and gather the
     * replies in order to resolve the matching set of actors.
     * /
    def actorSelection(path: String): ActorSelection = path match {
        case RelativeActorPath(elems) ⇒
            if (elems.isEmpty) ActorSelection(provider.deadLetters, "")
            else if (elems.head.isEmpty) ActorSelection(provider.rootGuardian, elems.tail)
            else ActorSelection(lookupRoot, elems)
        case ActorPathExtractor(address, elems) ⇒
            ActorSelection(provider.rootGuardianAt(address), elems)
        case _ ⇒
            ActorSelection(provider.deadLetters, "")
    }*/

    /* *
     * Construct an [[akka.actor.ActorSelection]] from the given path, which is
     * parsed for wildcards (these are replaced by regular expressions
     * internally). No attempt is made to verify the existence of any part of
     * the supplied path, it is recommended to send a message and gather the
     * replies in order to resolve the matching set of actors.
     * /
    def actorSelection(path: JsActorPath): ActorSelection =
        ActorSelection(provider.rootGuardianAt(path.address), path.elements)*/

    def stop(actor: JsActorRef): Unit

    implicit def dispatcher: ExecutionContextExecutor
}

private[jsactor] trait JsInternalActorRefFactory extends JsActorRefFactory {
    this: JsInternalActorContext ⇒

    private val actorUids = new Iterator[Long] {
        private var nextItem = 0L

        override def hasNext: Boolean = true

        override def next(): Long = {
            val ret = nextItem
            nextItem += 1
            ret
        }
    }

    protected var _children = Set.empty[JsActorRef]

    override def actorOf(props: JsProps): JsActorRef = {
        actorOf(props, None)
    }

    override def actorOf(props: JsProps, name: String): JsActorRef = {
        actorOf(props, Some(name))
    }

    private def actorOf(props: JsProps, name: Option[String]): JsActorRef = {
        val actorName = name match {
            case None ⇒ RippedFromAkka.base64(actorUids.next())
            case Some(n) ⇒
                if (!JsActorPath.isValidPathElement(n))
                    throw new Exception(s"Invalid Actor Name: $n")

                child(n) foreach (c ⇒ throw new Exception(s"Invalid Actor Name: ${c.path} already exists"))

                n
        }

        val path = self.path / actorName

        val newRef = new JsInternalActorRef(path)
        val ctx = new JsInternalActorContext(props, self, newRef)
        ctx.start()
        _children += newRef
        newRef
    }

    override def stop(actor: JsActorRef): Unit = {
        if (actor.asInstanceOf[JsInternalActorRef].ctx.parent == self)
            dispatcher.execute(new Runnable {
                override def run(): Unit = {
                    logger.trace(s"stopping $actor from $self")
//                    actor.asInstanceOf[JsInternalActorRef].ctx.stop()
                    stopChild(actor)
                }
            })
        else {
            logger.trace(s"actorParent = ${actor.asInstanceOf[JsInternalActorRef].ctx.parent}")
            actor.asInstanceOf[JsInternalActorRef].ctx.parent.asInstanceOf[JsInternalActorRef].ctx.stop(actor)
        }
//            logger.error("this is not the parent!")
//        dispatcher.execute(new Runnable {
//            override def run(): Unit = {
//                logger.debug(s"stopping $actor")
//                actor.asInstanceOf[JsInternalActorRef].ctx.stop()
//            }
//        })
    }
}
