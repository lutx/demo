package io.streamx.rag.retrieval;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Rewrites vague follow-up questions using conversation history so they
 * become self-contained queries suitable for vector search.
 *
 * Example:
 *   History: "User: Show me sofas / Bot: Here is the Nordic Sofa..."
 *   Question: "What are its dimensions?"
 *   Output:  "dimensions of Nordic Sofa"
 */
@RegisterAiService
public interface ContextualizingAiService {

    @SystemMessage("""
            You help rewrite vague product search queries to be self-contained.
            Given a short conversation history and the user's latest question,
            rewrite the question so it can be understood without the conversation context.
            
            Rules:
            - Return ONLY the rewritten question — no explanations or extra text.
            - Replace pronouns (it, its, this, that, they, them, those, the one, the same)
              with the specific product name or attribute they refer to from the history.
            - If the question is already self-contained (no ambiguous references), return it UNCHANGED.
            - Keep the same language as the input question.
            - Keep product names, SKUs and numbers exactly as-is.
            """)
    @UserMessage("Conversation history (last messages):\n{history}\n\nLatest question: {question}")
    String contextualize(String history, String question);
}
