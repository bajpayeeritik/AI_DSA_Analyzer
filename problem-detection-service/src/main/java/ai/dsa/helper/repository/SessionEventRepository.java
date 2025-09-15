package ai.dsa.helper.repository;

import ai.dsa.helper.model.SessionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {

    // Basic queries
    List<SessionEvent> findByUserIdOrderByCreatedAtDesc(String userId);

    List<SessionEvent> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    Optional<SessionEvent> findByUserIdAndProblemIdAndSessionId(String userId, String problemId, String sessionId);

    // Paginated queries for better performance
    Page<SessionEvent> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<SessionEvent> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    // Chrome Extension specific queries
    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId AND se.eventType IN ('CODE_RUN', 'CODE_SUBMIT') ORDER BY se.createdAt DESC")
    List<SessionEvent> findUserCodingActivity(@Param("userId") String userId);

    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId AND se.eventType = :eventType ORDER BY se.createdAt DESC")
    List<SessionEvent> findByUserIdAndEventType(@Param("userId") String userId, @Param("eventType") String eventType);

    // Analytics queries
    @Query("SELECT COUNT(se) FROM SessionEvent se WHERE se.userId = :userId AND se.eventType = 'CODE_SUBMIT'")
    Long countSubmissionsByUser(@Param("userId") String userId);

    @Query("SELECT COUNT(se) FROM SessionEvent se WHERE se.userId = :userId AND se.eventType = 'CODE_RUN'")
    Long countRunsByUser(@Param("userId") String userId);

    @Query("SELECT COUNT(DISTINCT se.problemId) FROM SessionEvent se WHERE se.userId = :userId")
    Long countUniqueProblemsAttempted(@Param("userId") String userId);

    @Query("SELECT DISTINCT se.language FROM SessionEvent se WHERE se.userId = :userId AND se.language IS NOT NULL")
    List<String> findLanguagesUsedByUser(@Param("userId") String userId);

    // Date range queries
    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId AND se.createdAt >= :fromDate ORDER BY se.createdAt DESC")
    List<SessionEvent> findUserEventsFromDate(@Param("userId") String userId, @Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId AND se.createdAt BETWEEN :fromDate AND :toDate ORDER BY se.createdAt DESC")
    List<SessionEvent> findUserEventsBetweenDates(@Param("userId") String userId,
                                                  @Param("fromDate") LocalDateTime fromDate,
                                                  @Param("toDate") LocalDateTime toDate);

    @Query("SELECT COUNT(se) FROM SessionEvent se WHERE se.userId = :userId AND se.eventType = :eventType AND se.createdAt >= :fromDate")
    Long countEventsByUserAndTypeFromDate(@Param("userId") String userId,
                                          @Param("eventType") String eventType,
                                          @Param("fromDate") LocalDateTime fromDate);

    // Problem-specific queries
    @Query("SELECT se FROM SessionEvent se WHERE se.problemId = :problemId ORDER BY se.createdAt DESC")
    List<SessionEvent> findByProblemId(@Param("problemId") String problemId);

    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId AND se.problemId = :problemId ORDER BY se.createdAt DESC")
    List<SessionEvent> findByUserIdAndProblemId(@Param("userId") String userId, @Param("problemId") String problemId);

    // Platform and language analytics
    @Query("SELECT se.language, COUNT(se) FROM SessionEvent se WHERE se.userId = :userId AND se.language IS NOT NULL GROUP BY se.language")
    List<Object[]> findLanguageDistributionByUser(@Param("userId") String userId);

    @Query("SELECT se.platform, COUNT(se) FROM SessionEvent se WHERE se.userId = :userId AND se.platform IS NOT NULL GROUP BY se.platform")
    List<Object[]> findPlatformDistributionByUser(@Param("userId") String userId);

    // Recent activity
    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId AND se.createdAt >= :cutoffTime ORDER BY se.createdAt DESC")
    List<SessionEvent> findRecentActivity(@Param("userId") String userId, @Param("cutoffTime") LocalDateTime cutoffTime);

    // Session-based queries
    @Query("SELECT DISTINCT se.sessionId FROM SessionEvent se WHERE se.userId = :userId AND se.createdAt >= :fromDate")
    List<String> findActiveSessionsByUser(@Param("userId") String userId, @Param("fromDate") LocalDateTime fromDate);

    // Extension-specific queries
    @Query("SELECT se FROM SessionEvent se WHERE se.extensionVersion = :version ORDER BY se.createdAt DESC")
    List<SessionEvent> findByExtensionVersion(@Param("version") String version);

    @Query("SELECT se FROM SessionEvent se WHERE se.leetcodeUsername = :username ORDER BY se.createdAt DESC")
    List<SessionEvent> findByLeetcodeUsername(@Param("username") String username);

    // Performance queries for large datasets
    @Query(value = "SELECT * FROM coding_session_events WHERE user_id = ?1 AND created_at >= ?2 ORDER BY created_at DESC LIMIT ?3", nativeQuery = true)
    List<SessionEvent> findRecentEventsByUserWithLimit(String userId, LocalDateTime fromDate, int limit);
}
