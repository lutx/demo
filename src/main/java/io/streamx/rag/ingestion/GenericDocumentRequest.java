package io.streamx.rag.ingestion;

import java.util.List;
import java.util.Map;

/**
 * Generic content document pushed by any external system (StreamX, AEM workflow,
 * headless CMS, custom script, etc.) directly into the RAG knowledge base.
 *
 * <p>Single document:
 * <pre>
 * POST /api/admin/ingest/document
 * {"url":"https://example.com/page","title":"Page title","text":"Full page text..."}
 * </pre>
 *
 * <p>Bulk (up to 500 documents per call):
 * <pre>
 * POST /api/admin/ingest/documents
 * {"documents":[{"url":"...","title":"...","text":"..."},...]}
 * </pre>
 *
 * @author Łukasz
 */
public record GenericDocumentRequest(

        /**
         * Unique identifier and source URL of the document.
         * Used as the deduplication key — re-posting the same URL replaces
         * the existing vectors with fresh ones (upsert semantics).
         * Required.
         */
        String url,

        /**
         * Human-readable document title shown in chat citations.
         * Optional — defaults to the URL if omitted.
         */
        String title,

        /**
         * Full plain-text content to embed.
         * HTML tags are not stripped automatically — send clean text.
         * Required.
         */
        String text,

        /**
         * Content type label for filtering / analytics (e.g. "page", "article",
         * "product", "faq"). Optional — defaults to "generic".
         */
        String type,

        /**
         * Arbitrary key-value metadata forwarded to the vector store.
         * Optional. Example: {"lang":"en","section":"support"}
         */
        Map<String, String> metadata

) {
    /** Validates required fields and returns a normalised copy. */
    public GenericDocumentRequest validate() {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("'url' is required");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("'text' is required");
        }
        String resolvedTitle = (title != null && !title.isBlank()) ? title : url;
        String resolvedType  = (type  != null && !type.isBlank())  ? type  : "generic";
        return new GenericDocumentRequest(url.trim(), resolvedTitle, text, resolvedType, metadata);
    }

    /** Bulk wrapper — up to 500 documents per call. */
    public record Bulk(List<GenericDocumentRequest> documents) {}
}
