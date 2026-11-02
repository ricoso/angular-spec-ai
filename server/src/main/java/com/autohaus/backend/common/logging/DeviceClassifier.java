package com.autohaus.backend.common.logging;

import java.util.Locale;

/**
 * Derives the anonymous device class from a user agent (REQ-037 BR-2). Returns exactly one of the
 * four values {@link #MOBILE}, {@link #TABLET}, {@link #DESKTOP}, {@link #UNKNOWN} — there is
 * deliberately no {@code bot} class (bots run no JavaScript, produce no {@code sessionId} and are
 * filtered out by BR-4 anyway).
 *
 * <p>Leitet aus einem User-Agent die anonyme Geräteklasse ab. Gibt ausschließlich einen der vier
 * Werte zurück; der rohe User-Agent wird weder geloggt noch gespeichert (REQ-037 BR-3).
 *
 * <p><b>BR-3 (anonymity by construction / Anonymität durch Konstruktion):</b> This class has no
 * logger, no field and no cache. The raw {@code User-Agent} and the client hint
 * {@code Sec-CH-UA-Mobile} exist only as method parameters and are never put into a log line, a
 * debug statement or an exception message — not even truncated. Do not add logging here. /
 * Kein Logger, kein Feld, keine Zwischenspeicherung: Die Roh-Header dürfen diese Klasse nicht
 * verlassen (TC-3, TC-3b, TC-3c).
 *
 * <p><b>Known limitation / bekannte Grenze:</b> iPadOS Safari in desktop mode sends a Macintosh
 * user agent and is counted as {@code desktop}; telling it apart would require fingerprinting and
 * is therefore excluded (BR-3). Tablets are thus slightly undercounted. / Tablets werden dadurch
 * systematisch leicht unterzählt.
 */
public final class DeviceClassifier {

    /** Mobile phones / Mobiltelefone. */
    public static final String MOBILE = "mobile";

    /** Tablets / Tablets. */
    public static final String TABLET = "tablet";

    /** Desktop and laptop browsers / Desktop- und Laptop-Browser. */
    public static final String DESKTOP = "desktop";

    /** Not classifiable — also the fallback for foreign values (BR-1). / Nicht klassifizierbar. */
    public static final String UNKNOWN = "unknown";

    /**
     * Defensive cap — pathological headers must not cost CPU. / Defensive Kappung: Übergroße
     * Header dürfen die Latenz von Kunden-Requests nicht messbar erhöhen (§9 Performance).
     */
    public static final int MAX_USER_AGENT_CHARS = 512;

    /** Client hint value for "is a mobile device" / Client-Hint-Wert „mobil". */
    private static final String CH_UA_MOBILE_TRUE = "?1";

    /** Client hint value for "is not a mobile device" / Client-Hint-Wert „nicht mobil". */
    private static final String CH_UA_MOBILE_FALSE = "?0";

    private DeviceClassifier() {
    }

    /**
     * Classifies a request into one of the four device classes. The user agent is capped at
     * {@link #MAX_USER_AGENT_CHARS} characters and lower-cased with {@link Locale#ROOT}; the rules
     * of BR-2 are evaluated <b>in this exact order</b>, the first match wins:
     *
     * <ol>
     *   <li>{@code null}, empty or blank → {@link #UNKNOWN}</li>
     *   <li>{@code ipad}, {@code tablet}, {@code kindle}, {@code silk}, {@code playbook} or
     *       ({@code android} and <b>not</b> {@code mobi}) → {@link #TABLET}</li>
     *   <li>{@code mobi}, {@code iphone}, {@code ipod}, {@code android}, {@code windows phone},
     *       {@code blackberry} → {@link #MOBILE}</li>
     *   <li>{@code windows nt}, {@code macintosh}, {@code x11}, {@code cros}, {@code linux} →
     *       {@link #DESKTOP}</li>
     *   <li>no rule matched, but {@code Sec-CH-UA-Mobile} present: {@code ?1} → {@link #MOBILE},
     *       {@code ?0} → {@link #DESKTOP}</li>
     *   <li>otherwise → {@link #UNKNOWN}</li>
     * </ol>
     *
     * <p><b>Tablet is checked before mobile</b> because iPad user agents carry both markers. /
     * Tablet wird vor Mobile geprüft, weil iPad-User-Agents beide Merkmale enthalten.
     *
     * @param userAgent raw {@code User-Agent} header value, may be {@code null} / roher Header-Wert
     * @param chUaMobile {@code Sec-CH-UA-Mobile} header ({@code "?0"}/{@code "?1"}), may be
     *     {@code null} / Low-Entropy-Client-Hint, darf {@code null} sein
     * @return one of {@link #MOBILE}, {@link #TABLET}, {@link #DESKTOP}, {@link #UNKNOWN} — never
     *     {@code null} / immer genau einer der vier Werte
     */
    public static String classifyDevice(String userAgent, String chUaMobile) {
        // Rule 1 — nothing to evaluate / Regel 1: nichts auswertbar.
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }

        // Cap first, then normalise — a pathological header is never scanned in full.
        // Erst kappen, dann normalisieren (MAX_USER_AGENT_CHARS, TC-3b).
        String ua = userAgent.length() > MAX_USER_AGENT_CHARS
            ? userAgent.substring(0, MAX_USER_AGENT_CHARS)
            : userAgent;
        ua = ua.toLowerCase(Locale.ROOT);

        boolean hasAndroid = ua.contains("android");
        boolean hasMobi = ua.contains("mobi");

        // Rule 2 — tablet before mobile / Regel 2: Tablet vor Mobile (iPad trägt beide Merkmale).
        if (ua.contains("ipad")
            || ua.contains("tablet")
            || ua.contains("kindle")
            || ua.contains("silk")
            || ua.contains("playbook")
            || (hasAndroid && !hasMobi)) {
            return TABLET;
        }

        // Rule 3 — mobile markers / Regel 3: Mobil-Marker.
        if (hasMobi
            || ua.contains("iphone")
            || ua.contains("ipod")
            || hasAndroid
            || ua.contains("windows phone")
            || ua.contains("blackberry")) {
            return MOBILE;
        }

        // Rule 4 — desktop markers / Regel 4: Desktop-Marker.
        if (ua.contains("windows nt")
            || ua.contains("macintosh")
            || ua.contains("x11")
            || ua.contains("cros")
            || ua.contains("linux")) {
            return DESKTOP;
        }

        // Rule 5 — low-entropy client hint as tie breaker / Regel 5: Client-Hint als Tie-Breaker.
        if (chUaMobile != null) {
            String hint = chUaMobile.trim();
            if (CH_UA_MOBILE_TRUE.equals(hint)) {
                return MOBILE;
            }
            if (CH_UA_MOBILE_FALSE.equals(hint)) {
                return DESKTOP;
            }
        }

        // Rule 6 — there is deliberately no "else desktop" default / bewusst kein Default „desktop".
        return UNKNOWN;
    }
}
