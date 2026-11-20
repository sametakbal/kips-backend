package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.user.AddressDtos.AddressRequest;
import com.akbal.kips.be.dto.user.AddressDtos.AddressResponse;
import com.akbal.kips.be.service.user.AddressService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/addresses")
public class AddressController extends BaseController {
    private final AddressService service;

    @GetMapping
    public ApiResponse<List<AddressResponse>> list(Principal user) {
        return respond(service.list(user.getName()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> create(
            Principal user, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(respond(service.create(user.getName(), request), HttpStatus.CREATED));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> update(
            Principal user, @PathVariable long id, @Valid @RequestBody AddressRequest request) {
        return respond(service.update(user.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Principal user, @PathVariable long id) {
        service.delete(user.getName(), id);
        return respond(null);
    }

    @PostMapping("/{id}/default")
    public ApiResponse<AddressResponse> setDefault(Principal user, @PathVariable long id) {
        return respond(service.setDefault(user.getName(), id));
    }
}
