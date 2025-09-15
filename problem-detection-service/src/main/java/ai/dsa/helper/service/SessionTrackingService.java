package ai.dsa.helper.service;

import ai.dsa.helper.model.SessionEvent;
import ai.dsa.helper.repository.SessionEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionTrackingService {

    private final SessionEventRepository sessionEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.kafka.topics.sessionStarted}")
    private String sessionStartedTopic;

    @Value("${app.kafka.topics.sessionProgress}")
    private String sessionProgressTopic;

    @Value("${app.kafka.topics.sessionSubmitted}")
    private String sessionSubmittedTopic;

    @Value("${app.kafka.topics.sessionEnded}")
    private String sessionEndedTopic;

    // Constants for event types
    private static final String CODE_RUN = "CODE_RUN";
    private static final String CODE_SUBMIT = "CODE_SUBMIT";
    private static final String PROBLEM_SESSION_STARTED = "ProblemSessionStarted";
    private static final String PROBLEM_PROGRESS = "ProblemProgress";
    private static final String PROBLEM_SUBMITTED = "ProblemSubmitted";
    private static final String PROBLEM_SESSION_ENDED = "ProblemSessionEnded";

    @Transactional
    public Long processSessionEvent(Map<String, Object> eventPayload) throws JsonProcessingException {
        String eventType = (String) eventPayload.get("eventType");
        Map<String, Object> data = (Map<String, Object>) eventPayload.get("data");

        log.info("🔄 Processing {} event for user: {}", eventType, data.get("userId"));

        // Extract code separately to avoid caching large blobs
        String sourceCode = (String) data.get("code");

        // Create metadata without the large code blob
        Map<String, Object> metadata = new HashMap<>(data);
        metadata.remove("code"); // Remove large code from JSON metadata

        // Extract problemId from URL if not provided
        String problemId = (String) data.get("problemId");
        if (problemId == null) {
            problemId = extractProblemIdFromUrl((String) data.get("problemUrl"));
        }

        // Build SessionEvent with Builder pattern
        SessionEvent event = SessionEvent.builder()
                .eventType(eventType)
                .userId((String) data.get("userId"))
                .problemId(problemId)
                .platform((String) data.get("platform"))
                .sessionId((String) data.get("sessionId"))
                .sourceCode(sourceCode)
                .language((String) data.get("language"))
                .problemTitle((String) data.get("problemTitle"))
                .problemUrl((String) data.get("problemUrl"))
                .leetcodeUsername((String) data.get("leetcodeUsername"))
                .extensionVersion((String) data.get("extensionVersion"))
                .eventData(objectMapper.writeValueAsString(metadata))
                .build();

        // Save to MySQL
        SessionEvent savedEvent = sessionEventRepository.save(event);

        // Cache metadata only (not the full code)
        cacheSessionMetadata(savedEvent, metadata);

        // Publish to Kafka with full data (for real-time processing)
        publishToKafka(eventType, data, savedEvent.getSessionId());

        log.info("✅ Processed {} event for user {} on problem {} - Event ID: {}",
                eventType, event.getUserId(), event.getProblemTitle(), savedEvent.getId());

        return savedEvent.getId();
    }

    private void cacheSessionMetadata(SessionEvent event, Map<String, Object> metadata) {
        try {
            String cacheKey = "session:" + event.getUserId() + ":" + event.getProblemId();

            Map<String, Object> sessionCache = new HashMap<>();
            sessionCache.put("eventId", event.getId());
            sessionCache.put("eventType", event.getEventType());
            sessionCache.put("sessionId", event.getSessionId());
            sessionCache.put("lastActivity", System.currentTimeMillis());
            sessionCache.put("language", event.getLanguage());
            sessionCache.put("problemTitle", event.getProblemTitle());
            sessionCache.put("problemUrl", event.getProblemUrl());
            sessionCache.put("leetcodeUsername", event.getLeetcodeUsername());
            sessionCache.put("hasCode", event.getSourceCode() != null);
            sessionCache.put("codeLength", event.getSourceCode() != null ? event.getSourceCode().length() : 0);
            sessionCache.put("platform", event.getPlatform());

            // Cache for 2 hours
            redisTemplate.opsForValue().set(cacheKey, sessionCache, Duration.ofHours(2));
            log.debug("📦 Cached session metadata for {} (excluding code)", cacheKey);

        } catch (Exception e) {
            log.warn("⚠️ Failed to cache session metadata: {}", e.getMessage());
        }
    }

    private void publishToKafka(String eventType, Map<String, Object> data, String sessionId) {
        try {
            String topic = getTopicForEvent(eventType);

            Map<String, Object> message = new HashMap<>();
            message.put("eventType", eventType);
            message.put("sessionId", sessionId);
            message.put("data", data);
            message.put("timestamp", System.currentTimeMillis());
            message.put("publishedAt", LocalDateTime.now().toString());

            kafkaTemplate.send(topic, sessionId, message); // Use sessionId as key for partitioning
            log.info("📤 Published {} to topic: {} with key: {}", eventType, topic, sessionId);

        } catch (Exception e) {
            log.error("❌ Failed to publish to Kafka: {}", e.getMessage(), e);
        }
    }

    private String getTopicForEvent(String eventType) {
        return switch (eventType) {
            case PROBLEM_SESSION_STARTED -> sessionStartedTopic;
            case PROBLEM_PROGRESS -> sessionProgressTopic;
            case PROBLEM_SUBMITTED -> sessionSubmittedTopic;
            case PROBLEM_SESSION_ENDED -> sessionEndedTopic;
            case CODE_RUN -> sessionProgressTopic;
            case CODE_SUBMIT -> sessionSubmittedTopic;
            default -> sessionProgressTopic;
        };
    }

    public List<SessionEvent> getUserEvents(String userId) {
        log.debug("📊 Retrieving events for user: {}", userId);
        return sessionEventRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Map<String, Object> getActiveSession(String userId, String problemId) {
        String cacheKey = "session:" + userId + ":" + problemId;
        Map<String, Object> session = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);

        log.debug("🔍 Retrieved active session for {}: {}", cacheKey, session != null ? "found" : "not found");
        return session;
    }

    // NEW: Get session with code (for when you actually need the code)
    public SessionEvent getSessionWithCode(Long eventId) {
        log.debug("📝 Retrieving full session data including code for event: {}", eventId);
        return sessionEventRepository.findById(eventId).orElse(null);
    }

    // NEW: Get user's coding activity statistics
    public Map<String, Object> getUserCodingStats(String userId) {
        List<SessionEvent> events = getUserEvents(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", events.size());
        stats.put("runCount", events.stream().filter(e -> CODE_RUN.equals(e.getEventType())).count());
        stats.put("submitCount", events.stream().filter(e -> CODE_SUBMIT.equals(e.getEventType())).count());
        stats.put("uniqueProblems", events.stream().map(SessionEvent::getProblemId).distinct().count());
        stats.put("languagesUsed", events.stream().map(SessionEvent::getLanguage).distinct().toList());

        log.info("📈 Generated stats for user {}: {}", userId, stats);
        return stats;
    }

    // Helper method to extract problem ID from LeetCode URL
    private String extractProblemIdFromUrl(String problemUrl) {
        if (problemUrl == null) return null;

        try {
            // Extract from URLs like: https://leetcode.com/problems/two-sum/
            String[] parts = problemUrl.split("/problems/");
            if (parts.length > 1) {
                String problemSlug = parts[1].split("/")[0];
                return problemSlug.replaceAll("[^a-zA-Z0-9-]", ""); // Clean up
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not extract problem ID from URL: {}", problemUrl);
        }

        return "unknown-problem";
    }

}
