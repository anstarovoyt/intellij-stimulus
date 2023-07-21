package stimulus

import junit.framework.TestCase
import org.junit.Test

@Suppress("JUnitMixedFramework")
class ControllerTest : StimulusTestBase() {

    @Test
    fun testSimple() {
        myFixture.addFileToProject("controllers/simple_controller.js", """
            import { Controller } from '@hotwired/stimulus'
            import { useHotkeys } from 'stimulus-use'

            export default class extends Controller {
              hello() {}
            }
        """.trimIndent())
        myFixture.configureByText("example.html", """
        <div class="ml-3 relative"
                 data-controller="sim<caret>ple">
                 hello
        </div>
        """.trimIndent())
        val ref = myFixture.getReferenceAtCaretPosition()
        val resolved = ref?.resolve()
        TestCase.assertNotNull(resolved)
        TestCase.assertTrue(resolved?.containingFile?.name == "simple_controller.js")
    }
}
