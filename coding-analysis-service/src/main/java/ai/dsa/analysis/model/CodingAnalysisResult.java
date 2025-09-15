package ai.dsa.analysis.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing AI analysis results for user coding patterns.
 * Stores comprehensive analysis data including ratings, insights, and recommendations.
 */
@Entity
@Table(name = "coding_analysis_results", indexes = {
        @Index(name = "idx_user_analysis_date", columnList = "user_id, analysis_date"),
        @Index(name = "idx_analysis_date", columnList = "analysis_date"),
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User Information
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(name = "analysis_period_days")
    private Integer analysisPeriodDays;

    // Summary Statistics
    @Column(name = "total_problems_attempted")
    private Integer totalProblemsAttempted;

    @Column(name = "total_runs")
    private Integer totalRuns;

    @Column(name = "total_submits")
    private Integer totalSubmits;

    @Column(name = "unique_languages_used")
    private Integer uniqueLanguagesUsed;

    @Column(name = "most_used_language", length = 50)
    private String mostUsedLanguage;

    // Pattern Analysis (JSON fields for flexibility)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "problem_categories", columnDefinition = "JSON")
    @JsonRawValue
    private String problemCategories; // {"easy": 15, "medium": 8, "hard": 2}

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "common_patterns", columnDefinition = "JSON")
    @JsonRawValue
    private String commonPatterns; // AI-generated patterns

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mistake_patterns", columnDefinition = "JSON")
    @JsonRawValue
    private String mistakePatterns; // AI-identified common mistakes

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvement_suggestions", columnDefinition = "JSON")
    @JsonRawValue
    private String improvementSuggestions; // AI suggestions

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_focus_areas", columnDefinition = "JSON")
    @JsonRawValue
    private String recommendedFocusAreas;

    // AI Analysis Results
    @Column(name = "initial_approach_rating", precision = 3, scale = 2)
    private BigDecimal initialApproachRating; // 1.00 to 5.00

    @Column(name = "code_quality_score", precision = 3, scale = 2)
    private BigDecimal codeQualityScore;

    @Column(name = "problem_solving_style", columnDefinition = "TEXT")
    private String problemSolvingStyle;

    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "weaknesses", columnDefinition = "TEXT")
    private String weaknesses;

    // AI Metadata
    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Column(name = "analysis_confidence", precision = 3, scale = 2)
    private BigDecimal analysisConfidence; // ✅ This field is now included!

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (analysisDate == null) {
            analysisDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods for common operations
    public boolean isRecentAnalysis() {
        return analysisDate != null && analysisDate.isAfter(LocalDate.now().minusDays(7));
    }

    public String getFormattedConfidence() {
        if (analysisConfidence == null) return "N/A";
        return String.format("%.1f%%", analysisConfidence.doubleValue() * 100);
    }

    public String getFormattedRating() {
        if (initialApproachRating == null) return "N/A";
        return String.format("%.1f/5.0", initialApproachRating.doubleValue());
    }
}
