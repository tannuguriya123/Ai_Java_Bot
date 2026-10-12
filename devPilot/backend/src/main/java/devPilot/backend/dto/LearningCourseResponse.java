package devPilot.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record LearningCourseResponse(
        UUID id,
        String name,
        long unitCount,
        long readyUnitCount,
        long chunkCount,
        Instant updatedAt) {
}
