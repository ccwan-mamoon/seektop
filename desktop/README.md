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

## 发布安装包与便携包

Windows 发布工作流会同时上传 `SeekTop-X.Y.Z-windows-x64-setup.exe` 安装包、`SeekTop-X.Y.Z-windows-x64.zip` 便携包、对应源码 ZIP 和 `SHA256SUMS.txt`。安装包与便携包来自同一 app-image，三份分发文件均写入校验清单。安装包编译或校验失败时，工作流不会进入发布步骤。

安装包使用固定版本 Inno Setup 7.1.1，准备脚本验证官方发布者的 Authenticode 签名及编译器版本。默认安装至当前用户的 `%LOCALAPPDATA%/Programs/SeekTop`，不请求管理员权限，创建开始菜单入口，并可选择桌面快捷方式。登录与偏好仍位于 `%LOCALAPPDATA%/SeekTop`。

```powershell
$seekTopIscc = ./desktop/provision-installer.ps1
python desktop/tests/test_release_package.py --iscc $seekTopIscc
python desktop/tests/test_installer.py --iscc $seekTopIscc
python desktop/package_release.py --app-dir desktop/build/compose/binaries/main/app/SeekTop --output-dir release --iscc $seekTopIscc
```

发布前先提交源码，并用该提交构建 app-image；`--source-ref` 默认为 `HEAD`。打包脚本会拒绝 app-image 版本与发布版本不一致的情况。本机只需便携测试包时，可使用 `--skip-installer`；正式发布工作流不使用这个选项。

应用内更新继续下载 ZIP。卸载程序及日志放在 `%LOCALAPPDATA%/SeekTop/installer`，不会被 ZIP 更新替换程序目录的过程覆盖。卸载会清理程序管理的 `app`、`runtime` 目录，保留用户数据。ZIP 更新不会自动刷新 Windows 卸载登记中的版本号；系统登记版本会在再次运行安装包后更新。

安装器回归使用独立 AppId 和临时目录，不安装真实账号环境。当前生成的 SeekTop 安装包尚未接入代码签名证书；编译器下载的签名验证不等于 SeekTop 安装包已签名。

## 平台实现

桌面壳组装原 Repository；Room 使用 JVM SQLite 驱动，设置使用原 DataStore。应用数据位于 `%LOCALAPPDATA%/SeekTop`。

原生账号密码及两步验证表单由 Compose 显示；Turnstile 由打包的沙箱 Chromium 辅助进程加载官方 `/signIn.html`，通过公开 `turnstile.getResponse()` 回传结果；通过 JNA/Win32 子窗口嵌入应用所属的验证对话框，并在宿主显示后加载。不再安装共享会话的 HTTPS 协议拦截器，保留完整浏览器网络上下文。网页登录入口作为备用。原 `HttpTransport` 通过带随机鉴权令牌的回环接口调用同一会话，Cookie 留在浏览器存储内。论坛请求与第三方图床使用不同的 Chromium session。登录窗口中完成操作后点击“完成并返回应用”，原 `SessionRepository` 将同步状态并通知页面刷新。

图片、头像和图床上传同样由平台网络通道服务。文件选择和文本导入导出使用桌面文件选择器。

## 当前桌面版 1.0.4

### 阅读、签到与托盘交互

启动先检查当前账号今日签到，尚未保存签到方式且当天未签到时弹窗选择，可取消；首次选择会保存，设置中可在随机、固定五鸡腿两种方式间更改。保存方式后，启动检查确认未签到才执行；已签到或检查失败时不提交。实际签到与登录验证仍需真实账号验收。

窗口关闭按钮提供退出、最小化到系统托盘和取消；托盘菜单可打开或退出，托盘模式继续检查提醒。系统托盘不可用时不允许隐藏窗口。升级流程的主动重启直接退出，不重复询问窗口关闭方式。

帖子详情刷新到最新回复，读取末页后滚到底部，失败保留原位置。追踪提醒保存首个新增楼层，旧提醒未包含楼层时继续打开帖子。切换标签保留搜索、会话和内部页面；重复点击当前标签返回其根页面，首页和图标仍返回综合并刷新。同一帖子重复点击仍关闭详情。

通知按新互动标识和会话变化去重，发送失败保留内存队列并重试，退出或账号变化清除未发送队列，本地提醒记录不受影响。

### 1.0.3 已发布能力

Windows 原生 Toast 独立开关默认开启，通知进入系统通知中心。每条通知保留自己的目标：互动打开对应帖子楼层，私信打开对应会话，关键词和追踪提醒打开对应帖子。关闭此开关则使用托盘提醒。
启动时左侧菜单默认折叠，保留常用板块简称与完整名称提示，仍可点击切换和管理。通知总开关保留用户原有选择。

