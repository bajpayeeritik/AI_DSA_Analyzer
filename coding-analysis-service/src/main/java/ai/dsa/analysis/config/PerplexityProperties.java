package ai.dsa.analysis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "perplexity.api")
public class PerplexityProperties {

    private String key;
    private String baseUrl;
    private String model;
    private int timeout = 30000;
    private int maxTokens = 1000;
    private double temperature = 0.7;

    // Default constructor required for Spring
    public PerplexityProperties() {}
}
