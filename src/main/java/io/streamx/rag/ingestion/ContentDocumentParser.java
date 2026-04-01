package io.streamx.rag.ingestion;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import jakarta.enterprise.context.ApplicationScoped;
import io.streamx.rag.connector.ContentSource.RawContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts RawContent from any connector into LangChain4j Documents,
 * attaching standardized metadata for source tracking and citation generation.
 */
@ApplicationScoped
public class ContentDocumentParser {

    public static final String META_SOURCE_TYPE = "source_type";
    public static final String META_SOURCE_URL = "source_url";
    public static final String META_TITLE = "title";
    public static final String META_LAST_MODIFIED = "last_modified";
    public static final String META_CONTENT_ID = "content_id";

    public List<Document> parse(List<RawContent> rawContents) {
        List<Document> documents = new ArrayList<>(rawContents.size());
        for (RawContent raw : rawContents) {
            Document doc = toDocument(raw);
            if (doc != null) {
                documents.add(doc);
            }
        }
        return documents;
    }

    public Document toDocument(RawContent raw) {
        if (raw.body() == null || raw.body().isBlank()) {
            return null;
        }

        Metadata metadata = new Metadata();
        metadata.put(META_SOURCE_TYPE, raw.sourceType());
        metadata.put(META_SOURCE_URL, raw.sourceUrl());
        metadata.put(META_TITLE, raw.title());
        metadata.put(META_LAST_MODIFIED, raw.lastModified().toString());
        metadata.put(META_CONTENT_ID, raw.id());

        if (raw.extraMetadata() != null) {
            raw.extraMetadata().forEach(metadata::put);
        }

        return Document.from(raw.body(), metadata);
    }
}