可折叠菜单、常用板块管理、分隔线调宽、整合搜索、列表与详情浮动刷新、可输入页码、主题和统一图标均已接入。
“我的”、通知、设置与宽屏编辑器使用桌面布局。人机验证仅在官方验证区域准备好后显示，未准备好或存在账号表单的容器不会直接展示。

游客打开个人主题帖、评论、收藏、关注或账号设置时，会显示登录提示；登录前不加载这些页面，登录成功后返回原入口。通知页分为“互动 / 私信 / 提醒”：互动和私信未登录时显示登录提示，提醒保存本机关键词命中和追踪回复记录，游客也可查看。提醒可逐条或全部标记已读，重启后保留最近 500 条。浏览历史、应用设置、主题外观和公开用户主页仍允许游客访问。

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

## 官方工具插件（1.0.5 开发版）

侧栏「插件」进入已安装和市场；官方签名 `.stplugin` 可下载或本地导入。工具文件和索引保存在 `%LOCALAPPDATA%/SeekTop/plugins`，主体升级保留。签名目录地址为本仓库 `plugin-market` Release，不参与客户端稳定版更新。

宿主界面为 Compose，独立原生进程只接受结构化参数并回传 NDJSON，不获得浏览器桥接或论坛会话。它有普通 Windows 用户权限，不是任意第三方代码的安全沙箱。首次安装/更新会确认这个边界。插件使用直连/系统 TUN 网络，不继承应用代理。详细构建、签名和来源见 [plugins/README.md](../plugins/README.md)。

```powershell
python plugins/test_tools.py --build-with go --directory build/plugin-smoke
SeekTop.exe --check-tool-plugins E:/path/to/built/plugin-packages
```

第二个检查使用临时插件目录，验证正式公钥、包安装、IPC、取消与端口释放；不会读写实际账号配置。插件引擎构建独立于主体，不要求所有客户端开发者都安装 Go。

市场下载使用单独且不携带 Cookie 的 Chromium 会话，遵守应用代理范围；网络工具本身仍使用直连/系统 TUN，不继承应用代理。可使用 `SeekTop.exe --open-plugins` 或 `SeekTop.exe --plugin io.github.seektop.toolbox.iperf` 打开插件入口。

插件工具界面参考 Bettbox 的信息层级，以 Compose 重写：测速半圆仪表、真实采样曲线和结果指标；iperf3 模式/协议/方向快捷切换，服务端隐藏不适用参数；连通性结果采用平台卡片。说明按需展开，不再占据主结果区。

### 帖子 AI 分析（1.0.7 开发版）

打开主楼后，点击「AI 分析」直接弹出 Markdown 结果，自动采用当前板块提示词。弹窗只保留设置入口、复制、停止和重新分析。设置会进入独立设置页面，返回后仍可继续阅读原帖；不再要求安装 AI 模板插件，也不显示提示词编辑、追问或估值表单。

配置入口为「设置 → AI 分析设置」或弹窗的同名入口。支持多个 OpenAI 兼容服务商、通用和各板块提示词。图片是否发送在设置中统一选择，默认关闭；启用后仅向支持图片的模型发送前 8 张正文图片链接。点击分析会向服务商发送标题与主楼正文，不包含评论和论坛登录凭据。密钥沿用 Windows 加密存储，已有配置保留。模型结果可能有误，尤其价格、周期与日期需要自行核对。

AI 回归检查：`node desktop/tests/test_ai_analysis.cjs`、`python desktop/tests/test_ai_browser.py`，覆盖流式 UTF-8、加密调用、服务商隔离、取消、截断错误和图片开关持久化。

### 原生外部链接接管（1.0.8 开发版）

设置页的「外部链接接管」默认关闭。启用时先使用 Windows AssocQueryString 读取并保存 HTTP、HTTPS 当前浏览器的 ProgID 和名称，再注册 SeekTop Links 为可选处理程序，打开 Windows 默认应用页面。用户需要亲自把 HTTP/HTTPS 选为 SeekTop Links；程序不修改 UserChoice 或其校验值。之后从 Telegram、邮件等通过系统默认处理程序打开的 NodeSeek 帖子和用户页直接进入客户端，其他网址、账号管理等未支持页面通过 ShellExecuteEx 指定原浏览器 ProgID 打开，避免再次调用默认处理程序形成循环。浏览器页面内部导航不在此范围。

分流器为小型 .NET Framework 原生程序，不加载网页。每用户的命名管道仅允许当前 Windows 用户连接，向已运行或托盘中的客户端交付帖子地址；冷启动通过互斥锁串行化，避免连续点击产生多个实例。页码及数字楼层锚点保留。关闭后，所有网址继续转给原浏览器；在 Windows 重新选择浏览器可完全恢复系统关联。

