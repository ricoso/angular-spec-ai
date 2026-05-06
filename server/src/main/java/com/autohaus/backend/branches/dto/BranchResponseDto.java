package com.autohaus.backend.branches.dto;

import java.util.List;

/**
 * REQ-025 (c): Branch-Response liefert die Adresse als strukturiertes
 * Objekt (`AddressDto`) statt als flachen `description`-String. Frontend
 * formatiert die Banner mit `<Street>, <ZIP> <City>`.
 *
 * `description` bleibt für Rückwärtskompatibilität mit älteren Frontend-
 * Versionen erhalten (ist eine Kurz-Form der Adresse).
 */
public record BranchResponseDto(
    String id,
    String name,
    String description,
    AddressDto address,
    List<String> brands,
    /** SoftNet-Group-ID der "tireChange"-Variante (Boxenstopp-Tage), null falls nicht konfiguriert. */
    String tireChangeGroupId
) {
    public record AddressDto(String street, String zip, String city, String country) {}
}
