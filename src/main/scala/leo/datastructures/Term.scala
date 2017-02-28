package leo.datastructures

import scala.language.implicitConversions

/**
 * Abstract interface for terms and operations on them that can be
 * done in the internal language.
 * Terms are generated by
 *
 * {{{s,t ::= i (bound symbol)
 *       | c (constant symbol)
 *       | λ:tau.s (term abstraction)
 *       | s t (term application)
 *       | Λs (type abstraction)
 *       | s tau (type application)}}}
 *
 * where `c` is some symbol (constant) and `tau` is a type (see `Type`).
 *
 * @author Alexander Steen
 * @since 21.05.2014
 * @note Updated 02.06.2014 Cleaned up method set, lambda terms always have types
 * @note Updated 09.06.2014 Added pattern matcher for terms, added definition expansion
 */
trait Term extends Pretty with Prettier {
  // Predicates on terms
  /** Returns true iff `this` is either a constant or a variable, i.e. `isConstant || isVariable`. */
  def isAtom: Boolean
  /** Returns true iff `this` is a constant from the signature as a term (i.e. TermApp(c,Nil) where c is a constant). */
  def isConstant: Boolean
  /** Returns true iff `this` is a (free/bound/loose bound) variable. */
  def isVariable: Boolean
  /** Returns true iff `this` is an abstraction (λ. t') for some term t'. */
  def isTermAbs: Boolean
  /** Returns true iff `this` is a type abstraction (Λ. t') for some term t'. */
  def isTypeAbs: Boolean
  /** Returns trie iff `this` is an application h ∙ args. */
  def isApp: Boolean
  def flexHead: Boolean
  /** `true` is the term is known to be in beta-normal form, else false.
    * @note Might return false if the term is in beta normal form but .betaNormalize was never invoked in it. */
  def isBetaNormal: Boolean

  type Sharing = Boolean
  def sharing: Sharing

  //////////////////////////
  // Handling def. expansion
  //////////////////////////

