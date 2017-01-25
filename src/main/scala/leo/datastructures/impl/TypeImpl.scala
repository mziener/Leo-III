package leo.datastructures.impl

import leo.datastructures.{Kind, Subst, Type, TypeFront, Signature}

protected[datastructures] abstract class TypeImpl extends Type {
  def splitFunParamTypesAt(n: Int): (Seq[Type], Type) = splitFunParamTypesAt0(n, Seq())
  protected[impl] def splitFunParamTypesAt0(n: Int, acc: Seq[Type]): (Seq[Type], Type) = if (n == 0) (acc, this) else
    throw new UnsupportedOperationException("splitFunParamTypesAt0 with non-zero n on non-Function type")
  // to be overridden by abstraction type below

  final def closure(subst: Subst) = substitute(subst)
  def instantiate(by: Seq[Type]): Type = this
  final def instantiate(by: Type): Type =  instantiate(Seq(by))
  def monomorphicBody: Type = this
}

/** Literal type, i.e. `$o` */
protected[datastructures] case class GroundTypeNode(id: Signature#Key, args: Seq[Type]) extends TypeImpl {
  // Pretty printing
  lazy val pretty = {
    if (args.isEmpty)
      s"ty($id)"
    else
      s"ty($id)" +"(" + args.map(_.pretty).mkString(",") + ")"
  }
  final def pretty(sig: Signature) = {
    if (args.isEmpty)
      sig(id).name
    else
      s"${sig(id).name}(${args.map(_.pretty).mkString(",")})"
  }

  // Predicates on types
  override final val isBaseType         = args.isEmpty
  override final val isComposedType     = args.nonEmpty
  final def isApplicableWith(arg: Type) = false

  // Queries on types
  lazy final val typeVars = args.flatMap(_.typeVars).toSet
  lazy final val symbols = Set(id)

  final val funDomainType = None
  final val codomainType  = this
  final val arity         = 0
  final val funParamTypesWithResultType = Seq(this)
  final val order         = 0
  final val polyPrefixArgsCount = 0

  final val scopeNumber = 0

  final def app(ty: Type): Type = GroundTypeNode(id, args :+ ty)

  final def occurs(ty: Type) = ty match {
    case GroundTypeNode(key, args2) if key == id => args == args2
    case _ => args.exists(_.occurs(ty))
  }

  // Substitutions
  final def replace(what: Type, by: Type) = if (what == this) by
    else GroundTypeNode(id, args.map(_.replace(what, by)))

  final def substitute(subst: Subst) = GroundTypeNode(id, args.map(_.substitute(subst)))
}

