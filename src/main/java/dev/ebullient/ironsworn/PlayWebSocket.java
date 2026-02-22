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
import dev.ebullient.ironsworn.chat.CreationResponse;
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
import dev.ebullient.ironsworn.model.Rank;
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

    @OnOpen
    @RunOnVirtualThread
    public String onOpen(@PathParam String campaignId) {
        this.campaignId = campaignId;
        GENERATION_LOCKS.computeIfAbsent(campaignId, k -> new AtomicBoolean(false));
        Log.infof("Play WebSocket opened: %s (connection: %s)", campaignId, connection.id());

        // Warm long-term story memory in the background for this campaign.
        storyMemoryIndexer.warmIndex(campaignId);

        try {
            return journal.isCreationPhase(campaignId)
                    ? handleCreationPhaseOpen()
                    : handleActivePlayOpen();
        } catch (Exception e) {
            Log.errorf(e, "Failed to open campaign: %s", campaignId);
            return errorJson("Campaign not found: " + campaignId);
        }
    }

    private String handleCreationPhaseOpen() throws Exception {
        // Send creation phase indicator
        connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                "type", "creation_phase",
                "phase", "creation")));

        String existingJournal = journal.getRecentJournal(campaignId, 100);
        if (existingJournal.isBlank()) {
            // Fresh creation — generate and send inspiration
            return handleInspire();
        }

        // Replay existing conversation to the client as pre-rendered blocks
        var blocks = JournalParser.parseToBlocks(existingJournal, prettify);
        connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                "type", "creation_resume",
                "blocks", blocks)));
        CharacterSheet character = journal.readCharacter(campaignId);
        connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                "type", "creation_ready",
                "character", character)));

        // Re-engage the guide with the last player message
        String lastPlayerInput = extractLastPlayerInput(existingJournal);
        if (lastPlayerInput == null) {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "creation_ready",
                    "character", character));
        }

        return reengageCreationGuide(character, existingJournal, lastPlayerInput);
    }

    private String reengageCreationGuide(CharacterSheet character, String existingJournal, String lastPlayerInput)
            throws Exception {
        String journalContext = journal.getRecentJournal(campaignId, 30);
        int exchangeCount = countExchanges(journalContext);
        String vowInstruction = exchangeCount >= 3
                ? "IMPORTANT: The player has responded %d times. You MUST now suggest a background vow. Set suggestedVow to a short imperative phrase based on the story (e.g. \"Find my lost sister\"). Do NOT ask more questions."
                        .formatted(exchangeCount)
                : "";
        memoryProvider.clear(campaignId);
        CreationResponse response = creationAssistant.guide(
                campaignId, character.name(),
                character.edge(), character.heart(), character.iron(),
                character.shadow(), character.wits(),
                journalContext, exchangeCount, lastPlayerInput,
                vowInstruction);

        String guideMessage = response.message() != null ? response.message() : "";
        if (!guideMessage.isBlank()) {
            journal.appendNarrative(campaignId, guideMessage);
        }

        return objectMapper.writeValueAsString(Map.of(
                "type", "creation_response",
                "message", guideMessage,
                "suggestedVow", response.suggestedVow()));
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
        String journalCtx = journal.getRecentJournal(campaignId, 60);
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
                // Creation flow
                case "creation_chat" -> handleCreationChat(msg);
                case "finalize_creation" -> handleFinalizeCreation(msg);
                // Gameplay flow
                case "narrative" -> handleNarrative(msg);
                case "move_result" -> handleMoveResult(msg);
                case "inspire" -> handleInspireMe();
                case "oracle" -> handleOracle(msg);
                case "oracle_manual" -> handleOracleManual(msg);
                case "progress_mark" -> handleProgressMark(msg);
                case "character_update" -> handleCharacterUpdate(msg);
                default -> errorJson("Unknown message type: " + type);
            };
        } catch (Exception e) {
            Log.errorf(e, "Error processing message for campaign: %s", campaignId);
            return errorJson(e.getMessage());
        }
    }

    // --- Creation flow ---

    private String handleInspire() throws Exception {
        AtomicBoolean lock = GENERATION_LOCKS.get(campaignId);
        if (lock != null && !lock.compareAndSet(false, true)) {
            return errorJson("Generation already in progress");
        }

        try {
            String sessionId = "inspire-" + campaignId;
            CreationResponse response = creationAssistant.inspire(sessionId);
            return objectMapper.writeValueAsString(Map.of(
                    "type", "inspire",
                    "text", response.message() != null ? response.message() : ""));
        } finally {
            if (lock != null) {
                lock.set(false);
            }
        }
    }

    private String handleCreationChat(JsonNode msg) throws Exception {
        String text = msg.path("text").asText();
        if (text.isBlank()) {
            return errorJson("Empty text");
        }

        AtomicBoolean lock = GENERATION_LOCKS.get(campaignId);
        if (lock != null && !lock.compareAndSet(false, true)) {
            return errorJson("Generation already in progress");
        }

        try {
            // Journal the player's input
            journal.appendNarrative(campaignId, "*Player: " + text + "*");

            CharacterSheet character = journal.readCharacter(campaignId);
            String journalContext = journal.getRecentJournal(campaignId, 30);
            int exchangeCount = countExchanges(journalContext);

            // Clear chat memory so the LLM only sees the current system+user message
            // (journal context is already included in the user message template)
            memoryProvider.clear(campaignId);

            String vowInstruction = exchangeCount >= 3
                    ? "IMPORTANT: The player has responded %d times. You MUST now suggest a background vow. Set suggestedVow to a short imperative phrase based on the story (e.g. \"Find my lost sister\"). Do NOT ask more questions."
                            .formatted(exchangeCount)
                    : "";

            CreationResponse response = creationAssistant.guide(
                    campaignId,
                    character.name(),
                    character.edge(), character.heart(), character.iron(),
                    character.shadow(), character.wits(),
                    journalContext,
                    exchangeCount,
                    text,
                    vowInstruction);

            // Journal the guide's response
            String guideMessage = response.message() != null ? response.message() : "";
            if (!guideMessage.isBlank()) {
                journal.appendNarrative(campaignId, guideMessage);
            }

            return objectMapper.writeValueAsString(Map.of(
                    "type", "creation_response",
                    "message", guideMessage,
                    "suggestedVow", response.suggestedVow()));
        } finally {
            if (lock != null) {
                lock.set(false);
            }
        }
    }

    private String handleFinalizeCreation(JsonNode msg) throws Exception {
        // Parse the finalized character data
        JsonNode charNode = msg.path("character");
        CharacterSheet character = new CharacterSheet(
                journal.readCharacter(campaignId).name(),
                charNode.path("edge").asInt(1),
                charNode.path("heart").asInt(1),
                charNode.path("iron").asInt(1),
                charNode.path("shadow").asInt(1),
                charNode.path("wits").asInt(1),
                5, 5, 5, 2,
                parseVows(charNode));

        // Update the character sheet in the journal (backstory conversation is already journaled)
        journal.updateCharacter(campaignId, character);

        // Send character update and transition to active play
        connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                "type", "character_update",
                "character", character)));

        return objectMapper.writeValueAsString(Map.of(
                "type", "creation_phase",
                "phase", "active"));
    }

    private List<Vow> parseVows(JsonNode charNode) {
        List<Vow> vows = new ArrayList<>();
        if (charNode.has("vows") && charNode.get("vows").isArray()) {
            for (JsonNode vowNode : charNode.get("vows")) {
                vows.add(new Vow(
                        vowNode.path("description").asText(),
                        Rank.valueOf(vowNode.path("rank").asText("DANGEROUS")),
                        vowNode.path("progress").asInt(0)));
            }
        }
        return vows;
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
            journal.appendNarrative(campaignId, "*Player: " + text + "*");

            CharacterSheet character = journal.readCharacter(campaignId);
            String charCtx = formatCharacterContext(character);
            String journalCtx = journal.getRecentJournal(campaignId, 100);
            String memoryCtx = storyMemory.relevantMemory(campaignId, text);

            PlayResponse response = assistant.narrate(campaignId, charCtx, journalCtx, memoryCtx, text);
            String narrative = OracleService.stripOracleLines(
                    JournalParser.sanitizeNarrative(response.narrative()));

            journal.appendNarrative(campaignId, narrative);

            return objectMapper.writeValueAsString(Map.of(
                    "type", "narrative",
                    "narrative", narrative,
                    "narrativeHtml", prettify.markdownToHtml(narrative),
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
            String journalCtx = journal.getRecentJournal(campaignId, 100);
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
            journal.appendNarrative(campaignId, "*Player: " + playerAction + "*");
        }

        // Journal the roll
        String moveName = moveKey.replace("_", " ");
        moveName = moveName.substring(0, 1).toUpperCase() + moveName.substring(1);
        journal.appendMechanical(campaignId,
                "**%s** (+%s): Action %d, Challenge %d|%d → **%s**".formatted(
                        moveName, stat, actionScore, challenge1, challenge2, outcome.display()));

        // Look up the rules text for this outcome
        String moveOutcomeText = mechanics.getMoveOutcomeText(categoryKey, moveKey, outcome);

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
            String journalCtx = journal.getRecentJournal(campaignId, 60);
            String memoryQuery = moveName + " " + outcome.display();
            String memoryCtx = storyMemory.relevantMemory(campaignId, memoryQuery);
            PlayResponse response = assistant.narrateMoveResult(
                    campaignId, moveName, outcome.display(),
                    actionScore, challenge1, challenge2,
                    moveOutcomeText, journalCtx, memoryCtx);
            String narrative = OracleService.stripOracleLines(
                    JournalParser.sanitizeNarrative(response.narrative()));

            journal.appendNarrative(campaignId, narrative);

            return objectMapper.writeValueAsString(Map.of(
                    "type", "narrative",
                    "narrative", narrative,
                    "narrativeHtml", prettify.markdownToHtml(narrative),
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
        return objectMapper.writeValueAsString(Map.of(
                "type", "oracle_result",
                "result", result));
    }

    private String handleOracleManual(JsonNode msg) throws Exception {
        String collectionKey = msg.path("collectionKey").asText();
        String tableKey = msg.path("tableKey").asText();
        int roll = msg.path("roll").asInt();
        OracleResult result = oracleService.rollOracleManual(collectionKey, tableKey, roll);
        journal.appendMechanical(campaignId, result.toJournalEntry());
        return objectMapper.writeValueAsString(Map.of(
                "type", "oracle_result",
                "result", result));
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

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", "error", "message", message));
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"Internal error\"}";
        }
    }
}
