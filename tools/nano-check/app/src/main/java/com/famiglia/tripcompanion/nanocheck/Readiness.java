package com.famiglia.tripcompanion.nanocheck;

import com.google.mlkit.genai.common.FeatureStatus;

final class Readiness {
    private Readiness() {}

    static String explain(int status) {
        switch (status) {
            case FeatureStatus.AVAILABLE:
                return "AVAILABLE\nAndroid reports that the on-device model is ready. "
                    + "Tap Test fictional itinerary to try local generation.";
            case FeatureStatus.DOWNLOADABLE:
                return "DOWNLOADABLE\nAndroid supports this feature, but its model needs downloading. "
                    + "Tap Download model to request it explicitly.";
            case FeatureStatus.DOWNLOADING:
                return "DOWNLOADING\nAndroid reports that the model is downloading. "
                    + "Let it finish and check again.";
            case FeatureStatus.UNAVAILABLE:
                return "UNAVAILABLE\nThis API is not currently available on this phone. "
                    + "This can reflect device support, AICore configuration or system updates; "
                    + "it does not prove the phone will never support it.";
            default:
                return "UNKNOWN (" + status + ")\nThe SDK returned an unrecognised status. "
                    + "Paste this report into the chat for review.";
        }
    }
}
