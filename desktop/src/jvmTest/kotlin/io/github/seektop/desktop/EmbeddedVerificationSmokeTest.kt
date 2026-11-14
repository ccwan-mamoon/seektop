package io.github.seektop.desktop

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.awt.BorderLayout
import java.awt.Panel
import java.io.File
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class EmbeddedVerificationSmokeTest {
    @Test
    fun `verification renders inside an application host and returns through the shared bridge`() {
        assumeTrue(System.getenv("SEEKTOP_EMBED_SMOKE") == "1")
        val runtime = File("resources/windows/browser-runtime").absoluteFile
        val data = java.nio.file.Files.createTempDirectory(File("build").toPath().toAbsolutePath(), "embedded-verification-").toFile()
        lateinit var frame: JFrame
        lateinit var host: Panel
        SwingUtilities.invokeAndWait {
            host = Panel(BorderLayout())
            frame = JFrame("SeekTop verification integration fixture").apply {
                add(host)
                setSize(480, 360)
                isVisible = true
            }
        }
        val browser = DesktopBrowser(File(runtime, "SeekTopBrowser.exe"), File(runtime, "resources/app"), data)
        try {
            val result = runBlocking {
                kotlinx.coroutines.withTimeout(45_000) { browser.verify(false, host) }
            }
            assertTrue(!result.isNullOrBlank(), "The live site did not return a verification token")
        } finally {
            browser.close()
            SwingUtilities.invokeAndWait { frame.dispose() }
            val expected = File("build").toPath().toAbsolutePath().normalize()
            check(data.toPath().normalize().startsWith(expected))
            data.deleteRecursively()
        }
    }
}