/** Type of a (bound) type variable when itself used as type in polymorphic function */
protected[datastructures] case class BoundTypeNode(scope: Int) extends TypeImpl {
  // Pretty printing
  final def pretty = scope.toString
  final def pretty(sig: Signature) = scope.toString

  // Predicates on types
  override final val isBoundTypeVar     = true
  final def isApplicableWith(arg: Type) = false

  // Queries on types
  final val typeVars: Set[Type] = Set(this)
  final val symbols = Set[Signature#Key]()

  final val funDomainType   = None
  final val codomainType = this
  final val arity = 0
  final val funParamTypesWithResultType = Seq(this)
  final val order = 0
  final val polyPrefixArgsCount = 0

  final val scopeNumber = -scope

  final def app(ty: Type): Type = throw new IllegalArgumentException("Typed applied to type variable")

  final def occurs(ty: Type) = false

  // Substitutions
  final def replace(what: Type, by: Type) = if (what == this) by else this
  import leo.datastructures.{BoundFront, TypeFront}
  final def substitute(subst: Subst) = subst.substBndIdx(scope) match {
    case BoundFront(j) => BoundTypeNode(j)
    case TypeFront(t)  => t
    case _ => throw new IllegalArgumentException("type substitution contains terms")
  }
}

/** Function type `in -> out` */
protected[datastructures] case class AbstractionTypeNode(in: Type, out: Type) extends TypeImpl {
  // Pretty printing
  def pretty = in match {
    case funTy:AbstractionTypeNode => "(" + funTy.pretty + ") -> " + out.pretty
    case otherTy:Type              => otherTy.pretty + " -> " + out.pretty
  }
  final def pretty(sig: Signature) = in match {
    case funTy:AbstractionTypeNode => "(" + funTy.pretty(sig) + ") -> " + out.pretty(sig)
    case otherTy:Type              => otherTy.pretty(sig) + " -> " + out.pretty(sig)
  }

  // Predicates on types
  override final val isFunType          = true
  final def isApplicableWith(arg: Type) = arg == in

  // Queries on types
  final lazy val typeVars = in.typeVars ++ out.typeVars
  final lazy val symbols = in.symbols ++ out.symbols

  final val funDomainType   = Some(in)
  final val codomainType = out
  final lazy val arity = 1 + out.arity
  final lazy val funParamTypesWithResultType = Seq(in) ++ out.funParamTypesWithResultType
  final lazy val order = Math.max(1+in.order,out.order)
  final val polyPrefixArgsCount = 0

  final override protected[impl] def splitFunParamTypesAt0(n: Int, acc: Seq[Type]): (Seq[Type], Type) = if (n == 0) (acc, this) else
    out.asInstanceOf[TypeImpl].splitFunParamTypesAt0(n-1, in +: acc)

  final lazy val scopeNumber = Math.min(in.scopeNumber, out.scopeNumber)

  final def app(ty: Type): Type = throw new IllegalArgumentException("Typed applied to abstraction type")

  final def occurs(ty: Type) = in.occurs(ty) || out.occurs(ty)

  // Substitutions
  final def replace(what: Type, by: Type) = if (what == this) by
  else AbstractionTypeNode(in.replace(what,by), out.replace(what,by))
  final def substitute(subst: Subst) = AbstractionTypeNode(in.substitute(subst), out.substitute(subst))
}

/** Product type `l * r` */
protected[datastructures] case class ProductTypeNode(l: Type, r: Type) extends TypeImpl {
  // Pretty printing
  final def pretty = s"(${l.pretty} * ${r.pretty})"
  final def pretty(sig: Signature) =  s"(${l.pretty(sig)} * ${r.pretty(sig)})"

  // Predicates on types
  final override val isProdType          = true
  final def isApplicableWith(arg: Type) = false

  // Queries on types
  final lazy val typeVars = l.typeVars ++ r.typeVars
  final lazy val symbols = l.symbols ++ r.symbols

  final val funDomainType   = None
  final val codomainType = this
  final val arity = 0
  final val funParamTypesWithResultType = Seq(this)
  final val order = 0
  final val polyPrefixArgsCount = 0

  final lazy val scopeNumber = Math.min(l.scopeNumber, r.scopeNumber)

  final def app(ty: Type): Type = throw new IllegalArgumentException("Typed applied to product type")

  final def occurs(ty: Type) = l.occurs(ty) || r.occurs(ty)

  // Substitutions
  final def replace(what: Type, by: Type) = if (what == this) by
  else ProductTypeNode(l.replace(what,by), r.replace(what,by))
  final def substitute(subst: Subst) = ProductTypeNode(l.substitute(subst), r.substitute(subst))

  // Other operations
  final override val numberOfComponents: Int = 1 + l.numberOfComponents
}

/** Product type `l + r` */
protected[datastructures] case class UnionTypeNode(l: Type, r: Type) extends TypeImpl {
  // Pretty printing
  final def pretty = s"(${l.pretty} + ${r.pretty})"
  final def pretty(sig: Signature) =  s"(${l.pretty(sig)} + ${r.pretty(sig)})"

  // Predicates on types
  final override val isUnionType        = true
  final def isApplicableWith(arg: Type) = false

  // Queries on types
  final lazy val typeVars = l.typeVars ++ r.typeVars
  final lazy val symbols = l.symbols ++ r.symbols

  final val funDomainType   = None
  final val codomainType = this
  final val arity = 0
  final val funParamTypesWithResultType = Seq(this)
  final val order = 0
  final val polyPrefixArgsCount = 0

  final lazy val scopeNumber = Math.min(l.scopeNumber, r.scopeNumber)

  final def app(ty: Type): Type = throw new IllegalArgumentException("Typed applied to union type")

  final def occurs(ty: Type) = l.occurs(ty) || r.occurs(ty)

  // Substitutions
  final def replace(what: Type, by: Type) = if (what == this) by
  else UnionTypeNode(l.replace(what,by), r.replace(what,by))
  final def substitute(subst: Subst) = UnionTypeNode(l.substitute(subst), r.substitute(subst))
}

/**
 * Type of a polymorphic function
 * @param body The type in which a type variable is now bound to this binder
 */
protected[datastructures] case class ForallTypeNode(body: Type) extends TypeImpl {
  // Pretty printing
  final def pretty = s"∀. ${body.pretty}"
  final def pretty(sig: Signature) = s"∀. ${body.pretty(sig)}"

  // Predicates on types
  final override val isPolyType         = true
  final def isApplicableWith(arg: Type) = arg match { // we dont allow instantiating type variables with polymorphic types
    case ForallTypeNode(_) => false
    case _ => true
  }

  // Queries on types
  final lazy val typeVars = body.typeVars
  final lazy val symbols = body.symbols

  final val funDomainType   = None
  final val codomainType = this
  final val arity = 0
  final val funParamTypesWithResultType = Seq(this)
  final val order = 0
  final lazy val polyPrefixArgsCount = 1 + body.polyPrefixArgsCount

  final override lazy val monomorphicBody: Type = body.monomorphicBody

  final lazy val scopeNumber = body.scopeNumber + 1

  final def app(ty: Type): Type = throw new IllegalArgumentException("Typed applied to type abstraction") //TODO: refine, since its basically beta reduction

  final def occurs(ty: Type) = body.occurs(ty)

  // Substitutions
  final def replace(what: Type, by: Type) = if (what == this) by
  else ForallTypeNode(body.replace(what, by))
  final def substitute(subst: Subst) = ForallTypeNode(body.substitute(subst.sink))

  final override def instantiate(by: Seq[Type]): Type = if (by.isEmpty) this
  else body.substitute(TypeFront(by.head) +: Subst.id).instantiate(by.tail)
}

object TypeImpl {
  private var types: Map[Signature#Key, Map[Seq[Type], Type]] = Map()
  private var varTypes: Map[Int, Type] = Map()

  def mkType(identifier: Signature#Key, args: Seq[Type]): Type = { //GroundTypeNode(identifier, args)
    if (types.isDefinedAt(identifier)) {
      val map = types(identifier)
      if (map.isDefinedAt(args))
        map(args)
      else {
        val ty = GroundTypeNode(identifier, args)
        types = types + (identifier -> (map + (args -> ty)))
        ty
      }
    } else {
      val ty = GroundTypeNode(identifier, args)
      val map = Map(args -> ty)
      types = types + (identifier -> map)
      ty
    }
  }
  def mkFunType(in: Type, out: Type): Type = AbstractionTypeNode(in, out)
  def mkProdType(t1: Type, t2: Type): Type = ProductTypeNode(t1,t2)
  def mkUnionType(t1: Type, t2: Type): Type = UnionTypeNode(t1,t2)
  def mkPolyType(bodyType: Type): Type = ForallTypeNode(bodyType)
  def mkVarType(scope: Int): Type = { //BoundTypeNode(scope)
    if (varTypes.isDefinedAt(scope))
      varTypes(scope)
    else {
      val ty = BoundTypeNode(scope)
      varTypes = varTypes + (scope -> ty)
      ty
    }
  }
}


//////////////////////////////////
/// Kinds
//////////////////////////////////

/** Represents the kind `*` (i.e. the type of a type) */
protected[datastructures] case object TypeKind extends Kind {
  final val pretty = "*"

  final val isTypeKind = true
  final val isSuperKind = false
  final val isFunKind = false

  final val arity = 0
}

protected[datastructures] case class FunKind(from: Kind, to: Kind) extends Kind {
  final def pretty = from.pretty + " > " + to.pretty

  final val isTypeKind = false
  final val isSuperKind = false
  final val isFunKind = true

  lazy val arity = 1 + to.arity
}

/** Artificial kind that models the type of `*` (i.e. []) */
protected[datastructures] case object SuperKind extends Kind {
  final val pretty = "#"

  final val isTypeKind = false
  final val isSuperKind = true
  final val isFunKind = false

  final val arity = 0
}
