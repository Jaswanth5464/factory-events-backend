package com.factory.events;

import com.factory.events.model.BatchResponse;
import com.factory.events.model.EventDTO;
import com.factory.events.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EventsApplicationTests {

    @Autowired
    private EventService eventService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Helper method to clear database before each test
    private void clearDatabase() {
        jdbcTemplate.execute("DELETE FROM events");
    }

    // ✅ Test 1: Identical duplicate eventId should be deduped
    @Test
    void testIdenticalDuplicateIsDeduped() {
        clearDatabase();

        EventDTO event = createValidEvent("E-1", "M-001", 1000, 0);

        BatchResponse response1 = eventService.processBatch(List.of(event));
        BatchResponse response2 = eventService.processBatch(List.of(event));

        assertEquals(1, response1.getAccepted());
        assertEquals(0, response1.getDeduped());

        assertEquals(0, response2.getAccepted());
        assertEquals(1, response2.getDeduped());
    }

    // ✅ Test 2: Different payload with newer receivedTime should update
    @Test
    void testDifferentPayloadNewerTimeUpdates() throws InterruptedException {
        clearDatabase();

        EventDTO event1 = createValidEvent("E-2", "M-001", 1000, 0);
        BatchResponse response1 = eventService.processBatch(List.of(event1));
        assertEquals(1, response1.getAccepted());

        Thread.sleep(1000);

        EventDTO event2 = createValidEvent("E-2", "M-001", 2000, 0);
        BatchResponse response2 = eventService.processBatch(List.of(event2));

        assertEquals(0, response2.getAccepted());
        assertEquals(1, response2.getUpdated());
    }

    // ✅ Test 3: Different payload with older receivedTime should be ignored
    @Test
    void testDifferentPayloadOlderTimeIgnored() {
        clearDatabase();

        Instant futureTime = Instant.now().plus(1, ChronoUnit.HOURS);

        jdbcTemplate.update(
                "INSERT INTO events (event_id, event_time, received_time, machine_id, line_id, factory_id, duration_ms, defect_count, payload_hash, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "E-3",
                Timestamp.from(Instant.now()),
                Timestamp.from(futureTime),
                "M-001",
                "L-01",
                "F-01",
                1000,
                0,
                "hash123",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );

        EventDTO event = createValidEvent("E-3", "M-001", 2000, 0);
        BatchResponse response = eventService.processBatch(List.of(event));

        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getUpdated());
    }

    // ✅ Test 4: Invalid duration should be rejected
    @Test
    void testInvalidDurationRejected() {
        clearDatabase();

        EventDTO event = createValidEvent("E-4", "M-001", -100, 0);
        BatchResponse response = eventService.processBatch(List.of(event));

        assertEquals(1, response.getRejected());
        assertEquals("INVALID_DURATION", response.getRejections().get(0).getReason());
    }

    // ✅ Test 5: Future eventTime should be rejected
    @Test
    void testFutureEventTimeRejected() {
        clearDatabase();

        Instant futureTime = Instant.now().plus(20, ChronoUnit.MINUTES);
        EventDTO event = createEventWithTime("E-5", "M-001", futureTime, 1000, 0);
        BatchResponse response = eventService.processBatch(List.of(event));

        assertEquals(1, response.getRejected());
        assertEquals("FUTURE_EVENT_TIME", response.getRejections().get(0).getReason());
    }

    // ✅ Test 6: defectCount = -1 should be stored
    @Test
    void testDefectCountMinusOneStored() {
        clearDatabase();

        EventDTO event = createValidEvent("E-6", "M-001", 1000, -1);
        BatchResponse response = eventService.processBatch(List.of(event));

        assertEquals(1, response.getAccepted());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE event_id = 'E-6'",
                Integer.class
        );

        assertEquals(1, count);
    }

    // ✅ Test 7: Start inclusive and End exclusive boundary
    @Test
    void testStartEndBoundary() {
        clearDatabase();

        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        Instant end = Instant.parse("2026-01-15T06:00:00Z");

        EventDTO event1 = createEventWithTime("E-7", "M-001", start, 1000, 1);
        EventDTO event2 = createEventWithTime("E-8", "M-001", end, 1000, 1);

        eventService.processBatch(List.of(event1, event2));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE machine_id = 'M-001' AND event_time >= ? AND event_time < ?",
                Integer.class,
                Timestamp.from(start),
                Timestamp.from(end)
        );

        assertEquals(1, count);
    }

    // ✅ Test 8: Thread-safety concurrent ingestion
    @Test
    void testConcurrentIngestion() throws InterruptedException {
        clearDatabase();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    EventDTO event = createValidEvent("E-CONCURRENT", "M-001", 1000 + index, 0);
                    eventService.processBatch(List.of(event));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE event_id = 'E-CONCURRENT'",
                Integer.class
        );

        assertEquals(1, count);
    }

    // ✅ Helper methods
    private EventDTO createValidEvent(String eventId, String machineId, int durationMs, int defectCount) {
        return createEventWithTime(eventId, machineId, Instant.now(), durationMs, defectCount);
    }

    private EventDTO createEventWithTime(String eventId, String machineId, Instant eventTime, int durationMs, int defectCount) {
        EventDTO dto = new EventDTO();
        dto.setEventId(eventId);
        dto.setEventTime(eventTime.toString());
        dto.setReceivedTime(Instant.now().toString()); // ignored by service
        dto.setMachineId(machineId);
        dto.setLineId("L-01");
        dto.setFactoryId("F-01");
        dto.setDurationMs(durationMs);
        dto.setDefectCount(defectCount);
        return dto;
    }
}
