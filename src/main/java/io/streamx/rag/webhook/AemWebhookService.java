package io.streamx.rag.webhook;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.streamx.rag.config.RagConfiguration;
import io.streamx.rag.connector.AemContentFragmentConnector;
import io.streamx.rag.connector.AemPageConnector;
import io.streamx.rag.connector.ContentSource.RawContent;
import io.streamx.rag.ingestion.ContentDocumentParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Processes AEM push webhook events.
 *
 * Activate  → fetch content from AEM, remove stale vectors, ingest fresh chunks.
 * Deactivate / Delete → remove all vectors for the given path.
 *
 * @author Łukasz
 */
@ApplicationScoped
public class AemWebhookService {

    private static final Logger LOG = Logger.getLogger(AemWebhookService.class);

    @Inject AemContentFragmentConnector cfConnector;
    @Inject AemPageConnector pageConnector;
    @Inject ContentDocumentParser documentParser;
    @Inject EmbeddingStore<TextSegment> embeddingStore;
    @Inject EmbeddingModel embeddingModel;
    @Inject RagConfiguration config;
    @Inject MeterRegistry meterRegistry;

    public WebhookResult handle(WebhookEvent event) {
        boolean isDelete = "Deactivate".equalsIgnoreCase(event.action())
                        || "Delete".equalsIgnoreCase(event.action());

        int upserted = 0;
        int deleted  = 0;

        for (String path : event.paths()) {
            if (isDelete) {
                removeByPath(path);
                deleted++;
            } else {
                if (upsertByPath(path, event.type())) {
                    upserted++;
                }
            }
        }

        // Tag only on action (low cardinality: Activate / Deactivate / Delete).
        // upserted/deleted are counts, not dimensions — putting them in tags creates
        // unbounded Prometheus cardinality and inflates the metrics registry.
        meterRegistry.counter("rag.webhook.events.total", "action", event.action()).increment();
        meterRegistry.counter("rag.webhook.paths.upserted").increment(upserted);
        meterRegistry.counter("rag.webhook.paths.deleted").increment(deleted);

        LOG.infof("Webhook processed: action=%s upserted=%d deleted=%d",
                event.action(), upserted, deleted);

        return new WebhookResult(event.action(), upserted, deleted);
    }

    // ── upsert ────────────────────────────────────────────────────────────────

    private boolean upsertByPath(String path, String typeHint) {
        ContentType type = detectType(path, typeHint);
        Optional<RawContent> raw = fetchContent(path, type);

        if (raw.isEmpty()) {
            LOG.warnf("Webhook upsert: no content returned for path=%s type=%s", path, type);
            return false;
        }

        Document doc = documentParser.toDocument(raw.get());
        if (doc == null) {
            return false;
        }

        // Remove stale vectors for this exact path before re-ingesting
        removeByPath(path);

        buildIngestor().ingest(doc);
        LOG.infof("Webhook upsert OK: path=%s type=%s", path, type);
        return true;
    }

    private Optional<RawContent> fetchContent(String path, ContentType type) {
        return switch (type) {
            case CONTENT_FRAGMENT -> cfConnector.fetchByPath(path);
            case PAGE              -> pageConnector.fetchByPath(path);
        };
    }

    // ── delete ────────────────────────────────────────────────────────────────

    private void removeByPath(String path) {
        try {
            embeddingStore.removeAll(
                    MetadataFilterBuilder.metadataKey(ContentDocumentParser.META_CONTENT_ID)
                            .isEqualTo(path));
            LOG.debugf("Removed vectors for path=%s", path);
        } catch (Exception e) {
            LOG.warnf("Could not remove vectors for path=%s: %s", path, e.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Determines content type from the explicit hint or from the path pattern:
     * AEM Content Fragments live under /content/dam/ by convention.
     */
    private ContentType detectType(String path, String typeHint) {
        if ("aem-content-fragment".equalsIgnoreCase(typeHint)) return ContentType.CONTENT_FRAGMENT;
        if ("aem-page".equalsIgnoreCase(typeHint))             return ContentType.PAGE;
        return path.startsWith("/content/dam/") ? ContentType.CONTENT_FRAGMENT : ContentType.PAGE;
    }

    private EmbeddingStoreIngestor buildIngestor() {
        return EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(DocumentSplitters.recursive(
                        config.ingestion().chunkSize(),
                        config.ingestion().chunkOverlap()))
                .build();
    }

    enum ContentType { CONTENT_FRAGMENT, PAGE }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /**
     * Event payload sent by AEM.
     *
     * <pre>
     * {
     *   "action": "Activate",               // "Activate" | "Deactivate" | "Delete"
     *   "paths": ["/content/dam/articles/nordic-sofa"],
     *   "type":  "aem-content-fragment"     // optional — "aem-content-fragment" | "aem-page"
     * }
     * </pre>
     */
    public record WebhookEvent(String action, java.util.List<String> paths, String type) {}

    /** Response returned to AEM after processing. */
    public record WebhookResult(String action, int upserted, int deleted) {}
}
