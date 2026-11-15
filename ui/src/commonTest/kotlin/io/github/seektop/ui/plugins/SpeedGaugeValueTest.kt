package io.github.seektop.ui.plugins

import io.github.seektop.plugins.ToolEvent
import io.github.seektop.plugins.ToolMetric
import io.github.seektop.plugins.ToolText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeedGaugeValueTest {
    private fun metric(label: String, value: String) = ToolMetric(ToolText(label, label), value)

    @Test fun `completed result retains measured speed when live metric disappears`() {
        val final = ToolEvent(ToolText("done", "done"), listOf(metric("Mean HTTP latency", "8 ms"), metric("Download test", "120 Mbps"), metric("Upload test", "35 Mbps")), done = true)
        assertEquals(35f, speedGaugeValue(final))
        assertEquals(42f, speedGaugeValue(final.copy(metrics = final.metrics + metric("Live rate", "42 Mbps"), done = false)))
        assertNull(speedGaugeValue(final.copy(metrics = listOf(metric("Mean HTTP latency", "8 ms")))))
    }
}
