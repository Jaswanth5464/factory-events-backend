package com.factory.events;

import com.factory.events.model.EventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GenerateEventsJson {

    public static void main(String[] args) throws Exception {

        int totalEvents = 1000;

        List<EventDTO> events = new ArrayList<>();

        Instant baseTime = Instant.now();

        for (int i = 1; i <= totalEvents; i++) {

            EventDTO dto = new EventDTO();
            dto.setEventId("B-" + i);
            dto.setEventTime(baseTime.minusSeconds(i).toString()); // past times
            dto.setMachineId("M-001");
            dto.setLineId("L-01");
            dto.setFactoryId("F-01");
            dto.setDurationMs(1000);
            dto.setDefectCount(i % 5);  // 0 to 4 defects

            events.add(dto);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        File outputFile = new File("events_1000.json");
        mapper.writeValue(outputFile, events);

        System.out.println("✅ Created JSON file: " + outputFile.getAbsolutePath());
        System.out.println("✅ Total events written: " + totalEvents);
    }
}
