package io.github.seektop.data.offline

/** Fetches one picture's bytes for storage. Null when it could not be had, or is not worth keeping. */
interface OfflineImageSource {
    suspend fun fetch(
        url: String,
        maxBytes: Long,
    ): ByteArray?
}
