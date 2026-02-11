package dev.ebullient.ironsworn.chat;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

public record JsonChatResponse(
        @Description("Your answer in markdown format") String response,
        @Description("List of referenced sources") List<String> sources) {
}
