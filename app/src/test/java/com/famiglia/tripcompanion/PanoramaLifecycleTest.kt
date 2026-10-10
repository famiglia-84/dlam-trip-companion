package com.famiglia.tripcompanion

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.famiglia.tripcompanion.maps.PanoramaLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PanoramaLifecycleTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
    @Test fun openingInAnAlreadyResumedActivityAndClosingPairsAllNativeCallsOnce() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val events = mutableListOf<Lifecycle.Event>()
        val bridge = PanoramaLifecycle(owner.lifecycle, events::add)
        bridge.close(); bridge.close()
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        assertEquals(listOf(Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY), events)
    }
    @Test fun backgroundAndResumeReuseThePanoramaUntilActivityDestruction() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val events = mutableListOf<Lifecycle.Event>()
        val bridge = PanoramaLifecycle(owner.lifecycle, events::add)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        bridge.close()
        assertEquals(listOf(Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY), events)
    }
    @Test fun closingBeforeCreationDoesNotCreateOrDestroyANeverCreatedView() {
        val owner = Owner()
        val events = mutableListOf<Lifecycle.Event>()
        val bridge = PanoramaLifecycle(owner.lifecycle, events::add)
        bridge.close()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(emptyList<Lifecycle.Event>(), events)
    }
}