捕获到的浏览器与转发器保存在 `%LOCALAPPDATA%/SeekTop/links`，不与论坛登录共享信息。卸载时先关闭原生接管：若用户已恢复浏览器，清理处理程序注册；若仍以 SeekTop Links 为默认，保留小型转发器和浏览器记录，确保卸载后其他网址仍能打开。用户仍需在 Windows 默认应用恢复原浏览器。便携版迁移路径后需要重新启用，以更新客户端位置。

回归检查：`python desktop/tests/test_link_router.py`。所有注册表测试使用随机身份，不修改真实默认关联；覆盖实际 Win32 原浏览器转交、URL 白名单、管道、冷启动互斥、关闭和卸载退化转发。

### DeepSeek 流式回答兼容修复（1.0.8）

旧版固定 `max_tokens=4096`，只读取 `delta.content`，因此 DeepSeek 默认思考阶段可能耗尽额度，随后被错误提示为空回答。新版针对官方 DeepSeek 接口显式默认关闭思考，普通回答上限为 8192；设置可开启深度思考，额度为 32768。思考阶段只显示生成状态，不把模型思考文本冒充最终回答；正文每批到达即显示 Markdown。不会自动重试产生额外分析请求。

同时兼容文本分段数组、JSON 回退和缺失标准 Content-Type 的 SSE；区分输出额度耗尽、仅有思考、内容过滤、接口格式不兼容和真实网络错误。达到额度时保留已有正文并提示不完整。API Key、Cookie 和原始服务商错误正文不会加入错误提示。官方参数参考 https://api-docs.deepseek.com/guides/thinking_mode/ 。

### 首次提示、追问与全楼层分析（1.0.9 开发版）

首次使用该功能时，在桌面初始化及签到询问之后提示是否接管外部链接。「暂不接管」也会记住选择，不在后续启动重复询问；已有接管配置的用户跳过提示。启用仍需要用户在 Windows 确认 HTTP/HTTPS 默认关联，捕获失败时可重试或放弃。

AI 弹窗支持追问和补充事实，例如补上到期日期后重新估值。补充信息保留在本次窗口会话内，切换分析范围时继续带上。原「重新分析」改为范围菜单「只分析楼主／全楼层分析」，首次打开仍直接分析楼主。选择范围会重新读取对应内容；全楼层按顺序读取各页，不改变阅读页、缓存切片或滚动位置，随后分析当前账号可见、未屏蔽的内容，并显示页数、回复数和排除数。追问沿用最近一次选定范围的快照。

全楼层最大 100 页、约 12 万字符，任一页失败、分页变化、重复回复、账号变化或超过限额时不提交不完整的全楼层分析。读取与生成均可停止。头像、签名、表情图片和论坛凭据不发送给 AI；启用图片时最多发送 8 张正文/回复图片链接。补充最多 16 条、总计 1.6 万字符；关闭窗口后清除会话补充。上下文历史有长度限制，已补充事实单独保留，避免范围切换后丢失。

1.0.10：只分析楼主也会读取所有分页，纳入主帖及楼主后续回复，排除其他用户回复。默认通用和交易提示词要求简短输出，设置中可直接查看及修改，已有自定义内容不覆盖。两种范围均沿用 100 页、12 万字符限制。

1.0.11：启动时后台读取 `ai/exchange-rates.json` 缓存并按 24 小时有效期更新，运行中每小时检查；失败至少间隔 15 分钟重试。缓存 USD、AUD、CAD、CHF、CNY、EUR、GBP、HKD、INR、JPY、KRW、NZD、RUB、SGD、TWD。数据来自 [Frankfurter v2](https://frankfurter.dev/) 的混合参考汇率，以 CNY 为基准获取后取倒数，统一保存为每单位外币兑人民币，CNY 恒为 1。每个币种保留报价日期，缓存保留获取时间；失败不覆盖旧快照。请求在独立内存会话执行，不携带论坛 Cookie、帖子内容或 AI 密钥，固定 HTTPS 接口，响应上限 64 KiB，超时 10 秒，按“所有流量／仅论坛”设置代理。分析附加来源、日期、换算规则和旧缓存标识，用户明确指定的汇率优先。此为参考估值，不含支付点差和未声明费用。

汇率配置可在「AI 分析设置 → 参考汇率」查看和编辑，缓存与手动覆盖值一起保存在 `ai/exchange-rates.json`，手动值记录保存日期。更新自动汇率只替换自动快照；手动值优先，清空单项并保存或点击「全部恢复自动」可撤销覆盖。无网络时也可手动配置，CNY 固定为 1。
