package stimulus.lang.js

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.javascript.psi.JSField
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.types.JSContext
import com.intellij.psi.PsiElement

class StimulusImplicitUsagesProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        if (element is ES6ExportDefaultAssignment) {
            val namedElement = element.namedElement
            return namedElement is JSClass && namedElement.extendsList != null 
        }
        
        if (element !is JSField || element.jsContext != JSContext.STATIC) return false
        val name = element.name
        return "targets" == name || "values" == name || "classes" == name
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false
    override fun isImplicitWrite(element: PsiElement): Boolean = false
}