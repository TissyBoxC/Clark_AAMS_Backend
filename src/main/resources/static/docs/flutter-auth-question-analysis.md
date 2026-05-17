# Flutter 用户登录与做题辅助接口

本文档面向 Flutter 客户端接入。当前客户端推荐只暴露两种登录方式：QQ 邮箱 + 密码登录、登录码登录。注册、邮箱验证码认定和退出登录接口也一并列出，方便完整开发用户中心。

## 通用约定

- Base URL 按部署环境配置，例如 `https://your-domain.com`。
- JSON 接口统一返回 `ApiResponse`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

- `code = 0` 表示成功；非 `0` 表示业务错误。
- 登录状态优先使用服务端 Session。Flutter 需要保存并携带 `JSESSIONID` Cookie。
- 为适配部分代理或 WebView Cookie 不稳定场景，客户端也可以在登录后保存 `loginCode`，后续请求带请求头 `X-Clark-Aams-Login-Code`。后端会用该登录码恢复 Session。
- `loginCode` 等同长期登录凭据，前端不要明文展示；本地建议使用 `flutter_secure_storage` 保存。

## 用户字段

登录、注册、`/me` 等接口会返回用户资料：

```json
{
  "id": 1,
  "username": "demo",
  "email": "123456@qq.com",
  "loginCode": "ABCD2345EFGH",
  "emailBound": false,
  "emailVerified": false,
  "questionAnalysisCount": 0
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | number | 用户 ID，按注册顺序递增，唯一 |
| `username` | string | 用户名，不要求唯一 |
| `email` | string | QQ 邮箱，唯一 |
| `loginCode` | string | 登录码，注册后生成，前端应加密/遮罩展示 |
| `emailBound` | boolean | 邮箱是否完成验证码认定 |
| `emailVerified` | boolean | 邮箱验证码是否验证通过 |
| `questionAnalysisCount` | number | 做题辅助成功调用次数 |

## 注册账号

`POST /api/v1/user/register`

注册不会直接完成邮箱绑定认定，只会保存 QQ 邮箱并生成登录码。用户可在注册后任意时间发送邮箱验证码完成认定。

请求体：

```json
{
  "username": "demo",
  "password": "your-password",
  "qqEmail": "123456@qq.com"
}
```

成功响应 `data` 为用户资料，并会写入服务端 Session。

常见错误：

| HTTP | code | 说明 |
| --- | --- | --- |
| 400 | `40001` | 用户名、密码或 QQ 邮箱为空 |
| 409 | `40901` | QQ 邮箱已被绑定 |

## QQ 邮箱登录

`POST /api/v1/user/login`

请求体：

```json
{
  "qqEmail": "123456@qq.com",
  "password": "your-password"
}
```

成功响应 `data` 为用户资料，并会写入服务端 Session。

## 登录码登录

`POST /api/v1/user/login`

请求体：

```json
{
  "loginCode": "ABCD2345EFGH"
}
```

登录码登录不需要密码。Flutter 登录页可以提供“邮箱登录 / 登录码登录”两个模式，底层都调用同一个 `/api/v1/user/login`。

登录失败返回：

```json
{
  "code": 40101,
  "message": "登录码无效",
  "data": null
}
```

## 检查当前用户

`GET /api/v1/user/me`

请求头建议：

```http
Cookie: JSESSIONID=xxxx
X-Clark-Aams-Login-Code: ABCD2345EFGH
```

`X-Clark-Aams-Login-Code` 不是必须的，但建议 Flutter 在已保存登录码时带上，用于 Session 丢失后的自动恢复。

已登录时返回用户资料；未登录且没有有效登录码时返回：

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

## 退出登录

`POST /api/v1/user/logout`

请求体可传空 JSON：

```json
{}
```

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

Flutter 退出登录时还应删除本地保存的 `loginCode`。

## 发送邮箱验证码

`POST /api/v1/user/email/code`

需要登录。后端会读取持久化的 `email-config.json` 发送验证码。当前实现支持 SMTP 邮箱发送，也支持 HTTP JSON Provider。

请求体：

```json
{
  "qqEmail": "123456@qq.com"
}
```

成功响应 `data` 为用户资料。发送验证码后，`emailBound` 和 `emailVerified` 会保持 `false`，直到用户提交验证码认定。

SMTP 配置约定：

| 配置项 | 说明 |
| --- | --- |
| `enabled` | 必须为 `true` 才会实际发送 |
| `provider` | 建议填 `smtp` 或 `qq-smtp` |
| `endpoint` | SMTP 地址，例如 `smtp.qq.com:465`、`smtps://smtp.qq.com:465`、`smtp://smtp.example.com:587` |
| `senderAddress` | 发件邮箱，也是 SMTP 用户名 |
| `senderName` | 发件人显示名称 |
| `verificationSubject` | 验证码邮件标题 |
| `htmlTemplate` | 验证码 HTML 邮件模板 |
| `apiKey` | SMTP 授权码或密码 |

`htmlTemplate` 支持以下占位符：

| 占位符 | 替换内容 |
| --- | --- |
| `{{code}}` / `${code}` / `%CODE%` | 6 位随机数字验证码 |
| `{{email}}` / `${email}` | 收件邮箱 |
| `{{subject}}` / `${subject}` | 邮件标题 |
| `{{senderName}}` / `${senderName}` | 发件人显示名称 |

