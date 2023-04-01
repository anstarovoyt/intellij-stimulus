package stimulus.lang.js

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.javascript.JSStringUtil
import com.intellij.lang.javascript.patterns.JSPatterns
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.impl.JSLocalImplicitElementImpl
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.types.JSContext
import com.intellij.lang.javascript.psi.util.JSUtils
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import stimulus.lang.getLiteralValues

const val targetPropertySuffix = "Target"
const val classPropertySuffix = "Class"
const val valuePropertySuffix = "Value"
const val outletPropertySuffix = "Outlet"
const val targetsField = "targets"
const val classesField = "classes"
const val valuesField = "values"
const val outletsField = "outlets"

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
    val scope = PsiTreeUtil.findFirstContext(element, true) {
        return@findFirstContext (it is JSExecutionScope &&
                (it !is JSFunction || !JSPsiImplUtils.isArrowFunction(it))
                )
    } ?: return null
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

        val outletResolve = resolveAsOutlet(jsClass, name)
        if (outletResolve != null) return outletResolve

        return resolveAsValue(jsClass, name)
    }

    private fun resolveAsTarget(jsClass: JSClass, name: String): PsiElement? {
        return resolveFromFieldWithSuffix(jsClass, name, targetPropertySuffix, targetsField)
    }

    private fun resolveAsClass(jsClass: JSClass, name: String): PsiElement? {
        return resolveFromFieldWithSuffix(jsClass, name, classPropertySuffix, classesField)
    }

    private fun resolveAsValue(jsClass: JSClass, name: String): PsiElement? {
        if (!name.endsWith(valuePropertySuffix) && !name.endsWith(StringUtil.pluralize(valuePropertySuffix))) return null
        val field = getStaticField(jsClass, valuesField) ?: return null
        val propName = getSimplePropertyName(name, valuePropertySuffix)
        val jsProperty = (field.initializer as? JSObjectLiteralExpression)?.properties?.firstOrNull {
            it.name == propName
        } ?: return null

        return JSLocalImplicitElementImpl(name, null, jsProperty, JSImplicitElement.Type.Property)
    }

    private fun resolveAsOutlet(jsClass: JSClass, name: String): PsiElement? {
        return resolveFromFieldWithSuffix(jsClass, JSStringUtil.toCamelCase(name), outletPropertySuffix, outletsField)
    }

    private fun resolveFromFieldWithSuffix(
        jsClass: JSClass,
        name: String,
        suffix: String,
        fieldName: String
    ): PsiElement? {
        val field = getStaticField(jsClass, fieldName) ?: return null
        if (!name.endsWith(suffix) && !name.endsWith(StringUtil.pluralize(suffix))) return null
        val propName = getSimplePropertyName(name, suffix)
        if (null != getLiteralValues(field).firstOrNull { it == propName }) {
            //to force empty type
            return JSLocalImplicitElementImpl(name, null, field, JSImplicitElement.Type.Property)
        }
        return null
    }

    private fun getSimplePropertyName(name: String, suffix: String): String {
        val withoutSuffix = if (name.endsWith(suffix))
            name.substring(0, name.length - suffix.length)
        else name.substring(0, name.length - StringUtil.pluralize(suffix).length)
        if (withoutSuffix.startsWith("has")) {
            return StringUtil.decapitalize(withoutSuffix.substring("has".length))
        }
        return withoutSuffix
    }

    private fun getStaticField(jsClass: JSClass, fieldName: String): JSField? {
        val field = jsClass.findFieldByName(fieldName) ?: return null
        return if (field.jsContext != JSContext.STATIC) null else field
    }
}

class StimulusCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val parent = parameters.position.parent
        if (parent is JSReferenceExpression && parent.qualifier is JSThisExpression) {
            val jsClass = getOwnerClass(parent) ?: return
            result.addAllElements(getLiteralValues(jsClass.findFieldByName(targetsField)).map {
                LookupElementBuilder.create(it + targetPropertySuffix)
            })
            result.addAllElements(getLiteralValues(jsClass.findFieldByName(classesField)).map {
                LookupElementBuilder.create(it + classPropertySuffix)
            })
            result.addAllElements(getLiteralValues(jsClass.findFieldByName(outletsField)).map {
                LookupElementBuilder.create(JSStringUtil.toCamelCase(it) + outletPropertySuffix)
            })
            result.addAllElements((jsClass.findFieldByName(valuesField)
                ?.initializer as? JSObjectLiteralExpression)
                ?.properties
                ?.mapNotNull { it.name }
                ?.map {
                    LookupElementBuilder.create(it + valuePropertySuffix)
                } ?: emptyList())

        }

    }
}
