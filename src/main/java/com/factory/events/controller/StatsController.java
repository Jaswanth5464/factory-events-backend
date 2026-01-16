package com.factory.events.controller;

import com.factory.events.model.DefectLineResponse;
import com.factory.events.model.StatsResponse;
import com.factory.events.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    // GET /stats?machineId=M-001&start=2026-01-15T00:00:00Z&end=2026-01-15T06:00:00Z
    @GetMapping
    public ResponseEntity<StatsResponse> getStats(
            @RequestParam String machineId,
            @RequestParam String start,
            @RequestParam String end) {

        Instant startTime =Instant.parse(start);
        Instant endTime = Instant.parse(end);
        StatsResponse response = statsService.getStats(machineId, startTime, endTime);
        return ResponseEntity.ok(response);
    }

    // GET /stats/top-defect-lines?factoryId=F01&from=...&to=...&limit=10
    @GetMapping("/top-defect-lines")
    public ResponseEntity<List<DefectLineResponse>> getTopDefectLines(
            @RequestParam String factoryId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "10") int limit) {

        Instant fromTime = Instant.parse(from);
        Instant toTime = Instant.parse(to);

        List<DefectLineResponse> response = statsService.getTopDefectLines(factoryId, fromTime, toTime, limit);
        return ResponseEntity.ok(response);
    }
}
