package com.akbal.kips.be.dto.product.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CategoryCreateRequest {
    @NotEmpty
    private List<@NotNull @Valid CategoryRequest> categories;
}
