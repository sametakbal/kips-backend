package com.akbal.kips.be.service.staff;

import com.akbal.kips.be.dto.staff.StaffDtos.StaffPermissionCatalog;
import com.akbal.kips.be.dto.staff.StaffDtos.StaffRole;
import com.akbal.kips.be.dto.staff.StaffDtos.StaffRolePermissions;
import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.security.StaffPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.STAFF_ROLE_MANAGE + "')")
public class StaffPermissionService {
    private final JdbcClient jdbc;
    private final StaffAudit audit;

    public StaffPermissionCatalog getCatalog() {
        return new StaffPermissionCatalog(List.of(StaffRole.values()), List.of(StaffPermission.values()));
    }

    @Transactional(readOnly = true)
    public StaffRolePermissions getPermissions(StaffRole role) {
        return new StaffRolePermissions(role, permissions(roleId(role, false)));
    }

    @Transactional
    public StaffRolePermissions replacePermissions(StaffRole role, Set<StaffPermission> requested) {
        long id = roleId(role, true);
        var previous = permissions(id);
        var next = requested.stream().sorted().toList();
        if (previous.equals(next)) {
            return new StaffRolePermissions(role, previous);
        }

        jdbc.sql("DELETE FROM staff_role_permission WHERE role_id = :roleId").param("roleId", id).update();
        for (var permission : next) {
            jdbc.sql("INSERT INTO staff_role_permission (role_id, permission) VALUES (:roleId, :permission)")
                    .param("roleId", id).param("permission", permission.name()).update();
        }
        audit.record("STAFF_PERMISSIONS_CHANGED", "ROLE", id, previous, next, role.name());
        return new StaffRolePermissions(role, next);
    }

    private long roleId(StaffRole role, boolean lock) {
        var sql = "SELECT id FROM app_user_roles WHERE name = :name" + (lock ? " FOR UPDATE" : "");
        return jdbc.sql(sql).param("name", role.name()).query(Long.class).single();
    }

    private List<StaffPermission> permissions(long id) {
        return jdbc.sql("SELECT permission FROM staff_role_permission WHERE role_id = :id")
                .param("id", id).query(String.class).list().stream()
                .map(StaffPermission::valueOf).sorted().toList();
    }
}
