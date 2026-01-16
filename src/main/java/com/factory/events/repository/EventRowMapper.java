package com.factory.events.repository;

import com.factory.events.model.Event;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

// Converts database rows into Event objects
public class EventRowMapper implements RowMapper<Event> {

    @Override
    public Event mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Event.builder()
                .eventId(rs.getString("event_id"))
                .eventTime(rs.getTimestamp("event_time").toInstant())
                .receivedTime(rs.getTimestamp("received_time").toInstant())
                .machineId(rs.getString("machine_id"))
                .lineId(rs.getString("line_id"))
                .factoryId(rs.getString("factory_id"))
                .durationMs(rs.getInt("duration_ms"))
                .defectCount(rs.getInt("defect_count"))
                .payloadHash(rs.getString("payload_hash"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .build();
    }
}