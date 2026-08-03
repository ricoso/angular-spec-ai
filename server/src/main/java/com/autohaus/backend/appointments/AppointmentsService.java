package com.autohaus.backend.appointments;

import com.autohaus.backend.appointments.dto.AppointmentResponseDto;
import com.autohaus.backend.appointments.dto.CreateAppointmentRequest;
import com.autohaus.backend.common.logging.LogPayloadUtil;
import com.autohaus.backend.common.util.PhoneNumberNormalizer;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.SnapCache;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AppointmentsService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentsService.class);

    private final SoftnetApiClient softnetApi;
    private final SnapCache snapCache;
    private final ObjectMapper objectMapper;
    private final SetupService setupService;

    public AppointmentsService(SoftnetApiClient softnetApi, SnapCache snapCache, ObjectMapper objectMapper,
                               SetupService setupService) {
        this.softnetApi = softnetApi;
        this.snapCache = snapCache;
        this.objectMapper = objectMapper;
        this.setupService = setupService;
    }

    public AppointmentResponseDto createAppointment(CreateAppointmentRequest dto) {
        JsonNode rawSnap = snapCache.getRawSnap(dto.snapId());
        if (rawSnap == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Snap \"" + dto.snapId() + "\" not found in cache. The appointment slot may have expired. Please select a new time slot.");
        }

        String groupId = setupService.resolveGroupId(dto.branchId(), dto.variant());

        // Patch a copy so the pristine SoftNet snap stays available for error logging.
        ObjectNode snap = ((ObjectNode) rawSnap).deepCopy();
        patchSnapWithBookingData(snap, dto);

        String branchLabel = "";
        JsonNode branchNode = snap.path("branch");
        if (!branchNode.isMissingNode()) {
            branchLabel = branchNode.path("branchLabel").asText(
                branchNode.path("name").asText(branchNode.path("id").asText("")));
        }
        log.debug("Creating appointment for snap {}, branch {}", dto.snapId(), branchLabel);

        Map<String, Object> snapMap = objectMapper.convertValue(snap, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        try {
            JsonNode result = softnetApi.createAppointment(snapMap, groupId);
            return mapAppointmentResponse(result, dto);
        } catch (RuntimeException e) {
            log.error("SOFTNET_ERROR appointment creation failed for snap {} | rawSnap={}",
                dto.snapId(), LogPayloadUtil.truncate(rawSnap.toString()));
            throw e;
        }
    }

    public AppointmentResponseDto getAppointment(String appointmentId, String branchId, String variant) {
        String groupId = setupService.resolveGroupId(branchId, variant);
        JsonNode result = softnetApi.getAppointment(appointmentId, groupId);
        return mapAppointmentResponse(result, null);
    }

    public AppointmentResponseDto cancelAppointment(String appointmentId, String branchId, String variant) {
        String groupId = setupService.resolveGroupId(branchId, variant);
        JsonNode result = softnetApi.cancelAppointment(appointmentId, groupId);
        AppointmentResponseDto mapped = mapAppointmentResponse(result, null);
        return new AppointmentResponseDto(mapped.id(), "cancelled", mapped.date(), mapped.time(),
            mapped.branchName(), mapped.services(), mapped.customerName(), mapped.licensePlate());
    }

    private void patchSnapWithBookingData(ObjectNode snap, CreateAppointmentRequest dto) {
        ObjectNode wishlist = snap.has("wishlist") && snap.get("wishlist").isObject()
            ? (ObjectNode) snap.get("wishlist")
            : objectMapper.createObjectNode();
        snap.set("wishlist", wishlist);

        ObjectNode client = objectMapper.createObjectNode();
        client.put("email", dto.customer().email());
        client.put("lastName", dto.customer().lastName());
        client.put("firstName", dto.customer().firstName());
        client.put("mobile", PhoneNumberNormalizer.toSoftnetFormat(dto.customer().mobile()));
        client.put("phone", PhoneNumberNormalizer.toSoftnetFormat(
            dto.customer().phone() != null ? dto.customer().phone() : dto.customer().mobile()));
        client.put("address", dto.customer().address());
        client.put("zipCode", dto.customer().zipCode());
        client.put("city", dto.customer().city());
        client.put("country", dto.customer().country() != null ? dto.customer().country() : "DE");
        client.put("language", dto.customer().language() != null ? dto.customer().language() : "de");
        wishlist.set("client", client);

        ObjectNode existingCar = wishlist.has("car") && wishlist.get("car").isObject()
            ? (ObjectNode) wishlist.get("car")
            : objectMapper.createObjectNode();

        ObjectNode car = existingCar.deepCopy();
        if (!car.has("brand") || car.get("brand").asText("").isEmpty()) {
            car.put("brand", "");
        }
        car.put("license", dto.vehicle().licensePlate());
        car.put("VIN", dto.vehicle().vin() != null ? dto.vehicle().vin() : "UNKNOWN");
        car.put("currentMileage", dto.vehicle().mileage());
        car.put("firstRegMonth", dto.vehicle().firstRegMonth() != null ? dto.vehicle().firstRegMonth() : "01");
        car.put("firstRegYear", dto.vehicle().firstRegYear() != null ? dto.vehicle().firstRegYear() : "2020");
        car.put("model", dto.vehicle().model() != null ? dto.vehicle().model()
            : (existingCar.has("model") ? existingCar.get("model").asText("Unknown") : "Unknown"));
        wishlist.set("car", car);

        if (dto.note() != null && !dto.note().isBlank()) {
            wishlist.put("note", dto.note());
        }

        if (dto.rentCategoryId() != null && !dto.rentCategoryId().isBlank()) {
            wishlist.put("isFleet", true);
            wishlist.put("fleetType", "rentObject");
            wishlist.put("fleetCategoryid", dto.rentCategoryId());
        }

        if (Boolean.TRUE.equals(dto.callbackRequested())) {
            wishlist.put("callbackPreference", true);
        }

        // REQ-027: forward notification channel ({ SMS, SMTP }) to SoftNet.
        if (dto.notifications() != null) {
            ObjectNode notifications = objectMapper.createObjectNode();
            notifications.put("SMS", Boolean.TRUE.equals(dto.notifications().SMS()));
            notifications.put("SMTP", Boolean.TRUE.equals(dto.notifications().SMTP()));
            snap.set("notifications", notifications);
        }
    }

    private AppointmentResponseDto mapAppointmentResponse(JsonNode result, CreateAppointmentRequest dto) {
        String branchName = "";
        JsonNode branchid = result.path("branchid");
        if (!branchid.isMissingNode() && branchid.has("branchLabel")) {
            branchName = branchid.path("branchLabel").asText("");
        }
        if (branchName.isEmpty()) {
            JsonNode branch = result.path("branch");
            branchName = branch.path("branchLabel").asText(branch.path("name").asText(""));
        }

        String status = result.path("status").asText(result.path("statusCode").asText("confirmed"));
        String checkInTime = result.path("checkInTime").asText("");
        String date = checkInTime.contains("T") ? checkInTime.split("T")[0] : "";
        String time = "";
        if (checkInTime.contains("T")) {
            String timePart = checkInTime.split("T")[1];
            time = timePart.length() >= 5 ? timePart.substring(0, 5) : timePart;
        }

        String customerName;
        String licensePlate;
        if (dto != null) {
            customerName = dto.customer().firstName() + " " + dto.customer().lastName();
            licensePlate = dto.vehicle().licensePlate();
        } else {
            JsonNode userid = result.path("userid");
            String fn = userid.path("firstName").asText("");
            String ln = userid.path("lastName").asText("");
            customerName = (fn + " " + ln).trim();
            licensePlate = result.path("carid").path("license").asText("");
        }

        return new AppointmentResponseDto(
            result.path("id").asText(),
            status, date, time, branchName,
            List.of(),
            customerName, licensePlate
        );
    }
}
