# Clark AAMS Backend 类与方法功能梳理

本文档按当前项目源码梳理后端类、记录类型、接口、枚举、测试类和主要静态页面脚本函数。项目未发现实际的 `widget` 或 `widgets` Java/Dart 包；仓库中仅在 `flutter-ocr-pickup-code-development.md` 的 Flutter 示例里出现 `StatefulWidget` 和 `Widget build`。因此本文在最后单独说明 `widget/widgets` 搜索结果，并更详细梳理静态页面脚本和该 Flutter Widget 示例。

## 根包

### `ClarkAamsBackendApplication`

Spring Boot 启动类。

- `main(String[] args)`：调用 `SpringApplication.run` 启动整个后端应用，加载 Spring 容器、控制器、服务、配置和静态资源映射。

### `WebSupport.java`

该文件合并了 Web 入口跳转、健康检查、CORS 和请求日志能力。

#### `PageController`

页面入口控制器，把短路径重定向到静态页面。

- `admin()`：将 `/admin`、`/admin/` 重定向到 `/admin/index.html`。
- `api()`：将 `/api`、`/api/`、`/api.html` 重定向到 `/api/index.html`。
- `user()`：将 `/user`、`/my` 等用户入口重定向到 `/user/index.html`。
- `login()`：将 `/login` 重定向到 `/auth/login.html`。
- `register()`：将 `/register` 重定向到 `/auth/register.html`。
- `recognize()`：将 `/recognize` 重定向到用户页的 `#recognize` 锚点。
- `favicon()`：把浏览器默认请求的 `/favicon.ico` 转发到 `/site/favicon.ico`。

#### `HealthController`

健康检查控制器。

- 构造器：从 `app.version` 配置读取应用版本，默认 `1.0.0`。
- `health()`：返回统一 `ApiResponse`，包含 `status=UP` 和当前版本号。

#### `CorsConfig`

CORS 配置类。

- `webMvcConfigurer()`：注册 `WebMvcConfigurer` Bean，允许 `/api/**` 跨域访问，支持 GET/POST/PUT/DELETE/OPTIONS、任意请求头和 Cookie。
- 匿名类 `addCorsMappings(CorsRegistry registry)`：实际写入跨域规则，设置预检缓存时间 3600 秒。

#### `RequestLogFilter`

一次请求一次执行的日志过滤器。

- `doFilterInternal(...)`：记录请求开始时间，继续执行过滤器链，最后输出 HTTP 方法、URI、响应状态码和耗时。

## common 包

### `ApiResponse<T>`

统一 API 响应结构，字段为 `code`、`message`、`data`。

- `ok(T data)`：构造成功响应，错误码为 `ErrorCode.OK`。
- `fail(ErrorCode errorCode)`：使用错误码默认消息构造失败响应。
- `fail(ErrorCode errorCode, String message)`：使用指定消息构造失败响应。

### `BusinessException`

业务异常类型，携带项目内部 `ErrorCode`。

- `BusinessException(ErrorCode errorCode)`：使用错误码默认文案作为异常消息。
- `BusinessException(ErrorCode errorCode, String message)`：使用自定义业务错误消息。
- `BusinessException(ErrorCode errorCode, String message, Throwable cause)`：保留底层异常原因，适合 IO、AI、教务请求等失败场景。
- `errorCode()`：返回该异常对应的业务错误码。

### `ErrorCode`

业务错误码枚举。

- 枚举值：`OK`、`BAD_REQUEST`、`LOGIN_INVALID`、`SCHOOL_NOT_FOUND`、`IMPORTER_NOT_FOUND`、`USER_NOT_FOUND`、`USER_EMAIL_CONFLICT`、`ACADEMIC_REQUEST_FAILED`、`COURSE_PARSE_FAILED`、`INTERNAL_ERROR`、`AI_REQUEST_FAILED`。
- `code()`：返回整数错误码。
- `defaultMessage()`：返回默认错误消息。

### `GlobalExceptionHandler`

全局异常处理器。

- `handleBusinessException(BusinessException exception)`：把业务异常转换为统一 JSON 响应，并通过 `statusFor` 映射 HTTP 状态码。
- `handleBadRequest(Exception exception)`：处理请求体不可读、参数校验失败、非法参数等情况，统一返回 400。
- `handleClientAbort(ClientAbortException exception)`：客户端提前断开时只记录 debug 日志，避免污染错误日志。
- `handleUnexpected(Exception exception)`：兜底处理未捕获异常，记录错误日志并返回 500。
- `statusFor(ErrorCode errorCode)`：把业务错误码映射为 HTTP 状态码。

## admin 包

### `AdminAuthController`

后台管理员认证接口，路径前缀 `/api/v1/admin/auth`。

- 构造器：注入 `AdminCredentialStore`。
- `setupStatus()`：返回后台是否已配置管理员、是否允许首次注册。
- `register(AdminRegisterRequest request, HttpServletRequest httpRequest)`：首次注册管理员账号，注册成功后在 Session 中写入后台登录标记。
- `login(AdminLoginRequest request, HttpServletRequest httpRequest)`：校验管理员账号密码，成功后写 Session，失败返回 401。
- `logout(HttpServletRequest request)`：注销后台 Session，返回 `authenticated=false`。
- `AdminLoginRequest`：登录请求记录，字段为 `username`、`password`。

### `AdminAuthFilter`

后台页面和后台 API 的访问控制过滤器。

- 构造器：注入 `AdminCredentialStore`，用于判断后台是否已经初始化。
- `doFilter(...)`：核心过滤逻辑；未初始化时引导注册，未登录访问后台 API 返回 JSON 401，访问后台页面重定向到登录页。
- `requiresAuthentication(String path)`：判断路径是否属于后台保护范围。
- `isAdminApi(String path)`：判断路径是否是后台管理 API，排除认证 API。
- `requiresSetupRedirect(String path)`：判断未初始化后台时是否需要跳转注册页。
- `isAuthenticated(HttpServletRequest request)`：从 Session 读取后台登录标记。
- `normalizePath(HttpServletRequest request)`：去掉 context path，得到应用内部路径。
- `encodeRedirect(HttpServletRequest request)`：编码原始目标地址，供登录后跳回。
- `writeUnauthorizedJson(HttpServletResponse response)`：向后台 API 返回固定的未登录 JSON 响应。

