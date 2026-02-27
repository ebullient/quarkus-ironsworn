package dev.ebullient.ironsworn;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import Datasworn.MarkdownString;
import dev.ebullient.ironsworn.chat.MarkdownAugmenter;
import io.quarkus.arc.Arc;
import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension(namespace = "util")
public class StringUtils {
    /**
     * Convert a name into a URL-friendly slug.
     */
    public static String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "untitled";
        }
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "") // Remove special characters
                .trim()
                .replaceAll("\\s+", "-") // Replace spaces with hyphens
                .replaceAll("-+", "-") // Replace multiple hyphens with single
                .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
    }

    public static String valueOrPlaceholder(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public static String valueOrPlaceholder(Integer value) {
        return value == null ? "—" : value.toString();
    }

    public static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    public static String valueOrPlaceholder(Collection<String> list) {
        if (list == null || list.isEmpty()) {
            return "—";
        }
        return String.join(", ", list);
    }

    public static String normalize(String value) {
        return value.trim().toLowerCase();
    }

    public static Collection<String> normalize(Collection<String> value) {
        if (value == null) {
            return List.of();
        }
        return value.stream()
                .map(s -> normalize(s))
                .filter(s -> !s.isBlank())
                .toList();
    }

    public static String formatEpoch(Long epochMillis) {
        if (epochMillis == null) {
            return "—";
        }
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // id:classic/atlas/ironlands/barrier_islands
    private static final Pattern ATLAS_ID = Pattern.compile("id:classic/atlas/ironlands/([^)]+)");
    // id:classic/moves/{category}/{move} → /reference/moves/{move}
    private static final Pattern MOVE_ID = Pattern.compile("id:classic/moves/[^/]+/([^)]+)");
    // id:classic/oracles/{path...} → /reference/oracles/{path...}
    private static final Pattern ORACLE_ID = Pattern.compile("id:classic/oracles/([^)]+)");

    public static RawString mdToHtml(String markdown) {
        return mdToHtml(markdown, false);
    }

    public static RawString mdToHtml(String markdown, boolean inline) {
        if (markdown == null) {
            return null;
        }

        MarkdownAugmenter augmenter = Arc.container().instance(MarkdownAugmenter.class).get();
        var text = MOVE_ID.matcher(markdown).replaceAll("/reference/moves#$1");
        text = ORACLE_ID.matcher(text).replaceAll("/reference/oracles#$1");
        text = ATLAS_ID.matcher(text).replaceAll("/reference/atlas#$1");

        var md = augmenter.markdownToHtml(text);
        if (inline) {
            md = md.replaceAll("</?p>", "");
        }

        return new RawString(md);
    }

    public static RawString mdToHtml(MarkdownString markdown) {
        return mdToHtml(markdown, false);
    }

    public static RawString mdToHtml(MarkdownString markdown, boolean inline) {
        if (markdown == null) {
            return null;
        }
        return mdToHtml(markdown.getValue(), inline);
    }
}
