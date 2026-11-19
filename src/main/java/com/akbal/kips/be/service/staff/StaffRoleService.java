package com.akbal.kips.be.service.staff;

import com.akbal.kips.be.dto.staff.StaffDtos.RoleAssignment;
import com.akbal.kips.be.dto.staff.StaffDtos.StaffRole;
import com.akbal.kips.be.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.STAFF_ROLE_MANAGE + "')")
public class StaffRoleService {
    private final JdbcClient jdbc;
    private final StaffAudit audit;

    @Transactional(readOnly = true)
    public RoleAssignment getRoles(String username) {
        return new RoleAssignment(username, roles(userId(username, false)));
    }

    @Transactional
    public RoleAssignment assign(String username, StaffRole role) {
        return change(username, role, true);
    }

    @Transactional
    public RoleAssignment revoke(String username, StaffRole role) {
        return change(username, role, false);
    }

    private RoleAssignment change(String username, StaffRole role, boolean assign) {
        long userId = userId(username, true);
        var previous = roles(userId);
        long roleId = jdbc.sql("SELECT id FROM app_user_roles WHERE name = :name")
                .param("name", role.name()).query(Long.class).single();
        String sql = assign
                ? "INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId) ON CONFLICT DO NOTHING"
                : "DELETE FROM user_roles WHERE user_id = :userId AND role_id = :roleId";
        if (jdbc.sql(sql).param("userId", userId).param("roleId", roleId).update() == 0) {
            return new RoleAssignment(username, previous);
        }
        var current = roles(userId);
        audit.record("STAFF_ROLES_CHANGED", "USER", userId, previous, current,
                (assign ? "Assigned " : "Revoked ") + role.name());
        return new RoleAssignment(username, current);
    }

    private long userId(String username, boolean lock) {
        String sql = "SELECT id FROM app_user WHERE username = :name" + (lock ? " FOR UPDATE" : "");
        return jdbc.sql(sql).param("name", username).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private List<String> roles(long userId) {
        return jdbc.sql("""
                SELECT r.name FROM app_user_roles r JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = :id ORDER BY r.name
                """).param("id", userId).query(String.class).list();
    }
}
