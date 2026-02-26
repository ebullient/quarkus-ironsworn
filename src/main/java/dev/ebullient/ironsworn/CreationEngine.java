package dev.ebullient.ironsworn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.chat.CreationAssistant;
import dev.ebullient.ironsworn.chat.CreationResponse;
import dev.ebullient.ironsworn.chat.MarkdownAugmenter;
import dev.ebullient.ironsworn.chat.PlayMemoryProvider;
import dev.ebullient.ironsworn.model.CharacterSheet;
import dev.ebullient.ironsworn.model.Rank;
import dev.ebullient.ironsworn.model.Vow;

@ApplicationScoped
public class CreationEngine {

    @Inject
    GameJournal journal;

    @Inject
    CreationAssistant creationAssistant;

    @Inject
    PlayMemoryProvider memoryProvider;

    @Inject
    MarkdownAugmenter prettify;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Handle WebSocket open during creation phase.
     * Sends creation_phase indicator, then either inspires (fresh) or resumes (existing journal).
     */
    public String handleOpen(EngineContext ctx) throws Exception {
        ctx.emitter().emit(Map.of(
                "type", "creation_phase",
                "phase", "creation"));

        String existingJournal = journal.getRecentJournal(ctx.campaignId(), 100);
        if (existingJournal.isBlank()) {
            ctx.emitter().delta("Preparing your inspiration\u2026");
            return inspire(ctx);
        }

        // Replay existing conversation to the client as pre-rendered blocks
        var blocks = JournalParser.parseToBlocks(existingJournal, prettify);
        ctx.emitter().emit(Map.of(
                "type", "creation_resume",
                "blocks", blocks));
        CharacterSheet character = journal.readCharacter(ctx.campaignId());
        ctx.emitter().emit(Map.of(
                "type", "creation_ready",
                "character", character));

        // Re-engage the guide with the last player message
        String lastPlayerInput = JournalParser.extractLastPlayerInput(existingJournal);
        if (lastPlayerInput == null) {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "creation_ready",
                    "character", character));
        }

        ctx.emitter().delta("Resuming your session\u2026");
        return reengageGuide(ctx, character, lastPlayerInput);
    }

    /**
     * Generate inspiration text with name and stats awareness.
     * Called with the generation lock already acquired.
     */
    public String inspire(EngineContext ctx) throws Exception {
        CharacterSheet character = journal.readCharacter(ctx.campaignId());
        String statsSummary;
        if (character.hasDefaultStats()) {
            statsSummary = "Stats have NOT been set yet.";
        } else {
            statsSummary = "Stats: Edge %d, Heart %d, Iron %d, Shadow %d, Wits %d.".formatted(
                    character.edge(), character.heart(), character.iron(),
                    character.shadow(), character.wits());
        }

        String sessionId = "inspire-" + ctx.campaignId();
        CreationResponse response = creationAssistant.inspire(sessionId, character.name(), statsSummary);
        return objectMapper.writeValueAsString(Map.of(
                "type", "inspire-create",
                "text", response.message() != null ? response.message() : ""));
    }

    /**
     * Handle player chat during backstory creation.
     * Called with the generation lock already acquired.
     */
    public String handleChat(EngineContext ctx, JsonNode msg) throws Exception {
        String text = msg.path("text").asText();
        if (text.isBlank()) {
            return ctx.emitter().errorJson("Empty text");
        }

        // Journal the player's input
        journal.appendNarrative(ctx.campaignId(), JournalParser.formatPlayerInput(text));

        CharacterSheet character = journal.readCharacter(ctx.campaignId());
        String journalContext = journal.getRecentJournal(ctx.campaignId(), 30);
        int exchangeCount = JournalParser.countExchanges(journalContext);

        // Clear chat memory so the LLM only sees the current system+user message
        memoryProvider.clear(ctx.campaignId());

        String vowInstruction = exchangeCount >= 3
                ? "IMPORTANT: The player has responded %d times. You MUST now suggest a background vow. Set suggestedVow to a short imperative phrase based on the story (e.g. \"Find my lost sister\"). Do NOT ask more questions."
                        .formatted(exchangeCount)
                : "";

        ctx.emitter().delta("The guide is thinking\u2026");

        CreationResponse response = creationAssistant.guide(
                ctx.campaignId(),
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
            journal.appendNarrative(ctx.campaignId(), guideMessage);
        }

        return objectMapper.writeValueAsString(Map.of(
                "type", "creation_response",
                "message", guideMessage,
                "suggestedVow", response.suggestedVow()));
    }

    /**
     * Finalize character creation: persist stats, vows, and transition to active play.
     */
    public String handleFinalize(EngineContext ctx, JsonNode msg) throws Exception {
        JsonNode charNode = msg.path("character");
        CharacterSheet character = new CharacterSheet(
                journal.readCharacter(ctx.campaignId()).name(),
                charNode.path("edge").asInt(1),
                charNode.path("heart").asInt(1),
                charNode.path("iron").asInt(1),
                charNode.path("shadow").asInt(1),
                charNode.path("wits").asInt(1),
                5, 5, 5, 2,
                parseVows(charNode));

        journal.updateCharacter(ctx.campaignId(), character);

        ctx.emitter().emit(Map.of(
                "type", "character_update",
                "character", character));

        return objectMapper.writeValueAsString(Map.of(
                "type", "creation_phase",
                "phase", "active"));
    }

    /**
     * Handle creation-phase slash commands.
     *
     * @return JSON response string, or null if the command is not creation-specific
     */
    public String handleSlashCommand(EngineContext ctx, String command) throws Exception {
        if ("reset-stats".equals(command)) {
            CharacterSheet current = journal.readCharacter(ctx.campaignId());
            CharacterSheet reset = CharacterSheet.defaults(current.name());
            journal.updateCharacter(ctx.campaignId(), reset);
            return objectMapper.writeValueAsString(Map.of(
                    "type", "slash_command_result",
                    "command", "reset-stats",
                    "character", reset));
        }
        return null;
    }

    private String reengageGuide(EngineContext ctx, CharacterSheet character, String lastPlayerInput)
            throws Exception {
        String journalContext = journal.getRecentJournal(ctx.campaignId(), 30);
        int exchangeCount = JournalParser.countExchanges(journalContext);
        String vowInstruction = exchangeCount >= 3
                ? "IMPORTANT: The player has responded %d times. You MUST now suggest a background vow. Set suggestedVow to a short imperative phrase based on the story (e.g. \"Find my lost sister\"). Do NOT ask more questions."
                        .formatted(exchangeCount)
                : "";
        memoryProvider.clear(ctx.campaignId());
        ctx.emitter().delta("The guide is thinking\u2026");
        CreationResponse response = creationAssistant.guide(
                ctx.campaignId(), character.name(),
                character.edge(), character.heart(), character.iron(),
                character.shadow(), character.wits(),
                journalContext, exchangeCount, lastPlayerInput,
                vowInstruction);

        String guideMessage = response.message() != null ? response.message() : "";
        if (!guideMessage.isBlank()) {
            journal.appendNarrative(ctx.campaignId(), guideMessage);
        }

        return objectMapper.writeValueAsString(Map.of(
                "type", "creation_response",
                "message", guideMessage,
                "suggestedVow", response.suggestedVow()));
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
}
