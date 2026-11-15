package io.github.seektop

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlin.test.Test
import kotlin.test.assertEquals

class AiSettingsNavigationTest {
    @Test fun `AI settings stack restores with the original post underneath`() {
        val original = listOf<NavKey>(PostListKey, PostDetailKey(17, "#29"), AiSettingsKey)
        val saved = encodeToSavedState(original, NavKeySavedStateConfiguration)
        val restored = decodeFromSavedState<List<NavKey>>(saved, NavKeySavedStateConfiguration).toMutableList()
        assertEquals(original, restored)
        restored.removeLast()
        assertEquals(PostDetailKey(17, "#29"), restored.last())
    }
}
