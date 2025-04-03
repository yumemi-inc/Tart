package io.yumemi.tart.core

/**
 * Annotation to mark experimental APIs in the Tart framework.
 * APIs marked with this annotation may change in the future without notice.
 *
 * This annotation is used to notify users that an API is experimental
 * and requires explicit opt-in when used.
 */
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

/**
 * Annotation to mark classes for DSL marker usage.
 * This annotation restricts the scope of implicit receivers to avoid confusion between receivers.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
internal annotation class TartStoreDsl
