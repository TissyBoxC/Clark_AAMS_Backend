# Clark AAMS Backend

`Clark AAMS Backend` 是一个纯后端 Spring Boot 服务，负责课程表导入、用户账号、邮箱验证、OCR/取件码、管理员配置和版本检查等 API。

当前仓库已移除内置静态前端页面，不再提供 `index.html`、`/admin`、`/user`、`/auth` 之类的页面入口。前端应独立部署，并通过 HTTP 调用本服务的 API。

## 本地运行

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

默认端口：`8080`

健康检查：

```text
GET http://localhost:8080/api/v1/health
```

## 管理员初始化

可通过环境变量预置管理员账号：

```text
CLARK_AAMS_ADMIN_USERNAME=<your-admin-username>
CLARK_AAMS_ADMIN_PASSWORD=<your-admin-password>
```

如果未预置，可通过 API 首次初始化：

```text
POST /api/v1/admin/auth/register
```

登录接口：

```text
POST /api/v1/admin/auth/login
```

受保护后台接口范围：

```text
/api/v1/admin/**
```

以下接口不需要管理员已登录：

```text
/api/v1/admin/auth/register
/api/v1/admin/auth/login
/api/v1/admin/auth/logout
/api/v1/admin/auth/setup
```

## 主要接口

公开接口：

- `GET /api/v1/health`
- `GET /api/v1/schools`
- `GET /api/v1/schools/{schoolId}`
- `POST /api/v1/imports/{schoolId}/courses`
- `GET /api/v1/app/version`
- `POST /api/v1/app/version/check`
- `POST /api/v1/courses/recognize`
- `POST /api/v1/course-pickups`
- `GET /api/v1/course-pickups/{code}`
- `POST /api/v1/user/register`
- `POST /api/v1/user/login`
- `POST /api/v1/user/logout`
- `GET /api/v1/user/me`
- `POST /api/v1/user/email/code`
- `POST /api/v1/user/email/verify`

后台接口：

- `GET /api/v1/admin/auth/setup`
- `POST /api/v1/admin/auth/register`
- `POST /api/v1/admin/auth/login`
- `POST /api/v1/admin/auth/logout`
- `GET /api/v1/admin/app-version/config`
- `PUT /api/v1/admin/app-version/config`
- `GET /api/v1/admin/ai/config`
- `PUT /api/v1/admin/ai/config`
- `GET /api/v1/admin/email/config`
- `PUT /api/v1/admin/email/config`
- `GET /api/v1/admin/course-pickups`
- `PUT /api/v1/admin/course-pickups/{code}`
- `DELETE /api/v1/admin/course-pickups/{code}`
- `POST /api/v1/admin/school-scaffold`

## 跨域部署

如果前端部署在 `https://www.clarkhub.cn`，后端部署在 `https://api.clarkhub.cn`，则两者属于不同源，后端必须开启 CORS。

项目当前已允许以下来源访问 `/api/**`：

- `https://www.clarkhub.cn`
- `https://clarkhub.cn`
- `http://localhost:3000`
- `http://127.0.0.1:3000`

如需调整，可通过配置项覆盖：

```properties
clark-aams.cors.allowed-origin-patterns=https://www.clarkhub.cn,https://clarkhub.cn,http://localhost:3000,http://127.0.0.1:3000
```

## Cookie / Session 注意事项

本项目部分登录态依赖 `HttpSession`：

- 管理员登录
- 用户登录

如果前端跨域并且要携带 session cookie，请同时满足：

- 前端请求启用凭据，例如 `fetch(..., { credentials: 'include' })`
- 后端 CORS 启用 `allowCredentials(true)`
- session cookie 使用 `SameSite=None`
- session cookie 使用 `Secure`

如果用户侧改为使用请求头 `X-Clark-Aams-Login-Code` 恢复登录，则对跨站 cookie 的依赖会降低，但管理员接口仍然依赖 session。
