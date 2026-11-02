package com.autohaus.backend.snaps;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
public class SnapsService {

    private static final Logger log = LoggerFactory.getLogger(SnapsService.class);
    private static final Map<Integer, String> DAY_NAMES = Map.of(
        0, "So", 1, "Mo", 2, "Di", 3, "Mi", 4, "Do", 5, "Fr", 6, "Sa"
    );

    private final SoftnetApiClient softnetApi;
    private final OperationIdMapper operationIdMapper;
    private final SnapCache snapCache;
    private final SetupService setupService;
    private final SoftnetBrandResolver brandResolver;

    public SnapsService(SoftnetApiClient softnetApi, OperationIdMapper operationIdMapper,
                        SnapCache snapCache, SetupService setupService,
                        SoftnetBrandResolver brandResolver) {
        this.softnetApi = softnetApi;
        this.operationIdMapper = operationIdMapper;
        this.snapCache = snapCache;
        this.setupService = setupService;
        this.brandResolver = brandResolver;
    }

    public List<SnapResponseDto> getSnaps(SnapRequestDto request) {
        String groupId = setupService.resolveGroupId(request.branchId(), request.variant());
        List<String> mappedOps = operationIdMapper.mapToSoftnetOperations(groupId, request.jobTypeIds());

        // SoftNet rejects an empty operations list with an opaque 400 ("Missing operations
        // list!"). Fail fast with a clear message instead of forwarding a broken request.
        if (mappedOps.isEmpty() && request.jobTypeIds() != null && !request.jobTypeIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No SoftNet operations resolved for requested services " + request.jobTypeIds()
                    + " in group \"" + groupId + "\"");
        }

        boolean rentalCar = Boolean.TRUE.equals(request.replacementCar());
        // REQ-039 BR-M6: SoftNet matches rent categories case-sensitively; only the rental path
        // needs the SoftNet spelling, the plain path stays byte-identical (AC-M3).
        // Fix 2026-10-13: rental is LOCATION-based — substitute a rent-category-valid brand when the
        // customer's brand is on no category, so SEAT/ŠKODA/CUPRA/Nutzfahrzeuge/Hyundai also book.
        String brand = rentalCar
            ? resolveRentalBrand(groupId, brandResolver.resolve(groupId, request.brandName()))
            : request.brandName();

        Map<String, Object> softnetRequest = new LinkedHashMap<>();
        softnetRequest.put("groupid", groupId);
        softnetRequest.put("brand", brand != null ? brand : "");
        softnetRequest.put("car", Map.of("brand", brand != null ? brand : ""));
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

        // REQ-039 BR-M2: any rent category — no rentCategoryid, SoftNet assigns the first free one.
        if (rentalCar) {
            softnetRequest.put("replacementPreference", true);
        }

        // REQ-027: forward notification channel ({ SMS, SMTP }) to SoftNet.
        if (request.notifications() != null) {
            Map<String, Boolean> notifications = new LinkedHashMap<>();
            notifications.put("SMS", Boolean.TRUE.equals(request.notifications().SMS()));
            notifications.put("SMTP", Boolean.TRUE.equals(request.notifications().SMTP()));
            softnetRequest.put("notifications", notifications);
        }

        // REQ-034: log technical metadata only, never the full request object.
        log.debug("SoftNet snaps request: group={}, {} operations, mode={}", groupId, mappedOps.size(), request.mode());
        List<JsonNode> rawSnaps;
        try {
            rawSnaps = softnetApi.getSnaps(softnetRequest, groupId);
        } catch (RestClientResponseException ex) {
            // Safety net (2026-10-12): a brand without a matching rent category makes SoftNet answer
            // 500 "No matching rent category for brand X" on the rental path. Treat it as "no rental
            // appointments" (empty list) instead of an error — the frontend already hides the switch
            // for such brands (BR-M8); this only catches edge cases and keeps the logs clean.
            if (rentalCar && ex.getStatusCode().value() == 500
                    && ex.getResponseBodyAsString().contains("No matching rent category")) {
                log.info("Rental snaps probe: brand has no matching rent category — returning no rental slots");
                return List.of();
            }
            throw ex;
        }
        log.debug("SoftNet snaps response: {} items", rawSnaps.size());

