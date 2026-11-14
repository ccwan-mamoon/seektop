package io.github.seektop.render

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.seektop.data.dns.DnsResolution
import io.github.seektop.data.proxy.ProxyType
import io.github.seektop.foundation.designsys.theme.SeekTopTheme
import io.github.seektop.ui.settings.DohSettingsScreen
import io.github.seektop.ui.settings.DohSettingsUiState
import io.github.seektop.ui.settings.ProxySettingsScreen
import io.github.seektop.ui.settings.ProxySettingsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 代理 (6e) and 加密 DNS (6f), each tall enough to reach its notes, in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1300dp")
class NetworkSettingsRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    private fun proxy(darkTheme: Boolean) {
        composeRule.setContent {
            SeekTopTheme(darkTheme = darkTheme) {
                ProxySettingsScreen(
                    state =
                    ProxySettingsUiState(
                        enabled = true,
                        type = ProxyType.SOCKS,
                        hostInput = "127.0.0.1",
                        portInput = "7890",
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onEnabledChange = {},
                    onTypeChange = {},
                    onForumOnlyChange = {},
                    onHostChange = {},
                    onPortChange = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onSave = {},
                    onTest = {},
                )
            }
        }
    }

    private fun doh(darkTheme: Boolean) {
        composeRule.setContent {
            SeekTopTheme(darkTheme = darkTheme) {
                DohSettingsScreen(
                    state =
                    DohSettingsUiState(
                        enabled = true,
                        resolution =
                        DnsResolution(
                            host = "www.nodeseek.com",
                            addresses = listOf("104.21.32.1", "172.67.140.1"),
                            elapsedMillis = 86,
                        ),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onEnabledChange = {},
                    onToggleServer = {},
                    onMoveServer = { _, _ -> },
                    onOpenServer = {},
                    onAddServer = {},
                    onIncludeIPv6Change = {},
                    onFallbackChange = {},
                    onSave = {},
                    onTest = {},
                )
            }
        }
    }

    @Test
    fun `proxy in light`() {
        proxy(darkTheme = false)
        composeRule.onRoot().captureRender("proxy-light")
    }

    @Test
    fun `proxy in dark`() {
        proxy(darkTheme = true)
        composeRule.onRoot().captureRender("proxy-dark")
    }

    @Test
    fun `encrypted DNS in light`() {
        doh(darkTheme = false)
        composeRule.onRoot().captureRender("doh-light")
    }

    @Test
    fun `encrypted DNS in dark`() {
        doh(darkTheme = true)
        composeRule.onRoot().captureRender("doh-dark")
    }
}
