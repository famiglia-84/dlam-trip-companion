package com.famiglia.tripcompanion.nanocheck;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

public final class MainActivity extends Activity {
    static final String SAMPLE_PROMPT = "Create a short morning itinerary for fictional Sampletown. "
        + "Use ONLY these three fictional stops, in this order: Amber Museum, Willow Garden, Pebble Market. "
        + "Start Amber Museum at 09:00 (a fixed reservation). Spend exactly 45 minutes at each stop. "
        + "Allow exactly 15 minutes of walking between stops. Finish by 12:00. "
        + "Show start and end times for each visit and each walk. "
        + "Do not add places, opening hours, prices or bookings. Use at most 150 words.";
    private enum Action { CHECK, DOWNLOAD, GENERATE }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // Package-private seam lets native tests exercise user actions without downloading a model.
    AiEngine engine = new SdkAiEngine();
    private Future<?> task;
    private TextView report;
    private Button check, download, generate, cancel, copy;
    private boolean busy;
    private int status = -1;
    private long operation;
    private String completedReport;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int space = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(space, space, space, space);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(space + insets.getSystemWindowInsetLeft(),
                space + insets.getSystemWindowInsetTop(), space + insets.getSystemWindowInsetRight(),
                space + insets.getSystemWindowInsetBottom());
            return insets;
        });
        layout.addView(text("Trip Companion AI Check", 24));
        layout.addView(text("Check availability, then download the model and test a fictional itinerary. "
            + "Download only starts when you tap its button; it uses internet and device storage through "
            + "Android’s AICore. Generation runs locally. No personal trip data is accessed. "
            + "Keep this checker open during each step.", 16));
        check = button(layout, R.id.check, "Check compatibility", () -> run(Action.CHECK));
        download = button(layout, R.id.download, "Download model", () -> run(Action.DOWNLOAD));
        generate = button(layout, R.id.generate, "Test fictional itinerary", () -> run(Action.GENERATE));
        cancel = button(layout, R.id.cancel, "Stop waiting", this::stop);
        copy = button(layout, R.id.copy, "Copy result", () -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText("On-device AI test", report.getText()));
            Toast.makeText(this, "Copied. Paste the result into the chat.", Toast.LENGTH_LONG).show();
        });
        completedReport = deviceInfo() + "\nTap Check compatibility (up to 20 seconds).";
        report = text(completedReport, 16);
        report.setId(R.id.report);
        report.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(report);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        if (savedInstanceState != null) {
            completedReport = savedInstanceState.getString("report", completedReport);
            status = savedInstanceState.getInt("status", -1);
            report.setText(completedReport);
            copy.setEnabled(savedInstanceState.getBoolean("canCopy", false));
        } else copy.setEnabled(false);
        updateButtons();
    }

    private Button button(LinearLayout layout, int id, String label, Runnable action) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        layout.addView(button);
        return button;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 8, 0, 8);
        return view;
    }

    private void updateButtons() {
        check.setEnabled(!busy);
        download.setEnabled(!busy && status == FeatureStatus.DOWNLOADABLE);
        generate.setEnabled(!busy && status == FeatureStatus.AVAILABLE);
        cancel.setEnabled(busy);
    }

    private void run(Action action) {
        if (busy || (action == Action.DOWNLOAD && status != FeatureStatus.DOWNLOADABLE)
            || (action == Action.GENERATE && status != FeatureStatus.AVAILABLE)) return;
        busy = true;
        long ticket = ++operation;
        String previous = completedReport;
        String label = action == Action.CHECK ? "Compatibility check" :
            action == Action.DOWNLOAD ? "Model download (up to 10 minutes)" : "Fictional itinerary (up to 2 minutes)";
        String prefix = previous + "\n\n--- " + label + " ---\n";
        copy.setEnabled(false);
        updateButtons();
        report.setText(prefix + "Working…");
        task = worker.submit(() -> {
            String result;
            int newStatus = -1;
            long started = SystemClock.elapsedRealtime();
            try {
                if (action == Action.CHECK) {
                    newStatus = engine.checkStatus();
                    result = "Result: " + Readiness.explain(newStatus)
                        + "\nThis check requested no inference or model download.";
                } else if (action == Action.DOWNLOAD) {
                    newStatus = engine.download(message -> runOnUiThread(() -> {
                        if (ticket == operation && busy && !isDestroyed()) report.setText(prefix + message);
                    }));
                    result = "Download request completed.\nResult: " + Readiness.explain(newStatus);
                } else {
                    String output = engine.generate(SAMPLE_PROMPT);
                    newStatus = FeatureStatus.AVAILABLE;
                    result = "Result: " + (output.trim().isEmpty() ? "NO TEXT RETURNED" : "TEXT RETURNED")
                        + "\nElapsed time: " + (SystemClock.elapsedRealtime() - started) + " ms"
                        + "\n\nFictional prompt:\n" + SAMPLE_PROMPT + "\n\nModel output:\n" + output
                        + "\n\nReview: museum 09:00–09:45, walk 09:45–10:00, garden 10:00–10:45, "
                        + "walk 10:45–11:00, market 11:00–11:45. "
                        + "Text returned does not mean these constraints were satisfied.";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (TimeoutException e) {
                result = "Result: TIMED OUT\nThe SDK did not finish within this step’s time limit. "
                    + "Check compatibility before retrying. AICore may continue a model download independently.";
            } catch (ExecutionException e) {
                result = describeError(e.getCause());
            } catch (Exception e) {
                result = describeError(e);
            }
            String finalReport = prefix + result + "\nNo personal trip data was used.";
            int finalStatus = newStatus;
            runOnUiThread(() -> {
                if (ticket != operation || isFinishing() || isDestroyed()) return;
                busy = false;
                status = finalStatus;
                completedReport = finalReport;
                report.setText(completedReport);
                copy.setEnabled(true);
                updateButtons();
            });
        });
    }

    private void stop() {
        if (!busy) return;
        ++operation;
        if (task != null) task.cancel(true);
        busy = false;
        status = -1;
        completedReport = report.getText() + "\nResult: STOPPED WAITING\n"
            + "AICore may continue a model download independently. Check compatibility again before retrying.";
        report.setText(completedReport);
        copy.setEnabled(true);
        updateButtons();
    }

    private String deviceInfo() {
        String core;
        try {
            PackageInfo info = getPackageManager().getPackageInfo("com.google.android.aicore", 0);
            core = "Installed (version " + info.versionName + ")";
        } catch (PackageManager.NameNotFoundException e) {
            core = "Not visible or not installed";
        }
        return "Trip Companion AI Check " + BuildConfig.VERSION_NAME + "\nML Kit Prompt SDK: 1.0.0-beta4\n"
            + "Manufacturer: " + Build.MANUFACTURER + "\nDevice model: " + Build.MODEL
            + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
            + "\nSecurity patch: " + Build.VERSION.SECURITY_PATCH + "\nAICore: " + core + "\n";
    }

    private String describeError(Throwable error) {
        if (error instanceof GenAiException) {
            GenAiException sdkError = (GenAiException) error;
            return "Result: SDK ERROR\nML Kit error code: " + sdkError.getErrorCode()
                + (sdkError.getRetryDelay() == null ? "" : "\nSuggested retry delay: " + sdkError.getRetryDelay())
                + "\nKeep the checker in the foreground. Check compatibility before retrying; "
                + "paste this report into the chat.";
        }
        return "Result: ERROR\nType: " + (error == null ? "Unknown" : error.getClass().getSimpleName())
            + "\nCheck compatibility before retrying. Paste this report into the chat for review.";
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("report", busy ? report.getText() + "\nResult: INTERRUPTED BY WINDOW RECREATION\n"
            + "AICore may continue downloading. Tap Check compatibility before retrying." : completedReport);
        state.putInt("status", busy ? -1 : status);
        state.putBoolean("canCopy", busy || copy.isEnabled());
    }

    @Override protected void onDestroy() {
        ++operation;
        if (task != null) task.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
