package io.streamx.rag.retrieval;

import dev.langchain4j.rag.query.Query;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class TranslatingQueryTransformerTest {

    @Inject
    TranslatingQueryTransformer transformer;

    @InjectMock
    TranslationAiService translationService;

    @InjectMock
    ContextualizingAiService contextualizingService;

    @Test
    void shouldTranslatePolishQueryToEnglish() {
        when(translationService.translate("najtańsza kanapa")).thenReturn("cheapest sofa");

        Collection<Query> result = transformer.transform(Query.from("najtańsza kanapa"));

        assertEquals(1, result.size());
        assertEquals("cheapest sofa", result.iterator().next().text());
    }

    @Test
    void shouldReturnOriginalOnTranslationFailure() {
        when(translationService.translate(anyString()))
                .thenThrow(new RuntimeException("OpenAI error"));

        Collection<Query> result = transformer.transform(Query.from("Show me sofas"));

        assertEquals(1, result.size());
        assertEquals("Show me sofas", result.iterator().next().text());
    }

    @Test
    void shouldReturnEmptyQueryUnchanged() {
        Collection<Query> result = transformer.transform(Query.from(""));

        assertEquals(1, result.size());
        assertEquals("", result.iterator().next().text());
    }

    @Test
    void shouldContextualizeVaguePronounQuery() {
        when(contextualizingService.contextualize(anyString(), anyString()))
                .thenReturn("dimensions of Nordic Sofa");
        when(translationService.translate("dimensions of Nordic Sofa"))
                .thenReturn("dimensions of Nordic Sofa");

        Query vague = Query.from("What are its dimensions?");
        Collection<Query> result = transformer.transform(vague);

        assertEquals(1, result.size());
        assertEquals("dimensions of Nordic Sofa", result.iterator().next().text());
    }

    @Test
    void shouldNotContextualizeSelfContainedQuery() {
        when(translationService.translate("cheapest lamp")).thenReturn("cheapest lamp");

        Collection<Query> result = transformer.transform(Query.from("cheapest lamp"));

        assertEquals(1, result.size());
        assertEquals("cheapest lamp", result.iterator().next().text());
    }
}
