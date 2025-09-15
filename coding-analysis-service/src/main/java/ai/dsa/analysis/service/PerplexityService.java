package ai.dsa.analysis.service;

import ai.dsa.analysis.config.PerplexityProperties;
import ai.dsa.analysis.dto.UserCodingData;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PerplexityService {

    private final WebClient webClient;
    private final PerplexityProperties properties;
    /**
     * Quick health check for Perplexity API
     */
    public boolean isApiHealthy() {
        try {
            // Quick test to verify API key and connectivity
            if (properties.getKey() == null ||
                    properties.getKey().equals("not-configured") ||
                    properties.getKey().trim().isEmpty()) {
                log.debug("API key not configured");
                return false;
            }

            String testResponse = callPerplexityAPI("Hello, respond with 'OK'")
                    .timeout(Duration.ofSeconds(5))
                    .block();

            boolean healthy = testResponse != null && !testResponse.trim().isEmpty();
            log.debug("Perplexity API health check: {}", healthy ? "PASS" : "FAIL");
            return healthy;

        } catch (Exception e) {
            log.debug("Perplexity API health check failed: {}", e.getMessage());
            return false;
        }
    }


    public PerplexityService(@Qualifier("perplexityWebClient") WebClient webClient,
                             PerplexityProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
        log.info("🤖 Perplexity service initialized with model: {}", properties.getModel());
    }

    public String analyzeUserCodingPatterns(List<UserCodingData> userData) {
        log.info("🔍 Starting AI analysis for user: {}", userData.get(0).getUserId());
        log.debug("🔑 API key prefix: {}...",
                properties.getKey() != null ? properties.getKey().substring(0, 8) : "null");

        // Build prompt
        String prompt = buildDetailedAnalysisPrompt(userData);
        log.debug("📝 Prompt sent to AI:\n{}", prompt);

        // Call Perplexity API
        Mono<String> responseMono = callPerplexityAPI(prompt)
                .doOnNext(resp -> log.debug("📋 Raw AI response JSON: {}", resp))
                .timeout(Duration.ofMillis(properties.getTimeout()))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)));

        // Block to get the response
        String aiResponse = responseMono.block();
        if (aiResponse == null || aiResponse.isBlank()) {
            log.warn("⚠️ Empty AI response, returning fallback");
            return generateFallbackAnalysis(userData);
        }

        log.info("✅ AI analysis successful for user: {}",  userData.get(0).getUserId());
        return aiResponse;
    }

    private Mono<String> callPerplexityAPI(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are an expert coding mentor; give actionable coding insights."),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", properties.getMaxTokens(),
                "temperature", properties.getTemperature()
        );

        return webClient.post()
                .uri(properties.getBaseUrl() + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::extractResponseContent)
                .doOnError(err -> log.error("🚨 API call failed: {}", err.getMessage()));
    }

    private String extractResponseContent(JsonNode response) {
        try {
            return response.path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();
        } catch (Exception e) {
            log.error("❌ Failed to parse AI response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    private String buildDetailedAnalysisPrompt(List<UserCodingData> userData) {
        StringBuilder dataString = new StringBuilder();
        for (UserCodingData data : userData) {
            dataString.append("Problem Title: ").append(data.getProblemTitle())
                    .append(" | Attempts (runs/submits): ").append(data.getTotalRuns())
                    .append("/").append(data.getTotalSubmits())
                    .append(" | Languages: ").append(data.getLanguagesUsed())
                    .append(" | Most Used: ").append(data.getMostUsedLanguage())
                    .append("\nRecent Code Samples:\n")
                    .append(data.getRecentCodeSamples()).append("\n\n");
        }

        return String.format("""
                   You are a "Developer Cognitive & Session Analyzer". \s

                   You will receive one or more developer coding sessions as JSON objects. \s
                   Each session corresponds to the following schema (mapped from Spring Boot entity `SessionEvent`):

                   {
                     "eventType": "<string>",
                     "userId": "<string>",
                     "problemId": "<string>",
                     "platform": "<string>",
                     "sessionId": "<string>",
                     "sourceCode": "<string>",   // Full code snapshot submitted/run
                     "language": "<string>",
                     "problemTitle": "<string>",
                     "problemUrl": "<string>",
                     "leetcodeUsername": "<string>",
                     "extensionVersion": "<string>",
                     "eventData": { ... },       // JSON metadata: run_count, results, execution type, commit notes
                     "createdAt": "<ISO timestamp>",
                     "processedAt": "<ISO timestamp>"
                   }

                   ---

                   ### 🎯 Task

                   Analyze the developer’s problem-solving sessions and produce **ONLY a structured JSON** object called `analysis_json`. \s

                   The structure must include:

                   1. **per_problem metrics** \s
                      - `problem_id`
                      - `problem_title`
                      - `total_runs`
                      - `turns_to_success`
                      - `runs_to_success`
                      - `languages_used`
                      - `final_result` ("success"/"failure")
                      - `thinking_patterns` → infer how the developer **approaches a prompt**:
                        * Do they start brute-force or optimized?
                        * Do they validate edge cases early or late?
                        * Do they refactor before/after debugging?
                        * Do they change strategies mid-way?

                   2. **global cognitive patterns** \s
                      - `approach_style`: e.g. "start simple & iterate", "jumps directly to optimized", "trial & error heavy".
                      - `pattern_consistency`: how consistent is the style across problems?
                      - `adaptability`: how quickly do they change approach when the first attempt fails?
                      - `prompt_understanding`: evidence of correctly/incorrectly interpreting problem statements.

                   3. **global metrics** \s
                      - `average_runs_to_success`
                      - `median_turns_to_success`
                      - `runs_distribution` (bucketed: 1–3, 4–10, 11–30, 31+)
                      - `common_thinking_traps` (e.g., overcomplicating, under-testing, edge-case blind spots)

                   4. **scored evaluation (1–5)** \s
                      - `problem_understanding`
                      - `initial_strategy_quality`
                      - `edge_case_awareness`
                      - `debugging_efficiency`
                      - `adaptability`
                      - `code_quality` \s
                      Each with justification string.

                   5. **recommendations** \s
                      - `per_problem`: 3 concrete suggestions per problem. \s
                      - `global`: thinking habits & mental models (e.g., "map problem to known patterns", "simulate small inputs before coding"). \s

                   6. **learning_path** \s
                      - Roadmap focusing on **thinking skills** as well as technical gaps (topics, problem-solving drills, reflection strategies).

                   ---

                   ### 📝 Notes for Analysis
                   - Use **diff-based heuristics** to classify cognitive steps (e.g., "brute force start", "pattern recognition", "debug-first iteration"). \s
                   - If uncertain, mark label with `"uncertain": true`. \s
                   - Always tie reasoning to **observed evidence** in code and iteration patterns. \s
                   - Only return **valid JSON** under the root key `analysis_json`. No extra commentary.

                   ---

                   ### ✅ Output Format

                   ```json
                   {
                     "analysis_json": {
                       "per_problem": [ ... ],
                       "global_cognitive_patterns": { ... },
                       "global_metrics": { ... },
                       "scored_evaluation": { ... },
                       "recommendations": {
                          "per_problem": { ... },
                          "global": [ ... ]
                       },
                       "learning_path": [ ... ]
                     }
                   }
            """, dataString.toString());
    }

    private String generateFallbackAnalysis(List<UserCodingData> userData) {
        StringBuilder dataString = new StringBuilder();

        for (UserCodingData data : userData) {
            dataString.append("📝 Problem: ").append(data.getProblemTitle())
                    .append(" (ID: ").append(data.getProblemId()).append(")\n")
                    .append("• Total Runs: ").append(data.getTotalRuns())
                    .append(", Submits: ").append(data.getTotalSubmits()).append("\n")
                    .append("• Languages Used: ").append(String.join(", ", data.getLanguagesUsed())).append("\n")
                    .append("• Most Used Language: ").append(data.getMostUsedLanguage()).append("\n")
                    .append("• Recent Code Samples:\n");

            int i = 1;
            for (String sample : data.getRecentCodeSamples()) {
                dataString.append("   Sample ").append(i++).append(":\n")
                        .append(sample).append("\n\n");
            }
        }

        return String.format("""
        ## 🔄 **Fallback Developer Analysis**

        Since advanced JSON analysis could not be generated, here’s a heuristic summary of coding behavior:

        ### 📊 Problem Attempts
        %s

        ### 🧠 Observed Thinking Patterns
        - Initial approach: Often starts with %s solutions.
        - Edge case handling: %s.
        - Debugging style: %s.

        ### 💡 Quick Recommendations
        1. Validate edge cases earlier.
        2. Try structured testing after each iteration.
        3. Experiment with alternative algorithms when stuck.

        *This fallback analysis highlights broad behavioral insights instead of detailed metrics.*
        """,
                dataString.toString(),
                userData.stream().anyMatch(d -> d.getTotalRuns() > d.getTotalSubmits() * 3) ? "iterative / brute-force" : "direct / confident",
                userData.stream().anyMatch(d -> d.getRecentCodeSamples().size() > 2) ? "appears reactive (handled later)" : "handled early or implicitly",
                userData.stream().anyMatch(d -> d.getTotalRuns() > 10) ? "trial-and-error heavy" : "focused and efficient"
        );
    }

}
