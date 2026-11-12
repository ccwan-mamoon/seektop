# 来源、许可与修改说明

本桌面客户端基于 [5151561/nodyssey](https://github.com/5151561/nodyssey) 二次开发，并非独立重写，也不是上游或 NodeSeek 官方发布的软件。

## 上游基线

- 项目：Nodyssey
- 代码基线：`495997983257a83d47a3c01ffbbe4fbe7205db14`
- 许可：GNU General Public License v3.0，完整原文保留于根目录 `LICENSE`。
- 上游作者及贡献者的版权与许可声明均予以保留。
- 原始项目介绍、更新记录和贡献指南存档于 `docs/upstream/`。

## 桌面移植修改

修改日期：2026-11-12。桌面移植发布账号：`ccwan-mamoon`。

- 新增 Windows Compose 应用模块 `desktop`，复用原 `ui`、`shared`、`designsys`、`richtext`。
- 新增 JVM 数据库初始化、桌面依赖组装、文件选择与文本导入导出适配。
- 新增 Chromium 登录窗口和共享会话桥接，并将桌面请求接入原网络与 Repository 契约。
- 增加桌面运行时和离线文件存储回归测试。
- 衍生项目继续采用 GPL-3.0；未将上游贡献声明为桌面移植维护者独立创作。

## 第三方软件

发行包包含 Electron/Chromium、Java 运行时，以及 Kotlin、Compose、Room、Coil 等组件。它们各自的版权和许可证仍有效，不因本项目采用 GPL-3.0 而被替换。

Electron/Chromium 的随附许可证位于发行包的 `app/resources/browser-runtime/`，Java 的许可证位于 `runtime/legal/`。组件清单亦可在项目版本目录与 Gradle 锁文件中查阅。

## 发布范围

发布源代码时应包含完整源文件、构建脚本、锁文件、运行时准备脚本和本说明；发布二进制时应提供同版本对应源码。首次发布为 Windows 移植原型，已验证范围与未适配功能见 `desktop/README.md`。
