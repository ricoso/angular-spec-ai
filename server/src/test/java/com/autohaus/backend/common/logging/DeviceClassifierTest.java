package com.autohaus.backend.common.logging;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge cases of the anonymous device classification (REQ-037 TC-1, TC-2).
 *
 * <p>Verifies the rule table of BR-2 in its binding order: tablet is checked before mobile, there
 * is deliberately no "else desktop" default, and the low-entropy client hint
 * {@code Sec-CH-UA-Mobile} acts only as a tie breaker once rules 1-4 did not match. The classifier
 * returns exactly one of the four literals (BR-1).
 *
 * <p>Prüft die Regeltabelle aus BR-2 in ihrer verbindlichen Reihenfolge: Tablet vor Mobile, kein
 * Default „sonst desktop", Client-Hint ausschließlich als Tie-Breaker. Das Ergebnis ist immer genau
 * eines der vier Literale.
 */
class DeviceClassifierTest {

    /** iPhone fixture (REQ-037 §13) — carries {@code Mobile/15E148}. */
    private static final String UA_IPHONE =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    /** iPad fixture — carries both the tablet and the mobile marker. */
    private static final String UA_IPAD =
        "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    /** Android tablet fixture — {@code Android} <b>without</b> {@code Mobi}. */
    private static final String UA_ANDROID_TABLET =
        "Mozilla/5.0 (Linux; Android 14; SM-X210) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** Android phone fixture — {@code Android} <b>with</b> {@code Mobile}. */
    private static final String UA_ANDROID_PHONE =
        "Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    /** Windows desktop fixture. */
    private static final String UA_WINDOWS =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** macOS desktop fixture — also the iPadOS desktop mode (known limitation of BR-2). */
    private static final String UA_MACINTOSH =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.5 Safari/605.1.15";

    /** Linux desktop fixture. */
    private static final String UA_X11 =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    /** Crawler fixture — matches no rule of BR-2 at all. */
    private static final String UA_GOOGLEBOT =
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    /** The four literals of BR-1 — nothing else may ever be returned. */
    private static final Pattern DEVICE_CLASS = Pattern.compile("^(mobile|tablet|desktop|unknown)$");

    @Test
    void classifiesTheFixtureUserAgents() {
        // TC-1: tablet is evaluated before mobile, therefore the iPad lands in "tablet" although
        // its user agent also carries "Mobile". / Tablet vor Mobile (BR-2, Regel 2 vor Regel 3).
        assertEquals(DeviceClassifier.MOBILE, DeviceClassifier.classifyDevice(UA_IPHONE, null));
        assertEquals(DeviceClassifier.TABLET, DeviceClassifier.classifyDevice(UA_IPAD, null));
        assertEquals(DeviceClassifier.MOBILE, DeviceClassifier.classifyDevice(UA_ANDROID_PHONE, null));
        assertEquals(DeviceClassifier.DESKTOP, DeviceClassifier.classifyDevice(UA_WINDOWS, null));
    }

    @Test
    void classifiesAndroidWithoutMobiAsTablet() {
        // TC-1: "Android" without "Mobi" is the only reliable tablet marker Google ships.
        // „Android" ohne „Mobi" ist der Tablet-Marker (BR-2, Regel 2).
        assertEquals(DeviceClassifier.TABLET, DeviceClassifier.classifyDevice(UA_ANDROID_TABLET, null));
    }

    @Test
    void classifiesDesktopMarkers() {
        // TC-1: macintosh and x11 are desktop markers (BR-2, rule 4).
        assertEquals(DeviceClassifier.DESKTOP, DeviceClassifier.classifyDevice(UA_MACINTOSH, null));
        assertEquals(DeviceClassifier.DESKTOP, DeviceClassifier.classifyDevice(UA_X11, null));
    }

    @Test
    void classifiesGooglebotAsUnknown() {
        // TC-1: the Googlebot user agent contains neither a mobile/tablet marker nor
        // windows nt / macintosh / x11 / cros / linux — and there is deliberately no default
        // rule "else desktop" (BR-2, rule 6). Bots drop out of every count anyway because they
        // produce no sessionId (BR-1/BR-4). / Bewusst kein Default „sonst desktop".
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice(UA_GOOGLEBOT, null));
    }

    @Test
    void classifiesMissingUserAgentAsUnknown() {
        // TC-1: null, empty and whitespace-only (BR-2, rule 1).
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice(null, null));
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice("", null));
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice("   ", null));
    }

    @Test
    void ignoresMarkersBeyondTheLengthCap() {
        // TC-1 (overlong header): the same marker classifies as mobile when it is inside the cap
        // and is ignored once it sits behind MAX_USER_AGENT_CHARS — a pathological header must
        // neither cost CPU nor throw. / Kappung auf MAX_USER_AGENT_CHARS, keine Exception.
        String padded = "Mozilla/5.0 (" + "A".repeat(600) + ") iPhone Mobile";
        assertTrue(padded.length() > DeviceClassifier.MAX_USER_AGENT_CHARS);

        assertEquals(DeviceClassifier.MOBILE, DeviceClassifier.classifyDevice("Mozilla/5.0 () iPhone Mobile", null));
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice(padded, null));
    }

    @Test
    void alwaysReturnsOneOfTheFourLiterals() {
        // TC-1 ("Und"): regex assertion over every fixture — BR-1 guarantees a closed set of four
        // values on both sides. / Abschließende Wertemenge (BR-1).
        List<String> userAgents = Arrays.asList(
            UA_IPHONE, UA_IPAD, UA_ANDROID_TABLET, UA_ANDROID_PHONE,
            UA_WINDOWS, UA_MACINTOSH, UA_X11, UA_GOOGLEBOT,
            null, "", "   ");

        for (String userAgent : userAgents) {
            String device = DeviceClassifier.classifyDevice(userAgent, null);
            assertNotNull(device);
            assertTrue(DEVICE_CLASS.matcher(device).matches(),
                "device class must be one of mobile|tablet|desktop|unknown but was: " + device);
        }
    }

    @Test
    void usesClientHintAsTieBreakerWhenNoRuleMatches() {
        // TC-2: "Mozilla/5.0" matches none of the rules 1-4, so rule 5 decides.
        assertEquals(DeviceClassifier.MOBILE, DeviceClassifier.classifyDevice("Mozilla/5.0", "?1"));
        assertEquals(DeviceClassifier.DESKTOP, DeviceClassifier.classifyDevice("Mozilla/5.0", "?0"));
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice("Mozilla/5.0", null));
    }

    @Test
    void ignoresUnknownClientHintValues() {
        // BR-2, rule 6: anything other than "?0"/"?1" falls through to unknown.
        // Alles außer „?0"/„?1" fällt auf unknown durch.
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice("Mozilla/5.0", "?2"));
        assertEquals(DeviceClassifier.UNKNOWN, DeviceClassifier.classifyDevice("Mozilla/5.0", ""));
    }

    @Test
    void clientHintNeverOverridesAMatchedRule() {
        // TC-2/BR-2: the hint is a tie breaker, not an override — rules 1-4 win.
        // Der Hint ist Tie-Breaker, kein Override.
        assertEquals(DeviceClassifier.DESKTOP, DeviceClassifier.classifyDevice(UA_WINDOWS, "?1"));
        assertEquals(DeviceClassifier.MOBILE, DeviceClassifier.classifyDevice(UA_IPHONE, "?0"));
        assertEquals(DeviceClassifier.TABLET, DeviceClassifier.classifyDevice(UA_IPAD, "?1"));
    }
}
