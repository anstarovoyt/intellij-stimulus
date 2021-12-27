package stimulus.lang

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlTag

const val dataControllerName = "data-controller"
const val dataActionName = "data-action"

fun resolveController(name: String, context: PsiElement): PsiElement? {
    val filesByNames = findControllersByName(context, name, "js") + findControllersByName(context, name, "ts")
    if (filesByNames.isEmpty()) return null

    return filesByNames.filterIsInstance<JSFile>().firstNotNullOfOrNull(ES6PsiUtil::findDefaultExport)
}

fun toControllerName(it: PsiFile) =
    StringUtil.trimEnd(FileUtil.getNameWithoutExtension(it.name), "_controller").replace('_', '-')


fun getContextControllers(contextTag: XmlTag): List<Pair<XmlTag, JSClass>> {
    return generateSequence(contextTag) { it.parentTag }.mapNotNull { tag ->
        val attribute = tag.getAttribute(dataControllerName) ?: return@mapNotNull null
        return@mapNotNull attribute.value
            ?.let { value ->
                value.splitToSequence(" ").filter(String::isNotEmpty).mapNotNull { resolveController(it, tag) }
            }
            ?.mapNotNull { if (it is ES6ExportDefaultAssignment) it.namedElement else null }
            ?.filterIsInstance<JSClass>()
            ?.map { tag to it }
            ?: return@mapNotNull null
    }.flatten().toList()
}

private fun findControllersByName(
    context: PsiElement,
    name: String,
    extension: String
): Array<PsiFile> = FilenameIndex.getFilesByName(
    context.project,
    "${name}_controller.".replace('-', '_') + extension,
    GlobalSearchScope.projectScope(context.project)
)

private fun getAllControllers(context: PsiElement): List<JSFile> {
    val scope = object : DelegatingGlobalSearchScope(GlobalSearchScope.projectScope(context.project)) {
        override fun contains(file: VirtualFile): Boolean =
            super.contains(file) && file.nameSequence.endsWith("_controller.js")
    }
    return getAllControllers(JavaScriptFileType.INSTANCE, context.manager, scope) +
            getAllControllers(TypeScriptFileType.INSTANCE, context.manager, scope)
}

private fun getAllControllers(fileType: FileType, manager: PsiManager, scope: GlobalSearchScope) =
    FileTypeIndex.getFiles(fileType, scope)
        .mapNotNull { manager.findFile(it) as JSFile }


class StimulusControllerReference(private val name: String, psiElement: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(psiElement, range, true) {

    override fun resolve(): PsiElement? = resolveController(name, element)

    override fun getVariants(): Array<LookupElement> =
        getAllControllers(element)
            .map { toControllerName(it) }
            .map { LookupElementBuilder.create(it) }
            .toTypedArray()

}