  /** Returns true iff any subterm of this term can be expanded by its definition. */
  def δ_expandable(implicit sig: Signature): Boolean
  /** Exhaustively expands all defined subterms (i.e. defined symbols) by its definitions.
    * This may not terminate for recursively defined symbols. */
  def δ_expand(implicit sig: Signature): Term
  /** Expands defined subterms as `δ_expand` but with at most `rep` recursive replacements. */
  def δ_expand(rep: Int)(implicit sig: Signature): Term
  /** Exhaustively expands all symbols except for those in `symbs` which are
    * defined by its definitions.
    * This may not terminate for recursively defined symbols. */
  def δ_expand_upTo(symbs: Set[Signature#Key])(implicit sig: Signature): Term

  //////////////////////////
  // Queries on terms
  //////////////////////////
  /** Returns the type of the term */
  def ty: Type
  /** Returns the free occurrences of variables as tuple (index, type). */
  def fv: Set[(Int, Type)]
  /** Same as `fv`, just that it returns the free occurences of variables
    * as terms. */
  final def freeVars: Set[Term] = fv.map(v => Term.mkBound(v._2, v._1))
  /** Same as `fv`, just that it returns the free occurences of variables
    * as de-bruijn indices. */
  final def looseBounds: Set[Int] = fv.map(_._1)
  /** true iff the term does not contain any free variables. */
  final def ground: Boolean = fv.isEmpty
  /** Returns the free occurrences of type variables. */
  def tyFV: Set[Int]
  def feasibleOccurrences: Map[Term, Set[Position]]
  def headSymbol: Term
  def headSymbolDepth: Int
  def size: Int

  def symbols: Multiset[Signature#Key]
  final def symbolsOfType(ty: Type)(implicit sig: Signature) = {
    symbols.filter({i => sig(i)._ty == ty})
  }
  // Functions for FV-Indexing
  def fvi_symbolFreqOf(symbol: Signature#Key): Int
  def fvi_symbolDepthOf(symbol: Signature#Key): Int

  // Substitutions and replacements
  /** Replace every occurrence of `what` in `this` by `by`. */
  def replace(what: Term, by: Term): Term
  def replaceAt(at: Position, by: Term): Term

  /** Apply substitution `subst` to underlying term.
    * I.e. each free variable `i` (NOT meta-vars!) occurring within `this` is replaced by `subst(i)`,
    * The term is then beta normalized */
  def substitute(termSubst: Subst, typeSubst: Subst = Subst.id): Term = closure(termSubst, typeSubst).betaNormalize
//  /** Apply type substitution `tySubst` to underlying term. */
//  def tySubstitute(tySubst: Subst): Term = this.tyClosure(tySubst).betaNormalize
  /** Apply a shifting substitution by `by`, i.e. return this.substitute(Subst.shift(by)).betanormalize*/
  def lift(by: Int): Term = substitute(Subst.shift(by)).betaNormalize

  /** Explicitly create a closure, i.e. a postponed (simultaneous) substitution (of types and terms) */
  def closure(termSubst: Subst, typeSubst: Subst): Term
  /** Explicitly create a term closure, i.e. a postponed substitution */
  def termClosure(subst: Subst): Term
  /** Explicitly create a term closure with underlying type substitution `tySubst`. */
  def typeClosure(subst: Subst): Term

  // Other operations
  def compareTo(that: Term)(implicit sig: Signature): CMP_Result = leo.Configuration.TERM_ORDERING.compare(this, that)(sig)
  /** Return the β-nf of the term */
  def betaNormalize: Term
  /** Return the eta-long-nf of the term */
  def etaExpand: Term
  /** Return the eta-short-nf of the term */
  def etaContract: Term
}

/////////////////////////////
// Associated traits, exceptions, ...
/////////////////////////////

class NotWellTypedException(msg: String, term: Option[Term]) extends RuntimeException(msg) {
  def this(msg: String) {
    this(msg, None)
  }
}
object NotWellTypedException {
  final def apply(): NotWellTypedException = new NotWellTypedException("", None)
  final def apply(msg: String): NotWellTypedException = new NotWellTypedException(msg, None)
  final def apply(term: Term): NotWellTypedException = new NotWellTypedException(term.pretty, Some(term))
  final def apply(msg: String, term: Term): NotWellTypedException = new NotWellTypedException(msg, Some(term))
}

/////////////////////////////
// Companion factory object
/////////////////////////////
/**
 * Term Factory object. Only this class is used to create new terms.
 *
 * Current default term implementation: [[impl.TermImpl]]
 */
object Term extends TermBank {
  import impl.TermImpl

  // Factory method delegation
  final def mkAtom(id: Signature#Key)(implicit sig: Signature): Term = TermImpl.mkAtom(id)(sig)
  final def mkAtom(id: Signature#Key, ty: Type): Term = TermImpl.mkAtom(id,ty)
  final def mkBound(t: Type, scope: Int): Term = TermImpl.mkBound(t,scope)
  final def mkTermApp(func: Term, arg: Term): Term = TermImpl.mkTermApp(func, arg)
  final def mkTermApp(func: Term, args: Seq[Term]): Term = TermImpl.mkTermApp(func, args)
  final def mkTermAbs(t: Type, body: Term): Term = TermImpl.mkTermAbs(t, body)
  final def mkTypeApp(func: Term, arg: Type): Term = TermImpl.mkTypeApp(func, arg)
  final def mkTypeApp(func: Term, args: Seq[Type]): Term = TermImpl.mkTypeApp(func, args)
  final def mkTypeAbs(body: Term): Term = TermImpl.mkTypeAbs(body)
  final def mkApp(func: Term, args: Seq[Either[Term, Type]]): Term = TermImpl.mkApp(func, args)

  // Term bank method delegation
  final val local = TermImpl.local
  final def insert(term: Term): Term = TermImpl.insert(term)
  final def contains(term: Term): Boolean = TermImpl.contains(term)
  final def reset(): Unit = TermImpl.reset()

  // Utility
  /** Checks if a term is well-typed. Does does check whether free variables
    * are consistently typed. */
  final def wellTyped(t: Term): Boolean = {
    try {
      TermImpl.wellTyped(t.asInstanceOf[TermImpl])
    } catch {
      case e: NotWellTypedException => false
    }
  }

  final def isLocal(t: Term): Boolean = !t.sharing
  final def isGlobal(t: Term): Boolean = t.sharing

  // Conversions
  /** Convert tuple (i,ty) to according de-Bruijn index */
  final implicit def intToBoundVar(in: (Int, Type)): Term = mkBound(in._2,in._1)
  /** Convert tuple (i,j) to according de-Bruijn index (where j is a type-de-Bruijn index) */
  final implicit def intsToBoundVar(in: (Int, Int)): Term = mkBound(in._2,in._1)


  // Legacy functions type types for statistics, like to be reused sometime
  type TermBankStatistics = (Int, Int, Int, Int, Int, Int, Map[Int, Int])
  final def statistics: TermBankStatistics = TermImpl.statistics


  //////////////////////////////////////////
  // Patterns for term structural matching
  //////////////////////////////////////////
  /**
   * Pattern for matching bound symbols in terms (i.e. De-Bruijn-Indices). Usage:
   * {{{
   * t match {
   *  case Bound(ty,scope) => println("Matched bound symbol of lambda-scope "
   *                                  + scope.toString + " with type "+ ty.pretty)
   *  case _               => println("something else")
   * }
   * }}}
   */
  object Bound { final def unapply(t: Term): Option[(Type, Int)] = TermImpl.boundMatcher(t) }

  /**
   * Pattern for matching constant symbols in terms (i.e. symbols in signature). Usage:
   * {{{
   * t match {
   *  case Symbol(constantKey) => println("Matched constant symbol "+ constantKey.toString)
   *  case _                   => println("something else")
   * }
   * }}}
   */
  object Symbol { final def unapply(t: Term): Option[Signature#Key] = TermImpl.symbolMatcher(t) }

  /**
   * Pattern for matching a general application (i.e. terms of form `(h ∙ S)`), where
   * `h` is the function term and `S` is a sequence of terms/types (arguments).
   * Usage:
   * {{{
   * t match {
   *  case s ∙ args => println("Matched application. Head: " + s.pretty
   *                                            + " Args: " + args.map.fold(_.pretty,_.pretty)).toString
   *  case _       => println("something else")
   * }
   * }}}
   */
  object ∙ { final def unapply(t: Term): Option[(Term, Seq[Either[Term, Type]])] = TermImpl.appMatcher(t) }

  /**
   * Pattern for matching a term application (i.e. terms of form `(h ∙ S)`), where
   * `h` is the function term and `S` is a sequence of terms only (arguments).
   * Usage:
   * {{{
   * t match {
   *  case s ∙ args => println("Matched application. Head: " + s.pretty
   *                                            + " Args: " + args.map.fold(_.pretty,_.pretty)).toString
   *  case _       => println("something else")
   * }
   * }}}
   */
  object TermApp {
    final def unapply(t: Term): Option[(Term, Seq[Term])] = t match {
      case h ∙ sp => if (sp.forall(_.isLeft)) {
                        Some(h, sp.map(_.left.get))
                      } else None
      case _ => None
    }
  }

  /**
   * Pattern for matching a type application (i.e. terms of form `(h ∙ S)`), where
   * `h` is the function term and `S` is a sequence of types only (arguments).
   * Usage:
   * {{{
   * t match {
   *  case s ∙ args => println("Matched application. Head: " + s.pretty
   *                                            + " Args: " + args.map.fold(_.pretty,_.pretty)).toString
   *  case _       => println("something else")
   * }
   * }}}
   */
  object TypeApp {
    final def unapply(t: Term): Option[(Term, Seq[Type])] = t match {
      case h ∙ sp => if (sp.forall(_.isRight)) {
        Some(h, sp.map(_.right.get))
      } else None
      case _ => None
    }
  }

  /**
   * Pattern for matching (term) abstractions in terms (i.e. terms of form `(\(ty)(s))` where `ty` is a type). Usage:
   * {{{
   * t match {
   *  case ty :::> s => println("Matched abstraction. Type of parameter: " + ty.pretty
   *                                                           + " Body: " + s.pretty)
   *  case _         => println("something else")
   * }
   * }}}
   */
  object :::> { final def unapply(t: Term): Option[(Type,Term)] = TermImpl.termAbstrMatcher(t) }

  /**
   * Pattern for matching (type) abstractions in terms (i.e. terms of form `/\(s)`). Usage:
   * {{{
   * t match {
   *  case TypeLambda(s) => println("Matched type abstraction. Body: " + s.pretty)
   *  case _             => println("something else")
   * }
   * }}}
   */
  object TypeLambda { final def unapply(t: Term): Option[Term] = TermImpl.typeAbstrMatcher(t) }

  /** A lexicographical ordering of terms. Its definition is arbitrary, but should form
   * a total order on terms.
   * */
  object LexicographicalOrdering extends Ordering[Term] {

      private def compareApp(a: Seq[Either[Term, Type]], b: Seq[Either[Term, Type]]): Int = (a, b) match {
        case (Left(h1) :: t1, Left(h2) :: t2) =>
          val c = this.compare(h1, h2)
          if (c != 0) c else compareApp(t1, t2)
        case (Right(h1) :: t1, Right(h2) :: t2) =>
          val c = Type.LexicographicalOrdering.compare(h1, h2)
          if (c != 0) c else compareApp(t1, t2)
        case (Left(h1) :: t1, Right(h2) :: t2) => 1
        case (Right(h1) :: t1, Left(h2) :: t2) => -1
        case (h :: t, Nil) => 1
        case (Nil, h :: t) => -1
        case (Nil, Nil) => 0
      }

      // The order of the match is important because Bound and Symbol is a special case of Application.
      def compare(a: Term, b: Term): Int = (a, b) match {
        case (Bound(t1, s1), Bound(t2, s2)) =>
          val c = s1 compare s2
          if (c == 0) Type.LexicographicalOrdering.compare(t1, t2) else c
        case (Bound(t, s), _) => 1
        case (_, Bound(t, s)) => -1
        case (Symbol(t1), Symbol(t2)) => t1 compare t2
        case (Symbol(t), _) => 1
        case (_, Symbol(t)) => -1
        case (h1 ∙ a1, h2 ∙ a2) =>
          val c = this.compare(h1, h2)
          if (c == 0) compareApp(a1, a2) else c
        case (t1 :::> s1, t2 :::> s2) =>
          val c = Type.LexicographicalOrdering.compare(t1, t2)
          if (c == 0) this.compare(s1, s2) else c
        case (TypeLambda(s1), TypeLambda(s2)) =>
          this.compare(s1, s2)
        case (h1 ∙ a1, _) => 1
        case (_, h2 ∙ a2) => -1
        case (t1 :::> s1, _) => 1
        case (_, t2 :::> s2) => -1
        case (TypeLambda(s1), _) => 1
        case (_, TypeLambda(s2)) => -1
      }
    }
}
