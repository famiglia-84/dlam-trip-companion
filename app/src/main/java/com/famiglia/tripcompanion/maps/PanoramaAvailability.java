package com.famiglia.tripcompanion.maps;

import com.google.android.gms.maps.StreetViewPanorama;

/** SDK 19.0.0 annotates locations non-null although absent coverage returns null.
 * Keep that check at the Java boundary, avoiding Kotlin's generated non-null guards. */
public final class PanoramaAvailability {
    private PanoramaAvailability() {}

    public interface Listener {
        void onLocationChanged(boolean available);
    }

    public static void observe(StreetViewPanorama panorama, Listener listener) {
        panorama.setOnStreetViewPanoramaChangeListener(location -> listener.onLocationChanged(location != null));
    }

    public static boolean hasLocation(StreetViewPanorama panorama) {
        return panorama.getLocation() != null;
    }
}
