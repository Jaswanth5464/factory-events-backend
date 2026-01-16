package com.factory.events.repository;

import com.factory.events.model.Event;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Fetch existing events by their IDs with row-level locking
    // FOR UPDATE ensures no other transaction can modify these rows until we're done
    // This prevents race conditions when multiple threads try to update same event
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Map<String, Event> findExistingEventsWithLock(List<String> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }

        // Create placeholders for SQL IN clause: (?, ?, ?)
        String placeholders = eventIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = String.format(
                "SELECT * FROM events WHERE event_id IN (%s) FOR UPDATE",
                placeholders
        );

        List<Event> events = jdbcTemplate.query(sql, new EventRowMapper(), eventIds.toArray());

        // Convert list to map for fast lookup: eventId → Event
        return events.stream()
                .collect(Collectors.toMap(Event::getEventId, e -> e));
    }

    // Insert multiple new events in one batch query
    // Much faster than inserting one-by-one
    @Transactional
    public void batchInsert(List<Event> events) {
        if (events.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO events (event_id, event_time, received_time, machine_id, 
                               line_id, factory_id, duration_ms, defect_count, payload_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, events, events.size(), (ps, event) -> {
            ps.setString(1, event.getEventId());
            ps.setTimestamp(2, Timestamp.from(event.getEventTime()));
            ps.setTimestamp(3, Timestamp.from(event.getReceivedTime()));
            ps.setString(4, event.getMachineId());
            ps.setString(5, event.getLineId());
            ps.setString(6, event.getFactoryId());
            ps.setInt(7, event.getDurationMs());
            ps.setInt(8, event.getDefectCount());
            ps.setString(9, event.getPayloadHash());
        });
    }

    // Update multiple existing events in one batch query
    @Transactional
    public void batchUpdate(List<Event> events) {
        if (events.isEmpty()) {
            return;
        }

        String sql = """
            UPDATE events SET
                event_time = ?,
                received_time = ?,
                machine_id = ?,
                line_id = ?,
                factory_id = ?,
                duration_ms = ?,
                defect_count = ?,
                payload_hash = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE event_id = ?
            """;

        jdbcTemplate.batchUpdate(sql, events, events.size(), (ps, event) -> {
            ps.setTimestamp(1, Timestamp.from(event.getEventTime()));
            ps.setTimestamp(2, Timestamp.from(event.getReceivedTime()));
            ps.setString(3, event.getMachineId());
            ps.setString(4, event.getLineId());
            ps.setString(5, event.getFactoryId());
            ps.setInt(6, event.getDurationMs());
            ps.setInt(7, event.getDefectCount());
            ps.setString(8, event.getPayloadHash());
            ps.setString(9, event.getEventId());
        });
    }
}