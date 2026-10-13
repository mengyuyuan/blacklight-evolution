# dev.5 本维护仓同步状态

2026-10-13 已同步作者 `forge-1.20.1` 至 `2a8c6930d8eb570ef7a3c83c32cedc9135b8315a`，包含 PR #10、#13、#17、#18 的版本协作工具、新技能、红雾修复及感染实体删除后停止访问 capability；另接入作者 PR #21 资源分支 `8145022db150253fb152eb0f9cac780a58f40654`。Minecraft 1.20.1 / Java 17 / Forge 47.4.10 / GeckoLib 4.8.4 目标保持不变。

默认 bundled 已补齐 27 个新技能动作 JSON，共 251 份运行资料（240 个 JSON、11 个 `.data`），保留 Forge 的 `armor-growth.data`。不再要求作者另行提供 external-profile 路径；本次资源接入未修改玩法代码。开发预发布保留版本号 `0.3.0-forge-1.20.1-dev.5`。

本仓完整 251 文件包验证结果：

- `clean assemble`、Forge Jar-in-Jar 重混淆、非空 refmap、独立依赖边界和 251 文件集合/字节数/SHA-256 校验通过。旧 224 文件保持不变，新增 27 文件共 27,844,474 字节；逐文件 JSON 解析通过。
- 全量 Java 测试 276 项：272 通过、4 失败、0 跳过。此前缺资料导致的 `WeaponContentTest.eachMoveHasFiniteFullBodyAnimationWithMatchingDuration` 已通过；5 项 `DevastatorSourceTest` 本轮实际执行并通过。剩余失败仍是移动预设、两项移动升级和警戒/伪装差异，未为迁就断言修改玩法；完整测试命令因这 4 项失败返回非零。
- 成品动作采样：240 片段、5,040 轨道、996,870 次采样、0 失败；装甲变形与待机检查通过，Epic Fight 依赖缺席检查通过。使用 `--continue` 保留测试失败并继续执行采样，不能把整条命令记为全绿。
- Python 资产/版本工作流测试 18 项全部通过。

独立客户端加载检查完成：21:56:23 模组初始化，21:56:24 开始资源重载，21:56:30～31 完成 OpenAL 与全部图集加载；日志未见缺动作、Mixin 注入、着色器编译失败或 FATAL。本次仅验证客户端加载，未执行新技能或死亡删除游戏场景；专用服务端、多人和完整生存验收尚未完成，构建与动作采样不能替代这些场景。

证据：`JB/dev5-complete-build-tests.log`、`JB/dev5-complete-resource-verification.log`、`JB/dev5-complete-python-tests.log`、`JB/dev5-complete-client.log`。

资源补齐前的基础包验证（历史）：Java 测试 276 项，266 通过、5 失败、5 跳过；除 4 项继承差异外，`WeaponContentTest.eachMoveHasFiniteFullBodyAnimationWithMatchingDuration` 因缺动作失败，5 项 `DevastatorSourceTest` 因无扩展资料跳过。Python 资产/版本工作流测试 18 项全部通过（`JB/dev5-python-tests.log`）。224 文件基础包编译、重混淆、非空 refmap 和资源校验通过，动作采样 213 片段、4,473 轨道、780,381 次采样、0 失败，装甲变形/待机通过（`JB/dev5-base-motion-verify.log`）；首次漏传采样成品路径的失败未记为通过。实体删除 GameTest 的 7 个场景仅编译通过（`JB/dev5-lifecycle-compile.log`），未运行游戏场景。上述数字不作为完整 251 文件包的验证结果。

上游红雾修复和实机报告见 [FORGE_DEV5_MIST_FIX.md](FORGE_DEV5_MIST_FIX.md)、[FORGE_DEV4_SKILLS.md](FORGE_DEV4_SKILLS.md)。这些报告不等于本维护仓重新进行了实机、服务端或多人验收。下文保留先前版本历史。

# Forge 1.20.1 维护与验收状态

dev.3 历史记录（2026-10-13）：版本 0.3.0-forge-1.20.1-dev.3 的完整基础安装包已构建并作为开发预发布提供。

本分支独立维护 Minecraft 1.20.1 Forge 版本。原作者末者继续维护上游 1.21.1 NeoForge。移植基于上游提交 `d43aa4bf4263ad5ebf1de799cee06f48a0f8c018`，保留 GPL、作者署名及第三方资源声明。

