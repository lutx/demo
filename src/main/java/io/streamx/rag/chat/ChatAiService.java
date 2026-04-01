package io.streamx.rag.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import io.streamx.rag.retrieval.RagRetrievalAugmentorSupplier;

@RegisterAiService(retrievalAugmentor = RagRetrievalAugmentorSupplier.class)
public interface ChatAiService {

    /**
     * Streams a GPT-4o response for the given question.
     *
     * <p>The system prompt is supplied dynamically from the active chat profile,
     * allowing behaviour to be changed at runtime without redeploying the service.
     *
     * @param sessionId    conversation memory key (one memory per session)
     * @param systemPrompt full system prompt for the active profile (with guardrails appended)
     * @param question     the user's question, passed as-is to preserve their language
     */
    @SystemMessage("{systemPrompt}")
    @UserMessage("{question}")
    Multi<String> chat(@MemoryId String sessionId,
                       @V("systemPrompt") String systemPrompt,
                       @V("question") String question);
}