### `AdminCredentialStore`

管理员凭据存储与校验组件。支持环境变量配置，也支持首次注册后写入 `admin-config.json`。

- 构造器：注入 JSON Mapper、配置文件路径、外部用户名和外部密码。
- `initialize()`：启动时解析配置文件路径；如果文件存在则读取持久化管理员凭据。
- `configured()`：判断是否已有外部凭据或本地配置凭据。
- `registrationAvailable()`：判断是否允许首次注册。
- `authenticate(String username, String password)`：按外部凭据优先、本地凭据其次的顺序校验登录。
- `register(String username, String password)`：校验首次注册条件，生成盐值和 PBKDF2 哈希，写入配置文件。
- `configPath()`：返回当前管理员配置文件路径。
- `externalConfigured()`：判断环境或配置项中的管理员用户名密码是否完整。
- `storedConfigured()`：判断本地读取的管理员配置是否完整。
- `readConfig(Path path)`：从 JSON 文件反序列化管理员凭据。
- `writeConfig(Path path, StoredAdminCredential credential)`：创建目录并格式化写入管理员凭据。
- `resolveConfigPath()`：按显式配置、jar 同级目录、工作目录的优先级确定配置文件位置。
- `codeSourcePath()`：解析当前应用代码来源路径，用于 jar 同级配置。
- `verifyPassword(String password, String salt, String expectedHash)`：使用盐重新哈希输入密码并常量时间比较。
- `hashPassword(String password, byte[] salt)`：使用 `PBKDF2WithHmacSHA256` 生成密码哈希。
- `constantTimeEquals(String left, String right)`：避免普通字符串比较带来的时序侧信道。
- `normalize(String value)`：空值转空串并 trim。
- `StoredAdminCredential`：内部持久化记录，字段为 `username`、`salt`、`passwordHash`。

### `AdminRegisterRequest`

首次注册请求记录，字段为 `username`、`password`。

### `AdminSetupStatusDto`

后台初始化状态记录，字段为 `configured`、`registrationAvailable`。

### `SchoolScaffoldController`

后台学校适配器脚手架接口。

- 构造器：注入 `SchoolScaffoldService`。
- `scaffoldSchool(SchoolScaffoldRequest request)`：根据请求生成学校配置、导入器、客户端、解析器四类 Java 文件。

### `SchoolScaffoldRequest`

学校脚手架请求记录，包含学校 ID、类名前缀、学校名称、登录 URL、成功 URL 规则、功能开关、版本号和是否覆盖已有文件。

### `SchoolScaffoldResponse`

学校脚手架响应记录，返回学校 ID、包名、类名前缀和已创建文件列表。

### `SchoolScaffoldService`

学校适配器代码生成服务。

- `scaffold(SchoolScaffoldRequest request)`：规范化请求，校验目标路径不越界，生成并写入四个代码文件。
- `normalize(SchoolScaffoldRequest request)`：校验学校 ID、类名前缀、学校名称、登录 URL、成功 URL 规则，并填充默认值。
- `schoolConfigTemplate(SchoolScaffoldData data)`：生成 `SchoolConfigProvider` 实现类源码。
- `importerTemplate(SchoolScaffoldData data)`：生成 `SchoolCourseImporter` 实现类源码。
- `clientTemplate(SchoolScaffoldData data)`：生成教务请求客户端源码骨架。
- `parserTemplate(SchoolScaffoldData data)`：生成课表解析器源码骨架。
- `javaStringArrayItems(List<String> values)`：把字符串列表转成 Java 字符串数组字面量片段。
- `javaString(String value)`：转义反斜杠、引号和换行，生成 Java 字符串字面量。
- `toClassPrefix(String schoolId)`：把下划线学校 ID 转成驼峰类名前缀。
- `requireText(String value, String message)`：校验必填文本。
- `safeTrim(String value)`：空值安全 trim。
- `GeneratedFile`：内部记录生成文件名和内容。
- `SchoolScaffoldData`：内部规范化后的学校脚手架数据。

## ai 包

### `AiConfigAdminController`

后台 AI 配置接口，路径前缀 `/api/v1/admin/ai`。

- `getConfig()`：读取 AI Base URL、模型名和 API Key 是否已配置。
- `updateConfig(AiConfigUpdateRequest request)`：校验并保存 AI 配置。
- `validate(AiConfigUpdateRequest request)`：要求请求不为空、Base URL 和模型名不为空。
- `blank(String value)`：判断空白字符串。

### `AiConfigDto`

AI 配置展示记录，字段为 `baseUrl`、`model`、`apiKeyConfigured`。不会把真实 API Key 返回给前端。

### `AiConfigStore`

AI 配置持久化组件，默认写入 `ai-config.json`。

- `initialize()`：启动时解析路径；已有文件则读取，没有则写入空配置。
- `current()`：返回脱敏配置。
- `runtimeConfig()`：返回包含真实 API Key 的运行时配置，供 AI 调用服务使用。
- `save(AiConfigUpdateRequest request)`：保存配置；支持保留、更新或清空 API Key。
- `configPath()`：返回配置文件路径。
- `resolveConfigPath()`：确定 AI 配置文件位置。
- `codeSourcePath()`：解析 jar 或 classpath 来源路径。
- `readConfig(Path path)`：读取 JSON 配置。
- `writeConfig(Path path, AiStoredConfig value)`：写入 JSON 配置。
- `normalize(AiStoredConfig value)`：规范化存储记录。
- `normalize(String value)`：空值安全 trim。
- `AiStoredConfig`：内部持久化记录，字段为 `baseUrl`、`apiKey`、`model`。

### `AiConfigUpdateRequest`

