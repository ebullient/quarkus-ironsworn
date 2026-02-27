package dev.ebullient.ironsworn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import dev.ebullient.ironsworn.memory.StoryMemoryIndexer;
import dev.ebullient.ironsworn.model.Campaign;
import dev.ebullient.ironsworn.model.CharacterSheet;
import dev.ebullient.ironsworn.model.Rank;
import dev.ebullient.ironsworn.model.Vow;

@Singleton
public class GameJournal {
    private static final Logger log = Logger.getLogger(GameJournal.class);

    private static final ConcurrentHashMap<String, Object> CAMPAIGN_LOCKS = new ConcurrentHashMap<>();

    private static final Pattern STATS_LINE = Pattern.compile(
            "\\*\\*Edge\\*\\*:\\s*(\\d+)\\s*\\|\\s*\\*\\*Heart\\*\\*:\\s*(\\d+)\\s*\\|\\s*\\*\\*Iron\\*\\*:\\s*(\\d+)\\s*\\|\\s*\\*\\*Shadow\\*\\*:\\s*(\\d+)\\s*\\|\\s*\\*\\*Wits\\*\\*:\\s*(\\d+)");
    private static final Pattern METERS_LINE = Pattern.compile(
            "\\*\\*Health\\*\\*:\\s*(-?\\d+)\\s*\\|\\s*\\*\\*Spirit\\*\\*:\\s*(-?\\d+)\\s*\\|\\s*\\*\\*Supply\\*\\*:\\s*(-?\\d+)\\s*\\|\\s*\\*\\*Momentum\\*\\*:\\s*(-?\\d+)");
    private static final Pattern VOW_LINE = Pattern.compile(
            "-\\s*\\[([x ])]\\s*(.+?)\\s*—\\s*(\\w+)\\s*\\((\\d+)/10\\)");
    private static final Pattern TITLE_LINE = Pattern.compile(
            "^#\\s+Ironsworn:\\s*(.+)$");

    @ConfigProperty(name = "ironsworn.journal.dir", defaultValue = "${user.home}/.ironsworn")
    String journalDir;

    @Inject
    StoryMemoryIndexer storyMemoryIndexer;

