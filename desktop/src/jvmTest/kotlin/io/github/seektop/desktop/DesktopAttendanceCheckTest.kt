package io.github.seektop.desktop

import io.github.seektop.data.AttendanceMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopAttendanceCheckTest {
    @Test fun `unconfigured attendance asks before writing and uses the selected mode only once`() = runBlocking {
        val check = DesktopAttendanceCheck()
        val writes = mutableListOf<AttendanceMode>()
        check.check(7, "today", { true }, { false }, { writes.add(it) })
        assertEquals(DesktopAttendanceState.CHOOSE_MODE, check.state.value)
        assertEquals(emptyList(), writes)
        check.check(7, "today", { true }, { false }, { writes.add(it) }, AttendanceMode.RANDOM, retry = true)
        assertEquals(listOf(AttendanceMode.RANDOM), writes)
        check.check(7, "today", { true }, { false }, { writes.add(it) }, AttendanceMode.FIXED_FIVE)
        assertEquals(listOf(AttendanceMode.RANDOM), writes)
    }

    @Test fun `already signed failed receipt and account switch never submit attendance`() = runBlocking {
        var writes = 0
        val check = DesktopAttendanceCheck()
        check.check(1, "today", { true }, { true }, { writes++ }, AttendanceMode.RANDOM)
        assertEquals(DesktopAttendanceState.ALREADY_DONE, check.state.value)
        check.check(2, "today", { true }, { error("network") }, { writes++ }, AttendanceMode.RANDOM)
        assertEquals(DesktopAttendanceState.FAILED, check.state.value)
        check.check(3, "today", { false }, { false }, { writes++ }, AttendanceMode.RANDOM)
        assertEquals(DesktopAttendanceState.NEED_LOGIN, check.state.value)
        assertEquals(0, writes)
    }
}
