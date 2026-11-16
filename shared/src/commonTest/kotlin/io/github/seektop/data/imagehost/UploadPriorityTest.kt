package io.github.seektop.data.imagehost

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UploadPriorityTest {
    private val first = ImageHostProvider.SMMS
    private val second = ImageHostProvider.IMGBB

    @Test
    fun `multiple hosts without a saved choice require confirmation`() {
        assertNull(preferredUploadHost(listOf(first, second), emptyList()))
    }

    @Test
    fun `priority wins over provider enumeration and skips disconnected hosts`() {
        assertEquals(second, preferredUploadHost(listOf(first, second), listOf(ImageHostProvider.NODE_IMAGE, second, first)))
    }

    @Test
    fun `only configured host is used even with stale preference`() {
        assertEquals(first, preferredUploadHost(listOf(first), listOf(second)))
        assertNull(preferredUploadHost(emptyList(), listOf(second)))
        assertNull(preferredUploadHost(listOf(first, second), listOf(ImageHostProvider.NODE_IMAGE)))
    }
}
