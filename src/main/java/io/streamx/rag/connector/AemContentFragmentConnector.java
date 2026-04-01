package io.streamx.rag.connector;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import io.streamx.rag.config.RagConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fetches AEM Content Fragments via the GraphQL persisted query API.
 *
 * Configurable via application.properties / env vars:
 *   rag.aem.cf-list-query      — GraphQL list query (default: articleList)
 *   rag.aem.cf-title-field     — CF field for title (default: title)
 *   rag.aem.cf-body-field      — CF field for body text (default: body)
 *   rag.aem.cf-page-size       — CFs per page for pagination (default: 200)
 *   rag.aem.service-token      — Bearer token for AEM Cloud (overrides Basic Auth)
 *   rag.aem.http-connect-timeout-seconds  — connect timeout (default: 10)
 *   rag.aem.http-request-timeout-seconds  — request timeout (default: 30)
 */
@ApplicationScoped
public class AemContentFragmentConnector implements ContentSource {

    private static final Logger LOG = Logger.getLogger(AemContentFragmentConnector.class);
    private static final String SOURCE_TYPE = "aem-content-fragment";

    @Inject
    RagConfiguration config;

    @Inject
    ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<RawContent> fetchAll() {
        return fetchAllPages(null);
    }

    @Override
    public List<RawContent> fetchSince(Instant since) {
        return fetchAllPages(since);
    }

    /**
     * Fetches a single Content Fragment by its JCR path.
     * Used by the webhook handler to upsert a specific CF after AEM publishes it.
     *
     * @param path JCR path, e.g. {@code /content/dam/my-site/articles/nordic-sofa}
     * @return the content, or empty if not found or body is blank
     */
    public Optional<RawContent> fetchByPath(String path) {
        String listQuery = config.aem().cfListQuery();
        // "articleList" → "articleByPath",  "blogPostList" → "blogPostByPath"
        String byPathQuery = listQuery.substring(0, listQuery.length() - "List".length()) + "ByPath";
        String titleField = config.aem().cfTitleField();
        String bodyField = config.aem().cfBodyField();

        // Escape path for safe embedding in a JSON/GraphQL string literal.
        // JCR paths should never contain these characters, but we defend against
        // malformed or injected input coming via the webhook body.
        String safePath = path.replace("\\", "\\\\").replace("\"", "\\\"");

        String queryBody = """
                {
                  "query": "{ %s(_path: \\"%s\\") { item { _path _metadata { stringMetadata { name value } } %s %s { plaintext } _modified } } }"
                }
                """.formatted(byPathQuery, safePath, titleField, bodyField);

        try {
            String baseUrl = config.aem().baseUrl();
            String endpoint = config.aem().graphqlEndpoint();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", buildAuthHeader())
                    .timeout(Duration.ofSeconds(config.aem().httpRequestTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(queryBody))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.errorf("AEM GraphQL byPath returned status %d for path %s", response.statusCode(), path);
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode item = root.path("data").path(byPathQuery).path("item");
            if (item.isMissingNode() || item.isNull()) {
                return Optional.empty();
            }

            String title = item.path(titleField).asText("Untitled");
            String body = item.path(bodyField).path("plaintext").asText("");
            if (body.isBlank()) {
                return Optional.empty();
            }

            String modified = item.path("_modified").asText("");
            Instant lastModified = modified.isEmpty() ? Instant.now() : Instant.parse(modified);

            return Optional.of(new RawContent(
                    path, title, body,
                    baseUrl + path,
                    SOURCE_TYPE, lastModified,
                    Map.of("cfModel", extractModel(item))
            ));
        } catch (Exception e) {
            LOG.errorf("Failed to fetch CF by path %s: %s", path, e.getMessage());
            return Optional.empty();
        }
    }

    private List<RawContent> fetchAllPages(Instant since) {
        List<RawContent> all = new ArrayList<>();
        int offset = 0;
        int pageSize = config.aem().cfPageSize();

        while (true) {
            List<RawContent> page = executeGraphQLQuery(buildListQuery(since, offset, pageSize));
            all.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }

        LOG.infof("Fetched %d content fragments from AEM (query: %s)",
                all.size(), config.aem().cfListQuery());
        return all;
    }

    private String buildListQuery(Instant since, int offset, int limit) {
        String modifiedFilter = since != null
                ? String.format(", filter: { _modified: { _after: \"%s\" } }", since.toString())
                : "";
        String bodyField = config.aem().cfBodyField();
        String titleField = config.aem().cfTitleField();
        String listQuery = config.aem().cfListQuery();

        return """
                {
                  "query": "{ %s(limit: %d, offset: %d%s) { items { _path _metadata { stringMetadata { name value } } %s %s { plaintext } _modified } } }"
                }
                """.formatted(listQuery, limit, offset, modifiedFilter, titleField, bodyField);
    }

    private List<RawContent> executeGraphQLQuery(String queryBody) {
        try {
            String baseUrl = config.aem().baseUrl();
            String endpoint = config.aem().graphqlEndpoint();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", buildAuthHeader())
                    .timeout(Duration.ofSeconds(config.aem().httpRequestTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(queryBody))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.errorf("AEM GraphQL returned status %d: %s", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            return parseGraphQLResponse(response.body(), baseUrl);
        } catch (Exception e) {
            LOG.error("Failed to fetch AEM Content Fragments", e);
            return Collections.emptyList();
        }
    }

    private List<RawContent> parseGraphQLResponse(String json, String baseUrl) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        String listQuery = config.aem().cfListQuery();
        JsonNode items = root.path("data").path(listQuery).path("items");

        if (!items.isArray()) {
            LOG.warnf("AEM GraphQL response for query '%s' returned no 'items' array. " +
                      "Check rag.aem.cf-list-query matches your CF model name.", listQuery);
            return Collections.emptyList();
        }

        String titleField = config.aem().cfTitleField();
        String bodyField = config.aem().cfBodyField();

        List<RawContent> results = new ArrayList<>();
        for (JsonNode item : items) {
            String path = item.path("_path").asText("");
            String title = item.path(titleField).asText("Untitled");
            String body = item.path(bodyField).path("plaintext").asText("");
            String modified = item.path("_modified").asText("");

            if (body.isBlank()) {
                LOG.debugf("CF at %s has empty body (field: %s) — skipping", path, bodyField);
                continue;
            }

            Instant lastModified = modified.isEmpty() ? Instant.now() : Instant.parse(modified);

            results.add(new RawContent(
                    path,
                    title,
                    body,
                    baseUrl + path,
                    SOURCE_TYPE,
                    lastModified,
                    Map.of("cfModel", extractModel(item))
            ));
        }

        return results;
    }

    private String buildAuthHeader() {
        return config.aem().serviceToken()
                .filter(t -> !t.isBlank())
                .map(t -> "Bearer " + t)
                .orElseGet(() -> {
                    String credentials = config.aem().username() + ":" + config.aem().password();
                    return "Basic " + Base64.getEncoder()
                            .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    private String extractModel(JsonNode item) {
        JsonNode metadata = item.path("_metadata").path("stringMetadata");
        if (metadata.isArray()) {
            for (JsonNode entry : metadata) {
                if ("cq:model".equals(entry.path("name").asText())) {
                    return entry.path("value").asText("");
                }
            }
        }
        return "unknown";
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(config.aem().httpConnectTimeoutSeconds()))
                            .build();
                }
            }
        }
        return httpClient;
    }
}
