package io.github.seektop.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDownloadMeterTest {
    @Test fun `resumed bytes are not speed and a stalled transfer stops reporting old throughput`() {
        val meter = DesktopDownloadMeter()
        assertEquals(0, meter.sample(200_000_000, 0))
        assertEquals(1000, meter.sample(200_001_000, 1_000_000_000))
        assertEquals(0, meter.sample(200_001_000, 2_000_000_000))
        assertEquals(0, meter.sample(0, 3_000_000_000))
        assertEquals(2000, meter.sample(2000, 4_000_000_000))
    }
}
