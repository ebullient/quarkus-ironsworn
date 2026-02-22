package dev.ebullient.ironsworn.chat;

import dev.langchain4j.model.output.structured.Description;

public record InspireOracleChoice(
        @Description("Oracle collection key to roll: action_and_theme, character, place, settlement, turning_point") String collectionKey,
        @Description("Oracle table key within the chosen collection (see system instructions)") String tableKey,
        @Description("1-2 sentences explaining why this oracle fits (for debugging)") String reason) {
}
