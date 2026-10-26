# Clark AAMS API 契约

> 版本：v1 · 最后更新：2026-10-26
> 对应路线图阶段：**P2 契约归一**
>
> 本文档是三端（Backend / Web / App）共同遵守的接口契约基线。
> 任何端点变更都要先改本文档，再改实现。

---

## 1. 通用约定

### 1.1 响应信封

**所有** JSON 接口（除下述明确例外）返回统一信封：

```json
{ "code": 0, "message": "ok", "data": <任意> }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | int | `0` 表示成功；非 0 见 §1.3 |
| `message` | string | 人类可读信息，可直接展示给用户 |
| `data` | any | 业务数据；失败时为 `null` |

**例外（仅两处，均为合理设计）**：

| 端点 | 形态 | 原因 |
|---|---|---|
| `GET /api/v1/admin/course-pickups/{code}/images/{fileName}` | 二进制流 | 返回图片本体 |
| 各 legacy 通道（见 §1.4） | 裸 JSON | 兼容未升级客户端，已标 deprecated |

> **P2 变更**：`GET /api/v1/course-pickups/{code}` 与
> `POST /api/v1/courses/recognize` 此前返回裸 JSON 数组，
> 是全项目仅有的两处裸 JSON 分支，现已统一为信封。
> 客户端不再需要为它们写单独的解析路径。

### 1.2 HTTP 状态码

| 状态 | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数错误（`40001`） |
| 401 | 未登录 / 登录失效（`40101`） |
| 403 | 已登录但身份不足（`40301`） |
| 404 | 资源不存在（`404xx`） |
| 409 | 冲突，如邮箱已绑定（`40901`） |
| 429 | 配额用尽（`42901`）或限流（`42902`） |
| 500 | 服务端错误（`50xxx`） |

**401 与 403 必须区分**：
- 401 = 「你还没登录」→ 客户端应引导登录
- 403 = 「你登录了，但没这个权限」→ 客户端应提示无权限，**不要**跳登录页

### 1.3 错误码

| 码 | 名称 | 分类 | 可重试 |
|---|---|---|---|
| 0 | OK | — | — |
| 40001 | BAD_REQUEST | input | 否 |
| 40101 | LOGIN_INVALID | auth | 否（需重新登录） |
| 40301 | FORBIDDEN | forbidden | 否 |
| 40401 | SCHOOL_NOT_FOUND | input | 否 |
| 40402 | IMPORTER_NOT_FOUND | input | 否 |
| 40403 | USER_NOT_FOUND | input | 否 |
| 40404 | COURSE_NOT_FOUND | input | 否 |
| 40901 | USER_EMAIL_CONFLICT | input | 否 |
| **42901** | **QUOTA_EXCEEDED** | **quota** | **否**（明天再试） |
| **42902** | **RATE_LIMITED** | **rateLimit** | **是**（稍后重试） |
| 50001 | ACADEMIC_REQUEST_FAILED | server | 是 |
| 50002 | COURSE_PARSE_FAILED | server | 否 |
| 50003 | INTERNAL_ERROR | server | 是 |
| 50004 | AI_REQUEST_FAILED | server | 是 |

**42901 与 42902 都是 HTTP 429，但语义完全不同**：

- `42901` 配额用尽 → 用户今天不能再操作，**重试没有意义**，
  UI 不应显示「重试」按钮
- `42902` 限流 → 只是太频繁，**稍后重试即可**，UI 可以提示等待

客户端分类实现（三端一致）：

- Web：`src/constants/error-codes.ts` → `classifyError()`
- App：`lib/models/error_codes.dart` → `classifyError()`
- Backend：`common/ErrorCode.java`

### 1.4 版本兼容（legacy 通道）

不兼容的响应格式变更，旧端点保留一个版本的兼容通道：

- 请求头 `Accept: application/vnd.clark-aams.legacy+json`
- 响应头 `Deprecation: true` 与 `Sunset: <日期>`

| 端点 | 新格式 | legacy 格式 |
|---|---|---|
| `GET /api/v1/course-pickups/{code}` | 信封，`data` 为课程数组 | 裸课程数组 |
| `POST /api/v1/courses/recognize` | 信封，`data` 为课程数组 | 裸课程数组 |

**容器化客户端（App）需明确设置 `Accept`**：
默认请求不带该头，因此走新格式。

### 1.5 认证

系统支持两种身份，由 `GET /api/v1/auth/me` 统一回答：

| 身份 | 凭据载体 | 说明 |
|---|---|---|
| 用户 | `X-Clark-Aams-Login-Code` 请求头，或同源 Cookie 会话 | App 与 Web 均用 |
| 管理员 | 同源 Cookie 会话（登录后台时建立） | 仅 Web 后台 |

管理员身份优先于用户身份。

---

## 2. 端点清单

### 2.1 身份

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/v1/auth/me` | 无 | 统一身份查询，返回 `role`/`authenticated`/`user`/`displayName` |

