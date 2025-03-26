package io.yumemi.tart.core

/**
 * Interface for persisting and restoring Store state.
 * Used to maintain state even when the application is restarted.
 */
interface StateSaver<S : State> {
    /**
     * Saves the current state.
     *
     * @param state The state to save
     */
    fun save(state: S)

    /**
     * Restores the saved state.
     *
     * @return The restored state, or null if there is no saved state
     */
    fun restore(): S?
}

/**
 * Factory function to easily create a StateSaver instance.
 *
 * @param save Callback function to save state
 * @param restore Callback function to restore state
 * @return A new StateSaver instance
 */
fun <S : State> StateSaver(
    save: (S) -> Unit,
    restore: () -> S?,
): StateSaver<S> {
    return object : StateSaver<S> {
        override fun save(state: S) {
            save.invoke(state)
        }

        override fun restore(): S? {
            return restore.invoke()
        }
    }
}
