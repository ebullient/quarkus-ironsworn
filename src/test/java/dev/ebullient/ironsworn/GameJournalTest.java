package dev.ebullient.ironsworn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.ebullient.ironsworn.model.Campaign;
import dev.ebullient.ironsworn.model.CharacterSheet;
import dev.ebullient.ironsworn.model.Rank;
import dev.ebullient.ironsworn.model.Vow;

class GameJournalTest {

    @TempDir
    Path tempDir;

    GameJournal journal;

    @BeforeEach
    void setUp() throws Exception {
        journal = new GameJournal();
        // Set the journal directory via reflection (it's a config property)
        var field = GameJournal.class.getDeclaredField("journalDir");
        field.setAccessible(true);
        field.set(journal, tempDir.toString());
    }

    @Test
    void createStubCampaign_createsValidFile() throws IOException {
        Campaign campaign = journal.createStubCampaign("Test Hero");

        assertEquals("test-hero", campaign.id());
        assertEquals("Test Hero", campaign.name());

        String content = Files.readString(campaign.journalPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("# Ironsworn: Test Hero"));
        assertTrue(content.contains("**Edge**: 1"));
        assertTrue(content.contains("**Health**: 5"));
        assertTrue(content.contains("### Vows"));
        assertTrue(content.contains("## Journal"));
    }

    @Test
    void readCharacter_defaultStats() {
        journal.createStubCampaign("Test Hero");
        CharacterSheet character = journal.readCharacter("test-hero");

        assertEquals("Test Hero", character.name());
        assertEquals(1, character.edge());
        assertEquals(1, character.heart());
        assertEquals(1, character.iron());
        assertEquals(1, character.shadow());
        assertEquals(1, character.wits());
        assertEquals(5, character.health());
        assertEquals(5, character.spirit());
        assertEquals(5, character.supply());
        assertEquals(2, character.momentum());
        assertTrue(character.vows().isEmpty());
    }

    @Test
    void readCharacter_withCustomStatsAndVows() {
        CharacterSheet custom = new CharacterSheet("Kira", 3, 2, 1, 2, 1,
                4, 3, 5, 4,
                List.of(
                        new Vow("Find the lost shrine", Rank.DANGEROUS, 3),
                        new Vow("Avenge my clan", Rank.FORMIDABLE, 0)));
        journal.createCampaign(custom, null);

        CharacterSheet read = journal.readCharacter("kira");
        assertEquals("Kira", read.name());
        assertEquals(3, read.edge());
        assertEquals(2, read.heart());
        assertEquals(1, read.iron());
        assertEquals(2, read.shadow());
        assertEquals(1, read.wits());
        assertEquals(4, read.health());
        assertEquals(3, read.spirit());
        assertEquals(5, read.supply());
        assertEquals(4, read.momentum());

        assertEquals(2, read.vows().size());
        assertEquals("Find the lost shrine", read.vows().get(0).description());
        assertEquals(Rank.DANGEROUS, read.vows().get(0).rank());
        assertEquals(3, read.vows().get(0).progress());
        assertEquals("Avenge my clan", read.vows().get(1).description());
        assertEquals(Rank.FORMIDABLE, read.vows().get(1).rank());
    }

    @Test
    void isCreationPhase_trueWhenNoVows() {
        journal.createStubCampaign("New Character");
        assertTrue(journal.isCreationPhase("new-character"));
    }

    @Test
    void isCreationPhase_falseWhenHasVows() {
        CharacterSheet withVows = new CharacterSheet("Kira", 2, 2, 2, 2, 1,
                5, 5, 5, 2,
                List.of(new Vow("A dangerous quest", Rank.DANGEROUS, 0)));
        journal.createCampaign(withVows, null);
        assertFalse(journal.isCreationPhase("kira"));
    }

    @Test
    void appendMechanical_writesBlockquoteFormat() throws IOException {
        Campaign campaign = journal.createStubCampaign("Test Hero");
        journal.appendMechanical("test-hero",
                "**Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**");

        String content = Files.readString(campaign.journalPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("> **Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**"));
    }

    @Test
    void appendNarrative_writesPlainText() throws IOException {
        Campaign campaign = journal.createStubCampaign("Test Hero");
        journal.appendNarrative("test-hero", "The wind howls across the moor.");

        String content = Files.readString(campaign.journalPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("The wind howls across the moor."));
        // Narrative should NOT be in blockquote format
        assertFalse(content.contains("> The wind howls"));
    }

    @Test
    void appendMechanical_formatCompatibleWithParser() {
        journal.createStubCampaign("Test Hero");
        journal.appendMechanical("test-hero",
                "**Face Danger** (+edge): Action 5, Challenge 3|7 → **Strong hit**");

        String recentJournal = journal.getRecentJournal("test-hero", 100);

        // The mechanical entry should be detected by JournalParser
        assertTrue(JournalParser.needsNarration(recentJournal),
                "Mechanical entry in journal should trigger needsNarration");
    }

