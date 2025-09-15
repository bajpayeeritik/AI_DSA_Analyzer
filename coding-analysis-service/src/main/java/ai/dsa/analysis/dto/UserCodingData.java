package ai.dsa.analysis.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class UserCodingData {
    private String userId;
    private String problemId;
    private String problemTitle;
    private int totalRuns;
    private int totalSubmits;
    private Set<String> languagesUsed;
    private String mostUsedLanguage;
    private List<String> recentCodeSamples;
}
