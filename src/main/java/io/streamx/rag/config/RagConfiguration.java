package io.streamx.rag.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Type-safe config mapping for the whole RAG service.
 * Backed by application.properties / env vars.
 *
 * @author Łukasz
 */
@ConfigMapping(prefix = "rag")
public interface RagConfiguration {

    AemConfig aem();

    PimConfig pim();

    IngestionConfig ingestion();

    WebhookConfig webhook();

    interface AemConfig {
        String baseUrl();

        @WithDefault("/content/_cq_graphql/global/endpoint.json")
        String graphqlEndpoint();

        @WithDefault("admin")
        String username();

        @WithDefault("admin")
        String password();

        /**
         * AEM as a Cloud Service service token (Bearer auth).
         * When set, overrides username/password Basic Auth.
         * Generate via: AEM → Tools → Security → Adobe IMS.
         */
        Optional<String> serviceToken();

        @WithDefault("/content/streamx")
        String contentRoot();

        /**
         * GraphQL list query name generated from the CF model name.
         * AEM auto-generates "${modelName}List" from a model called "${modelName}".
         * Example: model "article" → query "articleList", model "blogPost" → "blogPostList".
         */
        @WithDefault("articleList")
        String cfListQuery();

        /**
         * Name of the Content Fragment field used as the document title.
         */
        @WithDefault("title")
        String cfTitleField();

        /**
         * Name of the Content Fragment RichText/MultiLine field used as the document body.
         * Use the plain-text variant if the field is RichText: e.g. "body" maps to "body { plaintext }".
         */
        @WithDefault("body")
        String cfBodyField();

        /**
         * Maximum number of Content Fragments fetched per GraphQL page.
         * The connector automatically paginates until all CFs are retrieved.
         */
        @WithDefault("200")
        int cfPageSize();

        /**
         * HTTP connect timeout in seconds for AEM requests.
         */
        @WithDefault("10")
        int httpConnectTimeoutSeconds();

        /**
         * HTTP request timeout in seconds for AEM requests.
         */
        @WithDefault("30")
        int httpRequestTimeoutSeconds();
    }

    interface PimConfig {
        String baseUrl();

        @WithDefault("/api/v1/products")
        String apiPath();

        Optional<String> apiKey();
    }

    interface IngestionConfig {
        @WithDefault("500")
        int chunkSize();

        @WithDefault("50")
        int chunkOverlap();

        @WithDefault("0 0 2 * * ?")
        String fullSyncCron();

        @WithDefault("15m")
        String deltaSyncInterval();
    }

    interface WebhookConfig {
        /**
         * Enable the AEM push webhook at POST /api/webhook/aem.
         * When false (default) the endpoint returns 404.
         * Set AEM_WEBHOOK_ENABLED=true in .env to activate.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * HMAC-SHA256 secret used to verify X-AEM-Signature headers sent by AEM.
         * When set, every webhook request must include:
         *   X-AEM-Signature: sha256=<hex(HMAC-SHA256(secret, requestBody))>
         * When empty, falls back to X-Admin-Key validation (same as admin endpoints).
         * Generate: openssl rand -hex 32
         */
        Optional<String> hmacSecret();
    }
}