## 目标环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Java | 17 |
| Forge | 47.4.10 |
| GeckoLib | 4.8.4，Forge 1.20.1 版 |
| ParCool | 1.20.1-3.4.3.3，可选 |
| Gradle | 8.9 |
| ForgeGradle | 6.0.54 |
| MixinExtras | Forge 0.4.1，内嵌 |

## dev.3 同步与验证

作者已将移植 PR #6 合并到专用 `forge-1.20.1` 分支。本次同步该分支提交 `c8da065758a682a0b5422e8729c770a98abe92ea`，包含 PR #7 的抓取 HOLD 阶段转向及持有者、握持位置、受抓生物的朝向插值修复；没有合入 NeoForge 主线或改动旧移动/警戒规则。

- 干净实际编译、Forge Jar-in-Jar 重混淆、非空 Mixin refmap 和 224 文件字节数/SHA-256 验证通过。
- 全量单元测试：209 项，205 通过、4 失败、0 跳过。新增 CarrySteeringTest 三项全部通过，覆盖 270 组朝向/输入组合、后退与侧向转身、无输入保持姿态。
- 四项失败与 dev.2 相同：移动预设与当前默认值不同、两项移动升级倍率断言、警戒/伪装断言。没有为了测试绿灯改动继承玩法或预设；历史预设还存在超出当前参数加载范围后被夹取的数值，发布不将这些差异记为通过。
- 成品动作采样与源码/分发包核验分别记录。dev.3 抓取实机试玩、专用服务端和多人验收未完成；dev.2 客户端记录只作为历史证据。

