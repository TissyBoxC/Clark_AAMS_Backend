# Flutter 接入 Clark AAMS Backend 开发文档

本文档给 Flutter 侧开发使用，目标是把当前本地课表 App 接入 Spring Boot 后端，实现学校列表远程配置、教务系统课表导入、App 版本检查和更新提示。

## 开发背景

当前项目是「珂拉课程表 / Clark AAMS」。

最初 Flutter 侧已经具备：

- 首次启动选择学校。
- 手动添加、编辑、删除、批量删除课程。
- 手动粘贴 JSON 批量导入课程。
- 导入课表后选择本学期第一周第一天。
- 首页根据系统时间自动定位当前周，并高亮当天。
- 每门课程可自定义颜色。
- 本地保存课程与设置。
- 已内置金陵科技学院 JIT 教务系统导入逻辑。

后端化目标：

- 学校列表从后端实时获取。
- 新增或修复学校教务导入逻辑时，尽量只更新后端。
- Flutter 保留通用 WebView 登录、cookie/session 读取、课程展示和本地保存能力。
- 后端负责学校适配器、教务请求、HTML/JSON 解析和统一课程 JSON 输出。
- 后端额外提供 App 版本控制能力，支持可选更新和必须更新。

后端已实现：

- `GET /api/v1/schools`
- `GET /api/v1/schools/{schoolId}`
- `POST /api/v1/imports/{schoolId}/courses`
- `GET /api/v1/app/version`
- `POST /api/v1/app/version/check`
- `GET /api/v1/health`
- 学校适配器脚手架管理页：`/admin/schools.html`
- 学校适配器开发文档：`/docs/school-importer-development.md`

## 通用响应结构

所有业务接口统一返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

错误示例：

```json
{
  "code": 40101,
  "message": "登录状态已过期，请重新登录",
  "data": null
}
```

常用错误码：

| code | 说明 |
| --- | --- |
| `0` | 成功 |
| `40001` | 请求参数错误 |
| `40101` | 登录态无效或已过期 |
| `40401` | 学校不存在 |
| `40402` | 学校未配置导入器 |
| `50001` | 教务系统请求失败 |
| `50002` | 课表解析失败 |
| `50003` | 后端内部错误 |

## 1. 学校列表

### 请求

```http
GET /api/v1/schools
```

### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": "jit",
      "name": "金陵科技学院",
      "shortName": "JIT",
      "enabled": true,
      "capabilities": {
        "academicImport": true,
        "imageRecognition": false
      },
      "login": {
        "mode": "webview",
        "loginUrl": "https://jwxt.jit.edu.cn/default2.aspx",
        "successUrlPatterns": [
          "https://jwxt.jit.edu.cn/xs_main.aspx*"
        ]
      },
      "version": 1
    }
  ]
}
```

### Flutter 侧建议

- 启动时请求学校列表。
- 请求失败时使用本地内置 JIT 配置兜底。
- `capabilities.academicImport == true` 时显示“教务导入”入口。
- `login.mode == webview` 时使用通用 WebView 登录页打开 `login.loginUrl`。

## 2. 单个学校配置

### 请求

```http
GET /api/v1/schools/{schoolId}
```

示例：

```http
GET /api/v1/schools/jit
```

### 响应

响应结构与学校列表中的单项一致。

## 3. 教务系统课程导入

### 请求

```http
POST /api/v1/imports/{schoolId}/courses
Content-Type: application/json
```

请求体：

```json
{
  "loginSession": {
    "cookie": "ASP.NET_SessionId=xxx; other=yyy",
    "studentId": "2023000000",
    "successUrl": "https://jwxt.jit.edu.cn/xs_main.aspx?xh=2023000000",
    "extra": {}
  }
}
```

兼容说明：旧客户端如果继续提交 `options` 字段，后端会忽略该字段；当前导入逻辑只读取 `loginSession`。

### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "schoolId": "jit",
    "importerVersion": 1,
    "courses": [
      {
        "name": "高等数学",
        "startWeek": 1,
        "endWeek": 18,
        "dayOfWeek": 1,
        "startLesson": 1,
        "endLesson": 2,
        "location": "A101",
        "teacher": "张三",
        "weeks": [1, 2, 3, 4],
        "rawTime": "1-2节 (1-4周)"
      }
    ],
    "warnings": []
  }
}
```

### Flutter 侧建议

- WebView 登录成功后读取 cookie。
- 尽量从 URL 或页面中提取 `studentId`，如 JIT 的 `xh`。
- 把 `cookie`、`studentId`、`successUrl` 发给后端。
- 后端返回的 `courses[]` 可直接交给当前 `Course.fromJson`。
- 后端不返回 `colorValue`，课程颜色仍由 Flutter 本地流程处理。
- 导入成功后继续使用现有“选择第一周第一天”和保存逻辑。

## 4. App 版本检查

版本检查用于控制 Flutter App 更新，支持三种状态：

| updateType | 说明 |
| --- | --- |
| `none` | 当前版本已是最新或无需更新 |
| `optional` | 有新版本，可选更新 |
| `required` | 当前版本低于最低可用版本，必须更新 |

后端优先使用 `currentBuild` 判断；如果没有 build number，则使用 `currentVersion` 做语义化版本比较。

### GET 请求

```http
GET /api/v1/app/version?platform=android&currentVersion=1.0.0&currentBuild=1&channel=stable
```

### POST 请求

```http
POST /api/v1/app/version/check
Content-Type: application/json
```

