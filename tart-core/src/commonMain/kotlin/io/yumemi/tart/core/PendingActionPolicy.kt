package io.yumemi.tart.core

/**
 * Controls how queued actions are handled when the store exits the current state.
 */
enum class PendingActionPolicy {
    /**
     * Clears queued actions after a transition to a different state variant is committed.
     * The currently running store work keeps running.
     */
    ClearOnStateExit,

    /**
     * Keeps queued actions unless they are cleared explicitly from DSL scopes.
     */
    Keep,
}
