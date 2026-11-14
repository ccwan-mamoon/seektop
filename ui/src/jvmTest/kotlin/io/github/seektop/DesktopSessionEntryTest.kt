package io.github.seektop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopSessionEntryTest {
    @Test
    fun `session changes dispose personal entry state while public reading state survives`() = runComposeUiTest {
        var generation by mutableStateOf(0)
        var personalStarts = 0
        var publicStarts = 0
        var personalStops = 0
        val removed = mutableListOf<Any>()
        setContent {
            val originals = remember {
                listOf(
                    NavEntry<NavKey>(MyTopicsKey) {
                        DisposableEffect(Unit) {
                            personalStarts++
                            onDispose { personalStops++ }
                        }
                    },
                    NavEntry<NavKey>(PostListKey) {
                        DisposableEffect(Unit) {
                            publicStarts++
                            onDispose {}
                        }
                    },
                )
            }
            val decorator = remember { NavEntryDecorator<NavKey>(onPop = { removed += it }) { it.Content() } }
            val entries = remember(generation) {
                listOf(desktopSessionEntry(MyTopicsKey, originals[0], generation), desktopSessionEntry(PostListKey, originals[1], generation))
            }
            val decorated = rememberDecoratedNavEntries(entries = entries, entryDecorators = listOf(decorator))
            // NavDisplay keys its displayed entries by contentKey; reproduce that scene contract.
            Column { decorated.forEach { entry -> key(entry.contentKey) { entry.Content() } } }
        }
        waitForIdle()
        assertEquals(1, personalStarts)
        assertEquals(1, publicStarts)
        runOnIdle { generation++ }
        waitForIdle()
        assertEquals(2, personalStarts)
        assertEquals(1, personalStops)
        assertEquals(1, publicStarts)
        assertTrue(removed.any { it.toString().endsWith("session-0") })
    }
}
