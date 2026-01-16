package com.factory.events.util;

import com.factory.events.model.EventDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PayloadHasher {

    // Creates a unique fingerprint of the event data
    // If ANY field changes, hash will be different
    // This helps us detect "different payload" for dedupe/update logic
    public static String computeHash(EventDTO dto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");

            // Concatenate all fields in a consistent order
            // Note: We ignore receivedTime as it's set by our service
            String payload = String.format("%s|%s|%s|%s|%s|%d|%d",
                    dto.getEventId(),
                    dto.getEventTime(),
                    dto.getMachineId(),
                    dto.getLineId() != null ? dto.getLineId() : "",
                    dto.getFactoryId() != null ? dto.getFactoryId() : "",
                    dto.getDurationMs(),
                    dto.getDefectCount()
            );

            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));

            // Convert bytes to hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}