# SeekTop 官方插件

Windows x64 原生工具与客户端分开打包。客户端使用 Compose 渲染 manifest 声明的表单、结果和状态；独立进程通过 stdin 接收一行 JSON 参数，通过 stdout 输出 NDJSON 事件。`cancel` 行停止任务；客户端另有限时和强制结束兜底。同一时间运行一个工具。

这不是第三方插件沙箱：当前只信任 `keys/official.pem` 对应发布者签名，安装时明确提示 Windows 用户权限。不会传递论坛 Cookie、浏览器桥接令牌或继承任意环境变量。工具使用直连或系统 TUN 网络，不继承客户端代理设置。未来第三方 JS SDK 仍是预留，不能按原生工具的信任级别执行。

## 网络工具（市场已隐藏）

- `speed`：Cloudflare HTTP 延迟、下载、上传；3–10 秒/阶段，1–4 并行，下载与上传各限 128 MiB。不会自动进行公网测速。
- `connectivity`：常用平台或最多 8 个自定义 HTTPS URL，分别呈现 HTTP、验证拦截、登录要求及明确的地区限制。检测不是完整流媒体解锁保证。
- `iperf`：固定 `github.com/appshubcc/go-iperf3 v0.1.0`，TCP/UDP、上传/下载/双向、并行及总速率限制；服务端手动开启，默认 loopback，最多监听 60 秒。Windows TCP RTT/重传统计不可用，不伪造零值作为测量结果。

## 构建

需要 Go 1.24+、Python 3.11+、Node.js。依赖在 `network-tools/go.mod` / `go.sum` 固定，打包时执行 `go mod vendor`，完整依赖源码与许可证随 `SOURCE.zip` 分发。

```powershell
go -C plugins/network-tools test ./...
python plugins/package_plugins.py --go C:/path/to/go.exe --key C:/secure/plugin-signing-key.pem
```

私钥不得进入仓库、插件包或源码归档；请把首次生成的发布者私钥存入有备份的凭据库。公钥固定在客户端与 `keys/official.pem`，打包脚本会验证两者对应。开发测试应注入临时测试公钥，不能替换生产公钥。

输出为 `build/plugins/*.stplugin` 和签名 `catalog.json`。包包含 manifest、Ed25519 签名、引擎、许可证及对应源码。只有官方签名包可导入；宿主还会校验摘要、安全解压、API 与运行时、版本和每次启动前的文件完整性。市场目录回退和插件降级被拒绝，安装以版本目录和原子活动索引切换保留旧版。

旧版市场目录为本仓库 `plugin-market` Release 的 `catalog.json`（保持三个网络工具隐藏）；支持 AI 的新宿主使用 `plugin-market-v2` 的 `catalog.json`；与 `desktop-v*` 主体发布分开，客户端更新器不接收插件包。只有已签名包及目录上传后，用户才能从市场下载。首次使用插件能力需要更新客户端一次；后续兼容的表单、检测逻辑和引擎可只更新插件。

## 重新构建包内对应源码

解压 `SOURCE.zip`，进入 `network-tools`，执行 `go test -mod=vendor ./...`。引擎可使用：

```powershell
$env:GOOS='windows'; $env:GOARCH='amd64'; $env:CGO_ENABLED='0'
go build -mod=vendor -trimpath -buildvcs=false -ldflags '-s -w -H=windowsgui -X main.toolKind=iperf' -o worker.exe .
```

将 `iperf` 改为 `speed` 或 `connectivity` 可构建另两个工具。重新分发修改版须遵守 GPL-3.0；客户端不会把修改后的未授权签名当作官方包接受。

市场下载使用单独且不携带 Cookie 的 Chromium 会话，遵守应用代理范围；网络工具本身仍使用直连/系统 TUN，不继承应用代理。可使用 `SeekTop.exe --open-plugins` 或 `SeekTop.exe --plugin io.github.seektop.toolbox.iperf` 打开插件入口。

## 独立更新市场

先递增对应 manifest 的插件版本并重新构建。保留当前和旧插件包，先把新版本 `.stplugin` 上传到 `plugin-market`，核对 GitHub 远端 SHA-256 后，最后替换已签名的 `catalog.json`（`gh release upload plugin-market catalog.json --clobber`）。目录替换瞬间的网络失败不影响已安装版本，可重试刷新；客户端拒绝低于本机已见 revision 的目录。不要用不同内容覆盖同名同版本插件包。

本机发布者私钥保存在项目 `.seektop-keys/plugin-signing-key.pem`，已从 Git 排除并仅赋予当前 Windows 用户访问。请另外做安全备份；清理 build 不会删除它。无需把私钥提供给普通客户端或插件使用者。

## 帖子 AI 分析（post-analysis-v1）

AI 模板插件位于 `plugins/ai-analysis/analysis.json`，独立签名打包，无可执行程序，不访问服务商密钥。宿主负责正文提取、可选图片、OpenAI 兼容请求、流式输出及停止、Windows 加密存储、按板块提示词设置和本地定点金额计算。需要包含 AI 宿主能力的客户端；旧版市场继续隐藏三个网络工具，新宿主使用 `plugin-market-v2` 索引，避免旧客户端解析不支持的运行时。

构建：`python plugins/package_ai_analysis.py --key <私钥路径> --output build/ai-plugin`。可在插件中心导入生成的 `.stplugin`。此构建不会发布或恢复任何网络工具。发布到 `plugin-market-v2` 时只上传 AI 包和它的签名 catalog.json。

使用：设置 → AI 分析设置，录入 Base URL（例如 `https://api.deepseek.com`，其他服务可能需要 `/v1`）、模型和 API Key，按服务商实际能力启用图片输入，保存并测试。连接测试只请求 `/models`，不验证付费推理或图片能力。帖子主楼加载后点击「AI 分析」，确认提示词和图片再开始。不默认发送评论；图片只发送你勾选的链接，服务商可能无法读取需要论坛登录的图片。模型输出中的图片不会自动加载。

通用提示词和自定义板块规则保存在本机。优先级：自定义板块（标识或标题）→ 自定义通用 → 插件板块模板 → 插件通用模板。本次分析中的修改不回写设置。追问沿用上次分析的正文、图片与提示词。关闭分析窗口会取消请求、清理窗口会话；结果不会额外持久化。

Windows safeStorage 加密所有 AI 配置，包括 API Key；不支持加密时拒绝写入，无明文回退。变更服务商地址后不会复用旧地址的密钥，需要重新录入。AI 网络使用独立非持久 Chromium session，不继承论坛 Cookie；跟随客户端「所有请求」代理设置，限制重定向目的地为原始 API URL。仅 HTTP loopback 允许本地测试，其余 API 地址要求 HTTPS。

交易模板只提取已明确给出的字段，缺失字段为 null。估值计算器需要用户确认原价/续费基准、报价、费用、完整周期天数、到期日和一致的币种；按上海日期排除到期当日，分为单位四舍五入，不换汇。不能从 AI 分析推断实时市场公允价。
