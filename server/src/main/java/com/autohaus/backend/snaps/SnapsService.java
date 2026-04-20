package com.autohaus.backend.snaps;

import com.autohaus.backend.config.SoftnetProperties;
import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class SnapsService {

    private static final Logger log = LoggerFactory.getLogger(SnapsService.class);
    private static final Map<Integer, String> DAY_NAMES = Map.of(
        0, "So", 1, "Mo", 2, "Di", 3, "Mi", 4, "Do", 5, "Fr", 6, "Sa"
    );

    private final SoftnetApiClient softnetApi;
    private final OperationIdMapper operationIdMapper;
    private final SnapCache snapCache;
    private final String groupId;

    public SnapsService(SoftnetApiClient softnetApi, OperationIdMapper operationIdMapper,
                        SnapCache snapCache, SoftnetProperties properties) {
        this.softnetApi = softnetApi;
        this.operationIdMapper = operationIdMapper;
        this.snapCache = snapCache;
        this.groupId = properties.groupId();
    }

    public List<SnapResponseDto> getSnaps(SnapRequestDto request) {
        List<String> mappedOps = operationIdMapper.mapToSoftnetOperations(request.jobTypeIds());

        Map<String, Object> softnetRequest = new LinkedHashMap<>();
        softnetRequest.put("groupid", groupId);
        softnetRequest.put("brand", request.brandName() != null ? request.brandName() : "");
        softnetRequest.put("car", Map.of("brand", request.brandName() != null ? request.brandName() : ""));
        softnetRequest.put("operations", mappedOps);
        softnetRequest.put("preferredBranch", request.branchId());
        softnetRequest.put("checkInDatePreference",
            "next-available".equals(request.mode()) ? "asSoonAsPossible" : "date");

        if ("date-range".equals(request.mode())) {
            softnetRequest.put("checkInDate", request.fromDate());
        }

        if (request.timePreference() != null && !request.timePreference().isBlank()) {
            softnetRequest.put("checkInTimePreference", request.timePreference());
        }

        if (request.replacementCar() != null && request.replacementCar()) {
            softnetRequest.put("replacementPreference", true);
        }

        log.debug("SoftNet snaps request: {}", softnetRequest);
        List<JsonNode> rawSnaps = softnetApi.getSnaps(softnetRequest);
        log.debug("SoftNet snaps response: {} items", rawSnaps.size());

        snapCache.cacheSnaps(rawSnaps);

        return transformSnaps(rawSnaps);
    }

    private List<SnapResponseDto> transformSnaps(List<JsonNode> snaps) {
        Map<String, List<SnapResponseDto.TimeSlotDto>> groupedByDate = new LinkedHashMap<>();

        for (JsonNode snap : snaps) {
            String checkInTime = snap.path("checkInTime").asText("");
            if (checkInTime.isEmpty()) continue;

            ZonedDateTime zdt = Instant.parse(checkInTime).atZone(ZoneOffset.UTC);
            String dateKey = String.format("%04d-%02d-%02d", zdt.getYear(), zdt.getMonthValue(), zdt.getDayOfMonth());
            String hours = String.format("%02d", zdt.getHour());
            String minutes = String.format("%02d", zdt.getMinute());

            SnapResponseDto.TimeSlotDto slot = new SnapResponseDto.TimeSlotDto(
                snap.path("internal").asText(),
                hours + ":" + minutes,
                hours + ":" + minutes + " Uhr",
                true,
                snap.path("checkInResource").asText(null)
            );

            groupedByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(slot);
        }

        List<SnapResponseDto> result = new ArrayList<>();
        for (Map.Entry<String, List<SnapResponseDto.TimeSlotDto>> entry : groupedByDate.entrySet()) {
            String dateKey = entry.getKey();
            String[] parts = dateKey.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            java.time.LocalDate ld = java.time.LocalDate.of(year, month, day);
            int dayOfWeek = ld.getDayOfWeek().getValue() % 7; // 0=Sun ... 6=Sat

            String displayDate = String.format("%02d.%02d.%04d", day, month, year);
            String dayAbbr = DAY_NAMES.getOrDefault(dayOfWeek, "");

            result.add(new SnapResponseDto(dateKey, displayDate, dayAbbr, entry.getValue()));
        }

        result.sort(Comparator.comparing(SnapResponseDto::date));
        return result;
    }
}
