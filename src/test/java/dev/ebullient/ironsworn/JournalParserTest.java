package dev.ebullient.ironsworn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JournalParserTest {

    // --- isPlayerEntry ---

    @Test
    void isPlayerEntry_openTag() {
        assertTrue(JournalParser.isPlayerEntry("<player>"));
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
    void isPlayerEntry_closeTag() {
        assertFalse(JournalParser.isPlayerEntry("</player>"));
    }

    // --- isPlayerEntryEnd ---

    @Test
    void isPlayerEntryEnd_closeTag() {
        assertTrue(JournalParser.isPlayerEntryEnd("</player>"));
    }

    @Test
    void isPlayerEntryEnd_openTag() {
        assertFalse(JournalParser.isPlayerEntryEnd("<player>"));
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
    void isMechanicalEntry_oracleResult_withoutSpaceAfterBlockquote() {
        assertTrue(JournalParser.isMechanicalEntry(
                ">**Oracle** (Action / Theme): 42 → Discovery"));
    }

    @Test
    void isMechanicalEntry_oracleResult_withExtraSpacesAfterBlockquote() {
        assertTrue(JournalParser.isMechanicalEntry(
                ">   **Oracle** (Action / Theme): 42 → Discovery"));
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
    void isMechanicalEntry_playerOpenTag() {
        assertFalse(JournalParser.isMechanicalEntry("<player>"));
    }

    @Test
    void isMechanicalEntry_boldWithoutBlockquote() {
        assertFalse(JournalParser.isMechanicalEntry("**Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**"));
    }

    // --- needsNarration ---

    @Test
    void needsNarration_endsWithPlayerBlock() {
        String journal = """
                The story begins...

                <player>
                I head north toward the ruins.
                </player>
                """;
        assertTrue(JournalParser.needsNarration(journal));
    }

    @Test
    void needsNarration_endsWithMechanicalEntry() {
        String journal = """
                <player>
                I try to dodge the trap.
                </player>

                > **Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**
                """;
        assertTrue(JournalParser.needsNarration(journal));
    }

    @Test
    void needsNarration_endsWithNarrative() {
        String journal = """
                <player>
                I draw my sword.
                </player>

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
        String journal = "<player>\nI search the room.\n</player>\n\n\n";
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
        String journal = "Some text\n\n<player>\nMy action.\n</player>\n";
        assertTrue(JournalParser.endsWithPlayerEntry(journal));
    }

    @Test
    void endsWithPlayerEntry_falseWhenMechanical() {
        String journal = "<player>\nI attack.\n</player>\n\n> **Strike** (+iron): Action 3, Challenge 8|9 → **Miss**\n";
        assertFalse(JournalParser.endsWithPlayerEntry(journal));
    }

    @Test
    void endsWithPlayerEntry_falseWhenNarrative() {
        String journal = "<player>\nhello\n</player>\n\nThe world turns dark.\n";
        assertFalse(JournalParser.endsWithPlayerEntry(journal));
    }

    // --- extractLastPlayerInput ---

    @Test
    void extractLastPlayerInput_single() {
        String journal = "<player>\nI look around.\n</player>\n\nSome response.";
        assertEquals("I look around.", JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_multiple() {
        String journal = """
                <player>
                First action.
                </player>

                Response one.

                <player>
                Second action.
                </player>

                Response two.
                """;
        assertEquals("Second action.", JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_none() {
        String journal = "Just some narrative text.\n\nMore text.";
        assertNull(JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_preservesInternalSpaces() {
        String journal = "<player>\nI carefully open the old wooden door.\n</player>";
        assertEquals("I carefully open the old wooden door.", JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_multiLine() {
        String journal = """
                <player>
                I take deep breaths to remain calm.

                "What does the fabric have on it?"

                I slowly sidestep away from the bear.
                </player>
                """;
        assertEquals(
                "I take deep breaths to remain calm.\n\n\"What does the fabric have on it?\"\n\nI slowly sidestep away from the bear.",
                JournalParser.extractLastPlayerInput(journal));
    }

    @Test
    void extractLastPlayerInput_multiLineFollowedByNarrative() {
        String journal = """
                <player>
                First line of input.

                Second line of input.
                </player>

                The narrator responds with vivid prose.
                """;
        assertEquals("First line of input.\n\nSecond line of input.",
                JournalParser.extractLastPlayerInput(journal));
    }

    // --- countExchanges ---

    @Test
    void countExchanges_none() {
        assertEquals(0, JournalParser.countExchanges("Just narrative.\nMore narrative."));
    }

    @Test
    void countExchanges_single() {
        assertEquals(1, JournalParser.countExchanges("<player>\nhello\n</player>\n\nResponse."));
    }

    @Test
    void countExchanges_multiple() {
        String journal = """
                <player>
                first
                </player>

                Response one.

                <player>
                second
                </player>

                Response two.

                <player>
                third
                </player>
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

    // --- Full journal scenarios ---

    @Test
    void fullJournalScenario_mixedContent() {
        String journal = """
                <player>
                I approach the bridge cautiously.
                </player>

                The old stone bridge creaks under your weight. Mist rises from the chasm below.

                <player>
                I try to cross quickly.
                </player>

                > **Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**

                You dash across the bridge, boots pounding on ancient stone.
                The far side holds firm as you reach safety.

                <player>
                I look for shelter.
                </player>

                > **Oracle** (Action / Theme): 42 → Discovery
                """;

        assertEquals("I look for shelter.", JournalParser.extractLastPlayerInput(journal));
        assertTrue(JournalParser.needsNarration(journal));
        assertFalse(JournalParser.endsWithPlayerEntry(journal));
        assertEquals(3, JournalParser.countExchanges(journal));
    }

    @Test
    void fullJournalScenario_endsWithNarrative() {
        String journal = """
                <player>
                I search the ruins.
                </player>

                > **Gather Information** (+wits): Action 7, Challenge 4|5 → **Strong hit**

                Among the rubble, you find an ancient scroll bearing the seal of the Iron Order.
                The parchment is brittle but legible.
                """;

        assertFalse(JournalParser.needsNarration(journal));
        assertEquals("I search the ruins.", JournalParser.extractLastPlayerInput(journal));
        assertEquals(1, JournalParser.countExchanges(journal));
    }

    @Test
    void fullJournalScenario_multiLinePlayer() {
        String journal = """
                <player>
                I approach the bridge cautiously.
                </player>

                The old stone bridge creaks under your weight.

                <player>
                I take deep breaths to remain calm. While I really want to look at
                that scrap of fabric, there is no way I'm going to fight a bear for it.

                "What does the fabric have on it? a button? something shiny?"

                I slowly sidestep, moving away from the bear.
                </player>

                > **Endure stress** (+heart): Action 10, Challenge 10|4 → **Weak Hit**
                """;

        String expected = """
                I take deep breaths to remain calm. While I really want to look at
                that scrap of fabric, there is no way I'm going to fight a bear for it.

                "What does the fabric have on it? a button? something shiny?"

                I slowly sidestep, moving away from the bear.""".strip();

        assertEquals(expected, JournalParser.extractLastPlayerInput(journal));
        assertEquals(2, JournalParser.countExchanges(journal));
        assertTrue(JournalParser.needsNarration(journal));
        assertFalse(JournalParser.endsWithPlayerEntry(journal));
    }

    @Test
    void parseExchanges_multiLinePlayerIsOneExchange() {
        String journal = """
                <player>
                Line one.

                Line two.
                </player>

                The narrator responds.
                """;
        var exchanges = JournalParser.parseExchanges(journal);
        // Player block + following narrative = one exchange
        assertEquals(1, exchanges.size());
        assertTrue(exchanges.get(0).content().contains("Line one"));
        assertTrue(exchanges.get(0).content().contains("Line two"));
        assertTrue(exchanges.get(0).content().contains("narrator responds"));
    }
}
