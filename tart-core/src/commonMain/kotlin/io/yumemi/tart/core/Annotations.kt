package io.yumemi.tart.core

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is an experimental API for Tart Framework. It may be changed in the future without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
annotation class ExperimentalTartApi
