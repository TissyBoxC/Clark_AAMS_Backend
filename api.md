# Clark AAMS Backend API

本文档基于当前后端代码整理，覆盖项目全部接口，并给出调用请求示例与预期成功返回示例。

Base URL:

- 本地开发: `http://localhost:8080`
- 生产示例: `https://api.clarkhub.cn`

统一响应结构:

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

说明:

- `code = 0` 表示成功。
- 除特别说明外，接口都返回上面的包装结构。
- 常见错误码:

| code | 含义 |
| --- | --- |
| `40001` | 请求参数错误 |
| `40101` | 未登录或登录失效 |
| `40102` | 管理员尚未初始化 |
| `40401` | 学校不存在 |
| `40402` | 学校未配置解析器 |
| `40403` | 用户不存在 |
| `40901` | 邮箱已被其他用户绑定 |
| `50001` | 教务系统请求失败 |
| `50002` | 课程解析失败 |
| `50003` | 服务内部错误 |
| `50004` | AI 请求失败 |

## 接口总览

| 模块 | 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- | --- |
| 健康检查 | `GET` | `/api/v1/health` | 无 | 服务状态 |
| 学校 | `GET` | `/api/v1/schools` | 无 | 学校列表 |
| 学校 | `GET` | `/api/v1/schools/{schoolId}` | 无 | 学校详情 |
| 学校 | `GET` | `/api/v1/schools/{schoolId}/lesson-times` | 无 | 学校节次时间 |
| 学校 | `GET` | `/api/v1/schools/lesson-time-profiles` | 无 | 通用节次模板 |
| 导课 | `POST` | `/api/v1/imports/courses/html` | 无 | 直接上传 `school/html` 解析 |
| 导课 | `POST` | `/api/v1/imports/{schoolId}/courses` | 无 | 按学校适配器导课 |
| 导课 | `POST` | `/api/v1/imports/{schoolId}/courses/pickup` | 无 | 导课并生成取件码 |
| OCR | `POST` | `/api/v1/courses/recognize` | 无 | 识别课表图片 |
| 取件码 | `POST` | `/api/v1/course-pickups` | 无 | OCR 并生成取件码 |
| 取件码 | `GET` | `/api/v1/course-pickups/{code}` | 无 | 取回课程 JSON |
| 版本 | `GET` | `/api/v1/app/version` | 无 | Query 方式检查版本 |
| 版本 | `POST` | `/api/v1/app/version/check` | 无 | JSON 方式检查版本 |
| 用户 | `POST` | `/api/v1/user/register` | 无 | 注册并登录 |
| 用户 | `POST` | `/api/v1/user/login` | 无 | 登录 |
| 用户 | `POST` | `/api/v1/user/logout` | Session | 退出 |
| 用户 | `GET` | `/api/v1/user/me` | Session / `X-Clark-Aams-Login-Code` | 当前用户 |
| 用户 | `POST` | `/api/v1/user/email/code` | Session / `X-Clark-Aams-Login-Code` | 发送邮箱验证码 |
| 用户 | `POST` | `/api/v1/user/email/verify` | Session / `X-Clark-Aams-Login-Code` | 校验邮箱验证码 |
| AI | `POST` | `/api/v1/questions/analyze` | Session / `X-Clark-Aams-Login-Code` | 题目分析 |
| AI | `POST` | `/api/v1/questions/analyze/fill-blank` | Session / `X-Clark-Aams-Login-Code` | 填空/主观题分析 |
| 管理员认证 | `GET` | `/api/v1/admin/auth/setup` | 无 | 后台初始化状态 |
| 管理员认证 | `POST` | `/api/v1/admin/auth/register` | 无 | 首次注册管理员 |
| 管理员认证 | `POST` | `/api/v1/admin/auth/login` | 无 | 管理员登录 |
| 管理员认证 | `POST` | `/api/v1/admin/auth/logout` | 无 | 管理员退出 |
| 后台版本配置 | `GET` | `/api/v1/admin/app-version/config` | 管理员 Session | 读取版本配置 |
| 后台版本配置 | `PUT` | `/api/v1/admin/app-version/config` | 管理员 Session | 全量覆盖版本配置 |
| 后台版本配置 | `PATCH` | `/api/v1/admin/app-version/config` | 管理员 Session | 部分更新版本配置 |
| 后台 AI 配置 | `GET` | `/api/v1/admin/ai/config` | 管理员 Session | 读取 AI 配置 |
| 后台 AI 配置 | `PUT` | `/api/v1/admin/ai/config` | 管理员 Session | 更新 AI 配置 |
| 后台邮箱配置 | `GET` | `/api/v1/admin/email/config` | 管理员 Session | 读取邮箱配置 |
| 后台邮箱配置 | `PUT` | `/api/v1/admin/email/config` | 管理员 Session | 更新邮箱配置 |
| 后台取件码 | `GET` | `/api/v1/admin/course-pickups` | 管理员 Session | 取件码列表 |
| 后台取件码 | `GET` | `/api/v1/admin/course-pickups/{code}` | 管理员 Session | 取件码详情 |
| 后台取件码 | `POST` | `/api/v1/admin/course-pickups` | 管理员 Session | 手工创建取件码 |
| 后台取件码 | `PUT` | `/api/v1/admin/course-pickups/{code}` | 管理员 Session | 更新取件码 JSON |
| 后台取件码 | `DELETE` | `/api/v1/admin/course-pickups/{code}` | 管理员 Session | 删除取件码 |
| 后台取件码 | `DELETE` | `/api/v1/admin/course-pickups/{code}/images/{fileName}` | 管理员 Session | 删除取件码图片 |
| 后台取件码 | `GET` | `/api/v1/admin/course-pickups/{code}/images/{fileName}` | 管理员 Session | 下载图片 |
| 后台学校脚手架 | `POST` | `/api/v1/admin/school-scaffold` | 管理员 Session | 生成学校适配器骨架 |

