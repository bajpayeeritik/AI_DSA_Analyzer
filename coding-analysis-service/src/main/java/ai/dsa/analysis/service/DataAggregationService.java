package ai.dsa.analysis.service;

import ai.dsa.analysis.dto.UserCodingData;
import ai.dsa.analysis.model.SessionEvent;
import ai.dsa.analysis.model.User;
import ai.dsa.analysis.repository.SessionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataAggregationService {

    private final SessionEventRepository sessionEventRepository;

    public  List<UserCodingData>  aggregateUserData(String userId, int periodDays) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(periodDays);

        log.info("🔍 Aggregating data for user: {} (last {} days)", userId, periodDays);

        // Get user's coding sessions
        List<SessionEvent> sessions = sessionEventRepository.findUserCodingActivity(userId, fromDate);
        log.debug("📊 Found {} coding sessions for user {}", sessions.size(), userId);

        // Calculate statistics
        Map<String, Long> languageStats = sessions.stream()
                .filter(s -> s.getLanguage() != null && !s.getLanguage().equals("unknown"))
                .collect(Collectors.groupingBy(SessionEvent::getLanguage, Collectors.counting()));

        Map<String, Long> problemCategories = categorizeProblemsByTitle(sessions);
        Map<String,List<String>> recentCodeSamples = extractRecentCodeSamples(sessions);

        String mostUsedLanguage = languageStats.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");

        List<UserCodingData> userCodingData=new ArrayList<>();
        for(String problemTitle: recentCodeSamples.keySet()){
            UserCodingData result = UserCodingData.builder()
                    .userId(userId)
                    .problemTitle(problemTitle)
                    .totalRuns((int) sessions.stream().filter(s -> "CODE_RUN".equals(s.getEventType())).count())
                    .totalSubmits((int) sessions.stream().filter(s -> "CODE_SUBMIT".equals(s.getEventType())).count())
                    .languagesUsed(languageStats.keySet())
                    .mostUsedLanguage(mostUsedLanguage)
                    .recentCodeSamples(recentCodeSamples.get(problemTitle))
                    .build();
            userCodingData.add(result);
        }
//        UserCodingData result = UserCodingData.builder()
//                .userId(userId)
//                .totalProblems((int) sessions.stream().map(SessionEvent::getProblemId).distinct().count())
//                .totalRuns((int) sessions.stream().filter(s -> "CODE_RUN".equals(s.getEventType())).count())
//                .totalSubmits((int) sessions.stream().filter(s -> "CODE_SUBMIT".equals(s.getEventType())).count())
//                .languagesUsed(languageStats.keySet())
//                .mostUsedLanguage(mostUsedLanguage)
//                .problemCategories(problemCategories)
//                .recentCodeSamples(recentCodeSamples)
//                .analysisPeriodDays(periodDays)
//                .build();
        for(UserCodingData data:userCodingData){
            log.info("✅ Aggregated data: {} problem, {} runs, {} submits for user {}",
                    data.getProblemTitle(), data.getTotalRuns(), data.getTotalSubmits(), userId);
        }


        return userCodingData;
    }

    private Map<String, Long> categorizeProblemsByTitle(List<SessionEvent> sessions) {
        Map<String, Long> categories = new HashMap<>();

        for (SessionEvent session : sessions) {
            String title = session.getProblemTitle();
            if (title == null) continue;

            String titleLower = title.toLowerCase();
            String category = "Other";

            if (titleLower.contains("array") || titleLower.contains("list")) {
                category = "Array";
            } else if (titleLower.contains("string")) {
                category = "String";
            } else if (titleLower.contains("tree") || titleLower.contains("binary")) {
                category = "Tree";
            } else if (titleLower.contains("graph") || titleLower.contains("bfs") || titleLower.contains("dfs")) {
                category = "Graph";
            } else if (titleLower.contains("dynamic") || titleLower.contains("dp")) {
                category = "Dynamic Programming";
            } else if (titleLower.contains("sort")) {
                category = "Sorting";
            } else if (titleLower.contains("hash") || titleLower.contains("map")) {
                category = "Hash Table";
            }

            categories.merge(category, 1L, Long::sum);
        }

        return categories;
    }

    private Map<String,List<String>> extractRecentCodeSamples(List<SessionEvent> sessions) {

        Map<String,List<String>> codes_by_problem=new HashMap<>();
        for(SessionEvent se: sessions){
            if(codes_by_problem.containsKey(se.getProblemTitle())){
                codes_by_problem.get(se.getProblemTitle()).add(se.getSourceCode() != null ?se.getSourceCode():"");
            }
            else{
                List<String> codes=new ArrayList<>();
                codes.add(se.getSourceCode() != null ?se.getSourceCode():"");
                codes_by_problem.put(se.getProblemTitle(),codes);
            }
        }

        return codes_by_problem;
    }

    private String formatCodeSample(SessionEvent session) {
        String code = session.getSourceCode();
        String title = session.getProblemTitle() != null ? session.getProblemTitle() : "Unknown Problem";
        String language = session.getLanguage() != null ? session.getLanguage() : "unknown";


        return String.format("Problem: %s\nLanguage: %s\nCode:\n%s\n---", title, language, code);
    }
}
