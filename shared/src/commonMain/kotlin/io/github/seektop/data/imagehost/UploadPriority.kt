package io.github.seektop.data.imagehost

/** Null means that the writer must choose before any image is sent. */
fun preferredUploadHost(
    available: List<ImageHostProvider>,
    priority: List<ImageHostProvider>,
): ImageHostProvider? = available.singleOrNull() ?: priority.firstOrNull { it in available }
