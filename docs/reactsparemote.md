# Clark AAMS 前端 React SPA 远程复刻开发文档

## 1. 文档目标

本文档用于把当前项目的**整个前端**迁移为 **React SPA**，并要求迁移后在视觉、文案、路由、交互、接口调用、状态行为上尽可能 **1:1 复刻当前项目**。

这不是“重新设计”文档，而是“严格复刻”文档。接手的 AI/开发者必须把当前静态多页面前端看作**唯一真源**。

本文档覆盖：

- 当前前端全部 HTML / CSS / JS / 资源文件
- 公共站点、用户中心、认证页、教务导入页、课表识别页、API 门户
- 用户指南静态页面
- 后台管理页面
- 当前前端依赖的接口、浏览器存储、页面跳转、动效、样式系统
- React SPA 迁移时必须保持一致的行为约束

## 2. 严格复刻约束

React SPA 版本必须满足以下约束：

1. 页面文案、信息结构、交互流程、接口调用路径必须与当前版本一致。
2. 视觉风格必须继承当前 `site/styles.css` 与 `admin/styles.css` 的设计语言，而不是重新发挥。
3. 用户注册后可直接登录；邮箱验证不阻塞使用；用户中心顶部提醒在已验证后消失，这一行为必须保留。
4. 用户中心当前版本已经被收缩为：
   - 页面主体只保留“仪表总览”和“账户资料”
   - 功能入口集中在左侧菜单
   - 页面内不再保留“功能入口”大卡片和“常用跳转”
5. 用户指南页必须保留目录侧栏、目录搜索、锚点滚动、高亮跟随、目录折叠展开逻辑。
6. 后台页必须保留现有功能，不可因为做 SPA 而删除。
7. 所有当前 URL 语义都要保留。即使最终用 React Router，也要支持当前访问路径。

## 3. 当前前端目录总览

当前前端根目录：`src/main/resources/static`

### 3.1 文件统计

- HTML：32 个
- JS：12 个
- CSS：2 个
- Markdown 文档资源：10 个
- PNG：2 个
- SVG：1 个
- ICO：1 个

### 3.2 前端文件清单

#### 页面 HTML

| 路径 | 行数 | 标题 | 说明 |
|---|---:|---|---|
| `index.html` | 114 | `Clark AAMS | 大学生课程表工作台` | 公开主页 |
| `api/index.html` | 138 | `接口文档 | Clark AAMS` | API 门户页 |
| `auth/login.html` | 88 | `登录 | Clark AAMS` | 用户登录页 |
| `auth/register.html` | 75 | `注册 | Clark AAMS` | 用户注册页 |
| `auth/verify.html` | 70 | `邮箱验证 | Clark AAMS` | 邮箱验证页 |
| `user/index.html` | 170 | `用户中心 | Clark AAMS` | 用户中心总览页 |
| `user/import.html` | 59 | `教务导入 | Clark AAMS` | 教务导入入口页 |
| `user/recognition.html` | 114 | `课表识别 | Clark AAMS` | OCR / 取件码工作台 |
| `course-import.html` | 57 | `教务导入 | Clark AAMS` | 学校课表 HTML 解析页 |
| `docs/user-guide/intro.html` | 82 | `用户指南 - 应用定位 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/first_use.html` | 85 | `用户指南 - 首次使用 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/main_interface.html` | 111 | `用户指南 - 主界面 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/import_course.html` | 209 | `用户指南 - 课程导入 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/add_course.html` | 110 | `用户指南 - 课程管理 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/semester_time.html` | 89 | `用户指南 - 学期与节次时间 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/app_settings.html` | 87 | `用户指南 - 应用设置 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/widgets.html` | 130 | `用户指南 - 桌面小组件与系统卡片 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/profile.html` | 85 | `用户指南 - 我的页面 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/account.html` | 91 | `用户指南 - 账号功能 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/xuexitong.html` | 96 | `用户指南 - 学习通作业辅助 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/about_updates.html` | 89 | `用户指南 - 关于与版本更新 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/data_network.html` | 94 | `用户指南 - 数据保存与网络说明 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/faq.html` | 126 | `用户指南 - 常见问题 | Clark AAMS` | 用户指南页 |
| `docs/user-guide/feature_list.html` | 132 | `用户指南 - 功能清单 | Clark AAMS` | 用户指南页 |
| `admin/login.html` | 169 | `Clark AAMS 后台登录` | 后台登录 |
| `admin/register.html` | 177 | `Clark AAMS 初始注册` | 后台初始化注册 |
| `admin/index.html` | 330 | `Clark AAMS 管理台` | 后台首页 |
| `admin/ai.html` | 327 | `AI 识别配置` | 后台 AI 配置 |
| `admin/email.html` | 266 | `邮箱配置` | 后台邮箱配置 |
| `admin/schools.html` | 416 | `学校适配器管理` | 后台学校管理 |
| `admin/versions.html` | 565 | `客户端版本管理` | 后台版本管理 |
| `admin/pickups.html` | 483 | `取件码管理` | 后台取件码管理 |

