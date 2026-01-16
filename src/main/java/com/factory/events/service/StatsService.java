package com.factory.events.service;

import com.factory.events.model.DefectLineResponse;
import com.factory.events.model.StatsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class StatsService {

    private final JdbcTemplate jdbcTemplate;

    public StatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Query stats for a specific machine within a time window
    public StatsResponse getStats(String machineId, Instant start, Instant end) {

        // SQL query to get event count and defect total
        // We ignore defectCount = -1 in the sum
        String sql = """
            SELECT 
                COUNT(*) as event_count,
                SUM(CASE WHEN defect_count != -1 THEN defect_count ELSE 0 END) as defect_total
            FROM events
            WHERE machine_id = ?
              AND event_time >= ?
              AND event_time < ?
            """;

        var result = jdbcTemplate.queryForMap(
                sql,
                machineId,
                java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(end)
        );


        long eventsCount = ((Number) result.get("event_count")).longValue();
        long defectsCount = ((Number) result.get("defect_total")).longValue();

        // Calculate time window in hours
        double windowSeconds = Duration.between(start, end).getSeconds();
        double windowHours = windowSeconds / 3600.0;

        // Calculate average defect rate per hour
        double avgDefectRate = windowHours > 0 ? defectsCount / windowHours : 0.0;

        // Determine status based on threshold
        String status = avgDefectRate < 2.0 ? "Healthy" : "Warning";

        return StatsResponse.builder()
                .machineId(machineId)
                .start(start.toString())
                .end(end.toString())
                .eventsCount(eventsCount)
                .defectsCount(defectsCount)
                .avgDefectRate(Math.round(avgDefectRate * 100.0) / 100.0)  // Round to 2 decimals
                .status(status)
                .build();
    }

    // Query top defect lines for a factory within a time window
    public List<DefectLineResponse> getTopDefectLines(String factoryId, Instant from, Instant to, int limit) {

        // SQL query groups by line_id and calculates totals
        // Orders by total defects descending, then limits results
        String sql = """
            SELECT 
                line_id,
                COUNT(*) as event_count,
                SUM(CASE WHEN defect_count != -1 THEN defect_count ELSE 0 END) as total_defects
            FROM events
            WHERE factory_id = ?
              AND event_time >= ?
              AND event_time < ?
              AND line_id IS NOT NULL
            GROUP BY line_id
            ORDER BY total_defects DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String lineId = rs.getString("line_id");
            long eventCount = rs.getLong("event_count");
            long totalDefects = rs.getLong("total_defects");

            // Calculate defects per 100 events (percentage)
            double defectsPercent = eventCount > 0
                    ? (totalDefects * 100.0 / eventCount)
                    : 0.0;

            // Round to 2 decimal places
            defectsPercent = Math.round(defectsPercent * 100.0) / 100.0;

            return new DefectLineResponse(lineId, totalDefects, eventCount, defectsPercent);
        }, factoryId,  java.sql.Timestamp.from(from),
                java.sql.Timestamp.from(to), limit);
    }
}