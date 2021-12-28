package stimulus.lang

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext

class StimulusReferenceProvider : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            XmlPatterns.xmlAttributeValue().withLocalName(dataControllerName),
            ControllerReferenceProvider()
        )

        registrar.registerReferenceProvider(
            XmlPatterns.xmlAttributeValue().withLocalName(dataActionName),
            ActionReferenceProvider()
        )
    }
}

private class ActionReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        return multiValueAttributeReferenceMapper(element) { part, range ->
            val actionSeparatorIndex = part.indexOf("->")
            val methodStart = part.indexOf("#")
            val controllerStartIndex = if (actionSeparatorIndex > 0) actionSeparatorIndex + 2 else 0
            val controllerName =
                part.substring(controllerStartIndex, if (methodStart > 0) methodStart else part.length)
            val methodEnd = if (methodStart > 0) part.indexOf(":", methodStart) else -1

            val controllerNameStartAbsolute = range.startOffset + controllerStartIndex
            val controllerNameEndAbsolute = controllerNameStartAbsolute + controllerName.length
            val parentRef = StimulusControllerReference(
                controllerName,
                element,
                TextRange(controllerNameStartAbsolute, controllerNameEndAbsolute)
            )
            if (methodStart <= 0) return@multiValueAttributeReferenceMapper listOf(parentRef)

            val methodName = part.substring(methodStart + 1, if (methodEnd > 0) methodEnd else part.length)
            val methodNameStartAbsolute = range.startOffset + methodStart + 1
            val methodNameEndAbsolute = methodNameStartAbsolute + methodName.length
            val methodRef = StimulusMethodReference(
                methodName,
                parentRef,
                element,
                TextRange(methodNameStartAbsolute, methodNameEndAbsolute)
            )
            return@multiValueAttributeReferenceMapper listOf(parentRef, methodRef)
        }
    }

}

private class ControllerReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        return multiValueAttributeReferenceMapper(element) { part, range ->
            listOf(StimulusControllerReference(part, element, range))
        }
    }
}

fun multiValueAttributeReferenceMapper(
    element: PsiElement,
    func: (part: String, range: TextRange) -> List<PsiReference>
): Array<PsiReference> {
    if (element !is XmlAttributeValue) return PsiReference.EMPTY_ARRAY
    var currentOffset = 1

    return element
        .value
        .split(" ")
        .map { part ->
            return@map when {
                part.isEmpty() -> emptyList()
                else -> func(part, TextRange(currentOffset, currentOffset + part.length))
            }.also { currentOffset += part.length + 1 }
        }
        .flatten()
        .toTypedArray()
} 