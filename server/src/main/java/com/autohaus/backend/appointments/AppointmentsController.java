package com.autohaus.backend.appointments;

import com.autohaus.backend.appointments.dto.AppointmentResponseDto;
import com.autohaus.backend.appointments.dto.CreateAppointmentRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentsController {

    private final AppointmentsService appointmentsService;

    public AppointmentsController(AppointmentsService appointmentsService) {
        this.appointmentsService = appointmentsService;
    }

    @PostMapping
    public AppointmentResponseDto createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        return appointmentsService.createAppointment(request);
    }

    @GetMapping("/{id}")
    public AppointmentResponseDto getAppointment(@PathVariable String id) {
        return appointmentsService.getAppointment(id);
    }

    @PutMapping("/{id}/cancel")
    public AppointmentResponseDto cancelAppointment(@PathVariable String id) {
        return appointmentsService.cancelAppointment(id);
    }
}
