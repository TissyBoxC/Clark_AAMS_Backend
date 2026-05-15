# Flutter OCR 与取件码功能开发文档

本文档用于指导 `D:\service\github\Clark_AAMS` Flutter 客户端接入当前 Spring Boot 后端的课表图片 OCR 和取件码导入能力。本文只描述客户端落地方案，不要求修改后端接口。

## 项目总结

### 后端项目

`Clark_AAMS_Backend` 是 Spring Boot 4 / Java 21 后端，当前承担以下职责：

- 项目主页与后台管理静态页面托管。
- 学校列表、学校配置与教务系统课表导入。
- 客户端版本检查与下载源配置。
- 后台首次注册、登录保护与管理入口。
- AI 视觉模型配置，支持 OpenAI-compatible `chat/completions`。
- 课表图片 OCR：上传一张或多张图片，识别为课程 JSON。
- 取件码：OCR 成功后生成 8 位取件码，保存原图和 JSON，后续可按取件码取回 JSON。

运行时文件默认与 jar 同级：

```text
admin-config.json
ai-config.json
client-version.json
course-pickups/
```

### Flutter 项目

`Clark_AAMS` 是 Flutter 本地课表客户端，当前结构中已有接入点：

- `lib/config/backend_config.dart`：后端地址配置。
- `lib/services/clark_backend_api.dart`：后端 API 封装。
- `lib/models/course.dart`：课程模型，已经兼容后端 OCR JSON 字段：
  - `day` -> `dayOfWeek`
  - `startSection` -> `startLesson`
  - `endSection` -> `endLesson`
  - `position` -> `location`
  - `weeks` -> `activeWeeks`
- `lib/services/course_json_importer.dart`：把课程 JSON 数组解析成 `List<Course>`。
- `lib/screens/course_settings_page.dart`：课程管理页，已预留导入菜单：
  - `使用OCR导入`
  - `使用取件码导入`
  - 目前两个入口仍显示“暂未配置”。

因此客户端落地重点是：补 API 方法、补图片选择/取件码输入 UI，然后复用现有 JSON 解析和导入流程。

## 后端接口

### 1. OCR 上传并生成取件码

```http
POST /api/v1/course-pickups
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `images` | file[] | 一张或多张课表图片 |

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "pickupCode": "A7K9Q2TX",
    "courses": [
      {
        "name": "计算方法[05]",
        "teacher": "宫成春",
        "position": "南岭-逸夫楼-A206",
        "day": 2,
        "startSection": 7,
        "endSection": 8,
        "weeks": [1, 2, 3, 5, 6],
        "rawTime": "1-3周,5-16周,星期2,第7节-第8节"
      }
    ]
  }
}
```

### 2. 按取件码获取课程 JSON

```http
GET /api/v1/course-pickups/{code}
```

响应是纯 JSON 数组，不包 `ApiResponse`：

```json
[
  {
    "name": "计算方法[05]",
    "teacher": "宫成春",
    "position": "南岭-逸夫楼-A206",
    "day": 2,
    "startSection": 7,
    "endSection": 8,
    "weeks": [1, 2, 3, 5, 6],
    "rawTime": "1-3周,5-16周,星期2,第7节-第8节"
  }
]
```

## 客户端依赖建议

当前 `pubspec.yaml` 已有 `http`，但没有图片选择库。建议新增：

```yaml
dependencies:
  image_picker: ^1.1.2
```

如果希望 Android 文件管理器多选体验更强，也可以用 `file_picker`。本功能只需要图片，`image_picker` 更轻量。

Android 13+ 通常不需要手写存储权限即可通过系统 Photo Picker 选图；旧 Android 版本如果遇到权限问题，再按插件说明补权限。

## 后端地址配置

当前 `lib/config/backend_config.dart` 写死为：

```dart
static const String scheme = 'http';
static const String serverIp = '118.25.131.69';
static const int serverPort = 8080;
```

如果后端已通过 Nginx 代理到 `https://clarkhub.cn`，建议改成：

```dart
class BackendConfig {
  const BackendConfig._();

  static const String scheme = 'https';
  static const String host = 'clarkhub.cn';
  static const int? serverPort = null;

  static String get baseUrl => '$scheme://$host';

  static Uri uri(
    String path, [
    Map<String, Object?> queryParameters = const {},
  ]) {
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    final query = <String, String>{};
    for (final entry in queryParameters.entries) {
      final value = entry.value;
      if (value != null) {
        query[entry.key] = '$value';
      }
    }

    return Uri(
      scheme: scheme,
      host: host,
      port: serverPort,
      path: normalizedPath,
      queryParameters: query.isEmpty ? null : query,
    );
  }
}
```

本地开发时可以临时使用局域网 IP 或模拟器地址。

## API 封装改造

修改 `lib/services/clark_backend_api.dart`。

### 1. 新增导入

```dart
import 'dart:io';
```

### 2. 超时时间调整

