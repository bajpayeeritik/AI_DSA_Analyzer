package ai.dsa.analysis.controller;

import ai.dsa.analysis.repository.CodingAnalysisRepository;
import ai.dsa.analysis.repository.SessionEventRepository;
import ai.dsa.analysis.service.PerplexityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestRepositoryController {
    @Autowired
    private PerplexityService perplexityService;
    private final CodingAnalysisRepository analysisRepository;
    private final SessionEventRepository sessionEventRepository;

    @GetMapping("/db-connection")
    public Map<String, Object> testDatabaseConnection() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Test analysis repository
            long analysisCount = analysisRepository.count();
            result.put("analysisTableCount", analysisCount);

            // Test session repository
            long sessionCount = sessionEventRepository.count();
            result.put("sessionTableCount", sessionCount);

            result.put("status", "success");
            result.put("message", "Database connection working!");
            result.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Database connection failed");
            result.put("error", e.getMessage());
            log.error("Database test failed: {}", e.getMessage());
        }

        return result;
    }

    @GetMapping("/user-data/{userId}")
    public Map<String, Object> testUserData(@PathVariable String userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Test user session data
            LocalDateTime fromDate = LocalDateTime.now().minusDays(30);
            long userSessions = sessionEventRepository.countEventsByUserAndType(userId, "CODE_RUN", fromDate);

            result.put("userId", userId);
            result.put("userRunCount", userSessions);
            result.put("status", "success");
            result.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
            log.error("User data test failed: {}", e.getMessage());
        }

        return result;
    }


    @GetMapping("/perplexity/health")
    public ResponseEntity<Map<String, Object>> testPerplexityHealth() {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean isHealthy = perplexityService.isApiHealthy();
            result.put("status", isHealthy ? "healthy" : "unhealthy");
            result.put("message", isHealthy ? "Perplexity API is working" : "Perplexity API is down");
            result.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to check Perplexity API health");
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

}
