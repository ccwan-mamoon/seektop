package io.github.nodyssey.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopRuntimeTest {
    @Test
    fun `view models have an installed Swing main dispatcher`() = runBlocking {
        withContext(Dispatchers.Main) {
            assertTrue(SwingUtilities.isEventDispatchThread())
        }
    }
}
