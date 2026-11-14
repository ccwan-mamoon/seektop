# 网络工具来源与许可

SeekTop 新增网络工具包装、协议与界面声明沿用本仓库 GPL-3.0。每份插件包含 LICENSE.txt 与 SOURCE.zip，对应源码包括精确依赖版本与构建说明。

实现调研参考 Bettbox，https://github.com/appshubcc/Bettbox ，快照 88b9597ad88f597e61a2843ad6fe33d5bdee8950（GPL-3.0）。没有复制其 Flutter 界面、Mihomo 内核或完整测速实现。

iperf 协议引擎依赖 https://github.com/appshubcc/go-iperf3 v0.1.0（MIT，Copyright (c) 2026 litesme），完整许可见 IPERF-LICENSE.txt。它与官方 iperf3 3.x 协议互通，但不是 ESnet 官方 Windows 可执行程序。

golang.org/x/sys v0.30.0 使用 BSD 3-Clause 许可，完整许可见 XSYS-LICENSE.txt。Go 编译器运行库许可包含在对应 Go 工具链源代码中；构建者须保留 Go 的 BSD 许可，打包脚本另外附带 GO-LICENSE.txt。

公网测速使用 Cloudflare speed.cloudflare.com，平台检测访问用户选定的服务；这些服务的名称属于各自权利人，不代表合作、背书或可用性保证。