AI 配置更新请求，字段为 `baseUrl`、`apiKey`、`model`、`clearApiKey`。

### `AiRuntimeConfig`

包内运行时 AI 配置记录。

- `ready()`：判断 Base URL、API Key、模型名是否全部非空。
- `blank(String value)`：判断空白字符串。

### `CourseImageRecognitionController`

课表图片识别 API，路径 `/api/v1/courses/recognize`。

- `recognize(List<MultipartFile> images)`：接收 multipart 图片，调用识别服务并直接返回 AI 归一化后的 JSON 数组文本。

### `CourseImageRecognitionService`

调用 OpenAI-compatible `chat/completions` 视觉模型识别课表。

- 构造器：注入 AI 配置、JSON Mapper，并创建带连接超时的 `HttpClient`。
- `recognize(List<MultipartFile> images)`：校验图片、读取 AI 配置、构造请求、调用模型、提取响应内容并归一化为 JSON 数组。
- `buildRequest(AiRuntimeConfig config, List<MultipartFile> images)`：构造多模态 chat completions 请求体。
- `prompt()`：返回课表识别系统提示词，要求模型输出指定字段的纯 JSON 数组。
- `dataUrl(MultipartFile image)`：把上传图片转为 `data:image/...;base64,...`。
- `chatCompletionsUri(String baseUrl)`：把 Base URL 规范化为 `/chat/completions` 端点。
- `extractContent(String responseBody)`：从模型响应的 `choices[0].message.content` 提取文本。
- `normalizeJsonArray(String content)`：去掉 Markdown 代码块，截取并解析 JSON 数组，最后重新序列化。
- `stripMarkdown(String value)`：剥离三反引号代码块。
- `validateImages(List<MultipartFile> images)`：限制必须上传图片、最多 8 张、单张不超过 10MB、类型为 image。

### `QuestionAnalysisController`

题目 AI 解析接口，路径 `/api/v1/questions/analyze`，需要用户登录。

- `analyze(Map<String,Object> request, HttpServletRequest httpRequest)`：校验用户身份，调用题目解析服务，成功后递增用户解析次数。
- `requireCurrentUserId(HttpServletRequest request)`：优先从 Session 获取用户 ID，不存在时尝试通过登录码 Header 恢复。
- `restoreUserIdFromLoginCodeHeader(HttpServletRequest request)`：读取 `X-Clark-Aams-Login-Code`，查到用户后写回 Session。
- `currentUserId(HttpServletRequest request)`：从 Session 中读取 `CLARK_AAMS_USER_ID`，兼容 Long 和 Integer。
- `header(HttpServletRequest request, String name)`：读取并 trim 请求头。

### `QuestionAnalysisService`

调用文本模型分析题目。

- `analyze(Map<String,Object> request)`：兼容多种字段名提取题型、题干、选项；校验必填项；调用 AI；返回模型 JSON 或降级错误结构。
- `buildRequest(AiRuntimeConfig config, Map<String,Object> userPrompt)`：构造 system+user 两条消息的 chat completions 请求。
- `extractContent(String responseBody)`：从 AI 响应中提取 `message.content`。
- `parseAiContent(String content)`：剥离 Markdown、提取 JSON 对象、解析为 Map；失败时返回 `error` 和原始内容。
- `stripMarkdown(String value)`：去除代码块包装。
- `extractJsonObject(String value)`：当模型输出含解释文本时截取第一个 JSON 对象。
- `stringifyKeys(Map<?,?> map)`：把 Map 键统一转成字符串。
- `stringifyNestedKeys(Object value)`：递归处理嵌套 Map/List 的键。
- `chatCompletionsUri(String baseUrl)`：规范化 AI 请求 URL。
- `firstPresent(Map<String,Object> request, String... keys)`：按候选字段名读取第一个存在的值。
- `blank(Object value)`：判断对象是否为空白。

## appversion 包

### `AppVersionAdminController`

后台客户端版本配置接口。

- `getConfig()`：读取当前版本配置。
- `updateConfig(AppVersionUpdateRequest request)`：校验后保存版本配置。
- `validate(AppVersionUpdateRequest request)`：校验版本号、构建号和最低支持版本合法。
- `normalize(String value)`：空值安全 trim。

### `AppVersionConfigDto`

版本配置记录，包含最新版本、最新构建号、最低支持版本、更新标题、更新文案、发布页、发布说明和下载源。

### `AppVersionConfigStore`

客户端版本配置持久化组件，默认写入 `client-version.json`。

- `initialize()`：启动时读取或创建配置文件；读取旧文件后尝试修复乱码并回写。
- `current()`：返回当前配置快照。
- `save(AppVersionUpdateRequest request)`：规范化请求，应用到 `AppVersionProperties`，并持久化。
- `configPath()`：返回配置文件路径。
- `resolveConfigPath()`：确定配置文件位置。
- `codeSourcePath()`：解析应用运行来源。
- `readConfig(Path path)`：读取 JSON 配置。
- `writeConfig(Path path, AppVersionConfigDto config)`：格式化写入 JSON 配置。
- `snapshot()`：从 `AppVersionProperties` 构造 DTO。
- `normalize(AppVersionUpdateRequest request)`：把更新请求转为规范 DTO。
- `apply(AppVersionConfigDto config)`：把 DTO 写回运行时属性对象。
- `normalizeNotes(List<String> values)`：清洗发布说明，过滤空项。
- `normalizeDownloadSources(List<DownloadSourceDto> values)`：清洗下载源，过滤无 URL 项。
- `repairMojibake(AppVersionConfigDto config)`：修复配置文件中疑似乱码字段。
- `repairTextList(List<String> values)`：修复发布说明列表。
- `repairDownloadSources(List<DownloadSourceDto> values)`：修复下载源的 label 和 description。
- `repairText(String value)`：把疑似 ISO-8859-1 误读文本转回 UTF-8。
- `looksMojibake(String value)`：按特征字符判断是否疑似乱码。
- `toPropertyDownloadSources(List<DownloadSourceDto> values)`：把 DTO 转成配置属性内部类。
- `normalize(String value)`：空值安全 trim。

