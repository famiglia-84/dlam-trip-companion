package com.famiglia.tripcompanion.nanocheck;

import android.content.ClipboardManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class DiagnosticDeviceTest {
    @Test public void checkCompletesAndReportSurvivesRecreationAndCanBeCopied() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(((Button) activity.findViewById(R.id.copy)).isEnabled());
                assertFalse(((Button) activity.findViewById(R.id.download)).isEnabled());
                assertFalse(((Button) activity.findViewById(R.id.generate)).isEnabled());
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
            assertTrue(result.get().contains("No personal trip data was used."));
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

    @Test public void downloadAndGenerationRequireSeparateTapsAndCopyPreservesOutput() throws Exception {
        FakeEngine fake = new FakeEngine();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.engine = fake);
            clickAndWait(scenario, R.id.check);
            assertEquals(0, fake.downloads);
            assertEquals(0, fake.generations);
            scenario.onActivity(activity -> {
                assertTrue(((Button) activity.findViewById(R.id.download)).isEnabled());
                assertFalse(((Button) activity.findViewById(R.id.generate)).isEnabled());
            });
            clickAndWait(scenario, R.id.download);
            assertEquals(1, fake.downloads);
            assertEquals(0, fake.generations);
            scenario.onActivity(activity -> assertTrue(((Button) activity.findViewById(R.id.generate)).isEnabled()));
            clickAndWait(scenario, R.id.generate);
            assertEquals(1, fake.generations);
            assertEquals(MainActivity.SAMPLE_PROMPT, fake.prompt);
            assertTrue(readReport(scenario).contains("Result: TEXT RETURNED"));
            assertTrue(readReport(scenario).contains("Elapsed time:"));
            assertTrue(readReport(scenario).contains(fake.output));
            String completed = readReport(scenario);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(completed, ((TextView) activity.findViewById(R.id.report)).getText().toString());
                activity.findViewById(R.id.copy).performClick();
                assertEquals(completed, activity.getSystemService(ClipboardManager.class)
                    .getPrimaryClip().getItemAt(0).getText().toString());
            });
        }
    }

    @Test public void sdkGenerationFailureIsCopyableAndRequiresFreshReadinessCheck() throws Exception {
        FakeEngine fake = new FakeEngine();
        fake.status = FeatureStatus.AVAILABLE;
        fake.failGeneration = true;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.engine = fake);
            clickAndWait(scenario, R.id.check);
            clickAndWait(scenario, R.id.generate);
            assertTrue(readReport(scenario).contains("ML Kit error code: 42"));
            assertFalse(readReport(scenario).contains("private SDK detail"));
            scenario.onActivity(activity -> {
                assertTrue(((Button) activity.findViewById(R.id.copy)).isEnabled());
                assertTrue(((Button) activity.findViewById(R.id.check)).isEnabled());
                assertFalse(((Button) activity.findViewById(R.id.generate)).isEnabled());
            });
        }
    }

    @Test public void stopWaitingCancelsWorkerAndDoesNotStartGeneration() throws Exception {
        FakeEngine fake = new FakeEngine();
        fake.blockDownload = true;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.engine = fake);
            clickAndWait(scenario, R.id.check);
            scenario.onActivity(activity -> activity.findViewById(R.id.download).performClick());
            assertTrue(fake.downloadStarted.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                assertTrue(((Button) activity.findViewById(R.id.cancel)).isEnabled());
                assertFalse(((Button) activity.findViewById(R.id.check)).isEnabled());
                activity.findViewById(R.id.cancel).performClick();
            });
            assertTrue(fake.downloadInterrupted.await(5, TimeUnit.SECONDS));
            assertTrue(readReport(scenario).contains("Result: STOPPED WAITING"));
            assertEquals(0, fake.generations);
            clickAndWait(scenario, R.id.check);
            assertTrue(readReport(scenario).contains("Result: DOWNLOADABLE"));
        }
    }

    @Test public void windowRecreationDuringDownloadPreservesReportAndRequiresRecheck() throws Exception {
        FakeEngine fake = new FakeEngine();
        fake.blockDownload = true;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.engine = fake);
            clickAndWait(scenario, R.id.check);
            scenario.onActivity(activity -> activity.findViewById(R.id.download).performClick());
            assertTrue(fake.downloadStarted.await(5, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.recreate();
            assertTrue(fake.downloadInterrupted.await(5, TimeUnit.SECONDS));
            assertTrue(readReport(scenario).contains("INTERRUPTED BY WINDOW RECREATION"));
            scenario.onActivity(activity -> {
                assertTrue(((Button) activity.findViewById(R.id.copy)).isEnabled());
                assertTrue(((Button) activity.findViewById(R.id.check)).isEnabled());
                assertFalse(((Button) activity.findViewById(R.id.download)).isEnabled());
                assertFalse(((Button) activity.findViewById(R.id.generate)).isEnabled());
            });
        }
    }

    private void clickAndWait(ActivityScenario<MainActivity> scenario, int button) throws Exception {
        scenario.onActivity(activity -> activity.findViewById(button).performClick());
        AtomicBoolean done = new AtomicBoolean(false);
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!done.get() && System.nanoTime() < deadline) {
            scenario.onActivity(activity -> done.set(((Button) activity.findViewById(R.id.copy)).isEnabled()));
            Thread.sleep(50);
        }
        assertTrue("Action did not complete", done.get());
    }

    private String readReport(ActivityScenario<MainActivity> scenario) {
        AtomicReference<String> value = new AtomicReference<>();
        scenario.onActivity(activity -> value.set(((TextView) activity.findViewById(R.id.report)).getText().toString()));
        return value.get();
    }

    private static final class FakeEngine implements AiEngine {
        volatile int status = FeatureStatus.DOWNLOADABLE;
        volatile int downloads, generations;
        volatile String prompt;
        final String output = "09:00–09:45 Amber Museum; 10:00–10:45 Willow Garden; 11:00–11:45 Pebble Market.";
        boolean failGeneration, blockDownload;
        final CountDownLatch downloadStarted = new CountDownLatch(1);
        final CountDownLatch downloadInterrupted = new CountDownLatch(1);

        @Override public int checkStatus() { return status; }
        @Override public int download(Progress progress) throws InterruptedException {
            downloads++;
            progress.update("Fake download started for native UI validation.");
            downloadStarted.countDown();
            if (blockDownload) {
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException e) { downloadInterrupted.countDown(); throw e; }
            }
            status = FeatureStatus.AVAILABLE;
            return status;
        }
        @Override public String generate(String prompt) throws GenAiException {
            generations++;
            this.prompt = prompt;
            if (failGeneration) throw new GenAiException("private SDK detail", null, 42);
            return output;
        }
    }
}
