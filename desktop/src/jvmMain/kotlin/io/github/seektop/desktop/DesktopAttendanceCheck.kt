package io.github.seektop.desktop

import io.github.seektop.data.AttendanceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class DesktopAttendanceState { IDLE, CHECKING, ALREADY_DONE, SIGNED, NEED_LOGIN, CHOOSE_MODE, FAILED }

/** One attempt per account/day; failed checks never fall through to a blind write. */
internal class DesktopAttendanceCheck {
    private val mutex = Mutex()
    private val attempted = mutableSetOf<Pair<Int, String>>()
    private val mutableState = MutableStateFlow(DesktopAttendanceState.IDLE)
    val state = mutableState.asStateFlow()

    suspend fun check(account: Int?, day: String, current: () -> Boolean, hasSigned: suspend () -> Boolean, sign: suspend (AttendanceMode) -> Unit, mode: AttendanceMode? = null, retry: Boolean = false) = mutex.withLock {
        if (account == null) {
            mutableState.value = DesktopAttendanceState.NEED_LOGIN
            return@withLock
        }
        val key = account to day
        if (!retry && key in attempted) return@withLock
        attempted.add(key)
        mutableState.value = DesktopAttendanceState.CHECKING
        try {
            val done = hasSigned()
            if (!current()) {
                mutableState.value = DesktopAttendanceState.NEED_LOGIN
                return@withLock
            }
            if (done) {
                mutableState.value = DesktopAttendanceState.ALREADY_DONE
            } else if (mode == null) {
                mutableState.value = DesktopAttendanceState.CHOOSE_MODE
            } else {
                sign(mode)
                mutableState.value = if (current()) DesktopAttendanceState.SIGNED else DesktopAttendanceState.NEED_LOGIN
            }
        } catch (cancelled: CancellationException) {
            attempted.remove(key)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = DesktopAttendanceState.FAILED
        }
    }
}
