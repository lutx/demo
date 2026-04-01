package io.streamx.rag.profile;

/**
 * Payload for creating or updating a chat profile.
 *
 * <p>All fields are optional for updates (PATCH semantics) — only non-null
 * values are applied. For creation, {@code name} and {@code systemPrompt}
 * are required.
 *
 * <p>Example body:
 * <pre>{@code
 * {
 *   "name":           "customer-support",
 *   "displayName":    "Customer Support Assistant",
 *   "systemPrompt":   "You are a friendly customer support agent for Acme Corp...",
 *   "maxResults":     8,
 *   "minScore":       0.45,
 *   "topicBlocklist": "competitors, internal pricing, legal advice",
 *   "active":         true
 * }
 * }</pre>
 */
public record ChatProfileRequest(
        String name,
        String displayName,
        String systemPrompt,
        Integer maxResults,
        Double minScore,
        String topicBlocklist,
        Boolean active
) {}
