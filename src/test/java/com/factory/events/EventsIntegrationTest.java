package com.factory.events;

import com.factory.events.model.BatchResponse;
import com.factory.events.model.EventDTO;
import com.factory.events.model.StatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDb() {
        jdbcTemplate.execute("DELETE FROM events");
    }

    // ✅ Integration Test 1: POST /events/batch inserts event
    @Test
    void testIngestBatchApi_InsertsEvent() {
        String url = "/events/batch";

        EventDTO dto = createEvent("IT-1", "M-100", "L-01", "F-01", 1000, 2, Instant.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<EventDTO>> request = new HttpEntity<>(List.of(dto), headers);

        ResponseEntity<BatchResponse> response =
                restTemplate.exchange(url, HttpMethod.POST, request, BatchResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(1, response.getBody().getAccepted());
        assertEquals(0, response.getBody().getRejected());

        // verify stored in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE event_id = 'IT-1'",
                Integer.class
        );
        assertEquals(1, count);
    }

    // ✅ Integration Test 2: Duplicate payload should dedupe
    @Test
    void testIngestBatchApi_DedupesDuplicateEvent() {
        String url = "/events/batch";

        EventDTO dto = createEvent("IT-2", "M-200", "L-01", "F-01", 1000, 0, Instant.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<EventDTO>> request = new HttpEntity<>(List.of(dto), headers);

        ResponseEntity<BatchResponse> response1 =
                restTemplate.exchange(url, HttpMethod.POST, request, BatchResponse.class);
        ResponseEntity<BatchResponse> response2 =
                restTemplate.exchange(url, HttpMethod.POST, request, BatchResponse.class);

        assertEquals(1, response1.getBody().getAccepted());
        assertEquals(1, response2.getBody().getDeduped());
    }

    // ✅ Integration Test 3: GET /stats returns correct values
    @Test
    void testStatsApi_ReturnsStats() {
        // Insert 2 events
        jdbcTemplate.update("""
                INSERT INTO events (event_id, event_time, received_time, machine_id, line_id, factory_id, duration_ms, defect_count, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "S-1",
                java.sql.Timestamp.from(Instant.parse("2026-01-16T00:10:00Z")),
                java.sql.Timestamp.from(Instant.now()),
                "M-STATS",
                "L-01",
                "F-01",
                1000,
                2,
                "hash1"
        );

        jdbcTemplate.update("""
                INSERT INTO events (event_id, event_time, received_time, machine_id, line_id, factory_id, duration_ms, defect_count, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "S-2",
                java.sql.Timestamp.from(Instant.parse("2026-01-16T00:20:00Z")),
                java.sql.Timestamp.from(Instant.now()),
                "M-STATS",
                "L-01",
                "F-01",
                1000,
                3,
                "hash2"
        );

        String url = "/stats?machineId=M-STATS&start=2026-01-16T00:00:00Z&end=2026-01-16T01:00:00Z";

        ResponseEntity<StatsResponse> response =
                restTemplate.getForEntity(url, StatsResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals("M-STATS", response.getBody().getMachineId());
        assertEquals(2, response.getBody().getEventsCount());
        assertEquals(5, response.getBody().getDefectsCount());
    }

    // ✅ Integration Test 4: GET /stats/top-defect-lines returns lines sorted
    @Test
    void testTopDefectLinesApi_ReturnsSortedLines() {
        // insert multiple lines for factory F-X
        insertEvent("TD-1", "F-X", "L-A", 5, Instant.parse("2026-01-16T02:10:00Z"));
        insertEvent("TD-2", "F-X", "L-B", 1, Instant.parse("2026-01-16T02:20:00Z"));
        insertEvent("TD-3", "F-X", "L-A", 2, Instant.parse("2026-01-16T02:30:00Z"));

        String url = "/stats/top-defect-lines?factoryId=F-X&from=2026-01-16T02:00:00Z&to=2026-01-16T03:00:00Z&limit=10";

        ResponseEntity<Object[]> response =
                restTemplate.getForEntity(url, Object[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        // First object should be L-A (7 defects)
        Map firstRow = (Map) response.getBody()[0];
        assertEquals("L-A", firstRow.get("lineId"));
        assertEquals(7, ((Number) firstRow.get("totalDefects")).intValue());
    }

    // ----------------- helper methods -----------------

    private EventDTO createEvent(String eventId, String machineId, String lineId, String factoryId,
                                 int durationMs, int defectCount, Instant eventTime) {
        EventDTO dto = new EventDTO();
        dto.setEventId(eventId);
        dto.setEventTime(eventTime.toString());
        dto.setMachineId(machineId);
        dto.setLineId(lineId);
        dto.setFactoryId(factoryId);
        dto.setDurationMs(durationMs);
        dto.setDefectCount(defectCount);
        return dto;
    }

    private void insertEvent(String eventId, String factoryId, String lineId, int defectCount, Instant eventTime) {
        jdbcTemplate.update("""
                INSERT INTO events (event_id, event_time, received_time, machine_id, line_id, factory_id, duration_ms, defect_count, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId,
                java.sql.Timestamp.from(eventTime),
                java.sql.Timestamp.from(Instant.now()),
                "M-TEST",
                lineId,
                factoryId,
                1000,
                defectCount,
                "hash-" + eventId
        );
    }
}
