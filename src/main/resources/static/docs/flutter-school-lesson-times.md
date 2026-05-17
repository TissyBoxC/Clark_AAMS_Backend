# Flutter 学校节次时间接口

本文档用于 Flutter 客户端落地“选择学校后，按学校获取第 1-13 节课开始时间”的功能。

## 使用场景

前端在导入课程或初始化课表设置时，用户会先选择学校。不同学校的上课时间可能不同，因此客户端不应写死节次时间，而应在选中学校后请求后端时间接口。

推荐流程：

1. 用户选择学校。
2. Flutter 调用 `GET /api/v1/schools/{schoolId}/lesson-times`，或在没有学校选择时调用 `GET /api/v1/schools/lesson-time-profiles`。
3. 将返回的 `lessons` 保存到本地课程表设置。
4. 渲染课表、导入课程或计算当前课程时，使用本地保存的学校节次时间。

## 按学校获取

```http
GET /api/v1/schools/{schoolId}/lesson-times
```

示例：

```http
GET /api/v1/schools/jit/lesson-times
```

不需要登录。

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "schoolId": "jit",
    "profile": "general-a",
    "lessons": [
      {"lesson": 1, "startTime": "08:30"},
      {"lesson": 2, "startTime": "09:20"},
      {"lesson": 3, "startTime": "10:25"},
      {"lesson": 4, "startTime": "11:15"},
      {"lesson": 5, "startTime": "13:30"},
      {"lesson": 6, "startTime": "14:20"},
      {"lesson": 7, "startTime": "15:25"},
      {"lesson": 8, "startTime": "16:15"},
      {"lesson": 9, "startTime": ""},
      {"lesson": 10, "startTime": ""},
      {"lesson": 11, "startTime": ""},
      {"lesson": 12, "startTime": ""},
      {"lesson": 13, "startTime": ""}
    ]
  }
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schoolId` | string | 学校 ID |
| `profile` | string | 后端使用的时间配置名 |
| `lessons` | array | 第 1-13 节课时间 |
| `lessons[].lesson` | number | 节次，固定 1-13 |
| `lessons[].startTime` | string | 上课开始时间，格式 `HH:mm`；空字符串表示该节次时间暂未配置 |

接口不返回下课时间 `endTime`。

## 获取通用配置

如果前端流程不要求用户选择学校，可以直接获取后端内置的两套通用配置。

```http
GET /api/v1/schools/lesson-time-profiles
```

不需要登录。

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "schoolId": "",
      "profile": "general-a",
      "lessons": [
        {"lesson": 1, "startTime": "08:30"},
        {"lesson": 2, "startTime": "09:20"},
        {"lesson": 3, "startTime": "10:25"},
        {"lesson": 4, "startTime": "11:15"},
        {"lesson": 5, "startTime": "13:30"},
        {"lesson": 6, "startTime": "14:20"},
        {"lesson": 7, "startTime": "15:25"},
        {"lesson": 8, "startTime": "16:15"},
        {"lesson": 9, "startTime": ""},
        {"lesson": 10, "startTime": ""},
        {"lesson": 11, "startTime": ""},
        {"lesson": 12, "startTime": ""},
        {"lesson": 13, "startTime": ""}
      ]
    },
    {
      "schoolId": "",
      "profile": "general-b",
      "lessons": [
        {"lesson": 1, "startTime": "08:00"},
        {"lesson": 2, "startTime": "08:55"},
        {"lesson": 3, "startTime": "10:00"},
        {"lesson": 4, "startTime": "10:55"},
        {"lesson": 5, "startTime": "13:30"},
        {"lesson": 6, "startTime": "14:25"},
        {"lesson": 7, "startTime": "14:30"},
        {"lesson": 8, "startTime": "16:25"},
        {"lesson": 9, "startTime": ""},
        {"lesson": 10, "startTime": ""},
        {"lesson": 11, "startTime": ""},
        {"lesson": 12, "startTime": ""},
        {"lesson": 13, "startTime": ""}
      ]
    }
  ]
}
```

