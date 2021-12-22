package stimulus.lang

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext
import com.intellij.xml.util.XmlUtil

class StimulusReferenceProvider : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        XmlUtil.registerXmlAttributeValueReferenceProvider(
            registrar, arrayOf("data-controller"), null, ControllerReferenceProvider()
        )
    }
}

private class ControllerReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is XmlAttributeValue) return PsiReference.EMPTY_ARRAY
        var currentOffset = 1 //quote

        return element
            .value
            .split(" ")
            .mapNotNull { part ->
                return@mapNotNull when {
                    part.isEmpty() -> null
                    else -> StimulusControllerReference(part, element, TextRange(currentOffset, currentOffset + part.length))
                }.also { currentOffset += part.length + 1 }
            }
            .toTypedArray()
    }
}