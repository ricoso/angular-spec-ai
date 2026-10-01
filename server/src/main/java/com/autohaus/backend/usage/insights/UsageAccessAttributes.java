package com.autohaus.backend.usage.insights;

/**
 * Request-attribute keys handed from controllers to the access-log filter (REQ-038 N1, §10.8) /
 * Request-Attribut-Schlüssel zwischen Controller und Access-Log-Filter.
 *
 * <p>{@code SnapsController} sets {@link #SEARCH_HITS} (a {@link SearchHits} value) after a
 * successful search; {@code LoggingFilter} appends {@code ,"hits":"…"} to the access line — only
 * while {@code usage.enabled}, {@code usage.funnel.enabled} and {@code usage.dropoff.enabled} are
 * all active (BR-29, AC-34). / Nur bei allen drei aktiven Schaltern entsteht das Feld.
 */
public final class UsageAccessAttributes {

    /** Attribute carrying the {@link SearchHits} value of a successful {@code POST /snaps}. */
    public static final String SEARCH_HITS = "com.autohaus.backend.usage.insights.SEARCH_HITS";

    private UsageAccessAttributes() {
    }
}