## 鉴权说明

用户侧:

- 主鉴权方式是 `HttpSession`
- 以下接口还支持请求头 `X-Clark-Aams-Login-Code`
  - `GET /api/v1/user/me`
  - `POST /api/v1/user/email/code`
  - `POST /api/v1/user/email/verify`
  - `POST /api/v1/questions/analyze`
  - `POST /api/v1/questions/analyze/fill-blank`

管理员侧:

- 使用管理员 `HttpSession`
- `/api/v1/admin/**` 除 `/api/v1/admin/auth/*` 外都需要管理员已登录

## 特殊返回接口

- `POST /api/v1/courses/recognize`
  - 直接返回 JSON 字符串
  - 不包 `ApiResponse`
- `GET /api/v1/course-pickups/{code}`
  - 直接返回课程 JSON 数组
  - 不包 `ApiResponse`
- `GET /api/v1/admin/course-pickups/{code}/images/{fileName}`
  - 直接返回文件流

## 1. 健康检查

### `GET /api/v1/health`

请求:

```http
GET /api/v1/health HTTP/1.1
Host: localhost:8080
```

预期返回:

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

## 2. 学校接口

### `GET /api/v1/schools`

请求:

```http
GET /api/v1/schools HTTP/1.1
Host: localhost:8080
```

