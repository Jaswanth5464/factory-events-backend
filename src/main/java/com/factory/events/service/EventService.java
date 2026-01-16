package com.factory.events.service;

import com.factory.events.model.*;
import com.factory.events.repository.EventRepository;
import com.factory.events.util.PayloadHasher;
import com.factory.events.util.ValidationUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    // Main method to process a batch of events
    // This implements the core dedupe/update logic
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public BatchResponse processBatch(List<EventDTO> dtos) {

        List<Rejection> rejections = new ArrayList<>();
        List<Event> validEvents = new ArrayList<>();

        // STEP 1: Validate all incoming events
        Instant currentTime = Instant.now();  // Set receivedTime for all events in this batch

        for (EventDTO dto : dtos) {
            String validationError = ValidationUtil.validate(dto);

            if (validationError != null) {
                // Event failed validation - reject it
                rejections.add(new Rejection(dto.getEventId(), validationError));
            } else {
                // Event passed validation - convert DTO to Entity
                String hash = PayloadHasher.computeHash(dto);

                Event event = Event.builder()
                        .eventId(dto.getEventId())
                        .eventTime(Instant.parse(dto.getEventTime()))
                        .receivedTime(currentTime)  // WE set this, ignore value from request
                        .machineId(dto.getMachineId())
                        .lineId(dto.getLineId())
                        .factoryId(dto.getFactoryId())
                        .durationMs(dto.getDurationMs())
                        .defectCount(dto.getDefectCount())
                        .payloadHash(hash)
                        .build();

                validEvents.add(event);
            }
        }

        if (validEvents.isEmpty()) {
            // All events were rejected
            return BatchResponse.builder()
                    .accepted(0)
                    .deduped(0)
                    .updated(0)
                    .rejected(rejections.size())
                    .rejections(rejections)
                    .build();
        }

        // STEP 2: Fetch existing events from database (with locking)
        List<String> eventIds = validEvents.stream()
                .map(Event::getEventId)
                .collect(Collectors.toList());

        Map<String, Event> existingEvents = repository.findExistingEventsWithLock(eventIds);

        // STEP 3: Classify events into: new, dedupe, update, or ignore
        List<Event> toInsert = new ArrayList<>();
        List<Event> toUpdate = new ArrayList<>();
        int dedupedCount = 0;
        int ignoredCount = 0;

        for (Event newEvent : validEvents) {
            Event existing = existingEvents.get(newEvent.getEventId());

            if (existing == null) {
                // Event doesn't exist in DB - INSERT it
                toInsert.add(newEvent);
            } else {
                // Event already exists - check if payload changed
                boolean samePayload = existing.getPayloadHash().equals(newEvent.getPayloadHash());

                if (samePayload) {
                    // Same eventId + same payload = DEDUPE (ignore)
                    dedupedCount++;
                } else {
                    // Different payload - check receivedTime to decide update or ignore
                    boolean newerReceivedTime = newEvent.getReceivedTime().isAfter(existing.getReceivedTime());

                    if (newerReceivedTime) {
                        // Different payload + newer receivedTime = UPDATE
                        toUpdate.add(newEvent);
                    } else {
                        // Different payload + older receivedTime = IGNORE
                        ignoredCount++;
                    }
                }
            }
        }

        // STEP 4: Execute batch operations
        repository.batchInsert(toInsert);
        repository.batchUpdate(toUpdate);

        // STEP 5: Build response
        return BatchResponse.builder()
                .accepted(toInsert.size())
                .deduped(dedupedCount)
                .updated(toUpdate.size())
                .rejected(rejections.size())
                .rejections(rejections)
                .build();
    }
}