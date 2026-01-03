package cz.payola.domain.actors

import akka.actor.{Actor, ActorRef}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case object TimerTimeout
case object TimerCancel

/**
  * An actor that sends a timeout message to the timeoutReceiver after specified number of milliseconds.
  * @param timeout The timeout in milliseconds.
  * @param timeoutReceiver The receiver of the timeout message.
  */
class Timer(private val timeout: Option[Long], private val timeoutReceiver: ActorRef) extends Actor {
    def this(timeout: Long, timeoutReceiver: ActorRef) = this(Some(timeout), timeoutReceiver)

    override def preStart(): Unit = {
        if (timeout.isDefined) {
            context.system.scheduler.scheduleOnce(timeout.get.milliseconds, self, TimerTimeout)
        }
    }

    def receive: Receive = {
        case TimerTimeout =>
            timeoutReceiver ! TimerTimeout
            context.stop(self)
        case _ =>
            context.stop(self)
    }
}