### `AppVersionController`

客户端版本检查公开接口。

- `checkByQuery(...)`：支持 GET Query 参数方式检查版本。
- `checkByBody(VersionCheckRequest request)`：支持 POST JSON 请求体方式检查版本。

### `AppVersionProperties`

绑定 `clark-aams.client-version` 配置的属性类。

- Getter/Setter：读写最新版本、最新构建号、最低支持版本、标题、可选更新文案、强制更新文案、发布页、发布说明和下载源。
- `DownloadSource` 内部类：表示一个下载源；包含 `type`、`label`、`url`、`primary`、`description` 的 Getter/Setter。

### `AppVersionService`

版本检查业务服务。

- `check(VersionCheckRequest request)`：校验请求，计算更新类型，组装 `VersionCheckResponse`。
- `resolveUpdateType(String currentVersion, Integer currentBuild)`：根据构建号和语义版本同时判断是否无需更新、可选更新或强制更新。
- `normalizeOrDefault(String value, String fallback)`：空值时返回默认值。
- `normalize(String value)`：空值安全 trim。

### `AppVersionUpdateRequest`

后台更新版本配置请求记录。

### `DownloadSourceDto`

下载源 DTO，字段为 `type`、`label`、`url`、`primary`、`description`。

### `SemanticVersion`

内部语义版本比较工具。

- `parse(String value)`：支持去掉 `v` 前缀、忽略预发布和 build metadata，把版本拆成数字段。
- `compareTo(SemanticVersion other)`：逐段比较版本号，不足三段补 0。

### `UpdateType`

更新类型枚举。

- 枚举值：`NONE`、`OPTIONAL`、`REQUIRED`。
- `value()`：返回接口使用的小写字符串。

### `VersionCheckRequest`

版本检查请求记录，字段为平台、当前版本、当前构建号、渠道和扩展字段。

### `VersionCheckResponse`

版本检查响应记录，包含当前版本、最新版本、最低支持版本、更新类型、更新文案、下载源和检查时间。

## school 包

### `LessonTimeDto`

课程节次时间记录，字段为 `lesson` 和 `startTime`。

### `SchoolCapabilityDto`

学校能力记录，字段为 `academicImport` 和 `imageRecognition`。

### `SchoolController`

学校公开接口，路径前缀 `/api/v1/schools`。

- `listSchools()`：返回启用的学校列表。
- `getSchool(String schoolId)`：返回指定学校配置。
- `getSchoolLessonTimes(String schoolId)`：返回指定学校使用的节次时间表。
- `listLessonTimeProfiles()`：返回系统内置节次时间模板。

### `SchoolDto`

学校接口 DTO，包含学校 ID、名称、简称、启用状态、能力、登录配置、节次模板和版本号。

### `SchoolEntity`

学校内部实体记录。

- 主构造器：包含 `lessonTimeProfile`。
- 兼容构造器：不传 `lessonTimeProfile` 时默认使用 `general-a`。

### `SchoolLessonTimesDto`

学校节次时间响应记录，字段为 `schoolId`、`profile`、`lessons`。

### `SchoolLoginDto`

学校登录方式记录，字段为 `mode`、`loginUrl`、`successUrlPatterns`。

### `SchoolService`

学校配置聚合服务。

- 构造器：收集所有 `SchoolConfigProvider`，按学校 ID 建立只读映射。
- `listEnabledSchools()`：过滤启用学校并转为 DTO。
- `getSchool(String schoolId)`：按 ID 查学校，不存在抛 `SCHOOL_NOT_FOUND`。
- `getLessonTimes(String schoolId)`：返回学校对应节次模板，未知模板回退到默认模板。
- `listLessonTimeProfiles()`：返回全部内置节次模板。
- `toDto(SchoolEntity entity)`：把内部实体转成接口 DTO。
- `normalize(String schoolId)`：学校 ID 小写 trim。
- `normalizeProfile(String profile)`：节次模板名规范化，空值转默认模板。
- `resolveProfile(String profile)`：未知模板回退到默认模板。
- `lessons(String... startTimes)`：生成 1 到 13 节课的开始时间列表。
- `startTime(String[] startTimes, int lesson)`：按节次取开始时间，超出已配置范围返回空串。

## importers 包

### `CourseImportController`

课表导入公开接口。

- `importCourses(String schoolId, ImportCourseRequest request)`：按学校 ID 调用对应导入器，返回统一课表数据。

### `CourseImportService`

课表导入服务。

- `importCourses(String schoolId, ImportCourseRequest request)`：校验学校 ID 和请求体，委托 `ImporterRegistry` 找到导入器执行。

### `ImporterRegistry`

学校导入器注册表。

- 构造器：收集所有 `SchoolCourseImporter`，按规范化学校 ID 建立映射。
- `get(String schoolId)`：返回指定学校导入器，不存在抛 `IMPORTER_NOT_FOUND`。
- `normalize(String schoolId)`：学校 ID 小写 trim。

### `SchoolCourseImporter`

学校课表导入接口。

- `schoolId()`：返回导入器负责的学校 ID。
- `importCourses(ImportCourseRequest request)`：执行课表导入并返回统一响应。

### `SchoolConfigProvider`

学校配置提供接口。

- `school()`：返回一个学校的 `SchoolEntity` 配置。

### DTO 记录

- `CourseDto`：统一课程 DTO，字段包括课程名、起止周、星期、起止节、地点、老师、周次数组和原始时间文本。
- `ImportCourseRequest`：导入请求，字段为 `loginSession`。
- `ImportCourseResponse`：导入响应，字段为 `schoolId`、`importerVersion`、`courses`、`warnings`。
- `LoginSessionDto`：登录态传输对象，字段为 `cookie`、`studentId`、`successUrl`、`extra`。

## importers.schools.jit 包

### `JitClient`

