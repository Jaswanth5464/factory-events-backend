package com.factory.events.model;

import lombok.Builder;
import lombok.Data;

// Response for GET /stats endpoint
@Data
@Builder
public class StatsResponse {
    private String machineId;
    private String start;
    private String end;
    private long eventsCount;      // Total events in time window
    private long defectsCount;     // Total defects (excluding defectCount=-1)
    private double avgDefectRate;  // Defects per hour
    private String status;         // "Healthy" or "Warning"
}