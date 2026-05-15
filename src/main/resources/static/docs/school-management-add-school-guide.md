# 后台学校管理新增学校适配文档

本文以现有 `jit` 适配为例，说明如何在后台“学校管理”中新增学校，并把生成的脚手架补全为可用的教务系统课表导入适配器。

## 目标

新增一个学校后，Flutter 客户端可以通过后端拿到学校列表，并在该学校支持教务系统导入时使用 WebView 登录教务系统，登录成功后把 Cookie、学号和成功页 URL 交给后端。后端再请求学校课表页面，解析为统一课程 JSON。

完整链路：

```text
后台生成学校适配器
  -> 后端 /api/v1/schools 返回新学校
  -> Flutter 选择学校
  -> Flutter WebView 打开 loginUrl
  -> 命中 successUrlPatterns 后提交 loginSession
  -> 后端 SchoolCourseImporter 拉取课表页并解析
  -> 返回 ImportCourseResponse.courses
```

## JIT 示例文件

现有金陵科技学院适配位于：

```text
src/main/java/io/github/tissyboxc/clark_aams_backend/importers/schools/jit/
  JitSchoolConfig.java
  JitCourseImporter.java
  JitClient.java
  JitJwxtParser.java
```

对应职责：

| 文件 | 职责 |
| --- | --- |
| `JitSchoolConfig.java` | 声明学校基础信息、登录地址、登录成功 URL 规则、能力开关 |
| `JitCourseImporter.java` | 实现 `SchoolCourseImporter`，接收导入请求并返回统一课程 |
| `JitClient.java` | 使用 Cookie、学号、成功页 URL 请求学校教务课表页面 |
| `JitJwxtParser.java` | 解析教务 HTML，把学校原始课表结构转换为课程字段 |

## 后台表单字段说明

访问：

```text
/admin/schools.html
```

点击学校脚手架生成区域，按下面字段填写。

| 字段 | 示例 | 说明 |
| --- | --- | --- |
| 学校 ID | `jit` | 后端和 Flutter 使用的学校唯一 ID。只能小写字母、数字、下划线，并以字母开头 |
| 类名前缀 | `Jit` | Java 类名前缀，会生成 `JitSchoolConfig`、`JitCourseImporter` 等 |
| 学校名称 | `金陵科技学院` | Flutter 展示用完整名称 |
| 学校简称 | `JIT` | Flutter 学校列表头像/短名 |
| 启用 | `true` | 是否出现在 `/api/v1/schools` 中 |
| 教务导入 | `true` | 是否允许 Flutter 使用教务系统导入 |
| 图像识别 | `false` | 学校级图像识别能力开关，当前 OCR 是通用接口，这里可先保持 false |
| 登录模式 | `webview` | 当前 Flutter 已支持 WebView 登录 |
| 登录地址 | `https://jwxt.jit.edu.cn/default2.aspx` | Flutter WebView 首次打开的 URL |
| 登录成功 URL 规则 | `https://jwxt.jit.edu.cn/xs_main.aspx*` | Flutter 判断登录成功的 URL pattern，支持 `*` 通配 |
| 版本 | `1` | 学校配置版本，后续配置变化可递增 |
| 覆盖已有文件 | `false` | 首次生成保持 false，确认覆盖时才开启 |

JIT 对应配置在 `JitSchoolConfig.java`：

```java
return new SchoolEntity(
        "jit",
        "金陵科技学院",
        "JIT",
        true,
        true,
        false,
        "webview",
        "https://jwxt.jit.edu.cn/default2.aspx",
        new String[]{"https://jwxt.jit.edu.cn/xs_main.aspx*"},
        "jit",
        1
);
```

## 生成后的文件

假设新增学校：

```text
schoolId: njupt
classPrefix: Njupt
```

会生成：

```text
src/main/java/io/github/tissyboxc/clark_aams_backend/importers/schools/njupt/
  NjuptSchoolConfig.java
  NjuptCourseImporter.java
  NjuptClient.java
  NjuptParser.java
```

生成后能编译，但 `Client` 和 `Parser` 默认是 TODO，需要按学校教务系统补全。

## 必须补全的核心代码

### 1. SchoolConfig

一般只需要检查字段是否正确。

重点字段：

```java
loginUrl
successUrlPatterns
importerKey
```

`importerKey` 通常与 `schoolId` 一致。`SchoolCourseImporter.schoolId()` 也必须返回同一个值。

### 2. Client

职责是拿到课表原始页面。

Flutter 提交的请求体结构：

```json
{
  "loginSession": {
    "cookie": "JSESSIONID=xxx",
    "studentId": "20240001",
    "successUrl": "https://example.edu.cn/main",
    "extra": {}
  }
}
```

JIT 的 `JitClient` 做了这些事：

1. 如果 `loginSession.extra.html` 存在，直接返回该 HTML，方便测试。
2. 校验 Cookie 不为空。
3. 根据 `successUrl`、`studentId` 拼多个候选课表 URL。
4. 带上 Cookie 请求教务系统。
5. 判断页面中是否存在课表表格。

新增学校时建议保留这个测试入口：

```java
if (loginSession.extra() != null) {
    Object html = loginSession.extra().get("html");
    if (html instanceof String value && !value.isBlank()) {
        return value;
    }
}
```

这样可以在不真实登录学校系统的情况下，用测试 HTML 覆盖 Parser。

### 3. Parser

职责是把学校课表 HTML/JSON 转成 `CourseDto`。