#### 前端脚本 JS

| 路径 | 行数 | 说明 |
|---|---:|---|
| `site/app.js` | 187 | 公共工具与 API 请求层 |
| `site/page-transitions.js` | 104 | 全站页面切换动画 |
| `site/home.js` | 134 | 主页逻辑 |
| `site/login.js` | 65 | 登录逻辑 |
| `site/register.js` | 43 | 注册逻辑 |
| `site/verify.js` | 91 | 邮箱验证逻辑 |
| `site/user.js` | 157 | 用户中心逻辑 |
| `site/user-import.js` | 60 | 用户导入入口逻辑 |
| `site/user-recognition.js` | 238 | OCR / 取件码工作台逻辑 |
| `site/course-import.js` | 269 | 学校课表采集 / 提交解析逻辑 |
| `site/guide.js` | 219 | 用户指南页目录与锚点逻辑 |
| `sw.js` | 12 | Service Worker 清理脚本 |

#### 样式 CSS

| 路径 | 行数 | 说明 |
|---|---:|---|
| `site/styles.css` | 2103 | 公开站点 / 用户站点 / 指南 / API / 用户中心统一样式 |
| `admin/styles.css` | 805 | 后台全部样式 |

#### 资源文件

| 路径 | 大小 | 说明 |
|---|---:|---|
| `site/logo.png` | 1494135 | 项目 Logo，导航、头像默认图使用 |
| `site/auth-bg.png` | 3638669 | 认证页背景图资源 |
| `site/globe-hero.svg` | 4829 | 首页 Hero 插画 |
| `site/favicon.ico` | 18311 | 站点图标 |

#### 静态 Markdown 文档资源

这些文档不是前端逻辑代码，但属于当前前端公开资源的一部分，SPA 迁移时必须决定是：

- 继续作为静态资源保留原路径；
- 或在 React 中做一个文档查看器并映射原链接。

| 路径 | 行数 | 标题 |
|---|---:|---|
| `docs/api-interface-and-auth-impact.md` | 424 | `Clark AAMS 后端接口与后台验证影响说明` |
| `docs/flutter-auth-question-analysis.md` | 388 | `Flutter 用户登录与做题辅助接口` |
| `docs/flutter-backend-integration.md` | 502 | `Flutter 接入 Clark AAMS Backend 开发文档` |
| `docs/flutter-in-app-update-development.md` | 599 | `Clark AAMS 软件内更新开发文档` |
| `docs/flutter-ocr-pickup-code-development.md` | 582 | `Flutter OCR 与取件码功能开发文档` |
| `docs/flutter-school-lesson-times.md` | 255 | `Flutter 学校节次时间接口` |
| `docs/project-class-methods.md` | 886 | `Clark AAMS Backend 类与方法功能梳理` |
| `docs/question-analysis-api.md` | 279 | `题目 AI 解析接口开发文档` |
| `docs/school-importer-development.md` | 201 | `学校适配器开发文档` |
| `docs/school-management-add-school-guide.md` | 386 | `后台学校管理新增学校适配文档` |

## 4. 当前前端路由与页面映射

### 4.1 公开用户侧

| 当前路径 | 当前文件 | React SPA 目标路由 |
|---|---|---|
| `/` | `index.html` | `/` |
| `/auth/login.html` | `auth/login.html` | `/auth/login` 或兼容 `/auth/login.html` |
| `/auth/register.html` | `auth/register.html` | `/auth/register` 或兼容 `/auth/register.html` |
| `/auth/verify.html` | `auth/verify.html` | `/auth/verify` 或兼容 `/auth/verify.html` |
| `/user/index.html` | `user/index.html` | `/user` 或兼容 `/user/index.html` |
| `/user/import.html` | `user/import.html` | `/user/import` 或兼容 `/user/import.html` |
| `/user/recognition.html` | `user/recognition.html` | `/user/recognition` 或兼容 `/user/recognition.html` |
| `/course-import.html` | `course-import.html` | `/course-import` 或兼容 `/course-import.html` |
| `/api/index.html` | `api/index.html` | `/api` 或兼容 `/api/index.html` |
| `/docs/user-guide/*.html` | 对应静态 HTML | React 文档路由或保留静态页 |

### 4.2 后台

| 当前路径 | 当前文件 | React SPA 目标路由 |
|---|---|---|
| `/admin/login.html` | `admin/login.html` | `/admin/login` |
| `/admin/register.html` | `admin/register.html` | `/admin/register` |
| `/admin/index.html` | `admin/index.html` | `/admin` |
| `/admin/ai.html` | `admin/ai.html` | `/admin/ai` |
| `/admin/email.html` | `admin/email.html` | `/admin/email` |
| `/admin/schools.html` | `admin/schools.html` | `/admin/schools` |
| `/admin/versions.html` | `admin/versions.html` | `/admin/versions` |
| `/admin/pickups.html` | `admin/pickups.html` | `/admin/pickups` |

