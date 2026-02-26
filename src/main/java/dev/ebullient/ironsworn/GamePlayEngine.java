package dev.ebullient.ironsworn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.chat.InspireResult;
import dev.ebullient.ironsworn.chat.MarkdownAugmenter;
import dev.ebullient.ironsworn.chat.OracleService;
import dev.ebullient.ironsworn.chat.PlayAssistant;
import dev.ebullient.ironsworn.chat.PlayMemoryProvider;
import dev.ebullient.ironsworn.chat.PlayResponse;
import dev.ebullient.ironsworn.memory.StoryMemoryService;
import dev.ebullient.ironsworn.model.ActionRollResult;
import dev.ebullient.ironsworn.model.CharacterSheet;
import dev.ebullient.ironsworn.model.OracleResult;
import dev.ebullient.ironsworn.model.Outcome;
import dev.ebullient.ironsworn.model.Vow;

@ApplicationScoped
public class GamePlayEngine {

    @Inject
    GameJournal journal;

    @Inject
    PlayAssistant assistant;

    @Inject
    OracleService oracleService;

    @Inject
    PlayMemoryProvider memoryProvider;

    @Inject
    IronswornMechanics mechanics;

    @Inject
    MarkdownAugmenter prettify;

    @Inject
    StoryMemoryService storyMemory;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Handle WebSocket open during active play phase.
     * Replays journal, sends character update, and re-engages narration if needed.
     */
    public String handleOpen(EngineContext ctx) throws Exception {
        CharacterSheet character = journal.readCharacter(ctx.campaignId());

        // Replay recent journal for active play as pre-rendered blocks
        String existingJournal = journal.getRecentJournal(ctx.campaignId(), 100);
        if (!existingJournal.isBlank()) {
            var blocks = JournalParser.parseToBlocks(existingJournal, prettify);
            ctx.emitter().emit(Map.of(
                    "type", "play_resume",
                    "blocks", blocks));
        }

        ctx.emitter().emit(Map.of(
                "type", "character_update",
                "character", character));

        // If the last journal entry needs narration (player input or move result), re-engage
        if (JournalParser.needsNarration(existingJournal)) {
            return reengageNarration(ctx, character, existingJournal);
        }

        return objectMapper.writeValueAsString(Map.of("type", "ready"));
    }

    /**
     * Handle player narrative input. Called with the generation lock already acquired.
     */
    public String handleNarrative(EngineContext ctx, JsonNode msg) throws Exception {
        String text = msg.path("text").asText();
        if (text.isBlank()) {
            return ctx.emitter().errorJson("Empty narrative text");
        }

        journal.appendNarrative(ctx.campaignId(), JournalParser.formatPlayerInput(text));

        CharacterSheet character = journal.readCharacter(ctx.campaignId());
        String charCtx = formatCharacterContext(character);
        String journalCtx = journal.getRecentJournal(ctx.campaignId(), 60);
        String memoryCtx = storyMemory.relevantMemory(ctx.campaignId(), text);

        memoryProvider.clear(ctx.campaignId());
        ctx.emitter().delta("The oracle speaks\u2026");
        PlayResponse response = assistant.narrate(ctx.campaignId(), charCtx, journalCtx, memoryCtx, text);
        String narrative = OracleService.stripOracleLines(
                JournalParser.sanitizeNarrative(response.narrative()));

        journal.appendNarrative(ctx.campaignId(), narrative);

        return narrativeResponse(narrative, response);
    }

