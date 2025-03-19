package io.yumemi.tart.core

interface StateSaver<S : State> {
    fun save(state: S)
    fun restore(): S?
}

fun <S : State> StateSaver(
    save: (state: S) -> Unit,
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
