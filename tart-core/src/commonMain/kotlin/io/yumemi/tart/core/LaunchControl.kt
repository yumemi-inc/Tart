package io.yumemi.tart.core

/**
 * Controls how coroutines launched from an `action {}` handler coordinate with
 * previous work from the same action lane.
 *
 * [Concurrent] launches are not tracked by key.
 * [Replace] and [DropNew] create tracked lanes with explicit keys that can also
 * be targeted by `cancelLaunch(key)`.
 */
sealed interface LaunchControl {

    /**
     * Launch every asynchronous handler invocation concurrently.
     */
    data object Concurrent : LaunchControl

    /**
     * Cancel the previous tracked asynchronous job in the same lane before
     * starting a new one.
     */
    data class Replace(val key: Any) : LaunchControl

    /**
     * Ignore new launches while tracked work in the same lane is still active.
     */
    data class DropNew(val key: Any) : LaunchControl
}
