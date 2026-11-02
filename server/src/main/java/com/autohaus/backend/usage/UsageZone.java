package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one resolution of {@code usage.zone} for the REQ-038 components (§14.1, BR-7) / die eine
 * Auflösung von {@code usage.zone} für die REQ-038-Komponenten.
 *
 * <p>Replaces the private {@code resolveZone} copies of the insights, capacity and reporting
 * classes with one semantics: a missing value ({@code null} properties or zone — not configured,
 * e.g. in tests) is {@value #DEFAULT_ZONE_ID} without a log line; a blank or unknown value is also
 * {@value #DEFAULT_ZONE_ID}, logged as {@code WARN} <b>once</b> per distinct value and JVM, so every
 * bean resolving the same misconfiguration does not repeat it. A valid value is trimmed. / Fehlend →
 * Berlin ohne Log; leer oder unbekannt → Berlin mit genau einem WARN je Wert.
 *
 * <p>The REQ-037 classes keep their own copies unchanged (existing REQs are not modified). /
 * REQ-037 bleibt unverändert.
 */
public final class UsageZone {

    private static final Logger log = LoggerFactory.getLogger(UsageZone.class);

    /** Fallback zone id / Rückfall-Zeitzone. */
    public static final String DEFAULT_ZONE_ID = "Europe/Berlin";

    /** Fallback zone / Rückfall-Zeitzone. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of(DEFAULT_ZONE_ID);

    /** Values already warned about / bereits gemeldete Werte. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private UsageZone() {
    }

    /**
     * The zone of {@code usage.zone} / die Zeitzone aus {@code usage.zone}.
     *
     * @param properties REQ-037 usage properties, may be {@code null} / darf {@code null} sein
     * @return the configured zone or {@value #DEFAULT_ZONE_ID} / konfigurierte Zone oder Berlin
     */
    public static ZoneId resolve(UsageStatsProperties properties) {
        return resolve(properties == null ? null : properties.zone());
    }

    /**
     * The zone of a configured value / die Zeitzone eines konfigurierten Werts.
     *
     * @param configured the raw value, may be {@code null} / Rohwert, darf {@code null} sein
     * @return the trimmed zone or {@value #DEFAULT_ZONE_ID} / getrimmte Zone oder Berlin
     */
    public static ZoneId resolve(String configured) {
        if (configured == null) {
            return DEFAULT_ZONE;
        }
        String trimmed = configured.trim();
        if (!trimmed.isEmpty()) {
            try {
                return ZoneId.of(trimmed);
            } catch (DateTimeException e) {
                // unknown or malformed id — handled below / unbekannt oder fehlerhaft
            }
        }
        if (WARNED.add(trimmed)) {
            log.warn("usage.zone '{}' is blank or not a valid time zone — falling back to {}",
                trimmed, DEFAULT_ZONE_ID);
        }
        return DEFAULT_ZONE;
    }

    /** Visible for testing / für Tests: forgets the values already warned about. */
    static void forgetWarnings() {
        WARNED.clear();
    }
}
