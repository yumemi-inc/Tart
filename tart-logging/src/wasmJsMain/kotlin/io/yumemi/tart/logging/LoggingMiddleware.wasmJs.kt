package io.yumemi.tart.logging

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val defaultLoggingCoroutineDispatcher: CoroutineDispatcher = Dispatchers.Default