    /**
     * Handle move result: journal the roll, send oracle/outcome, then narrate.
     * Called with the generation lock already acquired.
     */
    public String handleMoveResult(EngineContext ctx, JsonNode msg) throws Exception {
        String categoryKey = msg.path("categoryKey").asText();
        String moveKey = msg.path("moveKey").asText();
        String stat = msg.path("stat").asText();
        int statValue = msg.path("statValue").asInt();
        int adds = msg.path("adds").asInt(0);
        int actionDie = msg.path("actionDie").asInt();
        int challenge1 = msg.path("challenge1").asInt();
        int challenge2 = msg.path("challenge2").asInt();
        int actionScore = msg.path("actionScore").asInt();
        String outcomeStr = msg.path("outcome").asText();
        Outcome outcome = Outcome.valueOf(outcomeStr);

        // Journal the player's action description (if provided) before the roll
        String playerAction = msg.path("playerAction").asText("").trim();
        if (!playerAction.isEmpty()) {
            journal.appendNarrative(ctx.campaignId(), JournalParser.formatPlayerInput(playerAction));
        }

        // Journal the roll
        String moveName = moveKey.replace("_", " ");
        moveName = moveName.substring(0, 1).toUpperCase() + moveName.substring(1);
        ActionRollResult roll = new ActionRollResult(
                moveName, stat, statValue, adds, actionDie, actionScore, challenge1, challenge2, outcome);
        journal.appendMechanical(ctx.campaignId(), roll.toJournalEntry());

        // Look up the rules text for this outcome
        String moveOutcomeText = mechanics.getMoveOutcomeText(categoryKey, moveKey, outcome);

        // If the outcome text mentions "Pay the Price", roll that oracle automatically
        if (moveOutcomeText.contains("Pay the Price")) {
            OracleResult ptpOracle = oracleService.rollOracle("moves", "pay_the_price");
            journal.appendMechanical(ctx.campaignId(), ptpOracle.toJournalEntry());

            ctx.emitter().emit(Map.of(
                    "type", "oracle_result",
                    "result", ptpOracle));

            moveOutcomeText += "\n\n**Pay the Price result**: " + ptpOracle.resultText();
        }

        // Send the rules text immediately
        ctx.emitter().emit(Map.of(
                "type", "move_outcome",
                "moveName", moveName,
                "moveOutcomeText", StringUtils.mdToHtml(moveOutcomeText).getValue()));

        // Now get LLM narration
        String journalCtx = journal.getRecentJournal(ctx.campaignId(), 20);

        memoryProvider.clear(ctx.campaignId());
        ctx.emitter().delta("Narrating the outcome\u2026");
        PlayResponse response = assistant.narrateMoveResult(
                ctx.campaignId(), moveName, outcome.display(),
                actionScore, challenge1, challenge2,
                moveOutcomeText, journalCtx, "");
        String narrative = OracleService.stripOracleLines(
                JournalParser.sanitizeNarrative(response.narrative()));

        journal.appendNarrative(ctx.campaignId(), narrative);

        return narrativeResponse(narrative, response);
    }

    /**
     * Handle "inspire me" — roll an oracle and narrate. Called with the generation lock already acquired.
     */
    public String handleInspireMe(EngineContext ctx) throws Exception {
        CharacterSheet character = journal.readCharacter(ctx.campaignId());
        String charCtx = formatCharacterContext(character);
        String journalCtx = journal.getRecentJournal(ctx.campaignId(), 60);
        // Use recent journal text as the query so memory retrieval finds relevant past context
        String[] lines = journalCtx.split("\n");
        String memoryQuery = String.join("\n",
                java.util.Arrays.copyOfRange(lines, Math.max(0, lines.length - 10), lines.length));
        String memoryCtx = storyMemory.relevantMemory(ctx.campaignId(), memoryQuery);

        ctx.emitter().delta("Seeking inspiration\u2026");
        InspireResult result = oracleService.inspireMe(ctx.campaignId(), charCtx, journalCtx, memoryCtx);

        // Send oracle result to client if one was rolled server-side (non-tool-calling path)
        if (result.oracleResult() != null) {
            ctx.emitter().emit(Map.of(
                    "type", "oracle_result",
                    "result", result.oracleResult()));
        }

        PlayResponse response = result.response();
        return objectMapper.writeValueAsString(Map.of(
                "type", "narrative",
                "narrative", result.narrative(),
                "narrativeHtml", prettify.markdownToHtml(result.narrative()),
                "blocks", blocksForNarrative(result.narrative()),
                "npcs", response.npcs() != null ? response.npcs() : java.util.List.of(),
                "location", response.location() != null ? response.location() : ""));
    }

