package com.factory.events.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

// Response sent back after batch ingestion
@Data
@Builder
public class BatchResponse {
    private int accepted;   // New events inserted successfully
    private int deduped;    // Events with identical payload, ignored
    private int updated;    // Events with different payload and newer receivedTime
    private int rejected;   // Events that failed validation
    private List<Rejection> rejections;  // Details of rejected events
}