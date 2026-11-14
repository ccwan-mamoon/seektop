# SeekTop · 寻桌（Windows 桌面移植）


这是 SeekTop 的 Windows 客户端模块，入口调用 `:ui` 的 `SeekTopRoot`，依赖 `:shared`、`:designsys`、`:richtext`。共享 ViewModel、Repository 和富文本；桌面能力由 `LocalDesktopExperience` 注入。桌面模式使用专用导航框架、紧凑列表、快捷键、固定阅读操作栏和居中弹窗。源码源于 Nodyssey，保留上游版权与 GPL-3.0 许可。

模块入口、统一命名与已有数据兼容规则见[客户端开发说明](../docs/client-development.md)。Android、iOS 平台代码继续保留。

## 运行时体积

Windows 打包只包含界面、网络、数据库、中文字符集和语言资源等所需 Java 模块，保留现有 Compose 界面及 Electron 浏览器。编译器、调试器等开发模块不随默认运行时打包。模块列表位于 `desktop/build.gradle.kts`，新增依赖后用 `:desktop:suggestModules` 辅助核对，并对实际程序包运行检查：

```powershell
python desktop/tests/test_packaged_runtime.py --app-dir desktop/build/compose/binaries/main/app/SeekTop
python desktop/package_report.py --app-dir desktop/build/compose/binaries/main/app/SeekTop
```

运行时检查通过程序包自己的 EXE 执行，覆盖 HTTP/TLS、中文编码与日期、图形、原生 SQLite 等能力，不使用开发电脑的完整 JDK 代替检查。它不代替真实窗口、登录、人机验证和发帖验收。CI 会将体积分项和大文件重复检查写入 `PACKAGE-SIZE.json`；重复文件仅报告，不自动删除。

需要对照或排查缺失模块时，使用 `:desktop:createDistributable -PfullDesktopRuntime=true --rerun-tasks` 重新生成全量运行时；切回精简版时移除此属性并重新构建。比较时应使用同一 JDK 和相同源码，分别保存两份输出。

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

原生账号密码及两步验证表单由 Compose 显示；Turnstile 由打包的沙箱 Chromium 辅助进程加载官方 `/signIn.html`，通过公开 `turnstile.getResponse()` 回传结果；通过 JNA/Win32 子窗口嵌入应用所属的验证对话框，并在宿主显示后加载。不再安装共享会话的 HTTPS 协议拦截器，保留完整浏览器网络上下文。网页登录入口作为备用。原 `HttpTransport` 通过带随机鉴权令牌的回环接口调用同一会话，Cookie 留在浏览器存储内。论坛请求与第三方图床使用不同的 Chromium session。登录窗口中完成操作后点击“完成并返回应用”，原 `SessionRepository` 将同步状态并通知页面刷新。

图片、头像和图床上传同样由平台网络通道服务。文件选择和文本导入导出使用桌面文件选择器。

## 当前桌面版 1.0.2

Windows 原生 Toast 独立开关默认开启，通知进入系统通知中心，点击可唤回应用；关闭此开关则使用托盘提醒。
启动时左侧菜单默认折叠，保留常用板块简称与完整名称提示，仍可点击切换和管理。通知总开关保留用户原有选择。

可折叠菜单、常用板块管理、分隔线调宽、整合搜索、列表与详情浮动刷新、可输入页码、主题和统一图标均已接入。
“我的”、通知、设置与宽屏编辑器使用桌面布局。人机验证仅在官方验证区域准备好后显示，未准备好或存在账号表单的容器不会直接展示。

游客打开个人主题帖、评论、收藏、关注、通知或账号设置时，会显示登录提示；登录前不加载这些页面，登录成功后返回原入口。浏览历史、应用设置、主题外观和公开用户主页仍允许游客访问。

窗口会先显示启动状态，再异步准备浏览器、设置和账号环境；失败后可重试或退出，详情保存在数据目录的 `startup-error.log`。登录状态变化会释放个人页面的旧状态，公开阅读页保持原有状态。更新程序只在完整界面准备好后收到启动确认，不会把加载画面当作升级成功。

Windows 通知已接入，四类提醒各自选择检查间隔；运行或最小化期间检查，完全退出后停止。通知采用定时检查，并受系统勿扰设置影响。
一键升级已接入，流程与验证方法见下文。登录后写入操作仍需真实账号验收；完全退出后的离线后台任务、头像更换、图片保存和系统分享面板尚未完整适配。

项目沿用上游 GPL-3.0，保留上游版权。Android 与 Apple 模块和历史文档保留供参考，本次发布目标为 Windows。

## Windows 一键升级

桌面端已使用独立的 `DesktopUpdateRepository` 和 `DesktopUpdateInstaller`，不再连接空更新实现。
应用启动时按用户设置检查 GitHub Releases；关于页可手动检查，下载时显示进度并支持取消。
点击“一键升级”会先说明自动重启，下载并校验 SHA-256 后，独立 .NET 更新程序等待旧进程退出，替换完整 app-image 并重启。
新应用完成首帧后确认启动；替换或启动进程失败时恢复旧目录。若新进程仍运行但迟迟未确认启动，不强制结束它，而是保留旧目录供恢复并报告状态。
登录会话与偏好在 `%LOCALAPPDATA%/SeekTop`，不会随应用目录替换；旧目录保留为同级备份。
更新程序需要应用所在目录及其父目录可写。另一实例占用文件时不会强制结束该实例，无法替换就保持旧版。

版本号统一位于 `gradle/libs.versions.toml` 的 `versions.seektop`。打包版本、ZIP 名称与发布标签必须一致。
客户端仅接收本仓库 `desktop-vX.Y.Z` 发布中对应的 Windows x64 ZIP，且必须有 GitHub SHA-256 摘要；源代码 ZIP 和上游 Android 包不会参与升级。
默认仅检查稳定版，预览版需要开启接收测试更新。发布工作流可选择稳定版或预览版，发布新版本前递增桌面版本号。
0.4.0 是含更新功能的初始版本；不含更新代码的旧版本需要先切换到此版本。

原生更新程序由构建任务使用 Windows .NET Framework 编译器生成，无需在用户电脑安装开发工具。
测试：`./gradlew :desktop:jvmTest :ui:jvmTest`、`python desktop/tests/test_updater.py`。
完整 app-image 重启演练：`python desktop/tests/test_updater.py --app-dir desktop/build/compose/binaries/main/app/SeekTop`，全程使用临时空配置和测试副本。

更新下载使用独立 Chromium 会话，支持代理范围、续传和有限重试。下载校验后由用户点击重启，安装准备中阻止重复提交。
