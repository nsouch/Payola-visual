package cz.payola.domain.entities.analyses.evaluation

import akka.actor.{Actor, ActorRef, Props}
import collection.mutable
import cz.payola.domain.actors.{Timer, TimerTimeout}
import cz.payola.domain.entities.Analysis
import cz.payola.domain.entities.analyses._
import cz.payola.domain.entities.plugins.PluginInstance
import cz.payola.domain.entities.analyses.optimization._
import cz.payola.domain.rdf.Graph
import cz.payola.domain.entities.analyses.optimization.phases._

/**
  * An actor that performs an analysis evaluation. It verifies and optimizes the analysis, starts all plugin instance
  * evaluations, takes care of sending the plugin instance evaluation outputs to appropriate inputs, tracks the time
  * spent evaluating, tracks the evaluation progress and responds to the control messages.
  * @param analysis The analysis to evaluate.
  * @param timeout The maximal time limit allowed for the evaluation to take in milliseconds.
  */
class AnalysisEvaluation(val analysis: Analysis, private val timeout: Option[Long]) extends Actor
{
    private var timer: Option[ActorRef] = None

    private val instanceEvaluations = new mutable.ArrayBuffer[ActorRef]

    private var progress: AnalysisEvaluationProgress = AnalysisEvaluationProgress(Nil, Map.empty, Nil, Map.empty)

    private var result: Option[AnalysisResult] = None
    
    private var optimizedAnalysis: OptimizedAnalysis = _

    override def preStart(): Unit = {
        optimizedAnalysis = optimizeAnalysis()

        def startInstanceEvaluation(instance: PluginInstance, outputProcessor: Option[Graph] => Unit): ActorRef = {
            val evaluation = context.actorOf(Props(new InstanceEvaluation(instance, self, outputProcessor)))
            instanceEvaluations += evaluation

            // Start the preceding plugin evaluations.
            optimizedAnalysis.pluginInstanceInputBindings(instance).foreach { binding =>
                val instanceOutputProcessor = bindingOutputProcessor(evaluation, binding.targetInputIndex) _
                startInstanceEvaluation(binding.sourcePluginInstance, instanceOutputProcessor)
            }
            
            evaluation
        }

        // Start the evaluation of the analysis by starting the output plugin instance.
        timer = Some(context.actorOf(Props(new Timer(timeout, self))))
        progress = AnalysisEvaluationProgress(Nil, Map.empty, optimizedAnalysis.allOriginalInstances, Map.empty)
        startInstanceEvaluation(optimizedAnalysis.outputInstance.get, analysisOutputProcessor)
    }

    def receive: Receive = active

    def active: Receive = {
        case InstanceEvaluationProgress(i, v) =>
            optimizedAnalysis.originalInstances(i).foreach { originalInstance =>
                progress = progress.withChangedProgress(originalInstance, v)
            }
        case InstanceEvaluationError(i, t) =>
            optimizedAnalysis.originalInstances(i).foreach { originalInstance =>
                progress = progress.withError(originalInstance, t)
            }
        case InstanceEvaluationInput(_, graph) =>
            finishEvaluation(graph.map(g => Success(g, progress.errors)).getOrElse {
                Error(
                    new AnalysisException("An error occured during evaluation of the analysis."),
                    progress.errors
                )
            })
            context.become(finished)
        case TimerTimeout => 
            finishEvaluation(TimeoutResult)
            context.become(finished)
        case control: AnalysisEvaluationControl => 
            processControlMessage(control)
    }

    def finished: Receive = {
        case control: AnalysisEvaluationControl => 
            processControlMessage(control)
    }

    /**
      * Prepares the analysis before the actual evaluation.
      * @return The prepared optimized analysis.
      */
    private def optimizeAnalysis(): OptimizedAnalysis = {
        try {
            analysis.checkValidity()
        } catch {
            case throwable => finishEvaluation(Error(throwable, progress.errors))
        }

        val optimizer = new AnalysisOptimizer(List(
            new MergeConstructs,
            new MergeJoins,
            new MergeLimit,
            new MergeFetchersWithQueries
        ))
        optimizer.optimize(analysis)
    }

    /**
      * A function that takes a plugin instance evaluation output and sends it to the specified input.
      * @param targetEvaluation The target plugin instance evaluation.
      * @param targetInputIndex Index of the target plugin instance evaluation input.
      * @param output The output graph to send.
      */
    private def bindingOutputProcessor(targetEvaluation: ActorRef, targetInputIndex: Int)
        (output: Option[Graph]) {
        targetEvaluation ! InstanceEvaluationInput(targetInputIndex, output)
    }

    /**
      * A function that takes the output of the output plugin instance evaluation and sends it to the analysis
      * evaluation.
      * @param output The output graph to send.
      */
    private def analysisOutputProcessor(output: Option[Graph]) {
        self ! InstanceEvaluationInput(0, output)
    }

    /**
      * Processes analysis evaluation control messages.
      * @param message The control message to process.
      */
    private def processControlMessage(message: AnalysisEvaluationControl) {
        message match {
            case GetProgress => sender() ! progress
            case GetResult => sender() ! result
            case Stop if result.isEmpty => finishEvaluation(Stopped)
            case Terminate =>
                terminateDependentActors()
                context.stop(self)
        }
    }

    /**
      * Finishes the analysis evaluation and starts to respond only to control messages.
      * @param analysisResult The result to finish the analysis evaluation with.
      */
    private def finishEvaluation(analysisResult: AnalysisResult) {
        terminateDependentActors()
        result = Some(analysisResult)
    }

    /**
      * Terminates all the dependent actors.
      */
    private def terminateDependentActors() {
        timer.foreach(context.stop)
        instanceEvaluations.foreach(context.stop)
    }
}