响应 `data`：

```json
{
  "role": "USER",
  "authenticated": true,
  "displayName": "张三",
  "user": { "id": 1, "username": "张三", "email": "...", "loginCode": "", ... }
}
```

`role` ∈ `GUEST` / `USER` / `ADMIN`。
`user.loginCode` **恒为空串**——凭据不出现在高频读取接口中。

### 2.2 用户

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/v1/user/register` | 无 | 注册 |
| POST | `/api/v1/user/login` | 无 | 登录（邮箱密码 / 登录码 / 用户名密码）；**管理员也用这个接口**，响应含 `role` |
| POST | `/api/v1/user/logout` | 无 | 登出当前会话 |
| GET | `/api/v1/user/me` | **必需** | 当前用户资料（不含登录码）；未登录 **401** |
| POST | `/api/v1/user/login-code/rotate` | **必需** | 退出所有设备：吊销旧码换新码 |
| POST | `/api/v1/user/email/code` | **必需** | 发送邮箱验证码 |
| POST | `/api/v1/user/email/verify` | **必需** | 验证邮箱 |

登录码有效期为 **90 天**，轮换后旧码立即失效。

#### 登录响应里的 `role`（P1-1）

管理员与普通用户现在是**同一条登录路径**，二者都存于 `users` 表，
用 `role` 字段区分。因此 `POST /user/login` 与 `POST /user/register`
的响应都带 `role`（`user` / `admin`）。

客户端应**直接用这个字段决定进入哪个工作台**，不要再请求 `/auth/me`
来猜——那样中间会有一帧「已登录但不知道去哪」的空状态。

```
POST /api/v1/user/login  {"username":"root","password":"..."}
→ {"code":0,"data":{"username":"root","role":"admin", ...}}
```

一个必须注意的约束：**同一会话内两种身份互斥**。建立用户身份会清除
管理员标记，反之亦然。否则「管理员登录后在同一浏览器注册的普通用户」
会继承管理员权限（这是本轮修掉的一个真实越权缺陷）。

### 2.3 课程与导入

#### 课程资源的 `id`（P3-3）

`GET/POST/PUT /api/v1/courses/schedule` 返回的每门课都带 `id`（服务端主键）。
`PUT` 与 `DELETE` 的 `/{courseId}` 用的就是它。

**客户端必须使用返回值里的 `id`，不要按数组下标推算。**
此前后端不回传 id，Web 用「下标 + 1」猜测，列表顺序一变就会改错、删错课。
已在后端修复；三端各有测试钉住解析优先级。

`id` 为 `0` 表示「尚未落库」——只有请求入参会出现这个值，
响应里的课程都是已落库记录。

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/v1/courses/recognize` | **必需** + 配额 | 图片识别（multipart），响应头 `X-Quota-Remaining` |
| POST | `/api/v1/imports/{schoolId}/courses` | 无 | 教务系统导入；**已登录时自动落库** |
| POST | `/api/v1/imports/{schoolId}/courses/pickup` | 无 | 导入并生成取件码；已登录时自动落库 |
| POST | `/api/v1/imports/courses/html` | 无 | 粘贴 HTML 导入；已登录且解析出课程时自动落库 |

