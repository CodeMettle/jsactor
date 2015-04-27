/*
 * JsFSM.scala
 *
 * Updated: Apr 14, 2015
 *
 * Copyright (c) 2015, CodeMettle
 */
package jsactor

import jsactor.logging.JsActorLogging
import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * @author steven
 *
 */
object JsFSM {

  /**
   * A partial function value which does not match anything and can be used to
   * “reset” `whenUnhandled` and `onTermination` handlers.
   *
   * {{{
   * onTermination(FSM.NullFunction)
   * }}}
   */
  object NullFunction extends PartialFunction[Any, Nothing] {
    def isDefinedAt(o: Any) = false
    def apply(o: Any) = sys.error("undefined")
  }

  case class CurrentState[S](fsmRef: JsActorRef, state: S)

  case class Transition[S](fsmRef: JsActorRef, from: S, to: S)

  case class SubscribeTransitionCallBack(actorRef: JsActorRef)

  case class UnsubscribeTransitionCallBack(actorRef: JsActorRef)

  sealed trait Reason

  /**
   * Default reason if calling `stop()`.
   */
  case object Normal extends Reason

  /**
   * Reason given when someone was calling `system.stop(fsm)` from outside;
   * also applies to `Stop` supervision directive.
   */
  case object Shutdown extends Reason

  case class Failure(cause: Any) extends Reason

  /**
   * This case object is received in case of a state timeout.
   */
  case object StateTimeout

  /**
   * INTERNAL API
   */
  private case class TimeoutMarker(generation: Long)

  /**
   * INTERNAL API
   */
  // FIXME: what about the cancellable?
  private[jsactor] case class Timer(name: String, msg: Any, repeat: Boolean, generation: Int)(context: JsActorContext) {
    private var ref: Option[JsCancellable] = _
    private val scheduler = context.system.scheduler
    private implicit val executionContext = context.dispatcher

    def schedule(actor: JsActorRef, timeout: FiniteDuration): Unit =
      ref = Some(
        if (repeat) scheduler.schedule(timeout, timeout, actor, this)
        else scheduler.scheduleOnce(timeout, actor, this))

    def cancel(): Unit =
      if (ref.isDefined) {
        ref.get.cancel()
        ref = None
      }
  }

  /**
   * This extractor is just convenience for matching a (S, S) pair, including a
   * reminder what the new state is.
   */
  object -> {
    def unapply[S](in: (S, S)) = Some(in)
  }

  case class LogEntry[S, D](stateName: S, stateData: D, event: Any)

  case class State[S, D](stateName: S, stateData: D, timeout: Option[FiniteDuration] = None, stopReason: Option[Reason] = None, replies: List[Any] = Nil) {

    /**
     * Modify state transition descriptor to include a state timeout for the
     * next state. This timeout overrides any default timeout set for the next
     * state.
     *
     * Use Duration.Inf to deactivate an existing timeout.
     */
    def forMax(timeout: Duration): State[S, D] = timeout match {
      case f: FiniteDuration ⇒ copy(timeout = Some(f))
      case _                 ⇒ copy(timeout = None)
    }

    /**
     * Send reply to sender of the current message, if available.
     *
     * @return this state transition descriptor
     */
    def replying(replyValue: Any): State[S, D] = {
      copy(replies = replyValue :: replies)
    }

    /**
     * Modify state transition descriptor with new state data. The data will be
     * set when transitioning to the new state.
     */
    def using(nextStateDate: D): State[S, D] = {
      copy(stateData = nextStateDate)
    }

    /**
     * INTERNAL API.
     */
    private[jsactor] def withStopReason(reason: Reason): State[S, D] = {
      copy(stopReason = Some(reason))
    }
  }

  case class Event[D](event: Any, stateData: D)


  //case class StopEvent[S, D](reason: Reason, currentState: S, stateData: D)

}

trait JsFSM[S, D] extends JsActor with JsListeners with JsActorLogging {

