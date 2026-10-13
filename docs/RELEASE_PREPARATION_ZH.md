# Forge 1.20.1 发布准备

开发版本：0.3.0-forge-1.20.1-dev.5。保留作者 dev 标记，完整安装包以 GitHub 开发预发布提供，另保留本地发群包；正式稳定版仍需完成余下验收。

1. Java 17 / Forge 47.4.10 / GeckoLib 4.8.4 环境构建 `clean assemble`，必须通过独立依赖边界、Forge 重混淆以及 bundled 251 文件字节与 SHA-256 校验。
2. 分别运行完整单元测试、动作采样、GameTest、独立客户端、服务端与多人检查；保留失败，不用上游的截图代替本版证据。
3. 用 `python tools/package_release.py --profile bundled` 收集完整 JAR、ZIP、源快照和校验清单。
4. 安装成品为 `Blacklight-Ascension-MC1.20.1-Forge-0.3.0-forge-1.20.1-dev.5-bundled.jar`；不分发 bundled-slim 中间文件。源快照包含仓库动作资料，不包含用户个人导出或测试存档。
5. 核对原作者署名、GPL 和资源来源说明。本轮推送 forge-1.20.1 并发布开发预发布；保留 4 项继承的测试差异和未完成的多人/生存验收，不宣称稳定版全部通过。

本轮通过作者 PR #21 接入新增 27 个动作 JSON，默认完整资料共 251 文件（240 个 JSON、11 个 `.data`），保留既有 Forge 装甲成长资料。旧 source-only/local-preview 文档属于历史；显式 external-profile 仅供开发者整套替换，不是普通安装要求。当前验收结果见 [维护状态](FORGE_1_20_1_STATUS.md)。
