package com.akbal.kips.be.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AddressDtos {
    private AddressDtos() {
    }

    public record AddressRequest(
            @NotBlank @Size(max = 32) String label,
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$") String phone,
            @NotBlank @Size(max = 255) String line1,
            @Size(max = 255) String line2,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String state,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "(?i)[A-Z]{2}") String country,
            boolean defaultAddress) {
    }

    public record AddressResponse(
            Long id, String label, String recipientName, String phone, String line1, String line2,
            String city, String state, String postalCode, String country, boolean defaultAddress) {
    }
}
