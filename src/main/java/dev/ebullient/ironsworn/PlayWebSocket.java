package dev.ebullient.ironsworn;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.chat.PlayMemoryProvider;
import dev.ebullient.ironsworn.memory.StoryMemoryIndexer;
import dev.ebullient.ironsworn.model.CharacterSheet;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.RunOnVirtualThread;

@WebSocket(path = "/ws/play/{campaignId}")
public class PlayWebSocket {

    private static final Map<String, ReentrantLock> GENERATION_LOCKS = new ConcurrentHashMap<>();

    @Inject
    WebSocketConnection connection;

    @Inject
    CreationEngine creationEngine;

    @Inject
    GamePlayEngine gamePlayEngine;

    @Inject
    PlayMemoryProvider memoryProvider;

    @Inject
    GameJournal journal;

    @Inject
    StoryMemoryIndexer storyMemoryIndexer;

    @Inject
    ObjectMapper objectMapper;

    String campaignId;

    @OnOpen
    @RunOnVirtualThread
    public String onOpen(@PathParam String campaignId) {
        this.campaignId = campaignId;
        Log.infof("Play WebSocket opened: %s (connection: %s)", campaignId, connection.id());

        try {
            return withLockWait(30_000, "Another action is in progress\u2026 waiting to resume.", () -> {
                // Clear stale LLM chat history so reconnects start fresh.
                memoryProvider.clear(campaignId);

                // Warm long-term story memory in the background for this campaign.
                storyMemoryIndexer.warmIndex(campaignId);

                EngineContext ctx = ctx();
                return journal.isCreationPhase(campaignId)
                        ? creationEngine.handleOpen(ctx)
                        : gamePlayEngine.handleOpen(ctx);
            });
        } catch (Exception e) {
            Log.errorf(e, "Failed to open campaign: %s", campaignId);
            String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
            return errorJson("Failed to open campaign %s: %s".formatted(campaignId, message));
        }
    }

    @OnClose
    public void onClose() {
        Log.infof("Play WebSocket closed: %s", campaignId);
    }

    @OnError
    public void onError(Throwable error) {
        Log.errorf(error, "Play WebSocket error: %s", campaignId);
        try {
            connection.closeAndAwait(CloseReason.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            Log.debug("Failed to close WebSocket after error", e);
        }
    }

    @OnTextMessage
    @RunOnVirtualThread
    public String onMessage(String rawMessage) {
        try {
            JsonNode msg = objectMapper.readTree(rawMessage);
            String type = msg.path("type").asText();
            EngineContext ctx = ctx();

            return switch (type) {
                // Creation flow (delegated to CreationEngine)
                case "creation_chat" -> withLock(() -> creationEngine.handleChat(ctx, msg));
                case "finalize_creation" -> withLock(() -> creationEngine.handleFinalize(ctx, msg));
                // Gameplay flow (delegated to GamePlayEngine)
                case "narrative" -> withLock(() -> gamePlayEngine.handleNarrative(ctx, msg));
                case "move_result" -> withLock(() -> gamePlayEngine.handleMoveResult(ctx, msg));
                case "inspire" -> withLock(() -> gamePlayEngine.handleInspireMe(ctx));
                case "oracle" -> gamePlayEngine.handleOracle(ctx, msg);
                case "oracle_manual" -> gamePlayEngine.handleOracleManual(ctx, msg);
                case "progress_mark" -> gamePlayEngine.handleProgressMark(ctx, msg);
                case "character_update" -> gamePlayEngine.handleCharacterUpdate(ctx, msg);
                case "backtrack" -> withLock(() -> gamePlayEngine.handleBacktrack(ctx, msg));
                case "slash_command" -> handleSlashCommand(ctx, msg);
                default -> errorJson("Unknown message type: " + type);
            };
        } catch (Exception e) {
            Log.errorf(e, "Error processing message for campaign: %s", campaignId);
            return errorJson(e.getMessage());
        }
    }

    private EngineContext ctx() {
        GameEventEmitter emitter = new GameEventEmitter() {
            @Override
            public void emit(String json) {
                connection.sendTextAndAwait(json);
            }

            @Override
            public void emit(Map<String, Object> map) throws Exception {
                connection.sendTextAndAwait(objectMapper.writeValueAsString(map));
            }

            @Override
            public void delta(String text) throws Exception {
                connection.sendTextAndAwait(objectMapper.writeValueAsString(
                        Map.of("type", "delta", "text", text)));
            }

            @Override
            public String errorJson(String message) {
                return PlayWebSocket.this.errorJson(message);
            }
        };
        return new EngineContext(campaignId, emitter);
    }

    private String handleSlashCommand(EngineContext ctx, JsonNode msg) throws Exception {
        String text = msg.path("text").asText("").trim();
        if (text.isBlank() || !text.startsWith("/")) {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "slash_command_result",
                    "command", "unknown",
                    "message", "Unknown command. Try /status."));
        }

        String commandToken = text.split("\\s+", 2)[0].strip();
        String command = (commandToken.startsWith("/") ? commandToken.substring(1) : commandToken)
                .toLowerCase();

        if ("status".equals(command)) {
            CharacterSheet character = journal.readCharacter(campaignId);
            String phase = journal.isCreationPhase(campaignId) ? "creation" : "active";
            return objectMapper.writeValueAsString(Map.of(
                    "type", "slash_command_result",
                    "command", "status",
                    "phase", phase,
                    "character", character));
        }

        return withLock(() -> handleMutableSlashCommand(ctx, command));
    }

    private String handleMutableSlashCommand(EngineContext ctx, String command) throws Exception {
        // Delegate creation-phase commands to CreationEngine
        if (journal.isCreationPhase(campaignId)) {
            String result = creationEngine.handleSlashCommand(ctx, command);
            if (result != null) {
                return result;
            }
        }

        return objectMapper.writeValueAsString(Map.of(
                "type", "slash_command_result",
                "command", "unknown",
                "message", "Unknown command. Try /status."));
    }

    @FunctionalInterface
    private interface LockAction {
        String call() throws Exception;
    }

    private String withLock(LockAction action) throws Exception {
        if (campaignId == null || campaignId.isBlank()) {
            return errorJson("Missing campaign id");
        }
        ReentrantLock lock = GENERATION_LOCKS.computeIfAbsent(campaignId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            return errorJson("Generation already in progress");
        }
        try {
            return action.call();
        } finally {
            lock.unlock();
        }
    }

    private String withLockWait(long timeoutMillis, String waitingDelta, LockAction action) throws Exception {
        if (campaignId == null || campaignId.isBlank()) {
            return errorJson("Missing campaign id");
        }
        ReentrantLock lock = GENERATION_LOCKS.computeIfAbsent(campaignId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            if (waitingDelta != null && !waitingDelta.isBlank()) {
                try {
                    ctx().emitter().delta(waitingDelta);
                } catch (Exception e) {
                    Log.debug("Failed to emit waiting delta while acquiring generation lock", e);
                }
            }
            boolean acquired;
            try {
                acquired = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return errorJson("Interrupted while waiting for generation lock");
            }
            if (!acquired) {
                return errorJson("Timed out waiting for generation lock");
            }
        }
        try {
            return action.call();
        } finally {
            lock.unlock();
        }
    }

    private String errorJson(String message) {
        try {
            String safeMessage = Objects.toString(message, "Unknown error");
            return objectMapper.writeValueAsString(Map.of("type", "error", "message", safeMessage));
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"Internal error\"}";
        }
    }
}
