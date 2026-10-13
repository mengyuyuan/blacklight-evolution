# Forge 1.20.1 dev.5 — 完整 251 份资料默认打包

本分支从上游 dev.7 接入 224 个基础运行文件，dev.5 再通过作者 PR #21（提交 `8145022db150253fb152eb0f9cac780a58f40654`）接入 27 个新技能动作 JSON。默认完整资料共 251 文件：240 个 JSON 和 11 个 JSON 格式 `.data`。默认输入 `art/motion-profile/`，输出到 JAR 内 `blacklight/bundled_motions/`。包含待机、走跑、跳跃、墙跑、武器变形、攻击、吸收、装甲、镜头及 HUD，以及毁灭技和子弹俯冲的主动作、蓄力、恢复与变体；既有 Forge `armor-growth.data` 保留。

`art/motion-profile-manifest.json` 记录字节数和 SHA-256。Forge 构建核验实际 Jar-in-Jar 重混淆成品的文件名集合、源字节、字节数和 SHA-256，`assemble` 依赖此检查。默认分类 bundled；显式 `-PbundleMotionDirectory` 完整替换并使用 external-profile。

## 构建环境

Minecraft 1.20.1、Java 17、Forge 47.4.10、GeckoLib 4.8.4 Forge 版。执行 `./gradlew.bat clean assemble --no-daemon`；运行客户端 `./gradlew.bat runClient --no-daemon`，不需外部动作目录。

安装候选：`Blacklight-Ascension-MC1.20.1-Forge-0.3.0-forge-1.20.1-dev.5-bundled.jar`。独立采样：`verifyStandaloneMotionAssets -PmotionProbeArchive=<完整成品JAR>`。普通安装无需 external-profile 或本机资料路径；GeckoLib 仍需单独安装。

## 验收边界

本分支结果见 [Forge 状态](FORGE_1_20_1_STATUS.md)。上游 dev.7 的动作采样及 80 物种截图属于上游记录，不代表 Forge 实机通过。Minecraft 1.20.1 不含犰狳、沼骸、旋风人；保留对应来源 JSON，但不得将它们算入本版本可召唤物种。
