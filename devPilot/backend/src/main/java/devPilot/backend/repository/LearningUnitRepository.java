package devPilot.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.LearningUnit;

public interface LearningUnitRepository extends JpaRepository<LearningUnit, UUID> {
    List<LearningUnit> findByCourseIdAndUserIdOrderByCreatedAtDesc(UUID courseId, UUID userId);

    Optional<LearningUnit> findByIdAndCourseIdAndUserId(UUID id, UUID courseId, UUID userId);

    long countByCourseIdAndUserId(UUID courseId, UUID userId);

    long countByCourseIdAndUserIdAndIndexStatus(UUID courseId, UUID userId, IndexStatus indexStatus);

    long countByCourseIdAndUserIdAndChunkCountGreaterThan(UUID courseId, UUID userId, int chunkCount);
}