导入保持匿名可用（分享链接、免登录粘贴 HTML 都是有效场景）；
只有识别到登录用户时才写库。

### 2.3.1 我的课表（P3-1）

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/v1/courses/schedule` | **必需** | 我的课表，按天/节次排序 |
| PUT | `/api/v1/courses/schedule` | **必需** | 用一组课程**整体覆盖** |
| POST | `/api/v1/courses/schedule` | **必需** | 手工新增一门课 |
| PUT | `/api/v1/courses/schedule/{id}` | **必需** | 修改一门课，不存在 → `40404` |
| DELETE | `/api/v1/courses/schedule/{id}` | **必需** | 删除一门课，不存在 → `40404` |
| DELETE | `/api/v1/courses/schedule` | **必需** | 清空，返回 `{removed}` |

**响应中的课程对象**（同时给出真相源与派生字段，两端不必各算一遍）：

```json
{
  "name": "高等数学",
  "dayOfWeek": 1,
  "startLesson": 1,
  "endLesson": 2,
  "location": "A101",
  "teacher": "张老师",
  "rawTime": "",
  "weekRanges": [[1, 16]],
  "weekRule": "ALL",
  "weeks": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16],
  "startWeek": 1,
  "endWeek": 16
}
```

**写请求只接受真相源字段**，`weeks` 或 `weekRanges + weekRule` 给其一即可：

```json
{
  "name": "高等数学",
  "dayOfWeek": 1,
  "startLesson": 1,
  "endLesson": 2,
  "location": "A101",
  "teacher": "张老师",
  "weekRanges": [[1, 16]],
  "weekRule": "ALL"
}
```

| 字段 | 必填 | 校验 |
|---|---|---|
| `name` | 是 | 非空，否则 `40001` |
| `dayOfWeek` | 是 | 1-7 |
| `startLesson` / `endLesson` | 是 | `startLesson >= 1` 且 `endLesson >= startLesson` |
| `weekRanges` | 二选一 | `[[start, end], ...]`；畸形项被跳过而非报错 |
| `weeks` | 二选一 | 离散周次，服务端从中推导单双周 |
| `weekRule` | 否 | `ALL` / `ODD` / `EVEN`，大小写与空白容错；未知值退化为 `ALL` |

> 写请求**不**复用响应对象：响应含 `List<int[]>` 与派生字段，
> 作为 `@RequestBody` 反序列化不稳，且容易让客户端提交自相矛盾的周次。

#### 配额看板 `GET /api/v1/admin/quota`（P5-4）

返回今日用量快照，只读，需管理员会话。

```json
{"code":0,"data":{
  "totalCalls": 7,
  "scopes": [{"scope":"recognize","calls":7}],
  "users": [{"userId":4,"totalCalls":5,
             "byScope":[{"scope":"recognize","count":5}],
             "exhaustedScopes":[]}],
  "inMemoryOnly": true,
  "note": "统计来自服务内存计数：进程重启后归零……"
}}
```

**`inMemoryOnly` 必须被前端如实展示。** 统计来自 `QuotaGuard` 的内存计数：

- 进程重启即归零
- 多实例部署时各实例独立计数
- `acquireRate`（每分钟限流）先于 `acquireDaily` 执行，
  被限流拦下的请求**不计入**日配额

`exhaustedScopes` 用的是**该次调用当时真正生效的上限**，
而不是另读一份配置——两处来源一旦不一致，标记会失效且很难看出来。

### 2.4 取件码

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/v1/course-pickups` | **必需** + 配额 | 识别并创建取件码 |
| GET | `/api/v1/course-pickups/{code}` | 无 | 按取件码取课程（信封） |

