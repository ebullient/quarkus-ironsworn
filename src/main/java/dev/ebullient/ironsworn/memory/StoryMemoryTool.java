package dev.ebullient.ironsworn.memory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;

/**
 * Tool that allows AI services to query story memory on demand.
 * Complements the pre-fetched {memoryContext} injected into each prompt —
 * use this when you need to recall something specific that may not be
 * in the pre-fetched slice (e.g., a particular NPC, location, or earlier event).
 */
@Singleton
public class StoryMemoryTool {

    @Inject
    StoryMemoryService storyMemory;

    @Inject
    PlayContext playContext;

    @Tool("""
            Retrieve relevant story memory for the current campaign.
            Use this when you need to recall specific earlier events, NPC details,
            locations, or established facts that go beyond what is already in context.
            Write a descriptive query about what you are trying to remember.
            Returns bullet-point excerpts from earlier journal entries, or empty string if none found.
            """)
    public String retrieveStoryMemory(String query) {
        String campaignId = playContext.get();
        if (campaignId == null) {
            return "";
        }
        Log.debugf("StoryMemoryTool query for campaign %s: %s", campaignId, query);
        return storyMemory.relevantMemory(campaignId, query);
    }
}