预期返回:

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
      "lessonTimeProfile": "general-a",
      "version": 1
    },
    {
      "id": "jlu",
      "name": "吉林大学",
      "shortName": "JLU",
      "enabled": true,
      "capabilities": {
        "academicImport": true,
        "imageRecognition": false
      },
      "login": {
        "mode": "webview",
        "loginUrl": "https://iedu.jlu.edu.cn",
        "successUrlPatterns": [
          "https://iedu.jlu.edu.cn/jwapp/*"
        ]
      },
      "lessonTimeProfile": "general-a",
      "version": 1
    }
  ]
}
```

### `GET /api/v1/schools/{schoolId}`

请求:

```http
GET /api/v1/schools/jit HTTP/1.1
Host: localhost:8080
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
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
    "lessonTimeProfile": "general-a",
    "version": 1
  }
}
```

### `GET /api/v1/schools/{schoolId}/lesson-times`

请求:

```http
GET /api/v1/schools/jit/lesson-times HTTP/1.1
Host: localhost:8080
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "schoolId": "jit",
    "profile": "general-a",
    "lessons": [
      { "lesson": 1, "startTime": "08:30" },
      { "lesson": 2, "startTime": "09:20" },
      { "lesson": 3, "startTime": "10:25" },
      { "lesson": 4, "startTime": "11:15" },
      { "lesson": 5, "startTime": "13:30" },
      { "lesson": 6, "startTime": "14:20" },
      { "lesson": 7, "startTime": "15:25" },
      { "lesson": 8, "startTime": "16:15" },
      { "lesson": 9, "startTime": "" },
      { "lesson": 10, "startTime": "" },
      { "lesson": 11, "startTime": "" },
      { "lesson": 12, "startTime": "" },
      { "lesson": 13, "startTime": "" }
    ]
  }
}
```

### `GET /api/v1/schools/lesson-time-profiles`

请求:

```http
GET /api/v1/schools/lesson-time-profiles HTTP/1.1
Host: localhost:8080
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "schoolId": "",
      "profile": "general-a",
      "lessons": [
        { "lesson": 1, "startTime": "08:30" }
      ]
    },
    {
      "schoolId": "",
      "profile": "general-b",
      "lessons": [
        { "lesson": 1, "startTime": "08:00" }
      ]
    }
  ]
}
```

## 3. 导课接口

### `POST /api/v1/imports/courses/html`

说明:

- 前端直接上传整页 HTML
- `school` 可传学校 `id`、学校名、简称
- 如果没有匹配到解析器，会按 `schoolName` 或 `school` 保存样本 HTML

请求:

```http
POST /api/v1/imports/courses/html HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "school": "jlu",
  "schoolName": "",
  "html": "<table class=\"wut_table\">...</table>"
}
```

预期返回 1: 已匹配解析器

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "status": "parsed",
    "schoolId": "jlu",
    "schoolName": "吉林大学",
    "importerVersion": 1,
    "courses": [
      {
        "name": "Advanced Math",
        "startWeek": 1,
        "endWeek": 6,
        "dayOfWeek": 1,
        "startLesson": 3,
        "endLesson": 4,
        "location": "Campus-323",
        "teacher": "Teacher Zhang",
        "weeks": [1, 2, 3, 4, 5, 6],
        "rawTime": "1-6周,星期1,第3节-第4节"
      }
    ],
    "warnings": [],
    "savedFileName": null
  }
}
```

预期返回 2: 未匹配解析器，保存样本

```json
{
  "code": 0,
  "message": "保存成功,请等待添加适配",
  "data": {
    "status": "saved",
    "schoolId": null,
    "schoolName": "某某大学",
    "importerVersion": null,
    "courses": [],
    "warnings": [],
    "savedFileName": "某某大学.html"
  }
}
```

### `POST /api/v1/imports/{schoolId}/courses`

请求:

```http
POST /api/v1/imports/jit/courses HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "loginSession": {
    "cookie": "",
    "studentId": "",
    "successUrl": "",
    "extra": {
      "html": "<html><body id=\"index-charts\"><table id=\"Table6\">...</table></body></html>"
    }
  }
}
```

预期返回:

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
        "endWeek": 4,
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
}
```

### `POST /api/v1/imports/{schoolId}/courses/pickup`

请求:

```http
POST /api/v1/imports/jlu/courses/pickup HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "loginSession": {
    "extra": {
      "html": "<table class=\"wut_table\">...</table>"
    }
  }
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "pickupCode": "AB12CD34",
    "courses": [
      {
        "name": "Linear Algebra",
        "startWeek": 7,
        "endWeek": 8,
        "dayOfWeek": 2,
        "startLesson": 1,
        "endLesson": 2,
        "location": "Campus-101",
        "teacher": "Teacher Li",
        "weeks": [7, 8],
        "rawTime": "7-8周,星期2,第1节-第2节"
      }
    ]
  }
}
```

## 4. OCR 与取件码

### `POST /api/v1/courses/recognize`

说明:

- `multipart/form-data`
- 字段名固定为 `images`
- 最多 8 张
- 每张不超过 10MB

请求示例:

```bash
curl -X POST "http://localhost:8080/api/v1/courses/recognize" \
  -F "images=@course1.png" \
  -F "images=@course2.png"
