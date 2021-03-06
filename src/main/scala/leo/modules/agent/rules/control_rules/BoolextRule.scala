package leo.modules.agent.rules
package control_rules
import leo.datastructures.{AnnotatedClause, Clause, Signature}
import leo.datastructures.blackboard.{DataStore, DataType, Delta, Result}
import leo.modules.GeneralState
import leo.modules.control.Control

/**
  * Created by mwisnie on 1/10/17.
  */
class BoolextRule(inType : DataType[AnnotatedClause],
                  outType : DataType[AnnotatedClause])
                 (implicit state : GeneralState[AnnotatedClause]) extends Rule{

  implicit val sig : Signature = state.signature
  override final val inTypes : Seq[DataType[Any]] = Seq(inType)
  override final val outTypes: Seq[DataType[Any]] = Seq(outType)
  private val lockType = LockType(inType)

  override def name: String = "bool_ext"
  override def canApply(r: Delta): Seq[Hint] = {
    val ins = r.inserts(inType).iterator

    var res : Seq[Hint] = Seq()
    //
    while(ins.hasNext){
      val cl = ins.next()
      val fcl = Control.boolext(cl).filterNot(c => Clause.trivial(c.cl))
      if(fcl.size < 1 || (fcl.size == 1 && cl == fcl.head)){
//        println(s"[BoolExt] Could not apply to ${cl.pretty(signature)}")
        res = new ReleaseLockHint(inType, cl) +: res
      } else {
        res = new BoolextHint(cl, fcl) +: res
      }
    }
    res
  }

  class BoolextHint(sClause : AnnotatedClause, nClauses : Set[AnnotatedClause]) extends Hint{
    override def apply(): Delta = {
      leo.Out.debug(s"[BoolExt] on ${sClause.pretty(state.signature)}\n  > ${nClauses.map(_.pretty(state.signature)).mkString("\n  > ")}")
      val r = Result()
      val it = nClauses.iterator
      r.insert(lockType)(sClause)
      while(it.hasNext){
        val simpClause = Control.shallowSimp(it.next())
        r.insert(outType)(simpClause)
      }
      r
    }

    override lazy val read: Map[DataType[Any], Set[Any]] = Map(inType -> Set(sClause))
    override lazy val write: Map[DataType[Any], Set[Any]] = Map()
  }

}
