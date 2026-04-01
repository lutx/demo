package io.streamx.rag.connector;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;
import io.streamx.rag.connector.ContentSource.RawContent;
import io.streamx.rag.ingestion.ContentDocumentParser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContentDocumentParserTest {

    private final ContentDocumentParser parser = new ContentDocumentParser();

    @Test
    void shouldParseRawContentToDocument() {
        RawContent raw = new RawContent(
                "cf-001",
                "Test Article",
                "This is the body of the article.",
                "http://localhost:4502/content/test",
                "aem-content-fragment",
                Instant.parse("2026-01-15T10:00:00Z"),
                Map.of("cfModel", "article")
        );

        List<Document> docs = parser.parse(List.of(raw));

        assertEquals(1, docs.size());
        Document doc = docs.get(0);
        assertEquals("This is the body of the article.", doc.text());
        assertEquals("aem-content-fragment", doc.metadata().getString("source_type"));
        assertEquals("Test Article", doc.metadata().getString("title"));
        assertEquals("http://localhost:4502/content/test", doc.metadata().getString("source_url"));
        assertEquals("article", doc.metadata().getString("cfModel"));
    }

    @Test
    void shouldSkipEmptyBody() {
        RawContent raw = new RawContent(
                "cf-002", "Empty", "", "http://example.com", "aem-page",
                Instant.now(), Map.of()
        );

        List<Document> docs = parser.parse(List.of(raw));
        assertTrue(docs.isEmpty());
    }

    @Test
    void shouldSkipNullBody() {
        RawContent raw = new RawContent(
                "cf-003", "Null Body", null, "http://example.com", "aem-page",
                Instant.now(), Map.of()
        );

        List<Document> docs = parser.parse(List.of(raw));
        assertTrue(docs.isEmpty());
    }
}
