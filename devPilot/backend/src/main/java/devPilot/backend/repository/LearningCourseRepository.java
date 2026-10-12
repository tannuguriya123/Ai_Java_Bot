package devPilot.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import devPilot.backend.entity.LearningCourse;

public interface LearningCourseRepository extends JpaRepository<LearningCourse, UUID> {
    List<LearningCourse> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<LearningCourse> findFirstByUserIdAndNameIgnoreCase(UUID userId, String name);

    Optional<LearningCourse> findByIdAndUserId(UUID id, UUID userId);
}
