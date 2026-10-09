package com.famiglia.tripcompanion.nanocheck;

import android.content.ClipboardManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DiagnosticDeviceTest {
    @Test public void checkCompletesAndReportSurvivesRecreationAndCanBeCopied() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(((Button) activity.findViewById(R.id.copy)).isEnabled());
                activity.findViewById(R.id.check).performClick();
            });
            AtomicBoolean done = new AtomicBoolean(false);
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (!done.get() && System.nanoTime() < deadline) {
                scenario.onActivity(activity -> done.set(((Button) activity.findViewById(R.id.copy)).isEnabled()));
                Thread.sleep(100);
            }
            assertTrue("Readiness check did not finish or recover from an SDK error", done.get());
            AtomicReference<String> result = new AtomicReference<>();
            scenario.onActivity(activity -> result.set(((TextView) activity.findViewById(R.id.report)).getText().toString()));
            assertTrue(result.get().contains("Result: "));
            assertTrue(result.get().contains("No inference or model download was requested."));
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(((Button) activity.findViewById(R.id.copy)).isEnabled());
                assertEquals(result.get(), ((TextView) activity.findViewById(R.id.report)).getText().toString());
                activity.findViewById(R.id.copy).performClick();
                ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                assertEquals(result.get(), clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            });
        }
    }
}
