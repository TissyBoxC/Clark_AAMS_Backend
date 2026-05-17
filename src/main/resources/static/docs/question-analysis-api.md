# 题目 AI 解析接口开发文档

本文档说明前端如何调用后端题目 AI 解析接口。该接口接收题型、题干和选项，后端读取现有 `ai-config.json` 配置，调用 OpenAI 兼容的 `/chat/completions` 接口，并返回选项分析、答案和学习提示。

## 接口信息

| 项目 | 内容 |
| --- | --- |
| 请求方法 | `POST` |
| 接口路径 | `/api/v1/questions/analyze` |
| Content-Type | `application/json` |
| 是否需要用户登录 | 是，需要携带登录后的 `JSESSIONID` Cookie |
| 响应格式 | 统一 `ApiResponse` JSON |

完整 URL 示例：

```text
http://localhost:8080/api/v1/questions/analyze
```

## 请求参数

接口支持 `snake_case` 和 `camelCase` 两种字段名，方便不同前端风格调用。

| 字段 | 兼容字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `question_type` | `questionType` / `type` | string | 否 | 题目类型，例如 `单选题`、`多选题`、`判断题` |
| `question_content` | `questionContent` / `content` | string | 是 | 题干内容 |
| `options` | `optionList` / `choices` | array/object | 是 | 题目选项，推荐传数组并带上 `A.`、`B.` 前缀 |

## 请求示例

```json
{
  "question_type": "单选题",
  "question_content": "下列关于 Java 接口的说法正确的是？",
  "options": [
    "A. 接口可以直接实例化",
    "B. 接口中的方法默认都是 private",
    "C. 类可以实现多个接口",
    "D. 接口不能包含常量"
  ]
}
```

也可以使用 camelCase：

```json
{
  "questionType": "单选题",
  "questionContent": "下列关于 Java 接口的说法正确的是？",
  "options": [
    "A. 接口可以直接实例化",
    "B. 接口中的方法默认都是 private",
    "C. 类可以实现多个接口",
    "D. 接口不能包含常量"
  ]
}
```

## 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "question_type": "单选题",
    "knowledge_points": [
      "Java 接口",
      "多实现"
    ],
    "thinking_steps": [
      "判断接口是否可以直接实例化",
      "分析 Java 类和接口的关系",
      "结合接口常量和方法默认规则排除错误选项"
    ],
    "option_analysis": {
      "A": "错误，接口不能直接实例化。",
      "B": "错误，接口中的抽象方法默认是 public abstract。",
      "C": "正确，Java 类可以实现多个接口。",
      "D": "错误，接口可以包含 public static final 常量。"
    },
    "answer": "C",
    "study_hint": "重点区分类的单继承和接口的多实现规则。"
  }
}
```

成功解析后，后端会把当前用户的 `questionAnalysisCount` 加 `1`。前端可通过 `GET /api/v1/user/me` 获取最新次数。

## 未登录响应

```json
{
  "code": 40101,
  "message": "请先登录后再使用做题辅助",
  "data": null
}
```

## 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | number | 业务状态码，`0` 表示成功 |
| `message` | string | 响应消息 |
| `data.question_type` | string | AI 识别或复述的题目类型 |
| `data.knowledge_points` | string[] | 涉及的知识点 |
| `data.thinking_steps` | string[] | 解题思路 |
| `data.option_analysis` | object | 每个选项的分析 |
| `data.answer` | string | 最终答案选项，例如 `A`、`C`、`AC` |
| `data.study_hint` | string | 学习提示 |

## 错误响应

缺少题干：

```json
{
  "code": 40001,
  "message": "question_content is required",
  "data": null
}
```

缺少选项：

```json
{
  "code": 40001,
  "message": "options is required",
  "data": null
}
```

AI 配置未完成：

```json
{
  "code": 40001,
  "message": "AI config is incomplete",
  "data": null
}
```

AI 调用失败：

```json
{
  "code": 50004,
  "message": "AI request failed",
  "data": null
}
```

如果 AI 返回的内容不是合法 JSON，接口仍会返回 `code = 0`，但 `data` 中会包含错误说明和原始内容：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "error": "AI returned invalid JSON",
    "raw_content": "AI 原始返回内容"
  }
}
```

## 前端调用示例

```dart
final response = await http.post(
  BackendConfig.uri('/api/v1/questions/analyze'),
  headers: const {'Content-Type': 'application/json'},
  body: jsonEncode({
    'question_type': '单选题',
    'question_content': questionText,
    'options': options,
  }),
);

final decoded = jsonDecode(utf8.decode(response.bodyBytes));
if (decoded is! Map || decoded['code'] != 0) {
  throw Exception(decoded is Map ? decoded['message'] : '题目解析失败');
}

final result = decoded['data'] as Map;
final answer = result['answer'] as String? ?? '';
final optionAnalysis = result['option_analysis'] as Map? ?? {};
```

## 后端 AI 配置

接口复用后端现有 `ai-config.json`：

```json
{
  "baseUrl": "https://api.chshapi.cn/v1",
  "apiKey": "你的 API Key",
  "model": "gpt-5.5"
}
```

`baseUrl` 可以配置为：

```text
https://api.example.com/v1
```

后端会自动拼接为：

```text
https://api.example.com/v1/chat/completions
```

如果 `baseUrl` 已经以 `/chat/completions` 结尾，后端会直接使用该地址。

## 调试命令

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/questions/analyze" `
  -H "Content-Type: application/json" `
  -d "{\"question_type\":\"单选题\",\"question_content\":\"下列关于 Java 接口的说法正确的是？\",\"options\":[\"A. 接口可以直接实例化\",\"B. 接口中的方法默认都是 private\",\"C. 类可以实现多个接口\",\"D. 接口不能包含常量\"]}"
```