### 4.3 后端已有重定向别名（迁移时需要兼容）

后端当前会做这些别名跳转：

- `/api`、`/api/`、`/api.html` -> `/api/index.html`
- `/user`、`/user/`、`/user.html`、`/my`、`/my/`、`/my.html` -> `/user/index.html`
- `/login` -> `/auth/login.html`
- `/register` -> `/auth/register.html`
- `/verify` -> `/auth/verify.html`
- `/admin` -> `/admin/index.html`

React SPA 版本必须至少兼容这些访问入口。

## 5. 全局运行机制

## 5.1 登录态与本地存储

当前项目前端并不持久化 JWT，而是持久化一个 **loginCode**。

- localStorage key：`clarkAamsLoginCode`
- 登录码来源：
  - 注册成功返回
  - 登录成功返回
  - URL query 参数 `loginCode`
- 前端在请求受保护接口时会发送请求头：
  - `X-Clark-Aams-Login-Code: <loginCode>`

React 版必须保留这个机制，不可擅自改成 token 名称。

## 5.2 页面切换动效

当前全站通过：

- `site/page-transitions.js`
- `site/styles.css` 内的 `body.page-entering / page-leaving`

实现淡出、模糊、位移动画。

SPA 迁移时要么：

1. 用 React Router + Framer Motion / CSS Transition 复刻相同动效；
2. 要么在根布局中保留同名 class 机制。

## 5.3 邮箱验证状态规则

当前正确规则是：

- `emailVerified === true` 才算“已验证”
- `emailBound` 不能单独视为已验证

用户体验规则：

- 注册后可以直接登录
- 未验证时只在用户中心顶部显示提醒
- 已验证后该提醒消失
- 不允许在用户中心其他区域重复提醒

## 5.4 头像规则

QQ 邮箱头像由 `site/app.js -> qqAvatarUrl(email)` 生成：

- 只有符合 `123456@qq.com` 这种格式时，才使用 `https://q1.qlogo.cn/g?...`
- 否则使用 `/site/logo.png`

React 版必须保留该规则。

## 6. 公共工具层：`site/app.js`

路径：`src/main/resources/static/site/app.js`

这是当前公开站点所有模块共享的底层运行时，React 迁移时应该变成：

- `src/shared/http.ts`
- `src/shared/auth.ts`
- `src/shared/navigation.ts`
- `src/shared/text.ts`

### 当前函数清单

- `safeRedirect(value, fallback)`
- `escapeHtml(value)`
- `copyText(value)`
- `getStoredLoginCode()`
- `setStoredLoginCode(loginCode)`
- `clearStoredLoginCode()`
- `navigateWithTransition(url, options)`
- `qqAvatarUrl(email)`
- `requestApi(url, options)`
- `fetchCurrentUser()`
- `restoreSessionWithLoginCode()`
- `ensureUserSession()`
- `captureLoginCodeFromUrl()`

### 逐功能说明

1. `safeRedirect`
   - 防止开放跳转
   - 只允许以 `/` 开头且不是 `//`
2. `escapeHtml`
   - 当前主要用于拼接 `innerHTML`
   - React 迁移后理论上可大幅减少使用
3. `copyText`
   - 优先用 `navigator.clipboard`
   - 回退到隐藏 `textarea`
4. `get/set/clearStoredLoginCode`
   - 统一读写 `localStorage`
5. `navigateWithTransition`
   - 在跳转前打 `page-leaving` class
   - 使用 `sessionStorage` 标记下一页执行 `page-entering`
6. `qqAvatarUrl`
   - 用 QQ 邮箱号映射头像
7. `requestApi`
   - 统一 fetch 包装
   - 自动加 `X-Clark-Aams-Login-Code`
   - 自动 JSON 序列化
   - 统一解析 `{ code, message, data }`
8. `fetchCurrentUser`
   - 调 `/api/v1/user/me`
9. `restoreSessionWithLoginCode`
   - 用 loginCode 调 `/api/v1/user/login`
10. `ensureUserSession`
   - 先调 `/user/me`
   - 不行再走登录码恢复
11. `captureLoginCodeFromUrl`
   - 从 URL 抓 `loginCode`
   - 写入 localStorage
   - 清理 query 参数

### React 迁移要求

- 这些函数不能散落在页面组件里，必须保留为共享工具层。
- `requestApi` 的错误语义必须与当前一致：后端 `code !== 0` 直接抛错。
- `ensureUserSession` 必须保留先 `/me` 后 `/login(loginCode)` 的两级恢复策略。

## 7. 全站样式系统：`site/styles.css`

路径：`src/main/resources/static/site/styles.css`  
总行数：2103

这是公开站点、认证页、用户中心、导入页、识别页、API 门户、指南页共用的**单一大样式文件**。

### 关键结构分段（按起始行附近）

