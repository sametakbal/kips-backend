package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.staff.StaffDtos.ReplacePermissions;
import com.akbal.kips.be.dto.staff.StaffDtos.RoleAssignment;
import com.akbal.kips.be.dto.staff.StaffDtos.StaffPermissionCatalog;
import com.akbal.kips.be.dto.staff.StaffDtos.StaffRole;
import com.akbal.kips.be.dto.staff.StaffDtos.StaffRolePermissions;
import com.akbal.kips.be.service.staff.StaffPermissionService;
import com.akbal.kips.be.service.staff.StaffRoleService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/staff-roles")
@SecurityRequirement(name = "bearerAuth")
public class StaffRoleController extends BaseController {
    private final StaffRoleService roles;
    private final StaffPermissionService permissions;

    @GetMapping
    public ApiResponse<RoleAssignment> getRoles(@RequestParam String username) {
        return respond(roles.getRoles(username));
    }

    @PutMapping
    public ApiResponse<RoleAssignment> assign(@RequestParam String username, @RequestParam StaffRole role) {
        return respond(roles.assign(username, role));
    }

    @DeleteMapping
    public ApiResponse<RoleAssignment> revoke(@RequestParam String username, @RequestParam StaffRole role) {
        return respond(roles.revoke(username, role));
    }

    @GetMapping("/permissions")
    public ApiResponse<StaffPermissionCatalog> getPermissionCatalog() {
        return respond(permissions.getCatalog());
    }

    @GetMapping("/{role}/permissions")
    public ApiResponse<StaffRolePermissions> getPermissions(@PathVariable StaffRole role) {
        return respond(permissions.getPermissions(role));
    }

    @PutMapping("/{role}/permissions")
    public ApiResponse<StaffRolePermissions> replacePermissions(@PathVariable StaffRole role,
            @Valid @RequestBody ReplacePermissions request) {
        return respond(permissions.replacePermissions(role, request.permissions()));
    }
}
