package dev.ebullient.ironsworn.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import dev.ebullient.ironsworn.JournalParser;

class OracleResultTest {

    @Test
    void toJournalEntry_format() {
        OracleResult result = new OracleResult("Action", "Theme", 42, "Discovery");
        assertEquals("**Oracle** (Action / Theme): 42 → Discovery", result.toJournalEntry());
    }

    @Test
    void toJournalEntry_compatibleWithMechanicalDetection() {
        // When stored via appendMechanical, the journal entry gets a "> " prefix.
        // Verify the raw entry content is what we expect before prefixing.
        OracleResult result = new OracleResult("Character", "Role", 73, "Warrior");
        String journalLine = "> " + result.toJournalEntry();

        assertTrue(JournalParser.isMechanicalEntry(journalLine),
                "Oracle journal entry with blockquote prefix should be detected as mechanical");
    }

    @Test
    void toJournalEntry_containsArrow() {
        OracleResult result = new OracleResult("Place", "Descriptor", 15, "Ancient");
        assertTrue(result.toJournalEntry().contains("→"),
                "Journal entry should contain arrow character for client-side parsing");
    }
}
