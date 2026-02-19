package dev.ebullient.ironsworn.model;

public record OracleResult(
        String collectionName,
        String tableName,
        int roll,
        String resultText) {

    public String toJournalEntry() {
        return "**Oracle** (%s / %s): %d → %s".formatted(
                collectionName, tableName, roll, resultText);
    }
}
