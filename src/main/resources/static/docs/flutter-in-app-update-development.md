# Clark AAMS 软件内更新开发文档

本文档用于落地 Clark AAMS Flutter 客户端的软件内更新能力。目标是让客户端通过后端版本控制模块获取最新版本，并在 Android 端直接下载 GitHub Releases 中的 APK 安装包。

本文档只描述实现方案和改造点，不包含对现有代码的直接修改。

## 目标

当前 Flutter 客户端已经具备版本检查能力，但更新动作是通过外部浏览器打开下载地址。目标改造为：

1. Flutter 客户端启动或用户手动检查更新时，请求后端版本检查接口。
2. 后端根据版本控制模块中的 `latestVersion` 返回 APK 直链。
3. Flutter 客户端在应用内下载 APK，并显示下载进度。
4. 下载完成后拉起 Android 系统安装器。
5. 强制更新时不允许关闭更新弹窗绕过更新。

APK 下载地址模板固定为：

```text
https://github.com/TissyBoxC/Clark_AAMS/releases/download/v{latestVersion}/app-release.apk
```

示例：

```text
latestVersion = 1.0.4
apkUrl = https://github.com/TissyBoxC/Clark_AAMS/releases/download/v1.0.4/app-release.apk
```

其中只有 `v1.0.4` 中的版本号会随版本控制模块变化。

## 当前项目现状

### 后端

后端已有版本控制模块：

- `src/main/java/io/github/tissyboxc/clark_aams_backend/appversion/AppVersionController.java`
- `src/main/java/io/github/tissyboxc/clark_aams_backend/appversion/AppVersionService.java`
- `src/main/java/io/github/tissyboxc/clark_aams_backend/appversion/AppVersionAdminController.java`
- `src/main/java/io/github/tissyboxc/clark_aams_backend/appversion/AppVersionConfigStore.java`
- `src/main/resources/static/admin/versions.html`

现有接口：

```http
GET /api/v1/app/version
POST /api/v1/app/version/check
GET /api/v1/admin/app-version/config
PUT /api/v1/admin/app-version/config
```

版本配置持久化文件：

```text
client-version.json
```

### Flutter 客户端

Flutter 已有版本检查模型和接口：

- `lib/models/app_version.dart`
- `lib/services/clark_backend_api.dart`
- `lib/screens/home_page.dart`
- `lib/screens/about_page.dart`
- `lib/config/backend_config.dart`

当前行为：

- `HomePage` 启动后会调用版本检查。
- `AboutPage` 中可以手动检查更新。
- 更新按钮当前使用 `url_launcher` 外部打开下载地址。

需要改造为：

- Android 端应用内下载 APK。
- 下载完成后拉起系统安装器。
- 非 Android 平台保留外部打开发布页或提示暂不支持。

## 后端开发方案

### 1. 版本号规范

后台版本管理页面中建议保存纯版本号：

```text
1.0.4
```

不要保存：

```text
v1.0.4
```

后端在生成 GitHub Releases tag 时自动补 `v`。

为了兼容管理员误填 `v1.0.4`，后端生成 URL 时需要防止出现 `vv1.0.4`。

### 2. 生成 APK 直链

在 `AppVersionService` 中增加 URL 生成逻辑：

```java
private String buildGithubApkUrl(String latestVersion) {
    String version = normalize(latestVersion);
    if (version.isBlank()) {
        return "";
    }
    String tag = version.startsWith("v") ? version : "v" + version;
    return "https://github.com/TissyBoxC/Clark_AAMS/releases/download/"
            + tag
            + "/app-release.apk";
}
```

### 3. 返回主下载源

版本检查响应中的 `downloadSources` 必须包含 GitHub APK 直链，并设为主下载源：

```json
{
  "type": "github-apk",
  "label": "APK 直链下载",
  "url": "https://github.com/TissyBoxC/Clark_AAMS/releases/download/v1.0.4/app-release.apk",
  "primary": true,
  "description": "Android 安装包"
}
```

建议后端处理规则：

1. 优先使用后端根据 `latestVersion` 自动生成的 GitHub APK 下载源。
2. 如果配置中已有其他下载源，可以继续保留，但 GitHub APK 源必须是 `primary = true`。
3. 若多个下载源都标记为 `primary`，后端应只保留 GitHub APK 为主下载源。

### 4. 版本检查接口契约

Flutter 请求：

```http
POST /api/v1/app/version/check
Content-Type: application/json
```

请求体：

```json
{
  "platform": "android",
  "currentVersion": "1.0.3",
  "currentBuild": 3,
  "channel": "stable",
  "extra": {}
}
```