        snapCache.cacheSnaps(rawSnaps);

        if (!rentalCar) {
            return transformSnaps(rawSnaps);
        }
        // REQ-039 BR-M2/AC-M2: with a rental car only snaps SoftNet reserved a car for are offered.
        List<JsonNode> withRentalCar = rawSnaps.stream().filter(SnapsService::hasRentalCar).toList();
        log.debug("Rental filter: {} of {} snaps carry a replacement resource", withRentalCar.size(), rawSnaps.size());
        return transformSnaps(withRentalCar);
    }

    private static boolean hasRentalCar(JsonNode snap) {
        JsonNode resource = snap.path("replacementResource");
        return resource.isTextual() && !resource.asText().isBlank();
    }

    /**
     * Rental is LOCATION-based, not brand-based (Kundenbestätigung 2026-10-13). SoftNet gates rent
     * categories by the request brand and answers 500 ("No matching rent category for brand X") for
     * a brand that is on no category — at this dealer only Audi/Volkswagen are listed, so SEAT,
     * ŠKODA, CUPRA, Volkswagen Nutzfahrzeuge and Hyundai got no rental slots at all.
     *
     * <p>If the customer's brand is already on a rent category we keep it (Audi → Audi,
     * Volkswagen → Volkswagen). Otherwise we substitute a brand the location's categories accept,
     * preferring Volkswagen, then Audi, then any — the dealer lends a brand-neutral pool car, so the
     * rental is booked on that location-valid brand regardless of the vehicle brand. An all-brand
     * category (empty brand list) means no substitution is needed. A failed lookup keeps the brand
     * unchanged; the existing 500 safety net then degrades gracefully to "no rental slots".
     */
    private String resolveRentalBrand(String groupId, String resolvedBrand) {
        List<JsonNode> categories;
        try {
            categories = softnetApi.getRentCategories(groupId);
        } catch (RuntimeException e) {
            log.warn("Rental brand resolution: rent categories unavailable ({}) — keeping brand '{}'",
                e.getClass().getSimpleName(), resolvedBrand);
            return resolvedBrand;
        }
        if (categories == null || categories.isEmpty()) {
            return resolvedBrand;
        }
        List<String> validBrands = categories.stream()
            .flatMap(cat -> {
                JsonNode brands = cat.path("brand");
                // Empty brand list = category valid for ALL brands.
                if (!brands.isArray() || brands.isEmpty()) {
                    return Stream.of("*");
                }
                return StreamSupport.stream(brands.spliterator(), false).map(b -> b.asText(""));
            })
            .filter(b -> b != null && !b.isBlank())
            .toList();

        // An all-brand category exists → any brand is accepted, keep the customer's.
        if (validBrands.contains("*")) {
            return resolvedBrand;
        }
        // Customer brand already accepted → keep it (Audi → Audi, Volkswagen → Volkswagen).
        if (resolvedBrand != null && validBrands.stream().anyMatch(b -> b.equalsIgnoreCase(resolvedBrand))) {
            return resolvedBrand;
        }
        // Substitute a location-valid brand: Volkswagen first, then Audi, then any (customer rule).
        String substitute = validBrands.stream().filter(b -> b.equalsIgnoreCase("Volkswagen")).findFirst()
            .or(() -> validBrands.stream().filter(b -> b.equalsIgnoreCase("Audi")).findFirst())
            .or(() -> validBrands.stream().findFirst())
            .orElse(resolvedBrand);
        if (substitute != null && !substitute.equalsIgnoreCase(resolvedBrand)) {
            log.info("Rental brand substitution: '{}' is on no rent category — using location-valid '{}'",
                resolvedBrand, substitute);
        }
        return substitute;
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
                snap.path("checkInResource").asText(null),
                hasRentalCar(snap)
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
