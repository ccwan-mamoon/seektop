package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import io.github.nodyssey.LocalDesktopExperience

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopAwareRefreshBox(isRefreshing: Boolean, onRefresh: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    if (LocalDesktopExperience.current) {
        Box(content = content)
    } else {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, content = content)
    }
}
