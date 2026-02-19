package dev.ebullient.ironsworn.chat;

import dev.langchain4j.model.output.structured.Description;

public record CreationResponse(
        @Description("Your message to the player — questions, scene-setting, or vow suggestion. 1-2 paragraphs.") String message,
        @Description("A suggested background vow for the character, or null if you are still asking questions") String suggestedVow) {
}
