package devPilot.backend.dto;

import java.time.Instant;
import java.util.UUID;

import devPilot.backend.entity.IndexStatus;

public record LearningUnitResponse(
        UUID id,
        UUID courseId,
        String name,
        String fileName,
        String mediaType,
        IndexStatus indexStatus,
        int chunkCount,
        String errorMessage,
        Instant createdAt) {
}
