package dev.ebullient.ironsworn.journal;

/**
 * CDI event fired by GameJournal after a campaign journal file is deleted,
 * to allow downstream listeners to clean up associated state (e.g. embeddings).
 *
 * @param campaignId the deleted campaign's ID
 */
public record CampaignDeletedEvent(String campaignId) {
}
