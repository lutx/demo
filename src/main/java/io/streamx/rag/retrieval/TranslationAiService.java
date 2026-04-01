package io.streamx.rag.retrieval;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface TranslationAiService {

    @SystemMessage("""
            Translate the following product search query to English.
            Return ONLY the translated text — no explanations, no extra punctuation.
            If the query is already in English, return it unchanged.
            Keep product model names, SKUs and numbers exactly as-is.
            """)
    @UserMessage("{query}")
    String translate(String query);
}
