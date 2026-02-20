package dev.ebullient.ironsworn;

/**
 * Pure-function utilities for parsing journal markdown content.
 * Extracted from PlayWebSocket for testability.
 */
public class JournalParser {

    private JournalParser() {
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
            if (line.startsWith("> ")) {
                sb.append(line.substring(2));
            } else if (line.equals(">")) {
                // Empty blockquote line
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
        return trimmedLine.startsWith("> **");
    }
}
