# SeekTop · 寻桌（Windows 桌面移植）

这是原 Nodyssey 的 Windows 应用壳，入口直接调用 `:ui` 的 `NodysseyRoot`，并依赖原 `:shared`、`:designsys`、`:richtext`。共享原 ViewModel、Repository 和富文本；桌面能力由 `LocalDesktopExperience` 注入。桌面模式使用专用导航框架、紧凑列表、快捷键、固定阅读操作栏和居中弹窗，不显示手机新手向导或单手入口。

## 构建

使用 JDK 21 和仓库自带 Gradle wrapper。先准备浏览器运行时：

```powershell
python desktop/provision-browser.py --mirror https://npmmirror.com/mirrors/electron
./gradlew :desktop:compileKotlinJvm :desktop:jvmTest
./gradlew :desktop:createDistributable
```

首次增加依赖时需要按仓库规则刷新锁文件。应用镜像位于 `desktop/build/compose/binaries/main/app/SeekTop`，保留完整目录并打开其中的 `SeekTop.exe`。发行目录包含 Java 运行时，使用者无需安装 Java 或 Python。

## 平台实现

桌面壳组装原 Repository；Room 使用 JVM SQLite 驱动，设置使用原 DataStore。应用数据位于 `%LOCALAPPDATA%/SeekTop`。

网页验证和登录由打包的 Chromium 辅助进程显示。原 `HttpTransport` 通过带随机鉴权令牌的回环接口调用同一会话，Cookie 留在浏览器存储内。论坛请求与第三方图床使用不同的 Chromium session。登录窗口中完成操作后点击“完成并返回应用”，原 `SessionRepository` 将同步状态并通知页面刷新。

图片、头像和图床上传同样由平台网络通道服务。文件选择和文本导入导出使用桌面文件选择器。

## 当前范围

这是一版桌面交互预览版。Windows 自动更新、系统推送、应用退出后的离线后台任务、头像更换、图片保存及系统分享面板尚未接入。离线任务只在进程存活时执行；Wi-Fi 限制尚未适配 Windows 网络计费状态。原生表单内的 Turnstile 小组件仍沿用上游的 JVM 降级处理，请使用页面提供的网页登录入口。

项目沿用上游 GPL-3.0 许可，保留上游版权；Electron 自带的许可证位于浏览器运行时目录。

## 0.2.0 验证记录

- UI 和桌面 JVM 模块编译通过，Windows app-image 已生成。
- Compose 桌面导航点击、返回与 Ctrl+N 快捷键测试通过。
- 当前帖子优先刷新、退出详情后恢复列表刷新测试通过。
- 桌面模块运行时及离线文件用例通过，Spotless 检查通过。
- 本轮未提交真实账号操作；全应用逐页实机点击回归尚未完成。

## 0.1.0 验证记录（2026-11-12）

- Windows app-image 成功生成并实际启动。
- 实际窗口中确认原版宽屏双栏首页、真实帖子列表、正文、评论和头像正常显示。
- desktop/shared/designsys/richtext 的 JVM 测试共 381 项通过；ui 的 JVM 测试目标无独立用例，已完成 JVM 编译。
- Spotless 格式检查通过。
- 浏览器桥接的鉴权、Cookie 域隔离、清除和代理重置检查通过；读取公开首页返回 HTTP 200。
- 未登录真实账号，也未提交发帖、回复、点赞、签到或转账。
- Android 和 Apple 平台的构建与测试未在本次 Windows 移植中执行。

本机打包时 GitHub 的 WiX 下载通道异常，使用 `-x :unzipWix` 并准备其声明的空工具目录生成 app-image。app-image 不调用 WiX，当前输出不含 MSI 安装器。常规网络环境可使用上面的标准命令。
