package com.famiglia.tripcompanion.nanocheck;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.util.concurrent.TimeUnit;

/** Blocking SDK calls run only on the activity's worker. Each operation owns its client. */
final class SdkAiEngine implements AiEngine {
    private interface Operation<T> { T run(GenerativeModelFutures model) throws Exception; }

    private <T> T withModel(Operation<T> operation) throws Exception {
        GenerativeModelFutures model = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
        try { return operation.run(model); }
        finally {
            try { model.getGenerativeModel().close(); }
            catch (RuntimeException ignored) { /* Preserve the operation's result/error. */ }
        }
    }

    private <T> T await(ListenableFuture<T> future, long seconds) throws Exception {
        try { return future.get(seconds, TimeUnit.SECONDS); }
        finally { if (!future.isDone()) future.cancel(true); }
    }

    @Override public int checkStatus() throws Exception {
        return withModel(model -> await(model.checkStatus(), 20));
    }

    @Override public int download(Progress progress) throws Exception {
        return withModel(model -> {
            await(model.download(new DownloadCallback() {
                @Override public void onDownloadStarted(long bytes) {
                    progress.update("Download started. Reported size: " + bytes + " bytes.");
                }
                @Override public void onDownloadProgress(long bytes) {
                    progress.update("Downloaded: " + bytes + " bytes. Keep this checker open.");
                }
                @Override public void onDownloadCompleted() {
                    progress.update("Download completed. Checking readiness…");
                }
                @Override public void onDownloadFailed(GenAiException error) {
                    progress.update("Download reported ML Kit error code: " + error.getErrorCode());
                }
            }), 600);
            return await(model.checkStatus(), 20);
        });
    }

    @Override public String generate(String prompt) throws Exception {
        return withModel(model -> {
            GenerateContentRequest.Builder request = new GenerateContentRequest.Builder(new TextPart(prompt));
            request.setTemperature(0.2f);
            request.setCandidateCount(1);
            request.setMaxOutputTokens(384);
            GenerateContentResponse response = await(model.generateContent(request.build()), 120);
            if (response.getCandidates().isEmpty()) return "";
            String text = response.getCandidates().get(0).getText();
            return text == null ? "" : text;
        });
    }
}
