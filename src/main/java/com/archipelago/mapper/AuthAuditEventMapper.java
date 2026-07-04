package com.archipelago.mapper;

import com.archipelago.model.AuthAuditEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface AuthAuditEventMapper {
    @Insert("""
            INSERT INTO auth_audit_events (
                event_time,
                user_id,
                event_type,
                outcome,
                ip_hash,
                user_agent_hash
            ) VALUES (
                CURRENT_TIMESTAMP,
                #{userId},
                #{eventType},
                #{outcome},
                #{ipHash},
                #{userAgentHash}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AuthAuditEvent event);
}
