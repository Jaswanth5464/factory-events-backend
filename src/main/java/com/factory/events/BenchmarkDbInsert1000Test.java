package com.factory.events;

import com.factory.events.model.Event;
import com.factory.events.model.EventDTO;
import com.factory.events.repository.EventRepository;
import com.factory.events.util.PayloadHasher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BenchmarkDbInsert1000Test {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testDirectBatchInsertTimeWithFullBreakdown() throws Exception {

        // ✅ STEP 0: Clear DB
        long t0 = System.nanoTime();
        jdbcTemplate.execute("DELETE FROM events");
        long t1 = System.nanoTime();

        // ✅ STEP 1: File locate
        String absolutePath = "C:\\Users\\kanam\\Downloads\\springbootprojects\\events\\events\\src\\test\\resources\\events_1000.json";
        long t2 = System.nanoTime();
        File file = new File(absolutePath);
        long t3 = System.nanoTime();

        if (!file.exists()) {
            fail("❌ File not found: " + absolutePath);
        }

        // ✅ STEP 2: JSON Parse (File -> List<EventDTO>)
        ObjectMapper mapper = new ObjectMapper();
        long t4 = System.nanoTime();
        List<EventDTO> dtos = mapper.readValue(file, new TypeReference<List<EventDTO>>() {});
        long t5 = System.nanoTime();

        assertFalse(dtos.isEmpty(), "❌ JSON file empty!");

        // ✅ STEP 3: Convert DTO -> Event + compute hash
        Instant now = Instant.now();
        long t6 = System.nanoTime();
        List<Event> events = dtos.stream()
                .map(dto -> Event.builder()
                        .eventId(dto.getEventId())
                        .eventTime(Instant.parse(dto.getEventTime()))
                        .receivedTime(now)
                        .machineId(dto.getMachineId())
                        .lineId(dto.getLineId())
                        .factoryId(dto.getFactoryId())
                        .durationMs(dto.getDurationMs())
                        .defectCount(dto.getDefectCount())
                        .payloadHash(PayloadHasher.computeHash(dto)) // hash cost included here
                        .build())
                .collect(Collectors.toList());
        long t7 = System.nanoTime();

        // ✅ STEP 4: Warm-up DB insert (ignore this time)
        long t8 = System.nanoTime();
        eventRepository.batchInsert(events);
        long t9 = System.nanoTime();

        // ✅ STEP 5: Clear DB again
        long t10 = System.nanoTime();
        jdbcTemplate.execute("DELETE FROM events");
        long t11 = System.nanoTime();

        // ✅ STEP 6: REAL DB insert benchmark
        long t12 = System.nanoTime();
        eventRepository.batchInsert(events);
        long t13 = System.nanoTime();

        // ✅ STEP 7: DB count check
        long t14 = System.nanoTime();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class);
        long t15 = System.nanoTime();

        // ✅ Convert nanoseconds -> milliseconds
        double clearDb1Ms = (t1 - t0) / 1_000_000.0;
        double fileLocateMs = (t3 - t2) / 1_000_000.0;
        double jsonParseMs = (t5 - t4) / 1_000_000.0;
        double dtoToEventMs = (t7 - t6) / 1_000_000.0;
        double warmupInsertMs = (t9 - t8) / 1_000_000.0;
        double clearDb2Ms = (t11 - t10) / 1_000_000.0;
        double realInsertMs = (t13 - t12) / 1_000_000.0;
        double countQueryMs = (t15 - t14) / 1_000_000.0;

        double totalMs = clearDb1Ms + fileLocateMs + jsonParseMs + dtoToEventMs + warmupInsertMs + clearDb2Ms + realInsertMs + countQueryMs;

        System.out.println("\n================= BENCHMARK BREAKDOWN =================");
        System.out.println("✅ Events Loaded : " + dtos.size());
        System.out.println("-------------------------------------------------------");
        System.out.println("🧹 Clear DB (1st)               : " + clearDb1Ms + " ms");
        System.out.println("📁 File Locate                  : " + fileLocateMs + " ms");
        System.out.println("📦 JSON Parse (File->DTO list)  : " + jsonParseMs + " ms");
        System.out.println("🔁 DTO->Event + Hash compute    : " + dtoToEventMs + " ms");
        System.out.println("🔥 Warm-up DB Insert            : " + warmupInsertMs + " ms");
        System.out.println("🧹 Clear DB (2nd)               : " + clearDb2Ms + " ms");
        System.out.println("🚀 REAL DB Insert (measured)    : " + realInsertMs + " ms");
        System.out.println("🔍 DB Count Query               : " + countQueryMs + " ms");
        System.out.println("-------------------------------------------------------");
        System.out.println("✅ Rows Inserted                : " + count);
        System.out.println("⏱️ TOTAL (all steps combined)    : " + totalMs + " ms");
        System.out.println("=======================================================\n");

        assertEquals(1000, count);
        assertTrue(realInsertMs < 1000, "❌ REAL DB insert took more than 1 second: " + realInsertMs + " ms");
    }
}
