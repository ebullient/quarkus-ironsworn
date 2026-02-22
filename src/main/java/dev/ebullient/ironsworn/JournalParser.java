package dev.ebullient.ironsworn;

import java.util.ArrayList;
import java.util.List;

import dev.ebullient.ironsworn.chat.MarkdownAugmenter;

/**
 * Pure-function utilities for parsing journal markdown content.
 * Extracted from PlayWebSocket for testability.
 */
public class JournalParser {

    public record JournalExchange(int index, String content) {
    }

    /** A typed block of journal content with pre-rendered HTML. */
    public record JournalBlock(String type, String html) {
    }

    private JournalParser() {
    }

    /**
     * Split journal content into embeddable exchanges.
     * Each exchange starts with a player entry ({@code *Player: ...*}) or
     * a mechanical entry ({@code > **...**}) and includes all following
     * lines until the next such marker.
     */
    public static List<JournalExchange> parseExchanges(String journalContent) {
        if (journalContent == null || journalContent.isBlank()) {
            return List.of();
        }

        List<JournalExchange> exchanges = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String line : journalContent.split("\n")) {
            String trimmed = line.trim();
            if (isPlayerEntry(trimmed) || isMechanicalEntry(trimmed)) {
                // Flush previous exchange
                if (!current.isEmpty()) {
                    exchanges.add(new JournalExchange(index++, current.toString().trim()));
                    current.setLength(0);
                }
            }
            if (!trimmed.isEmpty() || !current.isEmpty()) {
                current.append(line).append("\n");
            }
        }
        // Flush last exchange
        if (!current.isEmpty()) {
            String text = current.toString().trim();
            if (!text.isEmpty()) {
                exchanges.add(new JournalExchange(index, text));
            }
        }
        return exchanges;
    }

    /**
     * Strip blockquote prefixes from narrative text.
     * The LLM sometimes mimics the journal's blockquote format for mechanical entries.
     * Blockquotes are reserved for mechanical results, so strip them from narrative.
     */
    public static String sanitizeNarrative(String narrative) {
        if (narrative == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : narrative.split("\n", -1)) {
            if (isMechanicalEntry(line.trim())) {
                // Preserve oracle/move blockquotes from tool calls
                sb.append(line);
            } else if (line.startsWith("> ")) {
                sb.append(line.substring(2));
            } else if (line.equals(">")) {
                sb.append("");
            } else {
                sb.append(line);
            }
            sb.append("\n");
        }
        // Remove trailing newline added by the loop
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Extract the text of the last player input from journal content.
     * Player entries are formatted as {@code *Player: some text*}.
     *
     * @return the unwrapped player text, or null if none found
     */
    public static String extractLastPlayerInput(String journalContent) {
        String last = null;
        for (String line : journalContent.split("\n")) {
            String trimmed = line.trim();
            if (isPlayerEntry(trimmed)) {
                last = trimmed.substring(8, trimmed.length() - 1).trim();
            }
        }
        return last;
    }

    /**
     * Check whether the last non-blank line of the journal requires narration.
     * This is true if the journal ends with a player entry or a mechanical result.
     */
    public static boolean needsNarration(String journalContent) {
        String[] lines = journalContent.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return isPlayerEntry(trimmed) || isMechanicalEntry(trimmed);
            }
        }
        return false;
    }

    /**
     * Check whether the last non-blank line is a player entry.
     */
    public static boolean endsWithPlayerEntry(String journalContent) {
        String[] lines = journalContent.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return isPlayerEntry(trimmed);
            }
        }
        return false;
    }

    /**
     * Count the number of player exchanges in the journal context.
     */
    public static int countExchanges(String journalContext) {
        int count = 0;
        for (String line : journalContext.split("\n")) {
            if (line.trim().startsWith("*Player:")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Test whether a trimmed line is a player entry: {@code *Player: ...*}
     */
    public static boolean isPlayerEntry(String trimmedLine) {
        return trimmedLine.startsWith("*Player:") && trimmedLine.endsWith("*");
    }

    /**
     * Test whether a trimmed line is a mechanical entry (blockquote with bold text and arrow).
     * Journal format: {@code > **Move Name** (+stat): Action N, Challenge N|N → **Outcome**}
     * or: {@code > **Oracle** (Collection / Table): roll → result}
     */
    public static boolean isMechanicalEntry(String trimmedLine) {
        if (trimmedLine == null) {
            return false;
        }
        String trimmed = trimmedLine.trim();
        if (!trimmed.startsWith(">")) {
            return false;
        }
        String afterBlockquote = trimmed.substring(1).stripLeading();
        return afterBlockquote.startsWith("**");
    }

    /**
     * Parse journal content into typed blocks with pre-rendered HTML.
     * Each block has a type ("user", "assistant", or "mechanical") and
     * HTML content ready for display.
     */
    public static List<JournalBlock> parseToBlocks(String journalContent, MarkdownAugmenter augmenter) {
        if (journalContent == null || journalContent.isBlank()) {
            return List.of();
        }

        List<JournalBlock> blocks = new ArrayList<>();
        List<String> currentLines = new ArrayList<>();
        String currentType = null; // "user", "assistant", "mechanical"

        for (String line : journalContent.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (!currentLines.isEmpty()) {
                    blocks.add(flushBlock(currentType, currentLines, augmenter));
                    currentLines.clear();
                    currentType = null;
                }
                continue;
            }

            if (isPlayerEntry(trimmed)) {
                if (!currentLines.isEmpty()) {
                    blocks.add(flushBlock(currentType, currentLines, augmenter));
                    currentLines.clear();
                }
                currentType = "user";
                currentLines.add(trimmed.substring(8, trimmed.length() - 1).trim()); // strip *Player: ...*
            } else if (isMechanicalEntry(trimmed)) {
                if (!currentLines.isEmpty() && !"mechanical".equals(currentType)) {
                    blocks.add(flushBlock(currentType, currentLines, augmenter));
                    currentLines.clear();
                }
                currentType = "mechanical";
                // Strip blockquote prefix for display
                String display = trimmed.startsWith(">") ? trimmed.replaceFirst("^>\\s*", "") : trimmed;
                currentLines.add(display);
            } else {
                if (!currentLines.isEmpty() && !"assistant".equals(currentType)) {
                    blocks.add(flushBlock(currentType, currentLines, augmenter));
                    currentLines.clear();
                }
                currentType = "assistant";
                currentLines.add(line);
            }
        }

        if (!currentLines.isEmpty()) {
            blocks.add(flushBlock(currentType, currentLines, augmenter));
        }

        return blocks;
    }

    private static JournalBlock flushBlock(String type, List<String> lines, MarkdownAugmenter augmenter) {
        String text = String.join("\n", lines).trim();
        if (type == null) {
            type = "assistant";
        }
        String html = switch (type) {
            case "user" -> escapeHtml(text);
            case "mechanical" -> augmenter.markdownToHtml(text);
            default -> augmenter.markdownToHtml(text);
        };
        return new JournalBlock(type, html);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
