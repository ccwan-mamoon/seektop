package io.github.nodyssey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

internal class DesktopRefreshTargets {
    private val targets = linkedMapOf<Any, Pair<Int, () -> Unit>>()
    fun register(owner: Any, priority: Int, refresh: () -> Unit) {
        targets[owner] = priority to refresh
    }
    fun unregister(owner: Any) {
        targets.remove(owner)
    }
    fun refresh(): Boolean {
        val target = targets.values.maxByOrNull { it.first } ?: return false
        target.second()
        return true
    }
}
internal val LocalDesktopRefreshTargets = staticCompositionLocalOf<DesktopRefreshTargets?> { null }

@Composable
internal fun DesktopRefreshTarget(priority: Int, onRefresh: () -> Unit) {
    val targets = LocalDesktopRefreshTargets.current ?: return
    val owner = remember { Any() }
    val currentRefresh by rememberUpdatedState(onRefresh)
    DisposableEffect(targets, priority) {
        targets.register(owner, priority) { currentRefresh() }
        onDispose { targets.unregister(owner) }
    }
}
