package com.famiglia.tripcompanion.nanocheck;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> task;
    private TextView report;
    private Button check;
    private Button copy;
    private boolean checking;
    private String deviceInfo;

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
        TextView title = text("Trip Companion AI Check", 24);
        layout.addView(title);
        layout.addView(text("Checks Android’s on-device AI availability. Only device/Android version "
            + "and readiness information are collected. No trip access, AI prompts or model downloads.", 16));
        check = new Button(this);
        check.setId(R.id.check);
        check.setText("Check compatibility");
        check.setOnClickListener(view -> runCheck());
        layout.addView(check);
        copy = new Button(this);
        copy.setId(R.id.copy);
        copy.setText("Copy result");
        copy.setEnabled(false);
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText("On-device AI check", report.getText()));
            Toast.makeText(this, "Copied. Paste the result into the chat.", Toast.LENGTH_LONG).show();
        });
        layout.addView(copy);
        report = text("Tap Check compatibility. The check can take up to 20 seconds.", 16);
        report.setId(R.id.report);
        report.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(report);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        if (savedInstanceState != null) {
            report.setText(savedInstanceState.getString("report", "Tap Check compatibility."));
            copy.setEnabled(savedInstanceState.getBoolean("canCopy", false));
        }
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 12, 0, 12);
        return view;
    }

    private void runCheck() {
        checking = true;
        check.setEnabled(false);
        copy.setEnabled(false);
        deviceInfo = deviceInfo();
        report.setText(deviceInfo + "\nChecking Android AI availability…");
        task = worker.submit(() -> {
            GenerativeModelFutures model = null;
            com.google.common.util.concurrent.ListenableFuture<Integer> availability = null;
            String result;
            try {
                model = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
                availability = model.checkStatus();
                int status = availability.get(20, TimeUnit.SECONDS);
                result = Readiness.explain(status);
            } catch (TimeoutException e) {
                result = "CHECK TIMED OUT\nNo readiness result was returned. Check system/AICore updates "
                    + "and retry with an internet connection available for Android’s system services.";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                result = describeError(e.getCause());
            } catch (Exception e) {
                result = describeError(e);
            } finally {
                if (availability != null && !availability.isDone()) availability.cancel(true);
                if (model != null) {
                    try { model.getGenerativeModel().close(); }
                    catch (RuntimeException ignored) { /* Cleanup must not hide the readiness report. */ }
                }
            }
            String completed = deviceInfo + "\nResult: " + result
                + "\n\nThis is an availability check, not proof that itinerary generation works. "
                + "No inference or model download was requested.";
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                checking = false;
                report.setText(completed);
                check.setEnabled(true);
                copy.setEnabled(true);
            });
        });
    }

    private String deviceInfo() {
        String core;
        try {
            PackageInfo info = getPackageManager().getPackageInfo("com.google.android.aicore", 0);
            core = "Installed (version " + info.versionName + ")";
        } catch (PackageManager.NameNotFoundException e) {
            core = "Not visible or not installed";
        }
        return "Trip Companion AI Check 1.0\nML Kit Prompt SDK: 1.0.0-beta4\n"
            + "Manufacturer: " + Build.MANUFACTURER + "\nDevice model: " + Build.MODEL
            + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
            + "\nSecurity patch: " + Build.VERSION.SECURITY_PATCH + "\nAICore: " + core + "\n";
    }

    private String describeError(Throwable error) {
        if (error instanceof GenAiException) {
            int code = ((GenAiException) error).getErrorCode();
            return "CHECK ERROR\nML Kit error code: " + code
                + "\nPaste this report into the chat. An error is not the same as confirmed lack of support.";
        }
        return "CHECK ERROR\nType: " + (error == null ? "Unknown" : error.getClass().getSimpleName())
            + "\nPaste this report into the chat for review.";
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("report", checking ? "Check interrupted by window recreation. Tap Check compatibility again." : report.getText().toString());
        state.putBoolean("canCopy", !checking && copy.isEnabled());
    }

    @Override protected void onDestroy() {
        if (task != null) task.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
