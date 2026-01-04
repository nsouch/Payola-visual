package cz.payola.domain.test

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test basique pour vérifier que la suite de tests fonctionne.
 * Utile pour tester le debugger et valider la configuration.
 */
class SanityTest extends AnyFlatSpec with Matchers {

  "Basic arithmetic" should "work correctly" in {
    val a = 2
    val b = 3
    val sum = a + b
    val product = a * b
    
    sum should be (5)
    product should be (6)
  }

  "String operations" should "concatenate properly" in {
    val hello = "Hello"
    val world = "World"
    val greeting = s"$hello $world!"
    
    greeting should be ("Hello World!")
    greeting.length should be (12)
  }

  "Collections" should "support basic operations" in {
    val numbers = List(1, 2, 3, 4, 5)
    val doubled = numbers.map(_ * 2)
    val sum = numbers.sum
    
    doubled should be (List(2, 4, 6, 8, 10))
    sum should be (15)
    numbers.length should be (5)
  }

  "Options" should "handle Some and None correctly" in {
    val someValue: Option[Int] = Some(42)
    val noneValue: Option[Int] = None
    
    someValue.isDefined should be (true)
    someValue.get should be (42)
    noneValue.isDefined should be (false)
  }

  "Exceptions" should "be thrown and caught correctly" in {
    assertThrows[IllegalArgumentException] {
      throw new IllegalArgumentException("Test exception")
    }
  }

  "Boolean logic" should "work as expected" in {
    val isTrue = true
    val isFalse = false
    
    (isTrue && !isFalse) should be (true)
    (isTrue || isFalse) should be (true)
    (!isTrue) should be (false)
  }

  "Case classes" should "support pattern matching" in {
    case class Person(name: String, age: Int)
    
    val person = Person("Alice", 30)
    
    val description = person match {
      case Person("Alice", age) => s"Alice is $age years old"
      case Person(name, _) => s"Person named $name"
    }
    
    description should be ("Alice is 30 years old")
    person.name should be ("Alice")
    person.age should be (30)
  }

  "Map operations" should "work correctly" in {
    val scores = Map("Alice" -> 95, "Bob" -> 87, "Charlie" -> 92)
    
    scores("Alice") should be (95)
    scores.contains("Bob") should be (true)
    scores.get("David") should be (None)
    scores.keys.size should be (3)
  }
}
