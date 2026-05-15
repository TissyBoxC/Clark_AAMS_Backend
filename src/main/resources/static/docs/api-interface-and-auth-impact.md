# Clark AAMS 后端接口与后台验证影响说明

本文档整理当前 Spring Boot 后端暴露的接口，并说明新增后台登录验证后对 Flutter 客户端的影响。

## 统一响应格式

后端 JSON 接口统一返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

常见约定：

- `code = 0` 表示成功。
- 非 `0` 表示业务错误。
- HTTP `401` 表示后台管理接口未登录或登录失败。

## 公开业务接口

这些接口面向 Flutter 客户端开放，不需要后台登录。

| 方法 | 路径 | 用途 | Flutter 调用位置 | 后台登录影响 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/schools` | 获取可用学校列表 | `welcome_page.dart`、`ClarkBackendApi.getSchools()` | 不受影响 |
| `GET` | `/api/v1/schools/{schoolId}` | 获取单个学校配置 | `course_settings_page.dart`、`ClarkBackendApi.getSchool()` | 不受影响 |
| `POST` | `/api/v1/imports/{schoolId}/courses` | 教务系统课表导入 | `academic_import_page.dart`、`ClarkBackendApi.importCourses()` | 不受影响 |
| `POST` | `/api/v1/app/version/check` | 客户端版本检查 | `home_page.dart`、`about_page.dart`、`ClarkBackendApi.checkVersion()` | 不受影响 |

### `GET /api/v1/schools`

返回当前启用的学校列表。

响应 `data` 示例：

```json
[
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
      "loginUrl": "https://example.edu.cn/login",
      "successUrlPatterns": []
    },
    "version": 1
  }
]
```

### `GET /api/v1/schools/{schoolId}`

返回指定学校配置。

路径参数：

| 参数 | 说明 |
| --- | --- |
| `schoolId` | 学校 ID，例如 `jit` |

响应 `data` 字段结构同单个学校对象。

### `POST /api/v1/imports/{schoolId}/courses`

根据学校适配器解析教务系统课表，并返回统一课程模型。

路径参数：

| 参数 | 说明 |
| --- | --- |
| `schoolId` | 学校 ID，例如 `jit` |

请求体示例：

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

兼容说明：旧客户端如果继续提交 `options` 字段，后端会忽略该字段；当前导入逻辑只读取 `loginSession`。

响应 `data` 示例：

```json
{
  "schoolId": "jit",
  "importerVersion": 1,
  "courses": [
    {
      "name": "高等数学",
      "startWeek": 1,
      "endWeek": 16,
      "dayOfWeek": 1,
      "startLesson": 1,
      "endLesson": 2,
      "location": "A101",
      "teacher": "张三",
      "weeks": [1, 2, 3, 4],
      "rawTime": "1-2节(1-4周)"
    }
  ],
  "warnings": []
}
```

### `POST /api/v1/app/version/check`

检查客户端是否需要更新。

请求体示例：

```json
{
  "platform": "android",
  "currentVersion": "1.0.0",
  "currentBuild": 1,
  "channel": "stable",
  "extra": {}
}
```

响应 `data` 示例：

```json
{
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
  "message": "有新版本可用",
  "releaseNotes": [],
  "downloadSources": [
    {
      "type": "gitee",
      "label": "Gitee 下载",
      "url": "https://example.com/releases",
      "primary": true,
      "description": "推荐国内网络使用"
    }
  ],
  "releasePageUrl": "https://example.com/releases",
  "checkedAt": "2026-05-15T12:00:00+08:00"
}
```

## 公开辅助接口

这些接口不需要后台登录。

| 方法 | 路径 | 用途 | 后台登录影响 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/health` | 健康检查，介绍页用它显示后端状态 | 不受影响 |
| `GET` | `/api/v1/app/version` | 版本检查的 Query 参数形式 | 不受影响 |

### `GET /api/v1/health`

响应 `data` 示例：

```json
{
  "status": "UP",
  "version": "1.0.0"
}
```

### `GET /api/v1/app/version`

Query 参数：

| 参数 | 说明 |
| --- | --- |
| `platform` | 平台，例如 `android`、`ios` |
| `currentVersion` | 当前客户端版本号 |
| `currentBuild` | 当前客户端 build 号 |
| `channel` | 渠道，默认可使用 `stable` |

响应结构同 `POST /api/v1/app/version/check`。

## 后台管理页面

后台页面现在需要登录后访问。

| 方法 | 路径 | 用途 | 登录要求 |
| --- | --- | --- | --- |
| `GET` | `/admin` | 后台入口，登录后跳转到 `/admin/index.html` | 需要登录 |
| `GET` | `/admin/` | 后台入口，登录后跳转到 `/admin/index.html` | 需要登录 |
| `GET` | `/admin/index.html` | 管理首页 | 需要登录 |
| `GET` | `/admin/schools.html` | 学校管理页 | 需要登录 |
| `GET` | `/admin/versions.html` | 版本管理页 | 需要登录 |
| `GET` | `/admin/login.html` | 后台登录页 | 不需要登录 |

未登录访问后台页面时，会重定向到：

```text
/admin/login.html?redirect=<原始路径>
```

## 后台管理 API

除登录接口外，后台管理 API 均需要登录。

