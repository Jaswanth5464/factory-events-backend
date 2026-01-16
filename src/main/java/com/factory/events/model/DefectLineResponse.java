package com.factory.events.model;

import lombok.AllArgsConstructor;
import lombok.Data;

// Response for GET /stats/top-defect-lines endpoint
@Data
@AllArgsConstructor
public class DefectLineResponse {
    private String lineId;
    private long totalDefects;      // Total defects for this line
    private long eventCount;        // Total events for this line
    private double defectsPercent;  // (totalDefects / eventCount) * 100, rounded to 2 decimals
}