package stimulus.lang.js

import com.intellij.lang.javascript.patterns.JSPatterns
import com.intellij.lang.javascript.psi.JSExecutionScope
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSThisExpression
import com.intellij.lang.javascript.psi.ecma6.impl.JSLocalImplicitElementImpl
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.types.JSContext
import com.intellij.lang.javascript.psi.util.JSUtils
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PatternCondition
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import stimulus.lang.getLiteralValues

const val targetPropertySuffix = "Target"
const val classPropertySuffix = "Class"
const val targetsField = "targets"
const val classesField = "classes"

class StimulusJSReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            JSPatterns.jsReferenceExpression()
                .with(object : PatternCondition<JSReferenceExpression?>("class this reference") {
                    override fun accepts(t: JSReferenceExpression, context: ProcessingContext?): Boolean {
                        if (t.qualifier !is JSThisExpression) return false
                        return getOwnerClass(t) != null
                    }
                }),
            JSOnClassThisReferenceProvider()
        )
    }
}

fun getOwnerClass(element: PsiElement): JSClass? {
    val scope = PsiTreeUtil.getContextOfType(element, JSExecutionScope::class.java) ?: return null
    return JSUtils.getMemberContainingClass(scope)
}

private class JSOnClassThisReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is JSReferenceExpression) return PsiReference.EMPTY_ARRAY
        return arrayOf(StimulusClassFieldReference(element, element.rangeInElement))
    }
}

class StimulusClassFieldReference(private val refExpression: JSReferenceExpression, range: TextRange) :
    PsiReferenceBase<PsiElement>(refExpression, range, true) {

    override fun resolve(): PsiElement? {
        val jsClass = getOwnerClass(element) ?: return null
        val name = refExpression.referenceName ?: return null
        
        val targetResolve = resolveAsTarget(jsClass, name)
        if (targetResolve != null) return targetResolve
        
        val classResolve = resolveAsClass(jsClass, name)
        if (classResolve != null) return classResolve

        return null
    }

    private fun resolveAsTarget(jsClass: JSClass, name: String): PsiElement? {
        return resolveFromFieldWithSuffix(jsClass, name, targetPropertySuffix, targetsField)
    }

    private fun resolveAsClass(jsClass: JSClass, name: String): PsiElement? {
        return resolveFromFieldWithSuffix(jsClass, name, classPropertySuffix, classesField)
    }

    private fun resolveFromFieldWithSuffix(
        jsClass: JSClass,
        name: String,
        suffix: String,
        fieldName: String
    ): PsiElement? {
        if (!name.endsWith(suffix)) return null
        val propName = name.substring(0, name.length - suffix.length)
        val targetField = jsClass.findFieldByName(fieldName) ?: return null
        if (targetField.jsContext != JSContext.STATIC) return null
        if (null != getLiteralValues(targetField).firstOrNull { it == propName }) {
            //to force empty type
            return JSLocalImplicitElementImpl(name, null, targetField, JSImplicitElement.Type.Property)
        }
        return null
    }
}