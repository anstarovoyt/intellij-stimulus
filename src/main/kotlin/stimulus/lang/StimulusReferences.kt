package stimulus.lang

import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

fun resolveController(name: String, context: PsiElement): PsiElement? {
    val filesByNames =
        FilenameIndex.getFilesByName(
            context.project,
            "${name}_controller.js",
            GlobalSearchScope.projectScope(context.project)
        )
    if (filesByNames.isEmpty()) return null

    return filesByNames.filterIsInstance<JSFile>()
        .mapNotNull(ES6PsiUtil::findDefaultExport)
        .mapNotNull { if (it is ES6ExportDefaultAssignment) it.namedElement else it }
        .filterIsInstance<JSClass>()
        .firstOrNull()
}

class StimulusControllerReference(private val name: String, psiElement: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(psiElement, range) {

    override fun resolve(): PsiElement? = resolveController(name, element)
}