  import JsFSM._

  type State = JsFSM.State[S, D]
  type Event = JsFSM.Event[D]
  //type StopEvent = JsFSM.StopEvent[S, D]
  type StateFunction = scala.PartialFunction[Event, State]
  type Timeout = Option[FiniteDuration]
  type TransitionHandler = PartialFunction[(S, S), Unit]

  /*
   * “import” so that these are visible without an import
   */
  //    val Event: JsFSM.Event.type = JsFSM.Event
  //    val StopEvent: JsFSM.StopEvent.type = JsFSM.StopEvent

  /**
   * This extractor is just convenience for matching a (S, S) pair, including a
   * reminder what the new state is.
   */
  val -> = JsFSM.->
  val → = JsFSM.->

  /**
   * This case object is received in case of a state timeout.
   */
  val StateTimeout = JsFSM.StateTimeout

  /**
   * ****************************************
   *                 DSL
   * ****************************************
   */

  /**
   * Insert a new StateFunction at the end of the processing chain for the
   * given state. If the stateTimeout parameter is set, entering this state
   * without a differing explicit timeout setting will trigger a StateTimeout
   * event; the same is true when using #stay.
   *
   * @param stateName designator for the state
   * @param stateTimeout default state timeout for this state
   * @param stateFunction partial function describing response to input
   */
  final def when(stateName: S, stateTimeout: FiniteDuration = null)(stateFunction: StateFunction): Unit =
    register(stateName, stateFunction, Option(stateTimeout))

  final def startWith(stateName: S, stateData: D, timeout: Timeout = None): Unit =
    currentState = JsFSM.State(stateName, stateData, timeout)

  /**
   * Produce transition to other state. Return this from a state function in
   * order to effect the transition.
   *
   * @param nextStateName state designator for the next state
   * @return state transition descriptor
   */
  final def goto(nextStateName: S): State = JsFSM.State(nextStateName, currentState.stateData)

  /**
   * Produce "empty" transition descriptor. Return this from a state function
   * when no state change is to be effected.
   *
   * @return descriptor for staying in current state
   */
  final def stay(): State = goto(currentState.stateName) // cannot directly use currentState because of the timeout field

  /**
   * Produce change descriptor to stop this FSM actor with reason "Normal".
   */
  final def stop(): State = stop(Normal)

  /**
   * Produce change descriptor to stop this FSM actor including specified reason.
   */
  final def stop(reason: Reason): State = stop(reason, currentState.stateData)

  /**
   * Produce change descriptor to stop this FSM actor including specified reason.
   */
  final def stop(reason: Reason, stateData: D): State = stay using stateData withStopReason reason

  final class TransformHelper(func: StateFunction) {
    def using(andThen: PartialFunction[State, State]): StateFunction =
      func andThen (andThen orElse { case x ⇒ x })
  }

  final def transform(func: StateFunction): TransformHelper = new TransformHelper(func)

  /**
   * Schedule named timer to deliver message after given delay, possibly repeating.
   * Any existing timer with the same name will automatically be canceled before
   * adding the new timer.
   * @param name identifier to be used with cancelTimer()
   * @param msg message to be delivered
   * @param timeout delay of first message delivery and between subsequent messages
   * @param repeat send once if false, scheduleAtFixedRate if true
   * @return current state descriptor
   */
  final def setTimer(name: String, msg: Any, timeout: FiniteDuration, repeat: Boolean = false): Unit = {
    if (debugEvent)
      log.debug("setting " + (if (repeat) "repeating " else "") + "timer '" + name + "'/" + timeout + ": " + msg)
    if (timers contains name) {
      timers(name).cancel()
    }
    val timer = Timer(name, msg, repeat, timerGen.next())(context)
    timer.schedule(self, timeout)
    timers(name) = timer
  }

