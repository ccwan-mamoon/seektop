package io.github.seektop.ui.composer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class VpsCalendarTest {
    @Test fun `calendar uses UTC date boundaries and preserves typed minute precision on date change`() {
        assertEquals(Instant.parse("2028-02-29T00:00:00Z").toEpochMilliseconds(), vpsCalendarMillis("2028-02-29 23:45"))
        val march = Instant.parse("2028-03-01T00:00:00Z").toEpochMilliseconds()
        assertEquals("2028-03-01 23:45", vpsCalendarDate(march, "2028-02-29 23:45", true))
        assertEquals("2028-03-01", vpsCalendarDate(march, "2028-02-29 23:45", false))
        assertEquals("2028-03-01 00:00", vpsCalendarDate(march, "", true))
        assertEquals(null, vpsCalendarMillis("2028-02-30"))
        assertEquals(null, vpsCalendarMillis("2228-02-29"))
    }
}
