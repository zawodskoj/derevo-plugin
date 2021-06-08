package pw.muffet.things.derevo

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScParameterizedType}

class DerevoInjector extends SyntheticMembersInjector {
  def extractTypeclassTpe(derivationObjectTpe: ScType): Option[ScTypeDefinition] =
    derivationObjectTpe.parents.collect { case s: ScParameterizedType => s }
      .map(s => s.extractDesignated(true) -> s.typeArguments)
      .collectFirst {
        case (Some(t: ScTrait), Seq(typearg)) if t.qualifiedName == "derevo.Derivation" => typearg.extractDesignated(true)
        case (Some(t: ScTrait), Seq(_, typearg, _)) if t.qualifiedName == "derevo.SpecificDerivation" => typearg.extractDesignated(true)
      }
      .collectFirst { case Some(designated: ScTypeDefinition) => designated }

  def extractTypeclassTpeFromShape(expr: ScExpression): Option[ScTypeDefinition] = expr match {
    case r: ScReferenceExpression =>
      r.shapeResolve.map(x => x.fromType.map(x => Right(x)).orElse(x.parentElement.collect { case o: ScObject => o.`type`() }))
        .collect { case Some(Right(t: ScType)) => extractTypeclassTpe(t) }
        .collectFirst { case Some(v) => v }
    case _ => None
  }

  def extractTypeclassTpeFromExprType(expr: ScExpression): Option[ScTypeDefinition] = expr.`type`() match {
    case Right(tpe) => extractTypeclassTpe(tpe)
    case _ => None
  }

  def extractTypeclassTpeFromExpr(expr: ScExpression): Option[ScTypeDefinition] =
    extractTypeclassTpeFromExprType(expr).orElse(extractTypeclassTpeFromShape(expr))

  def tryGenerateImplicitForReferenceExpr(source: ScTypeDefinition, expr: ScReferenceExpression): Option[String] =
    extractTypeclassTpeFromExpr(expr)
      .map(tpe => s"implicit val derived_${tpe.qualifiedName.replace(".", "_")}: _root_.${tpe.qualifiedName}[${source.name}] = ???")

  def deriveAnnotArgs(source: PsiClass): Seq[ScExpression] = source match {
    case tpe: ScTypeDefinition =>
      tpe.findAnnotationNoAliases("derevo.derive") match {
        case annot: ScAnnotation => annot.annotationExpr.getAnnotationParameters
        case _ => Nil
      }
    case _ => Nil
  }

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = deriveAnnotArgs(source).nonEmpty

  override def injectMembers(source: ScTypeDefinition): Seq[String] = source match {
    case o: ScObject =>
      deriveAnnotArgs(o.fakeCompanionClassOrCompanionClass).flatMap {
        case r: ScReferenceExpression =>
          tryGenerateImplicitForReferenceExpr(source, r)
        case m: ScMethodCall =>
          m.getChildren.headOption.collect { case s: ScReferenceExpression => s }.flatMap(tryGenerateImplicitForReferenceExpr(source, _))
        case _ => None
      }
    case _ => Nil
  }
}
