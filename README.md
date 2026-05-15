# Clark AAMS Backend

Clark AAMS Backend 是珂拉课程表的 Spring Boot 后端服务，配套 Flutter 客户端使用。它负责学校配置、教务系统课表导入、客户端版本检查、版本配置管理，以及项目介绍页和后台管理页的静态托管。

## 功能概览

- 学校列表与学校登录配置接口
- 金陵科技学院 JIT 教务系统课表导入适配
- 客户端版本检查与下载源配置
- 后台管理页，支持版本配置维护和学校适配脚手架生成
- 后台登录验证
- 项目介绍页
- 版本配置文件持久化，替换 jar 后不丢配置

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 运行时 | Java 21 |
| 框架 | Spring Boot 4.0.6 |
| 构建 | Gradle Wrapper |
| Web | Spring Web MVC |
| HTML 解析 | jsoup |
| 测试 | JUnit / Spring MockMvc |

## 快速开始

### 运行测试

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

### 本地启动

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

默认端口为 `8080`。

启动后可访问：

| 地址 | 说明 |
| --- | --- |
| `http://localhost:8080/` | 项目介绍页 |
| `http://localhost:8080/api/v1/health` | 健康检查 |
| `http://localhost:8080/admin/` | 后台管理入口 |
| `http://localhost:8080/docs/api-interface-and-auth-impact.md` | 接口与鉴权说明 |

### 构建 jar

```bash
./gradlew bootJar
```

产物位置：

```text
build/libs/Clark_AAMS_Backend-0.0.1-SNAPSHOT.jar
```

运行：

```bash
java -jar build/libs/Clark_AAMS_Backend-0.0.1-SNAPSHOT.jar
```

## 后台登录

后台页面和后台管理接口需要登录。

账号密码可以通过环境变量配置：

```text
账号：<your-admin-username>
密码：<your-admin-password>
```

受保护范围：

Set credentials with environment variables before starting:

```text
CLARK_AAMS_ADMIN_USERNAME=<your-admin-username>
CLARK_AAMS_ADMIN_PASSWORD=<your-admin-password>
```

如果未配置环境变量且本地不存在 `admin-config.json`，首次访问 `/admin` 会自动进入 `/admin/register.html` 创建管理员账号。

```text
/admin
/admin/**
/api/v1/admin/**
```

登录接口：

```text
POST /api/v1/admin/auth/login
```

## 版本配置持久化

客户端版本配置会保存到独立 JSON 文件，避免每次替换后端 jar 后重新配置。

默认行为：

- 文件名：`client-version.json`
- 以 jar 运行时，文件位于 jar 同级目录
- 本地 `bootRun` 时，文件位于项目工作目录
- 文件不存在时，启动后按 `application.properties` 默认值自动创建
- 文件存在时，启动后读取该文件，不覆盖
- 后台保存版本配置后，会同步写回该文件

可通过配置指定自定义路径：

```properties
clark-aams.client-version.config-path=/path/to/client-version.json
```

`client-version.json` 是运行时文件，已在 `.gitignore` 中忽略。

## 主要接口

### 公开接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/health` | 健康检查 |
| `GET` | `/api/v1/schools` | 获取学校列表 |
| `GET` | `/api/v1/schools/{schoolId}` | 获取单个学校配置 |
| `POST` | `/api/v1/imports/{schoolId}/courses` | 导入课表 |
| `GET` | `/api/v1/app/version` | Query 参数形式版本检查 |
| `POST` | `/api/v1/app/version/check` | JSON 请求体形式版本检查 |
| `POST` | `/api/v1/courses/recognize` | 上传一张或多张课表图片，返回课程 JSON 数组 |
| `POST` | `/api/v1/course-pickups` | 上传一张或多张课表图片，返回取件码和课程 JSON |
| `GET` | `/api/v1/course-pickups/{code}` | 按 8 位取件码返回课程 JSON |

### 后台接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/admin/auth/login` | 后台登录 |
| `POST` | `/api/v1/admin/auth/register` | 首次初始化管理员账号 |
| `GET` | `/api/v1/admin/auth/setup` | 读取后台初始化状态 |
| `POST` | `/api/v1/admin/auth/logout` | 后台退出 |
| `GET` | `/api/v1/admin/app-version/config` | 读取版本配置 |
| `PUT` | `/api/v1/admin/app-version/config` | 更新版本配置 |
| `GET` | `/api/v1/admin/ai/config` | 读取 AI 识别配置 |
| `PUT` | `/api/v1/admin/ai/config` | 更新 AI Base URL、API Key 和模型 |
| `GET` | `/api/v1/admin/course-pickups` | 读取取件码记录 |
| `PUT` | `/api/v1/admin/course-pickups/{code}` | 更新取件码对应 JSON |
| `DELETE` | `/api/v1/admin/course-pickups/{code}` | 删除取件码记录和图片 |
| `POST` | `/api/v1/admin/school-scaffold` | 生成学校适配脚手架 |

完整接口说明见：

```text
src/main/resources/static/docs/api-interface-and-auth-impact.md
```

## Flutter 客户端影响

当前 Flutter 客户端只调用公开接口：

```text
/api/v1/schools
/api/v1/schools/{schoolId}
/api/v1/imports/{schoolId}/courses
/api/v1/app/version/check
```

后台登录验证只保护 `/admin/**` 和 `/api/v1/admin/**`，不会影响现有 Flutter 客户端流程。

## 项目结构

```text
src/main/java/io/github/tissyboxc/clark_aams_backend/
  admin/          后台登录、后台页面入口、学校脚手架
  appversion/     客户端版本检查与版本配置持久化
  common/         统一响应、错误码、异常处理
  config/         CORS 配置
  health/         健康检查
  importers/      课表导入服务与学校适配器
  school/         学校配置接口与模型
  security/       请求日志过滤器

src/main/resources/static/
  index.html      项目介绍页
  admin/          后台管理静态页面
  docs/           接口与开发文档
  site/           介绍页样式和资源
```

## 新增学校适配

学校适配相关文档：

```text
src/main/resources/static/docs/school-importer-development.md
```

基本步骤：

1. 实现 `SchoolConfigProvider`，提供学校基础配置。
2. 实现 `SchoolCourseImporter`，完成教务系统请求和课程转换。
3. 将最终课程转换为统一 `CourseDto`。
4. 添加测试样例，覆盖 HTML 解析和导入接口行为。

也可以登录后台，通过学校管理页面生成适配器脚手架。

## 部署注意事项

- 生产部署时，请通过环境变量或首次注册配置并保护好后台登录账号密码，不要将真实凭据提交到仓库。
- `admin-config.json`、`client-version.json`、`ai-config.json` 和 `course-pickups/` 应与 jar 放在同一目录，便于升级 jar 时保留运行时配置。
- 如果后台管理页和后端分开部署，需要额外处理 CORS、Cookie 和 Session。
- 运行时生成文件、构建目录和 IDE 目录已通过 `.gitignore` 忽略。
