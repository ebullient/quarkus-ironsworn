package dev.ebullient.ironsworn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.chat.CreationAssistant;
import dev.ebullient.ironsworn.chat.InspireResult;
import dev.ebullient.ironsworn.chat.MarkdownAugmenter;
import dev.ebullient.ironsworn.chat.OracleService;
import dev.ebullient.ironsworn.chat.PlayAssistant;
import dev.ebullient.ironsworn.chat.PlayMemoryProvider;
import dev.ebullient.ironsworn.chat.PlayResponse;
import dev.ebullient.ironsworn.memory.StoryMemoryIndexer;
import dev.ebullient.ironsworn.memory.StoryMemoryService;
import dev.ebullient.ironsworn.model.CharacterSheet;
import dev.ebullient.ironsworn.model.OracleResult;
import dev.ebullient.ironsworn.model.Outcome;
import dev.ebullient.ironsworn.model.Vow;
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
    PlayAssistant assistant;

    @Inject
    OracleService oracleService;

    @Inject
    CreationAssistant creationAssistant;

    @Inject
    PlayMemoryProvider memoryProvider;

    @Inject
    GameJournal journal;

    @Inject
    IronswornMechanics mechanics;

    @Inject
    MarkdownAugmenter prettify;

    @Inject
    StoryMemoryService storyMemory;

    @Inject
    StoryMemoryIndexer storyMemoryIndexer;

    @Inject
    ObjectMapper objectMapper;

    String campaignId;

    CreationEngine creationEngine;

    @OnOpen
    public String onOpen(@PathParam String campaignId) {
        this.campaignId = campaignId;
        GENERATION_LOCKS.computeIfAbsent(campaignId, k -> new AtomicBoolean(false));
        Log.infof("Play WebSocket opened: %s (connection: %s)", campaignId, connection.id());

        // Clear stale LLM chat history so reconnects start fresh.
        memoryProvider.clear(campaignId);

        // Warm long-term story memory in the background for this campaign.
        storyMemoryIndexer.warmIndex(campaignId);

        // Send lightweight handshake — heavy work deferred until client sends "start"
        return connectedJson();
    }

    private String handleActivePlayOpen() throws Exception {
        CharacterSheet character = journal.readCharacter(campaignId);

        // Replay recent journal for active play as pre-rendered blocks
        String existingJournal = journal.getRecentJournal(campaignId, 100);
        if (!existingJournal.isBlank()) {
            var blocks = JournalParser.parseToBlocks(existingJournal, prettify);
            connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                    "type", "play_resume",
                    "blocks", blocks)));
        }

        connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                "type", "character_update",
                "character", character)));

        // If the last journal entry needs narration (player input or move result), re-engage
        if (needsNarration(existingJournal)) {
            return reengageNarration(character, existingJournal);
        }

        return objectMapper.writeValueAsString(Map.of("type", "ready"));
    }

    private String reengageNarration(CharacterSheet character, String existingJournal) throws Exception {
        String charCtx = formatCharacterContext(character);
        String journalCtx = journal.getRecentJournal(campaignId, 30);
        String resumePrompt = endsWithPlayerEntry(existingJournal)
                ? extractLastPlayerInput(existingJournal)
                : "Continue the story based on what just happened.";
        String memoryCtx = storyMemory.relevantMemory(campaignId, resumePrompt);

        AtomicBoolean lock = GENERATION_LOCKS.get(campaignId);
        if (lock != null && lock.compareAndSet(false, true)) {
            try {
                connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                        "type", "loading")));
                PlayResponse response = assistant.narrate(campaignId, charCtx, journalCtx, memoryCtx, resumePrompt);
                String narrative = OracleService.stripOracleLines(
                        JournalParser.sanitizeNarrative(response.narrative()));
                journal.appendNarrative(campaignId, narrative);
                return objectMapper.writeValueAsString(Map.of(
                        "type", "narrative",
                        "narrative", narrative,
                        "narrativeHtml", prettify.markdownToHtml(narrative),
                        "blocks", blocksForNarrative(narrative),
                        "npcs", response.npcs() != null ? response.npcs() : java.util.List.of(),
                        "location", response.location() != null ? response.location() : ""));
            } finally {
                lock.set(false);
            }
        }

        return objectMapper.writeValueAsString(Map.of("type", "ready"));
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
                // Handshake
                case "start" -> handleStart();
                // Creation flow (delegated to CreationEngine)
                case "creation_chat" -> creationEngine.handleChat(msg);
                case "creation_inspire" -> creationEngine.handleInspire();
                case "finalize_creation" -> {
                    String result = creationEngine.handleFinalize(msg);
                    creationEngine = null;
                    yield result;
                }
                // Gameplay flow
                case "narrative" -> handleNarrative(msg);
                case "move_result" -> handleMoveResult(msg);
                case "inspire" -> handleInspireMe();
                case "oracle" -> handleOracle(msg);
                case "oracle_manual" -> handleOracleManual(msg);
                case "progress_mark" -> handleProgressMark(msg);
                case "character_update" -> handleCharacterUpdate(msg);
                case "backtrack" -> handleBacktrack(msg);
                default -> errorJson("Unknown message type: " + type);
            };
        } catch (Exception e) {
            Log.errorf(e, "Error processing message for campaign: %s", campaignId);
            return errorJson(e.getMessage());
        }
    }

    // --- Handshake ---

    private String handleStart() throws Exception {
        try {
            if (journal.isCreationPhase(campaignId)) {
                creationEngine = new CreationEngine(connection, journal, creationAssistant,
                        memoryProvider, prettify, objectMapper, campaignId,
                        GENERATION_LOCKS.get(campaignId));
                return creationEngine.handleOpen();
            }
            return handleActivePlayOpen();
        } catch (Exception e) {
            Log.errorf(e, "Failed to open campaign: %s", campaignId);
            return errorJson("Campaign not found: " + campaignId);
        }
    }

    // --- Gameplay flow ---

    private String handleNarrative(JsonNode msg) throws Exception {
        String text = msg.path("text").asText();
        if (text.isBlank()) {
            return errorJson("Empty narrative text");
        }

        AtomicBoolean lock = GENERATION_LOCKS.get(campaignId);
        if (lock != null && !lock.compareAndSet(false, true)) {
            return errorJson("Generation already in progress");
        }

        try {
            journal.appendNarrative(campaignId, formatPlayerInput(text));

            CharacterSheet character = journal.readCharacter(campaignId);
            String charCtx = formatCharacterContext(character);
            String journalCtx = journal.getRecentJournal(campaignId, 60);
            String memoryCtx = storyMemory.relevantMemory(campaignId, text);

            memoryProvider.clear(campaignId);
            PlayResponse response = assistant.narrate(campaignId, charCtx, journalCtx, memoryCtx, text);
            String narrative = OracleService.stripOracleLines(
                    JournalParser.sanitizeNarrative(response.narrative()));

            journal.appendNarrative(campaignId, narrative);

            return objectMapper.writeValueAsString(Map.of(
                    "type", "narrative",
                    "narrative", narrative,
                    "narrativeHtml", prettify.markdownToHtml(narrative),
                    "blocks", blocksForNarrative(narrative),
                    "npcs", response.npcs() != null ? response.npcs() : java.util.List.of(),
                    "location", response.location() != null ? response.location() : ""));
        } finally {
            if (lock != null) {
                lock.set(false);
            }
        }
    }

    private String handleInspireMe() throws Exception {
        AtomicBoolean lock = GENERATION_LOCKS.get(campaignId);
        if (lock != null && !lock.compareAndSet(false, true)) {
            return errorJson("Generation already in progress");
        }

        try {
            CharacterSheet character = journal.readCharacter(campaignId);
            String charCtx = formatCharacterContext(character);
            String journalCtx = journal.getRecentJournal(campaignId, 60);
            // Use recent journal text as the query so memory retrieval finds relevant past context
            String[] lines = journalCtx.split("\n");
            String memoryQuery = String.join("\n",
                    java.util.Arrays.copyOfRange(lines, Math.max(0, lines.length - 10), lines.length));
            String memoryCtx = storyMemory.relevantMemory(campaignId, memoryQuery);

            InspireResult result = oracleService.inspireMe(campaignId, charCtx, journalCtx, memoryCtx);

            // Send oracle result to client if one was rolled server-side (non-tool-calling path)
            if (result.oracleResult() != null) {
                connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                        "type", "oracle_result",
                        "result", result.oracleResult())));
            }

            PlayResponse response = result.response();
            return objectMapper.writeValueAsString(Map.of(
                    "type", "narrative",
                    "narrative", result.narrative(),
                    "narrativeHtml", prettify.markdownToHtml(result.narrative()),
                    "blocks", blocksForNarrative(result.narrative()),
                    "npcs", response.npcs() != null ? response.npcs() : java.util.List.of(),
                    "location", response.location() != null ? response.location() : ""));
        } finally {
            if (lock != null) {
                lock.set(false);
            }
        }
    }

    private String handleMoveResult(JsonNode msg) throws Exception {
        String categoryKey = msg.path("categoryKey").asText();
        String moveKey = msg.path("moveKey").asText();
        String stat = msg.path("stat").asText();
        int actionDie = msg.path("actionDie").asInt();
        int challenge1 = msg.path("challenge1").asInt();
        int challenge2 = msg.path("challenge2").asInt();
        int actionScore = msg.path("actionScore").asInt();
        String outcomeStr = msg.path("outcome").asText();
        Outcome outcome = Outcome.valueOf(outcomeStr);

        // Journal the player's action description (if provided) before the roll
        String playerAction = msg.path("playerAction").asText("").trim();
        if (!playerAction.isEmpty()) {
            journal.appendNarrative(campaignId, formatPlayerInput(playerAction));
        }

        // Journal the roll
        String moveName = moveKey.replace("_", " ");
        moveName = moveName.substring(0, 1).toUpperCase() + moveName.substring(1);
        journal.appendMechanical(campaignId,
                "**%s** (+%s): Action %d, Challenge %d|%d → **%s**".formatted(
                        moveName, stat, actionScore, challenge1, challenge2, outcome.display()));

        // Look up the rules text for this outcome
        String moveOutcomeText = mechanics.getMoveOutcomeText(categoryKey, moveKey, outcome);

        // If the outcome text mentions "Pay the Price", roll that oracle automatically
        if (moveOutcomeText.contains("Pay the Price")) {
            OracleResult ptpOracle = oracleService.rollOracle("moves", "pay_the_price");
            journal.appendMechanical(campaignId, ptpOracle.toJournalEntry());

            // Send the Pay the Price result to the client
            String ptpJson = objectMapper.writeValueAsString(Map.of(
                    "type", "oracle_result",
                    "result", ptpOracle));
            connection.sendTextAndAwait(ptpJson);

            // Append to moveOutcomeText so the LLM narrates with the specific price
            moveOutcomeText += "\n\n**Pay the Price result**: " + ptpOracle.resultText();
        }

        // Send the rules text immediately
        String moveOutcomeJson = objectMapper.writeValueAsString(Map.of(
                "type", "move_outcome",
                "moveName", moveName,
                "moveOutcomeText", StringUtils.mdToHtml(moveOutcomeText).getValue()));
        connection.sendTextAndAwait(moveOutcomeJson);

        // Now get LLM narration
        AtomicBoolean lock = GENERATION_LOCKS.get(campaignId);
        if (lock != null && !lock.compareAndSet(false, true)) {
            return errorJson("Generation already in progress");
        }

        try {
            String journalCtx = journal.getRecentJournal(campaignId, 20);

            memoryProvider.clear(campaignId);
            PlayResponse response = assistant.narrateMoveResult(
                    campaignId, moveName, outcome.display(),
                    actionScore, challenge1, challenge2,
                    moveOutcomeText, journalCtx, "");
            String narrative = OracleService.stripOracleLines(
                    JournalParser.sanitizeNarrative(response.narrative()));

            journal.appendNarrative(campaignId, narrative);

            return objectMapper.writeValueAsString(Map.of(
                    "type", "narrative",
                    "narrative", narrative,
                    "narrativeHtml", prettify.markdownToHtml(narrative),
                    "blocks", blocksForNarrative(narrative),
                    "npcs", response.npcs() != null ? response.npcs() : java.util.List.of(),
                    "location", response.location() != null ? response.location() : ""));
        } finally {
            if (lock != null) {
                lock.set(false);
            }
        }
    }

    private String handleOracle(JsonNode msg) throws Exception {
        String collectionKey = msg.path("collectionKey").asText();
        String tableKey = msg.path("tableKey").asText();
        OracleResult result = oracleService.rollOracle(collectionKey, tableKey);
        journal.appendMechanical(campaignId, result.toJournalEntry());
        int blockIndex = JournalParser.countBlocks(journal.getRecentJournal(campaignId, 100)) - 1;
        return objectMapper.writeValueAsString(Map.of(
                "type", "oracle_result",
                "result", result,
                "blockIndex", blockIndex));
    }

    private String handleOracleManual(JsonNode msg) throws Exception {
        String collectionKey = msg.path("collectionKey").asText();
        String tableKey = msg.path("tableKey").asText();
        int roll = msg.path("roll").asInt();
        OracleResult result = oracleService.rollOracleManual(collectionKey, tableKey, roll);
        journal.appendMechanical(campaignId, result.toJournalEntry());
        int blockIndex = JournalParser.countBlocks(journal.getRecentJournal(campaignId, 100)) - 1;
        return objectMapper.writeValueAsString(Map.of(
                "type", "oracle_result",
                "result", result,
                "blockIndex", blockIndex));
    }

    private String handleProgressMark(JsonNode msg) throws Exception {
        int vowIndex = msg.path("vowIndex").asInt();
        CharacterSheet character = journal.readCharacter(campaignId);

        if (vowIndex < 0 || vowIndex >= character.vows().size()) {
            return errorJson("Invalid vow index: " + vowIndex);
        }

        var vow = character.vows().get(vowIndex);
        int newProgress = mechanics.markProgress(vow.progress(), vow.rank());
        var updatedVow = new Vow(vow.description(), vow.rank(), newProgress);

        var updatedVows = new ArrayList<>(character.vows());
        updatedVows.set(vowIndex, updatedVow);
        CharacterSheet updated = new CharacterSheet(
                character.name(), character.edge(), character.heart(), character.iron(),
                character.shadow(), character.wits(), character.health(), character.spirit(),
                character.supply(), character.momentum(), updatedVows);

        journal.updateCharacter(campaignId, updated);
        return objectMapper.writeValueAsString(Map.of(
                "type", "character_update",
                "character", updated));
    }

    private String handleCharacterUpdate(JsonNode msg) throws Exception {
        CharacterSheet character = objectMapper.treeToValue(msg.path("character"), CharacterSheet.class);
        journal.updateCharacter(campaignId, character);
        return objectMapper.writeValueAsString(Map.of(
                "type", "character_update",
                "character", character));
    }

    private String handleBacktrack(JsonNode msg) throws Exception {
        int blockIndex = msg.path("blockIndex").asInt(-1);
        if (blockIndex < 0) {
            return errorJson("Invalid block index");
        }
        journal.truncateJournal(campaignId, blockIndex);
        memoryProvider.clear(campaignId);
        return objectMapper.writeValueAsString(Map.of("type", "backtrack_done"));
    }

    private String extractLastPlayerInput(String journalContent) {
        return JournalParser.extractLastPlayerInput(journalContent);
    }

    private boolean needsNarration(String journalContent) {
        return JournalParser.needsNarration(journalContent);
    }

    private boolean endsWithPlayerEntry(String journalContent) {
        return JournalParser.endsWithPlayerEntry(journalContent);
    }

    private int countExchanges(String journalContext) {
        return JournalParser.countExchanges(journalContext);
    }

    private List<JournalParser.JournalBlock> blocksForNarrative(String narrative) {
        if (narrative == null || narrative.isBlank()) {
            return List.of();
        }
        return JournalParser.parseToBlocks(narrative, prettify);
    }

    private String formatCharacterContext(CharacterSheet c) {
        StringBuilder sb = new StringBuilder();
        sb.append("**%s** — Edge %d, Heart %d, Iron %d, Shadow %d, Wits %d\n".formatted(
                c.name(), c.edge(), c.heart(), c.iron(), c.shadow(), c.wits()));
        sb.append("Health %d, Spirit %d, Supply %d, Momentum %d\n".formatted(
                c.health(), c.spirit(), c.supply(), c.momentum()));
        if (!c.vows().isEmpty()) {
            sb.append("Vows:\n");
            for (var vow : c.vows()) {
                sb.append("- %s (%s, %d/10)\n".formatted(
                        vow.description(), vow.rank().name().toLowerCase(), vow.progress()));
            }
        }
        return sb.toString();
    }

    private String formatPlayerInput(String text) {
        return "<player>\n" + text.strip() + "\n</player>";
    }

    private String connectedJson() {
        try {
            return objectMapper.writeValueAsString(Map.of("type", "connected"));
        } catch (Exception e) {
            return "{\"type\":\"connected\"}";
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