    public String handleOracle(EngineContext ctx, JsonNode msg) throws Exception {
        String collectionKey = msg.path("collectionKey").asText();
        String tableKey = msg.path("tableKey").asText();
        OracleResult result = oracleService.rollOracle(collectionKey, tableKey);
        journal.appendMechanical(ctx.campaignId(), result.toJournalEntry());
        return objectMapper.writeValueAsString(Map.of(
                "type", "oracle_result",
                "result", result));
    }

    public String handleOracleManual(EngineContext ctx, JsonNode msg) throws Exception {
        String collectionKey = msg.path("collectionKey").asText();
        String tableKey = msg.path("tableKey").asText();
        int roll = msg.path("roll").asInt();
        OracleResult result = oracleService.rollOracleManual(collectionKey, tableKey, roll);
        journal.appendMechanical(ctx.campaignId(), result.toJournalEntry());
        return objectMapper.writeValueAsString(Map.of(
                "type", "oracle_result",
                "result", result));
    }

    public String handleProgressMark(EngineContext ctx, JsonNode msg) throws Exception {
        int vowIndex = msg.path("vowIndex").asInt();
        CharacterSheet character = journal.readCharacter(ctx.campaignId());

        if (vowIndex < 0 || vowIndex >= character.vows().size()) {
            return ctx.emitter().errorJson("Invalid vow index: " + vowIndex);
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

        journal.updateCharacter(ctx.campaignId(), updated);
        return objectMapper.writeValueAsString(Map.of(
                "type", "character_update",
                "character", updated));
    }

    public String handleCharacterUpdate(EngineContext ctx, JsonNode msg) throws Exception {
        CharacterSheet character = objectMapper.treeToValue(msg.path("character"), CharacterSheet.class);
        journal.updateCharacter(ctx.campaignId(), character);
        return objectMapper.writeValueAsString(Map.of(
                "type", "character_update",
                "character", character));
    }

    public String handleBacktrack(EngineContext ctx, JsonNode msg) throws Exception {
        int blockIndex = msg.path("blockIndex").asInt(-1);
        if (blockIndex < 0) {
            return ctx.emitter().errorJson("Invalid block index");
        }
        journal.truncateJournal(ctx.campaignId(), blockIndex);
        memoryProvider.clear(ctx.campaignId());
        return objectMapper.writeValueAsString(Map.of("type", "backtrack_done"));
    }

    private String reengageNarration(EngineContext ctx, CharacterSheet character, String existingJournal)
            throws Exception {
        String charCtx = formatCharacterContext(character);
        String journalCtx = journal.getRecentJournal(ctx.campaignId(), 30);
        String resumePrompt = JournalParser.endsWithPlayerEntry(existingJournal)
                ? JournalParser.extractLastPlayerInput(existingJournal)
                : "Continue the story based on what just happened.";
        String memoryCtx = storyMemory.relevantMemory(ctx.campaignId(), resumePrompt);

        ctx.emitter().delta("Picking up the story\u2026");
        PlayResponse response = assistant.narrate(ctx.campaignId(), charCtx, journalCtx, memoryCtx, resumePrompt);
        String narrative = OracleService.stripOracleLines(
                JournalParser.sanitizeNarrative(response.narrative()));
        journal.appendNarrative(ctx.campaignId(), narrative);

        return narrativeResponse(narrative, response);
    }

    private String narrativeResponse(String narrative, PlayResponse response) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "type", "narrative",
                "narrative", narrative,
                "narrativeHtml", prettify.markdownToHtml(narrative),
                "blocks", blocksForNarrative(narrative),
                "npcs", response.npcs() != null ? response.npcs() : java.util.List.of(),
                "location", response.location() != null ? response.location() : ""));
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
}