| 方法 | 路径 | 用途 | 登录要求 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/admin/auth/login` | 后台登录 | 不需要登录 |
| `POST` | `/api/v1/admin/auth/logout` | 后台退出 | 不需要登录 |
| `GET` | `/api/v1/admin/app-version/config` | 读取版本配置 | 需要登录 |
| `PUT` | `/api/v1/admin/app-version/config` | 更新版本配置 | 需要登录 |
| `POST` | `/api/v1/admin/school-scaffold` | 生成学校适配脚手架 | 需要登录 |

## 版本配置持久化

客户端版本配置会持久化到一个独立 JSON 文件中，避免每次替换后端 jar 后都要重新配置。

默认行为：

- 文件名：`client-version.json`
- 位置：与运行中的后端 jar 同级目录
- 文件不存在：启动时根据 `application.properties` 中的默认版本配置自动创建
- 文件已存在：启动时读取该文件，不再重新创建或覆盖
- 后台保存版本配置：同步写回该文件

如果需要指定自定义路径，可以配置：

```properties
clark-aams.client-version.config-path=/path/to/client-version.json
```

### `POST /api/v1/admin/auth/login`

后台账号密码通过环境变量或外部配置提供，不提交真实凭据到仓库。

请求体：

```json
{
  "username": "<your-admin-username>",
  "password": "<your-admin-password>"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "authenticated": true
  }
}
```

失败响应：

```json
{
  "code": 40101,
  "message": "账号或密码错误",
  "data": null
}
```

登录成功后，服务端通过 Session 保存登录状态，浏览器会自动携带 `JSESSIONID`。

### `POST /api/v1/admin/auth/logout`

退出后台登录。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "authenticated": false
  }
}
```

### `GET /api/v1/admin/app-version/config`

读取当前客户端版本配置。

响应 `data` 示例：

```json
{
  "latestVersion": "1.1.0",
  "latestBuild": 2,
  "minimumSupportedVersion": "1.0.0",
  "minimumSupportedBuild": 1,
  "title": "发现新版本",
  "optionalUpdateMessage": "有新版本可用",
  "requiredUpdateMessage": "当前版本已停止支持",
  "releasePageUrl": "https://example.com/releases",
  "releaseNotes": [],
  "downloadSources": []
}
```

### `PUT /api/v1/admin/app-version/config`

更新客户端版本配置。

请求体结构同 `GET /api/v1/admin/app-version/config` 的 `data`。

必填校验：

- `latestVersion` 不能为空，且必须是语义化版本。
- `minimumSupportedVersion` 不能为空，且必须是语义化版本。
- `latestBuild` 不能为空且不能小于 `0`。
- `minimumSupportedBuild` 不能为空且不能小于 `0`。

### `POST /api/v1/admin/school-scaffold`

生成学校适配器脚手架。

请求体示例：

```json
{
  "schoolId": "njupt",
  "classPrefix": "Njupt",
  "name": "南京邮电大学",
  "shortName": "NJUPT",
  "enabled": true,
  "academicImport": true,
  "imageRecognition": false,
  "loginMode": "webview",
  "loginUrl": "https://example.edu.cn/login",
  "successUrlPatterns": ["https://example.edu.cn/main*"],
  "version": 1,
  "overwrite": false
}
```

响应 `data` 示例：

```json
{
  "schoolId": "njupt",
  "packageName": "io.github.tissyboxc.clark_aams_backend.importers.schools.njupt",
  "classPrefix": "Njupt",
  "createdFiles": []
}
```

## 静态页面与文档

| 路径 | 用途 | 登录影响 |
| --- | --- | --- |
| `/` | 项目介绍页 | 不受影响 |
| `/index.html` | 项目介绍页 | 不受影响 |
| `/site/styles.css` | 项目介绍页样式 | 不受影响 |
| `/site/logo.png` | 项目介绍页 Logo | 不受影响 |
| `/docs/flutter-backend-integration.md` | Flutter 接入文档 | 不受影响 |
| `/docs/school-importer-development.md` | 学校适配文档 | 不受影响 |
| `/docs/api-interface-and-auth-impact.md` | 本文档 | 不受影响 |

## 对 Flutter 项目的影响

当前 Flutter 项目不会被后台登录验证影响。

Flutter 当前实际调用的后端路径只有：

```text
/api/v1/schools
/api/v1/schools/{schoolId}
/api/v1/imports/{schoolId}/courses
/api/v1/app/version/check
```

后台登录过滤器只拦截：

```text
/admin
/admin/**
/api/v1/admin/**
```

因此现有 Flutter 流程保持不变：

- 首次启动加载学校列表：不受影响。
- 课程导入前读取学校配置：不受影响。
- WebView 登录后提交教务 Cookie 导入课程：不受影响。
- 首页和关于页版本检查：不受影响。
- 本地课程表存储、手动 JSON 导入、设置页：不受影响。

## 后续注意事项

如果未来 Flutter 客户端需要直接调用 `/api/v1/admin/**` 管理接口，需要先调用：

```text
POST /api/v1/admin/auth/login
```

并在后续请求中携带同一个 `JSESSIONID`。当前 Flutter 的 `ClarkBackendApi` 没有实现后台登录和 Session Cookie 管理，所以直接调用后台管理接口会得到 `401`。

当前后台管理页面和后端同源部署，浏览器会自动处理 Session Cookie。如果未来将后台管理前端部署到独立域名，还需要额外处理跨域请求、Cookie 凭据和 CORS 预检。
