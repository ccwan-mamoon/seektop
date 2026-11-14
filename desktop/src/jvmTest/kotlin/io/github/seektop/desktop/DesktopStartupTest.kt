package io.github.seektop.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStartupTest {
    @Test
    fun `blocking initialization leaves Swing free and transfers ownership on the UI thread`() = runBlocking {
        val closed = AtomicInteger()
        var published: AutoCloseable? = null
        withContext(Dispatchers.Main) {
            loadDesktopResource(open = {
                assertFalse(SwingUtilities.isEventDispatchThread())
                AutoCloseable { closed.incrementAndGet() }
            }) {
                assertTrue(SwingUtilities.isEventDispatchThread())
                published = it
            }
        }
        assertEquals(0, closed.get())
        checkNotNull(published).close()
        assertEquals(1, closed.get())
    }

    @Test
    fun `closing while initialization is blocked closes its late result without publishing it`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicInteger()
        var published = false
        val job = launch {
            loadDesktopResource(open = {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                AutoCloseable { closed.incrementAndGet() }
            }) { published = true }
        }
        try {
            withContext(Dispatchers.IO) { assertTrue(started.await(5, TimeUnit.SECONDS)) }
            job.cancel()
        } finally {
            release.countDown()
        }
        job.join()
        assertFalse(published)
        assertEquals(1, closed.get())
    }
}
