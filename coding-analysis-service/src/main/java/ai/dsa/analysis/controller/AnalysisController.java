package ai.dsa.analysis.controller;

import ai.dsa.analysis.dto.AnalysisResult;
import ai.dsa.analysis.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analysis")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final AnalysisService analysisService;
//    http://localhost:8082/api/v1/users/
    @PostMapping("/analyze/{userId}")
    public ResponseEntity<Map<String, Object>> analyzeUserPatterns(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") int periodDays) {

        log.info("🔍 Starting analysis for user: {} (period: {} days)", userId, periodDays);

        try {
            AnalysisResult result = analysisService.analyzeUserCodingPatterns(userId, periodDays);

            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Analysis completed successfully");
                response.put("data", Map.of(
                        "analysisId", result.getAnalysisId(),
                        "summary", result.getSummary(),
                        "recommendations", result.getRecommendations(),
                        "scores", Map.of(
                                "initialApproachRating", result.getInitialApproachRating(),
                                "codeQualityScore", result.getCodeQualityScore()
                        )
                ));
                response.put("timestamp", LocalDateTime.now());

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", result.getErrorMessage(),
                        "timestamp", LocalDateTime.now()
                ));
            }

        } catch (Exception e) {
            log.error("❌ Error analyzing user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Analysis failed: " + e.getMessage(),
                    "timestamp", LocalDateTime.now()
            ));
        }
    }

    @GetMapping("/results/{userId}")
    public ResponseEntity<Map<String, Object>> getUserAnalysisHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("📊 Retrieving analysis history for user: {}", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("userId", userId);
        response.put("message", "Analysis history retrieved successfully");
        response.put("data", Map.of()); // Will be implemented based on your repository
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "AI Analysis Controller");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }
}
