package io.github.tissyboxc.clark_aams_backend.ai;

import io.github.tissyboxc.clark_aams_backend.common.BusinessException;
import io.github.tissyboxc.clark_aams_backend.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseImageRecognitionService {
    private static final int MAX_IMAGES = 8;
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final AiConfigStore configStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CourseImageRecognitionService(AiConfigStore configStore, ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public String recognize(List<MultipartFile> images) {
        validateImages(images);
        AiRuntimeConfig config = configStore.runtimeConfig();
        if (!config.ready()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 配置未完成");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(config, images));
            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri(config.baseUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI 请求失败: HTTP " + response.statusCode());
            }
            return normalizeJsonArray(extractContent(response.body()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI 请求被中断", exception);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI 请求失败", exception);
        }
    }

    private Map<String, Object> buildRequest(AiRuntimeConfig config, List<MultipartFile> images) throws IOException {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "text",
                "text", prompt()
        ));
        for (MultipartFile image : images) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUrl(image))
            ));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", content
        )));
        body.put("temperature", 0);
        body.put("stream", false);
        return body;
    }

    private String prompt() {
        return """
你是课表图片识别器。请从用户上传的一张或多张课表截图中提取所有能完整读到的课程。

必须只输出纯 JSON，不要 Markdown，不要代码块，不要解释文字。

输出必须是 JSON 数组，数组元素严格使用以下字段：

name: string，课程名称，保留截图中的编号如 [02]、[cx01]。
teacher: string，教师姓名；没有或看不清时填空字符串。
position: string，上课地点；线上课填“线上教学-校区”，没有或看不清时填空字符串。
day: number，星期一到星期日分别为 1 到 7。
startSection: number，开始节次。
endSection: number，结束节次。
weeks: number[]，把周次范围展开为数字数组；例如 1-3周,5-6周 输出 [1,2,3,5,6]。
rawTime: string，格式固定为：
“第X-Y周,星期N,第A节-第B节”
多个周次段时：
“第1-3周,第5-16周,星期N,第A节-第B节”

【严格识别规则】

1. 节次必须严格依据课表网格判断：
- 以左侧节次编号（1、2、3……）和课程块实际占据的纵向格数为准。
- 一个课程块跨越几行，就代表跨越几节课。
- 不允许仅根据课程文字位置推断节次。

2. 课程块高度优先级最高：
- 如果课程块从第1节区域开始并明显覆盖到第2节区域，则输出：
  startSection=1,endSection=2
- 如果覆盖第3~4节，则输出：
  startSection=3,endSection=4
- 即使文字只显示在上半部分，也必须按整个课程块实际高度判断。

3. 禁止单节误判：
- 在高校课表中，大多数课程连续 2 节或更多。
- 若视觉上明显跨越多个节次，禁止输出单节。
- 只有课程块明确只占一行时，才能输出单节。

4. rawTime 必须与节次完全一致：
- rawTime 中的节次必须严格等于 startSection 和 endSection。
- 例如：
  startSection=1,endSection=2
  则 rawTime 必须是：
  “第2-9周,星期1,第1节-第2节”
- 严禁出现：
  startSection=1,endSection=2
  rawTime="第1节-第1节"

5. 周次规则：
- “第2-9周”展开为 [2,3,4,5,6,7,8,9]
- “第1-3周,第5-16周”展开为完整数组。
- 单周如“第8周”输出 [8]

6. 拆分规则：
- 同一门课在不同周次、不同地点或不同节次分成多个卡片时，输出多个对象。
- 例如：
  第1-8周在A教室，第9-16周在B教室 → 两个对象。

7. 完整性规则：
- 图片边缘只露出部分课程卡片、无法确认完整时间或地点时，不要输出。

8. 冲突处理：
- 当文本位置与视觉网格冲突时，一律以课表网格占格范围为准。

JSON 示例：
[
  {
    "name":"计算方法[05]",
    "teacher":"宫成春",
    "position":"南岭-逸夫楼-A206",
    "day":2,
    "startSection":7,
    "endSection":8,
    "weeks":[1,2,3,5,6,7,8,9,10,11,12,13,14,15,16],
    "rawTime":"第1-3周,第5-16周,星期2,第7节-第8节"
  }
]
9. 节次异常修正规则（强制执行）：
- rawTime 中禁止出现：
  “第X节-第X节”
  这种开始节次与结束节次完全相同的情况（例如“第1节-第1节”、“第5节-第5节”），除非课程块在视觉上明确只占一个节次格。

- 如果模型无法确认结束节次，并错误生成了：
  “第X节-第X节”
  则默认修正为连续两节：
  “第X节-第(X+1)节”

例如：
“第1节-第1节” → 修正为 “第1节-第2节”
“第3节-第3节” → 修正为 “第3节-第4节”
“第7节-第7节” → 修正为 “第7节-第8节”

同时必须同步修正：
startSection = X
endSection = X + 1

rawTime、startSection、endSection 三者必须严格一致。
""";
    }

    private String dataUrl(MultipartFile image) throws IOException {
        String contentType = image.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/jpeg";
        }
        contentType = contentType.split(";")[0].trim();
        byte[] bytes = image.getBytes();
        return "data:%s;base64,%s".formatted(contentType, Base64.getEncoder().encodeToString(bytes));
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

    @SuppressWarnings("unchecked")
    private String extractContent(String responseBody) throws IOException {
        Object parsed = objectMapper.readValue(responseBody, Object.class);
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "AI 响应格式错误");
        }
        Object choicesValue = root.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "AI 响应缺少 choices");
        }
        Object firstChoice = choices.getFirst();
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "AI 响应 choice 格式错误");
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "AI 响应缺少 message");
        }
        Object contentValue = message.get("content");
        if (!(contentValue instanceof String content) || content.isBlank()) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "AI 响应内容为空");
        }
        return content;
    }

    private String normalizeJsonArray(String content) throws IOException {
        String json = stripMarkdown(content.trim());
        if (!json.startsWith("[")) {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }
        Object parsed = objectMapper.readValue(json, Object.class);
        if (!(parsed instanceof List<?>)) {
            throw new BusinessException(ErrorCode.COURSE_PARSE_FAILED, "AI 未返回 JSON 数组");
        }
        return objectMapper.writeValueAsString(parsed);
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

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty() || images.stream().allMatch(MultipartFile::isEmpty)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传课表图片");
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多上传 " + MAX_IMAGES + " 张图片");
        }
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能为空");
            }
            if (image.getSize() > MAX_IMAGE_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "单张图片不能超过 10MB");
            }
            String contentType = image.getContentType();
            if (contentType != null && !contentType.isBlank() && !contentType.startsWith("image/")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "只支持图片文件");
            }
        }
    }
}
