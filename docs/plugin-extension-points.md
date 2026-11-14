# SeekTop 插件扩展

## Windows 官方网络工具（1.0.5 开发版）

已接入侧栏插件中心、已安装/市场、签名包下载与导入、启停、卸载、独立版本升级和 Compose 表单/结果页。首批为网速测试、连通性测试和 iperf3，每个独立包约 4.6 MB，工具引擎不随主体捆绑。当前正式版 1.0.4 不含这些入口，首次需要升级一次主体。

`ToolPlugins.kt` 是单独的 `native-tool-v1` 官方工具协议；`DesktopToolPlugins` 负责签名目录、下载、原子安装索引、版本回退保护和子进程生命周期。签名公钥固定在宿主，目录与 manifest 使用 Ed25519，包和包内文件使用 SHA-256，每次启动再次验文件。zip 仅接受声明过的平面普通文件，不把 ZIP 中的路径或链接作为外部磁盘路径使用。卸载删除本插件版本目录，旧版本在升级后保留供失败恢复。

这是明确收窄后的首批实现：**仅限 SeekTop 发布者签名的官方原生网络工具，不是第三方代码沙箱**。安装时要求用户确认本地程序具有同等 Windows 用户权限；宿主不会向工具传递论坛 Cookie、浏览器桥接令牌或任意环境变量，但也不声称可阻止原生程序访问同用户文件。工具使用直连/系统 TUN 网络，不继承客户端代理。服务端默认 loopback，手动开启并最多监听 60 秒；停止/超时/退出会清理进程和套接字。凭据、图床、邮箱和后台通知没有因此开放。

插件版本和固定 Go 依赖在 `plugins/` 中独立维护；分发包含许可证和完整对应源码。构建、签名、发布者私钥备份、协议与验证方法见 [插件开发说明](../plugins/README.md)。市场使用本仓库独立的 `plugin-market` Release 签名目录，主体更新器只认 `desktop-v*`，不会把插件误判为客户端更新。

以下 JavaScript、图床和邮箱契约仍为预留。现有 NodeImage 图床继续由内置实现提供，不迁移账号和配置。

## 已有代码入口

- `shared/src/commonMain/kotlin/io/github/seektop/plugins/PluginContract.kt`：插件身份、API 版本、运行时类型、权限声明、页面与图床贡献、插件路由，以及文件引用和上传结果的数据契约。
- `PluginManifestValidator.kt`：验证插件 ID、版本格式、API 版本、包内 JS 入口、精确网络域名、网络权限与贡献 ID。它只验证元数据，不替代签名、包解压检查或实际运行时权限控制。
- `PluginRegistry.kt`：只读注册接口与默认 `ReservedPluginRegistry`。默认状态为 `RESERVED`，已安装列表为空，没有安装和执行方法。
- `ui/.../di/AppContainer.kt`：提供默认关闭的 `pluginRegistry`。现有 Windows、Android、iOS 容器及测试容器均兼容；JS 注册表仍不连接原生工具中心。

这些 Kotlin 类型用于宿主边界与未来桥接协议，不表示允许下载 JAR、加载 JVM 类或访问 `AppContainer`。API 版本暂定为 1，正式 SDK 发布前仍可调整。

## 首批扩展类型

Seek.li 邮箱保留 ID `io.github.seektop.seekli`，按独立页面插件接入。服务地址为 <https://seek.li/>，用户提供的文档为 <https://seek.li/manage/developer/docs#tag/邮箱>。本次读取文档返回 HTTP 468，尚未核对认证方式和接口；没有在代码中猜测邮箱 API 或建立登录请求。

NodeImage 保留 ID `io.github.seektop.nodeimage`，按图床能力插件接入，后续向编辑器提供上传能力，并可贡献图片管理页面。当前 `ImageHostProvider` 枚举与 `DefaultImageHostRepository` 的固定映射仍保留。接入真实插件时，再增加可动态注册的 provider 标识及适配器，并兼容已有 `nodeimage` 配置 ID。

两个 ID 只是预留身份，不是已安装插件，也不是市场中的可下载商品。`PluginRoute` 独立携带插件 ID 和页面 ID，为后续插件页面及原生通知定向跳转留出空间，目前不接入导航栈。

## 后续实现顺序

1. 实现第三方隔离运行时及受控能力桥接。官方网络工具宿主已独立接入；插件页面继续用 Compose 组件渲染；复用现有运行时执行独立插件逻辑，明确网络、凭据、文件选择、后台任务和通知权限。当前声明不会自动授予任何能力。
2. 用 NodeImage 实现首个完整插件，验证独立打包、开发者本地加载、上传进度、取消、禁用及配置迁移。文件通过短期不透明引用交接，不把任意磁盘路径交给插件。
3. 在官方原生工具市场基础上扩展第三方市场：已签名目录、插件包验证、兼容性检查、安装、启停、升级与失败回退。市场在这些能力完成后开放；普通用户可以独立更新插件，不必每次更新主体。
4. 核对 Seek.li 开发者 API 后实现邮箱插件，再接入插件来源的提醒记录与通知目标。现有“互动 / 私信 / 提醒”保持不变，插件提醒应进入“提醒”，点击打开对应插件页面。

新的宿主能力、新组件或 SDK 不兼容变更仍需更新主体；已支持能力范围内的业务和接口变化才由插件独立更新。

## 实现前必须补齐的边界

- 插件不能取得论坛 Cookie、浏览器桥接令牌、原始 Electron IPC、任意文件读写或进程执行权限。网络声明必须由宿主实际执行校验，包含重定向后的域名；元数据校验并不能阻止私网访问等网络行为。
- 下载的逻辑不能直接放进 JVM、Electron 主进程或普通 Node `vm` 执行。隔离运行时、IPC 发送者校验、按插件分配的存储与资源限制需要另外实现。依据：[Electron 安全建议](https://www.electronjs.org/docs/latest/tutorial/security)、[进程沙箱](https://www.electronjs.org/docs/latest/tutorial/sandbox)。
- Windows 当前使用 `PlaintextSecretCipher`，不能当作已完成的加密凭据库。真实插件接入令牌前，应实现系统加密存储和旧配置迁移，并按插件、服务账号隔离。可评估现有运行时的异步 [safeStorage](https://www.electronjs.org/docs/latest/api/safe-storage)。
- 安装必须校验可信发布者、目录及包签名、摘要、兼容性和安全解压；权限增加需要重新授权。卸载、禁用和插件缺失时，编辑器及通知必须有明确恢复入口。
- JS/图床/邮箱没有增加运行时依赖、持久化表或后台任务；官方网络工具新增外部下载和本地文件索引，但不修改现有许可与上游署名。

## 本阶段验证

公共契约测试覆盖两个服务的扩展声明、序列化往返、入口路径穿越、外部可执行路径、未来 API 版本、网络声明约束、贡献 ID 冲突，以及默认注册表为空。安装与运行测试应随对应实现一起加入，不能由声明测试代替。

Windows 新增回归覆盖签名/文件篡改、路径穿越、安装失败保持旧版、降级拒绝、启停持久化和损坏重装；实际打包运行时覆盖官方签名安装、NDJSON、停止释放端口及失败结果。Go 与 Python 回环测试覆盖 TCP 上传/下载/双向、UDP 下载和取消，不使用真实账号或默认运行公网测速。

市场下载使用单独且不携带 Cookie 的 Chromium 会话，遵守应用代理范围；网络工具本身仍使用直连/系统 TUN，不继承应用代理。可使用 `SeekTop.exe --open-plugins` 或 `SeekTop.exe --plugin io.github.seektop.toolbox.iperf` 打开插件入口。
