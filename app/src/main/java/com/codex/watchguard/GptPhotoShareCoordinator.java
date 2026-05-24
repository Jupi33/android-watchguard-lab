package com.codex.watchguard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class GptPhotoShareCoordinator {
    static final String GPT_PACKAGE = "com.openai.chatgpt";
    static final String PROMPT = "Estan desordenadas, analiza y resuelve - procedimiento con respuesta concreta";
    private static final int CHUNK_SIZE = 10;
    private static final long NEXT_CHUNK_DELAY_MS = 2_500L;
    private static final long GPT_READY_RETRY_MS = 500L;
    private static final int GPT_READY_MAX_ATTEMPTS = 10;
    private static final long GPT_AUTO_LOCK_DELAY_MS = 250L;

    private GptPhotoShareCoordinator() {
    }

    static void startSessionShare(Context context, String source) {
        List<String> photos = existingPhotos(AppState.getWatchCameraSessionPhotosRaw(context));
        long sessionId = AppState.getWatchCameraSessionId(context);
        if (photos.isEmpty()) {
            AppState.markGptPhotoShareNoPhotos(context, sessionId);
            return;
        }
        boolean existingGptTask = ForegroundResolver.moveExistingTaskToFront(context, GPT_PACKAGE);
        boolean gptVisible = GPT_PACKAGE.equals(ForegroundResolver.findVisiblePackage(context));
        boolean needsNewChat = !existingGptTask && !gptVisible;
        String targetMode = needsNewChat
                ? "new_chat"
                : (gptVisible ? "visible_existing" : "existing_task");
        int chunkCount = (photos.size() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        AppState.startGptPhotoShare(
                context,
                sessionId,
                PROMPT,
                joinLines(photos),
                chunkCount,
                needsNewChat,
                targetMode,
                ""
        );
        if (isCameraDoubleClickSource(source)) {
            AppState.requestGptAutoLock(context, source);
        }
        boolean accessibilityEnabled = AppState.isGptAutomationAccessibilityEnabled(context);
        boolean automationReady = AppState.isGptAutomationServiceReady(context);
        if (!accessibilityEnabled) {
            AppState.markGptAutomationAccessibilityOff(context);
        } else if (!automationReady) {
            AppState.markGptAutomationServiceNotAlive(context, "service_not_alive_start");
        }
        if (needsNewChat && automationReady) {
            ForegroundResolver.launchPackage(context, GPT_PACKAGE);
            launchPendingChunkWhenGptReady(context, 0, "after_new_chat_window", 0);
            return;
        }
        launchPendingChunk(context, 0, source);
    }

    private static void launchPendingChunkWhenGptReady(
            final Context context,
            final int index,
            final String source,
            final int attempt
    ) {
        boolean visible = GPT_PACKAGE.equals(ForegroundResolver.findVisiblePackage(context));
        if (visible || attempt >= GPT_READY_MAX_ATTEMPTS) {
            launchPendingChunk(context, index, source);
            return;
        }
        AppState.recordGptAutomationAction(context,
                "waiting_gpt_visible attempt=" + attempt);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                launchPendingChunkWhenGptReady(context, index, source, attempt + 1);
            }
        }, GPT_READY_RETRY_MS);
    }

    static void launchPendingChunk(Context context, int index, String source) {
        List<String> allPhotos = existingPhotos(AppState.getGptShareAllPhotosRaw(context));
        int chunkCount = AppState.getGptShareChunkCount(context);
        if (allPhotos.isEmpty() || index < 0 || index >= chunkCount) {
            AppState.recordGptAutomationFailure(context, "chunk_out_of_range");
            return;
        }
        int start = index * CHUNK_SIZE;
        int end = Math.min(allPhotos.size(), start + CHUNK_SIZE);
        ArrayList<Uri> uris = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Uri uri = PhotoShareProvider.uriFor(context, allPhotos.get(i));
            uris.add(uri);
            context.grantUriPermission(
                    GPT_PACKAGE,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        }
        if (uris.isEmpty()) {
            AppState.recordGptAutomationFailure(context, "empty_chunk");
            return;
        }
        String prompt = AppState.getGptSharePrompt(context);
        if (chunkCount > 1) {
            prompt = prompt + " (lote " + (index + 1) + "/" + chunkCount + ")";
        }
        Intent share = new Intent(Intent.ACTION_SEND_MULTIPLE);
        share.setType("image/jpeg");
        share.setPackage(GPT_PACKAGE);
        share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        share.putExtra(Intent.EXTRA_TEXT, prompt);
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(clipDataFor(uris));
        try {
            AppState.markGptShareChunkLaunched(context, index, uris.size());
            context.startActivity(share);
            AppState.recordGptAutomationAction(context,
                    "share_intent chunk=" + index + " source=" + source);
            scheduleGptAutoLockIfRequested(context, "share_intent_" + source,
                    GPT_AUTO_LOCK_DELAY_MS);
        } catch (RuntimeException error) {
            AppState.recordGptAutomationFailure(
                    context,
                    "share_launch_failed:" + error.getClass().getSimpleName()
            );
        }
    }

    static void startGptAutoLockIfRequested(Context context, String source) {
        if (!AppState.shouldStartGptAutoLock(context)) {
            return;
        }
        AppState.setLockedCycle(context, GPT_PACKAGE, false);
        AppState.markLockSettle(context, AppState.getLockSettleMs(), "gpt_auto_lock");
        TouchBlockerService.lockGptAutomation(context, TouchBlockerService.NO_TIMEOUT);
        AppState.recordGptAutomationAction(context,
                "gpt_auto_lock_start source=" + source);
    }

    private static void scheduleGptAutoLockIfRequested(
            final Context context,
            final String source,
            long delayMs
    ) {
        if (!AppState.shouldStartGptAutoLock(context)) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startGptAutoLockIfRequested(context, source);
            }
        }, Math.max(0L, delayMs));
    }

    static void afterAutomationSend(Context context, String source) {
        int index = AppState.getGptShareChunkIndex(context);
        int count = AppState.getGptShareChunkCount(context);
        AppState.recordGptAutomationSent(context, source + " chunk=" + index);
        if (index + 1 >= count) {
            AppState.completeGptPhotoShare(context, "all_chunks_sent");
            if (AppState.isGptAutoLockActive(context)) {
                AppState.recordGptAutoLockPromoted(context, "automation_complete");
                TouchBlockerService.lock(context, TouchBlockerService.NO_TIMEOUT);
            }
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                launchPendingChunk(context, index + 1, "next_chunk");
            }
        }, NEXT_CHUNK_DELAY_MS);
    }

    private static boolean isCameraDoubleClickSource(String source) {
        return source != null && source.contains("watch_camera_double_click");
    }

    private static android.content.ClipData clipDataFor(ArrayList<Uri> uris) {
        android.content.ClipData data = new android.content.ClipData(
                "watchguard-photo",
                new String[]{"image/jpeg"},
                new android.content.ClipData.Item(uris.get(0))
        );
        for (int i = 1; i < uris.size(); i++) {
            data.addItem(new android.content.ClipData.Item(uris.get(i)));
        }
        return data;
    }

    private static List<String> existingPhotos(String raw) {
        ArrayList<String> result = new ArrayList<>();
        if (raw == null || raw.length() == 0) {
            return result;
        }
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            String path = line == null ? "" : line.trim();
            if (path.length() > 0 && new File(path).isFile()) {
                result.add(path);
            }
        }
        return result;
    }

    private static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }
}