    private Path resolveJournalDir() {
        Path dir = Path.of(journalDir);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new RuntimeException("Cannot create journal directory: " + dir, e);
            }
        }
        return dir;
    }

    private Path journalPath(String campaignId) {
        return resolveJournalDir().resolve(campaignId + ".md");
    }

    public List<Campaign> listCampaigns() {
        Path dir = resolveJournalDir();
        List<Campaign> campaigns = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        String id = filename.substring(0, filename.length() - 3);
                        String name = readCampaignName(p);
                        campaigns.add(new Campaign(id, name, p));
                    });
        } catch (IOException e) {
            log.errorf(e, "Failed to list campaigns in %s", dir);
        }
        return campaigns;
    }

    private String readCampaignName(Path path) {
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                Matcher m = TITLE_LINE.matcher(line);
                if (m.matches()) {
                    return m.group(1).trim();
                }
            }
        } catch (IOException e) {
            log.warnf("Could not read campaign name from %s", path);
        }
        return path.getFileName().toString();
    }

    /**
     * Create a stub campaign with default stats and empty journal.
     * Used when the player enters a name; character creation continues on the play page.
     */
    public Campaign createStubCampaign(String name) {
        CharacterSheet defaults = CharacterSheet.defaults(name);
        return createCampaign(defaults, null);
    }

    public Campaign createCampaign(CharacterSheet character, String backstory) {
        String id = slugify(character.name());
        Path path = journalPath(id);

        StringBuilder sb = new StringBuilder();
        sb.append("# Ironsworn: ").append(character.name()).append("\n\n");
        sb.append("## Character\n");
        sb.append(formatStatsLine(character)).append("\n");
        sb.append(formatMetersLine(character)).append("\n");
        sb.append("\n### Vows\n");
        for (Vow vow : character.vows()) {
            sb.append(formatVowLine(vow)).append("\n");
        }
        sb.append("\n---\n\n## Journal\n\n");
        if (backstory != null && !backstory.isBlank()) {
            sb.append(backstory.trim()).append("\n\n");
        }

        try {
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            log.infof("Created campaign journal: %s", path);
            if (storyMemoryIndexer != null) {
                storyMemoryIndexer.warmIndex(id);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create campaign file: " + path, e);
        }
        return new Campaign(id, character.name(), path);
    }

    /**
     * Check if a campaign is still in the creation phase (empty journal section).
     */
    public boolean isCreationPhase(String campaignId) {
        CharacterSheet character = readCharacter(campaignId);
        return character.vows().isEmpty();
    }

    public CharacterSheet readCharacter(String campaignId) {
        Path path = journalPath(campaignId);
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return parseCharacter(lines);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read campaign: " + campaignId, e);
        }
    }

    public void updateCharacter(String campaignId, CharacterSheet character) {
        // Use per-campaign lock to prevent concurrent writes
        Object lock = CAMPAIGN_LOCKS.computeIfAbsent(campaignId, k -> new Object());
        synchronized (lock) {
            Path path = journalPath(campaignId);
            try {
                List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
                updateStatsAndMeters(lines, character);
                lines = replaceVowSection(lines, character);
                Files.writeString(path, String.join("\n", lines), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update character in campaign: " + campaignId, e);
            }
        }
    }

    private void updateStatsAndMeters(List<String> lines, CharacterSheet character) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (STATS_LINE.matcher(line).find()) {
                lines.set(i, formatStatsLine(character));
            }
            if (METERS_LINE.matcher(line).find()) {
                lines.set(i, formatMetersLine(character));
            }
        }
    }

    private List<String> replaceVowSection(List<String> lines, CharacterSheet character) {
        int vowStart = -1;
        int vowEnd = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().equals("### Vows")) {
                vowStart = i + 1;
            }
            if (line.trim().equals("---") && vowStart >= 0) {
                vowEnd = i;
                break;
            }
        }

        if (vowStart < 0 || vowEnd < 0) {
            return lines;
        }

        List<String> result = new ArrayList<>(lines.subList(0, vowStart));
        for (Vow vow : character.vows()) {
            result.add(formatVowLine(vow));
        }
        result.add(""); // blank line before ---
        result.addAll(lines.subList(vowEnd, lines.size()));
        return result;
    }

    public String getFullJournal(String campaignId) {
        Path path = journalPath(campaignId);
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int journalStart = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("## Journal")) {
                    journalStart = i + 1;
                    break;
                }
            }
            if (journalStart < 0 || journalStart >= lines.size()) {
                return "";
            }
            return String.join("\n", lines.subList(journalStart, lines.size())).trim();
        } catch (IOException e) {
            log.errorf(e, "Failed to read full journal for campaign: %s", campaignId);
            return "";
        }
    }

    public String getRecentJournal(String campaignId, int maxLines) {
        Path path = journalPath(campaignId);
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int journalStart = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("## Journal")) {
                    journalStart = i + 1;
                    break;
                }
            }
            if (journalStart < 0 || journalStart >= lines.size()) {
                return "";
            }
            List<String> journalLines = lines.subList(journalStart, lines.size());
            if (journalLines.size() <= maxLines) {
                return String.join("\n", journalLines).trim();
            }
            return String.join("\n", journalLines.subList(journalLines.size() - maxLines, journalLines.size())).trim();
        } catch (IOException e) {
            log.errorf(e, "Failed to read journal for campaign: %s", campaignId);
            return "";
        }
    }

    public void appendNarrative(String campaignId, String text) {
        appendToJournal(campaignId, "\n" + text.trim() + "\n");
    }

    public void appendMechanical(String campaignId, String text) {
        appendToJournal(campaignId, "\n> " + text.trim() + "\n");
    }

    private void appendToJournal(String campaignId, String content) {
        Path path = journalPath(campaignId);
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            if (storyMemoryIndexer != null) {
                storyMemoryIndexer.requestIndex(campaignId);
            }
        } catch (IOException e) {
            log.errorf(e, "Failed to append to journal: %s", campaignId);
        }
    }

    /**
     * Truncate the journal at the given block index, removing that block and everything after it.
     */
    public void truncateJournal(String campaignId, int blockIndex) {
        Object lock = CAMPAIGN_LOCKS.computeIfAbsent(campaignId, k -> new Object());
        synchronized (lock) {
            Path path = journalPath(campaignId);
            try {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                int journalStart = -1;
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).trim().equals("## Journal")) {
                        journalStart = i + 1;
                        break;
                    }
                }
                if (journalStart < 0) {
                    return;
                }

                String journalContent = String.join("\n", lines.subList(journalStart, lines.size()));
                int lineOffset = JournalParser.findBlockStartLine(journalContent, blockIndex);
                if (lineOffset < 0) {
                    return;
                }

                // Keep everything before the journal section + journal header + lines up to the cut point
                int cutLine = journalStart + lineOffset;
                // Trim trailing blank lines before the cut point
                while (cutLine > journalStart && lines.get(cutLine - 1).trim().isEmpty()) {
                    cutLine--;
                }
                List<String> kept = new ArrayList<>(lines.subList(0, cutLine));
                kept.add(""); // ensure trailing newline

                Files.writeString(path, String.join("\n", kept) + "\n", StandardCharsets.UTF_8);
                log.infof("Truncated journal for campaign %s at block %d (line %d)", campaignId, blockIndex, cutLine);
            } catch (IOException e) {
                throw new RuntimeException("Failed to truncate journal for campaign: " + campaignId, e);
            }
        }
    }

    /**
     * Replace block text in the journal file using simple string substitution.
     * The originalText and newText are the raw markdown content (without structural
     * delimiters like {@code <player>} tags).
     */
    public void replaceBlockText(String campaignId, String originalText, String newText) {
        Object lock = CAMPAIGN_LOCKS.computeIfAbsent(campaignId, k -> new Object());
        synchronized (lock) {
            Path path = journalPath(campaignId);
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                String updated = content.replace(originalText.trim(), newText.trim());
                if (updated.equals(content)) {
                    log.warnf("replaceBlockText: original text not found in journal %s", campaignId);
                    return;
                }
                Files.writeString(path, updated, StandardCharsets.UTF_8);
                if (storyMemoryIndexer != null) {
                    storyMemoryIndexer.requestIndex(campaignId);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to replace block text in campaign: " + campaignId, e);
            }
        }
    }

    public boolean deleteCampaign(String campaignId) {
        Object lock = CAMPAIGN_LOCKS.computeIfAbsent(campaignId, k -> new Object());
        synchronized (lock) {
            Path path = journalPath(campaignId);
            if (!Files.exists(path)) {
                return false;
            }
            try {
                Files.delete(path);
                log.infof("Deleted campaign journal: %s", path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete campaign file: " + path, e);
            }
            if (storyMemoryIndexer != null) {
                storyMemoryIndexer.deleteCampaignIndex(campaignId);
            }
        }
        CAMPAIGN_LOCKS.remove(campaignId);
        return true;
    }

    public Campaign getCampaign(String campaignId) {
        Path path = journalPath(campaignId);
        if (!Files.exists(path)) {
            return null;
        }
        String name = readCampaignName(path);
        return new Campaign(campaignId, name, path);
    }

    // --- Parsing helpers ---

    private CharacterSheet parseCharacter(List<String> lines) {
        String name = "";
        int edge = 1, heart = 1, iron = 1, shadow = 1, wits = 1;
        int health = 5, spirit = 5, supply = 5, momentum = 2;
        List<Vow> vows = new ArrayList<>();

        for (String line : lines) {
            Matcher titleMatch = TITLE_LINE.matcher(line);
            if (titleMatch.matches()) {
                name = titleMatch.group(1).trim();
            }

            Matcher statsMatch = STATS_LINE.matcher(line);
            if (statsMatch.find()) {
                edge = Integer.parseInt(statsMatch.group(1));
                heart = Integer.parseInt(statsMatch.group(2));
                iron = Integer.parseInt(statsMatch.group(3));
                shadow = Integer.parseInt(statsMatch.group(4));
                wits = Integer.parseInt(statsMatch.group(5));
            }

            Matcher metersMatch = METERS_LINE.matcher(line);
            if (metersMatch.find()) {
                health = Integer.parseInt(metersMatch.group(1));
                spirit = Integer.parseInt(metersMatch.group(2));
                supply = Integer.parseInt(metersMatch.group(3));
                momentum = Integer.parseInt(metersMatch.group(4));
            }

            Matcher vowMatch = VOW_LINE.matcher(line);
            if (vowMatch.find()) {
                String desc = vowMatch.group(2).trim();
                Rank rank = Rank.valueOf(vowMatch.group(3).toUpperCase());
                int progress = Integer.parseInt(vowMatch.group(4));
                vows.add(new Vow(desc, rank, progress));
            }
        }

        return new CharacterSheet(name, edge, heart, iron, shadow, wits,
                health, spirit, supply, momentum, vows);
    }

    // --- Formatting helpers ---

    private String formatStatsLine(CharacterSheet c) {
        return "- **Edge**: %d | **Heart**: %d | **Iron**: %d | **Shadow**: %d | **Wits**: %d".formatted(
                c.edge(), c.heart(), c.iron(), c.shadow(), c.wits());
    }

    private String formatMetersLine(CharacterSheet c) {
        return "- **Health**: %d | **Spirit**: %d | **Supply**: %d | **Momentum**: %d".formatted(
                c.health(), c.spirit(), c.supply(), c.momentum());
    }

    private String formatVowLine(Vow v) {
        String check = v.progress() >= 10 ? "x" : " ";
        return "- [%s] %s — %s (%d/10)".formatted(
                check,
                v.description(),
                v.rank().name().charAt(0) + v.rank().name().substring(1).toLowerCase(),
                v.progress());
    }

    private String slugify(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
