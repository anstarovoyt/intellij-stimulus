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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlTag

const val dataControllerName = "data-controller"
const val dataActionName = "data-action"
private const val CONTROLLERS = "controllers"

fun resolveController(name: String, context: PsiElement): PsiElement? {
    val filesByNames = findControllersByName(context, name, "js") + findControllersByName(context, name, "ts")
    if (filesByNames.isEmpty()) return null
    return filesByNames.firstNotNullOfOrNull(ES6PsiUtil::findDefaultExport)
}

fun toControllerName(file: PsiFile): String {
    val name = trimControllerPostfix(file).replace('_', '-')
    val virtualFile = file.virtualFile ?: return name
    val startParent = virtualFile.parent ?: return name

    val controllersDirectory = CONTROLLERS
    if (startParent.name == controllersDirectory) return name

    var parent: VirtualFile? = startParent
    while (parent != null && parent.name != controllersDirectory) {
        parent = parent.parent
    }

    if (parent != null) {
        VfsUtil.getRelativePath(startParent, parent)?.let {
            if (it.isNotEmpty()) {
                return it.replace('_', '-').replace(".", "--") + "--" + name
            }
        }
    }

    return name
}

private fun trimControllerPostfix(file: PsiFile): String {
    val name = FileUtil.getNameWithoutExtension(file.name)
    val trimmedSlash = StringUtil.trimEnd(name, "_controller")
    if (name != trimmedSlash) return trimmedSlash
    return StringUtil.trimEnd(name, "-controller")
}


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
): List<JSFile> {
    val filesWithUnderscore = FilenameIndex.getVirtualFilesByName(
        "${trimPrefix(name)}_controller.".replace('-', '_') + extension,
        GlobalSearchScope.projectScope(context.project)
    )

    val filesWithDash = FilenameIndex.getVirtualFilesByName(
        "${trimPrefix(name)}-controller." + extension,
        GlobalSearchScope.projectScope(context.project)
    )

    val manager = context.manager
    val files = filesWithUnderscore + filesWithDash
    val mappedFiles = files.mapNotNull { manager.findFile(it) as? JSFile }
    return mappedFiles.filter { toControllerName(it) == name }.ifEmpty { mappedFiles }
}

private fun trimPrefix(name: String): String {
    val lastIndex = name.lastIndexOf("--")
    if (lastIndex <= 0) return name

    return name.substring(lastIndex + 2)
}

private fun getAllControllers(context: PsiElement): List<JSFile> {
    val scope = object : DelegatingGlobalSearchScope(GlobalSearchScope.projectScope(context.project)) {
        override fun contains(file: VirtualFile): Boolean {
            val nameSequence = file.nameSequence
            return super.contains(file) &&
                    (nameSequence.endsWith("_controller.js") || nameSequence.endsWith("-controller.js")) ||
                    (nameSequence.endsWith("_controller.ts") || nameSequence.endsWith("-controller.ts"))
        }
    }
    return getAllControllers(JavaScriptFileType.INSTANCE, context.manager, scope) +
            getAllControllers(TypeScriptFileType.INSTANCE, context.manager, scope)
}

private fun getAllControllers(fileType: FileType, manager: PsiManager, scope: GlobalSearchScope) =
    FileTypeIndex.getFiles(fileType, scope)
        .mapNotNull { manager.findFile(it) as? JSFile }


class StimulusControllerReference(private val name: String, psiElement: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(psiElement, range, true) {

    override fun resolve(): PsiElement? = resolveController(name, element)

    override fun getVariants(): Array<LookupElement> =
        getAllControllers(element)
            .map { toControllerName(it) }
            .map { LookupElementBuilder.create(it) }
            .toTypedArray()

}

class StimulusMethodReference(
    private val name: String,
    private val parentRef: PsiReference,
    psiElement: PsiElement,
    range: TextRange
) :
    PsiReferenceBase<PsiElement>(psiElement, range, true) {
    override fun resolve(): PsiElement? {
        return resolveParent()?.findFunctionByName(name)
    }

    private fun resolveParent(): JSClass? {
        val parent = parentRef.resolve() ?: return null
        return ((parent as? ES6ExportDefaultAssignment)?.namedElement as? JSClass)
    }

    override fun getVariants(): Array<Any> {
        val resolveParent = resolveParent() ?: return emptyArray()
        return resolveParent.functions.map { LookupElementBuilder.create(it) }.toTypedArray()
    }
}
