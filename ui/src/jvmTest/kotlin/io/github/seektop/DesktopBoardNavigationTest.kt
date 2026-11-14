package io.github.seektop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class DesktopBoardNavigationTest {
    @Test fun `board clicks survive settings and refresh page one even on repeated selection`() = runComposeUiTest {
        val navigation = DesktopBoardNavigation()
        var mounted by mutableStateOf(false)
        var slug by mutableStateOf<String?>(null)
        var page by mutableStateOf(6)
        val refreshed = mutableListOf<Pair<String?, Int>>()
        setContent {
            CompositionLocalProvider(LocalDesktopBoardNavigation provides navigation) {
                if (mounted) {
                    ConsumeDesktopBoardSelection(slug, page, {
                        slug = it
                        page = 1
                    }) { refreshed.add(slug to page) }
                }
            }
        }
        runOnIdle { navigation.select("tech") }
        waitForIdle()
        assertEquals(emptyList<Pair<String?, Int>>(), refreshed)
        runOnIdle { mounted = true }
        waitForIdle()
        assertEquals(listOf<Pair<String?, Int>>("tech" to 1), refreshed)
        assertNull(navigation.pendingSelection)
        runOnIdle { navigation.select("tech") }
        waitForIdle()
        assertEquals(listOf<Pair<String?, Int>>("tech" to 1, "tech" to 1), refreshed)
        runOnIdle { mounted = false }
        runOnIdle {
            navigation.select("daily")
            navigation.select(null)
        }
        runOnIdle { mounted = true }
        waitForIdle()
        assertEquals(listOf("tech" to 1, "tech" to 1, null to 1), refreshed)
    }
}
