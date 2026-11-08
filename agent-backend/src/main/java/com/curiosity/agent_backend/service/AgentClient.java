package com.curiosity.agent_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AgentClient {

    private final RestClient restClient;

    public AgentClient(@Value("${agent.base-url}") String baseUrl) {
        // Uzun işler var: Gemini + validate döngüsü dakikalar,
        // XTTS model yüklemesi ilk istekte daha da uzun sürebilir.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public Map<String, Object> ping() {
        return restClient.get()
                .uri("/ping")
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> generateScript(String topic) {
        return restClient.post()
                .uri("/agent/script")
                .body(Map.of("topic", topic))
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> reviewScript(String topic, String script) {
        return restClient.post()
                .uri("/agent/review")
                .body(Map.of("topic", topic, "script", script))
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> splitScript(String script) {
        return restClient.post()
                .uri("/agent/split")
                .body(Map.of("script", script))
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> generateImagePrompts(String topic, List<String> parts) {
        return restClient.post()
                .uri("/agent/image-prompts")
                .body(Map.of("topic", topic, "parts", parts))
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> generateTts(String text, String outputDir, int index) {
        return restClient.post()
                .uri("/agent/tts")
                .body(Map.of("text", text, "outputDir", outputDir, "index", index))
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> generateImage(String prompt, String outputDir, int index) {
        return restClient.post()
                .uri("/agent/image")
                .body(Map.of("prompt", prompt, "outputDir", outputDir, "index", index))
                .retrieve()
                .body(Map.class);
    }
}