```

预期返回:

```json
[
  {
    "name": "计算机网络[02]",
    "teacher": "李老师",
    "position": "A301",
    "day": 2,
    "startSection": 3,
    "endSection": 4,
    "weeks": [1, 2, 3, 4, 5, 6],
    "rawTime": "第1-6周 星期2,第3节 第4节"
  }
]
```

### `POST /api/v1/course-pickups`

请求示例:

```bash
curl -X POST "http://localhost:8080/api/v1/course-pickups" \
  -F "images=@course1.png" \
  -F "images=@course2.png"
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "pickupCode": "Q8W2M4NP",
    "courses": [
      {
        "name": "计算机网络[02]",
        "teacher": "李老师",
        "position": "A301",
        "day": 2,
        "startSection": 3,
        "endSection": 4,
        "weeks": [1, 2, 3, 4, 5, 6],
        "rawTime": "第1-6周 星期2,第3节 第4节"
      }
    ]
  }
}
```

### `GET /api/v1/course-pickups/{code}`

请求:

```http
GET /api/v1/course-pickups/Q8W2M4NP HTTP/1.1
Host: localhost:8080
```

预期返回:

```json
[
  {
    "name": "计算机网络[02]",
    "teacher": "李老师",
    "position": "A301",
    "day": 2,
    "startSection": 3,
    "endSection": 4,
    "weeks": [1, 2, 3, 4, 5, 6],
    "rawTime": "第1-6周 星期2,第3节 第4节"
  }
]
```

## 5. 版本检查

### `GET /api/v1/app/version`

请求:

```http
GET /api/v1/app/version?platform=android&currentVersion=1.0.0&currentBuild=1 HTTP/1.1
Host: localhost:8080
```

预期返回:

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
      }
    ],
    "releasePageUrl": "https://gitee.com/your-org/clark-aams/releases",
    "checkedAt": "2026-06-10T17:20:00+08:00"
  }
}
```

### `POST /api/v1/app/version/check`

请求:

```http
POST /api/v1/app/version/check HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "platform": "android",
  "currentVersion": "0.9.0",
  "currentBuild": 0,
  "channel": "stable",
  "extra": {}
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "platform": "android",
    "channel": "stable",
    "currentVersion": "0.9.0",
    "currentBuild": 0,
    "latestVersion": "1.1.0",
    "latestBuild": 2,
    "minimumSupportedVersion": "1.0.0",
    "minimumSupportedBuild": 1,
    "updateType": "required",
    "updateAvailable": true,
    "forceUpdate": true,
    "title": "发现新版本",
    "message": "当前版本已停止支持，请更新后继续使用。",
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
      }
    ],
    "releasePageUrl": "https://gitee.com/your-org/clark-aams/releases",
    "checkedAt": "2026-06-10T17:20:00+08:00"
  }
}
```

## 6. 用户接口

### `POST /api/v1/user/register`

请求:

```http
POST /api/v1/user/register HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "test-user",
  "password": "test-password",
  "qqEmail": "123456@qq.com"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "test-user",
    "email": "123456@qq.com",
    "loginCode": "8QW9X2ABCD34",
    "emailBound": false,
    "emailVerified": false,
    "questionAnalysisCount": 0
  }
}
```

### `POST /api/v1/user/login`

请求示例 1: 登录码登录

```http
POST /api/v1/user/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "loginCode": "8QW9X2ABCD34"
}
```

请求示例 2: 邮箱密码登录

```http
POST /api/v1/user/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "qqEmail": "123456@qq.com",
  "password": "test-password"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "test-user",
    "email": "123456@qq.com",
    "loginCode": "8QW9X2ABCD34",
    "emailBound": false,
    "emailVerified": false,
    "questionAnalysisCount": 0
  }
}
```

### `POST /api/v1/user/logout`

请求:

```http
POST /api/v1/user/logout HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "authenticated": false
  }
}
```

### `GET /api/v1/user/me`

请求:

```http
GET /api/v1/user/me HTTP/1.1
Host: localhost:8080
X-Clark-Aams-Login-Code: 8QW9X2ABCD34
```

预期返回 1: 已登录

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "test-user",
    "email": "123456@qq.com",
    "loginCode": "8QW9X2ABCD34",
    "emailBound": false,
    "emailVerified": false,
    "questionAnalysisCount": 0
  }
}
```

预期返回 2: 未登录

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 0,
    "username": "",
    "email": "",
    "loginCode": "",
    "emailBound": false,
    "emailVerified": false,
    "questionAnalysisCount": 0
  }
}
```

