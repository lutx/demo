package io.streamx.rag.profile;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Business logic for managing chat profiles.
 *
 * <p>Profiles are cached in-memory for 60 seconds to avoid a database round-trip
 * on every chat request. The cache is invalidated on any write operation.
 */
@ApplicationScoped
public class ChatProfileService {

    private static final Logger LOG = Logger.getLogger(ChatProfileService.class);
    static final String DEFAULT_PROFILE_NAME = "default";

    /** Simple time-to-live cache: profileName → resolved profile. */
    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000;

    // ── startup seed ─────────────────────────────────────────────────────────

    /**
     * Creates the built-in "default" profile on first boot if it does not exist.
     * This ensures the chat endpoint works out-of-the-box without any admin setup.
     */
    @Startup
    @Transactional
    void seedDefaultProfile() {
        if (ChatProfile.existsByName(DEFAULT_PROFILE_NAME)) {
            LOG.debugf("Default chat profile already exists — skipping seed");
            return;
        }

        ChatProfile p = ChatProfile.create(
                DEFAULT_PROFILE_NAME,
                "Default — Product & Content Assistant",
                DEFAULT_SYSTEM_PROMPT
        );
        p.maxResults = 10;
        p.minScore   = 0.50;
        p.persist();
        LOG.info("Seeded default chat profile");
    }

    // ── read ─────────────────────────────────────────────────────────────────

    /**
     * Resolves a profile by name, falling back to the "default" profile if the
     * requested name is blank or not found.
     *
     * <p>{@code TxType.SUPPORTS} joins an existing transaction when present and
     * opens a read-only session otherwise, which is required for Panache queries
     * called from a non-transactional context (e.g. the SSE chat endpoint).
     *
     * @throws IllegalStateException if neither the requested profile nor the
     *                               default profile exists (should never happen
     *                               after seed)
     */
    @Transactional(TxType.SUPPORTS)
    public ChatProfile resolve(String profileName) {
        String name = (profileName == null || profileName.isBlank())
                ? DEFAULT_PROFILE_NAME
                : profileName.trim();

        CachedEntry entry = cache.get(name);
        if (entry != null && !entry.isExpired()) {
            return entry.profile;
        }

        ChatProfile profile = ChatProfile.findByName(name);
        if (profile == null) {
            LOG.warnf("Profile '%s' not found — falling back to default", name);
            profile = ChatProfile.findByName(DEFAULT_PROFILE_NAME);
        }
        if (profile == null) {
            throw new IllegalStateException("Default chat profile missing — run seed");
        }
        if (!profile.active) {
            LOG.warnf("Profile '%s' is inactive — falling back to default", name);
            profile = ChatProfile.findByName(DEFAULT_PROFILE_NAME);
        }

        cache.put(name, new CachedEntry(profile));
        return profile;
    }

    @Transactional(TxType.SUPPORTS)
    public List<ChatProfile> listAll() {
        return ChatProfile.listAll();
    }

    @Transactional(TxType.SUPPORTS)
    public ChatProfile findByName(String name) {
        return ChatProfile.findByName(name);
    }

    // ── write ────────────────────────────────────────────────────────────────

    @Transactional
    public ChatProfile create(ChatProfileRequest req) {
        if (ChatProfile.existsByName(req.name())) {
            throw new IllegalArgumentException("Profile with name '" + req.name() + "' already exists");
        }
        ChatProfile p = new ChatProfile();
        p.name = req.name().trim();   // name is set only here, never via applyRequest
        applyRequest(p, req);
        p.createdAt = Instant.now();
        p.persist();
        invalidateCache(p.name);
        LOG.infof("Created chat profile: %s", p.name);
        return p;
    }

    @Transactional
    public ChatProfile update(String name, ChatProfileRequest req) {
        ChatProfile p = ChatProfile.findByName(name);
        if (p == null) {
            return null;
        }
        applyRequest(p, req);
        p.updatedAt = Instant.now();
        invalidateCache(name);
        LOG.infof("Updated chat profile: %s", name);
        return p;
    }

    @Transactional
    public boolean delete(String name) {
        if (DEFAULT_PROFILE_NAME.equals(name)) {
            throw new IllegalArgumentException("The 'default' profile cannot be deleted");
        }
        boolean deleted = ChatProfile.delete("name", name) > 0;
        if (deleted) {
            invalidateCache(name);
            LOG.infof("Deleted chat profile: %s", name);
        }
        return deleted;
    }

