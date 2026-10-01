package com.autohaus.backend.snaps;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.common.filter.LoggingFilter;
import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import com.autohaus.backend.usage.insights.SearchHits;
import com.autohaus.backend.usage.insights.UsageAccessAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Search hit level handed from {@link SnapsController} to the access line (REQ-038 N1, §10.8, BR-29,
 * AC-34) / Trefferstufe der Terminsuche in der Access-Zeile: TC-61 (a–e) end to end through MockMvc
 * and the real {@link LoggingFilter}.
 */
@ExtendWith(MockitoExtension.class)
class SnapsControllerTest {

    private static final String BRANCH = "68f0a1b2c3d4e5f6a7b8c9d0";
    private static final String FROM_DATE = "2026-10-23";

    private static final String BODY = """
        {"branchId":"%s","jobTypeIds":["inspection"],"fromDate":"%s","toDate":"2026-11-06",
         "mode":"next-available","brandName":"volkswagen","variant":"default"}""".formatted(BRANCH, FROM_DATE);

    /**
     * The REQ-037 access line of the search ({@code ACCESS_PATTERN_WITH_DEVICE}); only the duration
     * varies / die REQ-037-Zeile der Suche.
     */
    private static final String REQ037_LINE =
        "^\\{\"correlationId\":\"corr-1\",\"sessionId\":\"0123456789abcdef\",\"method\":\"POST\","
            + "\"url\":\"/api/v1/snaps\",\"statusCode\":%d,\"duration\":\"\\d+ms\",\"device\":\"unknown\"}$";

    @Mock private SnapsService snapsService;

    private Logger httpLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

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

    // ---- TC-61 through MockMvc + LoggingFilter ----

    @Test
    @DisplayName("TC-61 (a): 3 days >= fromDate and 1 day before -> \"hits\":\"few\" at the end")
    void countsOnlyDaysFromTheRequestedDateAsFew() throws Exception {
        when(snapsService.getSnaps(any())).thenReturn(days("2026-10-22", "2026-10-23", "2026-10-24", "2026-10-26"));

        search(allSwitchesOn()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));

