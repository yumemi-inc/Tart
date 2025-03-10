package io.yumemi.tart.saver.persistent

import platform.Foundation.NSUserDefaults

internal actual fun saveString(key: String, value: String) {
    NSUserDefaults.standardUserDefaults.setObject(value, key)
}

internal actual fun getString(key: String): String? {
    return NSUserDefaults.standardUserDefaults.stringForKey(key)
}
