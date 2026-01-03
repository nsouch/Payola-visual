package cz.payola.domain.sparql.evaluation

import cz.payola.domain.actors.{Timer, TimerTimeout}
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

/**
 * An actor that launches and measures running time of a sparql query. It creates another actor that
 * performs the query (SimpleQueryRunner), a Timer actor and launches both of them and waits for
 * a result of the query or a timeout.
 * @param query What query to perform.
 * @param dataSource On what datasource to perform the query.
 * @param timeout How long to wait for the results.
 */
class SimpleTimeoutQueryRunner(query: String, dataSource: cz.payola.domain.entities.plugins.DataSource,
    private val timeout: Option[Long]) extends Actor {

    private var timer: Option[ActorRef] = None
    private var result: Option[QueryResult] = None
    private var actorChild: Option[ActorRef] = None
    
    override def preStart(): Unit = {
        timer = Some(context.actorOf(Props(new Timer(timeout, self))))
        actorChild = Some(context.actorOf(Props(new SimpleQueryRunner(query, dataSource))))
        actorChild.foreach(_ ! "run")
    }

    def receive: Receive = active

    def active: Receive = {
        case ErrorResult(e) =>
            finishEvaluation(ErrorResult(e))
        case SuccessResult(languagesGraph) =>
            result = Some(SuccessResult(languagesGraph))
        case TimerTimeout =>
            finishEvaluation(TimeoutResult)
        case control: QueryRunnerControl =>
            processControlMessage(control)
    }

    def finished: Receive = {
        case control: QueryRunnerControl =>
            processControlMessage(control)
    }

    private def processControlMessage(message: QueryRunnerControl) {
        message match {
            case GetResult =>
                sender() ! result
            case Stop if result.isEmpty => 
                finishEvaluation(StoppedResult)
            case Terminate =>
                terminateChild()
                context.stop(self)
        }
    }

    private def finishEvaluation(queryResult: QueryResult) {
        terminateChild()
        result = Some(queryResult)
        context.become(finished)
    }

    private def terminateChild() {
        timer.foreach(context.stop)
        actorChild.foreach(context.stop)
    }

    /**
     * End this actor.
     */
    def finish {
        implicit val t = Timeout(5.seconds)
        Await.result(self ? Terminate, t.duration)
    }

    /**
     * Fetch result of the query.
     * @return current result of the query (the query is still running and the timeout has not orruced if None)
     */
    def getResult: Option[QueryResult] = {
        implicit val t = Timeout(5.seconds)
        Await.result((self ? GetResult).mapTo[Option[QueryResult]], t.duration)
    }

    /**
     * Is true only if some result of the query evaluation was reached (even if it is a timeout or an error).
     * @return false if query is still running and timeout has not occured
     */
    def isFinished = getResult.isDefined
}