    @Test
    void appendNarrative_playerEntry_formatCompatibleWithParser() {
        journal.createStubCampaign("Test Hero");
        journal.appendNarrative("test-hero", "<player>\nI search the room\n</player>");

        String recentJournal = journal.getRecentJournal("test-hero", 3);

        assertTrue(JournalParser.needsNarration(recentJournal),
                "Player entry in journal should trigger needsNarration");
        assertTrue(JournalParser.endsWithPlayerEntry(recentJournal));
        assertEquals("I search the room", JournalParser.extractLastPlayerInput(recentJournal));
    }

    @Test
    void getRecentJournal_emptyJournal() {
        journal.createStubCampaign("Test Hero");
        String recent = journal.getRecentJournal("test-hero", 100);
        assertTrue(recent.isBlank());
    }

    @Test
    void getRecentJournal_withContent() {
        journal.createStubCampaign("Test Hero");
        journal.appendNarrative("test-hero", "<player>\nhello\n</player>");
        journal.appendNarrative("test-hero", "A response.");

        String recent = journal.getRecentJournal("test-hero", 100);
        assertTrue(recent.contains("<player>\nhello\n</player>"));
        assertTrue(recent.contains("A response."));
    }

    @Test
    void getRecentJournal_respectsMaxLines() {
        journal.createStubCampaign("Test Hero");
        // Write enough lines to exceed a small maxLines limit
        for (int i = 0; i < 20; i++) {
            journal.appendNarrative("test-hero", "Line " + i);
        }

        String recent = journal.getRecentJournal("test-hero", 5);
        // Should only have the last 5 lines worth of content
        String[] lines = recent.split("\n");
        assertTrue(lines.length <= 5, "Expected at most 5 lines but got " + lines.length);
    }

    @Test
    void updateCharacter_updatesStatsAndVows() {
        journal.createStubCampaign("Test Hero");

        CharacterSheet updated = new CharacterSheet("Test Hero", 3, 2, 1, 2, 1,
                4, 3, 5, 6,
                List.of(new Vow("Save the village", Rank.DANGEROUS, 5)));
        journal.updateCharacter("test-hero", updated);

        CharacterSheet read = journal.readCharacter("test-hero");
        assertEquals(3, read.edge());
        assertEquals(2, read.heart());
        assertEquals(4, read.health());
        assertEquals(6, read.momentum());
        assertEquals(1, read.vows().size());
        assertEquals("Save the village", read.vows().get(0).description());
        assertEquals(5, read.vows().get(0).progress());
    }

    @Test
    void listCampaigns_findsMultiple() {
        journal.createStubCampaign("Hero One");
        journal.createStubCampaign("Hero Two");

        List<Campaign> campaigns = journal.listCampaigns();
        assertEquals(2, campaigns.size());
    }

    @Test
    void fullPlaySession_journalRoundTrip() {
        // Simulate a play session and verify the journal can be parsed correctly
        journal.createStubCampaign("Ash");

        // Finalize character with vows
        CharacterSheet withVows = new CharacterSheet("Ash", 2, 3, 1, 2, 1,
                5, 5, 5, 2,
                List.of(new Vow("Find the iron shrine", Rank.DANGEROUS, 0)));
        journal.updateCharacter("ash", withVows);

        // Player acts
        journal.appendNarrative("ash", "<player>\nI head into the Hinterlands\n</player>");

        // Narrator responds
        journal.appendNarrative("ash",
                "The path narrows as you leave the settlement behind. Twisted oaks line the trail.");

        // Player makes a move
        journal.appendNarrative("ash", "<player>\nI try to navigate through the forest\n</player>");

        // Mechanical result
        journal.appendMechanical("ash",
                "**Face Danger** (+wits): Action 4, Challenge 6|2 → **Weak hit**");

        // Narrator responds to move
        journal.appendNarrative("ash",
                "You find your way, but the journey takes longer than expected. Night falls.");

        // Verify full round-trip
        String recentJournal = journal.getRecentJournal("ash", 100);
        assertFalse(JournalParser.needsNarration(recentJournal),
                "Should not need narration — journal ends with narrative");
        assertEquals("I try to navigate through the forest",
                JournalParser.extractLastPlayerInput(recentJournal));
        assertEquals(2, JournalParser.countExchanges(recentJournal));

        // Verify character state
        assertFalse(journal.isCreationPhase("ash"));
        CharacterSheet read = journal.readCharacter("ash");
        assertEquals(2, read.edge());
        assertEquals(3, read.heart());
    }
}