OCR 涉及图片上传和 AI 识别，20 秒偏短。建议：

```dart
static const _timeout = Duration(seconds: 180);
```

如果担心影响普通接口，可以单独给 OCR 方法使用 180 秒。

### 3. 新增响应模型

可放在 `clark_backend_api.dart` 底部，或新建 `lib/models/course_pickup.dart`。

```dart
class CoursePickupResult {
  const CoursePickupResult({
    required this.pickupCode,
    required this.courses,
  });

  final String pickupCode;
  final List<Course> courses;
}
```

### 4. 新增 OCR 上传方法

```dart
Future<CoursePickupResult> recognizeCourseImages(List<File> images) async {
  if (images.isEmpty) {
    throw const ApiException(40001, '请先选择课表图片');
  }

  final request = http.MultipartRequest(
    'POST',
    BackendConfig.uri('/api/v1/course-pickups'),
  );

  for (final image in images) {
    request.files.add(
      await http.MultipartFile.fromPath('images', image.path),
    );
  }

  final streamed = await _client.send(request).timeout(_timeout);
  final response = await http.Response.fromStream(streamed);
  final data = _unwrap(response);

  if (data is! Map) {
    throw const ApiException(50003, 'OCR响应格式错误');
  }

  final pickupCode = data['pickupCode'] as String? ?? '';
  final coursesJson = data['courses'];
  if (pickupCode.isEmpty || coursesJson is! List) {
    throw const ApiException(50003, 'OCR课程数据响应格式错误');
  }

  return CoursePickupResult(
    pickupCode: pickupCode,
    courses: _parseCourseList(coursesJson),
  );
}
```

### 5. 新增取件码获取方法

`GET /api/v1/course-pickups/{code}` 返回纯 JSON 数组，因此不能走 `_unwrap`。

```dart
Future<List<Course>> getCoursesByPickupCode(String code) async {
  final normalized = code.trim().toUpperCase();
  if (normalized.length != 8) {
    throw const ApiException(40001, '请输入8位取件码');
  }

  final response = await _client
      .get(BackendConfig.uri('/api/v1/course-pickups/$normalized'))
      .timeout(_timeout);

  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw ApiException(
      response.statusCode,
      '取件码获取失败：HTTP ${response.statusCode}',
    );
  }

  final decoded = jsonDecode(utf8.decode(response.bodyBytes));
  if (decoded is! List) {
    throw const ApiException(50003, '取件码课程数据格式错误');
  }
  return _parseCourseList(decoded);
}
```

### 6. 抽取课程列表解析

把 `importCourses` 里的课程解析逻辑抽成私有方法，供教务导入、OCR、取件码共用。

```dart
List<Course> _parseCourseList(List coursesJson) {
  return [
    for (var index = 0; index < coursesJson.length; index++)
      if (coursesJson[index] is Map<String, Object?>)
        Course.fromJson(
          coursesJson[index] as Map<String, Object?>,
          fallbackId: index + 1,
        )
      else if (coursesJson[index] is Map)
        Course.fromJson(
          (coursesJson[index] as Map).cast<String, Object?>(),
          fallbackId: index + 1,
        ),
  ];
}
```

然后 `importCourses` 中改为：

```dart
return _parseCourseList(coursesJson);
```

## 课程管理页落地

修改 `lib/screens/course_settings_page.dart`。

### 1. 新增导入

```dart
import 'dart:io';
import 'package:image_picker/image_picker.dart';
```

### 2. 补 OCR 分支

当前逻辑：

```dart
case _ImportMethod.ocr:
  _showUnavailable('OCR导入暂未配置');
```

改为：

```dart
case _ImportMethod.ocr:
  await _importFromOcr();
```

新增方法：

```dart
Future<void> _importFromOcr() async {
  final picker = ImagePicker();
  final picked = await picker.pickMultiImage(imageQuality: 92);
  if (picked.isEmpty) {
    return;
  }

  _showUnavailable('正在上传图片并识别，请稍候');
  try {
    final result = await ClarkBackendApi.instance.recognizeCourseImages(
      picked.map((item) => File(item.path)).toList(),
    );
    if (!mounted) {
      return;
    }

    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('识别完成'),
        content: SelectableText('取件码：${result.pickupCode}'),
        actions: [
          TextButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: result.pickupCode));
              Navigator.of(context).pop();
            },
            child: const Text('复制取件码'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('继续导入'),
          ),
        ],
      ),
    );

    if (result.courses.isEmpty) {
      _showUnavailable('未识别到课程');
      return;
    }
    final imported = await _importCoursesAndSetTermStart(result.courses);
    if (mounted && imported) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('成功导入 ${result.courses.length} 门课程')),
      );
    }
  } catch (error) {
    if (!mounted) {
      return;
    }
    _showUnavailable('OCR导入失败：$error');
  }
}
```

建议后续把“正在识别”改成阻塞式 Dialog 或页面级 loading，避免用户重复点击。