统一字段：

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

字段规则：

| 字段 | 说明 |
| --- | --- |
| `name` | 课程名，不能为空 |
| `startWeek` | 起始周，可由 `weeks` 首项推导 |
| `endWeek` | 结束周，可由 `weeks` 末项推导 |
| `dayOfWeek` | 星期一到星期日为 1 到 7 |
| `startLesson` | 开始节次 |
| `endLesson` | 结束节次 |
| `location` | 上课地点 |
| `teacher` | 教师，没有则空字符串 |
| `weeks` | 展开后的周次数组，例如 `[1,2,3,5,6]` |
| `rawTime` | 原始时间文本，便于排查 |

JIT 的 Parser 使用 `jsoup`：

```java
Document document = Jsoup.parse(html);
Element table = document.selectFirst("table#Table6");
```

如果新学校页面也是 HTML，优先用 CSS selector 定位；不要用脆弱的字符串截取。

### 4. CourseImporter

生成的 `CourseImporter` 通常只需要少量调整。

标准流程：

```java
String html = client.fetchCoursePage(request.loginSession());
List<CourseDto> courses = XxxParser.parseCourses(html);
return new ImportCourseResponse(schoolId(), 1, courses, List.of());
```

如果学校接口返回 JSON，也可以让 `Client` 返回 JSON 字符串，Parser 再解析 JSON。

## 登录成功 URL 如何确定

1. 打开学校教务登录页。
2. 登录成功后观察地址栏。
3. 找一个稳定特征，写成 pattern。

JIT 示例：

```text
登录页：https://jwxt.jit.edu.cn/default2.aspx
成功页：https://jwxt.jit.edu.cn/xs_main.aspx?xh=...
规则：https://jwxt.jit.edu.cn/xs_main.aspx*
```

如果成功后进入多个页面，可填写多个规则。

## 如何验证新增学校

### 1. 学校列表

```http
GET /api/v1/schools
```

确认新学校出现在列表里：

```json
{
  "id": "njupt",
  "name": "南京邮电大学",
  "shortName": "NJUPT",
  "capabilities": {
    "academicImport": true,
    "imageRecognition": false
  },
  "login": {
    "mode": "webview",
    "loginUrl": "https://example.edu.cn/login",
    "successUrlPatterns": ["https://example.edu.cn/main*"]
  },
  "version": 1
}
```

### 2. 单学校配置

```http
GET /api/v1/schools/{schoolId}
```

示例：

```http
GET /api/v1/schools/jit
```

### 3. 用 HTML 测 Parser

可以直接提交 HTML 到 `loginSession.extra.html`：

```http
POST /api/v1/imports/{schoolId}/courses
Content-Type: application/json
```

```json
{
  "loginSession": {
    "cookie": "",
    "studentId": "",
    "successUrl": "",
    "extra": {
      "html": "<html>课表页面 HTML</html>"
    }
  }
}
```

如果 Parser 正确，应返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "schoolId": "njupt",
    "importerVersion": 1,
    "courses": []
  }
}
```

### 4. 用 Flutter 真机验证

1. 后端启动。
2. Flutter 选择新增学校。
3. WebView 打开 `loginUrl`。
4. 登录成功后命中 `successUrlPatterns`。
5. Flutter 提交 Cookie 到后端。
6. 后端返回课程，客户端进入导入确认流程。

## 测试建议

建议为每个学校至少加两个测试：

1. Parser 单元测试：给固定 HTML，断言课程名称、星期、节次、周次、地点。
2. 接口测试：通过 `extra.html` 调用 `/api/v1/imports/{schoolId}/courses`，断言统一响应结构。

可参考现有 `ApiContractTests.importCoursesParsesJitTable6Html`。

## 常见问题

### 学校列表没有新学校

检查：

- `SchoolConfig` 是否有 `@Component`。
- `enabled` 是否为 `true`。
- 包路径是否在 Spring Boot 扫描范围内。

### Flutter 登录成功后没有触发导入

检查：

- `successUrlPatterns` 是否匹配真实成功 URL。
- URL 是否 http/https、域名、路径都正确。
- 规则末尾是否需要 `*`。

### 后端提示缺少 Cookie

检查：

- Flutter WebView 是否拿到了当前域名 Cookie。
- 学校系统是否使用多个域名，Cookie 是否在另一个域名下。
- 登录成功页和课表页是否同域。

### Parser 返回空课程

检查：

- 课表页面是否是 iframe 或需要另一个接口加载。
- 是否需要先请求菜单页再请求课表页。
- HTML selector 是否匹配真实页面。
- 页面编码是否正确。

### 教务系统返回 403/302

检查：

- Cookie 是否完整。
- 是否需要 Referer。
- 是否需要 User-Agent。
- 是否需要携带学号参数。
- 是否需要先访问首页建立服务端会话。

## 新增学校开发清单

- [ ] 在后台生成学校脚手架。
- [ ] 确认 `SchoolConfig` 中学校 ID、登录地址、成功 URL 规则正确。
- [ ] 实现 `Client.fetchCoursePage`。
- [ ] 实现 `Parser.parseCourses`。
- [ ] 保留 `extra.html` 测试入口。
- [ ] 新增 Parser 测试。
- [ ] 新增接口测试。
- [ ] 通过 `/api/v1/schools` 验证学校配置。
- [ ] 通过 `/api/v1/imports/{schoolId}/courses` 验证课程导入。
- [ ] 用 Flutter 真机完成登录导入闭环。

