package devPilot.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
        UUID id,
        UUID repositoryId,
        UUID courseId,
        UUID unitId,
        String title,
        Instant createdAt) {
}
