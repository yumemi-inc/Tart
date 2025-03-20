package io.yumemi.tart.compose

import androidx.compose.runtime.Composable
import io.github.takahirom.rin.rememberRetained
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StateSaver

private class StateSaverImpl<S : State> : StateSaver<S> {
    private var savedState: S? = null

    override fun save(state: S) {
        savedState = state
    }

    override fun restore(): S? {
        return savedState
    }
}

@Suppress("unused")
@Composable
fun <S : State> rememberStateSaver(): StateSaver<S> {
    return rememberRetained {
        StateSaverImpl()
    }
}
