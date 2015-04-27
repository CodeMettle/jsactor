# jsactor
Actors on Scala.js

### Disclaimer
Much of this code (the good parts) are straight from [Typesafe/Akka](https://github.com/akka/akka) and I don't claim
authorship. Most of the code that isn't straight from Akka was at least written while looking at Akka code and uses
their concepts and techniques. Anything broken is my fault, everything else is thanks to their work. My hope is that
they create and maintain an official Scala.js version of Akka; until then `jsactor` seems to mostly work.

While this projects works, somewhat, it's not close to production-quality.

# Why?

I'm used to working with the Actor model on the backend, with Scala.js it feels natural to use Actors and have
hierarchy, a well-defined lifecycle, an `EventStream`, and so on.

# Import

```scala
libraryDependencies ++= Seq(
  "com.codemettle.scalajs" %%% "jsactor" % "0.6.1",
  "com.codemettle.scalajs" %%% "jsactor-bridge-client" % "0.6.1" // if using jsactor-bridge
  "com.codemettle.scalajs" %%% "jsactor-loglevel" % "0.6.1" // to use the LogLevel logging adapter
)
```

# Usage

### Create ActorSystem

```scala
import jsactor.JsActorSystem
import jsactor.logging.impl.JsLoglevelActorLoggerFactory

// logging defaults to scala.js println statements, this is using LogLevel; JsNullActorLoggerFactory also exists
val system = JsActorSystem("mySystemName", JsLoglevelActorLoggerFactory)
```

### Create an Actor

```scala
val system: JsActorSystem = ???

class MyActor extends JsActor with JsActorLogging {
  override def preStart() = {
    super.preStart()

    log.info(s"$self is starting at ${self.path}")
  }

  override def receive = JsActor.emptyBehavior
}

val myactor = system.actorOf(JsProps(new MyActor), "myactor")

myactor ! MyMessage()
```

### More usage

A lot of stuff you expect from Akka exists with pretty much the same signatures; this includes `context.system.eventStream`,
`context.actorOf()`, `sender()`, `?` (import `jsactor.pattern.ask`), `context watch actor` (with `JsTerminated` messages),
`JsActor with JsStash`, `context.setReceiveTimeout` (with `JsReceiveTimeout` messages), etc.

### What's missing?

Probably the feature you need.

In all seriousness, most of the basic functionality of vanilla akka-actor is there.

#### Supervision

Supervision probably wouldn't be hard to write, but I haven't had a need for it. Most of the code that isn't straight
from Akka is comprised of ugly hacks and casts and "just barely works on my box"isms, and that probably makes
supervision harder to implement than it should be.

#### FSM

I copied the FSM trait straight from Akka, but it has strange runtime errors. This is probably the biggest hole that I
personally care about; hopefully I'll have time to look at it again someday.

# Server-client Interop

This is the part we care about, right? Why use Actors on the client if we're not interacting with a backend ActorSystem?

To that end, I'm including some extra projects that make interaction pretty seamless, at least in our usage. We have
many µservices running on the backend, one of which consolidates large amounts of data from many different sources and
transforms to model objects and pushes changes to the GUI. No pull, no REST, no client-side polling for changes - the
backend reacts to changes it cares about, based on the current client view (what it cares about), and when the model
objects change then the server pushes them.

### Shared model

```scala
// project with model classes shared between client & server, cross-compiled to JVM & JS
 ...
libraryDependencies += "com.codemettle.scalajs" %%% "jsactor-bridge-shared" % "0.6.1"
 ...
```

Currently this project (so, by extension, client and server bridge projects) depends on
[upickle](https://github.com/lihaoyi/upickle), with no way to change that.

`upickle` was written, I assume, with [autowire](https://github.com/lihaoyi/autowire) in mind - all types are known in
advance. That presents an issue for `jsactor` since the Akka model is typeless and depends on pattern matching.
I worked around this with inspiration from (or, really, by copying) [scala-js-pickling](https://github.com/scala-js/scala-js-pickling)'s
`PicklerRegistry`. The top-level bridge actor on client and server need an implicit `BridgeProtocol` in scope. The
protocol has to register every top-level message that is going to be sent across the bridge.

```scala
object MyProjectProtocol extends BridgeProtocol {
  override def registerMessages(registry: MessageRegistry): Unit = {
    def add[A : Reader : Writer : ClassTag] = registry.add[A]
    def addObj[A <: Singleton : Reader : Writer : ClassTag](obj: A) = registry.addObj(obj)

    addObj(SessionApi.SubscribeToUserAndAlarmPrefs)
    add[SessionApi.UserUpdate]
    add[SessionApi.PreferenceUpdate]
    add[SessionApi.UpdatePreferences]
    add[SessionApi.PreferenceUpdateResult]
  }
}
```

### Server-side

```scala
// server project; also needs to depend on the shared model project
 ...
libraryDependencies += "com.codemettle.scalajs" %%% "jsactor-bridge-server" % "0.6.1"
 ...
```

The server-side of the bridge takes a reference to an Actor which sends/receives Strings to/from the client,
presumably over a WebSocket.

```scala
class ServerBridgeActor(clientWebSocket: ActorRef)(implicit bridgeProtocol: BridgeProtocol) extends Actor
```

Play 2.3 happens to give us an Actor representing a WebSocket, so this is pretty trivial in Play:

```scala
object MyController extends Controller {
  ...

  private def getSession(implicit request: RequestHeader): Future[Option[ClientSession]] = ???

  def ws = WebSocket.tryAcceptWithActor[String, String] { request ⇒
    getSession(request) map {
      case None ⇒ Left(Forbidden)

      case Some(session) ⇒
        implicit val bridgeProtocol = MyProjectProtocol
        Right((wsActor) ⇒ ServerBridgeActor.props(wsActor))
    }
  }

  ...
}
```

### Client-side

```scala
// client project; also needs to depend on the shared model project
 ...
libraryDependencies += "com.codemettle.scalajs" %%% "jsactor-bridge-client" % "0.6.1"
 ...
```

`jsactor-bridge-client` provides building blocks for creating the client-side of a bridge, any/all of the provided
 classes can be replaced with your own, but it's easiest to use `jsactor.bridge.client.{ClientBridgeActor, SocketManager, WebSocketActor}`.

```scala
  val actorSystem: JsActorSystem = ???

  private implicit val bridgeProtocal = MyProjectProtocol
  val wsManager = actorSystem.actorOf(SocketManager.props(SocketManager.Config("ws://localhost:9000/ws"), "socketManager")

  class MyActor extends JsActor {
    override def preStart() = {
      super.preStart()

      wsManager ! SocketManager.Events.SubscribeToEvents
    }

    override def receive = {
      case SocketManager.Events.WebSocketConnected(socket) ⇒
        socket ! WebSocketActor.Messages.SendMessageToServer("/user/MyServerActor", MessageThatIHaveRegistered())
        socket ! WebSocketActor.Messages.SendMessageToServer("akka.tcp://ActorSystem@address:port/user/MyServerActor", MessageThatIHaveRegistered())

        // could receive a jsactor.bridge.protocol.ServerActorNotFound message,
        // otherwise the server-side actor will have a proxy representing this client-side actor, any messages the
        // server sends to that actor will come to this actor; the server can DeathWatch that proxy to be notified
        // that this actor has shutdown (or the WebSocket was interrupted)

        socket ! WebSocketActor.Messages.IdentifyServerActor("/user/MyServerActor")

        // this actor will receive a jsactor.bridge.protocol.ServerActorNotFound message, or will receive a
        // jsactor.bridge.protocol.ServerActorFound message (sent from the client-side proxy of that server-side actor).
        // Any message sent to this client-side proxy will be sent to the corresponding server-side actor, and the
        // client-side proxy can be DeathWatched to notify that the server actor has shutdown (or WebSocket was interrupted).
    }
  }
```

I've also included a rough utility trait, `jsactor.bridge.client.util.RemoteActorListener`, that can be used to
automatically start trying to `Identify` a server-side actor when the websocket connects (or reconnects):

```scala
import jsactor.bridge.client.util.RemoteActorListener

class MyClientActor(val wsManager: JsActorRef) extends RemoteActorListener {
  def actorPath = "/user/MyServerActor"

  def onConnect(serverActor: JsActorRef) = {
    serverActor ! MyRegisteredMessage
  }

  def whenConnected(serverActor: JsActorRef) = {
    case RegisteredMessageFromServer(param) => log.info(s"got $param from server")
    case msg@RegisteredMessageFromClient => serverActor forward msg
  }
}
```
