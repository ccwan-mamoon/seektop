package io.github.seektop.ui.login

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelStore
import io.github.seektop.core.NodeSeekSite
import io.github.seektop.data.session.SessionRepository
import io.github.seektop.data.session.SignInCredentials
import io.github.seektop.data.session.SignInOutcome
import io.github.seektop.data.session.SignInRepository
import io.github.seektop.data.session.TwoFactorChallenge
import io.github.seektop.foundation.core.AppClock
import io.github.seektop.foundation.core.net.SessionCookieStore
import io.github.seektop.foundation.core.net.SessionCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopNativeSignInTest {
    @Test
    fun `verification gates credentials and two factor success publishes the shared session and clears secrets`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        var cookieHeader: String? = null
        val cookies = object : SessionCookieStore {
            override fun cookieHeader(url: String) = cookieHeader
            override fun setCookie(url: String, cookie: String) {
                cookieHeader = cookie
            }
            override suspend fun removeAll() {
                cookieHeader = null
            }
            override fun flush() {}
        }
        var requests = 0
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        val repository = object : SignInRepository {
            override suspend fun signIn(credentials: SignInCredentials): SignInOutcome {
                requests++
                assertEquals("verification-fixture", credentials.verificationToken)
                return SignInOutcome.TwoFactorRequired(TwoFactorChallenge(credentials.account, "otp-fixture"))
            }
            override suspend fun verifyTwoFactor(challenge: TwoFactorChallenge, code: String): SignInOutcome {
                assertEquals("otp-fixture", challenge.otpSession)
                assertEquals("123456", code)
                cookieHeader = NodeSeekSite.CONFIG.sessionCookieNames.first() + "=session-fixture"
                return SignInOutcome.Signed
            }
        }
        val vm = SignInViewModel(repository, session, AppClock { 0L })
        store.put("sign-in", vm)
        try {
            vm.accountState.edit { append("fixture-user") }
            vm.passwordState.edit { append("fixture-password") }
            runCurrent()
            assertFalse(vm.uiState.value.canSubmitCredentials)
            vm.submitCredentials()
            runCurrent()
            assertEquals(0, requests)
            vm.onVerified("verification-fixture")
            vm.submitCredentials()
            vm.submitCredentials()
            runCurrent()
            assertEquals(1, requests)
            assertEquals(SignInStep.TwoFactor, vm.uiState.value.step)
            assertFalse(session.state.value.isSignedIn)
            vm.codeState.edit { append("123456") }
            Snapshot.sendApplyNotifications()
            runCurrent()
            vm.submitTwoFactor()
            runCurrent()
            assertTrue(vm.uiState.value.signedIn)
            assertTrue(session.state.value.isSignedIn)
            assertEquals("", vm.passwordState.text.toString())
            assertEquals("", vm.codeState.text.toString())
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
