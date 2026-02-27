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
    public record JournalBlock(String type, String html, int index) {
    }

    private static final String PLAYER_OPEN = "<player>";
    private static final String PLAYER_CLOSE = "</player>";

    private JournalParser() {
    }

    /**
     * Split journal content into embeddable exchanges.
     * Each exchange starts with a player entry ({@code <player>}) or
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
        boolean inPlayerBlock = false;

        for (String line : journalContent.split("\n")) {
            String trimmed = line.trim();

            if (inPlayerBlock) {
                current.append(line).append("\n");
                if (isPlayerEntryEnd(trimmed)) {
                    inPlayerBlock = false;
                }
                continue;
            }

            if (isPlayerEntry(trimmed) || isMechanicalEntry(trimmed)) {
                // Flush previous exchange
                if (!current.isEmpty()) {
                    exchanges.add(new JournalExchange(index++, current.toString().trim()));
                    current.setLength(0);
                }
                if (isPlayerEntry(trimmed)) {
                    inPlayerBlock = true;
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
     * Player entries are delimited by {@code <player>...</player>} tags.
     *
     * @return the unwrapped player text (preserving internal whitespace), or null if none found
     */
    public static String extractLastPlayerInput(String journalContent) {
        String[] lines = journalContent.split("\n");
        // Scan backwards for the last </player> then collect back to <player>
        int closeIdx = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (closeIdx == -1) {
                if (isPlayerEntryEnd(trimmed)) {
                    closeIdx = i;
                } else if (isPlayerEntry(trimmed) || isMechanicalEntry(trimmed)) {
                    // Last thing is an open tag or mechanical — no complete player block at end
                    break;
                } else {
                    // Narrative text at end — need to search further back
                    break;
                }
            } else {
                if (isPlayerEntry(trimmed)) {
                    return extractPlayerContent(lines, i, closeIdx);
                }
            }
        }
        // If we found a trailing block, return it
        if (closeIdx != -1) {
            // Edge case: <player> is at line 0
            for (int i = closeIdx - 1; i >= 0; i--) {
                if (isPlayerEntry(lines[i].trim())) {
                    return extractPlayerContent(lines, i, closeIdx);
                }
            }
        }
        // No trailing block — scan forward for the last complete block anywhere
        String lastContent = null;
        boolean inBlock = false;
        int openIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!inBlock && isPlayerEntry(trimmed)) {
                inBlock = true;
                openIdx = i;
            } else if (inBlock && isPlayerEntryEnd(trimmed)) {
                lastContent = extractPlayerContent(lines, openIdx, i);
                inBlock = false;
            }
        }
        return lastContent;
    }

    /** Extract the content between player open/close tag lines (exclusive of the tags). */
    private static String extractPlayerContent(String[] lines, int openIdx, int closeIdx) {
        StringBuilder sb = new StringBuilder();
        for (int i = openIdx + 1; i < closeIdx; i++) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(lines[i]);
        }
        String result = sb.toString().strip();
        return result.isEmpty() ? null : result;
    }

    /**
     * Check whether the last non-blank line of the journal requires narration.
     * This is true if the journal ends with a player block or a mechanical result.
     */
    public static boolean needsNarration(String journalContent) {
        String[] lines = journalContent.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return isPlayerEntryEnd(trimmed) || isMechanicalEntry(trimmed);
            }
        }
        return false;
    }

    /**
     * Check whether the last non-blank line is an oracle result (not a move roll).
     */
    public static boolean endsWithOracleEntry(String journalContent) {
        String[] lines = journalContent.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return isOracleEntry(trimmed);
            }
        }
        return false;
    }

    /**
     * Test whether a trimmed line is an oracle entry: {@code > **Oracle** (...)}.
     */
    public static boolean isOracleEntry(String trimmedLine) {
        if (trimmedLine == null) {
            return false;
        }
        String trimmed = trimmedLine.trim();
        if (!trimmed.startsWith(">")) {
            return false;
        }
        String afterBlockquote = trimmed.substring(1).stripLeading();
        return afterBlockquote.startsWith("**Oracle**");
    }

    /**
     * Check whether the journal ends with a player block ({@code </player>}).
     */
    public static boolean endsWithPlayerEntry(String journalContent) {
        String[] lines = journalContent.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return isPlayerEntryEnd(trimmed);
            }
        }
        return false;
    }

    /**
     * Count the number of player exchanges in the journal context.
     * Each {@code <player>} opening tag counts as one exchange.
     */
    public static int countExchanges(String journalContext) {
        int count = 0;
        for (String line : journalContext.split("\n")) {
            if (isPlayerEntry(line.trim())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Test whether a trimmed line is a player entry opening tag: {@code <player>}
     */
    public static boolean isPlayerEntry(String trimmedLine) {
        return PLAYER_OPEN.equals(trimmedLine);
    }

    /**
     * Test whether a trimmed line is a player entry closing tag: {@code </player>}
     */
    public static boolean isPlayerEntryEnd(String trimmedLine) {
        return PLAYER_CLOSE.equals(trimmedLine);
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
        boolean inPlayerBlock = false;
        int blockIndex = 0;

        for (String line : journalContent.split("\n")) {
            String trimmed = line.trim();

            // Handle player block content
            if (inPlayerBlock) {
                if (isPlayerEntryEnd(trimmed)) {
                    inPlayerBlock = false;
                    // Flush the player block
                    blocks.add(flushBlock("user", currentLines, blockIndex++, augmenter));
                    currentLines.clear();
                    currentType = null;
                } else {
                    currentLines.add(line);
                }
                continue;
            }

            if (trimmed.isEmpty()) {
                if (!currentLines.isEmpty()) {
                    blocks.add(flushBlock(currentType, currentLines, blockIndex++, augmenter));
                    currentLines.clear();
                    currentType = null;
                }
                continue;
            }

            if (isPlayerEntry(trimmed)) {
                if (!currentLines.isEmpty()) {
                    blocks.add(flushBlock(currentType, currentLines, blockIndex++, augmenter));
                    currentLines.clear();
                }
                currentType = "user";
                inPlayerBlock = true;
            } else if (isMechanicalEntry(trimmed)) {
                if (!currentLines.isEmpty() && !"mechanical".equals(currentType)) {
                    blocks.add(flushBlock(currentType, currentLines, blockIndex++, augmenter));
                    currentLines.clear();
                }
                currentType = "mechanical";
                // Strip blockquote prefix for display
                String display = trimmed.startsWith(">") ? trimmed.replaceFirst("^>\\s*", "") : trimmed;
                currentLines.add(display);
            } else {
                if (!currentLines.isEmpty() && !"assistant".equals(currentType)) {
                    blocks.add(flushBlock(currentType, currentLines, blockIndex++, augmenter));
                    currentLines.clear();
                }
                currentType = "assistant";
                currentLines.add(line);
            }
        }

        if (!currentLines.isEmpty()) {
            blocks.add(flushBlock(currentType, currentLines, blockIndex, augmenter));
        }

        return blocks;
    }

    /**
     * Find the line offset (within the journal section) where the given block index starts.
     * Walks the same state machine as {@link #parseToBlocks} but tracks line numbers.
     *
     * @return the 0-based line offset, or -1 if blockIndex is out of range
     */
    public static int findBlockStartLine(String journalContent, int blockIndex) {
        if (journalContent == null || journalContent.isBlank() || blockIndex < 0) {
            return -1;
        }

        String[] lines = journalContent.split("\n");
        int currentBlock = 0;
        String currentType = null;
        boolean inPlayerBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            if (inPlayerBlock) {
                if (isPlayerEntryEnd(trimmed)) {
                    inPlayerBlock = false;
                    // Block was flushed — increment
                    currentBlock++;
                    currentType = null;
                }
                continue;
            }

            if (trimmed.isEmpty()) {
                if (currentType != null) {
                    // Flush current block
                    currentBlock++;
                    currentType = null;
                }
                continue;
            }

            if (isPlayerEntry(trimmed)) {
                if (currentType != null) {
                    currentBlock++;
                }
                if (currentBlock == blockIndex) {
                    return i;
                }
                currentType = "user";
                inPlayerBlock = true;
            } else if (isMechanicalEntry(trimmed)) {
                if (currentType != null && !"mechanical".equals(currentType)) {
                    currentBlock++;
                }
                if (currentBlock == blockIndex && !"mechanical".equals(currentType)) {
                    return i;
                }
                currentType = "mechanical";
            } else {
                if (currentType != null && !"assistant".equals(currentType)) {
                    currentBlock++;
                }
                if (currentBlock == blockIndex && !"assistant".equals(currentType)) {
                    return i;
                }
                currentType = "assistant";
            }
        }
        return -1;
    }

    /**
     * Count the number of blocks in the journal content without rendering HTML.
     * Uses the same block-boundary logic as {@link #parseToBlocks}.
     */
    public static int countBlocks(String journalContent) {
        if (journalContent == null || journalContent.isBlank()) {
            return 0;
        }

        int blockCount = 0;
        String currentType = null;
        boolean inPlayerBlock = false;

        for (String line : journalContent.split("\n")) {
            String trimmed = line.trim();

            if (inPlayerBlock) {
                if (isPlayerEntryEnd(trimmed)) {
                    inPlayerBlock = false;
                    blockCount++;
                    currentType = null;
                }
                continue;
            }

            if (trimmed.isEmpty()) {
                if (currentType != null) {
                    blockCount++;
                    currentType = null;
                }
                continue;
            }

            if (isPlayerEntry(trimmed)) {
                if (currentType != null) {
                    blockCount++;
                }
                currentType = "user";
                inPlayerBlock = true;
            } else if (isMechanicalEntry(trimmed)) {
                if (currentType != null && !"mechanical".equals(currentType)) {
                    blockCount++;
                }
                currentType = "mechanical";
            } else {
                if (currentType != null && !"assistant".equals(currentType)) {
                    blockCount++;
                }
                currentType = "assistant";
            }
        }

        if (currentType != null) {
            blockCount++;
        }
        return blockCount;
    }

    private static JournalBlock flushBlock(String type, List<String> lines, int index, MarkdownAugmenter augmenter) {
        String text = String.join("\n", lines).trim();
        if (type == null) {
            type = "assistant";
        }
        String html = augmenter.markdownToHtml(text);
        return new JournalBlock(type, html, index);
    }
}