  /**
   * Cancel named timer, ensuring that the message is not subsequently delivered (no race).
   * @param name of the timer to cancel
   */
  final def cancelTimer(name: String): Unit = {
    if (debugEvent)
      log.debug("canceling timer '" + name + "'")
    if (timers contains name) {
      timers(name).cancel()
      timers -= name
    }
  }

  /**
   * Inquire whether the named timer is still active. Returns true unless the
   * timer does not exist, has previously been canceled or if it was a
   * single-shot timer whose message was already received.
   */
  final def isTimerActive(name: String): Boolean = timers contains name

  /**
   * Set state timeout explicitly. This method can safely be used from within a
   * state handler.
   */
  final def setStateTimeout(state: S, timeout: Timeout): Unit = stateTimeouts(state) = timeout

  /**
   * INTERNAL API, used for testing.
   */
  private[jsactor] final def isStateTimerActive = timeoutFuture.isDefined

  /**
   * Set handler which is called upon each state transition, i.e. not when
   * staying in the same state. This may use the pair extractor defined in the
   * FSM companion object like so:
   *
   * <pre>
   * onTransition {
   *   case Old -&gt; New =&gt; doSomething
   * }
   * </pre>
   *
   * It is also possible to supply a 2-ary function object:
   *
   * <pre>
   * onTransition(handler _)
   *
   * private def handler(from: S, to: S) { ... }
   * </pre>
   *
   * The underscore is unfortunately necessary to enable the nicer syntax shown
   * above (it uses the implicit conversion total2pf under the hood).
   *
   * <b>Multiple handlers may be installed, and every one of them will be
   * called, not only the first one matching.</b>
   */
  final def onTransition(transitionHandler: TransitionHandler): Unit = transitionEvent :+= transitionHandler

  /**
   * Convenience wrapper for using a total function instead of a partial
   * function literal. To be used with onTransition.
   */
  implicit final def total2pf(transitionHandler: (S, S) ⇒ Unit): TransitionHandler =
    new TransitionHandler {
      def isDefinedAt(in: (S, S)) = true
      def apply(in: (S, S)) { transitionHandler(in._1, in._2) }
    }

  /**
   * Set handler which is called upon termination of this FSM actor. Calling
   * this method again will overwrite the previous contents.
   */
  //    final def onTermination(terminationHandler: PartialFunction[StopEvent, Unit]): Unit =
  //        terminateEvent = terminationHandler

  /**
   * Set handler which is called upon reception of unhandled messages. Calling
   * this method again will overwrite the previous contents.
   *
   * The current state may be queried using ``stateName``.
   */
  final def whenUnhandled(stateFunction: StateFunction): Unit = {
    log.debug(s"handleEvent = $handleEvent")
    log.debug(s"handleEventDefault = $handleEventDefault")
    log.debug(s"whenUnhandled = $stateFunction")
    handleEvent = stateFunction orElse handleEventDefault
  }

  final def initialize(): Unit = makeTransition(currentState)

  /**
   * Return current state name (i.e. object of type S)
   */
  final def stateName: S = currentState.stateName

  /**
   * Return current state data (i.e. object of type D)
   */
  final def stateData: D = currentState.stateData

  /**
   * Return next state data (available in onTransition handlers)
   */
  final def nextStateData = nextState match {
    case null ⇒ throw new IllegalStateException("nextStateData is only available during onTransition")
    case x    ⇒ x.stateData
  }

  /*
   * ****************************************************************
   *                PRIVATE IMPLEMENTATION DETAILS
   * ****************************************************************
   */

  private[jsactor] def debugEvent: Boolean = false

  /*
   * FSM State data and current timeout handling
   */
  private var currentState: State = _
  private var timeoutFuture: Option[JsCancellable] = None
  private var nextState: State = _
  private var generation: Long = 0L

  /*
   * Timer handling
   */
  private val timers = mutable.Map[String, Timer]()
  private val timerGen = Iterator from 0

