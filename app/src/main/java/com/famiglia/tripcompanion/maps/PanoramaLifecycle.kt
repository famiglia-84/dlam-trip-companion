package com.famiglia.tripcompanion.maps

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** A native panorama can leave composition before its Activity is destroyed.
 * Pair each start/resume and destroy exactly once, including that early exit. */
internal class PanoramaLifecycle(private val lifecycle: Lifecycle, private val dispatch: (Lifecycle.Event) -> Unit) : LifecycleEventObserver {
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false

    init { lifecycle.addObserver(this) }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (destroyed) return
        when (event) {
            Lifecycle.Event.ON_CREATE -> if (!created) { created = true; dispatch(event) }
            Lifecycle.Event.ON_START -> if (!started) { started = true; dispatch(event) }
            Lifecycle.Event.ON_RESUME -> if (!resumed) { resumed = true; dispatch(event) }
            Lifecycle.Event.ON_PAUSE -> if (resumed) { resumed = false; dispatch(event) }
            Lifecycle.Event.ON_STOP -> if (started) { started = false; dispatch(event) }
            Lifecycle.Event.ON_DESTROY -> destroy()
            Lifecycle.Event.ON_ANY -> Unit
        }
    }

    fun close() { lifecycle.removeObserver(this); destroy() }

    private fun destroy() {
        if (destroyed) return
        if (resumed) { resumed = false; dispatch(Lifecycle.Event.ON_PAUSE) }
        if (started) { started = false; dispatch(Lifecycle.Event.ON_STOP) }
        destroyed = true
        if (created) dispatch(Lifecycle.Event.ON_DESTROY)
    }
}
