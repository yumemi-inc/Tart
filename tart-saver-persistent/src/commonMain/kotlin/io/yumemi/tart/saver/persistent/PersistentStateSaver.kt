package io.yumemi.tart.saver.persistent

import io.yumemi.tart.core.ExperimentalTartApi
import io.yumemi.tart.core.State
import io.yumemi.tart.core.StateSaver
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

internal const val LIBRARY_NAME = "io.yumemi.tart.saver.persistent"

@Suppress("unused")
@ExperimentalTartApi
inline fun <reified S : State> persistentStateSaver(key: String? = null): StateSaver<S> {
    val typeKey = S::class.qualifiedName ?: throw IllegalArgumentException("Cannot use a class without a qualified name as a key: ${S::class.simpleName ?: "Unknown"}")
    val userKey = if (key != null) "${key}:" else ""
    return persistentStateSaver("${userKey}${typeKey}", serializer<S>())
}

@ExperimentalTartApi
fun <S : State> persistentStateSaver(key: String, serializer: KSerializer<S>, json: Json = Json): StateSaver<S> {
    return PersistentStateSaverImpl("$LIBRARY_NAME:${key}", serializer, json)
}

private class PersistentStateSaverImpl<S : State>(
    private val key: String,
    private val serializer: KSerializer<S>,
    private val json: Json,
) : StateSaver<S> {
    override fun save(state: S) {
        val jsonString = json.encodeToString(serializer, state)
        saveString(key, jsonString)
    }

    override fun restore(): S? {
        val jsonString = getString(key) ?: return null
        return try {
            json.decodeFromString(serializer, jsonString)
        } catch (e: Exception) {
            null
        }
    }
}

internal expect fun saveString(key: String, value: String)
internal expect fun getString(key: String): String?
