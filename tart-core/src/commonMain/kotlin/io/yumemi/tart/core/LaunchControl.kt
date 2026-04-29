package io.yumemi.tart.core

/**
 * Stable token used to coordinate tracked coroutines launched from `action {}`.
 *
 * Reuse the same instance when multiple launches should share a lane or when the
 * lane should be cancellable via `cancelLaunch(...)`.
 */
class LaunchLane

/**
 * Controls how coroutines launched from an `action {}` handler coordinate with
 * previous work from the same action lane.
 *
 * [Concurrent] launches are not tracked by lane.
 * [Replace] and [DropNew] create tracked lanes. When [LaunchLane] is omitted,
 * the launch uses an internal default lane that is fixed for the current
 * `action {}` block.
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
    data class Replace(val lane: LaunchLane? = null) : LaunchControl

    /**
     * Ignore new launches while tracked work in the same lane is still active.
     */
    data class DropNew(val lane: LaunchLane? = null) : LaunchControl
}