取件码读取保持匿名：取件码本身即凭据，且分享场景需要免登录打开。

### 2.5 学校

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/v1/schools` | 无 | 学校列表 |
| GET | `/api/v1/schools/{schoolId}` | 无 | 学校详情 |
| GET | `/api/v1/schools/{schoolId}/lesson-times` | 无 | 作息时间 |
| GET | `/api/v1/schools/lesson-time-profiles` | 无 | 作息模板 |

### 2.6 题目解析

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/v1/questions/analyze` | **必需** + 配额 | 题目解析 |
| POST | `/api/v1/questions/analyze/fill-blank` | **必需** + 配额 | 填空题解析 |

### 2.7 版本

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| GET | `/api/v1/app/version` | 无 | 版本配置 |
| POST | `/api/v1/app/version/check` | 无 | 版本检查 |

### 2.8 后台（全部需 ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/auth/setup` | 初始化状态（**无需认证**） |
| POST | `/api/v1/admin/auth/register` | 首次注册管理员（**无需认证**，仅一次） |
| POST | `/api/v1/admin/auth/login` | 管理员登录（**无需认证**） |
| POST | `/api/v1/admin/auth/logout` | 登出 |
| GET/PUT | `/api/v1/admin/ai/config` | AI 配置 |
| GET/PUT | `/api/v1/admin/email/config` | 邮件配置 |
| GET/PUT/PATCH | `/api/v1/admin/app-version/config` | 版本配置 |
| POST | `/api/v1/admin/school-scaffold` | 学校脚手架 |
| GET/POST | `/api/v1/admin/course-pickups` | 取件码列表 / 创建 |
| GET/PUT/DELETE | `/api/v1/admin/course-pickups/{code}` | 取件码详情 / 更新 / 删除 |
| GET/DELETE | `/api/v1/admin/course-pickups/{code}/images/{fileName}` | 图片读取（二进制）/ 删除 |
| GET | `/api/v1/admin/quota` | 今日配额用量看板（只读，见 2.4） |

普通用户访问以上任何需 ADMIN 的端点 → **403 / 40301**；匿名 → **401 / 40101**。

### 2.9 配额

| 作用域 | 每日上限 | 每分钟上限 |
|---|---|---|
| `recognize` | 20 | 5 |
| `pickup` | 20 | 5 |
| `analyze` | 50 | 10 |

均可用 `clark-aams.quota.<scope>.daily-limit` / `.per-minute` 覆盖；
设为 `<= 0` 表示不限。

**当前配额状态保存在内存中**，多实例部署会成倍放大限额。
持久化属于 P5。

---

## 3. 已知缺口（P4 及以后处理）

本契约覆盖**当前已实现**的端点。以下在路线图中但尚未落地：

- `terms` 资源（学期）—— 判断为当前不需要：学期只有三个标量，
  不参与跨端判定，仍留客户端。详见路线图 P3-1 说明。
- `/auth/*` 完整令牌体系（刷新、登出全部设备）—— P1 部分完成
- OpenAPI 机器可读定义与代码生成 —— P2-1 未完成

**关于 P2-1 的说明**：原计划落地 `contracts/openapi.yaml`
并由 CI 生成 TS/Dart 类型。实际执行时判断为**时机不合适**：
P3 会新增 `courses` 资源、改写课表相关契约，
此时生成的类型会立刻过期。因此本轮先以本 Markdown 契约
固定**信封、状态码、错误码**这三项跨端不变式，
等端点稳定后再生成 OpenAPI 与类型。

### 3.1 本轮修掉的契约不一致

- `UserAuthFilter` 的题目保护前缀原为 `/api/v1/ai/questions/`，
  与控制器实际路径 `/api/v1/questions` 不符，该条过滤长期失效。
  已改正；控制器内另有 `requireUserId` 兜底，故此前未造成越权。
- `/user/me` 匿名访问返回 401（此前返回 200 + 空用户），
  与 §1.2「未登录用 401」一致。