```json
{
  "platform": "android",
  "currentVersion": "1.0.0",
  "currentBuild": 1,
  "channel": "stable",
  "extra": {}
}
```

### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "platform": "android",
    "channel": "stable",
    "currentVersion": "1.0.0",
    "currentBuild": 1,
    "latestVersion": "1.1.0",
    "latestBuild": 2,
    "minimumSupportedVersion": "1.0.0",
    "minimumSupportedBuild": 1,
    "updateType": "optional",
    "updateAvailable": true,
    "forceUpdate": false,
    "title": "发现新版本",
    "message": "有新版本可用，建议更新后继续使用。",
    "releaseNotes": [
      "后端已支持学校列表、教务导入和版本检查。"
    ],
    "downloadSources": [
      {
        "type": "gitee",
        "label": "Gitee 下载",
        "url": "https://gitee.com/your-org/clark-aams/releases",
        "primary": true,
        "description": "推荐国内网络使用"
      },
      {
        "type": "git",
        "label": "Git 仓库",
        "url": "https://gitee.com/your-org/clark-aams.git",
        "primary": false,
        "description": "开发者可从仓库拉取源码"
      }
    ],
    "releasePageUrl": "https://gitee.com/your-org/clark-aams/releases",
    "checkedAt": "2026-05-15T04:00:00+08:00"
  }
}
```

### Flutter 侧更新逻辑

1. App 启动后或进入首页后调用版本检查接口。
2. 从 `package_info_plus` 获取当前 `version` 和 `buildNumber`。
3. `updateType == none`：不提示。
4. `updateType == optional`：弹出普通更新弹窗，允许“稍后再说”。
5. `updateType == required`：弹出不可关闭的强制更新弹窗，不允许继续使用旧版本。
6. 优先打开 `downloadSources` 中 `primary == true` 的地址。
7. 如果 Gitee 下载不可用，可以展示其他下载源，如 `git`。

建议 Flutter 引入：

```yaml
dependencies:
  package_info_plus: ^8.0.0
  url_launcher: ^6.3.0
```

伪代码：

```dart
final info = await PackageInfo.fromPlatform();
final response = await api.checkVersion(
  platform: Platform.isAndroid ? 'android' : 'ios',
  currentVersion: info.version,
  currentBuild: int.tryParse(info.buildNumber),
  channel: 'stable',
);

switch (response.updateType) {
  case 'required':
    showForceUpdateDialog(response);
    break;
  case 'optional':
    showOptionalUpdateDialog(response);
    break;
  default:
    break;
}
```

## 5. 健康检查

### 请求

```http
GET /api/v1/health
```

### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "status": "UP",
    "version": "1.0.0"
  }
}
```

## 需要 Flutter 侧实现的功能

### API Client

新增一个后端 API client，建议集中处理：

- baseUrl。
- JSON 编解码。
- `ApiResponse<T>` 解包。
- 非 0 错误码转异常或错误状态。
- 网络超时。
- 后端不可用时的兜底逻辑。

### 学校选择页

需要从后端获取学校列表，并映射到现有学校模型。后端不可用时使用本地内置 JIT。

### 通用教务 WebView 登录页

需要从学校配置读取：

- `login.loginUrl`
- `login.successUrlPatterns`
- `login.mode`

登录成功后读取：

- cookie
- successUrl
- studentId

然后调用课程导入接口。

### 课程导入流程

后端返回 `courses[]` 后：

- 使用 `Course.fromJson` 转模型。
- 复用现有学期第一天选择流程。
- 复用现有课程颜色配置流程。
- 保存到当前 `ClassScheduleStore`。

### 版本检查和更新弹窗

需要实现：

- 启动时检查版本。
- 可选更新弹窗。
- 强制更新弹窗。
- 打开 Gitee 下载地址或 Git 地址。
- 可选更新可以延后，强制更新不能跳过。

## 后端管理说明

### 学校适配器管理 UI

```text
http://localhost:8080/admin/schools.html
```

该页面用于生成后端学校适配器代码包。

脚手架接口会写入源码文件，当前后端已限制为本机访问。生产环境不要把该管理页暴露给公网。

### 学校适配器开发文档

```text
http://localhost:8080/docs/school-importer-development.md
```

后端学校适配器使用“一个学校一个包”的结构：

```text
importers/schools/{schoolId}/
  {Prefix}SchoolConfig.java
  {Prefix}CourseImporter.java
  {Prefix}Client.java
  {Prefix}Parser.java
```

新增学校后，重启后端即可让学校配置被扫描。

## 当前后端版本配置位置

版本控制配置目前在：

```text
src/main/resources/application.properties
```

关键配置：

```properties
clark-aams.client-version.latest-version=1.1.0
clark-aams.client-version.latest-build=2
clark-aams.client-version.minimum-supported-version=1.0.0
clark-aams.client-version.minimum-supported-build=1
clark-aams.client-version.download-sources[0].type=gitee
clark-aams.client-version.download-sources[0].url=https://gitee.com/your-org/clark-aams/releases
clark-aams.client-version.download-sources[1].type=git
clark-aams.client-version.download-sources[1].url=https://gitee.com/your-org/clark-aams.git
```

发布新版本时：

1. 上传安装包或 release 到 Gitee。
2. 更新 `latestVersion` 和 `latestBuild`。
3. 如果旧版本必须停止使用，更新 `minimumSupportedVersion` 和 `minimumSupportedBuild`。
4. 更新下载地址和 release notes。
5. 重启后端。
