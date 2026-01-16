package com.factory.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

// This is the actual entity we store in database
// After validation, we convert EventDTO → Event
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    private String eventId;
    private Instant eventTime;      // Parsed from ISO string to Instant
    private Instant receivedTime;   // Set by our service when event arrives
    private String machineId;
    private String lineId;
    private String factoryId;
    private Integer durationMs;
    private Integer defectCount;
    private String payloadHash;     // SHA-256 hash to detect payload changes
    private Instant createdAt;
    private Instant updatedAt;
}