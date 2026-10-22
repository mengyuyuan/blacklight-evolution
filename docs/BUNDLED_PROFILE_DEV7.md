# dev.7 — 完整资料与感染模型整合

dev.7 整合已合并的五种武器液态变形，以及 80 种原版生物模型、原版变种贴图与已确认感染结构的组合渲染。

## 动作资料补齐

以前的 GitHub 仓库只含运行代码，完整动作从本机目录读取。普通源码构建因此缺少 `basic_idle` 等必要片段，不能启动。本次将同一套 **224 个文件**直接纳入 `art/motion-profile/`，包含 213 个动作 JSON 和 11 个 `.data` 文件；`.data` 内容也是 JSON。

包括待机、走跑、跳跃、滑翔、墙跑、翻越、落地、各武器攻击与变形、抓取投掷、吸收主角与受害者动作，以及盾牌、装甲、镜头与 HUD。文件与此前完整试玩资料逐字节一致，没有重新生成或替换动作。

`art/motion-profile-manifest.json` 记录每个文件的字节数。默认构建会检查清单字节数、成品文件齐全且与输入字节一致；旧清单若提供 SHA-256 仍会核对。发布收集器现场计算输入摘要，再比对成品及源码快照。新增数据不会重新引入旧动作前置。

## 普通源码构建

Java 21、Minecraft 1.21.1、NeoForge 21.1.244。安装游戏时仍需单独安装 GeckoLib 4.9.2。

```powershell
./gradlew.bat clean assemble --no-daemon
./gradlew.bat runClient --no-daemon
```

Linux 使用 `bash ./gradlew assemble --no-daemon`。不需要指定本机动作路径。

输出命名：`Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.7-bundled.jar` / `.zip`。

默认构建进行独立运行边界和动作打包检查。单元测试、GameTest、自动游戏验收按项目流程另行执行。`verifyStandaloneMotionAssets -PmotionProbeArchive=<成品JAR>` 可不依赖游戏或 Epic Fight 核对每个动作关键帧及插值。

模型实机材料与范围见 [感染模型说明](STANDALONE_INFECTED_MODELS.md)，液态变形检查见 [dev.6 检查记录](LIQUID_WEAPON_MORPH_QA.md)。开发版不代表全部多人、巨构战斗连招和完整生存玩法已验收。

## 本轮实际验证（2026-10-12）

- 不指定 `bundleMotionDirectory` 的 `clean assemble` 通过；默认成品 224 个文件的字节与清单 SHA-256 全部一致，独立运行边界通过。
- 独立动作采样器检查 213 个片段、4473 条轨道、780381 次关键帧/插值采样，失败 0，装甲变形和待机片段均在成品中。
- 不指定外部资料的真实客户端进入复制的 QA 存档，完成 80 种感染体的原版/变异对照渲染，14 张实际截图，全部骨骼绑定非空。文档中的 3 页来自此次运行。
- 感染模型分支在整合前执行的 17 项资源检查全部通过。本轮整合没有把历史已知失败的全部单元/GameTest 清单重跑，也没有将 dev.6 的液态变形检查冒充为新的完整玩法验收。
