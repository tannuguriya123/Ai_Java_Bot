package devPilot.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import devPilot.backend.dto.LearningCourseDetailResponse;
import devPilot.backend.dto.LearningCourseResponse;
import devPilot.backend.dto.LearningUnitResponse;
import devPilot.backend.security.CurrentUser;
import devPilot.backend.service.LearningService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final CurrentUser currentUser;
    private final LearningService learningService;

    @GetMapping("/courses")
    public List<LearningCourseResponse> listCourses() {
        return learningService.listCourses(currentUser.require().getId());
    }

    @GetMapping("/courses/{courseId}")
    public LearningCourseDetailResponse getCourse(@PathVariable UUID courseId) {
        return learningService.getCourse(currentUser.require().getId(), courseId);
    }

    @DeleteMapping("/courses/{courseId}")
    public ResponseEntity<Void> deleteCourse(@PathVariable UUID courseId) {
        learningService.deleteCourse(currentUser.require().getId(), courseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/courses/{courseId}/units/{unitId}")
    public ResponseEntity<Void> deleteUnit(
            @PathVariable UUID courseId,
            @PathVariable UUID unitId) {
        learningService.deleteUnit(currentUser.require().getId(), courseId, unitId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/units", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LearningUnitResponse> upload(
            @RequestParam String courseName,
            @RequestParam String unitName,
            @RequestParam MultipartFile file) {
        LearningUnitResponse unit = learningService.upload(
                currentUser.require().getId(), courseName, unitName, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(unit);
    }
}
