package com.akbal.kips.be.service.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CatalogIdentifiers {
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern GTIN = Pattern.compile("(?:[0-9]{8}|[0-9]{12,14})");

    private CatalogIdentifiers() {
    }

    public static String normalizedCode(String code, String field) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        }
        return code.toUpperCase(Locale.ROOT);
    }

    public static String gtin(String value) {
        if (!GTIN.matcher(value).matches()) {
            throw invalidGtin();
        }
        int sum = 0;
        int multiplier = 3;
        for (int index = value.length() - 2; index >= 0; index--) {
            sum += (value.charAt(index) - '0') * multiplier;
            multiplier = 4 - multiplier;
        }
        if ((10 - sum % 10) % 10 != value.charAt(value.length() - 1) - '0') {
            throw invalidGtin();
        }
        return value;
    }

    private static ResponseStatusException invalidGtin() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid GTIN length or check digit");
    }
}
