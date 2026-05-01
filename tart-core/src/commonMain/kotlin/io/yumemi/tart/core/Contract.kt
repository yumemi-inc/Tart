package io.yumemi.tart.core

/**
 * Marker interface for state snapshots managed by a Tart [Store].
 */
interface State

/**
 * Marker interface for inputs dispatched to a Tart [Store], such as user intents or external signals.
 */
interface Action

/**
 * Marker interface for one-off outputs emitted from a Tart [Store].
 *
 * Unlike [State], events are not retained as the Store's current snapshot.
 */
interface Event
