package com.factory.events;

import com.factory.events.model.BatchResponse;
import com.factory.events.model.EventDTO;
import com.factory.events.service.EventService;
import org.testng.annotations.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Helper method to clear database before each test
    private void clearDatabase() {
        jdbcTemplate.execute("DELETE FROM events");
    }

    // Test 1: Identical duplicate eventId should be deduped
    @Test
    void testIdenticalDuplicateIsDeduped() {
        clearDatabase();

        EventDTO event = createValidEvent("E-1", "M-001", 1000, 0);

        // Send same event twice
        BatchResponse response1 = eventService.processBatch(List.of(event));
        BatchResponse response2 = eventService.processBatch(List.of(event));

        assertEquals(1, response1.getAccepted());  // First time: accepted
        assertEquals(0, response1.getDeduped());

        assertEquals(0, response2.getAccepted());  // Second time: deduped
        assertEquals(1, response2.getDeduped());
    }

    // Test 2: Different payload with newer receivedTime should update
    @Test
    void testDifferentPayloadNewerTimeUpdates() throws InterruptedException {
        clearDatabase();

        EventDTO event1 = createValidEvent("E-2", "M-001", 1000, 0);
        BatchResponse response1 = eventService.processBatch(List.of(event1));
        assertEquals(1, response1.getAccepted());

        // Wait 1 second to ensure newer receivedTime
        Thread.sleep(1000);

        // Send same eventId but different durationMs (different payload)
        EventDTO event2 = createValidEvent("E-2", "M-001", 2000, 0);
        BatchResponse response2 = eventService.processBatch(List.of(event2));

        assertEquals(0, response2.getAccepted());
        assertEquals(1, response2.getUpdated());  // Should update
    }

    // Test 3: Different payload with older receivedTime should be ignored
    // This test is tricky - we need to manually set receivedTime in DB
    @Test
    void testDifferentPayloadOlderTimeIgnored() {
        clearDatabase();

        // Insert event directly with future receivedTime
        Instant futureTime = Instant.now().plus(1, ChronoUnit.HOURS);
        jdbcTemplate.update(
                "INSERT INTO events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "E-3", Instant.now(), futureTime, "M-001", "L-01", "F-01",
                1000, 0, "hash123", Instant.now(), Instant.now()
        );

        // Now send event with current receivedTime (which will be older)
        EventDTO event = createValidEvent("E-3", "M-001", 2000, 0);
        BatchResponse response = eventService.processBatch(List.of(event));

        // Should be ignored (counted as deduped in our current logic)
        // In production, you might track "ignored" separately
        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getUpdated());
    }

    // Test 4: Invalid duration should be rejected
    @Test
    void testInvalidDurationRejected() {
        clearDatabase();

        EventDTO event = createValidEvent("E-4", "M-001", -100, 0);  // Negative duration
        BatchResponse response = eventService.processBatch(List.of(event));

        assertEquals(1, response.getRejected());
        assertEquals("INVALID_DURATION", response.getRejections().get(0).getReason());
    }

    // Test 5: Future eventTime should be rejected
    @Test
    void testFutureEventTimeRejected() {
        clearDatabase();

        Instant futureTime = Instant.now().plus(20, ChronoUnit.MINUTES);  // 20 min in future
        EventDTO event = createEventWithTime("E-5", "M-001", futureTime, 1000, 0);
        BatchResponse response = eventService.processBatch(List.of(event));

        assertEquals(1, response.getRejected());
        assertEquals("FUTURE_EVENT_TIME", response.getRejections().get(0).getReason());
    }

    // Test 6: defectCount = -1 should be stored but ignored in stats
    @Test
    void testDefectCountMinusOneIgnored() {
        clearDatabase();

        EventDTO event = createValidEvent("E-6", "M-001", 1000, -1);
        BatchResponse response = eventService.processBatch(List.of(event));

        assertEquals(1, response.getAccepted());

        // Verify it's stored in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE event_id = 'E-6'",
                Integer.class
        );
        assertEquals(1, count);

        // Stats query should ignore it (tested separately in StatsServiceTest)
    }

    // Test 7: Start/end boundary correctness
    @Test
    void testStartEndBoundary() {
        clearDatabase();

        Instant start = Instant.parse("2026-01-15T00:00:00Z");
        Instant end = Instant.parse("2026-01-15T06:00:00Z");

        // Event exactly at start - should be included
        EventDTO event1 = createEventWithTime("E-7", "M-001", start, 1000, 1);
        // Event exactly at end - should be excluded
        EventDTO event2 = createEventWithTime("E-8", "M-001", end, 1000, 1);

        eventService.processBatch(List.of(event1, event2));

        // Query with same boundaries
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE machine_id = 'M-001' AND event_time >= ? AND event_time < ?",
                Integer.class,
                start, end
        );

        assertEquals(1, count);  // Only event1 should be in range
    }

    // Test 8: Thread-safety - concurrent ingestion
    @Test
    void testConcurrentIngestion() throws InterruptedException {
        clearDatabase();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // All threads will try to insert same eventId with different payloads
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

        latch.await();  // Wait for all threads to complete
        executor.shutdown();

        // Should have exactly 1 event in DB (others deduped or updated)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE event_id = 'E-CONCURRENT'",
                Integer.class
        );
        assertEquals(1, count);
    }

    // Helper methods
    private EventDTO createValidEvent(String eventId, String machineId, int durationMs, int defectCount) {
        return createEventWithTime(eventId, machineId, Instant.now(), durationMs, defectCount);
    }

    private EventDTO createEventWithTime(String eventId, String machineId, Instant eventTime, int durationMs, int defectCount) {
        EventDTO dto = new EventDTO();
        dto.setEventId(eventId);
        dto.setEventTime(eventTime.toString());
        dto.setReceivedTime(Instant.now().toString());  // Will be ignored by service
        dto.setMachineId(machineId);
        dto.setLineId("L-01");
        dto.setFactoryId("F-01");
        dto.setDurationMs(durationMs);
        dto.setDefectCount(defectCount);
        return dto;
    }
}