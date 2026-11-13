package io.github.nodyssey

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.nodyssey.ui.settings.AboutAppScreen
import io.github.nodyssey.ui.settings.AboutAppUiState
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.designsys.theme.PlazaTheme
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopUpdateConfirmationTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `restart upgrade requires confirmation and cancelling does not start a download`() = runComposeUiTest {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        var downloads = 0
        setContent {
            CompositionLocalProvider(LocalDesktopExperience provides true) {
                PlazaTheme(compactLayout = true) {
                    AboutAppScreen(
                        state = AboutAppUiState(appName = "SeekTop", versionName = "0.4.0", update = AppUpdateState(check = UpdateCheck.Available(AppRelease("0.4.1", "desktop-v0.4.1", "", "https://github.com/ccwan-mamoon/seektop", "SeekTop-0.4.1-windows-x64.zip", 100, "https://github.com/ccwan-mamoon/seektop")))),
                        onBack = {}, onCheckUpdates = {}, onDownloadUpdate = { downloads++ }, onCancelDownload = {}, onInstallUpdate = {}, onGrantInstallPermission = {}, onOpenChangelog = {}, onOpenHelp = {}, onOpenLicenses = {}, onOpenUri = {}, onExportCrashReport = {}, onClearCrashReport = {},
                    )
                }
            }
        }
        onNodeWithText("一键升级").performClick()
        assertEquals(0, downloads)
        onNodeWithText("取消").performClick()
        assertEquals(0, downloads)
        onNodeWithText("一键升级").performClick()
        // The dialog confirm is the second matching action; the first belongs to the page beneath it.
        onAllNodesWithText("一键升级")[1].performClick()
        assertEquals(1, downloads)
    }
}
