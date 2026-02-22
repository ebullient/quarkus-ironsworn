package dev.ebullient.ironsworn.chat;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

public record CampaignResponse(
        @Description("Your answer in markdown format") String response,
        @Description("List of context sources used: 'campaign journal', 'story memory', or 'general knowledge'") List<String> sources) {
}