响应体：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "platform": "android",
    "channel": "stable",
    "currentVersion": "1.0.3",
    "currentBuild": 3,
    "latestVersion": "1.0.4",
    "latestBuild": 4,
    "minimumSupportedVersion": "1.0.0",
    "minimumSupportedBuild": 1,
    "updateType": "optional",
    "updateAvailable": true,
    "forceUpdate": false,
    "title": "发现新版本",
    "message": "有新版本可用，建议更新后继续使用。",
    "releaseNotes": [
      "优化课表导入体验。",
      "修复已知问题。"
    ],
    "downloadSources": [
      {
        "type": "github-apk",
        "label": "APK 直链下载",
        "url": "https://github.com/TissyBoxC/Clark_AAMS/releases/download/v1.0.4/app-release.apk",
        "primary": true,
        "description": "Android 安装包"
      }
    ],
    "releasePageUrl": "https://github.com/TissyBoxC/Clark_AAMS/releases/tag/v1.0.4",
    "checkedAt": "2026-05-15T14:00:00+08:00"
  }
}
```

### 5. 管理后台建议

`/admin/versions.html` 建议保留现有字段，同时增加一个只读预览：

```text
APK 直链预览
https://github.com/TissyBoxC/Clark_AAMS/releases/download/v当前版本/app-release.apk
```

管理员只需要维护：

- 最新版本号：`latestVersion`
- 最新 Build：`latestBuild`
- 最低支持版本号：`minimumSupportedVersion`
- 最低支持 Build：`minimumSupportedBuild`
- 更新标题
- 更新说明
- 发布说明

下载地址由后端自动生成，避免手动填写错误。

## Flutter 开发方案

### 1. 新增更新服务

建议新增：

```text
lib/services/apk_update_service.dart
```

职责：

1. 从 `AppVersionInfo.primaryDownloadSource` 获取 APK URL。
2. 下载 APK 到本地临时目录。
3. 提供下载进度回调。
4. 下载完成后调用 Android 原生安装器。
5. 非 Android 平台降级为外部打开发布页。

建议接口：

```dart
class ApkUpdateProgress {
  const ApkUpdateProgress({
    required this.receivedBytes,
    required this.totalBytes,
  });

  final int receivedBytes;
  final int? totalBytes;

  double? get ratio {
    final total = totalBytes;
    if (total == null || total <= 0) {
      return null;
    }
    return receivedBytes / total;
  }
}

class ApkUpdateService {
  Future<void> downloadAndInstall({
    required AppVersionInfo version,
    required void Function(ApkUpdateProgress progress) onProgress,
  });
}
```

### 2. 下载实现

推荐使用 `HttpClient` 或 `http.Client.send()`，因为普通 `http.get()` 不方便显示实时进度。

下载文件建议保存到：

```text
临时目录/Clark_AAMS_{latestVersion}.apk
```

Flutter 需要新增依赖：

```yaml
path_provider: ^2.1.5
```

如果使用权限处理，也可以新增：

```yaml
permission_handler: ^11.3.1
```

Android 10 及以上下载到应用私有目录不需要外部存储权限。

### 3. Android 安装器调用

安装 APK 需要 Android 原生配合。

需要改造：

```text
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/io/github/tissyboxc/MainActivity.java
android/app/src/main/res/xml/file_paths.xml
```

Manifest 增加安装权限：

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
```

`application` 内增加 `FileProvider`：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

新增 `file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="cache" path="." />
    <files-path name="files" path="." />
    <external-cache-path name="external_cache" path="." />
</paths>
```

`MainActivity` 中通过 `MethodChannel` 暴露安装方法：

```text
channel: clark_aams/update
method: installApk
argument: apkPath
```

Android 安装逻辑：

1. 判断 APK 文件是否存在。
2. Android 8.0 及以上检查 `canRequestPackageInstalls()`。
3. 如果没有未知来源安装权限，跳转系统授权页：

```java
Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
```

4. 使用 `FileProvider.getUriForFile()` 获取 `content://` URI。
5. 使用 `Intent.ACTION_VIEW` 打开安装器。
6. MIME 类型：

```text
application/vnd.android.package-archive
```

7. 添加权限：

```java
Intent.FLAG_GRANT_READ_URI_PERMISSION
Intent.FLAG_ACTIVITY_NEW_TASK
```

### 4. 首页自动更新弹窗

现有 `HomePage` 已有版本检查逻辑：

```text
_checkAppVersion()
_showUpdateDialog()
_openDownload()
```

需要将 `_openDownload()` 从外部打开 URL 改成调用 `ApkUpdateService.downloadAndInstall()`。

弹窗状态建议：

- 初始：展示版本标题、更新说明、发布说明。
- 点击立即更新：按钮进入 loading 状态。
- 下载中：展示 `LinearProgressIndicator`。
- 下载完成：提示“下载完成，正在打开安装器”。
- 失败：展示错误，并允许重试。

强制更新逻辑：

```dart
final required = version.forceUpdate || version.updateType == 'required';
```

如果 `required = true`：

- `barrierDismissible = false`
- `PopScope.canPop = false`
- 不显示“稍后再说”
- 下载失败后仍停留在弹窗中，只允许重试或再次打开安装器

