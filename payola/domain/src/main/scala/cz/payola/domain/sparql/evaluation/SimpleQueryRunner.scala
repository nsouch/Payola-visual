package cz.payola.domain.sparql.evaluation

import akka.actor.Actor
import cz.payola.domain.entities.plugins.DataSource

class SimpleQueryRunner(query: String, dataSource: DataSource) extends Actor {

    def receive: Receive = {
        case "run" =>
            try {
                val resultGraph = dataSource.executeQuery(query)
                sender() ! SuccessResult(Some(resultGraph))
            } catch {
                case e: Throwable => sender() ! ErrorResult(e)
            }
            context.stop(self)
    }
}