### 3. 补取件码分支

当前逻辑：

```dart
case _ImportMethod.pickupCode:
  _showUnavailable('取件码导入暂未配置');
```

改为：

```dart
case _ImportMethod.pickupCode:
  await _importFromPickupCode();
```

新增方法：

```dart
Future<void> _importFromPickupCode() async {
  final code = await showDialog<String>(
    context: context,
    builder: (context) => const _PickupCodeDialog(),
  );
  if (code == null || code.isEmpty) {
    return;
  }

  try {
    final courses = await ClarkBackendApi.instance.getCoursesByPickupCode(code);
    if (!mounted) {
      return;
    }
    if (courses.isEmpty) {
      _showUnavailable('取件码没有对应课程');
      return;
    }
    final imported = await _importCoursesAndSetTermStart(courses);
    if (mounted && imported) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('成功导入 ${courses.length} 门课程')),
      );
    }
  } catch (error) {
    if (!mounted) {
      return;
    }
    _showUnavailable('取件码导入失败：$error');
  }
}
```

### 4. 新增取件码输入 Dialog

可放在 `course_settings_page.dart` 底部。

```dart
class _PickupCodeDialog extends StatefulWidget {
  const _PickupCodeDialog();

  @override
  State<_PickupCodeDialog> createState() => _PickupCodeDialogState();
}

class _PickupCodeDialogState extends State<_PickupCodeDialog> {
  final TextEditingController _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('输入取件码'),
      content: TextField(
        controller: _controller,
        maxLength: 8,
        textCapitalization: TextCapitalization.characters,
        decoration: const InputDecoration(
          border: OutlineInputBorder(),
          hintText: '例如 A7K9Q2TX',
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () {
            final code = _controller.text.trim().toUpperCase();
            if (code.length != 8) {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('请输入8位取件码')),
              );
              return;
            }
            Navigator.of(context).pop(code);
          },
          child: const Text('导入'),
        ),
      ],
    );
  }
}
```

## 首次启动与导入流程

当前导入最终都应调用：

```dart
_importCoursesAndSetTermStart(courses)
```

该方法会：

1. 询问学期第一周第一天。
2. 为课程分配默认颜色。
3. 弹出课程颜色确认/调整对话框。
4. 调用 `store.importCourses(...)` 保存课程。
5. 设置 `isFirstLaunch = false`。

OCR 和取件码导入都应该复用这条路径，避免绕过现有本地存储和首次启动状态逻辑。

## 错误处理建议

客户端应重点处理这些错误：

| 场景 | 建议提示 |
| --- | --- |
| 未选择图片 | `请先选择课表图片` |
| OCR 超时 | `识别耗时较长，请稍后重试或减少图片数量` |
| AI 配置缺失 | `服务器暂未配置 OCR 服务` |
| 取件码不存在 | `取件码不存在或已被删除` |
| JSON 格式异常 | `课程数据格式异常，请联系管理员` |
| 网络不可达 | `无法连接服务器，请检查网络` |

## UI 落地建议

最小可用版本：

- 在课程管理页导入菜单中启用 OCR 和取件码。
- OCR 使用系统图片选择器多选。
- OCR 成功后弹窗展示取件码，并提供复制按钮。
- 取件码导入使用输入框弹窗。
- 导入后继续使用现有“选择学期开始日期”和“课程颜色确认”流程。

体验增强版本：

- 新增独立 `OcrImportPage`，展示图片预览、上传进度、识别状态和取件码。
- 对 OCR 返回课程先进入预览列表，允许删除误识别课程后再导入。
- 保存最近一次取件码到本地，便于用户找回。

## 测试清单

### API 测试

- 后端配置 AI 后，Flutter 上传 1 张图片能返回取件码和课程。
- 上传 2 张以上图片能正常识别并合并课程。
- 输入有效取件码能导入课程。
- 输入不存在的取件码能显示错误。
- 后端未配置 AI 时，OCR 上传能显示明确失败提示。

### 数据测试

- `position` 字段能正确映射到 `Course.location`。
- `day/startSection/endSection` 能正确映射到星期和节次。
- `weeks` 能正确映射到 `activeWeeks`，并在课表中按周显示。
- 没有教师字段时不影响导入。

### 回归测试

- 原有教务系统导入仍可用。
- 原有批量 JSON 添加仍可用。
- 首次启动导入后进入主页。
- 已有课程不会被 OCR 导入覆盖，只会追加。

## 推荐实施顺序

1. 调整 `BackendConfig` 到正式域名。
2. 添加 `image_picker` 依赖。
3. 在 `ClarkBackendApi` 中新增 OCR 和取件码方法。
4. 在 `CourseSettingsPage` 中启用两个导入分支。
5. 复用 `_importCoursesAndSetTermStart` 完成落库。
6. 真机测试图片选择、上传、取件码导入和离线错误提示。