### 5. 关于页面手动更新

现有 `AboutPage` 中：

```text
_openUpdateUrl()
```

需要改为：

1. 如果没有版本信息，先调用 `_loadVersionInfo()`。
2. 如果 Android 且存在 APK 下载地址，调用应用内下载。
3. 如果非 Android 或没有 APK 地址，使用 `url_launcher` 打开 `releasePageUrl`。

按钮文案建议：

```text
检查更新
下载更新
```

下载时按钮不可重复点击。

### 6. 下载地址选择规则

Flutter 获取下载地址时使用：

```dart
final source = version.primaryDownloadSource;
final url = source?.url.isNotEmpty == true
    ? source!.url
    : version.releasePageUrl;
```

建议额外校验：

1. `url` 必须是 `https`。
2. Android 应用内安装时 URL 最好以 `.apk` 结尾。
3. 如果不是 APK 地址，则降级外部打开。

## 发布流程

### 1. Flutter 打包

构建 Android APK：

```bash
flutter build apk --release
```

产物一般位于：

```text
build/app/outputs/flutter-apk/app-release.apk
```

### 2. GitHub Releases

在 GitHub 仓库创建 Release：

```text
tag: v1.0.4
```

上传文件名必须是：

```text
app-release.apk
```

最终直链必须可访问：

```text
https://github.com/TissyBoxC/Clark_AAMS/releases/download/v1.0.4/app-release.apk
```

### 3. 后端版本配置

进入后端管理页面：

```text
/admin/versions.html
```

配置：

```text
latestVersion = 1.0.4
latestBuild = 4
minimumSupportedVersion = 1.0.0
minimumSupportedBuild = 1
```

如果要强制低版本更新，将 `minimumSupportedVersion` 或 `minimumSupportedBuild` 提高到旧客户端之上。

## 测试用例

### 后端测试

1. 配置 `latestVersion = 1.0.4`。
2. 调用：

```http
POST /api/v1/app/version/check
```

请求：

```json
{
  "platform": "android",
  "currentVersion": "1.0.3",
  "currentBuild": 3,
  "channel": "stable",
  "extra": {}
}
```

预期：

```text
updateAvailable = true
downloadSources[0].url = https://github.com/TissyBoxC/Clark_AAMS/releases/download/v1.0.4/app-release.apk
downloadSources[0].primary = true
```

3. 配置 `latestVersion = v1.0.4`。
4. 再次调用接口。
5. 预期 URL 不出现 `vv1.0.4`。

### Flutter 测试

1. 安装旧版本 APK，例如 `1.0.3+3`。
2. 后端配置最新版本为 `1.0.4+4`。
3. 启动 App。
4. 预期自动弹出更新提示。
5. 点击“立即更新”。
6. 预期应用内显示下载进度。
7. 下载完成后拉起系统安装器。
8. 安装完成后再次打开 App。
9. 预期不再提示更新。

### 强制更新测试

1. 当前客户端版本：`1.0.3+3`。
2. 后端配置：

```text
minimumSupportedVersion = 1.0.4
minimumSupportedBuild = 4
```

3. 启动 App。
4. 预期：

```text
forceUpdate = true
updateType = required
```

5. 更新弹窗不可关闭。
6. 不显示“稍后再说”。

### 异常测试

需要覆盖：

1. 后端不可用：App 不闪退，首页可正常打开。
2. GitHub APK 地址 404：显示下载失败，可重试。
3. 用户拒绝未知来源安装权限：提示用户去系统设置开启权限。
4. 下载中断：提示失败，可重新下载。
5. 非 Android 平台：打开发布页或提示暂不支持应用内更新。

## 验收标准

后端验收：

- `latestVersion` 控制 GitHub APK URL 中的版本号。
- 版本号为 `1.0.4` 时返回 `v1.0.4`。
- 版本号为 `v1.0.4` 时仍返回 `v1.0.4`，不能返回 `vv1.0.4`。
- `downloadSources` 中 GitHub APK 是主下载源。
- 管理后台可以正常修改版本配置。

Flutter 验收：

- 首页启动检查更新正常。
- 关于页面手动检查更新正常。
- Android 端可以应用内下载 APK。
- 下载过程有进度条和状态提示。
- 下载完成后能拉起系统安装器。
- 强制更新不可绕过。
- 下载失败不会导致 App 崩溃。

## 注意事项

1. GitHub Releases 的 tag 必须与后端生成的 tag 一致，例如 `v1.0.4`。
2. APK 文件名必须保持为 `app-release.apk`。
3. Android 8.0 及以上需要用户授予“安装未知应用”权限。
4. 应用内更新安装的是完整 APK，不是热更新。
5. Flutter 的 `version` 和后端 `latestVersion/latestBuild` 要同步维护。
6. 当前项目中存在部分中文乱码文件，落地更新功能时建议统一按 UTF-8 修复，避免更新弹窗出现乱码。
