package io.github.seektop.foundation.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