  /*
   * State definitions
   */
  private val stateFunctions = mutable.Map[S, StateFunction]()
  private val stateTimeouts = mutable.Map[S, Timeout]()

  private def register(name: S, function: StateFunction, timeout: Timeout): Unit = {
    if (stateFunctions contains name) {
      stateFunctions(name) = stateFunctions(name) orElse function
      stateTimeouts(name) = timeout orElse stateTimeouts(name)
    } else {
      stateFunctions(name) = function
      stateTimeouts(name) = timeout
    }
  }

  /*
   * unhandled event handler
   */
  private val handleEventDefault: StateFunction = {
    case Event(value, stateData) ⇒
      log.warn("unhandled event " + value + " in state " + stateName)
      stay()
  }
  private var handleEvent: StateFunction = handleEventDefault

  /*
   * termination handling
   */
  //    private var terminateEvent: PartialFunction[StopEvent, Unit] = NullFunction

  /*
   * transition handling
   */
  private var transitionEvent: List[TransitionHandler] = Nil
  private def handleTransition(prev: S, next: S) {
    val tuple = (prev, next)
    for (te ← transitionEvent) { if (te.isDefinedAt(tuple)) te(tuple) }
  }

  /*
   * *******************************************
   *       Main actor receive() method
   * *******************************************
   */
  override def receive: Receive = {
    case TimeoutMarker(gen) ⇒
      if (generation == gen) {
        processMsg(StateTimeout, "state timeout")
      }
    case t @ Timer(name, msg, repeat, gen) ⇒
      if ((timers contains name) && (timers(name).generation == gen)) {
        if (timeoutFuture.isDefined) {
          timeoutFuture.get.cancel()
          timeoutFuture = None
        }
        generation += 1
        if (!repeat) {
          timers -= name
        }
        processMsg(msg, t)
      }
    case SubscribeTransitionCallBack(actorRef) ⇒
      // TODO Use context.watch(actor) and receive Terminated(actor) to clean up list
      listeners += actorRef
      // send current state back as reference point
      actorRef ! CurrentState(self, currentState.stateName)
    case Listen(actorRef) ⇒
      // TODO Use context.watch(actor) and receive Terminated(actor) to clean up list
      listeners += actorRef
      // send current state back as reference point
      actorRef ! CurrentState(self, currentState.stateName)
    case UnsubscribeTransitionCallBack(actorRef) ⇒
      listeners -= actorRef
    case Deafen(actorRef) ⇒
      listeners -= actorRef
    case value ⇒
      if (timeoutFuture.isDefined) {
        timeoutFuture.get.cancel()
        timeoutFuture = None
      }
      generation += 1
      processMsg(value, sender())
  }

  private def processMsg(value: Any, source: AnyRef): Unit = {
    val event = Event(value, currentState.stateData)
    processEvent(event, source)
  }

  private[jsactor] def processEvent(event: Event, source: AnyRef): Unit = {
    val stateFunc = stateFunctions(currentState.stateName)
    val nextState = if (stateFunc isDefinedAt event) {
      stateFunc(event)
    } else {
      // handleEventDefault ensures that this is always defined
      log.trace(s"$handleEvent")
      log.debug(s"gonna call handleEvent($event) with source=$source")
      log.debug(s"event.getClass=${event.getClass}")
      log.debug(s"handleEvent.isDefinedAt(event) = ${handleEvent.isDefinedAt(event)}")
      handleEvent(event)
    }
    applyState(nextState)
  }

  private[jsactor] def applyState(nextState: State): Unit = {
    nextState.stopReason match {
      case None ⇒ makeTransition(nextState)
      case _ ⇒
        nextState.replies.reverse foreach { r ⇒ sender() ! r }
        terminate(nextState)
        context.stop(self)
    }
  }

