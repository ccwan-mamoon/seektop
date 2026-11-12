package io.github.nodyssey.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopOfflineFilesTest {
    @Test
    fun `sweeping keeps a picture still referenced by another thread`() {
        val directory = Files.createTempDirectory("nodyssey-offline").toFile()
        try {
            val store = DesktopOfflineFiles(directory)
            val kept = "https://example.com/shared.png"
            val removed = "https://example.com/removed.png"
            store.write(store.nameOf(kept), byteArrayOf(1, 2))
            store.write(store.nameOf(removed), byteArrayOf(3))
            store.sweep(setOf(store.nameOf(kept)))
            assertTrue(store.hasStored(kept))
            assertFalse(store.hasStored(removed))
            assertContentEquals(byteArrayOf(1, 2), directory.resolve(store.nameOf(kept)).readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `stored names cannot escape the offline directory`() {
        val directory = Files.createTempDirectory("nodyssey-offline").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { DesktopOfflineFiles(directory).write("../outside", byteArrayOf(1)) }
        } finally {
            directory.deleteRecursively()
        }
    }
}
