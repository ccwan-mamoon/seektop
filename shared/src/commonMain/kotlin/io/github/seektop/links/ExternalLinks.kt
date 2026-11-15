package io.github.seektop.links

import kotlinx.serialization.Serializable

@Serializable
data class LinkRoutingStatus(val enabled: Boolean = false, val httpActive: Boolean = false, val httpsActive: Boolean = false, val prepared: Boolean = false, val httpName: String = "", val httpsName: String = "")

interface ExternalLinkRouting {
    suspend fun status(): LinkRoutingStatus
    suspend fun prepare(): LinkRoutingStatus
    suspend fun disable(): LinkRoutingStatus
    suspend fun openSystemSettings()
}
