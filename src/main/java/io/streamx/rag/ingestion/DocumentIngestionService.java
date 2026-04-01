package io.streamx.rag.ingestion;

// @author Łukasz

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import io.streamx.rag.config.RagConfiguration;
import io.streamx.rag.connector.ContentSource;
import io.streamx.rag.connector.ContentSource.RawContent;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

@ApplicationScoped
public class DocumentIngestionService {

    private static final Logger LOG = Logger.getLogger(DocumentIngestionService.class);

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    RagConfiguration config;

    @Inject
    ContentDocumentParser documentParser;

    @Inject
    Instance<ContentSource> contentSources;

    @Inject
    MeterRegistry meterRegistry;

    private final AtomicReference<Instant> lastSyncTimestamp = new AtomicReference<>(null);

    public IngestionResult runFullSync() {
        LOG.info("Starting full sync ingestion — clearing existing embeddings first...");

        // Deduplication: wipe the entire store before a full re-ingest to avoid
        // accumulating stale or duplicate vectors across runs.
        try {
            embeddingStore.removeAll();
            LOG.info("Embedding store cleared.");
        } catch (Exception e) {
            LOG.warnf("Could not clear embedding store (non-fatal): %s", e.getMessage());
        }

        int totalIngested = 0;
        EmbeddingStoreIngestor ingestor = buildIngestor();

        for (ContentSource source : contentSources) {
            try {
                List<RawContent> rawContents = source.fetchAll();
                List<Document> documents = documentParser.parse(rawContents);
                if (!documents.isEmpty()) {
                    ingestor.ingest(documents);
                    totalIngested += documents.size();
                    LOG.infof("Ingested %d documents from %s", documents.size(), source.sourceType());
                }
            } catch (Exception e) {
                LOG.errorf(e, "Failed to ingest from source: %s", source.sourceType());
            }
        }

        lastSyncTimestamp.set(Instant.now());
        meterRegistry.counter("rag.ingestion.documents", "sync_type", "full")
                .increment(totalIngested);
        LOG.infof("Full sync completed. Total documents ingested: %d", totalIngested);
        return new IngestionResult(totalIngested, "full");
    }

    public IngestionResult runDeltaSync() {
        Instant since = lastSyncTimestamp.get();
        if (since == null) {
            LOG.info("No previous sync timestamp — running full sync instead");
            return runFullSync();
        }

        LOG.infof("Starting delta sync (since %s)...", since);
        int totalIngested = 0;
        EmbeddingStoreIngestor ingestor = buildIngestor();

        for (ContentSource source : contentSources) {
            try {
                List<RawContent> rawContents = source.fetchSince(since);
                List<Document> documents = documentParser.parse(rawContents);
                if (!documents.isEmpty()) {
                    // Deduplication: remove old vectors for each changed document
                    // before inserting fresh ones.
                    for (Document doc : documents) {
                        String sourceUrl = doc.metadata()
                                .getString(ContentDocumentParser.META_SOURCE_URL);
                        if (sourceUrl != null && !sourceUrl.isBlank()) {
                            try {
                                embeddingStore.removeAll(
                                        MetadataFilterBuilder.metadataKey(
                                                ContentDocumentParser.META_SOURCE_URL)
                                                .isEqualTo(sourceUrl));
                            } catch (Exception e) {
                                LOG.debugf("removeAll by source_url not supported, skipping: %s",
                                        e.getMessage());
                            }
                        }
                    }
                    ingestor.ingest(documents);
                    totalIngested += documents.size();
                    LOG.infof("Delta ingested %d documents from %s",
                            documents.size(), source.sourceType());
                }
            } catch (Exception e) {
                LOG.errorf(e, "Delta sync failed for source: %s", source.sourceType());
            }
        }

        lastSyncTimestamp.set(Instant.now());
        meterRegistry.counter("rag.ingestion.documents", "sync_type", "delta")
                .increment(totalIngested);
        LOG.infof("Delta sync completed. Documents ingested: %d", totalIngested);
        return new IngestionResult(totalIngested, "delta");
    }

    public IngestionResult ingestFromSource(String sourceType) {
        EmbeddingStoreIngestor ingestor = buildIngestor();

        for (ContentSource source : contentSources) {
            if (source.sourceType().equals(sourceType)) {
                List<RawContent> rawContents = source.fetchAll();
                List<Document> documents = documentParser.parse(rawContents);
                if (!documents.isEmpty()) {
                    // Remove stale vectors for this source type before re-ingesting
                    try {
                        embeddingStore.removeAll(
                                MetadataFilterBuilder.metadataKey(
                                        ContentDocumentParser.META_SOURCE_TYPE)
                                        .isEqualTo(sourceType));
                    } catch (Exception e) {
                        LOG.debugf("removeAll by source_type not supported, continuing: %s",
                                e.getMessage());
                    }
                    ingestor.ingest(documents);
                }
                meterRegistry.counter("rag.ingestion.documents", "sync_type", "source")
                        .increment(documents.size());
                return new IngestionResult(documents.size(), "source:" + sourceType);
            }
        }

        throw new IllegalArgumentException("Unknown source type: " + sourceType);
    }

    /**
     * Upserts one or more documents from an external push source (StreamX, AEM workflow,
     * headless CMS, scripts, etc.). Each document is identified by its URL:
     * existing vectors for that URL are removed before new ones are stored.
     */
    public IngestionResult ingestDocuments(List<GenericDocumentRequest> requests) {
        EmbeddingStoreIngestor ingestor = buildIngestor();
        int count = 0;

        for (GenericDocumentRequest req : requests) {
            GenericDocumentRequest r = req.validate();

            // Upsert: remove stale vectors for this URL first
            removeByUrl(r.url());

            RawContent raw = new RawContent(
                    r.url(),
                    r.title(),
                    r.text(),
                    r.url(),
                    r.type(),
                    Instant.now(),
                    r.metadata() != null ? r.metadata() : Map.of()
            );

            Document doc = documentParser.toDocument(raw);
            if (doc != null) {
                ingestor.ingest(doc);
                count++;
            }
        }

        meterRegistry.counter("rag.ingestion.documents", "sync_type", "push").increment(count);
        LOG.infof("Generic push ingestion completed: %d document(s) upserted", count);
        return new IngestionResult(count, "push");
    }

    /**
     * Removes all vectors associated with the given source URL.
     * Returns true if the removal was attempted (even if no vectors existed for that URL).
     */
    public boolean deleteDocument(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("'url' is required");
        }
        removeByUrl(url.trim());
        LOG.infof("Deleted vectors for url: %s", url);
        return true;
    }

    private void removeByUrl(String url) {
        try {
            embeddingStore.removeAll(
                    MetadataFilterBuilder.metadataKey(ContentDocumentParser.META_SOURCE_URL)
                            .isEqualTo(url));
        } catch (Exception e) {
            LOG.debugf("removeAll by source_url not supported or no vectors found for %s: %s",
                    url, e.getMessage());
        }
    }

    private EmbeddingStoreIngestor buildIngestor() {
        return EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(DocumentSplitters.recursive(
                        config.ingestion().chunkSize(),
                        config.ingestion().chunkOverlap()
                ))
                .build();
    }

    public record IngestionResult(int documentCount, String syncType) {}
}
