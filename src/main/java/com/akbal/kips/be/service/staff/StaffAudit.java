package com.akbal.kips.be.service.staff;

import com.akbal.kips.be.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Types;

@Component
@RequiredArgsConstructor
public class StaffAudit {
    private final JdbcClient jdbc;
    private final JsonMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, long targetId,
                       Object previousValue, Object newValue, String reason) {
        var actor = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        jdbc.sql("""
                INSERT INTO staff_audit_event
                    (actor_user_id, actor_username, action, target_type, target_id, previous_value, new_value, reason)
                VALUES (:actorId, :actor, :action, :targetType, :targetId,
                        CAST(:previous AS jsonb), CAST(:next AS jsonb), :reason)
                """)
                .param("actorId", actor.getId()).param("actor", actor.getUsername())
                .param("action", action).param("targetType", targetType).param("targetId", targetId)
                .param("previous", json.writeValueAsString(previousValue))
                .param("next", json.writeValueAsString(newValue)).param("reason", reason, Types.VARCHAR)
                .update();
    }
}
