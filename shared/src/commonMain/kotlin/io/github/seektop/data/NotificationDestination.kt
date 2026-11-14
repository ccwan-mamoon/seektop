package io.github.seektop.data

import kotlinx.serialization.Serializable

/** A toast owns its destination; never resolve it using whichever toast was sent last. */
@Serializable
data class NotificationDestination(
    val tab: NotificationTab = NotificationTab.INTERACTIONS,
    val postId: Long? = null,
    val floor: String? = null,
    val uid: Long? = null,
    val userName: String = "",
    val reminderId: String? = null,
    val account: Int? = null,
)
