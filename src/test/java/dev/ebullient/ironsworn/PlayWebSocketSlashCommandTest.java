package dev.ebullient.ironsworn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.model.CharacterSheet;
import dev.ebullient.ironsworn.model.Rank;
import dev.ebullient.ironsworn.model.Vow;

class PlayWebSocketSlashCommandTest {

    @TempDir
    Path tempDir;

    GameJournal journal;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        journal = new GameJournal();
        var field = GameJournal.class.getDeclaredField("journalDir");
        field.setAccessible(true);
        field.set(journal, tempDir.toString());

        objectMapper = new ObjectMapper();
    }

    @Test
    void status_doesNotModifyJournal() throws Exception {
        var campaign = journal.createStubCampaign("Test Hero");
        String before = Files.readString(campaign.journalPath(), StandardCharsets.UTF_8);

        PlayWebSocket ws = new PlayWebSocket();
        inject(ws, "journal", journal);
        inject(ws, "objectMapper", objectMapper);
        inject(ws, "campaignId", "test-hero");

        ws.onMessage("{\"type\":\"slash_command\",\"text\":\"/status\"}");

        String after = Files.readString(campaign.journalPath(), StandardCharsets.UTF_8);
        assertEquals(before, after);
    }

    @Test
    void status_returnsCreationPhaseWhenNoVows() throws Exception {
        journal.createStubCampaign("Test Hero");

        PlayWebSocket ws = new PlayWebSocket();
        inject(ws, "journal", journal);
        inject(ws, "objectMapper", objectMapper);
        inject(ws, "campaignId", "test-hero");
        inject(ws, "creationEngine", new CreationEngine());

        String json = ws.onMessage("{\"type\":\"slash_command\",\"text\":\"/status\"}");
        JsonNode node = objectMapper.readTree(json);

        assertEquals("slash_command_result", node.path("type").asText());
        assertEquals("status", node.path("command").asText());
        assertEquals("creation", node.path("phase").asText());
        assertEquals("Test Hero", node.path("character").path("name").asText());
    }

    @Test
    void status_returnsCharacterFromHeader() throws Exception {
        CharacterSheet custom = new CharacterSheet("Kira", 3, 2, 1, 2, 1,
                4, 3, 5, 4,
                List.of(new Vow("Find the lost shrine", Rank.DANGEROUS, 3)));
        journal.createCampaign(custom, null);

        PlayWebSocket ws = new PlayWebSocket();
        inject(ws, "journal", journal);
        inject(ws, "objectMapper", objectMapper);
        inject(ws, "campaignId", "kira");

        String json = ws.onMessage("{\"type\":\"slash_command\",\"text\":\"/status\"}");
        JsonNode node = objectMapper.readTree(json);

        assertEquals("active", node.path("phase").asText());
        JsonNode c = node.path("character");
        assertEquals("Kira", c.path("name").asText());
        assertEquals(3, c.path("edge").asInt());
        assertEquals(2, c.path("heart").asInt());
        assertEquals(1, c.path("iron").asInt());
        assertEquals(2, c.path("shadow").asInt());
        assertEquals(1, c.path("wits").asInt());
        assertEquals(4, c.path("health").asInt());
        assertEquals(3, c.path("spirit").asInt());
        assertEquals(5, c.path("supply").asInt());
        assertEquals(4, c.path("momentum").asInt());

        JsonNode vows = c.path("vows");
        assertEquals(1, vows.size());
        assertEquals("Find the lost shrine", vows.get(0).path("description").asText());
        assertEquals("DANGEROUS", vows.get(0).path("rank").asText());
        assertEquals(3, vows.get(0).path("progress").asInt());
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
