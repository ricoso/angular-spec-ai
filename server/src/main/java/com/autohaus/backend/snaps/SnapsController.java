package com.autohaus.backend.snaps;

import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import com.autohaus.backend.usage.insights.SearchHits;
import com.autohaus.backend.usage.insights.UsageAccessAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/snaps")
public class SnapsController {

    private final SnapsService snapsService;

    public SnapsController(SnapsService snapsService) {
        this.snapsService = snapsService;
    }

    /**
     * Appointment search / Terminsuche. REQ-038 N1 (§10.8, BR-29): after a <b>successful</b>
     * {@code getSnaps} the hit level of the answer is handed to the access log as request attribute
     * {@link UsageAccessAttributes#SEARCH_HITS} — a {@link SearchHits} value, never a location, date
     * or variant. Whether the field is written at all is decided by {@code LoggingFilter} (all three
     * switches, status &lt; 400). / Nach erfolgreicher Suche wird nur die Trefferstufe als
     * Request-Attribut übergeben; ob das Feld geloggt wird, entscheidet der {@code LoggingFilter}.
     */
    @PostMapping
    public List<SnapResponseDto> getSnaps(@Valid @RequestBody SnapRequestDto request,
                                          HttpServletRequest httpRequest) {
        List<SnapResponseDto> days = snapsService.getSnaps(request);
        httpRequest.setAttribute(UsageAccessAttributes.SEARCH_HITS,
            SearchHits.ofDayCount(countDaysFrom(days, request.fromDate())));
        return days;
    }

    /**
     * Search without request context — kept for existing callers and tests, sets no attribute /
     * Suche ohne Request-Kontext; setzt kein Attribut (kein Mapping).
     */
    public List<SnapResponseDto> getSnaps(SnapRequestDto request) {
        return snapsService.getSnaps(request);
    }

    /**
     * Days of the answer with {@code date >= fromDate} (§10.8) — the same lexicographic comparison of
     * ISO dates ({@code YYYY-MM-DD}) the frontend applies ({@code workshop-calendar-api.service.ts}).
     * Days without a date never count; without {@code fromDate} every dated day counts. / Tage der
     * Antwort ab {@code fromDate}, derselbe Vergleich wie im Frontend.
     */
    static int countDaysFrom(List<SnapResponseDto> days, String fromDate) {
        if (days == null) {
            return 0;
        }
        int count = 0;
        for (SnapResponseDto day : days) {
            if (day == null || day.date() == null) {
                continue;
            }
            if (fromDate == null || day.date().compareTo(fromDate) >= 0) {
                count++;
            }
        }
        return count;
    }
}