- `:root` 设计令牌：第 7 行
- 全局 hidden / html / body / page transition：第 41–95 行附近
- 按钮、状态、表单、页脚、客服浮按钮：第 193–396 行附近
- 品牌、首页、Hero、功能卡：第 400–698 行附近
- Modal：第 703–744 行附近
- API 页、认证页、验证页：第 746–989 行附近
- 用户中心：第 994–1540 行附近
- 用户工作台 / 导入 / 识别：第 1555–1692 行附近
- 用户指南：第 1696–1947 行附近
- 响应式断点：
  - `@media (prefers-reduced-motion: reduce)` 第 1948 行
  - `@media (max-width: 1180px)` 第 1968 行
  - `@media (max-width: 920px)` 第 2002 行
  - `@media (max-width: 640px)` 第 2057 行

### 设计令牌

- 字体：
  - `Outfit`
  - `Plus Jakarta Sans`
- 主要色：
  - 背景偏白粉紫渐变
  - 主色 `#8057d7`
  - 主深色 `#6442b8`
- 边角：
  - `--radius-xl: 32px`
  - `--radius-lg: 24px`
  - `--radius-md: 18px`
  - `--radius-sm: 12px`

### React 迁移要求

- 样式不是单纯“差不多”，而是必须尽量复刻当前变量体系。
- 推荐迁移方式：
  - 保留一份 `globals.css`
  - 拆成 `home.css` / `auth.css` / `dashboard.css` / `guide.css` / `admin.css`
  - 但生成的类名和视觉效果要和当前接近

## 8. 后台样式系统：`admin/styles.css`

路径：`src/main/resources/static/admin/styles.css`  
总行数：805

### 关键结构分段（按起始行附近）

- `:root` 与基础通用块：第 1–394 行附近
- 后台 dashboard 布局：第 420 行后
- sidebar：第 592 行后
- 响应式断点：
  - `980px`
  - `680px`
  - `1100px`
  - `820px`
  - `560px`

### 后台视觉语言

- 与用户前台不同，是较偏工具后台的面板风格
- 大量使用：
  - `.panel`
  - `.status`
  - `.admin-*`

React SPA 迁移时，后台可作为独立布局子树处理。

## 9. 页面级前端说明

---

## 9.1 主页：`index.html` + `site/home.js`

### 当前页面职责

- 展示项目定位
- 展示三张核心能力卡
- 展示登录态状态 pill
- 提供“教务导入”弹层入口
- 提供“课表识别”快捷入口

### 页面结构

主要 DOM：

- `#home-user-avatar`
- `#home-user-label`
- `#home-import-button`
- `#home-user-status`
- `#school-import-modal`
- `#school-import-close`
- `#school-import-title`
- `#school-import-list`
- `#school-import-status`

### 文案定位

当前首页已经改成：

- 面向大学生课程表项目
- 不再使用 “Nanocloud Inspired” 之类无关文案
- 不再保留首页下方的大块长说明

### `home.js` 行为

1. 读取 `[data-user-entry]`
2. 若已登录，相关入口直接跳用户中心
3. 若未登录，跳登录页
4. 打开学校列表弹层
5. 调 `/api/v1/schools` 读取支持导入的学校
6. 点击学校后以新窗口打开 `/course-import.html?schoolId=...`

### 调用接口

- `GET /api/v1/user/me`
- `POST /api/v1/user/login`（通过 `ensureUserSession` 可能间接触发）
- `GET /api/v1/schools`

### React 拆分建议

- `HomePage`
- `HeroSection`
- `CapabilityCards`
- `SchoolImportModal`
- `LoginStatusBadge`

---

## 9.2 登录页：`auth/login.html` + `site/login.js`

### 当前页面职责

- 用户登录
- 支持两种方式切换：
  - QQ 邮箱 + 密码
  - 登录码

### 核心 DOM

- `#login-form`
- `#login-status`
- `.auth-switch`
- `[data-login-method]`
- `[data-login-panel]`

### 交互逻辑

1. 读取 URL `redirect`
2. 修正“注册”链接，保持 redirect
3. 认证方式开关切换 panel
4. 提交时根据当前方式构造 payload
5. 请求成功后写入 `localStorage.loginCode`
6. 跳转 `redirectUrl`

### 接口

- `POST /api/v1/user/login`

### React 拆分建议

- `LoginPage`
- `LoginMethodSwitch`
- `EmailPasswordForm`
- `LoginCodeForm`

---

## 9.3 注册页：`auth/register.html` + `site/register.js`

### 当前页面职责

- 创建普通用户账号
- 注册成功后直接进入用户中心

### 核心 DOM

- `#register-form`
- `#register-status`

### 逻辑

1. 读取 `redirect`
2. 修正“去登录”链接
3. 检查两次密码一致
4. `POST /api/v1/user/register`
5. 保存 `loginCode`
6. 跳转 redirect

### 接口

- `POST /api/v1/user/register`

---

## 9.4 邮箱验证页：`auth/verify.html` + `site/verify.js`

### 当前页面职责

- 独立邮箱验证流程页
- 不在该页重复渲染大量提醒，只保留简洁表单

