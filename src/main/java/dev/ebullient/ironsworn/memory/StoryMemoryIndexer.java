package dev.ebullient.ironsworn.memory;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.ebullient.ironsworn.JournalParser;
import dev.ebullient.ironsworn.JournalParser.JournalExchange;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.filter.Filter;
import io.quarkus.logging.Log;

@Singleton
public class StoryMemoryIndexer {
    record IndexState(long journalLastModifiedMillis, List<String> exchangeHashes) {
    }

    @ConfigProperty(name = "ironsworn.memory.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "ironsworn.memory.index.debounce-ms", defaultValue = "500")
    long debounceMillis;

    @ConfigProperty(name = "ironsworn.journal.dir", defaultValue = "${user.home}/.ironsworn")
    String journalDir;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    Neo4jEmbeddingStore embeddingStore;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> campaignLocks = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @PostConstruct
    void init() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("story-memory-indexer-", 0).factory());
        Log.debugf("StoryMemoryIndexer isAvailable=%s", isAvailable());
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public boolean isAvailable() {
        return enabled;
    }

    public void warmIndex(String campaignId) {
        scheduleIndex(campaignId, 0);
    }

    public void deleteCampaignIndex(String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            return;
        }
        // Cancel any pending index task
        ScheduledFuture<?> existing = pending.remove(campaignId);
        if (existing != null) {
            existing.cancel(false);
        }
        Object lock = campaignLocks.computeIfAbsent(campaignId, k -> new Object());
        synchronized (lock) {
            clearCampaignIndex(campaignId, indexStatePath(campaignId));
        }
        campaignLocks.remove(campaignId);
    }

    public void requestIndex(String campaignId) {
        scheduleIndex(campaignId, debounceMillis);
    }

    private void scheduleIndex(String campaignId, long delayMillis) {
        if (!isAvailable() || campaignId == null || campaignId.isBlank()) {
            return;
        }
        ScheduledFuture<?> existing = pending.remove(campaignId);
        if (existing != null) {
            existing.cancel(false);
        }
        pending.put(campaignId, scheduler.schedule(() -> {
            try {
                indexNow(campaignId);
            } catch (Exception e) {
                Log.warnf(e, "Story memory indexing failed for %s", campaignId);
            } finally {
                pending.remove(campaignId);
            }
        }, delayMillis, TimeUnit.MILLISECONDS));
    }

    void indexNow(String campaignId) {
        if (!isAvailable()) {
            return;
        }
        Path journalPath = journalPath(campaignId);
        if (!Files.exists(journalPath)) {
            return;
        }

        Object lock = campaignLocks.computeIfAbsent(campaignId, k -> new Object());
        synchronized (lock) {
            long lastModified = journalLastModifiedMillis(journalPath);
            String journalSection = readJournalSection(journalPath);

            Path statePath = indexStatePath(campaignId);
            IndexState oldState = readState(statePath);
            if (oldState != null && oldState.journalLastModifiedMillis() == lastModified) {
                return;
            }

            if (journalSection.isBlank()) {
                clearCampaignIndex(campaignId, statePath);
                return;
            }

            List<JournalExchange> exchanges = JournalParser.parseExchanges(journalSection);
            if (exchanges.isEmpty()) {
                clearCampaignIndex(campaignId, statePath);
                return;
            }

            List<String> newHashes = new ArrayList<>(exchanges.size());
            for (JournalExchange ex : exchanges) {
                newHashes.add(sha256(ex.content()));
            }

            int firstDiff = 0;
            List<String> oldHashes = oldState != null ? oldState.exchangeHashes() : List.of();
            int min = Math.min(oldHashes.size(), newHashes.size());
            while (firstDiff < min && Objects.equals(oldHashes.get(firstDiff), newHashes.get(firstDiff))) {
                firstDiff++;
            }

            // Remove extra embeddings if the journal was shortened.
            if (oldHashes.size() > newHashes.size()) {
                List<String> toRemove = new ArrayList<>();
                for (int i = newHashes.size(); i < oldHashes.size(); i++) {
                    toRemove.add(embeddingId(campaignId, i));
                }
                if (!toRemove.isEmpty()) {
                    embeddingStore.removeAll(toRemove);
                }
            }

            if (firstDiff >= exchanges.size()) {
                writeState(statePath, new IndexState(lastModified, newHashes));
                return;
            }

            List<String> ids = new ArrayList<>();
            List<TextSegment> segments = new ArrayList<>();
            for (int i = firstDiff; i < exchanges.size(); i++) {
                JournalExchange exchange = exchanges.get(i);
                // Strip mechanical lines (oracle rolls, move results) — only
                // embed the narrative content for better semantic matching.
                String narrative = stripNonNarrative(exchange.content());
                if (narrative.isBlank()) {
                    continue;
                }
                ids.add(embeddingId(campaignId, i));
                segments.add(TextSegment.from(narrative, metadata(campaignId, i)));
            }

            Log.infof("Indexing %s: %d narrative segments from %d exchanges (firstDiff=%d)",
                    campaignId, segments.size(), exchanges.size(), firstDiff);
            if (segments.isEmpty()) {
                Log.warnf("All exchanges were purely mechanical for %s — nothing to embed", campaignId);
                writeState(statePath, new IndexState(lastModified, newHashes));
                return;
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            int n = Math.min(embeddings.size(), segments.size());
            if (n <= 0) {
                Log.warnf("No embeddings produced for %s", campaignId);
                return;
            }
            if (embeddings.size() != segments.size()) {
                Log.warnf("Embedding count mismatch for %s: %d embeddings for %d segments (indexing %d)",
                        campaignId, embeddings.size(), segments.size(), n);
            }

            Log.infof("Storing %d embeddings for %s", n, campaignId);
            embeddingStore.addAll(ids.subList(0, n), embeddings.subList(0, n), segments.subList(0, n));
            Log.infof("Successfully stored embeddings for %s", campaignId);
            writeState(statePath, new IndexState(lastModified, newHashes));
        }
    }

    private void clearCampaignIndex(String campaignId, Path statePath) {
        try {
            Filter filter = metadataKey("campaignId").isEqualTo(campaignId);
            embeddingStore.removeAll(filter);
        } catch (Exception e) {
            Log.debugf(e, "Failed to clear Neo4j embeddings for %s", campaignId);
        }
        try {
            Files.deleteIfExists(statePath);
        } catch (IOException e) {
            Log.debugf(e, "Failed to delete index state %s", statePath);
        }
    }

    private Path journalPath(String campaignId) {
        return ensureJournalDir().resolve(campaignId + ".md");
    }

    private Path ensureJournalDir() {
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

    private Path indexStatePath(String campaignId) {
        Path indexDir = ensureJournalDir().resolve(".memory-index");
        try {
            Files.createDirectories(indexDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create memory index directory: " + indexDir, e);
        }
        return indexDir.resolve(campaignId + ".json");
    }

    private IndexState readState(Path statePath) {
        if (!Files.exists(statePath)) {
            return null;
        }
        try {
            return objectMapper.readValue(Files.readString(statePath, StandardCharsets.UTF_8), IndexState.class);
        } catch (Exception e) {
            Log.debugf(e, "Failed to read index state %s", statePath);
            return null;
        }
    }

    private void writeState(Path statePath, IndexState state) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
            Files.writeString(statePath, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception e) {
            Log.debugf(e, "Failed to write index state %s", statePath);
        }
    }

    private long journalLastModifiedMillis(Path journalPath) {
        try {
            return Files.getLastModifiedTime(journalPath).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private String readJournalSection(Path journalPath) {
        try {
            List<String> lines = Files.readAllLines(journalPath, StandardCharsets.UTF_8);
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
            Log.debugf(e, "Failed to read journal section from %s", journalPath);
            return "";
        }
    }

    private static String embeddingId(String campaignId, int exchangeIndex) {
        return campaignId + ":" + exchangeIndex;
    }

    private static Metadata metadata(String campaignId, int exchangeIndex) {
        return new Metadata()
                .put("campaignId", campaignId)
                .put("exchangeIndex", exchangeIndex);
    }

    /** Return only narrative content, stripping mechanical entries and player markup. */
    private static String stripNonNarrative(String text) {
        return text.lines()
                .filter(line -> {
                    String trimmed = line.trim();
                    return !JournalParser.isMechanicalEntry(trimmed)
                            && !JournalParser.isPlayerEntry(trimmed)
                            && !JournalParser.isPlayerEntryEnd(trimmed);
                })
                .collect(java.util.stream.Collectors.joining("\n"))
                .trim();
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Objects.requireNonNullElse(text, "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
