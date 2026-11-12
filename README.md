# SeekTop · 寻桌

NodeSeek 的非官方 Windows 桌面客户端，基于 [Nodyssey](https://github.com/5151561/nodyssey) 二次开发与桌面移植。

采用 Kotlin Multiplatform 和 Compose，复用上游界面、导航、富文本、内容解析及共享业务逻辑，并补充 Windows 平台实现。项目采用 **GPL-3.0**，保留上游版权与贡献归属。与 NodeSeek 官方及 Nodyssey 上游维护者无隶属或背书关系。

## 下载与启动

在本仓库 [Releases](https://github.com/ccwan-mamoon/seektop/releases) 下载 `SeekTop-0.1.0-windows-x64.zip`，解压整个目录后双击 **`SeekTop.exe`**。请保留完整目录；无需另外安装 Java、Node.js 或 Python。

这是首个 Windows 移植原型。发布页应标记为 **Pre-release**。

## 已验证与当前范围

- 原版宽屏双栏首页、真实帖子列表、正文、评论和头像显示已在 Windows 窗口中验证。
- 共享业务与桌面模块共 **381 项 JVM 测试通过**，源码格式检查通过。
- 浏览器桥接的鉴权、Cookie 域隔离、清除和代理重置检查通过。
- 登录请使用“改用网页登录”，完成后在登录窗口选择“完成并返回应用”。登录后的发帖、回复、点赞、签到和转账等操作未实际提交验证。
- Windows 自动更新、系统推送、头像更换、图片保存、系统分享和退出应用后的后台下载尚未接入。离线 Wi-Fi 限制尚未适配 Windows 网络计费状态。

应用数据保存在 `%LOCALAPPDATA%/SeekTop`。请勿将登录数据、Cookie、令牌或个人数据库上传到仓库。

## 来源与修改

上游：[`5151561/nodyssey`](https://github.com/5151561/nodyssey)，基线提交 `495997983257a83d47a3c01ffbbe4fbe7205db14`。

桌面移植修改始于 **2026-11-12**，由本仓库维护者维护。新增 `:desktop` 壳、JVM 数据库初始化、Chromium 登录与网络共享会话、桌面文件选择及导入导出适配。代码包名与上游模块名保留，便于核对来源和后续同步。

完整说明见 [NOTICE.md](NOTICE.md)。上游原始 README、更新记录、贡献指南和工作流保存在 [docs/upstream](docs/upstream/)。本仓库的问题和建议请提交到本仓库 Issues。

## 从源码构建

准备 JDK 21 和 Python 3.11+，使用仓库 Gradle wrapper：

```powershell
python desktop/provision-browser.py
./gradlew :desktop:compileKotlinJvm
./gradlew :desktop:jvmTest :shared:jvmTest :designsys:jvmTest :richtext:jvmTest spotlessCheck
./gradlew :desktop:createDistributable
```

运行时下载较慢时可指定镜像：

```powershell
python desktop/provision-browser.py --mirror https://npmmirror.com/mirrors/electron
```

应用目录位于 `desktop/build/compose/binaries/main/app/SeekTop`。更多平台实现和本机打包说明见 [desktop/README.md](desktop/README.md)。版本目录和 Gradle 锁文件应一同提交。

## 许可与第三方组件

本项目按 [GNU GPL v3.0](LICENSE) 发布。分发修改版时请保留版权和许可声明、注明修改，并提供对应源码及构建脚本。Electron/Chromium、Java 及其他组件保留各自的许可证，详见 [NOTICE.md](NOTICE.md)。

## 致谢

- [Nodyssey 作者及贡献者](https://github.com/5151561/nodyssey)：本桌面移植的代码基础。
- 上游所列的相关项目与开源组件：详见 [上游 README](docs/upstream/README.md)。
