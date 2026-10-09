package com.famiglia.tripcompanion.planning;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** No network fallback. The SDK binds to Android AICore for local inference. */
public final class NanoClient {
    public interface Progress { void update(String message); }
    private interface Operation<T> { T run(GenerativeModelFutures model) throws Exception; }
    private <T> T withModel(Operation<T> action) throws Exception {
        GenerativeModelFutures model = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
        try { return action.run(model); }
        finally {
            try { model.getGenerativeModel().close(); }
            catch (RuntimeException ignored) { /* Preserve the operation result. */ }
        }
    }
    private <T> T await(ListenableFuture<T> future, long seconds) throws Exception {
        try { return future.get(seconds, TimeUnit.SECONDS); }
        finally { if (!future.isDone()) future.cancel(true); }
    }
    public String check() throws Exception {
        return withModel(model -> {
            switch (await(model.checkStatus(), 20)) {
                case FeatureStatus.AVAILABLE: return "AVAILABLE";
                case FeatureStatus.DOWNLOADABLE: return "DOWNLOADABLE";
                case FeatureStatus.DOWNLOADING: return "DOWNLOADING";
                case FeatureStatus.UNAVAILABLE: return "UNAVAILABLE";
                default: return "UNKNOWN";
            }
        });
    }
    public String download(Progress progress) throws Exception {
        withModel(model -> await(model.download(new DownloadCallback() {
            @Override public void onDownloadStarted(long bytes) { progress.update("Model download started. Keep the app open."); }
            @Override public void onDownloadProgress(long bytes) { progress.update("Downloaded " + bytes + " bytes. Keep the app open."); }
            @Override public void onDownloadCompleted() { progress.update("Downloaded. Checking readiness…"); }
        }), 600));
        return check();
    }
    public String generate(String prompt) throws Exception {
        return withModel(model -> {
            GenerateContentRequest.Builder request = new GenerateContentRequest.Builder(new TextPart(prompt));
            request.setTemperature(0.2f);
            request.setCandidateCount(1);
            request.setMaxOutputTokens(256);
            GenerateContentResponse response = await(model.generateContent(request.build()), 120);
            if (response.getCandidates().isEmpty()) return "";
            String text = response.getCandidates().get(0).getText();
            return text == null ? "" : text;
        });
    }
    public static String describeError(Exception exception) {
        Throwable error = exception;
        while (error instanceof ExecutionException && error.getCause() != null) error = error.getCause();
        if (error instanceof TimeoutException) return "On-device AI timed out. Recheck AI readiness, or build without AI.";
        if (error instanceof GenAiException) {
            GenAiException sdkError = (GenAiException) error;
            return "On-device AI error " + sdkError.getErrorCode() + ". Keep the app open, recheck readiness and retry."
                + (sdkError.getRetryDelay() == null ? "" : " Suggested retry delay: " + sdkError.getRetryDelay())
                + " You can build without AI.";
        }
        return "On-device AI could not complete this step. Recheck readiness or build without AI.";
    }
}
