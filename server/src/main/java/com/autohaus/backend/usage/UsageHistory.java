package com.autohaus.backend.usage;

import java.time.Instant;
import java.util.Map;

/**
 * The deserialized blob file, keyed by ISO date / deserialisierte Blob-Datei, Schlüssel ist der
 * ISO-Kalendertag (§10.3). Contains anonymous integer counters only (NFR-Datenschutz-4).
 *
 * <p>The day is exclusively the map key ({@code days["2026-09-19"]}); the day objects carry no
 * {@code day} field — matching {@link DailyUsage} without a {@code day} component (§10.2). Keys
 * that cannot be parsed as an ISO {@code LocalDate} make the file unusable (E4). / Nicht
 * parsebare Keys führen zu E4.
 *
 * @param schemaVersion blob schema version, enables later format changes without data loss /
 *     ermöglicht spätere Formatänderungen ohne Datenverlust
 * @param updatedAt last successful write, ISO-8601 in JSON / letzter erfolgreicher Schreibvorgang
 * @param days day rows keyed by ISO date, e.g. {@code "2026-09-20"} / Tageszeilen je ISO-Datum
 */
public record UsageHistory(
    int schemaVersion,
    Instant updatedAt,
    Map<String, DailyUsage> days
) {

    /** Current blob schema version / aktuelle Schema-Version der Blob-Datei. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * An empty history — used when the blob does not exist yet or is unusable (E2/E4). Never
     * returns {@code null} fields. / Leere Historie; liefert nie {@code null}-Felder.
     */
    public static UsageHistory empty() {
        return new UsageHistory(CURRENT_SCHEMA_VERSION, null, Map.of());
    }
}
