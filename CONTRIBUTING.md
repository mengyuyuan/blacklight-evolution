# 参与 Blacklight Evolution

欢迎参与代码、建模、动画、特效和测试。项目目前处于原型迭代阶段，目标是制作受 Prototype 启发的 Minecraft 模组。提交功能前请先说明你要解决的问题；大型改动先讨论设计和分工，避免多人同时重做同一套动作。

项目原创内容采用 [GPL-3.0-only](LICENSE)。公开协作仓库：https://github.com/mengyuyuan/blacklight-evolution 。提交贡献时，请确认你有权按此许可证提供这些内容；第三方文件保留原有许可和署名。

## 多版本协作

`main` 维护 NeoForge 1.21.1 / Java 21，`forge-1.20.1` 维护 Forge 1.20.1 / Java 17。新增版本、功能分支、跨版本同步、PR 目标、发布与清理遵循 [多版本规范](docs/MULTIVERSION_WORKFLOW_ZH.md)，支持分支登记在 [versions.json](docs/versions.json)。一个目标版本一个 PR，跨版本同步记录原 PR / SHA 并在跟踪 Issue 中逐版本确认；不把整套平台移植合入 `main`。

## 开发环境

- Java 21，使用仓库内的 Gradle Wrapper。
- Minecraft 1.21.1、NeoForge 21.1.244；GeckoLib 由 Gradle 获取；独立版不要求 Epic Fight、Epic ParCool 或 Weapons of Miracles。
- 默认只编译打包：Windows 使用 `./gradlew.bat assemble --no-daemon`；Linux/macOS 使用 `bash ./gradlew assemble --no-daemon`。
- 用户明确要求启动试玩时，使用 `./gradlew.bat runClient`；Linux/macOS 替换为 `bash ./gradlew`。
- 第一轮构建需要网络下载依赖。不要提交 `.gradle/`、`build/`、`run/`、存档或个人账户配置。

默认源码构建使用 `art/motion-profile/` 的完整 224 个动作、HUD 和镜头资料，生成 bundled 包并校验成品清单，无需本机外部资料目录。source-only / local-preview 属于旧构建历史；资源说明见 `THIRD_PARTY_ASSETS.md`，当前打包说明见 [完整资料打包](docs/BUNDLED_PROFILE_DEV7.md)。

默认构建与 CI 不运行单元测试、碰撞测试、GameTest 或游戏内验收；这些检查仅在用户明确要求时运行。编译打包成功不代表真人试玩已通过。

## 目前适合参与的工作

- **动画与建模**：墙跑、慢速攀爬、静止抓墙和沿墙跳跃之间的衔接；利爪与头部、身体的空间关系；多个观察角度下的动作表现。
- **移动与战斗**：联网延迟下的客户端预测和服务端状态同步；转角、凹凸墙体、墙顶及战斗打断。
- **测试**：复现手脚反向、模型穿插、动画重播和卡墙问题，提供操作步骤及短视频。
- **文档与翻译**：安装说明、操作说明及中文/英文文本。

## 代码与素材入口

- `src/main/java/com/codex/blacklight/movement/`：移动逻辑；`WallTraversal` 为客户端和服务器共用的墙面状态机，`WallContacts` 负责方块接触检测。
- `src/main/java/com/codex/blacklight/client/`：客户端预测、动作选择及渲染。
- `src/main/java/com/codex/blacklight/animation/`：独立动作时钟、骨架采样、姿态与 Root 移动。
- `src/main/java/com/codex/blacklight/client/animation/` 与 `client/renderer/`：模型姿态、原生第一人称手臂挂接与渲染。
- `tools/`：Blender 创作、烘焙及预览脚本。脚本需要额外的骨架、插件源码或 `.blend` 输入，目前尚不是新克隆仓库即可完整重建美术资产的流程；开始美术工作前请向维护者确认源文件。
- 现有第三方声明见 `THIRD_PARTY_ASSETS.md`、`THIRD_PARTY_NOTICES.md` 和资源目录内的许可文件。

## 提交改动

1. 从对应长期版本分支建立自己的功能分支；一个改动集中解决一个问题，PR 的 base 选择该长期分支。跨版本适配另开目标版本 PR，并引用来源 PR / SHA。
2. 描述修改前后的行为，附复现步骤。动作和模型改动提供可编辑源文件和导出结果；已有正面、侧面或运动视频可附在 PR 中，没有游戏内验收材料时如实说明。
3. 运行 `assemble` 编译打包。只有用户明确要求时才运行相应测试；如要求墙面碰撞测试，可执行 `runGameTestServer -PwallTraversalTest`，使用独立的 `run-wall-gametest/` 世界。
4. 在 PR 中填写验证结果、未验证项和素材来源。服务端裁定伤害与移动规则，不能只改客户端表现而遗漏同步。
5. 不上传从商业游戏提取的模型、纹理、音频或完整参考视频。原创或第三方资产应注明作者、来源和许可证，保留要求的声明。

历史记录：R7 有 82 项单元测试和 3 项方块碰撞测试通过；该记录不代表当前版本的测试结果，也不等于所有动作、联网场景与真实手感均已验收。
