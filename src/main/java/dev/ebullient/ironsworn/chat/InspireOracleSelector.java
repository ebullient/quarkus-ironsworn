package dev.ebullient.ironsworn.chat;

import java.util.List;
import java.util.Map;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@OutputGuardrails(InspireOracleChoiceGuardrail.class)
public interface InspireOracleSelector {

    static final Map<String, List<String>> INSPIRE_ORACLE_TABLES = Map.of(
            "action_and_theme", List.of("action", "theme"),
            "character", List.of("descriptor", "role", "goal", "disposition"),
            "place", List.of("location", "descriptor"),
            "settlement", List.of("name", "trouble"),
            // See rules/oracles/turning_point.yaml: these are the rollable tables within the turning_point collection.
            "turning_point", List.of("major_plot_twist", "combat_action", "mystic_backlash"));

    @SystemMessage(fromResource = "prompts/play-chooseOracle-system.txt")
    @UserMessage(fromResource = "prompts/play-chooseOracle-user.txt")
    InspireOracleChoice chooseForInspiration(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext);

    public static String normalizeOracleKey(String key) {
        if (key == null) {
            return "turning_point";
        }
        String trimmed = key.trim();
        return INSPIRE_ORACLE_TABLES.containsKey(trimmed) ? trimmed : "turning_point";
    }

    public static String normalizeOracleTable(String collectionKey, String tableKey) {
        List<String> validTables = INSPIRE_ORACLE_TABLES.get(collectionKey);
        if (validTables == null || validTables.isEmpty()) {
            validTables = INSPIRE_ORACLE_TABLES.get("turning_point");
        }
        if (tableKey == null) {
            return validTables.getFirst();
        }
        String trimmed = tableKey.trim();
        return validTables.contains(trimmed) ? trimmed : validTables.getFirst();
    }
}
