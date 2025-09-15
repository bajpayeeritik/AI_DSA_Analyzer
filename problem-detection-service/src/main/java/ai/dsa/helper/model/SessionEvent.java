package ai.dsa.helper.model;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coding_session_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "problem_id")
    private String problemId;

    @Column(name = "platform")
    private String platform;

    @Column(name = "session_id")
    private String sessionId;

    // NEW: Separate code storage from metadata
    @Lob
    @Column(name = "source_code", columnDefinition = "LONGTEXT")
    private String sourceCode;

    // NEW: Additional structured fields for better querying
    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "problem_title")
    private String problemTitle;

    @Column(name = "problem_url", length = 500)
    private String problemUrl;

    @Column(name = "leetcode_username")
    private String leetcodeUsername;

    @Column(name = "extension_version", length = 20)
    private String extensionVersion;

    // Keep for metadata only (no large code blobs)
    @Column(name = "event_data", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    @JsonRawValue
    private String eventData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        processedAt = LocalDateTime.now();
        if (sessionId == null && userId != null && problemId != null) {
            sessionId = userId + "_" + problemId + "_" + System.currentTimeMillis();
        }
    }
}
