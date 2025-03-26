package io.yumemi.tart.core

/**
 * Marker interface representing state managed by the Tart framework.
 * Data classes representing application state must implement this interface.
 */
interface State

/**
 * Marker interface representing actions such as user interactions or external events.
 * Actions are dispatched to a Store and trigger state changes.
 */
interface Action

/**
 * Marker interface representing events to be notified to the UI.
 * Events are emitted from a Store and are one-time notifications to be processed once.
 */
interface Event
