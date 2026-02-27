package dev.ebullient.ironsworn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.chat.CreationAssistant;
import dev.ebullient.ironsworn.chat.CreationResponse;
import dev.ebullient.ironsworn.chat.MarkdownAugmenter;
import dev.ebullient.ironsworn.chat.PlayMemoryProvider;
import dev.ebullient.ironsworn.model.CharacterSheet;
import dev.ebullient.ironsworn.model.Rank;
import dev.ebullient.ironsworn.model.Vow;
import io.quarkus.websockets.next.WebSocketConnection;

/**
 * Handles the character creation flow, extracted from PlayWebSocket.
 * One instance per WebSocket session during the creation phase.
 */
public class CreationEngine {

    private final WebSocketConnection connection;
    private final GameJournal journal;
    private final CreationAssistant creationAssistant;
    private final PlayMemoryProvider memoryProvider;
    private final MarkdownAugmenter prettify;
    private final ObjectMapper objectMapper;
    private final String campaignId;
    private final AtomicBoolean generationLock;

    public CreationEngine(WebSocketConnection connection, GameJournal journal,
            CreationAssistant creationAssistant, PlayMemoryProvider memoryProvider,
            MarkdownAugmenter prettify, ObjectMapper objectMapper,
            String campaignId, AtomicBoolean generationLock) {
        this.connection = connection;
        this.journal = journal;
        this.creationAssistant = creationAssistant;
        this.memoryProvider = memoryProvider;
        this.prettify = prettify;
        this.objectMapper = objectMapper;
        this.campaignId = campaignId;
        this.generationLock = generationLock;
    }

    /**
     * Handle the creation phase opening — either fresh creation or resume.
     */
    public String handleOpen() throws Exception {
        // Send creation phase indicator
        connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                "type", "creation_phase",
                "phase", "creation")));

        String existingJournal = journal.getRecentJournal(campaignId, 100);
        if (existingJournal.isBlank()) {
            // Fresh creation — send fixed welcome greeting
            return sendWelcomeGreeting();
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

        // Re-engage the guide if the journal ends with unnarrated content
        // (player input or mechanical result like an oracle roll)
        if (!JournalParser.needsNarration(existingJournal)) {
            return objectMapper.writeValueAsString(Map.of("type", "ready"));
        }

        String lastPlayerInput = JournalParser.endsWithPlayerEntry(existingJournal)
                ? JournalParser.extractLastPlayerInput(existingJournal)
                : "Continue the conversation based on what just happened.";
        return reengageGuide(character, lastPlayerInput);
    }

    /**
     * Send the fixed welcome greeting (no LLM call).
     */
    private String sendWelcomeGreeting() throws Exception {
        String name = journal.readCharacter(campaignId).name();
        String greeting = """
                Welcome to the Ironlands. Let's work together to explore who %s is,
                the truths about their world, and determine the vow that sets them adventuring.

                Start by setting your character's stats, then roll some oracles
                or use your own words to determine the place and theme of this adventure.
                """
                .formatted(name);
        return objectMapper.writeValueAsString(Map.of(
                "type", "inspire-create",
                "text", greeting));
    }

    /**
     * Handle player chat input during creation.
     */
    public String handleChat(JsonNode msg) throws Exception {
        String text = msg.path("text").asText();
        if (text.isBlank()) {
            return errorJson("Empty text");
        }

        if (!acquireLock()) {
            return errorJson("Generation already in progress");
        }

        try {
            // Journal the player's input
            journal.appendNarrative(campaignId, formatPlayerInput(text));
            return callGuide(text);
        } finally {
            releaseLock();
        }
    }

    /**
     * Handle the creation inspire button — synthesize oracle results already in the journal.
     */
    public String handleInspire() throws Exception {
        if (!acquireLock()) {
            return errorJson("Generation already in progress");
        }

        try {
            CharacterSheet character = journal.readCharacter(campaignId);
            String name = character.name();
            return callGuide(
                    "Use the oracle results in the journal to suggest truths about %s's world and what drives them."
                            .formatted(name));
        } finally {
            releaseLock();
        }
    }

    /**
     * Handle finalizing creation — save character and transition to active play.
     */
    public String handleFinalize(JsonNode msg) throws Exception {
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

        journal.updateCharacter(campaignId, character);

        connection.sendTextAndAwait(objectMapper.writeValueAsString(Map.of(
                "type", "character_update",
                "character", character)));

        return objectMapper.writeValueAsString(Map.of(
                "type", "creation_phase",
                "phase", "active"));
    }

    // --- Private helpers ---

    private String callGuide(String playerInput) throws Exception {
        CharacterSheet character = journal.readCharacter(campaignId);
        String journalContext = journal.getRecentJournal(campaignId, 30);
        int exchangeCount = JournalParser.countExchanges(journalContext);

        memoryProvider.clear(campaignId);

        CreationResponse response = creationAssistant.guide(
                campaignId,
                character.name(),
                character.edge(), character.heart(), character.iron(),
                character.shadow(), character.wits(),
                journalContext,
                exchangeCount,
                playerInput,
                vowInstruction(exchangeCount));

        String guideMessage = response.message() != null ? response.message() : "";
        if (!guideMessage.isBlank()) {
            journal.appendNarrative(campaignId, guideMessage);
        }

        return creationResponseJson(guideMessage, response.suggestedVow());
    }

    private String reengageGuide(CharacterSheet character, String lastPlayerInput) throws Exception {
        String journalContext = journal.getRecentJournal(campaignId, 30);
        int exchangeCount = JournalParser.countExchanges(journalContext);

        memoryProvider.clear(campaignId);
        CreationResponse response = creationAssistant.guide(
                campaignId, character.name(),
                character.edge(), character.heart(), character.iron(),
                character.shadow(), character.wits(),
                journalContext, exchangeCount, lastPlayerInput,
                vowInstruction(exchangeCount));

        String guideMessage = response.message() != null ? response.message() : "";
        if (!guideMessage.isBlank()) {
            journal.appendNarrative(campaignId, guideMessage);
        }

        return creationResponseJson(guideMessage, response.suggestedVow());
    }

    private String vowInstruction(int exchangeCount) {
        if (exchangeCount >= 3) {
            return "IMPORTANT: The player has responded %d times. You MUST now suggest a background vow. Set suggestedVow to a short imperative phrase based on the story (e.g. \"Find my lost sister\"). Do NOT ask more questions."
                    .formatted(exchangeCount);
        }
        return "";
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

    private String formatPlayerInput(String text) {
        return "<player>\n" + text.strip() + "\n</player>";
    }

    private boolean acquireLock() {
        return generationLock == null || generationLock.compareAndSet(false, true);
    }

    private void releaseLock() {
        if (generationLock != null) {
            generationLock.set(false);
        }
    }

    private String creationResponseJson(String message, String suggestedVow) throws Exception {
        var map = Map.of(
                "type", "creation_response",
                "message", message,
                "suggestedVow", suggestedVow != null ? suggestedVow : "");
        return objectMapper.writeValueAsString(map);
    }

    private String errorJson(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", "error", "message", message));
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"Internal error\"}";
        }
    }
}
