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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches product data from the PIM REST API.
 * Supports pagination and delta sync via lastModified query param.
 */
@ApplicationScoped
public class PimProductConnector implements ContentSource {

    private static final Logger LOG = Logger.getLogger(PimProductConnector.class);
    private static final String SOURCE_TYPE = "pim-product";

    @Inject
    RagConfiguration config;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public List<RawContent> fetchAll() {
        return fetchProducts(null);
    }

    @Override
    public List<RawContent> fetchSince(Instant since) {
        return fetchProducts(since);
    }

    private List<RawContent> fetchProducts(Instant since) {
        List<RawContent> allProducts = new ArrayList<>();
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = buildUrl(page, since);
                String json = executeRequest(url);
                if (json == null) break;

                JsonNode root = objectMapper.readTree(json);
                JsonNode items = root.has("products") ? root.path("products")
                        : root.has("items") ? root.path("items")
                        : root.has("data") ? root.path("data") : root;

                if (!items.isArray() || items.isEmpty()) {
                    hasMore = false;
                    continue;
                }

                for (JsonNode product : items) {
                    RawContent content = parseProduct(product);
                    if (content != null) {
                        allProducts.add(content);
                    }
                }

                hasMore = root.has("hasMore") ? root.path("hasMore").asBoolean(false) : items.size() >= 100;
                page++;
            } catch (Exception e) {
                LOG.error("Failed to fetch PIM products page " + page, e);
                hasMore = false;
            }
        }

        LOG.infof("Fetched %d products from PIM", allProducts.size());
        return allProducts;
    }

    private String buildUrl(int page, Instant since) {
        String baseUrl = config.pim().baseUrl() + config.pim().apiPath();
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("?page=").append(page).append("&size=100");
        if (since != null) {
            url.append("&modifiedSince=").append(since.toString());
        }
        return url.toString();
    }

    private String executeRequest(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET();

            config.pim().apiKey().ifPresent(key -> builder.header("X-API-Key", key));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.errorf("PIM API returned status %d for %s", response.statusCode(), url);
                return null;
            }
            return response.body();
        } catch (Exception e) {
            LOG.error("PIM API request failed: " + url, e);
            return null;
        }
    }

    private RawContent parseProduct(JsonNode product) {
        String id = product.path("sku").asText(product.path("id").asText(""));
        if (id.isEmpty()) return null;

        String name = product.path("name").asText(product.path("title").asText("Unknown Product"));
        String description = product.path("description").asText("");
        String shortDesc = product.path("shortDescription").asText("");

        StringBuilder body = new StringBuilder();
        body.append("Product: ").append(name).append("\n");
        if (!shortDesc.isEmpty()) body.append(shortDesc).append("\n");
        if (!description.isEmpty()) body.append(description).append("\n");

        appendAttributes(product, body);

        String modified = product.path("lastModified").asText(product.path("updatedAt").asText(""));
        Instant lastModified;
        try {
            lastModified = modified.isEmpty() ? Instant.now() : Instant.parse(modified);
        } catch (Exception e) {
            lastModified = Instant.now();
        }

        Map<String, String> extra = new HashMap<>();
        extra.put("sku", id);
        if (product.has("category")) extra.put("category", product.path("category").asText(""));
        if (product.has("brand")) extra.put("brand", product.path("brand").asText(""));

        String sourceUrl = config.pim().baseUrl() + "/products/" + id;

        return new RawContent(id, name, body.toString(), sourceUrl, SOURCE_TYPE, lastModified, extra);
    }

    private void appendAttributes(JsonNode product, StringBuilder body) {
        JsonNode attrs = product.path("attributes");
        if (attrs.isObject()) {
            attrs.fields().forEachRemaining(entry ->
                    body.append(entry.getKey()).append(": ").append(entry.getValue().asText("")).append("\n")
            );
        } else if (attrs.isArray()) {
            for (JsonNode attr : attrs) {
                body.append(attr.path("name").asText("")).append(": ")
                        .append(attr.path("value").asText("")).append("\n");
            }
        }
    }
}
