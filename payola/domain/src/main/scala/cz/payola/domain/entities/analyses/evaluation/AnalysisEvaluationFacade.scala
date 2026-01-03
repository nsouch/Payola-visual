package cz.payola.domain.entities.analyses.evaluation

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.{Timeout => AkkaTimeout}
import scala.concurrent.duration._
import scala.concurrent.Await

/**
  * Facade for AnalysisEvaluation actor that provides the same interface as the old Actor-based implementation.
  * @param actorRef The ActorRef of the AnalysisEvaluation actor
  */
class AnalysisEvaluationFacade(private val actorRef: ActorRef) {
    /**
      * Progress of the analysis evaluation.
      */
    def getProgress: AnalysisEvaluationProgress = {
        implicit val t = AkkaTimeout(5.seconds)
        Await.result((actorRef ? GetProgress).mapTo[AnalysisEvaluationProgress], t.duration)
    }

    /**
      * Result of the analysis evaluation. [[scala.None]] in case the evaluation hasn't finished yet.
      */
    def getResult: Option[AnalysisResult] = {
        implicit val t = AkkaTimeout(5.seconds)
        Await.result((actorRef ? GetResult).mapTo[Option[AnalysisResult]], t.duration)
    }

    /**
      * Whether the analysis evaluation has finished.
      */
    def isFinished: Boolean = {
        getResult.isDefined
    }
}
