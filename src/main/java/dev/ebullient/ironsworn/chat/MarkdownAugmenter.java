package dev.ebullient.ironsworn.chat;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Service that augments AI responses by converting markdown to HTML.
 */
@ApplicationScoped
public class MarkdownAugmenter {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownAugmenter() {
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    /**
     * Converts markdown text to HTML.
     *
     * @param markdownText the markdown response from the AI
     * @return HTML-formatted response
     */
    public String markdownToHtml(String markdownText) {
        if (markdownText == null || markdownText.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdownText);
        return renderer.render(document);
    }
}
