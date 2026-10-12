package devPilot.backend.dto;

import java.util.List;
import java.util.UUID;

public record LearningCourseDetailResponse(
        UUID id,
        String name,
        List<LearningUnitResponse> units) {
}
