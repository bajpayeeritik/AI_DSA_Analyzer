package ai.dsa.analysis.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coding_session_events")
@Data
@NoArgsConstructor
public class SessionEvent {

    @Id
    private Long id;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "problem_id")
    private String problemId;

    @Column(name = "platform")
    private String platform;

    @Column(name = "session_id")
    private String sessionId;

    // ✅ Fix: Use TEXT for large source code
    @Lob
    @Column(name = "source_code", columnDefinition = "LONGTEXT")
    private String sourceCode;

    @Column(name = "language")
    private String language;

    @Column(name = "problem_title")
    private String problemTitle;

    @Column(name = "problem_url")
    private String problemUrl;

    @Column(name = "leetcode_username")
    private String leetcodeUsername;

    @Column(name = "extension_version")
    private String extensionVersion;

    // ✅ Fix: Use TEXT for large event data
    @Lob
    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
