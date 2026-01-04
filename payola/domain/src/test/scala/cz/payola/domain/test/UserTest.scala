package cz.payola.domain.test

import cz.payola.domain._
import entities.{Group, User}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cz.payola.common.ValidationException

class UserTest extends AnyFlatSpec with Matchers {
    "User" should "retain values passed in the constructor" in {
        new User(_name = "Franta").name == "Franta"
    }

    it should "not add null owned Group" in  {
        val u: User = new User(_name = "Franta")
        assertThrows[IllegalArgumentException] {
            u.addOwnedGroup(null)
        }
    }

    it should "not be removed from null owned Group" in  {
        val u: User = new User(_name = "Franta")
        assertThrows[IllegalArgumentException] {
            u.removeOwnedGroup(null)
        }
    }

    it should "not be a member and should be an owner of group when added" in  {
        val u: User = new User(_name = "Franta")
        val g: Group = new Group(_name = "Grupa", _owner = u);
        !g.hasMember(u) && g.owner == u
    }

    it should "be a member but not an owner of group when added" in  {
        val u1: User = new User(_name = "Franta")
        val u2: User = new User(_name = "Pepa")
        val g: Group = new Group(_name = "Grupa", _owner = u1);
        g.addMember(u2)
        g.hasMember(u2) && u2 != g.owner
    }

    it should "not be renamed to null or empty string" in  {
        val u: User = new User(_name = "Franta")
        assertThrows[ValidationException] {
            u.name_=(null)
        }
        assertThrows[ValidationException] {
            u.name_=("")
        }
    }
}

