package io.streamx.rag.connector;

import java.time.Instant;
import java.util.List;

/**
 * Abstraction for content sources that feed the RAG ingestion pipeline.
 * Each source provides raw content items that will be parsed into LangChain4j Documents.
 */
public interface ContentSource {

    String sourceType();

    List<RawContent> fetchAll();

    List<RawContent> fetchSince(Instant since);

    record RawContent(
            String id,
            String title,
            String body,
            String sourceUrl,
            String sourceType,
            Instant lastModified,
            java.util.Map<String, String> extraMetadata
    ) {}
}
