package my.custom.plugin

import scala.collection._
import cz.payola.domain._
import cz.payola.domain.entities._
import cz.payola.domain.entities.plugins._
import cz.payola.domain.entities.plugins.parameters._
import cz.payola.domain.rdf._

class DelayInSeconds(name: String, inputCount: Int, parameters: immutable.Seq[Parameter[_]], id: String)
    extends Plugin(name, inputCount, parameters, id)
{
    def this() = this("Time Delay in seconds", 1, List(new IntParameter("Delay", 1)), IDGenerator.newId)

    def evaluate(instance: PluginInstance, inputs: IndexedSeq[Option[Graph]], progressReporter: Double => Unit) = {
        usingDefined(instance.getIntParameter("Delay")) { d =>
            (1 to d).foreach { i =>
                Thread.sleep(1000)
                progressReporter(i.toDouble / d)
            }
            inputs(0).getOrElse(PayolaGraph.empty)
        }
    }
}