package io.yumemi.tart.saver.retained

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.takahirom.rin.rememberRetained
import io.yumemi.tart.core.ExperimentalTartApi
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StateSaver

@Suppress("unused")
@ExperimentalTartApi
@Composable
fun <S : State> retainedStateSaver(): StateSaver<S> {
    var savedState: S? by rememberRetained {
        mutableStateOf(null)
    }

    return remember {
        object : StateSaver<S> {
            override fun save(state: S) {
                savedState = state
            }

            override fun restore(): S? {
                return savedState
            }
        }
    }
}
