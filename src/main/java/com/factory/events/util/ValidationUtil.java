package com.factory.events.util;

import com.factory.events.model.EventDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ValidationUtil {

    private static final long SIX_HOURS_MS = 6 * 60 * 60 * 1000;  // 6 hours in milliseconds

    // Validates an event according to assignment rules
    // Returns null if valid, or error reason string if invalid
    public static String validate(EventDTO dto) {

        // Rule 1: durationMs must be >= 0 and <= 6 hours
        if (dto.getDurationMs() == null || dto.getDurationMs() < 0) {
            return "INVALID_DURATION";
        }
        if (dto.getDurationMs() > SIX_HOURS_MS) {
            return "INVALID_DURATION";
        }

        // Rule 2: eventTime must not be more than 15 minutes in the future
        try {
            Instant eventTime = Instant.parse(dto.getEventTime());
            Instant now = Instant.now();
            Instant maxAllowed = now.plus(15, ChronoUnit.MINUTES);

            if (eventTime.isAfter(maxAllowed)) {
                return "FUTURE_EVENT_TIME";
            }
        } catch (Exception e) {
            return "INVALID_EVENT_TIME_FORMAT";
        }

        // Rule 3: eventId must be present
        if (dto.getEventId() == null || dto.getEventId().trim().isEmpty()) {
            return "MISSING_EVENT_ID";
        }

        // Rule 4: machineId must be present
        if (dto.getMachineId() == null || dto.getMachineId().trim().isEmpty()) {
            return "MISSING_MACHINE_ID";
        }

        return null;  // null means validation passed
    }
}