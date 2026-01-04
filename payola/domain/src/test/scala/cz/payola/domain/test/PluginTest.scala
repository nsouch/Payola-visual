package cz.payola.domain.test

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cz.payola.domain.entities.Plugin
import cz.payola.domain.entities.plugins.parameters.StringParameter
import cz.payola.common.ValidationException

class PluginTest extends AnyFlatSpec with Matchers {

    "Plugin" should "have sane getters and setters" in {
        val p: Plugin = new PseudoPlugin("MyPlugin")
        val param: StringParameter = new StringParameter("Hello", "")

        p.getParameter("Helo").isDefined should be (false)
        p.getParameter("Hello").isDefined should be (false)

        assertThrows[ValidationException] {
            p.name_=(null)
        }
        assertThrows[ValidationException] {
            p.name_=("")
        }

        p.name_=("NewName")
        p.name should be ("NewName")
    }
}

