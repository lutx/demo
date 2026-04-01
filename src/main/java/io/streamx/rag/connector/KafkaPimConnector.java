package io.streamx.rag.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import io.streamx.rag.config.RagConfiguration;
import io.streamx.rag.ingestion.ContentDocumentParser;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real-time PIM product ingestion via Kafka.
 * Activated only when the "kafka" build profile is active:
 *   mvn quarkus:dev -Dquarkus.profile=kafka
 *
 * Expects messages on the "pim-products" topic with JSON product payloads.
 */
@ApplicationScoped
@IfBuildProfile("kafka")
public class KafkaPimConnector {

    private static final Logger LOG = Logger.getLogger(KafkaPimConnector.class);

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    RagConfiguration config;

    @Inject
    ContentDocumentParser documentParser;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("pim-products")
    public void onProductEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.path("eventType").asText("update");

            if ("delete".equals(eventType)) {
                LOG.infof("Product deleted event received, skipping ingestion: %s",
                        event.path("sku").asText(""));
                return;
            }

            JsonNode product = event.has("payload") ? event.path("payload") : event;
            ContentSource.RawContent rawContent = parseKafkaProduct(product);
            if (rawContent == null) return;

            Document doc = documentParser.toDocument(rawContent);
            if (doc == null) return;

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .documentSplitter(DocumentSplitters.recursive(
                            config.ingestion().chunkSize(),
                            config.ingestion().chunkOverlap()
                    ))
                    .build();

            ingestor.ingest(List.of(doc));
            LOG.infof("Kafka: ingested product %s", rawContent.id());
        } catch (Exception e) {
            LOG.error("Failed to process Kafka PIM event", e);
        }
    }

    private ContentSource.RawContent parseKafkaProduct(JsonNode product) {
        String id = product.path("sku").asText(product.path("id").asText(""));
        if (id.isEmpty()) return null;

        String name = product.path("name").asText("Unknown Product");
        String description = product.path("description").asText("");

        StringBuilder body = new StringBuilder();
        body.append("Product: ").append(name).append("\n");
        if (!description.isEmpty()) body.append(description).append("\n");

        String modified = product.path("lastModified").asText("");
        Instant lastModified;
        try {
            lastModified = modified.isEmpty() ? Instant.now() : Instant.parse(modified);
        } catch (Exception e) {
            lastModified = Instant.now();
        }

        Map<String, String> extra = new HashMap<>();
        extra.put("sku", id);
        if (product.has("category")) extra.put("category", product.path("category").asText(""));

        return new ContentSource.RawContent(
                id, name, body.toString(),
                config.pim().baseUrl() + "/products/" + id,
                "pim-product-kafka",
                lastModified, extra
        );
    }
}
