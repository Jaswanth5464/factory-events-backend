package com.factory.events.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// This represents the incoming JSON from the request
// We use this to receive data, then convert to Event entity
@Data
public class EventDTO {
    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventTime")
    private String eventTime;  // ISO timestamp string like "2026-01-15T10:12:03.123Z"

    @JsonProperty("receivedTime")
    private String receivedTime;  // We ignore this - set by our service

    @JsonProperty("machineId")
    private String machineId;

    @JsonProperty("lineId")
    private String lineId;  // For top defect lines endpoint

    @JsonProperty("factoryId")
    private String factoryId;  // For top defect lines endpoint

    @JsonProperty("durationMs")
    private Integer durationMs;

    @JsonProperty("defectCount")
    private Integer defectCount;
}