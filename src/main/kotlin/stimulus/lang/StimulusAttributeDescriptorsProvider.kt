package stimulus.lang

import com.intellij.lang.javascript.psi.JSArrayLiteralExpression
import com.intellij.lang.javascript.psi.JSField
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.types.JSContext
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptorsProvider
import com.intellij.xml.impl.BasicXmlAttributeDescriptor

class StimulusAttributeDescriptorsProvider : XmlAttributeDescriptorsProvider {

    override fun getAttributeDescriptors(context: XmlTag): Array<XmlAttributeDescriptor> {
        val controllers = getContextControllers(context)
        if (controllers.isEmpty()) return emptyArray()

        return getTargetDescriptors(controllers)
    }

    override fun getAttributeDescriptor(attributeName: String, context: XmlTag): XmlAttributeDescriptor? {
        return getAttributeDescriptors(context).firstOrNull { it.name == attributeName }
    }

    private fun getTargetDescriptors(controllers: List<Pair<XmlTag, JSClass>>): Array<XmlAttributeDescriptor> {
        return controllers.mapNotNull { (_, controller) ->
            val targetsField = controller.findFieldByName("targets")
            return@mapNotNull if (targetsField != null && targetsField.jsContext == JSContext.STATIC)
                SimpleFieldAttributeDescriptor(targetsField)
            else null
        }.toTypedArray()
    }
}

class SimpleFieldAttributeDescriptor(private val field: JSField) :
    BasicXmlAttributeDescriptor() {

    override fun isFixed(): Boolean = false
    override fun getDefaultValue(): String? = null
    override fun isEnumerated(): Boolean = true

    override fun getEnumeratedValues(): Array<String>? {
        return (field.initializer as? JSArrayLiteralExpression)
            ?.expressions?.mapNotNull { it as? JSLiteralExpression }
            ?.mapNotNull { it.stringValue }
            ?.toTypedArray()
    }

    override fun getDeclaration(): PsiElement = field
    override fun getName(): String = "data-${toControllerName(field.containingFile)}-target"
    override fun init(element: PsiElement?) {}
    override fun isRequired(): Boolean = false
    override fun hasIdType(): Boolean = false
    override fun hasIdRefType(): Boolean = false

}