package ai.dsa.analysis.service;

import ai.dsa.analysis.dto.AnalysisResult;
import ai.dsa.analysis.dto.UserCodingData;
import ai.dsa.analysis.model.CodingAnalysisResult;
import ai.dsa.analysis.repository.CodingAnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing user coding patterns with AI-powered insights.
 * Integrates with Perplexity AI for advanced analysis and recommendations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisService {

    private final DataAggregationService dataAggregationService;
    private final CodingAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;
    private final PerplexityService perplexityService;

    /**
     * Analyzes user coding patterns within the specified period.
     * Uses AI analysis with fallback to heuristics if AI is unavailable.
     *
     * @param userId     The user ID to analyze
     * @param periodDays Number of days to look back for analysis
     * @return AnalysisResult containing AI-powered insights and recommendations
     */
    @Transactional
    public AnalysisResult analyzeUserCodingPatterns(String userId, int periodDays) {
        // Input validation
        if (userId == null || userId.trim().isEmpty()) {
            return AnalysisResult.builder()
                    .success(false)
                    .errorMessage("User ID cannot be null or empty")
                    .build();
        }

        if (periodDays <= 0 || periodDays > 365) {
            return AnalysisResult.builder()
                    .success(false)
                    .errorMessage("Period days must be between 1 and 365")
                    .build();
        }

        log.info("🤖 Starting AI analysis for user: {} (period: {} days)", userId, periodDays);

        try {
            // Check if analysis already exists for today
            LocalDate today = LocalDate.now();
//            if (analysisRepository.existsByUserIdAndAnalysisDate(userId, today)) {
//                log.info("📅 Analysis already exists for user {} on {}", userId, today);
//                Optional<CodingAnalysisResult> existingOpt = analysisRepository
//                        .findFirstByUserIdOrderByAnalysisDateDesc(userId);
//
//                if (existingOpt.isPresent()) {
//                    CodingAnalysisResult existing = existingOpt.get();
//                    return buildAnalysisResult(existing);
//                }
//            }

            // Step 1: Aggregate user data
            List<UserCodingData>   userData = dataAggregationService.aggregateUserData(userId, periodDays);
            for(UserCodingData data:userData){
                log.info("✅ Aggregated data: {} problem, {} runs, {} submits for user {}",
                        data.getProblemTitle(), data.getTotalRuns(), data.getTotalSubmits(), userId);
                if (data.getTotalRuns() == 0 && data.getTotalSubmits() == 0) {
                    log.warn("⚠️ No coding activity found for user {} in the last {} days", userId, periodDays);
                    return AnalysisResult.builder()
                            .success(false)
                            .errorMessage("No coding activity found for the specified period")
                            .build();
                }
            }

            // Check if user has sufficient data for meaningful analysis


            // Step 2: Perform AI-powered analysis with fallback
            CodingAnalysisResult analysisResult = performAIAnalysis(userData);

            // Step 3: Save results
            CodingAnalysisResult savedResult = analysisRepository.save(analysisResult);
            log.info("✅ Analysis saved with ID: {}", savedResult.getId());

            return buildAnalysisResult(savedResult);

        } catch (JsonProcessingException e) {
            log.error("❌ JSON processing error for user {}: {}", userId, e.getMessage(), e);
            return AnalysisResult.builder()
                    .success(false)
                    .errorMessage("Failed to process analysis data: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("❌ Unexpected error analyzing user {}: {}", userId, e.getMessage(), e);
            return AnalysisResult.builder()
                    .success(false)
                    .errorMessage("Analysis failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Builds AnalysisResult from CodingAnalysisResult entity
     */
    private AnalysisResult buildAnalysisResult(CodingAnalysisResult savedResult) {
        // Extract summary from problemSolvingStyle (which contains the full analysis)
        String summary = extractSummaryFromAnalysis(savedResult.getProblemSolvingStyle());

        return AnalysisResult.builder()
                .success(true)
                .analysisId(savedResult.getId())
                .summary(summary)  // ✅ Now properly populated
                .recommendations(parseRecommendations(savedResult.getImprovementSuggestions()))
                .initialApproachRating(savedResult.getInitialApproachRating() != null ?
                        savedResult.getInitialApproachRating().doubleValue() : null)
                .codeQualityScore(savedResult.getCodeQualityScore() != null ?
                        savedResult.getCodeQualityScore().doubleValue() : null)
                .build();
    }

    /**
     * Extracts a concise summary from the full analysis
     */
    private String extractSummaryFromAnalysis(String fullAnalysis) {
        if (fullAnalysis == null || fullAnalysis.trim().isEmpty()) {
            return "Analysis completed with basic heuristic evaluation.";
        }

        // Extract the first meaningful paragraph as summary
        String[] sections = fullAnalysis.split("\n\n");
        for (String section : sections) {
            if (section.length() > 50 && !section.startsWith("#")) {
                // Clean up formatting and take first 200 characters
                String clean = section.replaceAll("\\*\\*", "").replaceAll("###?\\s*", "").trim();
                if (clean.length() > 200) {
                    return clean.substring(0, 197) + "...";
                }
                return clean;
            }
        }

        return "Comprehensive coding pattern analysis completed based on your recent activity.";
    }

    /**
     * Performs AI-powered analysis with fallback to heuristics
     */
    private CodingAnalysisResult performAIAnalysis(List<UserCodingData> userData) throws JsonProcessingException {
        String aiInsights;
        String aiModelUsed;
        double analysisConfidence;

        // Try AI analysis first
        try {
            log.info("🤖 Requesting AI analysis from Perplexity for user {}",  userData.get(0).getUserId());
            aiInsights = perplexityService.analyzeUserCodingPatterns(userData);

            if (aiInsights != null && !aiInsights.trim().isEmpty() && !aiInsights.contains("AI analysis temporarily unavailable")) {
                aiModelUsed = "perplexity-ai";
                analysisConfidence = 0.90;
                log.info("✅ Successfully obtained AI analysis for user {}",  userData.get(0).getUserId());
            } else {
                throw new RuntimeException("AI returned empty or fallback response");
            }
        } catch (Exception e) {
            log.warn("🔄 AI analysis failed for user {}, using heuristic fallback: {}", userData.getUserId(), e.getMessage());
            aiInsights = generateHeuristicAnalysis(userData);
            aiModelUsed = "heuristic-fallback";
            analysisConfidence = 0.65;
        }

        // Extract or calculate metrics
        double approachRating = extractOrCalculateApproachRating(aiInsights, userData);
        double qualityScore = extractOrCalculateQualityScore(aiInsights, userData);

        String problemSolvingStyle = extractOrGenerateProblemSolvingStyle(aiInsights, userData);
        String strengths = extractOrGenerateStrengths(aiInsights, userData);
        String weaknesses = extractOrGenerateWeaknesses(aiInsights, userData);

        Map<String, Object> suggestions = extractOrGenerateImprovementSuggestions(aiInsights, userData);

        return CodingAnalysisResult.builder()
                .userId(userData.getUserId())
                .analysisDate(LocalDate.now())
                .analysisPeriodDays(userData.getAnalysisPeriodDays())
                .totalProblemsAttempted(userData.getTotalProblems())
                .totalRuns(userData.getTotalRuns())
                .totalSubmits(userData.getTotalSubmits())
                .uniqueLanguagesUsed(userData.getLanguagesUsed().size())
                .mostUsedLanguage(userData.getMostUsedLanguage())
                .problemCategories(objectMapper.writeValueAsString(userData.getProblemCategories()))
                .initialApproachRating(BigDecimal.valueOf(approachRating))
                .codeQualityScore(BigDecimal.valueOf(qualityScore))
                .problemSolvingStyle(problemSolvingStyle)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .improvementSuggestions(objectMapper.writeValueAsString(suggestions))
                .aiModelUsed(aiModelUsed)
                .analysisConfidence(BigDecimal.valueOf(analysisConfidence))
                .build();
    }

    /**
     * Generates comprehensive heuristic-based analysis as fallback
     */
    private String generateHeuristicAnalysis(List<UserCodingData> userDataList) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("## 🎯 CODING PATTERN ANALYSIS (Fallback)\n\n");
        prompt.append("You are an expert coding mentor. ")
                .append("Analyze the following multiple users' coding activity data and generate a **heuristic-style analysis**. ")
                .append("For each user, provide insights on:\n")
                .append("1. Problem-Solving Approach\n")
                .append("2. Practice Consistency & Volume\n")
                .append("3. Technical Versatility\n")
                .append("4. Problem Domain Coverage\n")
                .append("5. Code Quality & Efficiency Indicators\n")
                .append("6. Strategic Development Path\n")
                .append("7. Key Strengths\n\n");

        int index = 1;
        for (UserCodingData userData : userDataList) {
            prompt.append("### 👤 User ").append(index++).append("\n");
            prompt.append("- User ID: ").append(userData.getUserId()).append("\n");
            prompt.append("- Total Code Runs: ").append(userData.getTotalRuns()).append("\n");
            prompt.append("- Total Submissions: ").append(userData.getTotalSubmits()).append("\n");
            prompt.append("- Languages Used: ").append(String.join(", ", userData.getLanguagesUsed())).append("\n");
            prompt.append("- Most Used Language: ").append(userData.getMostUsedLanguage()).append("\n");
            prompt.append("\n");
        }

        prompt.append("### 📝 Instructions:\n");
        prompt.append("Provide the heuristic analysis for each user individually in the same order. ")
                .append("Make the insights **actionable, clear, and motivational**. ")
                .append("Keep the tone professional but encouraging.\n");

        return prompt.toString();
    }


    /**
     * Extracts approach rating from AI response or calculates heuristically
     */
    private double extractOrCalculateApproachRating(String aiInsights, UserCodingData userData) {
        // Try to extract rating from AI response
        try {
            if (aiInsights.contains("Initial Approach Rating") || aiInsights.contains("approach rating")) {
                // Use regex or string parsing to extract rating
                String[] lines = aiInsights.split("\n");
                for (String line : lines) {
                    if (line.toLowerCase().contains("rating") && line.matches(".*\\b[1-5](\\.\\d)?\\b.*")) {
                        String rating = line.replaceAll(".*\\b([1-5](?:\\.\\d)?)\\b.*", "$1");
                        return Double.parseDouble(rating);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract rating from AI response, using calculation");
        }

        return calculateApproachRating(userData);
    }

    /**
     * Extracts quality score from AI response or calculates heuristically
     */
    private double extractOrCalculateQualityScore(String aiInsights, UserCodingData userData) {
        try {
            if (aiInsights.contains("Code Quality Score") || aiInsights.contains("quality score")) {
                String[] lines = aiInsights.split("\n");
                for (String line : lines) {
                    if (line.toLowerCase().contains("quality") && line.matches(".*\\b[1-5](\\.\\d)?\\b.*")) {
                        String score = line.replaceAll(".*\\b([1-5](?:\\.\\d)?)\\b.*", "$1");
                        return Double.parseDouble(score);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract quality score from AI response, using calculation");
        }

        return calculateQualityScore(userData);
    }

    /**
     * Extracts problem-solving style from AI or generates heuristically
     */
    private String extractOrGenerateProblemSolvingStyle(String aiInsights, UserCodingData userData) {
        if (aiInsights.contains("Problem-Solving Style") || aiInsights.contains("problem solving")) {
            return extractSectionFromAI(aiInsights, "Problem-Solving Style");
        }
        return generateProblemSolvingStyle(userData);
    }

    /**
     * Extracts strengths from AI or generates heuristically
     */
    private String extractOrGenerateStrengths(String aiInsights, UserCodingData userData) {
        if (aiInsights.contains("Strengths") || aiInsights.contains("Key Strengths")) {
            return extractSectionFromAI(aiInsights, "Strengths");
        }
        return generateStrengths(userData);
    }

    /**
     * Extracts weaknesses from AI or generates heuristically
     */
    private String extractOrGenerateWeaknesses(String aiInsights, UserCodingData userData) {
        if (aiInsights.contains("Weaknesses") || aiInsights.contains("Areas for Improvement")) {
            return extractSectionFromAI(aiInsights, "Areas for Improvement");
        }
        return generateWeaknesses(userData);
    }

    /**
     * Extracts improvement suggestions from AI or generates heuristically
     */
    private Map<String, Object> extractOrGenerateImprovementSuggestions(String aiInsights, UserCodingData userData) {
        if (aiInsights.contains("Recommendations") || aiInsights.contains("improvement")) {
            try {
                String recommendations = extractSectionFromAI(aiInsights, "Recommendations");
                List<String> focusAreas = Arrays.stream(recommendations.split("\\n"))
                        .filter(line -> line.trim().startsWith("•") || line.trim().startsWith("-") || line.trim().startsWith("*"))
                        .map(line -> line.replaceAll("^[•\\-\\*]\\s*", "").trim())
                        .collect(Collectors.toList());

                Map<String, Object> suggestions = new HashMap<>();
                suggestions.put("focus_areas", focusAreas);
                suggestions.put("next_steps", focusAreas); // Use same for now
                return suggestions;
            } catch (Exception e) {
                log.debug("Could not extract suggestions from AI response");
            }
        }
        return generateImprovementSuggestions(userData);
    }

    // Include all the helper methods from the previous implementation
    private double calculateApproachRating(UserCodingData userData) {
        double rating = 3.0;
        if (userData.getTotalRuns() > 10) rating += 0.5;
        if (userData.getTotalSubmits() > 5) rating += 0.3;
        if (userData.getTotalProblems() > 5) rating += 0.4;
        if (userData.getLanguagesUsed().size() > 1) rating += 0.2;
        if (userData.getProblemCategories().size() > 3) rating += 0.2;
        return Math.min(5.0, Math.max(1.0, rating));
    }

    private double calculateQualityScore(UserCodingData userData) {
        double score = 3.5;
        if (userData.getTotalSubmits() > 0) {
            double ratio = (double) userData.getTotalRuns() / userData.getTotalSubmits();
            if (ratio <= 2) score += 1.0;
            else if (ratio <= 3) score += 0.5;
            else if (ratio > 6) score -= 0.3;
        }
        if (userData.getTotalProblems() > 0 && userData.getTotalSubmits() > 0) {
            double submitRatio = (double) userData.getTotalSubmits() / userData.getTotalProblems();
            if (submitRatio > 0.7) score += 0.3;
        }
        return Math.min(5.0, Math.max(1.0, score));
    }

    private String generateProblemSolvingStyle(UserCodingData userData) {
        StringBuilder style = new StringBuilder();

        if (userData.getTotalRuns() > userData.getTotalSubmits() * 2) {
            style.append("Iterative problem solver who thoroughly tests code before submission. ");
        } else {
            style.append("Confident problem solver with a focused and efficient approach. ");
        }

        if (userData.getLanguagesUsed().size() > 1) {
            style.append("Demonstrates versatility by using multiple programming languages. ");
        }

        if (userData.getProblemCategories().size() > 2) {
            style.append("Shows breadth in problem-solving by tackling diverse categories. ");
        }

        return style.toString().trim();
    }

    private String generateStrengths(UserCodingData userData) {
        List<String> strengths = new ArrayList<>();
        strengths.add("Active coding practice");

        if (userData.getTotalRuns() > 5) {
            strengths.add("Regular practice habits");
        }
        if (userData.getLanguagesUsed().size() > 1) {
            strengths.add("Language versatility");
        }
        if (userData.getProblemCategories().size() > 3) {
            strengths.add("Diverse problem-solving approach");
        }
        if (userData.getTotalSubmits() > userData.getTotalRuns() * 0.3) {
            strengths.add("Good solution completion rate");
        }

        return String.join(", ", strengths);
    }

    private String generateWeaknesses(UserCodingData userData) {
        List<String> weaknesses = new ArrayList<>();

        if (userData.getAnalysisPeriodDays() < 14) {
            weaknesses.add("Limited analysis period");
        }
        if (userData.getProblemCategories().size() <= 2) {
            weaknesses.add("Need more diverse problem categories");
        }
        if (userData.getLanguagesUsed().size() == 1) {
            weaknesses.add("Could benefit from exploring multiple programming languages");
        }
        if (userData.getTotalRuns() > userData.getTotalSubmits() * 5) {
            weaknesses.add("High run-to-submit ratio suggests room for improvement in solution confidence");
        }

        if (weaknesses.isEmpty()) {
            weaknesses.add("Areas for continued growth and learning");
        }

        return String.join(", ", weaknesses);
    }

    private Map<String, Object> generateImprovementSuggestions(UserCodingData userData) {
        Map<String, Object> suggestions = new HashMap<>();

        List<String> focusAreas = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();
        List<String> resources = new ArrayList<>();

        // Dynamic focus areas based on actual patterns
        if (userData.getProblemCategories().size() <= 2) {
            focusAreas.add("Expand into new problem categories (Graphs, Dynamic Programming, Trees)");
            resources.add("LeetCode problem categories guide");
        }

        if (userData.getLanguagesUsed().size() == 1) {
            focusAreas.add("Learn a second programming language (Python/Java/C++)");
            resources.add("Multi-language algorithm practice");
        }

        double runToSubmitRatio = userData.getTotalSubmits() > 0 ?
                (double) userData.getTotalRuns() / userData.getTotalSubmits() : 0;

        if (runToSubmitRatio > 4) {
            focusAreas.add("Improve initial problem analysis to reduce testing iterations");
            resources.add("Problem-solving frameworks and pattern recognition");
        } else if (runToSubmitRatio < 1.5) {
            focusAreas.add("Increase code testing and edge case consideration");
        }

        // Smart next steps
        if (userData.getTotalProblems() < 10) {
            nextSteps.add("Complete 15-20 problems in the next month");
            nextSteps.add("Focus on fundamental data structures (Arrays, LinkedLists, Stacks)");
        } else if (userData.getTotalProblems() < 50) {
            nextSteps.add("Progress to medium-difficulty problems");
            nextSteps.add("Study time and space complexity analysis");
        } else {
            nextSteps.add("Tackle hard problems and optimize existing solutions");
            nextSteps.add("Explore system design concepts");
        }

        nextSteps.add("Join coding competitions or daily challenges");
        nextSteps.add("Review and optimize your most challenging solutions");

        suggestions.put("focus_areas", focusAreas);
        suggestions.put("next_steps", nextSteps);
        suggestions.put("resources", resources);
        suggestions.put("timeline", "2-4 weeks for immediate improvements, 2-3 months for advanced skills");

        return suggestions;
    }

    /**
     * Helper method to extract sections from AI response
     */
    private String extractSectionFromAI(String aiResponse, String section) {
        try {
            String[] lines = aiResponse.split("\n");
            StringBuilder result = new StringBuilder();
            boolean inSection = false;

            for (String line : lines) {
                if (line.contains(section)) {
                    inSection = true;
                    continue;
                }
                if (inSection && line.startsWith("**") && !line.contains(section)) {
                    break;
                }
                if (inSection && !line.trim().isEmpty()) {
                    result.append(line.trim()).append(" ");
                }
            }

            return result.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to extract section {} from AI response", section);
            return "AI analysis available - see full details";
        }
    }

    /**
     * Parses recommendations JSON string back to List
     */
    @SuppressWarnings("unchecked")
    private List<String> parseRecommendations(String suggestionsJson) {
        if (suggestionsJson == null || suggestionsJson.trim().isEmpty()) {
            return Arrays.asList("Continue practicing regularly", "Focus on problem-solving patterns");
        }

        try {
            Map<String, Object> suggestions = objectMapper.readValue(suggestionsJson, Map.class);
            Object focusAreas = suggestions.get("focus_areas");
            if (focusAreas instanceof List) {
                return (List<String>) focusAreas;
            }
        } catch (Exception e) {
            log.warn("Failed to parse recommendations JSON: {}", e.getMessage());
        }

        return Arrays.asList("Continue practicing regularly", "Focus on problem-solving patterns");
    }
}