### 核心 DOM

- `#verify-summary`
- `#verify-page-form`
- `#verify-email`
- `#send-email-code-button`
- `#verify-submit-button`
- `#verify-status`

### 逻辑

1. `captureLoginCodeFromUrl()`
2. `ensureUserSession()`
3. 已登录时回填当前邮箱
4. 发验证码：
   - `POST /api/v1/user/email/code`
5. 验证验证码：
   - `POST /api/v1/user/email/verify`
6. 成功后额外调用一次 `/api/v1/user/me`
7. 然后跳转 `/user/index.html?refreshUser=1`

### 必须保留的行为

- 已验证用户进入该页时，发送按钮、提交按钮、邮箱输入和验证码输入都应禁用
- 验证成功后返回用户中心必须刷新真实状态

---

## 9.5 用户中心页：`user/index.html` + `site/user.js`

### 当前页面职责

当前版本的用户中心已经被压缩成：

- 顶部邮箱验证提醒
- 左侧集中导航
- 页面主体只保留：
  - 仪表总览
  - 账户资料

### 当前页面结构

关键 DOM：

- `#dashboard-avatar`
- `#dashboard-email`
- `#dashboard-user-name`
- `#dashboard-email-alert`
- `#summary-title`
- `#summary-copy`
- `#summary-avatar`
- `#metric-analysis-count`
- `#metric-email-state`
- `#metric-email-copy`
- `#account-data-list`
- `#logout-button`
- `#account-status`

### 左侧菜单规则

当前左侧菜单只保留：

- 总览
  - 仪表总览
- 功能
  - 教务导入
  - 课表识别
- 帮助
  - 使用指南
  - 返回首页

注意：

- “账户资料”不再作为左侧总览菜单项存在
- 页面内不再展示“功能入口”卡片
- 页面内不再展示“常用跳转”

### 账户区规则

当前登录码被放在账户资料的行内：

- 左侧字段名：登录码
- 右侧值区域：`code + 复制按钮`
- 页面下方只剩退出登录按钮

### `site/user.js` 行为

1. 启动时调用 `ensureUserSession()`
2. 如果未登录，跳转登录页并带 redirect
3. 根据 `emailVerified` 渲染状态
4. 顶部提醒只由 `emailVerified` 决定
5. `pageshow` 时若带 `refreshUser=1` 或页面来自 bfcache，则重新拉用户状态
6. 行内 “复制” 按钮点击时复制 `currentUser.loginCode`
7. 退出登录调用 `/api/v1/user/logout`

### 已修复的关键行为

- 顶部邮箱提示不会再因为旧 session / bfcache 停留
- 验证成功后回到用户中心会强制刷新用户状态

### React 拆分建议

- `UserDashboardPage`
- `DashboardSidebar`
- `ProfileSummaryCard`
- `AccountInfoCard`
- `EmailVerificationAlert`

---

## 9.6 用户导入页：`user/import.html` + `site/user-import.js`

### 当前页面职责

- 仅作为学校导入入口页
- 展示可用学校列表
- 点击学校进入真正的 `course-import.html`

### DOM

- `#import-user-summary`
- `#import-school-list`
- `#import-school-status`

### 行为

1. 检查登录态
2. 拉 `/api/v1/schools`
3. 只保留 `capabilities.academicImport === true` 的学校
4. 点击后跳 `/course-import.html?schoolId=...`

---

## 9.7 用户识别页：`user/recognition.html` + `site/user-recognition.js`

### 当前页面职责

- 上传课表截图并识别
- 用取件码回查课程 JSON

### 关键 DOM

- `#recognition-user-summary`
- `#upload-form`
- `#pickup-form`
- `#drop-zone`
- `#image-input`
- `#file-list`
- `#tool-status`
- `#tool-hint`
- `#pickup-code`
- `#progress-bar`
- `#course-json`
- `#pickup-modal`
- `#pickup-modal-code`
- `#copy-pickup-code-button`

### 逻辑

1. 登录态校验
2. Tab 模式切换：
   - 上传识别
   - 取件码回查
3. 支持点击和拖拽图片
4. 上传识别使用 `XMLHttpRequest`
   - 原因：需要上传进度
5. 成功后展示 JSON
6. 成功后弹出取件码 modal
7. 支持复制取件码
8. 支持回查：
   - `GET /api/v1/course-pickups/{code}`

### 接口

- `POST /api/v1/course-pickups`
- `GET /api/v1/course-pickups/{code}`

### React 拆分建议

- `RecognitionPage`
- `RecognitionModeTabs`
- `UploadRecognizerPanel`
- `PickupLookupPanel`
- `PickupCodeModal`
- `JsonResultViewer`

---

## 9.8 学校导入解析页：`course-import.html` + `site/course-import.js`

### 当前页面职责

这是整个项目里最特殊的前端页。它不是普通表单页，而是：

