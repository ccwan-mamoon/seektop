package io.github.seektop

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

/** Navigation 3 owns disposal of the old entry's state and ViewModels when its content key changes. */
internal fun desktopSessionEntry(key: NavKey, entry: NavEntry<NavKey>, generation: Int?): NavEntry<NavKey> {
    if (generation == null || (key != ProfileKey && !key.requiresDesktopSession())) return entry
    return NavEntry(key, contentKey = "${entry.contentKey}:session-$generation", metadata = entry.metadata) { entry.Content() }
}