金陵科技学院教务系统请求客户端。

- 构造器：创建支持重定向、连接超时 8 秒的 `HttpClient`。
- `fetchCoursePage(LoginSessionDto loginSession)`：优先读取 `extra.html/pageHtml/tableHtml`，否则用 Cookie 和候选 URL 请求教务系统，直到找到课表表格。
- `fetch(URI uri, String cookie)`：带 Cookie、User-Agent、Accept、Referer 请求指定 URL，非 2xx 抛业务异常。
- `buildCandidateUris(LoginSessionDto loginSession)`：根据成功 URL、学号等构造候选教务页面地址。
- `safeJitUri(String url)`：只接受 `https://jwxt.jit.edu.cn` 下的 URL，避免 SSRF 风险。
- `readExtraHtml(LoginSessionDto loginSession)`：从额外字段中读取调试或客户端直接传入的 HTML。
- `safeTrim(String value)`：空值安全 trim。

### `JitCourseImporter`

JIT 课表导入器。

- 构造器：创建 `JitClient`。
- `schoolId()`：返回 `jit`。
- `importCourses(ImportCourseRequest request)`：校验登录态，拉取 HTML，调用解析器解析，再映射为统一 `CourseDto` 列表。

### `JitJwxtParser`

JIT 教务课表 HTML 解析器。

- `pageHasTable6(String html)`：判断页面是否包含课表表格。
- `parseDay(String text)`：把星期文本转换为 1 到 7。
- `parseWeeks(String weekText)`：解析周次范围、单周/双周和单个周次，返回展开后的周次数组。
- `parseTimeText(String timeText)`：解析节次范围和周次信息，返回 `TimeInfo`。
- `parseIndexChartsTable(String html)`：定位 `table#Table6`，逐行解析课程块并去重。
- `parseCourseTd(Element td, int day)`：解析一个单元格中的多个课程块。
- `selectTable(String html)`：按完整 CSS 路径、`table#Table6`、`.class-table table` 的顺序选择课表。
- `textOf(Element element)`：提取元素文本并 trim。
- `firstNonNull(Element... elements)`：返回第一个非空元素，用于兼容教师字段的多个 class 名。
- `TimeInfo`：解析后的起止节次和周次数组。
- `JitRawCourse`：JIT 原始课程记录，字段包括课程名、老师、地点、星期、起止节、周次和原始时间。

### `JitSchoolConfig`

JIT 学校配置。

- `school()`：返回 JIT 学校实体，配置学校名称、登录地址、登录成功 URL 规则、导入器 key、启用状态和版本号。

## pickup 包

### `CoursePickupAdminController`

后台取件码管理接口。

- `list()`：返回所有取件码摘要，按创建时间倒序。
- `get(String code)`：查看某个取件码完整记录。
- `createManual(CoursePickupUpdateRequest request)`：后台手动创建一个仅包含 JSON 的取件码。
- `updateJson(String code, CoursePickupUpdateRequest request)`：修改取件码对应的课程 JSON。
- `delete(String code)`：删除取件码记录和图片目录。
- `deleteImage(String code, String fileName)`：删除取件码中的某张图片。
- `image(String code, String fileName)`：读取并返回取件码图片资源。

### `CoursePickupController`

公开取件码接口。

- `create(List<MultipartFile> images)`：上传图片识别课表，并创建取件码。
- `get(String code)`：按取件码直接返回课程 JSON 数组文本。

### `CoursePickupCreateResponse`

创建取件码响应，字段为 `pickupCode`、`courses`。

### `CoursePickupImageDto`

取件码图片记录，字段为文件名、原始文件名、内容类型和大小。

### `CoursePickupRecordDto`

取件码完整记录，字段为编码、JSON 文本、图片列表、创建时间和更新时间。

### `CoursePickupService`

取件码创建服务。

- `recognizeAndCreate(List<MultipartFile> images)`：调用图片识别服务得到 JSON，保存取件码，并返回取件码和解析后的课程对象。
- `parseCourses(String jsonText)`：把 JSON 文本解析成对象供响应使用。

### `CoursePickupStore`

取件码本地文件存储组件。

- `initialize()`：创建存储目录和图片目录，读取或初始化 `index.json`。
- `create(String jsonText, List<MultipartFile> images)`：校验 JSON 数组，生成唯一取件码，保存图片，写入索引。
- `find(String code)`：按取件码查记录。
- `list()`：返回摘要列表。
- `updateJson(String code, String jsonText)`：校验并更新课程 JSON，同时更新时间。
- `delete(String code)`：删除记录和图片目录。
- `deleteImage(String code, String fileName)`：删除单张图片并更新记录。
- `imagePath(String code, String fileName)`：返回图片路径，同时校验文件属于记录且路径不越界。
- `storageDir()`：返回存储根目录。
- `requireRecord(String code)`：查不到记录时抛业务异常。
- `saveImages(String code, List<MultipartFile> images)`：保存上传图片并生成图片 DTO。
- `readIndex()`：读取 `index.json` 并修复缺省字段。
- `writeIndex()`：把记录列表格式化写回索引。
- `validateJsonArray(String jsonText)`：确保取件内容是合法 JSON 数组。
- `nextCode()`：生成 8 位无歧义字符取件码，最多重试 100 次。
- `resolveStorageDir()`：确定存储根目录。
- `imageDir(String code)`：返回某个取件码的图片目录。
- `normalizeCode(String code)`：取件码大写 trim。
- `safeFileName(String value)`：只接受安全文件名，防止路径穿越。
- `extension(String originalName, String contentType)`：从原始文件名或 MIME 类型推断扩展名。
- `normalizeImages(List<CoursePickupImageDto> images)`：过滤无效图片记录。
- `now()`：生成 ISO_OFFSET_DATE_TIME 时间戳。
- `deleteDirectory(Path path)`：递归删除目录。

### `CoursePickupSummaryDto`

取件码摘要记录，字段为 `code`、`imageCount`、`createdAt`、`updatedAt`。

