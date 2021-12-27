package stimulus.lang

import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.types.JSContext
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlAttributeDescriptorsProvider
import com.intellij.xml.impl.BasicXmlAttributeDescriptor
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor

class StimulusAttributeDescriptorsProvider : XmlAttributeDescriptorsProvider {

    override fun getAttributeDescriptors(context: XmlTag): Array<XmlAttributeDescriptor> {
        val controllers = getContextControllers(context)
        if (controllers.isEmpty()) return getControllerAttributeDescriptors()

        return getControllerAttributeDescriptors() +
                getActionsAttributeDescriptors() +
                getTargetDescriptors(controllers) +
                getValuesDescriptors(controllers)
    }

    override fun getAttributeDescriptor(attributeName: String, context: XmlTag): XmlAttributeDescriptor? {
        return getAttributeDescriptors(context).firstOrNull { it.name == attributeName }
    }

    private fun getTargetDescriptors(controllers: List<Pair<XmlTag, JSClass>>): Array<XmlAttributeDescriptor> {
        return controllers.mapNotNull { (_, controller) ->
            val targetsField = controller.findFieldByName("targets")
            return@mapNotNull if (targetsField != null && targetsField.jsContext == JSContext.STATIC)
                TargetFieldAttributeDescriptor(targetsField)
            else null
        }.toTypedArray()
    }

    private fun getValuesDescriptors(controllers: List<Pair<XmlTag, JSClass>>): Array<XmlAttributeDescriptor> {
        return controllers.mapNotNull { (_, controller) ->
            val valuesField = controller.findFieldByName("values")
            return@mapNotNull (valuesField?.initializer as? JSObjectLiteralExpression)?.properties?.mapNotNull {
                ValuesFieldAttributeDescriptor(it)
            }
        }.flatten().toTypedArray()
    }

    private fun getControllerAttributeDescriptors(): Array<XmlAttributeDescriptor> =
        arrayOf(AnyXmlAttributeDescriptor(dataControllerName))

    private fun getActionsAttributeDescriptors(): Array<XmlAttributeDescriptor> =
        arrayOf(AnyXmlAttributeDescriptor(dataActionName))
}

class TargetFieldAttributeDescriptor(private val field: JSField) : BaseStimulusAttributeDescriptor() {
    override fun isEnumerated(): Boolean = true
    override fun getDeclaration(): PsiElement = field
    override fun getEnumeratedValues(): Array<String>? =
        (field.initializer as? JSArrayLiteralExpression)
            ?.expressions?.mapNotNull { it as? JSLiteralExpression }
            ?.mapNotNull { it.stringValue }
            ?.toTypedArray()

    override fun getName(): String = "data-${toControllerName(field.containingFile)}-target"
}

class ValuesFieldAttributeDescriptor(private val property: JSProperty) : BaseStimulusAttributeDescriptor() {
    override fun isEnumerated(): Boolean = false
    override fun getEnumeratedValues(): Array<String>? = null
    override fun getDeclaration(): PsiElement = property
    override fun getName(): String = "data-${toControllerName(property.containingFile)}-${property.name}-value"
}

abstract class BaseStimulusAttributeDescriptor : BasicXmlAttributeDescriptor() {
    override fun isFixed(): Boolean = false
    override fun getDefaultValue(): String? = null
    override fun init(element: PsiElement?) {}
    override fun isRequired(): Boolean = false
    override fun hasIdType(): Boolean = false
    override fun hasIdRefType(): Boolean = false
}