package devPilot.backend.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import devPilot.backend.dto.LearningCourseDetailResponse;
import devPilot.backend.dto.LearningCourseResponse;
import devPilot.backend.dto.LearningUnitResponse;
import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.LearningCourse;
import devPilot.backend.entity.LearningUnit;
import devPilot.backend.exception.BadRequestException;
import devPilot.backend.exception.NotFoundException;
import devPilot.backend.repository.LearningCourseRepository;
import devPilot.backend.repository.LearningUnitRepository;
import devPilot.backend.service.indexing.LearningDocumentIndexer;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningService {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".pdf", ".ppt", ".pptx");

    private final LearningCourseRepository courseRepository;
    private final LearningUnitRepository unitRepository;
    private final LearningDocumentIndexer documentIndexer;

    @Value("${app.learning.max-file-bytes:26214400}")
    private long maxFileBytes;

    @Transactional(readOnly = true)
    public List<LearningCourseResponse> listCourses(UUID userId) {
        return courseRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(course -> toCourseResponse(course, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public LearningCourseDetailResponse getCourse(UUID userId, UUID courseId) {
        LearningCourse course = requireCourse(userId, courseId);
        List<LearningUnitResponse> units = unitRepository
                .findByCourseIdAndUserIdOrderByCreatedAtDesc(courseId, userId)
                .stream()
                .map(this::toUnitResponse)
                .toList();
        return new LearningCourseDetailResponse(course.getId(), course.getName(), units);
    }

    @Transactional
    public void deleteCourse(UUID userId, UUID courseId) {
        LearningCourse course = requireCourse(userId, courseId);
        List<LearningUnit> units = unitRepository
                .findByCourseIdAndUserIdOrderByCreatedAtDesc(courseId, userId);
        documentIndexer.prepareDeletion(units.stream().map(LearningUnit::getId).toList());
        courseRepository.delete(course);
    }

    @Transactional
    public void deleteUnit(UUID userId, UUID courseId, UUID unitId) {
        LearningUnit unit = requireUnit(userId, courseId, unitId);
        documentIndexer.prepareDeletion(List.of(unitId));
        unitRepository.delete(unit);
    }

    @Transactional
    public LearningUnitResponse upload(
            UUID userId,
            String courseName,
            String unitName,
            MultipartFile file) {
        String cleanCourseName = normalizeName(courseName, "Course name");
        String cleanUnitName = normalizeName(unitName, "Unit name");
        String fileName = safeFileName(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new BadRequestException("Choose a non-empty PDF or PowerPoint file");
        }
        if (file.getSize() > maxFileBytes) {
            throw new BadRequestException("File exceeds the 25 MB upload limit");
        }
        String lowerName = fileName.toLowerCase(java.util.Locale.ROOT);
        if (SUPPORTED_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            throw new BadRequestException("Only PDF, PPT, and PPTX files are supported");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the uploaded file");
        }

        LearningCourse course = courseRepository
                .findFirstByUserIdAndNameIgnoreCase(userId, cleanCourseName)
                .orElseGet(() -> {
                    LearningCourse created = new LearningCourse();
                    created.setUserId(userId);
                    created.setName(cleanCourseName);
                    return courseRepository.save(created);
                });
        course.setUpdatedAt(java.time.Instant.now());
        courseRepository.save(course);

        LearningUnit unit = new LearningUnit();
        unit.setUserId(userId);
        unit.setCourseId(course.getId());
        unit.setName(cleanUnitName);
        unit.setSourceFileName(fileName);
        unit.setMediaType(file.getContentType());
        unit.setIndexStatus(IndexStatus.PENDING);
        unit = unitRepository.save(unit);
        UUID unitId = unit.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentIndexer.indexAsync(unitId, fileBytes);
                }
            });
        } else {
            documentIndexer.indexAsync(unitId, fileBytes);
        }
        return toUnitResponse(unit);
    }

    @Transactional(readOnly = true)
    public LearningCourse requireCourse(UUID userId, UUID courseId) {
        return courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException("Course not found"));
    }

    @Transactional(readOnly = true)
    public LearningUnit requireUnit(UUID userId, UUID courseId, UUID unitId) {
        return unitRepository.findByIdAndCourseIdAndUserId(unitId, courseId, userId)
                .orElseThrow(() -> new NotFoundException("Unit not found"));
    }

    @Transactional(readOnly = true)
    public LearningUnitResponse unitResponse(UUID userId, UUID courseId, UUID unitId) {
        return toUnitResponse(requireUnit(userId, courseId, unitId));
    }

    private LearningCourseResponse toCourseResponse(LearningCourse course, UUID userId) {
        List<LearningUnit> units = unitRepository
                .findByCourseIdAndUserIdOrderByCreatedAtDesc(course.getId(), userId);
        long readyCount = units.stream()
                .filter(unit -> unit.getIndexStatus() == IndexStatus.READY)
                .count();
        long chunks = units.stream().mapToLong(LearningUnit::getChunkCount).sum();
        return new LearningCourseResponse(
                course.getId(), course.getName(), units.size(), readyCount, chunks, course.getUpdatedAt());
    }

    private LearningUnitResponse toUnitResponse(LearningUnit unit) {
        return new LearningUnitResponse(
                unit.getId(),
                unit.getCourseId(),
                unit.getName(),
                unit.getSourceFileName(),
                unit.getMediaType(),
                unit.getIndexStatus(),
                unit.getChunkCount(),
                unit.getErrorMessage(),
                unit.getCreatedAt());
    }

    private static String normalizeName(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new BadRequestException(label + " must be between 1 and 200 characters");
        }
        return normalized;
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Uploaded file must have a name");
        }
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || name.length() > 255) {
            throw new BadRequestException("Invalid file name");
        }
        return name;
    }
}
