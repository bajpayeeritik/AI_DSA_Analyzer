package ai.dsa.analysis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@Slf4j
public class WebClientConfig {

    @Bean("perplexityWebClient")
    public WebClient perplexityWebClient() {
        return WebClient.builder()
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("🔍 API Request: {} {}", request.method(), request.url());
            request.headers().forEach((name, values) ->
                    values.forEach(value -> log.debug("📤 Request Header: {}={}", name, value)));
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("📥 API Response Status: {}", response.statusCode());
            response.headers().asHttpHeaders().forEach((name, values) ->
                    values.forEach(value -> log.debug("📥 Response Header: {}={}", name, value)));
            return Mono.just(response);
        });
    }
}
