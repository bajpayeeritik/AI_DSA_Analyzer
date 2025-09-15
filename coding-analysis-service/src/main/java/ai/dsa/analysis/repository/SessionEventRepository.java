package ai.dsa.analysis.repository;

import ai.dsa.analysis.model.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {

    // Find user events in date range
    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId " +
            "AND se.createdAt >= :fromDate ORDER BY se.createdAt DESC")
    List<SessionEvent> findUserEventsFromDate(@Param("userId") String userId,
                                              @Param("fromDate") LocalDateTime fromDate);

    // Find user's coding activities (runs and submits only)
    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId " +
            "AND se.eventType IN ('CODE_RUN', 'CODE_SUBMIT') " +
            "AND se.createdAt >= :fromDate ORDER BY se.createdAt DESC")
    List<SessionEvent> findUserCodingActivity(@Param("userId") String userId,
                                              @Param("fromDate") LocalDateTime fromDate);

    // Count events by type for user
    @Query("SELECT COUNT(se) FROM SessionEvent se WHERE se.userId = :userId " +
            "AND se.eventType = :eventType AND se.createdAt >= :fromDate")
    Long countEventsByUserAndType(@Param("userId") String userId,
                                  @Param("eventType") String eventType,
                                  @Param("fromDate") LocalDateTime fromDate);

    // Get unique problems attempted by user
    @Query("SELECT DISTINCT se.problemId FROM SessionEvent se WHERE se.userId = :userId " +
            "AND se.createdAt >= :fromDate AND se.problemId IS NOT NULL")
    List<String> findUniqueProblemsAttempted(@Param("userId") String userId,
                                             @Param("fromDate") LocalDateTime fromDate);

    // Get language usage statistics
    @Query("SELECT se.language, COUNT(se) FROM SessionEvent se WHERE se.userId = :userId " +
            "AND se.createdAt >= :fromDate AND se.language IS NOT NULL " +
            "GROUP BY se.language ORDER BY COUNT(se) DESC")
    List<Object[]> findLanguageUsageStats(@Param("userId") String userId,
                                          @Param("fromDate") LocalDateTime fromDate);

    // Find recent code samples with source code
    @Query("SELECT se FROM SessionEvent se WHERE se.userId = :userId " +
            "AND se.sourceCode IS NOT NULL AND LENGTH(se.sourceCode) > 10 " +
            "AND se.createdAt >= :fromDate ORDER BY se.createdAt DESC")
    List<SessionEvent> findRecentCodeSamples(@Param("userId") String userId,
                                             @Param("fromDate") LocalDateTime fromDate);

    // Get problem category distribution (basic keyword matching)
    @Query("SELECT " +
            "CASE " +
            "  WHEN LOWER(se.problemTitle) LIKE '%array%' OR LOWER(se.problemTitle) LIKE '%list%' THEN 'Array' " +
            "  WHEN LOWER(se.problemTitle) LIKE '%string%' THEN 'String' " +
            "  WHEN LOWER(se.problemTitle) LIKE '%tree%' OR LOWER(se.problemTitle) LIKE '%binary%' THEN 'Tree' " +
            "  WHEN LOWER(se.problemTitle) LIKE '%graph%' OR LOWER(se.problemTitle) LIKE '%bfs%' OR LOWER(se.problemTitle) LIKE '%dfs%' THEN 'Graph' " +
            "  WHEN LOWER(se.problemTitle) LIKE '%dynamic%' OR LOWER(se.problemTitle) LIKE '%dp%' THEN 'Dynamic Programming' " +
            "  WHEN LOWER(se.problemTitle) LIKE '%sort%' THEN 'Sorting' " +
            "  WHEN LOWER(se.problemTitle) LIKE '%hash%' OR LOWER(se.problemTitle) LIKE '%map%' THEN 'Hash Table' " +
            "  ELSE 'Other' " +
            "END as category, COUNT(DISTINCT se.problemId) " +
            "FROM SessionEvent se WHERE se.userId = :userId " +
            "AND se.createdAt >= :fromDate AND se.problemTitle IS NOT NULL " +
            "GROUP BY category ORDER BY COUNT(DISTINCT se.problemId) DESC")
    List<Object[]> getProblemCategoryStats(@Param("userId") String userId,
                                           @Param("fromDate") LocalDateTime fromDate);
}
