package devPilot.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.LearningCourse;
import devPilot.backend.entity.LearningUnit;
import devPilot.backend.exception.BadRequestException;
import devPilot.backend.repository.LearningCourseRepository;
import devPilot.backend.repository.LearningUnitRepository;
import devPilot.backend.service.indexing.LearningDocumentIndexer;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class LearningServiceTests {

    private LearningCourseRepository courseRepository;
    private LearningUnitRepository unitRepository;
    private LearningDocumentIndexer documentIndexer;
    private LearningService learningService;

    @BeforeEach
    void setUp() {
        courseRepository = Mockito.mock(LearningCourseRepository.class);
        unitRepository = Mockito.mock(LearningUnitRepository.class);
        documentIndexer = Mockito.mock(LearningDocumentIndexer.class);
        learningService = new LearningService(courseRepository, unitRepository, documentIndexer);
        ReflectionTestUtils.setField(learningService, "maxFileBytes", 25L * 1024 * 1024);
    }

    @Test
    void uploadRejectsUnsupportedFileExtension() {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] { 1 });

        assertThrows(
                BadRequestException.class,
                () -> learningService.upload(UUID.randomUUID(), "Calculus", "Unit 1", file));
        verify(courseRepository, never()).save(any());
        verify(unitRepository, never()).save(any());
    }

    @Test
    void uploadRejectsFilesAboveConfiguredLimit() {
        ReflectionTestUtils.setField(learningService, "maxFileBytes", 2L);
        var file = new MockMultipartFile(
                "file", "unit.pdf", "application/pdf", new byte[] { 1, 2, 3 });

        assertThrows(
                BadRequestException.class,
                () -> learningService.upload(UUID.randomUUID(), "Calculus", "Unit 1", file));
        verify(courseRepository, never()).save(any());
    }

    @Test
    void uploadCreatesUnitAndSchedulesIndexingForCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        var course = new LearningCourse();
        course.setId(courseId);
        course.setUserId(userId);
        course.setName("Calculus");
        when(courseRepository.findFirstByUserIdAndNameIgnoreCase(userId, "Calculus"))
                .thenReturn(Optional.of(course));
        when(courseRepository.save(any(LearningCourse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.save(any(LearningUnit.class))).thenAnswer(invocation -> {
            LearningUnit saved = invocation.getArgument(0);
            saved.setId(unitId);
            return saved;
        });
        byte[] bytes = new byte[] { 1, 2, 3 };
        var file = new MockMultipartFile(
                "file", "unit-1.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                bytes);

        var response = learningService.upload(userId, " Calculus ", " Unit 1 ", file);

        assertEquals(courseId, response.courseId());
        assertEquals(unitId, response.id());
        assertEquals(IndexStatus.PENDING, response.indexStatus());
        assertEquals("Unit 1", response.name());
        ArgumentCaptor<byte[]> indexedBytes = ArgumentCaptor.forClass(byte[].class);
        verify(documentIndexer).indexAsync(org.mockito.ArgumentMatchers.eq(unitId), indexedBytes.capture());
        assertEquals(bytes.length, indexedBytes.getValue().length);
    }

    @Test
    void uploadStartsIndexingOnlyAfterTransactionCommits() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        var course = new LearningCourse();
        course.setId(courseId);
        course.setUserId(userId);
        course.setName("Calculus");
        when(courseRepository.findFirstByUserIdAndNameIgnoreCase(userId, "Calculus"))
                .thenReturn(Optional.of(course));
        when(courseRepository.save(any(LearningCourse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.save(any(LearningUnit.class))).thenAnswer(invocation -> {
            LearningUnit saved = invocation.getArgument(0);
            saved.setId(unitId);
            return saved;
        });
        var file = new MockMultipartFile(
                "file", "unit-2.pdf", "application/pdf", new byte[] { 1, 2, 3 });
        TransactionSynchronizationManager.initSynchronization();

        try {
            learningService.upload(userId, "Calculus", "Unit 2", file);

            verify(documentIndexer, never()).indexAsync(any(), any());
            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(documentIndexer).indexAsync(org.mockito.ArgumentMatchers.eq(unitId), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadsWithSameCourseNameReuseOneCourse() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        var course = new LearningCourse();
        course.setId(courseId);
        course.setUserId(userId);
        course.setName("Calculus");
        when(courseRepository.findFirstByUserIdAndNameIgnoreCase(userId, "Calculus"))
                .thenReturn(Optional.of(course));
        when(courseRepository.save(any(LearningCourse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(unitRepository.save(any(LearningUnit.class))).thenAnswer(invocation -> {
            LearningUnit saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var first = learningService.upload(
                userId, "Calculus", "Lecture 1",
                new MockMultipartFile("file", "lecture-1.pdf", "application/pdf", new byte[] { 1 }));
        var second = learningService.upload(
                userId, "Calculus", "Lecture 2",
                new MockMultipartFile("file", "lecture-2.pdf", "application/pdf", new byte[] { 2 }));

        assertEquals(courseId, first.courseId());
        assertEquals(courseId, second.courseId());
        verify(courseRepository, times(2))
                .findFirstByUserIdAndNameIgnoreCase(userId, "Calculus");
        verify(unitRepository, times(2)).save(any(LearningUnit.class));
    }

    @Test
    void deleteCourseRemovesOwnedCourseAndSchedulesAllUnitVectorsForCleanup() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        LearningCourse course = new LearningCourse();
        course.setId(courseId);
        course.setUserId(userId);

        LearningUnit firstUnit = new LearningUnit();
        firstUnit.setId(UUID.randomUUID());
        LearningUnit secondUnit = new LearningUnit();
        secondUnit.setId(UUID.randomUUID());
        when(courseRepository.findByIdAndUserId(courseId, userId)).thenReturn(Optional.of(course));
        when(unitRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(courseId, userId))
                .thenReturn(List.of(firstUnit, secondUnit));

        learningService.deleteCourse(userId, courseId);

        verify(documentIndexer).prepareDeletion(List.of(firstUnit.getId(), secondUnit.getId()));
        verify(courseRepository).delete(course);
        verify(unitRepository).findByCourseIdAndUserIdOrderByCreatedAtDesc(courseId, userId);
    }

    @Test
    void deleteUnitRequiresUserAndCourseOwnershipBeforeVectorCleanup() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        LearningUnit unit = new LearningUnit();
        unit.setId(unitId);
        when(unitRepository.findByIdAndCourseIdAndUserId(unitId, courseId, userId))
                .thenReturn(Optional.of(unit));

        learningService.deleteUnit(userId, courseId, unitId);

        verify(documentIndexer).prepareDeletion(List.of(unitId));
        verify(unitRepository).delete(unit);
        verify(courseRepository, never()).delete(any(LearningCourse.class));
    }

    @Test
    void deleteCourseRejectsCourseOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(courseRepository.findByIdAndUserId(courseId, userId)).thenReturn(Optional.empty());

        assertThrows(
                devPilot.backend.exception.NotFoundException.class,
                () -> learningService.deleteCourse(userId, courseId));

        verifyNoInteractions(documentIndexer);
        verify(courseRepository, never()).delete(any(LearningCourse.class));
    }
}
