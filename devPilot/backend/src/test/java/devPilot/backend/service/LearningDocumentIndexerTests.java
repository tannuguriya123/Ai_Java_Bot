package devPilot.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;

import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.LearningUnit;
import devPilot.backend.repository.LearningCourseRepository;
import devPilot.backend.repository.LearningUnitRepository;
import devPilot.backend.service.indexing.LearningDocumentIndexer;

class LearningDocumentIndexerTests {

    @Test
    void malformedDocumentIsMarkedFailed() {
        LearningUnitRepository unitRepository = mock(LearningUnitRepository.class);
        LearningCourseRepository courseRepository = mock(LearningCourseRepository.class);
        VectorStore vectorStore = mock(VectorStore.class);
        UUID unitId = UUID.randomUUID();
        LearningUnit unit = new LearningUnit();
        unit.setId(unitId);
        unit.setCourseId(UUID.randomUUID());
        unit.setIndexStatus(IndexStatus.PENDING);
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(unitRepository.save(any(LearningUnit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LearningDocumentIndexer indexer = new LearningDocumentIndexer(
                unitRepository, courseRepository, vectorStore);
        indexer.indexAsync(unitId, new byte[] { 0, 1, 2, 3 });

        ArgumentCaptor<LearningUnit> saved = ArgumentCaptor.forClass(LearningUnit.class);
        verify(unitRepository, times(2)).save(saved.capture());
        assertEquals(IndexStatus.FAILED, saved.getAllValues().getLast().getIndexStatus());
    }
}
