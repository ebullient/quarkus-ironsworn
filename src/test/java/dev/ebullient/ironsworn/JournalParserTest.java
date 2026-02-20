package dev.ebullient.ironsworn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JournalParserTest {

    // --- isPlayerEntry ---

    @Test
    void isPlayerEntry_validEntry() {
        assertTrue(JournalParser.isPlayerEntry("*Player: I draw my sword*"));
    }

    @Test
    void isPlayerEntry_emptyPlayerText() {
        assertTrue(JournalParser.isPlayerEntry("*Player: *"));
    }

    @Test
    void isPlayerEntry_narrativeText() {
        assertFalse(JournalParser.isPlayerEntry("The wind howls across the moor."));
    }

    @Test
    void isPlayerEntry_mechanicalEntry() {
        assertFalse(JournalParser.isPlayerEntry("> **Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**"));
    }

    @Test
    void isPlayerEntry_italicButNotPlayer() {
        assertFalse(JournalParser.isPlayerEntry("*This is just italic text*"));
    }

    // --- isMechanicalEntry ---

    @Test
    void isMechanicalEntry_moveResult() {
        assertTrue(JournalParser.isMechanicalEntry(
                "> **Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**"));
    }

    @Test
    void isMechanicalEntry_oracleResult() {
        assertTrue(JournalParser.isMechanicalEntry(
                "> **Oracle** (Action / Theme): 42 → Discovery"));
    }

    @Test
    void isMechanicalEntry_weakHit() {
        assertTrue(JournalParser.isMechanicalEntry(
                "> **Secure an Advantage** (+wits): Action 4, Challenge 6|2 → **Weak hit**"));
    }

    @Test
    void isMechanicalEntry_miss() {
        assertTrue(JournalParser.isMechanicalEntry(
                "> **Strike** (+iron): Action 3, Challenge 8|9 → **Miss**"));
    }

    @Test
    void isMechanicalEntry_narrativeText() {
        assertFalse(JournalParser.isMechanicalEntry("The blade finds its mark."));
    }

    @Test
    void isMechanicalEntry_playerEntry() {
        assertFalse(JournalParser.isMechanicalEntry("*Player: I attack the beast*"));
    }

    @Test
    void isMechanicalEntry_boldWithoutBlockquote() {
        // Bold text without blockquote prefix is NOT a mechanical entry
        assertFalse(JournalParser.isMechanicalEntry("**Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**"));
    }

    // --- needsNarration ---

    @Test
    void needsNarration_endsWithPlayerEntry() {
        String journal = """
                The story begins...

                *Player: I head north toward the ruins*
                """;
        assertTrue(JournalParser.needsNarration(journal));
    }

    @Test
    void needsNarration_endsWithMechanicalEntry() {
        String journal = """
                *Player: I try to dodge the trap*

                > **Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**
                """;
        assertTrue(JournalParser.needsNarration(journal));
    }

    @Test
    void needsNarration_endsWithNarrative() {
        String journal = """
                *Player: I draw my sword*

                The blade gleams in the torchlight. Your adversary takes a step back,
                eyes widening at the sight of the iron weapon.
                """;
        assertFalse(JournalParser.needsNarration(journal));
    }

    @Test
    void needsNarration_endsWithOracleResult() {
        String journal = """
                Some narrative here.

                > **Oracle** (Action / Theme): 42 → Discovery
                """;
        assertTrue(JournalParser.needsNarration(journal));
    }

    @Test
    void needsNarration_emptyJournal() {
        assertFalse(JournalParser.needsNarration(""));
    }

    @Test
    void needsNarration_blankLines() {
        assertFalse(JournalParser.needsNarration("\n\n\n"));
    }

    @Test
    void needsNarration_trailingBlankLinesAfterPlayer() {
        String journal = "*Player: I search the room*\n\n\n";
        assertTrue(JournalParser.needsNarration(journal));
    }

    @Test
    void needsNarration_trailingBlankLinesAfterMechanical() {
        String journal = "> **Strike** (+iron): Action 3, Challenge 8|9 → **Miss**\n\n\n";
        assertTrue(JournalParser.needsNarration(journal));
    }

    // --- endsWithPlayerEntry ---

    @Test
    void endsWithPlayerEntry_true() {
        String journal = "Some text\n\n*Player: My action*\n";
        assertTrue(JournalParser.endsWithPlayerEntry(journal));
    }

    @Test
    void endsWithPlayerEntry_falseWhenMechanical() {
        String journal = "*Player: I attack*\n\n> **Strike** (+iron): Action 3, Challenge 8|9 → **Miss**\n";
        assertFalse(JournalParser.endsWithPlayerEntry(journal));
    }

    @Test
    void endsWithPlayerEntry_falseWhenNarrative() {
        String journal = "*Player: hello*\n\nThe world turns dark.\n";
        assertFalse(JournalParser.endsWithPlayerEntry(journal));
    }

    // --- extractLastPlayerInput ---

    @Test
    void extractLastPlayerInput_single() {
        String journal = "*Player: I look around*\n\nSome response.";
        assertEquals("I look around", JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_multiple() {
        String journal = """
                *Player: First action*

                Response one.

                *Player: Second action*

                Response two.
                """;
        assertEquals("Second action", JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_none() {
        String journal = "Just some narrative text.\n\nMore text.";
        assertNull(JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_preservesInternalSpaces() {
        String journal = "*Player: I carefully open the old wooden door*";
        assertEquals("I carefully open the old wooden door", JournalParser.extractLastPlayerInput(journal));
    }

    // --- countExchanges ---

    @Test
    void countExchanges_none() {
        assertEquals(0, JournalParser.countExchanges("Just narrative.\nMore narrative."));
    }

    @Test
    void countExchanges_single() {
        assertEquals(1, JournalParser.countExchanges("*Player: hello*\n\nResponse."));
    }

    @Test
    void countExchanges_multiple() {
        String journal = """
                *Player: first*

                Response one.

                *Player: second*

                Response two.

                *Player: third*
                """;
        assertEquals(3, JournalParser.countExchanges(journal));
    }

    // --- sanitizeNarrative ---

    @Test
    void sanitizeNarrative_stripsBlockquotePrefix() {
        String input = "> The air seemed to thicken around you.";
        assertEquals("The air seemed to thicken around you.",
                JournalParser.sanitizeNarrative(input));
    }

    @Test
    void sanitizeNarrative_multipleBlockquoteLines() {
        String input = "> First paragraph of narrative.\n> \n> Second paragraph continues.";
        assertEquals("First paragraph of narrative.\n\nSecond paragraph continues.",
                JournalParser.sanitizeNarrative(input));
    }

    @Test
    void sanitizeNarrative_mixedBlockquoteAndPlain() {
        String input = "> This line is blockquoted.\nThis line is not.";
        assertEquals("This line is blockquoted.\nThis line is not.",
                JournalParser.sanitizeNarrative(input));
    }

    @Test
    void sanitizeNarrative_plainTextUnchanged() {
        String input = "The wind howls across the moor.\nYou press onward.";
        assertEquals(input, JournalParser.sanitizeNarrative(input));
    }

    @Test
    void sanitizeNarrative_nullReturnsEmpty() {
        assertEquals("", JournalParser.sanitizeNarrative(null));
    }

    @Test
    void sanitizeNarrative_emptyStringUnchanged() {
        assertEquals("", JournalParser.sanitizeNarrative(""));
    }

    // --- Full journal scenario ---

    @Test
    void fullJournalScenario_mixedContent() {
        String journal = """
                *Player: I approach the bridge cautiously*

                The old stone bridge creaks under your weight. Mist rises from the chasm below.

                *Player: I try to cross quickly*

                > **Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**

                You dash across the bridge, boots pounding on ancient stone.
                The far side holds firm as you reach safety.

                *Player: I look for shelter*

                > **Oracle** (Action / Theme): 42 → Discovery
                """;

        // Last player input should be the most recent one
        assertEquals("I look for shelter", JournalParser.extractLastPlayerInput(journal));

        // Journal ends with oracle result (mechanical) — needs narration
        assertTrue(JournalParser.needsNarration(journal));

        // Last line is mechanical, not player
        assertFalse(JournalParser.endsWithPlayerEntry(journal));

        // Three player exchanges
        assertEquals(3, JournalParser.countExchanges(journal));
    }

    @Test
    void fullJournalScenario_endsWithNarrative() {
        String journal = """
                *Player: I search the ruins*

                > **Gather Information** (+wits): Action 7, Challenge 4|5 → **Strong hit**

                Among the rubble, you find an ancient scroll bearing the seal of the Iron Order.
                The parchment is brittle but legible.
                """;

        assertTrue(JournalParser.needsNarration(journal) == false);
        assertEquals("I search the ruins", JournalParser.extractLastPlayerInput(journal));
        assertEquals(1, JournalParser.countExchanges(journal));
    }
}