- 根据 `schoolId` 读取学校配置
- 打开学校原站登录页
- 通过复制脚本 / 粘贴 HTML / 页面消息采集课表源数据
- 提交后端进行解析

### 关键 DOM

- `#school-name`
- `#import-lead`
- `#parse-button`
- `#open-login-button`
- `#copy-collector-button`
- `#manual-html`
- `#import-status`
- `#import-result-panel`
- `#course-result`

### 核心逻辑

1. 从 URL 读 `schoolId`
2. 调 `/api/v1/schools/{schoolId}`
3. 根据学校 ID 生成不同说明文案
4. 打开学校原站新窗口
5. 生成浏览器采集脚本
6. 支持三种输入来源：
   - `window.postMessage`
   - 手动粘贴 HTML
   - 采集脚本直接请求后端
7. 解析调用：
   - `POST /api/v1/imports/{schoolId}/courses/pickup`

### 特殊点

- 内置 `collectorScriptFor(schoolId)`
- 对 `jit` / `jlu` 有特殊处理
- 会尝试在原站 DOM 中查找课程表 table

### React 迁移要求

这一页**不能简化**。必须保留：

- schoolId 读取
- 打开原站
- 采集脚本复制
- HTML 文本框
- 解析结果面板

---

## 9.9 API 门户：`api/index.html`

### 当前职责

- 不是 Swagger
- 是一个手写的 API 与项目文档入口索引页

### 当前内容

- 公开接口入口
- 项目文档入口
- 课表识别入口链接

### React 迁移建议

可作为 `ApiPortalPage` 静态实现，不需要复杂状态。

---

## 9.10 用户指南系统：`docs/user-guide/*.html` + `site/guide.js`

### 用户指南页共性

所有 `docs/user-guide/*.html` 都共享：

- `body.guide-page`
- 同一套 header
- 左侧 `guide-sidebar`
- 搜索框 `.guide-search`
- 右侧文章 `.guide-article`
- 公共脚本 `site/guide.js`
- 公共样式 `site/styles.css`

### `guide.js` 当前行为

1. 识别当前文件名
2. 根据 hash 高亮侧栏当前链接
3. 支持 collection 折叠展开
4. 点击同页锚点时：
   - 阻止默认跳转
   - 平滑滚动
   - 高亮目标 section
5. 侧栏搜索：
   - 过滤链接
   - 打开匹配分组
6. 页面滚动时动态更新当前激活链接

### 用户指南页逐文件说明

#### `docs/user-guide/intro.html`

- 标题：应用定位
- H1：应用定位
- 用途：介绍项目是什么

#### `docs/user-guide/first_use.html`

- 标题：首次使用
- H2：
  - 选择学校
  - 使用其他方式导入

#### `docs/user-guide/main_interface.html`

- 标题：主界面
- H2：
  - 首页
  - 课程表

#### `docs/user-guide/import_course.html`

- 标题：课程导入
- H2：
  - OCR 图片识别导入
  - 取件码导入
  - 教务系统导入
  - JSON 批量导入

#### `docs/user-guide/add_course.html`

- 标题：课程管理
- H2：
  - 手动添加课程
  - 编辑课程
  - 删除课程
  - 课程颜色

#### `docs/user-guide/semester_time.html`

- 标题：学期与节次时间

#### `docs/user-guide/app_settings.html`

- 标题：应用设置

#### `docs/user-guide/widgets.html`

- 标题：桌面小组件与系统卡片
- H2：
  - 支持的小组件尺寸
  - 2x2 小组件显示内容
  - 4x2 小组件显示内容
  - 不同系统的小组件添加方式
- H3：
  - ColorOS

#### `docs/user-guide/profile.html`

- 标题：我的页面

#### `docs/user-guide/account.html`

- 标题：账号功能

#### `docs/user-guide/xuexitong.html`

- 标题：学习通作业辅助

#### `docs/user-guide/about_updates.html`

- 标题：关于与版本更新

#### `docs/user-guide/data_network.html`

- 标题：数据保存与网络说明

#### `docs/user-guide/faq.html`

- 标题：常见问题
- 包含多个 `h2 + id` 锚点
- 这是最依赖锚点导航的一页

#### `docs/user-guide/feature_list.html`

- 标题：功能清单

### React 迁移建议

最优做法不是继续维护 15 份重复 HTML，而是：

1. 抽出一个 `GuideLayout`
2. 抽出一份 `guideNavConfig`
3. 把文章内容转换为 markdown / mdx / json sections
4. 保持原 URL slug 不变

但视觉、目录层级、搜索和锚点行为必须与当前一致。

---

## 9.11 后台前端：`admin/*.html` + `admin/styles.css`

后台当前不是复用 `site/app.js`，而是每个页面都用**内联脚本**直接 `fetch`。

React 迁移时建议把后台作为 `AdminApp` 子路由统一重构，但逻辑必须完整保留。

### 9.11.1 `admin/login.html`

- 标题：后台管理登录
- 关键 DOM：
  - `#loginForm`
  - `#status`
