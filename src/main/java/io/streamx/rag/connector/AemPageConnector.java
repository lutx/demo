package io.streamx.rag.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches AEM Pages via the Sling Model JSON Exporter.
 * Recursively walks the content tree under the configured content root
 * and extracts text from page components.
 */
@ApplicationScoped
public class AemPageConnector implements ContentSource {

    private static final Logger LOG = Logger.getLogger(AemPageConnector.class);
    private static final String SOURCE_TYPE = "aem-page";

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
        return fetchPages(config.aem().contentRoot(), null);
    }

    @Override
    public List<RawContent> fetchSince(Instant since) {
        return fetchPages(config.aem().contentRoot(), since);
    }

    /**
     * Fetches a single AEM page by its JCR path via the Sling Model JSON Exporter.
     * Used by the webhook handler to upsert a specific page after AEM publishes it.
     *
     * @param path JCR path, e.g. {@code /content/my-site/en/products/nordic-sofa}
     * @return the page content, or empty if the page has no extractable text
     */
    public Optional<RawContent> fetchByPath(String path) {
        return Optional.ofNullable(fetchPageContent(path));
    }

    // Prevents infinite recursion on unexpectedly deep or circular JCR trees.
    private static final int MAX_PAGE_TREE_DEPTH = 10;

    private List<RawContent> fetchPages(String rootPath, Instant since) {
        try {
            List<String> pagePaths = new ArrayList<>();
            collectPagePathsRecursive(rootPath, pagePaths, 0);

            List<RawContent> results = new ArrayList<>();
            for (String pagePath : pagePaths) {
                RawContent content = fetchPageContent(pagePath);
                if (content != null) {
                    if (since == null || content.lastModified().isAfter(since)) {
                        results.add(content);
                    }
                }
            }

            LOG.infof("Fetched %d pages from AEM (root: %s)", results.size(), rootPath);
            return results;
        } catch (Exception e) {
            LOG.error("Failed to fetch AEM pages", e);
            return Collections.emptyList();
        }
    }

    /**
     * Recursively discovers all cq:Page nodes under {@code path} by fetching
     * {@code .1.json} at each level and descending into child pages.
     *
     * <p>Using {@code .1.json} (depth=1) per request rather than
     * {@code .infinity.json} avoids retrieving huge payloads for deep trees
     * while still traversing the full page hierarchy.
     */
    private void collectPagePathsRecursive(String path, List<String> paths, int depth) {
        if (depth > MAX_PAGE_TREE_DEPTH) {
            LOG.warnf("AEM page tree depth limit (%d) reached at path: %s — deeper pages skipped",
                    MAX_PAGE_TREE_DEPTH, path);
            return;
        }

        String json = fetchJson(path + ".1.json");
        if (json == null) return;

        try {
            JsonNode node = objectMapper.readTree(json);
            if ("cq:Page".equals(node.path("jcr:primaryType").asText())) {
                paths.add(path);
            }

            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                if (!name.startsWith("jcr:") && !name.startsWith("cq:") && !name.startsWith("sling:")) {
                    JsonNode child = node.get(name);
                    if (child.isObject() && "cq:Page".equals(child.path("jcr:primaryType").asText())) {
                        collectPagePathsRecursive(path + "/" + name, paths, depth + 1);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not traverse page tree at %s: %s", path, e.getMessage());
        }
    }

    private RawContent fetchPageContent(String pagePath) {
        try {
            String json = fetchJson(pagePath + ".model.json");
            if (json == null) return null;

            JsonNode page = objectMapper.readTree(json);
            String title = page.path("title").asText(page.path(":title").asText("Untitled"));
            String baseUrl = config.aem().baseUrl();

            StringBuilder textBuilder = new StringBuilder();
            extractText(page, textBuilder);
            String body = textBuilder.toString().trim();
            if (body.isEmpty()) return null;

            String modified = page.path("lastModified").asText(page.path(":lastModified").asText(""));
            Instant lastModified;
            try {
                lastModified = modified.isEmpty() ? Instant.now() : Instant.parse(modified);
            } catch (Exception e) {
                lastModified = Instant.now();
            }

            return new RawContent(
                    pagePath,
                    title,
                    body,
                    baseUrl + pagePath + ".html",
                    SOURCE_TYPE,
                    lastModified,
                    Map.of("template", page.path(":template").asText("unknown"))
            );
        } catch (Exception e) {
            LOG.warnf("Failed to parse page: %s", pagePath);
            return null;
        }
    }

    private void extractText(JsonNode node, StringBuilder builder) {
        if (node.has("text")) {
            String text = node.path("text").asText("").replaceAll("<[^>]+>", " ").trim();
            if (!text.isEmpty()) {
                builder.append(text).append("\n");
            }
        }
        if (node.has(":items")) {
            for (JsonNode item : node.path(":items")) {
                extractText(item, builder);
            }
        }
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            JsonNode child = node.get(field);
            if (child.isObject() && !field.startsWith(":") && !field.equals("text")) {
                extractText(child, builder);
            }
        }
    }

    private String fetchJson(String path) {
        try {
            String baseUrl = config.aem().baseUrl();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Authorization", buildAuthHeader())
                    .timeout(Duration.ofSeconds(config.aem().httpRequestTimeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (Exception e) {
            LOG.debugf("Could not fetch %s: %s", path, e.getMessage());
            return null;
        }
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
