package io.streamx.rag.profile;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A named configuration that shapes the chat assistant's behaviour.
 *
 * <p>Each profile carries its own system prompt, retrieval parameters, and an
 * optional topic block-list so different business use-cases can run off a single
 * deployment without a code change or redeploy.
 *
 * <p>Examples: "product-assistant", "customer-support", "technical-help".
 *
 * <p>The profile named {@code default} is used when the caller does not specify
 * a {@code profileName} in the chat request.
 */
@Entity
@Table(name = "chat_profiles")
public class ChatProfile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Unique slug used as the API identifier, e.g. "default", "customer-support". */
    @Column(unique = true, nullable = false, length = 100)
    public String name;

    /** Human-readable label shown in admin tooling. */
    @Column(name = "display_name", length = 200)
    public String displayName;

    /**
     * Full system prompt sent to GPT-4o for every request using this profile.
     * Supports plain text; a NEVER-discuss guardrail is appended automatically
     * when {@link #topicBlocklist} is non-empty.
     */
    @Column(name = "system_prompt", columnDefinition = "TEXT", nullable = false)
    public String systemPrompt;

    /**
     * How many vector-store chunks are retrieved per question.
     * Higher = richer context; lower = faster and cheaper.
     * Recommended range: 5–20. Default: 10.
     */
    @Column(name = "max_results")
    public int maxResults = 10;

    /**
     * Minimum cosine-similarity score for a chunk to be included in context.
     * Range 0–1. Lower = more results but more noise.
     * Recommended: 0.50 for product catalogs, 0.40 for broader knowledge bases.
     */
    @Column(name = "min_score")
    public double minScore = 0.50;

    /**
     * Comma-separated list of topics the assistant must not discuss.
     * Example: {@code "competitors,internal pricing,legal advice"}
     *
     * <p>Each entry is appended to the system prompt as a hard guardrail.
     * Leave blank to impose no restrictions.
     */
    @Column(name = "topic_blocklist", columnDefinition = "TEXT")
    public String topicBlocklist;

    /**
     * When false, chat requests for this profile fall back to the "default" profile.
     * Use this to temporarily disable a profile without deleting it.
     */
    @Column(name = "active", nullable = false)
    public boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();

    // ── factory ──────────────────────────────────────────────────────────────

    public static ChatProfile create(String name, String displayName, String systemPrompt) {
        ChatProfile p = new ChatProfile();
        p.name = name;
        p.displayName = displayName;
        p.systemPrompt = systemPrompt;
        return p;
    }

    // ── finders ──────────────────────────────────────────────────────────────

    public static ChatProfile findByName(String name) {
        return find("name", name).firstResult();
    }

    public static boolean existsByName(String name) {
        return count("name", name) > 0;
    }
}
