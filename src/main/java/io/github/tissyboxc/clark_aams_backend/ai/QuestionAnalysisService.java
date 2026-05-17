package io.github.tissyboxc.clark_aams_backend.ai;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionAnalysisService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final String SYSTEM_PROMPT = """
            You are a learning assistant used only for self-study, review, and question analysis.
            The user will provide question_type, question_content, and options.
            Analyze the knowledge points, explain why each option may be correct or wrong,
            give concise solving steps, and return the final option letter.
            Return only one valid JSON object. Do not include markdown, code fences, or extra text.
            The JSON object must use exactly these fields:
            {
              "question_type": "question type",
              "knowledge_points": ["knowledge point"],
              "thinking_steps": ["step"],
              "option_analysis": {
                "A": "analysis for option A",
                "B": "analysis for option B",
                "C": "analysis for option C",
                "D": "analysis for option D"
              },
              "answer": "final option letters, for example A or AC",
              "study_hint": "short study hint"
            }
            Use Chinese for the field values when the question is in Chinese.
            """;

    private final AiConfigStore configStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QuestionAnalysisService(AiConfigStore configStore, ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public Map<String, Object> analyze(Map<String, Object> request) {
        Object questionType = firstPresent(request, "question_type", "questionType", "type");
        Object questionContent = firstPresent(request, "question_content", "questionContent", "content");
        Object options = firstPresent(request, "options", "optionList", "choices");

        if (blank(questionContent)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "question_content is required");
        }
        if (options == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "options is required");
        }

        AiRuntimeConfig config = configStore.runtimeConfig();
        if (!config.ready()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI config is incomplete");
        }

        Map<String, Object> userPrompt = new LinkedHashMap<>();
        userPrompt.put("question_type", blank(questionType) ? "unknown" : String.valueOf(questionType).trim());
        userPrompt.put("question_content", String.valueOf(questionContent).trim());
        userPrompt.put("options", options);

        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(config, userPrompt));
            HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri(config.baseUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI request failed: HTTP " + response.statusCode());
            }
            return parseAiContent(extractContent(response.body()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI request interrupted", exception);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI request failed", exception);
        }
    }

    private Map<String, Object> buildRequest(AiRuntimeConfig config, Map<String, Object> userPrompt) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", objectMapper.writeValueAsString(userPrompt))
        ));
        body.put("temperature", 0.2);
        body.put("stream", false);
        return body;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(String responseBody) throws IOException {
        Object parsed = objectMapper.readValue(responseBody, Object.class);
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI response format is invalid");
        }
        Object choicesValue = root.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI response missing choices");
        }
        Object firstChoice = choices.getFirst();
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI choice format is invalid");
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI response missing message");
        }
        Object contentValue = message.get("content");
        if (!(contentValue instanceof String content) || content.isBlank()) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI response content is empty");
        }
        return content;
    }

    private Map<String, Object> parseAiContent(String content) {
        String json = extractJsonObject(stripMarkdown(content.trim()));
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return stringifyKeys(map);
            }
        } catch (Exception ignored) {
            // Keep the raw model output so the frontend can show a useful failure.
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("error", "AI returned invalid JSON");
        fallback.put("raw_content", content);
        return fallback;
    }

    private String stripMarkdown(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return value.substring(firstLineEnd + 1, lastFence).trim();
        }
        return value;
    }

    private String extractJsonObject(String value) {
        if (value.startsWith("{")) {
            return value;
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private Map<String, Object> stringifyKeys(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), stringifyNestedKeys(entry.getValue()));
        }
        return result;
    }

    private Object stringifyNestedKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringifyKeys(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::stringifyNestedKeys).toList();
        }
        return value;
    }

    private URI chatCompletionsUri(String baseUrl) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized + "/chat/completions");
    }

    private Object firstPresent(Map<String, Object> request, String... keys) {
        for (String key : keys) {
            if (request.containsKey(key)) {
                return request.get(key);
            }
        }
        return null;
    }

    private boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
