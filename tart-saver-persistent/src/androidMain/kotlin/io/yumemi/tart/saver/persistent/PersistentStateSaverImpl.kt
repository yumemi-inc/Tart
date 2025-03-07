package io.yumemi.tart.saver.persistent

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.lang.reflect.Method

internal actual fun saveString(key: String, value: String) {
    getSharedPreferences().edit().putString(key, value).apply()
}

internal actual fun getString(key: String): String? {
    return getSharedPreferences().getString(key, null)
}

private var applicationContext: Context? = null

@Suppress("unused")
fun initPersistentStateSaver(context: Context) {
    applicationContext = context.applicationContext
}

private fun getSharedPreferences(): SharedPreferences {
    val context = applicationContext ?: getApplicationContextReflectively()
    return context.getSharedPreferences(LIBRARY_NAME, Context.MODE_PRIVATE)
}

@SuppressLint("PrivateApi")
private fun getApplicationContextReflectively(): Context {
    try {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val currentApplicationMethod: Method = activityThreadClass.getDeclaredMethod("currentApplication")
        val application = currentApplicationMethod.invoke(null) as? Context
        if (application != null) {
            applicationContext = application
            return application
        }
    } catch (e: Exception) {
        // reflection failed
    }
    throw IllegalStateException("PersistentStateSaver could not automatically obtain application context. Please call initPersistentStateSaver(context) explicitly.")
}
