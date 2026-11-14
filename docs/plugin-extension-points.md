# SeekTop 插件扩展预留

当前阶段只预留声明协议和依赖入口，没有插件安装、代码加载、沙箱运行、市场下载或插件管理界面。现有 NodeImage 图床继续由内置实现提供，不迁移账号和配置，不改变现有页面。

## 已有代码入口

- `shared/src/commonMain/kotlin/io/github/seektop/plugins/PluginContract.kt`：插件身份、API 版本、运行时类型、权限声明、页面与图床贡献、插件路由，以及文件引用和上传结果的数据契约。
- `PluginManifestValidator.kt`：验证插件 ID、版本格式、API 版本、包内 JS 入口、精确网络域名、网络权限与贡献 ID。它只验证元数据，不替代签名、包解压检查或实际运行时权限控制。
- `PluginRegistry.kt`：只读注册接口与默认 `ReservedPluginRegistry`。默认状态为 `RESERVED`，已安装列表为空，没有安装和执行方法。
- `ui/.../di/AppContainer.kt`：提供默认关闭的 `pluginRegistry`。现有 Windows、Android、iOS 容器及测试容器均兼容；目前不向页面添加尚不可用的市场入口。

这些 Kotlin 类型用于宿主边界与未来桥接协议，不表示允许下载 JAR、加载 JVM 类或访问 `AppContainer`。API 版本暂定为 1，正式 SDK 发布前仍可调整。

## 首批扩展类型

Seek.li 邮箱保留 ID `io.github.seektop.seekli`，按独立页面插件接入。服务地址为 <https://seek.li/>，用户提供的文档为 <https://seek.li/manage/developer/docs#tag/邮箱>。本次读取文档返回 HTTP 468，尚未核对认证方式和接口；没有在代码中猜测邮箱 API 或建立登录请求。

NodeImage 保留 ID `io.github.seektop.nodeimage`，按图床能力插件接入，后续向编辑器提供上传能力，并可贡献图片管理页面。当前 `ImageHostProvider` 枚举与 `DefaultImageHostRepository` 的固定映射仍保留。接入真实插件时，再增加可动态注册的 provider 标识及适配器，并兼容已有 `nodeimage` 配置 ID。

两个 ID 只是预留身份，不是已安装插件，也不是市场中的可下载商品。`PluginRoute` 独立携带插件 ID 和页面 ID，为后续插件页面及原生通知定向跳转留出空间，目前不接入导航栈。

## 后续实现顺序

1. 实现宿主管理器及受控能力桥接。插件页面继续用 Compose 组件渲染；复用现有运行时执行独立插件逻辑，明确网络、凭据、文件选择、后台任务和通知权限。当前声明不会自动授予任何能力。
2. 用 NodeImage 实现首个完整插件，验证独立打包、开发者本地加载、上传进度、取消、禁用及配置迁移。文件通过短期不透明引用交接，不把任意磁盘路径交给插件。
3. 建立插件市场最小版：已签名目录、插件包验证、兼容性检查、安装、启停、升级与失败回退。市场在这些能力完成后开放；普通用户可以独立更新插件，不必每次更新主体。
4. 核对 Seek.li 开发者 API 后实现邮箱插件，再接入插件来源的提醒记录与通知目标。现有“互动 / 私信 / 提醒”保持不变，插件提醒应进入“提醒”，点击打开对应插件页面。

新的宿主能力、新组件或 SDK 不兼容变更仍需更新主体；已支持能力范围内的业务和接口变化才由插件独立更新。

## 实现前必须补齐的边界

- 插件不能取得论坛 Cookie、浏览器桥接令牌、原始 Electron IPC、任意文件读写或进程执行权限。网络声明必须由宿主实际执行校验，包含重定向后的域名；元数据校验并不能阻止私网访问等网络行为。
- 下载的逻辑不能直接放进 JVM、Electron 主进程或普通 Node `vm` 执行。隔离运行时、IPC 发送者校验、按插件分配的存储与资源限制需要另外实现。依据：[Electron 安全建议](https://www.electronjs.org/docs/latest/tutorial/security)、[进程沙箱](https://www.electronjs.org/docs/latest/tutorial/sandbox)。
- Windows 当前使用 `PlaintextSecretCipher`，不能当作已完成的加密凭据库。真实插件接入令牌前，应实现系统加密存储和旧配置迁移，并按插件、服务账号隔离。可评估现有运行时的异步 [safeStorage](https://www.electronjs.org/docs/latest/api/safe-storage)。
- 安装必须校验可信发布者、目录及包签名、摘要、兼容性和安全解压；权限增加需要重新授权。卸载、禁用和插件缺失时，编辑器及通知必须有明确恢复入口。
- 当前未增加运行时依赖、持久化表、外部下载、后台任务，也未修改现有许可与上游署名。

## 本阶段验证

公共契约测试覆盖两个服务的扩展声明、序列化往返、入口路径穿越、外部可执行路径、未来 API 版本、网络声明约束、贡献 ID 冲突，以及默认注册表为空。安装与运行测试应随对应实现一起加入，不能由声明测试代替。
