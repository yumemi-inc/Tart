package io.yumemi.tart.logging

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val defaultLoggingCoroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
