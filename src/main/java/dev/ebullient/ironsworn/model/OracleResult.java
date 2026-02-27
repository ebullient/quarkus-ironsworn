package dev.ebullient.ironsworn.model;

import dev.ebullient.ironsworn.StringUtils;

public record OracleResult(
        String collectionName,
        String tableName,
        int roll,
        String resultText,
        String htmlResultText) {

    public OracleResult(String collectionName, String tableName, int roll, String resultText) {
        this(collectionName, tableName, roll, resultText, StringUtils.mdToHtml(resultText, true).getValue());
    }

    public String toJournalEntry() {
        return "**Oracle** (%s / %s): %d → %s".formatted(
                collectionName, tableName, roll, resultText);
    }
}
