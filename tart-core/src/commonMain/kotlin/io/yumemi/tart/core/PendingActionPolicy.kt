package io.yumemi.tart.core

/**
 * Controls how queued actions are handled when the store exits the current state.
 */
enum class PendingActionPolicy {
    /**
     * Clears queued actions after a transition to a different state variant is committed.
     * The currently running store work keeps running.
     */
    CLEAR_ON_STATE_EXIT,

    /**
     * Keeps queued actions unless they are cleared explicitly from DSL scopes.
     */
    KEEP,
}
