package dev.ebullient.ironsworn.chat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.ebullient.ironsworn.GameJournal;
import dev.ebullient.ironsworn.IronswornMechanics;
import dev.ebullient.ironsworn.JournalParser;
import dev.ebullient.ironsworn.model.OracleResult;
import io.quarkus.logging.Log;

@ApplicationScoped
public class OracleService {

    @Inject
    IronswornMechanics mechanics;

    @Inject
    InspireOracleSelector oracleSelector;

    @Inject
    InspireToolAssistant inspireToolAssistant;

    @Inject
    PlayAssistant assistant;

    @Inject
    GameJournal journal;

    @Inject
    PlayMemoryProvider memoryProvider;

    @ConfigProperty(name = "ironsworn.oracle.use-tool-calling", defaultValue = "false")
    boolean useToolCalling;

    /**
     * Roll on an oracle table (server generates the roll).
     */
    public OracleResult rollOracle(String collectionKey, String tableKey) {
        return mechanics.rollOracle(collectionKey, tableKey);
    }

    /**
     * Look up an oracle result for a player-provided roll (physical dice).
     */
    public OracleResult rollOracleManual(String collectionKey, String tableKey, int roll) {
        return mechanics.lookupOracleResult(collectionKey, tableKey, roll);
    }

    /**
     * Orchestrate the full "Inspire Me" flow: oracle selection/rolling + narration.
     * Delegates to either the tool-calling or non-tool-calling path based on config.
     */
    public InspireResult inspireMe(String campaignId, String charCtx, String journalCtx, String memoryCtx) {
        if (useToolCalling) {
            return inspireMeWithTools(campaignId, charCtx, journalCtx, memoryCtx);
        }
        return inspireMeWithSelector(campaignId, charCtx, journalCtx, memoryCtx);
    }

    /**
     * Non-tool-calling path: InspireOracleSelector picks the table, server rolls,
     * then PlayAssistant.inspire() narrates with the oracle already in the journal.
     */
    private InspireResult inspireMeWithSelector(String campaignId, String charCtx,
            String journalCtx, String memoryCtx) {
        // Let the model choose WHICH oracle to roll, then roll it server-side.
        InspireOracleChoice choice = null;
        try {
            choice = oracleSelector.chooseForInspiration(campaignId, charCtx, journalCtx, memoryCtx);
        } catch (Exception e) {
            Log.warnf(e, "Failed to choose oracle; using turning_point");
        }

        String collectionKey = InspireOracleSelector.normalizeOracleKey(choice != null ? choice.collectionKey() : null);
        String tableKey = InspireOracleSelector.normalizeOracleTable(collectionKey,
                choice != null ? choice.tableKey() : null);
        if (choice != null && choice.reason() != null && !choice.reason().isBlank()) {
            Log.debugf("%s: Inspire oracle choice %s/%s (%s)", campaignId, collectionKey, tableKey, choice.reason());
        } else {
            Log.debugf("%s: Inspire oracle choice %s/%s", campaignId, collectionKey, tableKey);
        }

        OracleResult oracle = mechanics.rollOracle(collectionKey, tableKey);
        journal.appendMechanical(campaignId, oracle.toJournalEntry());

        // Refresh journal context so the inspire prompt includes the oracle that was just rolled.
        String refreshedJournalCtx = journal.getRecentJournal(campaignId, 100);
        String inspireJournalCtx = buildInspireJournalContext(refreshedJournalCtx);

        // Clear chat memory so the LLM relies on the current system+user prompt.
        memoryProvider.clear(campaignId);
        PlayResponse response = assistant.inspire(campaignId, oracle.toJournalEntry(), charCtx, inspireJournalCtx,
                memoryCtx);
        String narrative = stripOracleLines(JournalParser.sanitizeNarrative(response.narrative()));
        journal.appendNarrative(campaignId, narrative);

        return new InspireResult(oracle, response, narrative);
    }

    /**
     * Tool-calling path: InspireToolAssistant uses OracleTool to pick and roll
     * oracle(s) via LLM tool calling, then narrates.
     */
    private InspireResult inspireMeWithTools(String campaignId, String charCtx,
            String journalCtx, String memoryCtx) {
        String inspireJournalCtx = buildInspireJournalContext(journalCtx);

        // Clear chat memory so the LLM relies on the current system+user prompt.
        memoryProvider.clear(campaignId);
        // InspireToolAssistant returns String (not PlayResponse) to avoid JSON format
        // constraint that prevents Ollama from emitting tool calls.
        String rawResponse = inspireToolAssistant.inspire(campaignId, charCtx, inspireJournalCtx, memoryCtx);
        // Preserve tool-produced mechanical lines (e.g. "> **Oracle** ...") so they are:
        // - sent to the client as part of the narrative
        // - journaled in-line with the narrative
        String narrative = JournalParser.sanitizeNarrative(rawResponse);
        journal.appendNarrative(campaignId, narrative);

        PlayResponse response = new PlayResponse(narrative, List.of(), "");
        return new InspireResult(null, response, narrative);
    }

    // -- Context building helpers (moved from PlayWebSocket) --

    String buildInspireJournalContext(String journalCtx) {
        if (journalCtx == null || journalCtx.isBlank()) {
            return "";
        }
        List<JournalParser.JournalExchange> exchanges = JournalParser.parseExchanges(journalCtx);

        String sceneAnchor = extractSceneAnchor(exchanges);
        String recent = exchanges.size() <= 3
                ? journalCtx
                : joinLastExchanges(exchanges, 3);

        if (sceneAnchor.isBlank()) {
            return recent;
        }
        return """
                SCENE ANCHOR (continue from here; do not change time/place):
                %s

                RECENT JOURNAL:
                %s
                """.formatted(sceneAnchor, recent).trim();
    }

    private String joinLastExchanges(List<JournalParser.JournalExchange> exchanges, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, exchanges.size() - n); i < exchanges.size(); i++) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(exchanges.get(i).content());
        }
        return sb.toString().trim();
    }

    String extractSceneAnchor(List<JournalParser.JournalExchange> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            return "";
        }

        for (int i = exchanges.size() - 1; i >= 0; i--) {
            String content = exchanges.get(i).content();
            if (content == null || content.isBlank()) {
                continue;
            }

            List<String> narrativeLines = content.lines()
                    .map(String::trim)
                    .filter(l -> !l.isBlank())
                    .filter(l -> !JournalParser.isMechanicalEntry(l))
                    .filter(l -> !JournalParser.isPlayerEntry(l))
                    .toList();

            if (narrativeLines.isEmpty()) {
                continue;
            }

            String narrative = String.join("\n", narrativeLines).trim();
            if (!narrative.isBlank()) {
                String[] paras = narrative.split("\\n\\s*\\n");
                for (int p = paras.length - 1; p >= 0; p--) {
                    String para = paras[p].trim();
                    if (!para.isBlank()) {
                        return para;
                    }
                }
                return narrative;
            }
        }
        return "";
    }

    public static String stripOracleLines(String narrative) {
        if (narrative == null || narrative.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (String line : narrative.split("\n")) {
            String trimmed = line.trim();
            String withoutBlockquote = trimmed.startsWith(">")
                    ? trimmed.substring(1).stripLeading()
                    : trimmed;
            String withoutListMarker = withoutBlockquote.startsWith("- ")
                    ? withoutBlockquote.substring(2).stripLeading()
                    : withoutBlockquote;
            if (!withoutListMarker.startsWith("**Oracle**") && !withoutListMarker.startsWith("**Oracle:**")) {
                cleaned.append(line).append("\n");
            }
        }
        return cleaned.toString().trim();
    }
}