用户明确选择保留作者 dev.3 版本号、发布完整安装包；`publicReleaseReady=false` 保留稳定版验收含义。[dev.3 历史下载](https://github.com/lingweiliang2510579032/blacklight-evolution/releases/tag/v0.3.0-forge-1.20.1-dev.3)不含 dev.5 新技能；新增行为见 [抓取转向说明](FORGE_CARRY_STEERING_DEV3.md)。

## dev.2 验证与发布范围（历史）

已整合上游提交 `9ba910fb23e812fbad4fd5649f29a6026f7e9bfb` 的 6 个提交；默认 bundled 构建从 art/motion-profile 包含 224 个动作、镜头和 HUD 文件，显式外部整套资料使用 external-profile。版本、元数据、README、安装与默认构建均保持 Minecraft 1.20.1 Forge。

| 验证 | 实际结果 |
| --- | --- |
| Java 17 编译、Mixin、独立依赖边界 | 通过 |
| Forge Jar-in-Jar、重混淆、分发包 | 通过；安装 bundled.jar，不安装 bundled-slim.jar |
| 完整资料清单 | 224 个文件的集合、字节数及 SHA-256 通过 |
| 成品内动作采样 | 213 个片段、4,473 条轨道、780,381 次采样，0 失败；装甲变形/待机通过 |
| 全量单元测试 | 206 项：202 通过、4 失败、0 跳过 |
| 新增与修改的战斗回归测试 | 通过：单帧接触、零前摇采样、连击衔接和完整恢复 |
| 可选 GameTest / 视觉探针 | Forge / Java 17 编译通过；未运行完整场景 |
| 独立开发客户端 | 已加载完整资料并进入测试存档，用户实际操作 B 模式；更新后的客户端完成资源加载，液态材质不再报着色器编译失败 |
| 专用服务端、多人、完整生存流程 | 待验收 |

修复了源动作单帧接触/零前摇的采样边界、连击与原版受伤无敌时间的衔接，以及液态材质的 1.20.1 `fog_distance` 接口。移除了吞噬标签中本版不存在的犰狳，感染模型目录保留上游来源资料，但不声称支持 1.20.1 没有的三种生物。

发布收集器还发现一次干净构建从缓存还原 Java 类时未还原 Mixin 映射；声明 refmap / TSRG 输出后，MixinGradle 的打包钩子仍跳过缓存恢复的结果。因此关闭主源码编译缓存，让干净构建实际执行注解处理；其余任务仍使用缓存，并在 assemble 中验证成品含非空运行映射。开发客户端可忽略此文件缺失，正式成品不能忽略。

剩余 4 项失败均为继承的断言/现有行为差异：移动预设 `jump_min_velocity=1.2407766990291262` 对编译默认 `0.55`；未升级冲刺倍率 `1.08` 对旧断言 `1.0`；一级跳跃倍率 `1.1` 对旧断言 `1.2`；警戒测试期待立即解除伪装，现有实现保留伪装。此次没有为通过旧测试而更改这些玩法行为，不能宣称全量测试通过。

用户最初反馈 B 模式掉帧，随后确认调整设置后恢复正常并要求结束排查。没有据此宣称修复了代码性能缺陷，也没有改动后台整合包。

公开产物为开发预发布，包含完整 JAR、安装 ZIP、源快照和校验和。`publicReleaseReady=false` 表示尚未完成正式稳定版验收，不表示安装包缺资源。维护分支 `forge-1.20.1`；不向原作者提交整套降版 PR。

## dev.1 历史验证

| 验证 | 实际结果 |
| --- | --- |
| Java 17 主源码、Mixin 注解处理 | 通过 |
| 独立运行依赖边界检查 | 通过；没有 Epic Fight / Weapons of Miracles 字节码依赖 |
| `jar` / `jarJar` 与两种 Forge 重混淆 | 通过 |
| `assemble` 分发压缩包 | 通过 |
| 独立动作解析工具编译 | 通过 |
| 可选 GameTest 与视觉探针 | 全部 23 个探针通过 Forge / Java 17 编译；未运行游戏场景 |
| 新增 Forge 单元测试 | 9/9 通过：状态持久化、死亡/非死亡复制、可变 NBT 深复制、capability 生命周期、网络往返及输入边界 |
| 完整单元测试 | 192 项：180 通过、1 跳过、11 失败；细分如下 |
| 独立开发客户端，Java 17 | 实际进入模组加载，因 `Missing Blacklight motion: basic_idle` 停止 |
| 专用服务端 | 已尝试启动，仅到 EULA 检查；未接受协议、未完成模组/玩法验收 |
| 多人、生存、动作手感与最终可玩验收 | 未完成 |

启动日志未出现 Mixin 注入错误，只能说明已加载类的启动过程；不能替代伤害、吸收、吞噬冻结和渲染等玩法场景验证。没有改动用户正在运行的整合包实例。

## 完整测试的 11 项失败

- 7 项需要缺失的动作资料：`NativeLandingPoseTest` 2 项、`WeaponMotionClockTest` 3 项、`FirstPersonPoseTest` 2 项；包括 `basic_land_moving`、`basic_sprint`、`utility_armor_transform` 等。
- 4 项上游旧断言与当前实现不同：移动配置预设 1 项、移动升级 2 项、警戒/伪装 1 项。移动升级与警戒状态源码未改动；配置预设与编译默认值也保留原数值。未为了通过测试修改这些玩法规则。
- 观察到的差异：跳跃预设 `1.2407766990291262` 对编译默认 `0.55`；未升级冲刺倍率 `1.08` 对旧断言 `1.0`；一级跳跃倍率 `1.1` 对旧断言 `1.2`；警戒测试仍期待立即解除伪装。

因此不能宣称完整测试通过。Windows 中文路径下 Java 17 的测试参数编码已用 URI manifest JAR 处理；capability 测试复用 Forge 本身的 `CapabilityTokenSubclass` 转换器，未替换为假的存档或 capability 实现。

## dev.1 历史候选

版本 `0.3.0-forge-1.20.1-dev.1`，配置 `source-only`。本地收集器验证 Java class 版本不高于 61、Forge 元数据与原作者署名、GPL/第三方声明、重混淆输出一致、Mixin 与 refmap、语言资源以及分发包一致。候选不含私有动作资料，`publicReleaseReady=false`。

维护分支为 `forge-1.20.1`；未推送移植改动或创建公开 Release。源码、原资源声明与独立维护边界保留；不向上游提交整套移植 PR。

## dev.1 资源缺失历史（已关闭）

初次移植的上游基线缺少完整动作、HUD 和镜头资料，因此 dev.1 默认构建是 source-only。上游 dev.7 已将完整224文件公开并默认打包，此资源缺口已关闭，保留历史构建和启动失败记录供追溯。Forge 的完整玩法验收仍需单独完成；上游平台截图不作为本分支证据。

适配依据见 [移植研究](FORGE_1_20_1_RESEARCH.md)。
