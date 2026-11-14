package io.github.seektop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import io.github.seektop.foundation.designsys.component.StatusAction
import io.github.seektop.foundation.designsys.component.StatusView
import io.github.seektop.foundation.designsys.theme.StatusShapes
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_sign_in
import io.github.seektop.ui.resources.desktop_account_login_required
import io.github.seektop.ui.resources.status_sign_in_title
import org.jetbrains.compose.resources.stringResource

/** Do not compose private destinations: their ViewModels start network requests immediately. */
@Composable
internal fun DesktopSessionGate(destination: NavKey?, isSignedIn: Boolean, onSignIn: () -> Unit, content: @Composable () -> Unit) {
    if (isSignedIn || !destination.requiresDesktopSession()) {
        content()
        return
    }
    StatusView(
        icon = Icons.Default.Lock,
        shape = StatusShapes.SignIn,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        title = stringResource(Res.string.status_sign_in_title),
        description = stringResource(Res.string.desktop_account_login_required),
        primaryAction = StatusAction(stringResource(Res.string.action_sign_in), onSignIn),
    )
}

internal fun NavKey?.requiresDesktopSession(): Boolean = when (this) {
    MyTopicsKey, MyCommentsKey, BookmarksKey,
    AccountSettingsKey, AccountProfileFieldsKey, AccountSecurityKey, AccountContactKey,
    AccountPreferencesKey, AssetsKey, CreditKey, StardustKey,
    is FollowKey, is AccountBlockListKey, is MessageThreadKey, is PostComposerKey,
    -> true

    is UserSpaceKey -> isSelf || openCollections

    else -> false
}
