package devPilot.backend.service.indexing;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.LearningUnit;
import devPilot.backend.repository.LearningCourseRepository;
import devPilot.backend.repository.LearningUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningDocumentIndexer {

    private static final int MAX_EXTRACTED_CHARACTERS = 2_000_000;
    private static final int VECTOR_BATCH_SIZE = 32;
    private static final int UNIT_LOCK_COUNT = 64;
    private static final Lock[] UNIT_LOCKS = createUnitLocks();

    private final LearningUnitRepository unitRepository;
    private final LearningCourseRepository courseRepository;
    private final VectorStore vectorStore;

    @Value("${app.indexing.chunk-size:800}")
    private int chunkSize;

    @org.springframework.scheduling.annotation.Async("indexingExecutor")
    public void indexAsync(UUID unitId, byte[] fileBytes) {
        Lock lock = unitLock(unitId);
        lock.lock();
        try {
            index(unitId, fileBytes);
        } finally {
            lock.unlock();
        }
    }

    public void prepareDeletion(Collection<UUID> unitIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Unit deletion requires an active transaction");
        }
        List<UUID> ids = List.copyOf(unitIds);
        TreeMap<Integer, Lock> locks = new TreeMap<>();
        for (UUID unitId : ids) {
            int lockIndex = Math.floorMod(unitId.hashCode(), UNIT_LOCK_COUNT);
            locks.put(lockIndex, UNIT_LOCKS[lockIndex]);
        }
        List<Lock> acquiredLocks = new ArrayList<>(locks.values());
        for (Lock lock : acquiredLocks) {
            lock.lock();
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                RuntimeException cleanupFailure = null;
                for (UUID unitId : ids) {
                    try {
                        var filter = new FilterExpressionBuilder()
                                .eq("unitId", unitId.toString())
                                .build();
                        vectorStore.delete(filter);
                    } catch (RuntimeException ex) {
                        if (cleanupFailure == null) {
                            cleanupFailure = new IllegalStateException(
                                    "Course records were deleted, but some vector data could not be removed");
                        }
                        cleanupFailure.addSuppressed(ex);
                    }
                }
                if (cleanupFailure != null) {
                    throw cleanupFailure;
                }
            }

            @Override
            public void afterCompletion(int status) {
                for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                    acquiredLocks.get(i).unlock();
                }
            }
        });
    }

    private void index(UUID unitId, byte[] fileBytes) {
        LearningUnit unit = unitRepository.findById(unitId).orElse(null);
        if (unit == null) {
            return;
        }
        try {
            unit.setIndexStatus(IndexStatus.INDEXING);
            unit.setErrorMessage(null);
            unitRepository.save(unit);

            String extractedText = extractText(fileBytes);
            if (extractedText.isBlank()) {
                throw new IllegalArgumentException("No readable text was found in this file");
            }

            var filter = new FilterExpressionBuilder().eq("unitId", unitId.toString()).build();
            vectorStore.delete(filter);

            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(Math.max(50, chunkSize / 4))
                    .build();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("courseId", unit.getCourseId().toString());
            metadata.put("unitId", unitId.toString());
            metadata.put("filePath", unit.getSourceFileName());
            metadata.put("language", "document");
            metadata.put("courseName", courseRepository.findById(unit.getCourseId())
                    .map(course -> course.getName())
                    .orElse("Course material"));

            String content = "Course unit: " + unit.getName()
                    + "\nSource file: " + unit.getSourceFileName()
                    + "\n\n" + extractedText;
            List<Document> chunks = splitter.apply(List.of(
                    new Document(content, metadata)));
            List<Document> batch = new ArrayList<>(VECTOR_BATCH_SIZE);
            for (Document chunk : chunks) {
                batch.add(chunk);
                if (batch.size() == VECTOR_BATCH_SIZE) {
                    vectorStore.add(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                vectorStore.add(batch);
            }

            unit.setChunkCount(chunks.size());
            unit.setIndexStatus(IndexStatus.READY);
            unitRepository.save(unit);
        } catch (Exception ex) {
            log.error("Learning material indexing failed for unit {}", unitId, ex);
            unitRepository.findById(unitId).ifPresent(current -> {
                current.setIndexStatus(IndexStatus.FAILED);
                current.setErrorMessage(truncate(ex.getMessage(), 2000));
                unitRepository.save(current);
            });
        }
    }

    private static Lock unitLock(UUID unitId) {
        return UNIT_LOCKS[Math.floorMod(unitId.hashCode(), UNIT_LOCK_COUNT)];
    }

    private static Lock[] createUnitLocks() {
        Lock[] locks = new Lock[UNIT_LOCK_COUNT];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private String extractText(byte[] fileBytes) throws Exception {
        var parser = new AutoDetectParser();
        var handler = new BodyContentHandler(MAX_EXTRACTED_CHARACTERS);
        parser.parse(
                new ByteArrayInputStream(fileBytes),
                handler,
                new Metadata(),
                new ParseContext());
        return handler.toString().trim();
    }

    private static String truncate(String message, int maxLength) {
        String value = message == null || message.isBlank()
                ? "Could not index this file"
                : message;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
