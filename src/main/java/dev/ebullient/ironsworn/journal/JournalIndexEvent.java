package dev.ebullient.ironsworn.journal;

/**
 * CDI event fired by GameJournal after a write operation to signal that
 * the story memory index should be updated.
 *
 * @param campaignId the campaign whose journal was written
 * @param immediate true for new-campaign warm-up (no debounce), false for
 *        normal append/edit (debounced)
 */
public record JournalIndexEvent(String campaignId, boolean immediate) {
}