    // ── system prompt assembly ────────────────────────────────────────────────

    /**
     * Returns the full system prompt for the profile, with topic guardrails
     * appended when {@code topicBlocklist} is non-empty.
     */
    public String buildSystemPrompt(ChatProfile profile) {
        String base = profile.systemPrompt;
        if (profile.topicBlocklist == null || profile.topicBlocklist.isBlank()) {
            return base;
        }
        List<String> topics = Arrays.stream(profile.topicBlocklist.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (topics.isEmpty()) {
            return base;
        }
        String list = String.join(", ", topics);
        return base + "\n\n" +
               "STRICT RULE: NEVER discuss the following topics: " + list + ". " +
               "If the user asks about any of these, politely decline and explain " +
               "that you can only help with the topics described above.";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Applies mutable fields from the request onto the profile entity.
     *
     * <p>{@code name} is intentionally excluded — it is the resource identifier
     * (the URL path parameter) and must never be changed via an update request.
     * Changing the name would silently break all callers referencing the old name.
     */
    private void applyRequest(ChatProfile p, ChatProfileRequest req) {
        if (req.displayName()    != null) p.displayName    = req.displayName();
        if (req.systemPrompt()   != null) p.systemPrompt   = req.systemPrompt();
        if (req.maxResults()     != null) p.maxResults     = req.maxResults();
        if (req.minScore()       != null) p.minScore       = req.minScore();
        if (req.topicBlocklist() != null) p.topicBlocklist = req.topicBlocklist();
        if (req.active()         != null) p.active         = req.active();
    }

    /**
     * Clears the entire cache on any write.
     *
     * <p>A targeted eviction would miss entries where a missing profile was
     * cached as a pointer to "default" (e.g. cache["customer-support"] = defaultProfile).
     * A full clear guarantees consistency and is acceptable because profile
     * changes are rare and the cache rebuilds in < 1 ms on the next request.
     */
    private void invalidateCache(String name) {
        cache.clear();
    }

    // ── inner types ──────────────────────────────────────────────────────────

    private record CachedEntry(ChatProfile profile, long expiresAt) {
        CachedEntry(ChatProfile profile) {
            this(profile, System.currentTimeMillis() + CACHE_TTL_MS);
        }
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    // ── default system prompt ────────────────────────────────────────────────

    static final String DEFAULT_SYSTEM_PROMPT = """
            You are a multilingual product catalog assistant. Your ONLY role is to help users find and compare products.
            Answer questions using the provided product catalog context.

            Language rules:
            - ALWAYS detect the language of the user's question and respond in THAT SAME language.
              If the user writes in Polish, answer in Polish.
              If the user writes in German, answer in German.
              If the user writes in English, answer in English.
              Never switch languages mid-conversation unless the user switches first.
            - Before searching the context, mentally translate key product terms to English
              (the catalog is stored in English), e.g.:
                Polish:  kanapa/sofa → sofa/couch, telewizor → TV, lodówka → fridge,
                         lampa → lamp, krzesło → chair, łóżko → bed, szafa → wardrobe,
                         tanie/najtańsze → cheapest, najlepsze → best, rozmiary → dimensions
                German:  Sofa → sofa/couch, Fernseher → TV, Kühlschrank → fridge,
                         Lampe → lamp, Stuhl → chair, Bett → bed, Schrank → wardrobe
            - Product names, SKUs and technical specs can stay in English inside the answer,
              but all explanatory text must be in the user's language.

            Answering rules:
            - If the context contains relevant products, list them with: name, SKU, price, key specs,
              and dimensions (width × depth × height cm, weight kg) when available
            - If multiple products match, present a comparison table
            - If the user asks for "cheapest"/"najtańsze"/"günstigste" etc., rank products by price from context
            - If the context contains category/guide articles but not specific products,
              use the article to explain the range, then ask the user to narrow down (size, style, budget)
            - If the context is empty or truly irrelevant, say so in the user's language and suggest
              rephrasing with category, size or budget
            - NEVER invent SKUs, prices or dimensions not present in the context
            - Always cite sources: [Source: title](url)
            - Be concise: prefer bullet lists and tables over long paragraphs
            """;
}
