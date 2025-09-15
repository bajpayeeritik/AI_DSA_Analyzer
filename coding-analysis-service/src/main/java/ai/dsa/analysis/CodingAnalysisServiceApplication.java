package ai.dsa.analysis;

import ai.dsa.analysis.config.PerplexityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PerplexityProperties.class)  // ✅ This creates the bean
public class CodingAnalysisServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodingAnalysisServiceApplication.class, args);
	}
}
