# SeekTop 客户端开发

本仓库开发与发布 SeekTop（寻桌）Windows x64 客户端。Android、iOS 平台实现保留，共享模块继续采用 Kotlin Multiplatform。

## 开发入口

- `desktop`：Windows 入口 `io.github.seektop.desktop.MainKt`、依赖组装、Chromium 会话、通知和更新。
- `ui`：`SeekTopRoot`、页面、ViewModel、桌面导航和多语言资源。
- `shared`：NodeSeek 协议、解析、Repository、Room 数据库和设置。
- `designsys`：`SeekTopTheme`、通用组件和富文本渲染。
- `richtext`：富文本树、Markdown 与 ANSI 解析。
- `gallery`：桌面组件预览；`app`、`iosapp`、`smoke`、`benchmark` 为保留的平台代码和验证工具。

业务包名为 `io.github.seektop`；通用库位于 `io.github.seektop.foundation`，与站点专属的 `io.github.seektop.core` 分开。Gradle 插件前缀为 `seektop`，根工程为 `SeekTop`，iOS 工程为 `SeekTop.xcodeproj`，framework 为 `SeekTopShell`。

## 新电脑准备

使用 JDK 21、Python 3.11+、Windows .NET Framework 编译器和仓库 Gradle wrapper。当前构建仍配置 Android 模块，需要 Android SDK 37。

```powershell
python desktop/provision-browser.py
.\gradlew.bat :desktop:jvmTest :shared:jvmTest :ui:jvmTest :designsys:jvmTest :richtext:jvmTest spotlessCheck
python desktop/tests/test_browser.py
python desktop/tests/test_updater.py
node desktop/tests/test_update_transfer.cjs
node desktop/tests/test_notifications.cjs
.\gradlew.bat :desktop:createDistributable
.\gradlew.bat --stop
```

输出为 `desktop/build/compose/binaries/main/app/SeekTop`，保留整个目录。发布以 `.github/workflows/windows-desktop.yml` 为准。桌面版本来自 `gradle/libs.versions.toml` 的 `versions.seektop`；Android 版本属性为 `seektop.android.versionCode/versionName`，与桌面版本独立。

## 改名兼容

Windows 数据继续位于 `%LOCALAPPDATA%/SeekTop`。浏览器桥接环境变量为 `SEEKTOP_*`，自定义浏览器路径的 JVM 属性为 `seektop.browser.bundle`。

旧名称中，Chromium 的 `persist:nodyssey` 分区、JVM 的 `io/github/nodyssey/settings` 语言节点、iOS 旧 Keychain 的 `io.github.nodyssey.secrets` 服务是兼容存储地址，保留原值，避免失去已有会话和设置。Room 表结构、schema 内容和版本、富文本 `@SerialName` 不变；schema 目录随数据库类的新包名迁移。

Android applicationId 和 iOS bundle identifier 统一为 `io.github.seektop`，与原 Nodyssey 安装包属于不同应用身份，不会自动继承原手机端沙箱、签名或 Keychain 权限。本次改名不是手机端升级迁移方案。手机代码仍保留上游更新源，未验证或发布 SeekTop 手机端更新，不应据此分发手机安装包。

## 功能验收

桌面已有分栏阅读、搜索、登录表单、人机验证、通知、主题和重启升级。真实账号的登录和写操作需要人工验收；完全退出后的后台任务、头像更换、图片保存和系统分享尚未完整适配。当前状态以根 README、desktop/README.md 和 CHANGELOG 为准。

Nodyssey、Plaza 的名称在来源、版权、上游链接和历史资料中保留。
