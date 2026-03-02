package dev.ebullient.ironsworn.chat;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

public record PlayResponse(
        @Description("Your narrative text in markdown format. 2-4 paragraphs of vivid, immersive prose.") String narrative,
        @Description("NPCs active in the current scene") List<String> npcs,
        @Description("Current location name") String location,
        @Description("3 short suggestions for what the player could do next. Each should be 1 sentence of player action, not narration.") List<String> choices) {
}
