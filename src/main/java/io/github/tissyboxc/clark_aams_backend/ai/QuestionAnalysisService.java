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
    private static final String OBJECTIVE_SYSTEM_PROMPT = """
            You are a learning assistant used only for self-study, review, and question analysis.
            The user will provide question_type, question_content, and options.
            This endpoint is only for single-choice, multiple-choice, and true/false questions.
            Analyze the knowledge points, explain why each option may be correct or wrong,
            give concise solving steps, and return the final option letter.
            Return only one valid JSON object. Do not include markdown, code fences, or extra text.
            Do not ask follow-up questions. Do not offer extra help. Do not output phrases such as
            "你还需要我", "如果你需要", "是否需要", or any similar conversational ending.
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
    private static final String FILL_BLANK_SYSTEM_PROMPT = """
            You are a learning assistant used only for self-study, review, and question analysis.
            The user will provide a fill-in-the-blank style question. This includes fill-in,
            material-analysis, short-answer, and essay/discussion questions.
            The user may provide question_type, question_content, material, reference_answer,
            student_answer, or other context fields. Options may be absent and must not be required.

            Your task:
            1. Read the material and question carefully.
            2. Extract knowledge points and scoring points.
            3. Give concise solving steps.
            4. Produce a direct answer suitable for filling into the answer area.
            5. If the provided information is insufficient, do not ask the user for more details.
               Put "无法确定" in answer, describe the missing information in missing_info, and still return JSON.
            6. If the question clearly contains multiple blanks or sub-questions, such as "(1)(2)",
               "（1）（2）", "1.", "2.", "1、", "2、", or similar numbered answer slots,
               the answer field must explicitly label each sub-answer as 第一小题, 第二小题, etc.
               Different sub-answers must be separated with "(1CLARK)", "(2CLARK)", "(3CLARK)" in order.
               This is mandatory. Do not merge multiple sub-answers into one paragraph without these CLARK separators.

            Return only one valid JSON object. Do not include markdown, code fences, explanations outside JSON,
            greetings, apologies, follow-up questions, or extra conversational text.
            Strictly avoid phrases such as "你还需要我", "如果你需要", "是否需要", "我可以继续",
            "需要的话", and any similar offer to continue.

            The JSON object must use exactly these fields:
            {
              "question_type": "question type",
              "knowledge_points": ["knowledge point"],
              "thinking_steps": ["step"],
              "answer": "direct final answer",
              "answer_analysis": "brief explanation for the answer",
              "scoring_points": ["key point that should appear in the answer"],
              "missing_info": ["missing information, empty array when enough information is available"],
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

        return requestAnalysis(config, OBJECTIVE_SYSTEM_PROMPT, userPrompt);
    }

    public Map<String, Object> analyzeFillBlank(Map<String, Object> request) {
        Object questionType = firstPresent(request, "question_type", "questionType", "type");
        Object questionContent = firstPresent(request, "question_content", "questionContent", "content", "stem");
        Object material = firstPresent(request, "material", "materials", "question_material", "questionMaterial", "context", "passage");
        Object referenceAnswer = firstPresent(request, "reference_answer", "referenceAnswer", "standard_answer", "standardAnswer");
        Object studentAnswer = firstPresent(request, "student_answer", "studentAnswer", "user_answer", "userAnswer");

        if (blank(questionContent)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "question_content is required");
        }

        AiRuntimeConfig config = configStore.runtimeConfig();
        if (!config.ready()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI config is incomplete");
        }

        Map<String, Object> userPrompt = new LinkedHashMap<>();
        userPrompt.put("question_type", blank(questionType) ? "fill_blank" : String.valueOf(questionType).trim());
        userPrompt.put("question_content", String.valueOf(questionContent).trim());
        putIfPresent(userPrompt, "material", material);
        putIfPresent(userPrompt, "reference_answer", referenceAnswer);
        putIfPresent(userPrompt, "student_answer", studentAnswer);
        userPrompt.put("raw_request", request);

        return requestAnalysis(config, FILL_BLANK_SYSTEM_PROMPT, userPrompt);
    }

    private Map<String, Object> requestAnalysis(
            AiRuntimeConfig config,
            String systemPrompt,
            Map<String, Object> userPrompt) {
        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(config, systemPrompt, userPrompt));
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

    private Map<String, Object> buildRequest(
            AiRuntimeConfig config,
            String systemPrompt,
            Map<String, Object> userPrompt) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
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

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (!blank(value)) {
            target.put(key, value);
        }
    }
}