  private[jsactor] def makeTransition(nextState: State): Unit = {
    if (!stateFunctions.contains(nextState.stateName)) {
      terminate(stay withStopReason Failure("Next state %s does not exist".format(nextState.stateName)))
    } else {
      nextState.replies.reverse foreach { r ⇒ sender() ! r }
      if (currentState.stateName != nextState.stateName) {
        this.nextState = nextState
        handleTransition(currentState.stateName, nextState.stateName)
        gossip(Transition(self, currentState.stateName, nextState.stateName))
        this.nextState = null
      }
      currentState = nextState
      val timeout = if (currentState.timeout.isDefined) currentState.timeout else stateTimeouts(currentState.stateName)
      if (timeout.isDefined) {
        val t = timeout.get
        if (t.isFinite && t.length >= 0) {
          timeoutFuture = Some(context.system.scheduler.scheduleOnce(t, self, TimeoutMarker(generation)))
        }
      }
    }
  }

  /**
   * Call `onTermination` hook; if you want to retain this behavior when
   * overriding make sure to call `super.postStop()`.
   *
   * Please note that this method is called by default from `preRestart()`,
   * so override that one if `onTermination` shall not be called during
   * restart.
   */
  override def postStop(): Unit = {
    /*
     * setting this instance’s state to terminated does no harm during restart
     * since the new instance will initialize fresh using startWith()
     */
    terminate(stay withStopReason Shutdown)
    super.postStop()
  }

  private def terminate(nextState: State): Unit = {
    if (currentState.stopReason.isEmpty) {
      val reason = nextState.stopReason.get
      logTermination(reason)
      for (timer ← timers.values) timer.cancel()
      timers.clear()
      currentState = nextState

      //            val stopEvent = StopEvent(reason, currentState.stateName, currentState.stateData)
      //            if (terminateEvent.isDefinedAt(stopEvent))
      //                terminateEvent(stopEvent)
    }
  }

  protected def logTermination(reason: Reason): Unit = reason match {
    case Failure(ex: Throwable) ⇒ log.error("terminating due to Failure: " + ex)
    case Failure(msg: AnyRef)   ⇒ log.error(msg.toString)
    case _                      ⇒
  }
}

trait JsLoggingFSM[S, D] extends JsFSM[S, D] { this: JsActor ⇒

  import JsFSM._

  def logDepth: Int = 0

  private[jsactor] override val debugEvent = false //context.system.settings.FsmDebugEvent

  private val events = new Array[Event](logDepth)
  private val states = new Array[AnyRef](logDepth)
  private var pos = 0
  private var full = false

  private def advance() {
    val n = pos + 1
    if (n == logDepth) {
      full = true
      pos = 0
    } else {
      pos = n
    }
  }

  private[jsactor] abstract override def processEvent(event: Event, source: AnyRef): Unit = {
    if (debugEvent) {
      val srcstr = source match {
        case s: String            ⇒ s
        case Timer(name, _, _, _) ⇒ "timer " + name
        case a: JsActorRef          ⇒ a.toString
        case _                    ⇒ "unknown"
      }
      log.debug("processing " + event + " from " + srcstr)
    }

    if (logDepth > 0) {
      states(pos) = stateName.asInstanceOf[AnyRef]
      events(pos) = event
      advance()
    }

    val oldState = stateName
    super.processEvent(event, source)
    val newState = stateName

    if (debugEvent && oldState != newState)
      log.debug("transition " + oldState + " -> " + newState)
  }

  /**
   * Retrieve current rolling log in oldest-first order. The log is filled with
   * each incoming event before processing by the user supplied state handler.
   * The log entries are lost when this actor is restarted.
   */
  protected def getLog: IndexedSeq[LogEntry[S, D]] = {
    val log = events zip states filter (_._1 ne null) map (x ⇒ LogEntry(x._2.asInstanceOf[S], x._1.stateData, x._1.event))
    if (full) {
      IndexedSeq() ++ log.drop(pos) ++ log.take(pos)
    } else {
      IndexedSeq() ++ log
    }
  }

}
