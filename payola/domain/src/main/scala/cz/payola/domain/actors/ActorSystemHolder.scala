package cz.payola.domain.actors

import akka.actor.ActorSystem

object ActorSystemHolder {
  implicit lazy val system: ActorSystem = ActorSystem("PayolaActorSystem")
}