- 内联函数：
  - `redirectToRegisterWhenNeeded`
- 调用接口：
  - `GET /api/v1/admin/auth/setup`
  - `POST /api/v1/admin/auth/login`

### 9.11.2 `admin/register.html`

- 标题：初始化管理员账号
- 关键 DOM：
  - `#registerForm`
  - `#status`
- 内联函数：
  - `ensureRegistrationAvailable`
- 调用接口：
  - `GET /api/v1/admin/auth/setup`
  - `POST /api/v1/admin/auth/register`

### 9.11.3 `admin/index.html`

- 标题：Clark AAMS 管理台
- H2：
  - 后台能力概览
  - 模块状态
- 关键 DOM：
  - `#healthBadge`
  - `#dashboardHealth`
  - `#dashboardPickups`
  - `#schoolsState`
  - `#latestVersion`
  - `#minimumVersion`
  - `#aiState`
  - `#emailState`
  - `#pickupState`
- 内联函数：
  - `load`
- 接口：
  - `/api/v1/health`
  - `/api/v1/admin/app-version/config`
  - `/api/v1/admin/ai/config`
  - `/api/v1/admin/email/config`
  - `/api/v1/admin/course-pickups`

### 9.11.4 `admin/ai.html`

- 标题：AI 识别配置
- H2：
  - 模型配置
  - 识别测试
- 关键 DOM：
  - `#configForm`
  - `#saveStatus`
  - `#recognizeForm`
  - `#recognizeStatus`
  - `#resultText`
- 内联函数：
  - `loadConfig`
  - `saveConfig`
  - `recognize`
  - `setStatus`
- 接口：
  - `GET /api/v1/admin/ai/config`
  - `PUT /api/v1/admin/ai/config`
  - `POST /api/v1/courses/recognize`

### 9.11.5 `admin/email.html`

- 标题：邮箱配置
- H2：
  - 发送器配置
  - 当前状态
- 关键 DOM：
  - `#emailForm`
  - `#saveStatus`
  - `#currentStatus`
- 内联函数：
  - `loadConfig`
  - `saveConfig`
  - `fillForm`
  - `setStatus`
- 接口：
  - `GET /api/v1/admin/email/config`
  - `PUT /api/v1/admin/email/config`

### 9.11.6 `admin/schools.html`

- 标题：学校适配器管理
- 关键 DOM：
  - `#refreshSchools`
  - `#schoolRows`
  - `#scaffoldForm`
  - `#status`
- 内联函数：
  - `loadSchools`
  - `setStatus`
  - `escapeHtml`
- 接口：
  - `GET /api/v1/schools`
  - `POST /api/v1/admin/school-scaffold`

### 9.11.7 `admin/versions.html`

- 标题：客户端版本管理
- H2：
  - 版本配置
  - 版本校验
- H3：
  - 下载源
- 关键 DOM：
  - `#versionForm`
  - `#sourceList`
  - `#saveStatus`
  - `#checkForm`
  - `#checkStatus`
  - `#checkResult`
- 内联函数：
  - `loadConfig`
  - `saveConfig`
  - `runCheck`
  - `fillForm`
  - `readForm`
  - `renderSources`
  - `syncSource`
  - `renderResult`
  - `setStatus`
- 接口：
  - `GET /api/v1/admin/app-version/config`
  - `PUT /api/v1/admin/app-version/config`
  - `POST /api/v1/app/version/check`

### 9.11.8 `admin/pickups.html`

- 标题：取件码管理
- H2：
  - 记录
  - 详情
  - 图片
- 关键 DOM：
  - `#recordList`
  - `#detailPanel`
  - `#manualCreateForm`
  - `#manualJsonText`
  - `#jsonText`
  - `#saveJsonButton`
  - `#copyCodeButton`
  - `#deleteRecordButton`
- 内联函数：
  - `requestJson`
  - `loadList`
  - `renderList`
  - `loadDetail`
  - `createManualRecord`
  - `renderDetail`
  - `saveJson`
  - `deleteRecord`
  - `deleteImage`
  - `parseJsonResponse`
  - `formatJson`
  - `setStatus`
  - `escapeHtml`
  - `escapeAttr`
- 接口：
  - `GET /api/v1/admin/course-pickups`
  - `GET /api/v1/admin/course-pickups/{code}`
  - `POST /api/v1/admin/course-pickups`
  - `PUT /api/v1/admin/course-pickups/{code}`
  - `DELETE /api/v1/admin/course-pickups/{code}`
  - `DELETE /api/v1/admin/course-pickups/{code}/images/{fileName}`
  - 图片展示：
    - `/api/v1/admin/course-pickups/{code}/images/{fileName}`

## 10. 用户指南之外的静态文档资源处理

`static/docs/*.md` 当前被这些页面直接链接：

- API 门户页
- 后台学校管理页

React SPA 迁移方案有两种：

1. 保持这些 `.md` 原样静态托管；
2. 用文档页组件读取 markdown 渲染。

