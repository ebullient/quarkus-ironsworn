package dev.ebullient.ironsworn.model;

public record ActionRollResult(
        String moveName,
        String stat,
        int statValue,
        int adds,
        int actionDie,
        int actionScore,
        int challenge1,
        int challenge2,
        Outcome outcome) {

    public String toJournalEntry() {
        return "**%s** (+%s %d): Action %d+%d=%d, Challenge %d|%d → **%s**".formatted(
                moveName, stat, statValue,
                actionDie, statValue + adds, actionScore,
                challenge1, challenge2,
                outcome.display());
    }
}
