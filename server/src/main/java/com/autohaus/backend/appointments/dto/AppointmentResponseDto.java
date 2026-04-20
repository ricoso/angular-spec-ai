package com.autohaus.backend.appointments.dto;

import java.util.List;

public record AppointmentResponseDto(
    String id, String status, String date, String time,
    String branchName, List<String> services,
    String customerName, String licensePlate
) {}
