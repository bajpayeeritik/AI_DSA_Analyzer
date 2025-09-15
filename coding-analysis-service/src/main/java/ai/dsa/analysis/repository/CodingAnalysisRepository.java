package ai.dsa.analysis.repository;

import ai.dsa.analysis.model.CodingAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CodingAnalysisRepository extends JpaRepository<CodingAnalysisResult, Long> {

    // Find user's analysis history
    List<CodingAnalysisResult> findByUserIdOrderByAnalysisDateDesc(String userId);

    // Find latest analysis for a user
    Optional<CodingAnalysisResult> findFirstByUserIdOrderByAnalysisDateDesc(String userId);

    // Find analysis by user and date range
    @Query("SELECT car FROM CodingAnalysisResult car WHERE car.userId = :userId " +
            "AND car.analysisDate BETWEEN :startDate AND :endDate " +
            "ORDER BY car.analysisDate DESC")
    List<CodingAnalysisResult> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Check if analysis exists for user and date
    boolean existsByUserIdAndAnalysisDate(String userId, LocalDate analysisDate);

    // Count total analyses
    @Query("SELECT COUNT(car) FROM CodingAnalysisResult car WHERE car.userId = :userId")
    Long countAnalysesByUser(@Param("userId") String userId);

    // Find recent analyses (last 30 days)
    @Query("SELECT car FROM CodingAnalysisResult car WHERE car.analysisDate >= :fromDate " +
            "ORDER BY car.analysisDate DESC")
    List<CodingAnalysisResult> findRecentAnalyses(@Param("fromDate") LocalDate fromDate);

    // Get analysis summary stats
    @Query("SELECT COUNT(car), AVG(car.initialApproachRating), AVG(car.codeQualityScore) " +
            "FROM CodingAnalysisResult car WHERE car.userId = :userId")
    Object[] getUserAnalysisStats(@Param("userId") String userId);
}
