package dev.ebullient.ironsworn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.chat.PlayMemoryProvider;
import dev.ebullient.ironsworn.memory.StoryMemoryIndexer;
import dev.ebullient.ironsworn.model.CharacterSheet;
import io.quarkus.logging.Log;
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

    private static final Map<String, AtomicBoolean> GENERATION_LOCKS = new ConcurrentHashMap<>();

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
        GENERATION_LOCKS.computeIfAbsent(campaignId, k -> new AtomicBoolean(false));
        Log.infof("Play WebSocket opened: %s (connection: %s)", campaignId, connection.id());

        // Clear stale LLM chat history so reconnects start fresh.
        memoryProvider.clear(campaignId);

        // Warm long-term story memory in the background for this campaign.
        storyMemoryIndexer.warmIndex(campaignId);

        try {
            return journal.isCreationPhase(campaignId)
                    ? creationEngine.handleOpen(ctx())
                    : gamePlayEngine.handleOpen(ctx());
        } catch (Exception e) {
            Log.errorf(e, "Failed to open campaign: %s", campaignId);
            return errorJson("Campaign not found: " + campaignId);
        }
    }

    @OnClose
    public void onClose() {
        Log.infof("Play WebSocket closed: %s", campaignId);
    }

    @OnError
    public String onError(Throwable error) {
        Log.errorf(error, "Play WebSocket error: %s", campaignId);
        return errorJson(error.getMessage());
    }

    @OnTextMessage
    @RunOnVirtualThread
    public String onMessage(String rawMessage) {
        try {
            JsonNode msg = objectMapper.readTree(rawMessage);
            String type = msg.path("type").asText();

            return switch (type) {
                // Creation flow (delegated to CreationEngine)
                case "creation_chat" -> withLock(() -> creationEngine.handleChat(ctx(), msg));
                case "finalize_creation" -> creationEngine.handleFinalize(ctx(), msg);
                // Gameplay flow (delegated to GamePlayEngine)
                case "narrative" -> withLock(() -> gamePlayEngine.handleNarrative(ctx(), msg));
                case "move_result" -> withLock(() -> gamePlayEngine.handleMoveResult(ctx(), msg));
                case "inspire" -> withLock(() -> gamePlayEngine.handleInspireMe(ctx()));
                case "oracle" -> gamePlayEngine.handleOracle(ctx(), msg);
                case "oracle_manual" -> gamePlayEngine.handleOracleManual(ctx(), msg);
                case "progress_mark" -> gamePlayEngine.handleProgressMark(ctx(), msg);
                case "character_update" -> gamePlayEngine.handleCharacterUpdate(ctx(), msg);
                case "backtrack" -> gamePlayEngine.handleBacktrack(ctx(), msg);
                case "slash_command" -> handleSlashCommand(msg);
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

    private String handleSlashCommand(JsonNode msg) throws Exception {
        String text = msg.path("text").asText("").trim();
        if (text.isBlank() || !text.startsWith("/")) {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "slash_command_result",
                    "command", "unknown",
                    "message", "Unknown command. Try /status."));
        }

        String commandToken = text.split("\\s+", 2)[0].strip();
        String command = commandToken.startsWith("/") ? commandToken.substring(1) : commandToken;
        command = command.toLowerCase();

        // Delegate creation-phase commands to CreationEngine
        if (journal.isCreationPhase(campaignId)) {
            String result = creationEngine.handleSlashCommand(ctx(), command);
            if (result != null) {
                return result;
            }
        }

        if ("status".equals(command)) {
            CharacterSheet character = journal.readCharacter(campaignId);
            String phase = journal.isCreationPhase(campaignId) ? "creation" : "active";
            return objectMapper.writeValueAsString(Map.of(
                    "type", "slash_command_result",
                    "command", "status",
                    "phase", phase,
                    "character", character));
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
        AtomicBoolean lock = GENERATION_LOCKS.get(campaignId);
        if (lock != null && !lock.compareAndSet(false, true)) {
            return errorJson("Generation already in progress");
        }
        try {
            return action.call();
        } finally {
            if (lock != null) {
                lock.set(false);
            }
        }
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", "error", "message", message));
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"Internal error\"}";
        }
    }
}
