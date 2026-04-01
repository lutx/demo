package io.streamx.rag.profile;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped holder for the chat profile resolved for the current HTTP request.
 *
 * <p>{@link io.streamx.rag.chat.ChatResource} resolves the profile from the
 * database once and stores it here. The {@link io.streamx.rag.retrieval.RagRetrievalAugmentorSupplier}
 * then reads {@code maxResults} and {@code minScore} from this bean without an
 * additional DB lookup.
 *
 * <p>CDI automatically creates one instance per HTTP request and tears it down
 * when the request completes. An {@code @ApplicationScoped} bean that injects
 * this class receives a transparent proxy that delegates to the correct
 * per-request instance.
 */
@RequestScoped
public class ActiveProfile {

    private ChatProfile profile;

    public void set(ChatProfile profile) {
        this.profile = profile;
    }

    public ChatProfile get() {
        return profile;
    }

    /** Convenience: retrieval max results, falls back to 10 if not yet set. */
    public int maxResults() {
        return profile != null ? profile.maxResults : 10;
    }

    /** Convenience: retrieval min score, falls back to 0.50 if not yet set. */
    public double minScore() {
        return profile != null ? profile.minScore : 0.50;
    }
}