### `CoursePickupUpdateRequest`

取件码 JSON 更新请求，字段为 `jsonText`。

## user 包

### `EmailCodeSender`

邮箱验证码发送服务，支持 SMTP 和 HTTP Provider。

- `sendVerificationCode(String email, String code)`：读取运行时邮件配置；未启用则只记日志，启用后按 Provider 类型发送。
- `sendBySmtp(StoredEmailConfig config, String email, String code)`：构造 JavaMailSender，通过 SMTP 发送 HTML+文本邮件。
- `sendByHttpProvider(StoredEmailConfig config, String email, String code)`：向 HTTP Provider POST 邮件发送 payload，支持 Bearer API Key。
- `isSmtpProvider(StoredEmailConfig config)`：根据 provider 和 endpoint 判断是否走 SMTP。
- `parseSmtpEndpoint(String rawEndpoint)`：解析 SMTP 地址、端口和 SSL/TLS 模式。
- `subject(StoredEmailConfig config)`：返回邮件主题，未配置则使用默认主题。
- `displaySenderName(StoredEmailConfig config)`：返回发件人显示名。
- `textBody(String code)`：生成纯文本验证码邮件内容。
- `htmlBody(StoredEmailConfig config, String email, String code)`：使用模板替换验证码、邮箱、主题和发件人名称。
- `normalize(String value)`：空值安全 trim。
- `isBlank(String value)`：判断空白字符串。
- `SmtpEndpoint`：内部记录，字段为 `host`、`port`、`ssl`。

### `EmailConfigAdminController`

后台邮箱配置接口。

- `getConfig()`：返回脱敏邮箱配置。
- `updateConfig(EmailConfigUpdateRequest request)`：保存邮箱配置。

### `EmailConfigDto`

邮箱配置展示 DTO，不暴露真实 API Key。

### `EmailConfigStore`

邮箱配置持久化组件，默认写入 `email-config.json`。

- `initialize()`：启动时读取或创建邮箱配置文件。
- `current()`：返回脱敏配置。
- `runtimeConfig()`：返回包含 API Key 的运行时配置。
- `save(EmailConfigUpdateRequest request)`：保存配置，支持更新、保留或清空 API Key。
- `configPath()`：返回配置文件路径。
- `readConfig(Path path)`：读取 JSON 配置。
- `writeConfig(Path path, StoredEmailConfig value)`：写入 JSON 配置。
- `normalize(StoredEmailConfig value)`：规范化存储配置。
- `resolveConfigPath()`：确定配置文件位置。
- `codeSourcePath()`：解析应用运行来源。
- `normalize(String value)`：空值安全 trim。
- `normalizeTemplate(String value)`：模板为空时使用默认 HTML 模板。
- `defaultHtmlTemplate()`：返回默认验证码 HTML 模板。
- `StoredEmailConfig`：持久化记录，包含启用状态、provider、endpoint、发件人、主题、模板和 API Key。

### `EmailConfigUpdateRequest`

邮箱配置更新请求，字段包括启用状态、provider、endpoint、发件人、主题、模板、API Key 和清空 API Key 标记。

### `SQLiteUserRepository`

SQLite 用户仓库实现。

- `initialize()`：确定数据库路径，创建父目录，初始化 JDBC URL 和表结构。
- `nextId()`：查询最大 ID 并加 1，生成新用户 ID。
- `save(UserRecord user)`：插入或更新用户记录。
- `findById(long id)`：按 ID 查询用户。
- `findByUsernameAndPassword(String username, String passwordHash)`：按用户名和密码哈希查询用户。
- `findByLoginCode(String loginCode)`：按登录码查询用户。
- `findByEmail(String email)`：按邮箱查询用户。
- `findAll()`：按 ID 升序返回所有用户。
- `incrementQuestionAnalysisCount(long id)`：题目解析次数加 1，并返回更新后的用户。
- `findByUsername(String username)`：按用户名查询第一个用户；当前不在接口中声明，是扩展方法。
- `updateEmail(long id, String email, boolean bound, boolean verified, String verifyCode)`：更新用户邮箱绑定状态；当前不在接口中声明，是扩展方法。
- `initSchema()`：创建 users 表，并补齐 `question_analysis_count` 字段。
- `ensureColumn(Connection connection, String column, String definition)`：表结构升级时检查并添加缺失列。
- `queryOne(String sql, Object value)`：执行单参数查询。
- `readOne(ResultSet resultSet)`：读取结果集第一行。
- `map(ResultSet resultSet)`：把数据库行映射为 `UserRecord`。
- `bind(PreparedStatement statement, UserRecord user)`：把用户字段绑定到 SQL 参数。
- `connection()`：创建 SQLite 连接。
- `resolveDbPath()`：确定数据库文件位置。
- `codeSourcePath()`：解析 jar 或 classpath 来源路径。

### `UserAccountService`

用户账号业务服务。

- `register(UserRegisterRequest request)`：校验用户名、密码、QQ 邮箱，检查邮箱冲突，生成用户 ID、密码哈希和登录码。
- `login(UserLoginRequest request)`：支持登录码登录、QQ 邮箱+密码登录、用户名+密码登录。
- `getById(long id)`：按 ID 获取用户资料。
- `getByLoginCode(String loginCode)`：按登录码获取用户资料。
- `requestEmailCode(long id, String email)`：生成邮箱验证码并保存到用户记录，返回用户资料。
- `requestEmailChallenge(long id, String email)`：生成邮箱验证码并把验证码和用户资料一起返回，供 Controller 发送邮件。
- `confirmEmail(long id, String code)`：校验验证码，成功后设置邮箱已绑定、已验证并清空验证码。
- `incrementQuestionAnalysisCount(long id)`：递增题目解析次数。
- `toProfile(UserRecord record)`：把仓库记录转换为对外用户资料。
- `generateUniqueLoginCode()`：生成不重复的 12 位登录码。
- `generateEmailCode()`：生成 6 位数字邮箱验证码。
- `randomCode(int length, String alphabet)`：从指定字符表生成随机码。
- `hashPassword(String password)`：使用 SHA-256 计算用户密码哈希。
- `constantTimeEquals(String left, String right)`：常量时间比较哈希。
- `normalize(String value)`：空值安全 trim。
- `normalizeEmail(String value)`：邮箱 trim 后转小写。
- `EmailChallenge`：内部响应记录，字段为用户资料和验证码。

