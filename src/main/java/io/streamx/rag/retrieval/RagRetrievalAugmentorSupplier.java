package io.streamx.rag.retrieval;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.streamx.rag.profile.ActiveProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.function.Supplier;

@ApplicationScoped
public class RagRetrievalAugmentorSupplier implements Supplier<RetrievalAugmentor> {

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    TranslatingQueryTransformer queryTransformer;

    /**
     * CDI injects a request-scoped proxy here. Each call to {@code activeProfile.maxResults()}
     * and {@code activeProfile.minScore()} is delegated to the per-request instance populated
     * by {@link io.streamx.rag.chat.ChatResource} before the LangChain4j pipeline runs.
     */
    @Inject
    ActiveProfile activeProfile;

    @Override
    public RetrievalAugmentor get() {
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(activeProfile.maxResults())
                .minScore(activeProfile.minScore())
                .build();

        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .build();
    }
}