如果目标是“1:1 最低风险复刻”，推荐方案 1。

## 11. Service Worker：`sw.js`

只有 12 行，当前作用非常单一：

- install 时 `skipWaiting`
- activate 时：
  - 清空所有 cache
  - 注销自己

这不是离线缓存，而是**清理旧 SW**。

React SPA 迁移时如果使用 Vite / CRA / PWA 插件，必须小心不要引入与当前相反的缓存行为。

## 12. React SPA 推荐目录（为了复刻，不是为了重设计）

建议结构：

```text
src/
  app/
    router.tsx
    layouts/
      PublicLayout.tsx
      DashboardLayout.tsx
      WorkspaceLayout.tsx
      GuideLayout.tsx
      AdminLayout.tsx
  shared/
    api/
      requestApi.ts
      adminApi.ts
    auth/
      loginCode.ts
      ensureUserSession.ts
    motion/
      pageTransition.ts
    utils/
      safeRedirect.ts
      qqAvatarUrl.ts
      copyText.ts
  pages/
    home/
    api/
    auth/
      login/
      register/
      verify/
    user/
      dashboard/
      import/
      recognition/
    course-import/
    guide/
      [slug]/
    admin/
      login/
      register/
      dashboard/
      ai/
      email/
      schools/
      versions/
      pickups/
  content/
    guide/
    docs/
  assets/
    logo.png
    auth-bg.png
    globe-hero.svg
  styles/
    globals.css
    public.css
    dashboard.css
    workspace.css
    guide.css
    admin.css
```

## 13. 1:1 复刻时必须保留的接口列表

### 用户侧

- `POST /api/v1/user/register`
- `POST /api/v1/user/login`
- `POST /api/v1/user/logout`
- `GET /api/v1/user/me`
- `POST /api/v1/user/email/code`
- `POST /api/v1/user/email/verify`
- `GET /api/v1/schools`
- `GET /api/v1/schools/{schoolId}`
- `GET /api/v1/schools/{schoolId}/lesson-times`
- `POST /api/v1/imports/{schoolId}/courses/pickup`
- `POST /api/v1/course-pickups`
- `GET /api/v1/course-pickups/{code}`
- `GET /api/v1/app/version`

### 后台

- `GET /api/v1/admin/auth/setup`
- `POST /api/v1/admin/auth/register`
- `POST /api/v1/admin/auth/login`
- `GET /api/v1/admin/ai/config`
- `PUT /api/v1/admin/ai/config`
- `GET /api/v1/admin/email/config`
- `PUT /api/v1/admin/email/config`
- `POST /api/v1/admin/school-scaffold`
- `GET /api/v1/admin/app-version/config`
- `PUT /api/v1/admin/app-version/config`
- `GET /api/v1/admin/course-pickups`
- `GET /api/v1/admin/course-pickups/{code}`
- `POST /api/v1/admin/course-pickups`
- `PUT /api/v1/admin/course-pickups/{code}`
- `DELETE /api/v1/admin/course-pickups/{code}`
- `DELETE /api/v1/admin/course-pickups/{code}/images/{fileName}`
- `GET /api/v1/health`
- `POST /api/v1/app/version/check`
- `POST /api/v1/courses/recognize`

## 14. 迁移实现注意事项

1. 不要把当前多页面拆得面目全非。
2. 左侧用户中心菜单、邮箱验证流程、识别页工作台、导入页采集脚本都是关键业务，不要弱化。
3. 用户指南不要做成单纯长文页面，必须保留目录交互。
4. 后台不要忘记；它虽然不是用户站，但属于前端的一部分。
5. 当前 CSS 大量通过 body class 切场景，React 版可以继续沿用该思路。
6. 尽量避免把所有内容塞进一个巨型组件；但输出效果必须和现在一致。

## 15. 交付验收清单

完成 React SPA 后，至少逐项验收：

- 首页视觉与当前一致
- 登录 / 注册 / 邮箱验证流程一致
- 用户中心只有顶部未验证提示
- 用户中心左侧菜单集中导航，页面内没有功能入口大块
- 登录码是资料行内“复制”按钮
- 教务导入学校列表能打开 `course-import`
- `course-import` 能复制采集脚本并提交解析
- OCR 上传识别能显示进度、结果和取件码弹窗
- 取件码回查可用
- 用户指南目录搜索、锚点滚动、激活高亮可用
- API 门户静态索引页可用
- 后台所有页面可用
- 所有当前链接路径仍能访问

## 16. 结论

当前项目前端不是单一首页，而是由以下几块组成的完整前端系统：

1. 公开主页与 API 门户
2. 用户认证系统
3. 用户中心与两个独立工作页
4. 一个特殊的学校导入解析页
5. 一整套用户指南页
6. 一整套后台管理页
7. 一套共享样式与共享工具层

如果目标是“让其他 AI 直接开始做 React SPA 并 1:1 复刻当前项目”，那么它必须把上面所有内容全部视为必须迁移对象，而不是只做首页和用户中心。

