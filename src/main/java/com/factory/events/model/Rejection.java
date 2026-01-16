package com.factory.events.model;

import lombok.AllArgsConstructor;
import lombok.Data;

// Represents a single rejected event with reason
@Data
@AllArgsConstructor
public class Rejection {
    private String eventId;
    private String reason;  // e.g., "INVALID_DURATION", "FUTURE_EVENT_TIME"
}