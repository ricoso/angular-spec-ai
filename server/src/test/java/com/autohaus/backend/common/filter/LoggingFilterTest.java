package com.autohaus.backend.common.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoggingFilterTest {

    /**
     * iPhone fixture user agent (REQ-037 §13). None of its substrings may ever appear in a log
     * line — the raw header is read and discarded inside the same expression (BR-3, TC-3). /
     * Kein Teilstring dieses User-Agents darf jemals in einer Log-Zeile auftauchen.
     */
    private static final String UA_IPHONE =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    /** The device field with the four literals of BR-1 / das Geräte-Feld mit den vier Literalen. */
    private static final Pattern DEVICE_FIELD =
        Pattern.compile("\"device\":\"(mobile|tablet|desktop|unknown)\"");

    /**
     * The access line exactly as it looked before REQ-037 — no {@code device} field at all, not
     * even an empty one (AC-1, BR-13). / Die Access-Zeile exakt wie vor REQ-037.
     */
    private static final Pattern LEGACY_ACCESS_LINE = Pattern.compile(
        "^\\{\"correlationId\":\"corr-123\",\"sessionId\":\"sess-456\",\"method\":\"GET\","
            + "\"url\":\"/api/v1/branches\",\"statusCode\":200,\"duration\":\"\\d+ms\"}$");

    private final LoggingFilter filter = new LoggingFilter(true);

    private Logger httpLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    /** Captures the access log line of the "HTTP" logger / fängt die Access-Zeile ab. */
    @BeforeEach
    void captureAccessLog() {
        httpLogger = (Logger) LoggerFactory.getLogger("HTTP");
        originalLevel = httpLogger.getLevel();
        httpLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        httpLogger.addAppender(appender);
    }

    @AfterEach
    void releaseAccessLog() {
        httpLogger.detachAppender(appender);
        appender.stop();
        httpLogger.setLevel(originalLevel);
    }

    @Test
    void logsRequestWithAllHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-req-id");
        request.addHeader("X-Correlation-Id", "corr-123");
        request.addHeader("X-Session-Id", "sess-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void logsRequestWithoutOptionalHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void logsRequestWhenFilterChainThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-err");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("boom")).when(chain).doFilter(request, response);

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));
    }

    @Test
    void logsRequestWithNullRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/something");
        // requestId attribute is not set
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void logsDeviceClassButNeverTheRawUserAgent() throws ServletException, IOException {
        // TC-3: the access line carries the derived class only — the raw User-Agent stays inside
        // the classifier call. / Geloggt wird ausschließlich die abgeleitete Klasse (AC-1, BR-3).
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("X-Session-Id", "abc123");
        request.addHeader("User-Agent", UA_IPHONE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String line = singleAccessLine();
        assertTrue(line.contains("\"device\":\"mobile\""), "device class must be logged: " + line);
        assertFalse(line.contains(UA_IPHONE), "the raw user agent must never be logged");
        assertFalse(line.contains("iPhone"), "no user agent substring must be logged");
        assertFalse(line.contains("CPU iPhone OS"), "no user agent substring must be logged");
        assertFalse(line.contains("Safari"), "no user agent substring must be logged");
        assertFalse(line.contains("Mozilla"), "no user agent substring must be logged");
        verify(chain).doFilter(request, response);
    }

    @Test
    void cutsPathologicalUserAgentsWithoutLoggingThem() throws ServletException, IOException {
        // TC-3b: a 2000 character header with quotes, angle brackets, a line break and non-ASCII
        // characters must neither throw nor leak into the line — the cap at MAX_USER_AGENT_CHARS
        // and BR-3 hold together. / Weder Exception noch Leck, die Kappung greift.
        String hostile = "\"<script>\nÄÖÜ-" + "x".repeat(2000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("X-Session-Id", "abc123");
        request.addHeader("User-Agent", hostile);
        request.addHeader("Sec-CH-UA-Mobile", "?1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String line = singleAccessLine();
        Matcher device = DEVICE_FIELD.matcher(line);
        assertTrue(device.find(), "device class must be one of the four literals: " + line);
        assertFalse(line.contains("<script>"), "no user agent substring must be logged");
        assertFalse(line.contains("ÄÖÜ"), "no user agent substring must be logged");
        assertFalse(line.contains("xxx"), "no user agent substring must be logged");
        assertFalse(line.contains("\n"), "a user agent must never inject a line break");
    }

    @Test
    void doesNotLeakTheUserAgentOnTheErrorPath() throws ServletException, IOException {
        // TC-3c: even when the chain blows up, the access line written in the finally block
        // carries the class only — no exception message transports the header (BR-3).
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/appointments");
        request.addHeader("X-Session-Id", "abc123");
        request.addHeader("User-Agent", UA_IPHONE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("boom")).when(chain).doFilter(request, response);

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));

        String line = singleAccessLine();
        assertTrue(line.contains("\"device\":\"mobile\""), "device class must be logged: " + line);
        assertFalse(line.contains("iPhone"), "no user agent substring must be logged");
        assertFalse(line.contains("Safari"), "no user agent substring must be logged");
    }

    @Test
    void keepsTheAccessLineUnchangedWhenUsageStatsAreDisabled() throws ServletException, IOException {
        // TC-3e: with usage.enabled=false the line is byte-identical to the state before REQ-037 —
        // no device field, not even an empty one (AC-1, BR-13).
        LoggingFilter disabled = new LoggingFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("X-Correlation-Id", "corr-123");
        request.addHeader("X-Session-Id", "sess-456");
        request.addHeader("User-Agent", UA_IPHONE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        disabled.doFilterInternal(request, response, chain);

        String line = singleAccessLine();
        assertFalse(line.contains("device"), "no device field when the kill switch is off: " + line);
        assertTrue(LEGACY_ACCESS_LINE.matcher(line).matches(), "access line must be unchanged: " + line);
        assertFalse(line.contains("iPhone"), "no user agent substring must be logged");
    }

    @Test
    void doesNotReadDeviceHeadersWhenUsageStatsAreDisabled() throws ServletException, IOException {
        // TC-3e: nothing is collected "just in case" — with the kill switch off the two headers
        // are not even read (purpose limitation, BR-13). / Die Header werden gar nicht gelesen.
        LoggingFilter disabled = new LoggingFilter(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/branches");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        disabled.doFilterInternal(request, response, chain);

        verify(request, never()).getHeader("User-Agent");
        verify(request, never()).getHeader("Sec-CH-UA-Mobile");
        verify(chain).doFilter(request, response);
    }

    /** The one access line written by the filter / die einzige vom Filter geschriebene Zeile. */
    private String singleAccessLine() {
        assertEquals(1, appender.list.size(), "the filter must write exactly one access line");
        return appender.list.getFirst().getFormattedMessage();
    }
}
