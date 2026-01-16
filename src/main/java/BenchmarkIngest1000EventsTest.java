package com.factory.events;

import com.factory.events.model.BatchResponse;
import com.factory.events.model.EventDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BenchmarkIngest1000EventsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testIngest1000EventsTime() throws Exception {

        // ✅ Clear DB before test
        jdbcTemplate.execute("DELETE FROM events");

        // ✅ Put your JSON absolute path here
        String absolutePath = "C:\\Users\\kanam\\Downloads\\springbootprojects\\events\\events\\src\\test\\resources\\events_1000.json";

        File file = new File(absolutePath);
        if (!file.exists()) {
            fail("❌ File not found at: " + absolutePath);
        }

        // ✅ Read JSON file -> List<EventDTO>
        ObjectMapper mapper = new ObjectMapper();
        List<EventDTO> events = mapper.readValue(file, new TypeReference<List<EventDTO>>() {});

        assertFalse(events.isEmpty(), "❌ JSON file is empty!");

        // ✅ Prepare request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<EventDTO>> request = new HttpEntity<>(events, headers);

        // ✅ Benchmark start
        long startTime = System.currentTimeMillis();

        ResponseEntity<BatchResponse> response =
                restTemplate.exchange("/events/batch", HttpMethod.POST, request, BatchResponse.class);

        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;

        // ✅ Validate
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        System.out.println("=======================================");
        System.out.println("✅ Total events sent: " + events.size());
        System.out.println("✅ Accepted: " + response.getBody().getAccepted());
        System.out.println("✅ Deduped: " + response.getBody().getDeduped());
        System.out.println("✅ Updated: " + response.getBody().getUpdated());
        System.out.println("✅ Rejected: " + response.getBody().getRejected());
        System.out.println("⏱️ Time taken: " + totalTimeMs + " ms");
        System.out.println("=======================================");

        // ✅ Check DB count
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "❌ No events inserted into DB!");

        // ✅ Performance check
        assertTrue(totalTimeMs < 1000, "❌ Took more than 1 second! Actual: " + totalTimeMs + " ms");
    }
}
