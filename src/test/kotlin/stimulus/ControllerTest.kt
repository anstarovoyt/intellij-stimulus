package stimulus

import org.junit.Test

@Suppress("JUnitMixedFramework")
class ControllerTest : StimulusTestBase() {

    private fun addSimpleController(name: String) {
        myFixture.addFileToProject(
            "controllers/$name", """
                import { Controller } from '@hotwired/stimulus'
                import { useHotkeys } from 'stimulus-use'
                export default class extends Controller {
                  hello() {}
                }
            """.trimIndent()
        )
    }

    private fun doSimpleTestForControllerName(name: String, refName: String) {
        addSimpleController(name)
        myFixture.configureByText("example.html", "<div data-controller='$refName<caret>'>hello</div>")
        val ref = myFixture.getReferenceAtCaretPosition()
        val resolved = ref?.resolve()
        assertNotNull(resolved)
        assertTrue(resolved?.containingFile?.name == name)
    }

    @Test
    fun testSimple() {
        doSimpleTestForControllerName("simple_controller.js", "simple")
    }

    @Test
    fun testSimpleDash() {
        doSimpleTestForControllerName("simple-controller.js", "simple")
    }

    @Test
    fun testSimpleUnderscore() {
        doSimpleTestForControllerName("simple_underscore_controller.js", "simple-underscore")
    }

    @Test
    fun testSimpleDashTwo() {
        doSimpleTestForControllerName("simple-dash-controller.js", "simple-dash")
    }

    fun testCompletion() {
        addSimpleController("simple_controller.js")
        addSimpleController("simple2_controller.js")
        addSimpleController("simple_underscore_controller.js")
        addSimpleController("simple-dash-controller.js")
        addSimpleController("simpleDash-controller.js")
        myFixture.configureByText("example.html", "<div data-controller='<caret>'>hello</div>")
        val elements = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(elements, "simple", "simple2", "simpleDash", "simple-underscore", "simple-dash")
    }
}