### `POST /api/v1/user/email/code`

请求:

```http
POST /api/v1/user/email/code HTTP/1.1
Host: localhost:8080
X-Clark-Aams-Login-Code: 8QW9X2ABCD34
Content-Type: application/json

{
  "qqEmail": "123456@qq.com"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "test-user",
    "email": "123456@qq.com",
    "loginCode": "8QW9X2ABCD34",
    "emailBound": false,
    "emailVerified": false,
    "questionAnalysisCount": 0
  }
}
```

### `POST /api/v1/user/email/verify`

请求:

```http
POST /api/v1/user/email/verify HTTP/1.1
Host: localhost:8080
X-Clark-Aams-Login-Code: 8QW9X2ABCD34
Content-Type: application/json

{
  "code": "123456"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "username": "test-user",
    "email": "123456@qq.com",
    "loginCode": "8QW9X2ABCD34",
    "emailBound": true,
    "emailVerified": true,
    "questionAnalysisCount": 0
  }
}
```

## 7. AI 题目分析

### `POST /api/v1/questions/analyze`

请求:

```http
POST /api/v1/questions/analyze HTTP/1.1
Host: localhost:8080
X-Clark-Aams-Login-Code: 8QW9X2ABCD34
Content-Type: application/json

{
  "question_type": "single_choice",
  "question_content": "Pick one",
  "options": [
    "A. one",
    "B. two"
  ]
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "answer": "A"
  }
}
```

说明:

- 实际 `data` 由 AI 返回内容决定，不固定只有 `answer`
- 调用成功后会累加 `questionAnalysisCount`

### `POST /api/v1/questions/analyze/fill-blank`

请求:

```http
POST /api/v1/questions/analyze/fill-blank HTTP/1.1
Host: localhost:8080
X-Clark-Aams-Login-Code: 8QW9X2ABCD34
Content-Type: application/json

{
  "question_type": "essay",
  "question_content": "Explain the purpose of an interface.",
  "material": "Use Java as the context."
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "answer": "fill answer"
  }
}
```

## 8. 管理员认证

### `GET /api/v1/admin/auth/setup`

请求:

```http
GET /api/v1/admin/auth/setup HTTP/1.1
Host: localhost:8080
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "configured": true,
    "registrationAvailable": false
  }
}
```

### `POST /api/v1/admin/auth/register`

说明:

- 仅在系统还没有管理员时可用
- 成功后自动登录

请求:

```http
POST /api/v1/admin/auth/register HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "admin",
  "password": "admin-password"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "authenticated": true
  }
}
```

### `POST /api/v1/admin/auth/login`

请求:

```http
POST /api/v1/admin/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "admin",
  "password": "admin-password"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "authenticated": true
  }
}
```

### `POST /api/v1/admin/auth/logout`

请求:

```http
POST /api/v1/admin/auth/logout HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "authenticated": false
  }
}
```

## 9. 后台版本配置

### `GET /api/v1/admin/app-version/config`

请求:

```http
GET /api/v1/admin/app-version/config HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "latestVersion": "1.1.0",
    "latestBuild": 2,
    "minimumSupportedVersion": "1.0.0",
    "minimumSupportedBuild": 1,
    "title": "发现新版本",
    "optionalUpdateMessage": "有新版本可用，建议更新后继续使用。",
    "requiredUpdateMessage": "当前版本已停止支持，请更新后继续使用。",
    "releasePageUrl": "https://gitee.com/your-org/clark-aams/releases",
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
    ]
  }
}
```

### `PUT /api/v1/admin/app-version/config`

说明:

- `PUT` 为全量覆盖，必传完整配置对象
- 如果只想改单个字段，例如只改 `latestVersion`，请使用下面的 `PATCH`

请求:

```http
PUT /api/v1/admin/app-version/config HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "latestVersion": "1.2.0",
  "latestBuild": 3,
  "minimumSupportedVersion": "1.0.0",
  "minimumSupportedBuild": 1,
  "title": "Update Found",
  "optionalUpdateMessage": "Optional update available",
  "requiredUpdateMessage": "Please update before continuing",
  "releasePageUrl": "https://example.com/releases",
  "releaseNotes": [
    "Added admin version config"
  ],
  "downloadSources": [
    {
      "type": "gitee",
      "label": "Gitee Download",
      "url": "https://example.com/download",
      "primary": true,
      "description": "Primary mirror"
    }
  ]
}
```

