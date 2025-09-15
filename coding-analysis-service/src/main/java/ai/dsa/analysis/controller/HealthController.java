package ai.dsa.analysis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "AI Coding Analysis Service");
        status.put("timestamp", LocalDateTime.now());
        status.put("version", "1.0.0");
        return status;
    }

    @GetMapping("/hello")
    public String hello() {
        return "🤖 AI Coding Analysis Service is running! ✅";
    }
}
