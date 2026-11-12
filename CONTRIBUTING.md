# 参与 SeekTop

请在本仓库 Issues 反馈问题，并提供 SeekTop 版本、Windows 版本、CPU 架构、复现步骤及截图。不要上传 Cookie、密码、令牌、已登录页面快照或含个人数据的数据库。

开发约定见 `AGENTS.md` 与 `docs/architecture.md`。桌面代码放在 `desktop`，共享逻辑继续遵守上游模块边界。请保留上游版权与 GPL-3.0 许可，不将上游实现标为自己的独立创作。

提交前运行：

```powershell
./gradlew spotlessCheck :desktop:jvmTest :shared:jvmTest :designsys:jvmTest :richtext:jvmTest
./gradlew :desktop:createDistributable
./gradlew --stop
```

PR 请说明实际变化、关联问题、验证结果，以及仍未验证的行为。新增依赖时刷新并提交相关 Gradle 锁文件。Android 与 Apple 的原始代码和历史文档保留供参考，本分支的发布重点为 Windows 桌面端。
