package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.service.product.ProductFamilyService;
import com.akbal.kips.be.service.product.ProductFamilyService.Family;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1")
public class ProductFamilyController extends BaseController {
    private final ProductFamilyService families;

    public record CreateFamily(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 255) String name) {
    }

    public record RenameFamily(@NotBlank @Size(max = 255) String name) {
    }

    @GetMapping("/product-families")
    public ResponseEntity<ApiResponse<List<Family>>> list(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        var page = families.list(pageable);
        return ResponseEntity.ok().header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(respond(page.getContent()));
    }

    @GetMapping("/product-families/{id}")
    public ApiResponse<Family> get(@PathVariable long id) {
        return respond(families.get(id));
    }

    @PostMapping("/admin/product-families")
    public ResponseEntity<ApiResponse<Family>> create(@Valid @RequestBody CreateFamily request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(respond(families.create(request.code(), request.name()), HttpStatus.CREATED));
    }

    @PutMapping("/admin/product-families/{id}")
    public ApiResponse<Family> rename(@PathVariable long id, @Valid @RequestBody RenameFamily request) {
        return respond(families.rename(id, request.name()));
    }

    @DeleteMapping("/admin/product-families/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        families.delete(id);
        return respond(null);
    }
}
