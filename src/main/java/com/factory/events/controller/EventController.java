package com.factory.events.controller;

import com.factory.events.model.BatchResponse;
import com.factory.events.model.EventDTO;
import com.factory.events.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // POST /events/batch
    // Accepts array of events and processes them in batch
    @PostMapping("/batch")
    public ResponseEntity<BatchResponse> ingestBatch(@RequestBody List<EventDTO> events) {
        BatchResponse response = eventService.processBatch(events);
        return ResponseEntity.ok(response);
    }
}