package com.akbal.kips.be.dto.product.request;

import com.akbal.kips.be.domain.product.CatalogDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductMetadata(@Size(max = 14) String gtin, @Positive Long familyId, @Valid CatalogDetails details) {
}
