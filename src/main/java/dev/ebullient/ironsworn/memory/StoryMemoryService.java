package dev.ebullient.ironsworn.memory;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;

@Singleton
public class StoryMemoryService {
    private static final Logger log = Logger.getLogger(StoryMemoryService.class);

    @ConfigProperty(name = "ironsworn.memory.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "ironsworn.memory.retrieve.max-results", defaultValue = "6")
    int maxResults;

    @ConfigProperty(name = "ironsworn.memory.retrieve.min-score", defaultValue = "0.35")
    double minScore;

    @ConfigProperty(name = "ironsworn.memory.retrieve.max-chars", defaultValue = "1200")
    int maxChars;

    @Inject
    StoryMemoryIndexer indexer;

    @Inject
    Instance<EmbeddingModel> embeddingModel;

    @Inject
    Instance<Neo4jEmbeddingStore> embeddingStore;

    public String relevantMemory(String campaignId, String query) {
        if (!enabled || query == null || query.isBlank()) {
            return "";
        }

        // Keep embeddings fresh in the background; do not block gameplay on a re-index.
        indexer.requestIndex(campaignId);

        try {
            Embedding queryEmbedding = embeddingModel.get().embed(query).content();
            Filter filter = metadataKey("campaignId").isEqualTo(campaignId);

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .filter(filter)
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.get().search(request);
            return format(result);
        } catch (Exception e) {
            log.debugf(e, "Story memory retrieval failed for %s", campaignId);
            return "";
        }
    }

    private String format(EmbeddingSearchResult<TextSegment> result) {
        if (result == null || result.matches() == null || result.matches().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int remaining = maxChars;

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>(result.matches());
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment seg = match.embedded();
            if (seg == null || seg.text() == null) {
                continue;
            }
            String text = collapseWhitespace(seg.text()).trim();
            if (text.isBlank()) {
                continue;
            }

            // Keep each excerpt reasonably sized; the "Recent Journal" already provides local continuity.
            if (text.length() > 450) {
                text = text.substring(0, 450).trim() + "…";
            }

            String line = "- " + text + "\n";
            if (line.length() > remaining) {
                break;
            }
            sb.append(line);
            remaining -= line.length();
        }

        return sb.toString().trim();
    }

    private static String collapseWhitespace(String text) {
        return Objects.requireNonNullElse(text, "").replaceAll("\\s+", " ");
    }
}