        String line = singleAccessLine();
        assertTrue(line.endsWith(",\"device\":\"unknown\",\"hits\":\"few\"}"), line);
        assertNoLocationDateOrVariant(line);
    }

    @Test
    @DisplayName("TC-61 (b): 0 days -> \"hits\":\"none\"")
    void emptyAnswerIsNone() throws Exception {
        when(snapsService.getSnaps(any())).thenReturn(List.of());

        search(allSwitchesOn()).andExpect(status().isOk());

        assertTrue(singleAccessLine().endsWith(",\"hits\":\"none\"}"), singleAccessLine());
    }

    @Test
    @DisplayName("TC-61 (b): only days before fromDate -> \"hits\":\"none\"")
    void daysBeforeTheRequestedDateOnlyAreNone() throws Exception {
        when(snapsService.getSnaps(any())).thenReturn(days("2026-10-21", "2026-10-22"));

        search(allSwitchesOn()).andExpect(status().isOk());

        assertTrue(singleAccessLine().endsWith(",\"hits\":\"none\"}"), singleAccessLine());
    }

    @Test
    @DisplayName("TC-61 (c): 5 days -> \"hits\":\"many\"")
    void fiveDaysAreMany() throws Exception {
        when(snapsService.getSnaps(any()))
            .thenReturn(days("2026-10-23", "2026-10-24", "2026-10-26", "2026-10-27", "2026-10-28"));

        search(allSwitchesOn()).andExpect(status().isOk());

        String line = singleAccessLine();
        assertTrue(line.endsWith(",\"hits\":\"many\"}"), line);
        assertNoLocationDateOrVariant(line);
    }

    @Test
    @DisplayName("TC-61 (d): usage.dropoff.enabled=false -> byte-identical to the REQ-037 line")
    void keepsTheReq037LineWhileDropoffIsOff() throws Exception {
        when(snapsService.getSnaps(any())).thenReturn(days("2026-10-23", "2026-10-24"));

        search(new LoggingFilter(true)).andExpect(status().isOk());              // REQ-037 filter
        search(new LoggingFilter(true, true, false)).andExpect(status().isOk()); // REQ-038, dropoff off

        List<String> lines = accessLines();
        assertEquals(2, lines.size());
        for (String line : lines) {
            assertTrue(Pattern.matches(REQ037_LINE.formatted(200), line), "REQ-037 line expected: " + line);
        }
        assertEquals(normaliseDuration(lines.get(0)), normaliseDuration(lines.get(1)));
    }

    @Test
    @DisplayName("TC-61 (e): a search answered with 400 keeps the REQ-037 line")
    void keepsTheReq037LineForARejectedSearch() throws Exception {
        when(snapsService.getSnaps(any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No SoftNet operations resolved"));

        search(allSwitchesOn()).andExpect(status().isBadRequest());

        String line = singleAccessLine();
        assertTrue(Pattern.matches(REQ037_LINE.formatted(400), line), "REQ-037 line expected: " + line);
    }

    @Test
    @DisplayName("TC-61 (e): an invalid request body (400 by bean validation) keeps the REQ-037 line")
    void keepsTheReq037LineForAnInvalidRequest() throws Exception {
        MockMvc mockMvc = mockMvc(allSwitchesOn());

        mockMvc.perform(post("/api/v1/snaps")
                .header("X-Correlation-Id", "corr-1")
                .header("X-Session-Id", "0123456789abcdef")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"\",\"jobTypeIds\":[],\"fromDate\":\"\",\"toDate\":\"\"}"))
            .andExpect(status().isBadRequest());

        verify(snapsService, never()).getSnaps(any());
        String line = singleAccessLine();
        assertTrue(Pattern.matches(REQ037_LINE.formatted(400), line), "REQ-037 line expected: " + line);
    }

    // ---- controller contract / Controller-Vertrag ----

    @Test
    @DisplayName("§10.8: the controller sets a SearchHits value after a successful getSnaps")
    void setsTheSearchHitsAttributeAfterASuccessfulSearch() {
        SnapsController controller = new SnapsController(snapsService);
        SnapRequestDto request = request(FROM_DATE);
        List<SnapResponseDto> answer = days("2026-10-23", "2026-10-24", "2026-10-26", "2026-10-27");
        when(snapsService.getSnaps(request)).thenReturn(answer);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/api/v1/snaps");

        List<SnapResponseDto> result = controller.getSnaps(request, httpRequest);

        assertEquals(answer, result, "the answer itself is unchanged");
        assertEquals(SearchHits.MANY, httpRequest.getAttribute(UsageAccessAttributes.SEARCH_HITS));
    }

    @Test
    @DisplayName("§10.8: a failing getSnaps sets no attribute")
    void setsNoAttributeWhenTheSearchFails() {
        SnapsController controller = new SnapsController(snapsService);
        SnapRequestDto request = request(FROM_DATE);
        when(snapsService.getSnaps(request)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/api/v1/snaps");

        try {
            controller.getSnaps(request, httpRequest);
        } catch (ResponseStatusException expected) {
            // expected / erwartet
        }

        assertNull(httpRequest.getAttribute(UsageAccessAttributes.SEARCH_HITS));
    }

    @Test
    @DisplayName("The overload without request context delegates unchanged")
    void overloadWithoutRequestContextDelegates() {
        SnapsController controller = new SnapsController(snapsService);
        SnapRequestDto request = request(FROM_DATE);
        when(snapsService.getSnaps(request)).thenReturn(days("2026-10-23"));

        assertEquals(1, controller.getSnaps(request).size());
    }

    @Test
    @DisplayName("§10.8: day count ignores missing days/dates and counts all dated days without fromDate")
    void countsDaysDefensively() {
        List<SnapResponseDto> answer = new ArrayList<>(days("2026-10-22", "2026-10-23"));
        answer.add(null);
        answer.add(new SnapResponseDto(null, "", "", List.of()));

        assertEquals(1, SnapsController.countDaysFrom(answer, FROM_DATE));
        assertEquals(2, SnapsController.countDaysFrom(answer, null));
        assertEquals(0, SnapsController.countDaysFrom(null, FROM_DATE));
        assertEquals(0, SnapsController.countDaysFrom(List.of(), FROM_DATE));
    }

    // ---- helpers / Hilfen ----

    private static LoggingFilter allSwitchesOn() {
        return new LoggingFilter(true, true, true);
    }

    private MockMvc mockMvc(LoggingFilter filter) {
        return MockMvcBuilders.standaloneSetup(new SnapsController(snapsService)).addFilters(filter).build();
    }

    private org.springframework.test.web.servlet.ResultActions search(LoggingFilter filter) throws Exception {
        return mockMvc(filter).perform(post("/api/v1/snaps")
            .header("X-Correlation-Id", "corr-1")
            .header("X-Session-Id", "0123456789abcdef")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY));
    }

    private static SnapRequestDto request(String fromDate) {
        return new SnapRequestDto(BRANCH, List.of("inspection"), fromDate, "2026-11-06", "next-available",
            "volkswagen", null, null, "default", null);
    }

    private static List<SnapResponseDto> days(String... dates) {
        return Arrays.stream(dates)
            .map(date -> new SnapResponseDto(date, date, "Fr", List.of(
                new SnapResponseDto.TimeSlotDto("snap-" + date, "08:00", "08:00 Uhr", true, "Berater"))))
            .toList();
    }

    private List<String> accessLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String singleAccessLine() {
        List<String> lines = accessLines();
        assertEquals(1, lines.size(), "the filter must write exactly one access line: " + lines);
        return lines.getFirst();
    }

    /** BR-29: the field carries no location, no date and no variant / weder Standort noch Datum. */
    private static void assertNoLocationDateOrVariant(String line) {
        assertFalse(line.contains(BRANCH), line);
        assertFalse(line.contains("2026-"), line);
        assertFalse(line.contains("default"), line);
        assertFalse(line.contains("volkswagen"), line);
        assertFalse(line.contains("snap-"), line);
        assertFalse(line.contains("Berater"), line);
    }

    private static String normaliseDuration(String line) {
        return line.replaceAll("\"duration\":\"\\d+ms\"", "\"duration\":\"0ms\"");
    }
}