## 当前内置配置

后端当前预留两套通用配置：`general-a`、`general-b`。

| profile | 第1节 | 第2节 | 第3节 | 第4节 | 第5节 | 第6节 | 第7节 | 第8节 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `general-a` | 08:30 | 09:20 | 10:25 | 11:15 | 13:30 | 14:20 | 15:25 | 16:15 |
| `general-b` | 08:00 | 08:55 | 10:00 | 10:55 | 13:30 | 14:25 | 14:30 | 16:25 |

第 9-13 节目前返回空字符串，前端应按“未配置”处理。

## 学校列表关联

`GET /api/v1/schools` 和 `GET /api/v1/schools/{schoolId}` 会返回：

```json
{
  "id": "jit",
  "lessonTimeProfile": "general-a"
}
```

Flutter 可以用 `lessonTimeProfile` 做缓存判断，但实际节次时间仍应以 `/lesson-times` 接口返回为准。

## Dart Model

```dart
class LessonTime {
  const LessonTime({
    required this.lesson,
    required this.startTime,
  });

  final int lesson;
  final String startTime;

  factory LessonTime.fromJson(Map<String, dynamic> json) {
    return LessonTime(
      lesson: json['lesson'] as int,
      startTime: (json['startTime'] as String?) ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
        'lesson': lesson,
        'startTime': startTime,
      };
}

class SchoolLessonTimes {
  const SchoolLessonTimes({
    required this.schoolId,
    required this.profile,
    required this.lessons,
  });

  final String schoolId;
  final String profile;
  final List<LessonTime> lessons;

  factory SchoolLessonTimes.fromJson(Map<String, dynamic> json) {
    final rawLessons = (json['lessons'] as List<dynamic>? ?? const []);
    return SchoolLessonTimes(
      schoolId: (json['schoolId'] as String?) ?? '',
      profile: (json['profile'] as String?) ?? '',
      lessons: rawLessons
          .map((item) => LessonTime.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}
```

## Dio 调用示例

```dart
Future<SchoolLessonTimes> fetchSchoolLessonTimes(String schoolId) async {
  final response = await dio.get('/api/v1/schools/$schoolId/lesson-times');
  final body = response.data as Map<String, dynamic>;
  if (body['code'] != 0) {
    throw Exception(body['message'] ?? '获取学校节次时间失败');
  }
  return SchoolLessonTimes.fromJson(body['data'] as Map<String, dynamic>);
}

Future<List<SchoolLessonTimes>> fetchGeneralLessonTimeProfiles() async {
  final response = await dio.get('/api/v1/schools/lesson-time-profiles');
  final body = response.data as Map<String, dynamic>;
  if (body['code'] != 0) {
    throw Exception(body['message'] ?? '获取通用节次时间失败');
  }
  final data = body['data'] as List<dynamic>;
  return data
      .map((item) => SchoolLessonTimes.fromJson(item as Map<String, dynamic>))
      .toList();
}
```

## 本地保存建议

建议以学校 ID 为维度保存：

```json
{
  "schoolId": "jit",
  "profile": "general-a",
  "lessons": [
    {"lesson": 1, "startTime": "08:30"}
  ],
  "updatedAt": "2026-05-16T16:00:00+08:00"
}
```

客户端可在以下时机刷新：

- 用户首次选择学校。
- 用户未选择学校但进入时间配置页。
- 学校 ID 变更。
- 后端学校配置 `lessonTimeProfile` 变更。
- 用户手动点击“刷新学校配置”。

## UI 处理建议

- `startTime` 为空时，不展示该节次的具体时间，或显示“未配置”。
- 不要根据第 1-8 节自动推算第 9-13 节。
- 不要在 Flutter 内写死学校作息表。
- 课程导入接口返回的课程仍使用 `startLesson` / `endLesson` 表示节次范围，节次对应的实际时间由本文接口提供。