### `UserController`

用户公开接口，路径前缀 `/api/v1/user`。

- `register(UserRegisterRequest request, HttpServletRequest httpRequest)`：注册用户并写入登录 Session。
- `login(UserLoginRequest request, HttpServletRequest httpRequest)`：登录用户并写入 Session。
- `logout(HttpServletRequest request)`：移除用户 Session 登录标记。
- `me(HttpServletRequest request)`：返回当前用户；未登录时返回空用户结构。
- `requestEmailCode(UserEmailCodeRequest request, HttpServletRequest httpRequest)`：要求已登录，生成并发送邮箱验证码。
- `verifyEmail(UserEmailVerifyRequest request, HttpServletRequest httpRequest)`：要求已登录，校验邮箱验证码。
- `currentUser(HttpServletRequest request)`：从 Session 或登录码 Header 还原当前用户。
- `restoreUserIdFromLoginCodeHeader(HttpServletRequest request)`：读取登录码 Header 并恢复 Session。
- `currentUserId(HttpServletRequest request)`：从 Session 读取当前用户 ID。
- `header(HttpServletRequest request, String name)`：读取请求头。
- `requireCurrentUserId(HttpServletRequest request)`：要求已登录，否则抛 `LOGIN_INVALID`。

### 用户 DTO 和仓库接口

- `UserEmailCodeRequest`：邮箱验证码请求，字段为 `qqEmail`。
- `UserEmailVerifyRequest`：邮箱验证请求，字段为 `code`。
- `UserLoginRequest`：登录请求，字段为 `loginCode`、`qqEmail`、`username`、`password`。
- `UserLoginResponse`：登录响应，包含用户资料、登录码和题目解析次数。
- `UserProfileDto`：用户资料 DTO。
- `UserRegisterRequest`：注册请求，字段为 `username`、`password`、`qqEmail`。
- `UserRepository`：用户仓库接口，声明 `nextId`、`save`、`findById`、`findByUsernameAndPassword`、`findByLoginCode`、`findByEmail`、`findAll`、`incrementQuestionAnalysisCount`。
- `UserRepository.UserRecord`：仓库内部用户记录。

## 测试类

### `AdminFirstRunTests`

首次后台初始化流程测试。

- `firstAdminVisitRedirectsToRegistrationUntilAdminIsCreated()`：验证未创建管理员时访问 `/admin` 会跳注册页，注册后同一 Session 可访问后台入口。
- `PropertiesInitializer.initialize(...)`：为测试设置临时 admin 配置、版本配置和取件码目录，避免污染真实运行文件。

### `ApiContractTests`

主要 API 契约测试。

- 学校相关测试：验证学校列表、节次时间、节次模板接口。
- 健康检查测试：验证 `/api/v1/health`。
- 题目解析测试：验证未登录拦截、缺少题干报错、登录码 Header 恢复 Session 和解析次数递增。
- 用户登录测试：验证 QQ 邮箱+密码和登录码登录。
- 静态资源与页面入口测试：验证 favicon、service worker、用户入口和后台入口重定向。
- 后台认证测试：验证后台登录、错误密码、后台 API 登录保护。
- 版本配置测试：验证后台版本配置可更新并写入文件。
- 导入器测试：用内联 JIT HTML 验证课表解析。
- 版本检查测试：验证可选更新、无需更新、强制更新和版本低于最低版本的情况。
- 取件码测试：验证存储的课程 JSON 可按取件码返回。
- `loginAsAdmin()`：测试辅助方法，登录后台并返回 Session。
- `registerAsUser()`：测试辅助方法，注册用户并返回 Session。
- `registerUser()`：测试辅助方法，注册随机邮箱用户并返回登录码和 Session。
- `RegisteredUser`：测试内部记录。
- `QuestionAnalysisTestConfig.questionAnalysisService(...)`：注册测试替身服务，避免测试真实 AI 调用。
- 匿名替身 `analyze(Map<String,Object> request)`：校验题干和选项，返回固定答案 `A`。

### `ClarkAamsBackendApplicationTests`

- `contextLoads()`：验证 Spring 上下文可以正常启动。

## 静态页面脚本功能

### `static/admin/ai.html`

后台 AI 配置和图片识别调试页。

- `loadConfig()`：请求后台 AI 配置并填充表单。
- `saveConfig(event)`：提交 Base URL、模型名和 API Key 更新。
- `recognize(event)`：上传图片到识别接口，展示识别结果 JSON。
- `setStatus(element, message, error, ok)`：统一更新状态提示样式。

### `static/admin/index.html`

后台首页。

- `load()`：聚合加载后台初始化状态、版本配置、AI 配置等关键状态并渲染入口信息。

### `static/admin/login.html`

后台登录页。

- `redirectToRegisterWhenNeeded()`：检查后台是否允许首次注册，若未初始化则跳转注册页。

### `static/admin/pickups.html`

后台取件码管理页。

- `requestJson(url, options)`：封装 JSON 请求，统一处理 API 响应和异常。
- `loadList()`：加载取件码摘要列表。
- `renderList()`：把取件码摘要渲染成列表。
- `loadDetail(code)`：加载指定取件码详情。
- `createManualRecord(event)`：从手工 JSON 创建取件码。
- `renderDetail(record)`：渲染取件码 JSON、图片和操作按钮。
- `saveJson(code)`：保存编辑后的课程 JSON。
- `deleteRecord(code)`：删除取件码。
- `deleteImage(code, fileName)`：删除取件码图片。
- `parseJsonResponse(response)`：解析后端 JSON 响应并处理错误。
- `formatJson(value)`：格式化 JSON 文本。
- `setStatus(element, message, error)`：显示操作状态。
- `escapeHtml(value)`：防止 HTML 注入。
- `escapeAttr(value)`：防止属性注入。

