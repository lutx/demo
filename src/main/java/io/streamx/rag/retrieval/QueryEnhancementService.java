package io.streamx.rag.retrieval;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Optional query enhancement that can be used to rewrite or expand user queries
 * before they hit the vector store. Useful for:
 * - Adding contextual keywords
 * - Translating abbreviations or domain-specific terms
 * - Expanding queries with synonyms
 */
@ApplicationScoped
public class QueryEnhancementService {

    /**
     * Enhances the raw user query for better retrieval.
     * Current implementation is pass-through; extend as needed.
     */
    public String enhance(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return rawQuery;
        }
        return rawQuery.trim();
    }
}