### `PATCH /api/v1/admin/app-version/config`

说明:

- `PATCH` 为部分更新
- 只会更新请求体中出现的字段，未出现的字段保持原值不变

请求:

```http
PATCH /api/v1/admin/app-version/config HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "latestVersion": "1.2.1"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "latestVersion": "1.2.1",
    "latestBuild": 3,
    "minimumSupportedVersion": "1.0.0",
    "minimumSupportedBuild": 1,
    "title": "Update Found",
    "optionalUpdateMessage": "Optional update available",
    "requiredUpdateMessage": "Please update before continuing",
    "releasePageUrl": "https://example.com/releases",
    "releaseNotes": [
      "Added admin version config"
    ],
    "downloadSources": [
      {
        "type": "gitee",
        "label": "Gitee Download",
        "url": "https://example.com/download",
        "primary": true,
        "description": "Primary mirror"
      }
    ]
  }
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "latestVersion": "1.2.0",
    "latestBuild": 3,
    "minimumSupportedVersion": "1.0.0",
    "minimumSupportedBuild": 1,
    "title": "Update Found",
    "optionalUpdateMessage": "Optional update available",
    "requiredUpdateMessage": "Please update before continuing",
    "releasePageUrl": "https://example.com/releases",
    "releaseNotes": [
      "Added admin version config"
    ],
    "downloadSources": [
      {
        "type": "gitee",
        "label": "Gitee Download",
        "url": "https://example.com/download",
        "primary": true,
        "description": "Primary mirror"
      }
    ]
  }
}
```

## 10. 后台 AI 配置

### `GET /api/v1/admin/ai/config`

请求:

```http
GET /api/v1/admin/ai/config HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "baseUrl": "https://api.example.com/v1",
    "model": "gpt-4.1",
    "apiKeyConfigured": true
  }
}
```

### `PUT /api/v1/admin/ai/config`

请求:

```http
PUT /api/v1/admin/ai/config HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "baseUrl": "https://api.example.com/v1",
  "apiKey": "sk-xxx",
  "model": "gpt-4.1",
  "clearApiKey": false
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "baseUrl": "https://api.example.com/v1",
    "model": "gpt-4.1",
    "apiKeyConfigured": true
  }
}
```

## 11. 后台邮箱配置

### `GET /api/v1/admin/email/config`

请求:

```http
GET /api/v1/admin/email/config HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "enabled": true,
    "provider": "resend",
    "endpoint": "https://api.resend.com",
    "senderAddress": "no-reply@example.com",
    "senderName": "Clark AAMS",
    "verificationSubject": "邮箱验证码",
    "htmlTemplate": "<p>验证码：{{code}}</p>",
    "apiKeyConfigured": true
  }
}
```

### `PUT /api/v1/admin/email/config`

请求:

```http
PUT /api/v1/admin/email/config HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "enabled": true,
  "provider": "resend",
  "endpoint": "https://api.resend.com",
  "senderAddress": "no-reply@example.com",
  "senderName": "Clark AAMS",
  "verificationSubject": "邮箱验证码",
  "htmlTemplate": "<p>验证码：{{code}}</p>",
  "apiKey": "re_xxx",
  "clearApiKey": false
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "enabled": true,
    "provider": "resend",
    "endpoint": "https://api.resend.com",
    "senderAddress": "no-reply@example.com",
    "senderName": "Clark AAMS",
    "verificationSubject": "邮箱验证码",
    "htmlTemplate": "<p>验证码：{{code}}</p>",
    "apiKeyConfigured": true
  }
}
```

## 12. 后台取件码管理

### `GET /api/v1/admin/course-pickups`

请求:

```http
GET /api/v1/admin/course-pickups HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "code": "Q8W2M4NP",
      "imageCount": 2,
      "createdAt": "2026-06-10T17:20:00+08:00",
      "updatedAt": "2026-06-10T17:20:00+08:00"
    }
  ]
}
```

