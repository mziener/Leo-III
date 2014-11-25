package leo.datastructures.context

import leo.Configuration
import leo.modules.CLParameterParser
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import leo.datastructures.context.impl._

/**
 * Created by ryu on 11/25/14.
 */
@RunWith(classOf[JUnitRunner])
class TreeContextSetTestSuite extends FunSuite {

  Configuration.init(new CLParameterParser(Array("arg0", "-v", "4")))

  val b : Context = Context()
  // b is root,

  test("One context"){


    b.split(BetaSplit, 2)
    val list = b.childContext.toList
    val l = list(0)
    val r = list(1)

    // l is the first and r is the second child

    val s : ContextSet[Int] = new TreeContextSet[Int]()
    assert(s.getAll(b).isEmpty, "The root context should contain no elements")

    s.add(4, l)
    assert(s.getAll(b).isEmpty, "The root context should contain no elements")
    assert(s.getAll(r).isEmpty, "The second context should contain no elements")
    assert(s.getAll(l).contains(4), "The first context should contain the 4")

    s.add(3,b)
    assert(s.getAll(b).contains(3), "The root context should contain the 3")
    assert(s.getAll(r).contains(3), "The second context should contain the 3")
    assert(s.getAll(l).contains(3), "The first context should contain the 3")
  }

  test("Insertion Before Split") {
    val s : ContextSet[Int] = new TreeContextSet[Int]()
    s.add(3,b)

    b.split(BetaSplit, 2)
    val list = b.childContext.toList
    val l = list(0)
    val r = list(1)

    assert(s.getAll(b).contains(3), "The root context should contain the 3")
    assert(s.getAll(r).contains(3), "The second context should contain the 3")
    assert(s.getAll(l).contains(3), "The first context should contain the 3")
  }
}