HTTP Provider 配置约定：

- `provider` 不包含 `smtp` 且 `endpoint` 不是 SMTP 地址时，后端会向 `endpoint` 发起 `POST application/json`。
- 如果配置了 `apiKey`，后端会带 `Authorization: Bearer <apiKey>`。
- 请求体包含：`to`、`code`、`subject`、`senderAddress`、`senderName`、`text`、`html`。
- 其中 `code` 是后端随机生成的 6 位数字验证码，`html` 是使用 `htmlTemplate` 替换占位符后的最终 HTML。

常见错误：

| HTTP | code | 说明 |
| --- | --- | --- |
| 401 | `40101` | 未登录 |
| 409 | `40901` | QQ 邮箱已被其他用户绑定 |

## 验证邮箱

`POST /api/v1/user/email/verify`

需要登录。

请求体：

```json
{
  "code": "123456"
}
```

成功后返回用户资料，`emailBound = true`，`emailVerified = true`。

## 做题辅助

`POST /api/v1/questions/analyze`

需要登录。客户端应携带 `JSESSIONID` Cookie，或携带 `X-Clark-Aams-Login-Code` 让后端恢复 Session。

请求体支持 `snake_case` 和 `camelCase`：

```json
{
  "question_type": "单选题",
  "question_content": "下列关于 Java 接口的说法正确的是？",
  "options": [
    "A. 接口可以直接实例化",
    "B. 接口中的方法默认都是 private",
    "C. 类可以实现多个接口",
    "D. 接口不能包含常量"
  ]
}
```

等价写法：

```json
{
  "questionType": "单选题",
  "questionContent": "题干内容",
  "options": ["A. 选项一", "B. 选项二"]
}
```

未登录响应：

```json
{
  "code": 40101,
  "message": "请先登录后再使用做题辅助",
  "data": null
}
```

成功响应 `data` 为 AI 解析结果。只有 AI 解析成功后，后端才会把当前用户的 `questionAnalysisCount` 加 `1`。前端需要刷新次数时调用 `GET /api/v1/user/me`。

## Dio 推荐封装

依赖建议：

```yaml
dependencies:
  dio: ^5.0.0
  cookie_jar: ^4.0.0
  dio_cookie_manager: ^3.0.0
  flutter_secure_storage: ^9.0.0
```

示例：

```dart
final dio = Dio(BaseOptions(
  baseUrl: 'https://your-domain.com',
  contentType: Headers.jsonContentType,
  responseType: ResponseType.json,
));

final cookieJar = CookieJar();
final secureStorage = FlutterSecureStorage();

void setupBackendClient() {
  dio.interceptors.add(CookieManager(cookieJar));
  dio.interceptors.add(InterceptorsWrapper(
    onRequest: (options, handler) async {
      final loginCode = await secureStorage.read(key: 'clark_login_code');
      if (loginCode != null && loginCode.isNotEmpty) {
        options.headers['X-Clark-Aams-Login-Code'] = loginCode;
      }
      handler.next(options);
    },
  ));
}

Future<Map<String, dynamic>> loginByEmail(String email, String password) async {
  final response = await dio.post('/api/v1/user/login', data: {
    'qqEmail': email,
    'password': password,
  });
  final user = response.data['data'] as Map<String, dynamic>;
  await secureStorage.write(key: 'clark_login_code', value: user['loginCode'] as String);
  return user;
}

Future<Map<String, dynamic>> loginByCode(String loginCode) async {
  final response = await dio.post('/api/v1/user/login', data: {
    'loginCode': loginCode,
  });
  final user = response.data['data'] as Map<String, dynamic>;
  await secureStorage.write(key: 'clark_login_code', value: user['loginCode'] as String);
  return user;
}

Future<Map<String, dynamic>> currentUser() async {
  final response = await dio.get('/api/v1/user/me');
  return response.data['data'] as Map<String, dynamic>;
}

Future<void> logout() async {
  await dio.post('/api/v1/user/logout', data: {});
  await secureStorage.delete(key: 'clark_login_code');
}

Future<Map<String, dynamic>> analyzeQuestion({
  required String type,
  required String content,
  required List<String> options,
}) async {
  final response = await dio.post('/api/v1/questions/analyze', data: {
    'question_type': type,
    'question_content': content,
    'options': options,
  });
  return response.data['data'] as Map<String, dynamic>;
}
```

## 错误处理建议

Flutter 侧不要只看 HTTP 状态码，也要检查 `code`：

```dart
void ensureOk(Response response) {
  final body = response.data as Map<String, dynamic>;
  if (body['code'] != 0) {
    throw Exception(body['message'] ?? '请求失败');
  }
}
```

常见业务码：

| code | 含义 |
| --- | --- |
| `0` | 成功 |
| `40001` | 请求参数错误 |
| `40101` | 登录状态无效、登录失败或未登录 |
| `40901` | QQ 邮箱冲突 |
| `50004` | AI 请求失败 |

## 前端展示注意

- 登录码是敏感凭据，只显示遮罩和复制按钮。
- 邮箱注册后不等于邮箱已认定，只有 `/api/v1/user/email/verify` 成功后才展示为已绑定。
- 做题辅助入口调用前应先检查 `/api/v1/user/me`，`id > 0` 才允许提交。