### `GET /api/v1/admin/course-pickups/{code}`

请求:

```http
GET /api/v1/admin/course-pickups/Q8W2M4NP HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "code": "Q8W2M4NP",
    "jsonText": "[{\"name\":\"计算机网络[02]\"}]",
    "images": [
      {
        "fileName": "01.png",
        "originalName": "course1.png",
        "contentType": "image/png",
        "size": 123456
      }
    ],
    "createdAt": "2026-06-10T17:20:00+08:00",
    "updatedAt": "2026-06-10T17:20:00+08:00"
  }
}
```

### `POST /api/v1/admin/course-pickups`

请求:

```http
POST /api/v1/admin/course-pickups HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "jsonText": "[{\"name\":\"Math\"}]"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "code": "AB12CD34",
    "jsonText": "[{\"name\":\"Math\"}]",
    "images": [],
    "createdAt": "2026-06-10T17:20:00+08:00",
    "updatedAt": "2026-06-10T17:20:00+08:00"
  }
}
```

### `PUT /api/v1/admin/course-pickups/{code}`

请求:

```http
PUT /api/v1/admin/course-pickups/AB12CD34 HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "jsonText": "[{\"name\":\"Updated Course\"}]"
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "code": "AB12CD34",
    "jsonText": "[{\"name\":\"Updated Course\"}]",
    "images": [],
    "createdAt": "2026-06-10T17:20:00+08:00",
    "updatedAt": "2026-06-10T17:30:00+08:00"
  }
}
```

### `DELETE /api/v1/admin/course-pickups/{code}`

请求:

```http
DELETE /api/v1/admin/course-pickups/AB12CD34 HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

### `DELETE /api/v1/admin/course-pickups/{code}/images/{fileName}`

请求:

```http
DELETE /api/v1/admin/course-pickups/Q8W2M4NP/images/01.png HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

### `GET /api/v1/admin/course-pickups/{code}/images/{fileName}`

请求:

```http
GET /api/v1/admin/course-pickups/Q8W2M4NP/images/01.png HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
```

预期返回:

- HTTP `200 OK`
- `Content-Type: image/png` 或实际文件类型
- Body 为图片二进制内容

## 13. 后台学校脚手架

### `POST /api/v1/admin/school-scaffold`

说明:

- 用于生成新增学校适配器骨架
- 当前会生成 `XxxSchoolAdapter.java`、`XxxClient.java`、`XxxParser.java`

请求:

```http
POST /api/v1/admin/school-scaffold HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=...
Content-Type: application/json

{
  "schoolId": "example",
  "classPrefix": "Example",
  "name": "示例大学",
  "shortName": "示大",
  "enabled": true,
  "academicImport": true,
  "imageRecognition": false,
  "loginMode": "webview",
  "loginUrl": "https://example.edu.cn/login",
  "successUrlPatterns": [
    "https://example.edu.cn/home*"
  ],
  "version": 1,
  "overwrite": false
}
```

预期返回:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "schoolId": "example",
    "packageName": "io.github.tissyboxc.clark_aams_backend.importers.schools.example",
    "classPrefix": "Example",
    "createdFiles": [
      "D:\\service\\github\\Clark_AAMS_Backend\\src\\main\\java\\io\\github\\tissyboxc\\clark_aams_backend\\importers\\schools\\example\\ExampleSchoolAdapter.java",
      "D:\\service\\github\\Clark_AAMS_Backend\\src\\main\\java\\io\\github\\tissyboxc\\clark_aams_backend\\importers\\schools\\example\\ExampleClient.java",
      "D:\\service\\github\\Clark_AAMS_Backend\\src\\main\\java\\io\\github\\tissyboxc\\clark_aams_backend\\importers\\schools\\example\\ExampleParser.java"
    ]
  }
}
```

## 14. CORS

当前代码配置:

- `allowedOriginPatterns = *`
- `allowedMethods = GET, POST, PUT, PATCH, DELETE, OPTIONS`
- `allowedHeaders = *`
- `allowCredentials = true`

如果前端依赖 Session / Cookie:

- 请求需要带 `credentials: 'include'`
- 生产环境还应确保 Session Cookie 正确设置 `SameSite=None` 与 `Secure`
