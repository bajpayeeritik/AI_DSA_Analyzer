package ai.dsa.helper.controller;

import ai.dsa.helper.model.SessionEvent;
import ai.dsa.helper.service.SessionTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/problems")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
@Validated
public class SessionController {

    private final SessionTrackingService sessionTrackingService;
    private final RedisTemplate<String, Object> redisTemplate; // ✅ Inject RedisTemplate
    // Health and test endpoints
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        log.info("🧪 Test endpoint called!");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Spring Boot is working! ✅");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "AI DSA Helper - Problem Detection Service");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "AI DSA Helper - Problem Detection Service");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("version", "v1.0.0");
        return ResponseEntity.ok(status);
    }

    // Chrome Extension endpoint
    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveLeetCodeActivity(@RequestBody  Map<String, Object> extensionPayload) {
        log.info("🔥 CHROME EXTENSION REQUEST RECEIVED from IP: {}", getClientIpAddress());
        log.debug("📦 Payload: {}", extensionPayload);

        try {
            // Extract and validate data
            String userId = extractAndValidateUserId(extensionPayload);
            String leetcodeUsername = (String) extensionPayload.get("leetcodeUsername");
            Map<String, Object> problemData = extractProblemData(extensionPayload);
            Map<String, Object> metadata = (Map<String, Object>) extensionPayload.get("metadata");

            log.info("📊 Processing activity for user: {} ({})", userId, leetcodeUsername);
            log.info("🎯 Problem: {} | Action: {}", problemData.get("title"), problemData.get("action"));

            // Transform and process
            Map<String, Object> eventPayload = transformExtensionToEvent(userId, leetcodeUsername, problemData, metadata);
            Long eventId = sessionTrackingService.processSessionEvent(eventPayload);

            // Build success response
            Map<String, Object> response = buildSuccessResponse(eventId, userId, problemData);

            log.info("✅ Activity processed successfully - Event ID: {}", eventId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid request data: {}", e.getMessage());
            return ResponseEntity.badRequest().body(buildErrorResponse("Invalid request data", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error processing LeetCode activity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse("Failed to process LeetCode activity", e.getMessage()));
        }
    }

    // Legacy event endpoint (backward compatibility)
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> receiveSessionEvent(@RequestBody Map<String, Object> eventPayload) {
        log.info("🔥 LEGACY EVENT REQUEST: {}", eventPayload.get("eventType"));

        try {
            Long eventId = sessionTrackingService.processSessionEvent(eventPayload);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Event processed successfully");
            response.put("eventId", eventId);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error processing event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse("Failed to process event", e.getMessage()));
        }
    }

    // User data endpoints
    @GetMapping("/events/{userId}")
    public ResponseEntity<List<SessionEvent>> getUserEvents(@PathVariable String userId) {
        log.info("📊 Retrieving events for user: {}", userId);
        List<SessionEvent> events = sessionTrackingService.getUserEvents(userId);
        return ResponseEntity.ok(events);
    }


    // Session management endpoints
    @GetMapping("/session/{userId}/{problemId}")
    public ResponseEntity<Map<String, Object>> getActiveSession(
            @PathVariable String userId,
            @PathVariable String problemId) {

        log.info("🔍 Retrieving active session for user: {} problem: {}", userId, problemId);
        Map<String, Object> session = sessionTrackingService.getActiveSession(userId, problemId);
        return ResponseEntity.ok(session != null ? session : new HashMap<>());
    }

    @GetMapping("/session/{eventId}/code")
    public ResponseEntity<Map<String, Object>> getSessionWithCode(@PathVariable Long eventId) {
        log.info("📝 Retrieving session with code for event: {}", eventId);

        SessionEvent event = sessionTrackingService.getSessionWithCode(eventId);
        if (event == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("eventId", event.getId());
        response.put("problemTitle", event.getProblemTitle());
        response.put("language", event.getLanguage());
        response.put("sourceCode", event.getSourceCode());
        response.put("createdAt", event.getCreatedAt());

        return ResponseEntity.ok(response);
    }

    // Analytics endpoints
    @GetMapping("/analytics/{userId}")
    public ResponseEntity<Map<String, Object>> getUserCodingStats(@PathVariable String userId) {
        log.info("📈 Generating coding stats for user: {}", userId);
        Map<String, Object> stats = sessionTrackingService.getUserCodingStats(userId);
        return ResponseEntity.ok(stats);
    }




    // Helper methods
    private String extractAndValidateUserId(Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        if (userId == null || userId.trim().isEmpty()) {
            userId = "user123"; // Default fallback
            log.info("🔧 Using default userId: {}", userId);
        }
        return userId;
    }

    private Map<String, Object> extractProblemData(Map<String, Object> payload) {
        Map<String, Object> problemData = (Map<String, Object>) payload.get("problemData");

        if (problemData == null) {
            log.warn("⚠️ Problem data is null, extracting from flat structure");
            problemData = new HashMap<>();
            problemData.put("title", payload.get("problemTitle"));
            problemData.put("url", payload.get("problemUrl"));
            problemData.put("action", payload.get("action"));
            problemData.put("timestamp", payload.get("timestamp"));
            problemData.put("language", payload.get("language"));
            problemData.put("code", payload.get("code"));
            problemData.put("sessionId", payload.get("sessionId"));
        }

        return problemData;
    }

    private Map<String, Object> transformExtensionToEvent(String userId, String leetcodeUsername,
                                                          Map<String, Object> problemData,
                                                          Map<String, Object> metadata) {
        String action = (String) problemData.get("action");
        String eventType = mapActionToEventType(action);

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("eventType", eventType);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("userId", userId);
        eventData.put("leetcodeUsername", leetcodeUsername);
        eventData.put("problemTitle", problemData.get("title"));
        eventData.put("problemUrl", problemData.get("url"));
        eventData.put("language", problemData.get("language"));
        eventData.put("code", problemData.get("code"));
        eventData.put("sessionId", problemData.get("sessionId"));
        eventData.put("timestamp", problemData.get("timestamp"));
        eventData.put("platform", "leetcode");
        eventData.put("source", "chrome_extension");
        eventData.put("extensionVersion", metadata != null ? metadata.get("extensionVersion") : "unknown");

        eventPayload.put("data", eventData);
        return eventPayload;
    }

    private String mapActionToEventType(String action) {
        if (action == null) return "CODE_ACTIVITY";

        return switch (action.toLowerCase()) {
            case "run" -> "CODE_RUN";
            case "submit" -> "CODE_SUBMIT";
            default -> "CODE_ACTIVITY";
        };
    }

    private Map<String, Object> buildSuccessResponse(Long eventId, String userId, Map<String, Object> problemData) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "LeetCode activity processed successfully");
        response.put("eventId", eventId);
        response.put("userId", userId);
        response.put("problemTitle", problemData.get("title"));
        response.put("action", problemData.get("action"));
        response.put("timestamp", LocalDateTime.now());
        response.put("processingTime", System.currentTimeMillis());
        return response;
    }

    private Map<String, Object> buildErrorResponse(String message, String error) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", message);
        errorResponse.put("error", error);
        errorResponse.put("timestamp", LocalDateTime.now());
        return errorResponse;
    }

    private String getClientIpAddress() {
        // Implementation to get client IP from request headers
        return "unknown"; // Placeholder
    }
    @GetMapping("/redis/test")
    public ResponseEntity<Map<String, Object>> testRedis() {
        try {
            // Test Redis operations for Chrome extension
            String testKey = "chrome_extension_test:" + System.currentTimeMillis();
            String testValue = "Redis working for LeetCode tracker at " + LocalDateTime.now();

            // Store with 5-minute expiration
            redisTemplate.opsForValue().set(testKey, testValue, Duration.ofMinutes(5));

            // Retrieve value
            String retrievedValue = (String) redisTemplate.opsForValue().get(testKey);

            // Test session-like data structure
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("userId", "user123");
            sessionData.put("problemTitle", "Two Sum");
            sessionData.put("language", "java");
            sessionData.put("lastActivity", System.currentTimeMillis());

            String sessionKey = "session:user123:two-sum";
            redisTemplate.opsForValue().set(sessionKey, sessionData, Duration.ofHours(2));

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "✅ Redis integration successful!");
            response.put("redisPort", 6380);
            response.put("testKey", testKey);
            response.put("testValue", testValue);
            response.put("retrievedValue", retrievedValue);
            response.put("valuesMatch", testValue.equals(retrievedValue));
            response.put("sessionKey", sessionKey);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "❌ Redis connection failed");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

}
