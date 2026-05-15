# 学校适配器开发文档

后端按“一个学校一个包”的方式组织适配代码。所有学校包都放在：

```text
src/main/java/io/github/tissyboxc/clark_aams_backend/importers/schools/
```

例如 JIT：

```text
importers/schools/jit/
  JitSchoolConfig.java
  JitCourseImporter.java
  JitClient.java
  JitJwxtParser.java
```

## 生成学校包

启动服务后打开：

```text
http://localhost:8080/admin/schools.html
```

填写学校 ID、名称、简称、登录地址、登录成功 URL 规则后提交。生成器会创建：

```text
{Prefix}SchoolConfig.java
{Prefix}CourseImporter.java
{Prefix}Client.java
{Prefix}Parser.java
```

默认不会覆盖已有文件。勾选“覆盖文件”会重写同名文件，已经手写过逻辑时不要使用。

## 包内职责

### `{Prefix}SchoolConfig`

负责提供学校配置，实现：

```java
SchoolConfigProvider
```

后端启动时会自动扫描所有 `SchoolConfigProvider`，因此新增学校配置类后，不需要修改 `SchoolService`。

关键字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 学校 ID，同时也是 importer 的 `schoolId` |
| `name` | 学校中文名 |
| `shortName` | 学校简称 |
| `loginUrl` | 前端 WebView 打开的登录页 |
| `successUrlPatterns` | 前端判断登录成功的 URL 规则 |
| `version` | 学校配置版本 |

### `{Prefix}CourseImporter`

负责实现统一导入入口：

```java
public interface SchoolCourseImporter {
    String schoolId();
    ImportCourseResponse importCourses(ImportCourseRequest request);
}
```

`schoolId()` 必须返回学校 ID。`ImporterRegistry` 会按这个值分发：

```http
POST /api/v1/imports/{schoolId}/courses
```

通常流程：

1. 校验 `request.loginSession()`。
2. 调用 `{Prefix}Client` 获取课表 HTML 或 JSON。
3. 调用 `{Prefix}Parser` 转成 `List<CourseDto>`。
4. 返回 `ImportCourseResponse`。

### `{Prefix}Client`

负责请求学校教务系统。

输入登录态：

```java
LoginSessionDto(
    String cookie,
    String studentId,
    String successUrl,
    Map<String, Object> extra
)
```

建议：

- 使用 `cookie` 时不要写入日志。
- 请求超时控制在 30 秒以内。
- 只允许访问目标学校域名，避免把接口做成任意 URL 代理。
- 可以支持 `extra.html` 作为本地测试入口。

### `{Prefix}Parser`

负责把学校课表页面转成统一课程模型：

```java
new CourseDto(
    name,
    startWeek,
    endWeek,
    dayOfWeek,
    startLesson,
    endLesson,
    location,
    teacher,
    weeks,
    rawTime
)
```

字段要求：

| 字段 | 说明 |
| --- | --- |
| `name` | 课程名称，不能为空 |
| `startWeek` / `endWeek` | 起止周 |
| `dayOfWeek` | 1 到 7，周一到周日 |
| `startLesson` / `endLesson` | 起止节次 |
| `location` | 上课地点 |
| `teacher` | 任课教师，可为空 |
| `weeks` | 精确周次，单双周等场景优先填 |
| `rawTime` | 教务系统原始时间文本 |

返回 JSON 必须兼容 Flutter `Course.fromJson`。

## 开发步骤

1. 用管理页生成学校包。
2. 确认 `{Prefix}SchoolConfig` 中的登录地址和成功 URL。
3. 在 `{Prefix}Client` 中实现教务系统请求。
4. 保存一份脱敏 HTML/JSON 样例。
5. 在 `{Prefix}Parser` 中实现解析。
6. 给该学校新增解析测试。
7. 调用 `POST /api/v1/imports/{schoolId}/courses` 验证响应。
8. 运行 `.\gradlew.bat test`。

## 测试建议

解析器测试优先使用脱敏后的 HTML/JSON 样本，不依赖真实教务系统。

请求示例：

```json
{
  "loginSession": {
    "cookie": "ASP.NET_SessionId=xxx",
    "studentId": "2023000000",
    "successUrl": "https://example.edu.cn/main",
    "extra": {}
  }
}
```

兼容说明：旧客户端如果继续提交 `options` 字段，后端会忽略该字段；当前导入逻辑只读取 `loginSession`。

本地解析测试可以传：

```json
{
  "loginSession": {
    "extra": {
      "html": "<html>脱敏课表样例</html>"
    }
  }
}
```

## 安全约束

- 不长期保存用户 cookie。
- 不在日志中输出完整 cookie。
- 不把后端接口做成任意 URL 抓取器。
- 生产环境必须使用 HTTPS。
- 教务系统请求必须有超时。
- 对解析失败和登录过期使用明确错误码。

常用错误码：

| code | 说明 |
| --- | --- |
| `40001` | 请求参数错误 |
| `40101` | 登录态无效或已过期 |
| `40401` | 学校不存在 |
| `40402` | 学校未配置导入器 |
| `50001` | 教务系统请求失败 |
| `50002` | 课表解析失败 |