### `static/admin/register.html`

后台首次注册页。

- `ensureRegistrationAvailable()`：检查是否仍允许注册；若已配置管理员则跳登录页或后台页。

### `static/admin/schools.html`

后台学校管理/脚手架页。

- `loadSchools()`：加载现有学校列表并渲染。
- `setStatus(message, error)`：显示脚手架生成或加载状态。
- `escapeHtml(value)`：渲染学校信息时转义 HTML。

### `static/admin/versions.html`

后台版本配置页。

- `loadConfig()`：读取当前版本配置。
- `saveConfig(event)`：保存版本号、构建号、更新文案、发布说明和下载源。
- `runCheck()`：用表单输入模拟客户端版本检查。
- `fillForm(config)`：把配置写入表单控件。
- `readForm()`：从表单读取并组织配置对象。
- `renderSources()`：渲染下载源编辑列表。
- `syncSource(index, input)`：同步某个下载源字段的编辑值。
- `renderResult(data)`：渲染版本检查结果。
- `setStatus(element, message, error)`：显示页面状态。
- `escapeHtml(value)`：转义 HTML。
- `escapeAttr(value)`：转义属性值。

### `static/auth/login.html`

用户登录页。

- `safeRedirect(value)`：校验跳转目标，仅允许站内安全路径。
- `withLoginCodeRedirect(url, loginCode)`：把登录码拼接到跳转 URL，便于客户端或页面恢复登录态。

### `static/auth/register.html`

用户注册页。

- `safeRedirect(value)`：校验注册后的站内跳转目标。
- `withLoginCodeRedirect(url, loginCode)`：注册成功后把登录码附加到跳转地址。

### `static/index.html`

项目首页。

- `setUserEntryTarget(target)`：根据当前登录状态或入口目标设置用户入口链接。

### `static/user/index.html`

用户中心与课表识别页面，是静态页面里交互最集中的部分。

- `showView(view)`：切换用户页内部视图。
- `requestJson(url, options)`：统一 API 请求封装，自动处理登录码 Header、JSON 解析和业务错误。
- `captureLoginCodeFromUrl()`：从 URL 查询参数中读取登录码。
- `rememberLoginCode(user)`：把用户登录码存入本地存储。
- `restoreSessionWithLoginCode()`：用本地登录码请求用户信息，恢复 Session。
- `updateTopStatus(user)`：更新页面顶部的登录/用户状态。
- `updateEmailBindStrip(user)`：根据邮箱绑定状态显示提醒条。
- `showAuth()`：展示登录注册入口。
- `showProfile(user, source)`：展示用户资料、登录码、邮箱状态和解析次数。
- `loadProfile()`：加载当前用户资料。
- `uploadWithProgress(formData)`：用 XHR 上传图片并跟踪进度。
- `renderSelectedFiles()`：渲染已选择的图片文件列表。
- `setToolState(message, code, hint)`：更新课表识别工具状态、取件码和提示。
- `setProgress(value)`：更新上传进度条。
- `resetProgress()`：重置上传进度。
- `showPickupModal(code)`：展示取件码弹窗。
- `closePickupModal()`：关闭取件码弹窗。
- `copyText(value)`：复制文本到剪贴板，兼容 Clipboard API 和旧方案。
- `copyPickupCode()`：复制当前取件码。
- `escapeHtml(value)`：转义 HTML。
- `qqAvatarUrl(email)`：根据 QQ 邮箱生成 QQ 头像地址。

### `static/sw.js`

Service Worker 清理脚本。当前无显式函数声明，主要作用是让浏览器拿到一个可更新的 SW 文件，便于清理或替换旧缓存策略。

## widget / widgets 重点说明

### 搜索结论

- 当前仓库没有 `src/**/widget` 或 `src/**/widgets` 目录。
- 当前后端 Java 包中也没有名为 `widget` 或 `widgets` 的包。
- `widget` 关键字只出现在 `src/main/resources/static/docs/flutter-ocr-pickup-code-development.md` 的 Flutter 示例代码中。

### `flutter-ocr-pickup-code-development.md` 中的 `_PickupCodeDialog`

该示例是 Flutter 客户端接入取件码功能时的弹窗组件。

- `_PickupCodeDialog extends StatefulWidget`：表示一个有状态弹窗组件，通常用于展示识别成功后的取件码，并支持复制、关闭等交互。
- `Widget build(BuildContext context)`：构建弹窗 UI。它会根据组件状态渲染取件码、说明文本和操作按钮，是 Flutter Widget 树的入口方法。

### 与 Widget 体验最相关的后端/静态页面功能

虽然本仓库不是 Flutter 客户端项目，但以下模块直接服务于 Flutter Widget 或 Web UI：

- `CoursePickupController.create` 和 `CoursePickupService.recognizeAndCreate`：支撑“上传课表图片 -> 生成取件码”的 Widget/页面流程。
- `CoursePickupController.get`：支撑 Flutter 客户端用取件码拉取课程 JSON。
- `CoursePickupStore`：保证取件码、课程 JSON 和图片文件持久化。
- `QuestionAnalysisController.analyze`：支撑做题辅助类 Widget 的登录校验和 AI 解析。
- `UserController`：支撑登录、注册、登录码恢复 Session、邮箱验证码等用户中心 Widget。
- `SchoolController`：支撑学校选择器、节次时间配置选择器等客户端组件。
- `static/user/index.html`：是 Web 端最接近 Widget 组合的页面，包含视图切换、上传进度、取件码弹窗、登录码恢复、用户状态展示等交互组件。

如果后续把 Flutter 客户端源码也放进同一仓库，建议把 Flutter 的 `lib/widgets/` 单独追加一章，逐个说明每个 Widget 的状态字段、构造参数、`build` 树结构、回调和后端接口依赖。
