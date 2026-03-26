package dev.ebullient.ironsworn.memory;

import jakarta.inject.Singleton;

/**
 * Holds the current campaign ID for the duration of an AI service call.
 * Set by calling sites (PlayWebSocket, CampaignResource) before invoking
 * an AI service, so that StoryMemoryTool can read it without requiring
 * the LLM to pass it as a parameter.
 *
 * Java 21 virtual threads have their own ThreadLocal storage, making this
 * safe for use with @RunOnVirtualThread handlers.
 */
@Singleton
public class PlayContext {
    private static final ThreadLocal<String> CAMPAIGN = new ThreadLocal<>();

    public void set(String campaignId) {
        CAMPAIGN.set(campaignId);
    }

    public String get() {
        return CAMPAIGN.get();
    }
}
