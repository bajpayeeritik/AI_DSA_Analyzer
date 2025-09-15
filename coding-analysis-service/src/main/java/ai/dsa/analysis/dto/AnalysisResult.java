package ai.dsa.analysis.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalysisResult {
    private boolean success;
    private Long analysisId;
    private String summary;
    private List<String> recommendations;
    private String errorMessage;
    private Double initialApproachRating;
    private Double codeQualityScore;
}
