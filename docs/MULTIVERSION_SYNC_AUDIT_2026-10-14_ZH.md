# 双版本同步审计 · 2026-10-14

## 结论与范围

不是“大量 PR 仍未合并”一个原因：已合并到 NeoForge 的 #20、#26 没有完整适配 Forge；Forge 的 #21 又有部分改进尚未进入 NeoForge。编译包、功能分支、长期分支和 GitHub Release 是不同状态。

本次对照的远程基线：
- NeoForge 1.21.1 `origin/main`: `980b06f23bebd79ca8b95e8e1220181ae758bb30`。
- Forge 1.20.1 `origin/forge-1.20.1`: `fbf63adb1f7082f05380d32e86937d067eea274e`。
- 本地继续工作：NeoForge `feat/neoforge-1.21.1-weapon-attack-growth`（已有 `f46555a`、`a98e208`）；Forge `feat/forge-1.20.1-weapon-attack-growth`（已有 `afa727c`、`6d6c26e`、`38cfb23`、`006ed09`）。本报告同时记录本次本地补丁，不能当作远程已合并清单。
- 通过 GitHub 实时读取确认仍开放的只有 [#25](https://github.com/mengyuyuan/blacklight-evolution/pull/25)（Forge 资料与前置说明）和 [#27](https://github.com/mengyuyuan/blacklight-evolution/pull/27)（NeoForge 攻击衔接等）。本轮没有合并这两个 PR。

检查了生产 Java 文件、共享动作资料及清单、客户端资源、数据包资源、结构 NBT、构建/版本配置和相关提交历史。Java 原始逐字比较为 Forge 368 个、NeoForge 354 个文件，公共文件中 253 个不同；过滤常见平台语法后的候选仍有 169 个。**这些数字不是漏同步功能数量**。用差异定位代码并人工核实调用位置；静态审计不代替游戏内、多人或整合包兼容验收。

## 本轮已补到本地，尚未发布

| 内容 | 来源 | 实际处理 |
| --- | --- | --- |
| 方块战甲、新利爪/刀锋/锤拳/肌肉/鞭拳及鞭梢 | [#20](https://github.com/mengyuyuan/blacklight-evolution/pull/20)，`56d6e2e9c0622d6cceffd4841b6443e34a586215` | Forge 更新 7 个模型 JSON、两张武器贴图、战甲成长资料及对应美术源文件；适配模型加载、掌心挂点、第一人称、抓取、拖尾、鞭索、装甲缩放及缓存。保留 Forge 的攻击姿态过渡和生命周期保护。 |
| 新刀臂的抓取/投掷防穿头回退动作 | [#26](https://github.com/mengyuyuan/blacklight-evolution/pull/26)，`4f697308cdff1f2ea1772c0924ec463ce639e933` | Forge 同步 `grab_blade.json`、`grab_blade_throw.json`，使新几何和配套姿态一起进入包。未重写整套身体动作。 |
| 方块抓取被自身禁止挖掘规则取消 | 本地新功能审计发现，两版共有 | `BlockThrowSystem` 为正在发布的精确 BreakEvent 建立作用域；`PrototypeSurvivalLock` 只放行这个事件。其他模组保护事件仍执行并可取消；finally 恢复嵌套作用域，取消时不移除原方块。 |

Forge 本地版本为 `0.3.0-forge-1.20.1-dev.9`；NeoForge 本地版本仍为 `0.3.4-dev.1`。这些是开发产物，不代表发布完成或所有缺口已补齐。

## 确认仍待同步：NeoForge → Forge

| 优先级 | 功能/修复 | 证据与缺口 | 来源 |
| --- | --- | --- | --- |
| 高 | 大范围目标查询与远程射线 | `CombatTargeting`：主线在超大范围查询时只遍历已加载实体，射线用 `getChunkNow` 避免强制读入远端区块；Forge 仍沿用一般实体查询/`level.clip`。 | #26 / `93d1db6` |
| 高 | 奖励与数值溢出保护 | `BlacklightState` 主线使用 long 中间值并夹取生物质、进化点、警报；Forge 仍先 int 相加。`DevastatorSystem` 地形半径加法也少了先转浮点的保护。 | #26 / `93d1db6`、`34f4e7f` |
| 高 | 非法动作包验证 | `ClawActionPayload`：主线拒绝非法动作 ID；Forge 将未知 ID 回退为切换利爪。需保留 Forge 网络接口适配验证。 | #26 / `93d1db6` |
| 高 | 飞行/乘骑时输入边沿保护 | `PrototypeMovementSystem.acceptInput` 缺少乘骑、鞘翅飞行、创造飞行的早退；服务器 tick 虽有过滤，但即时输入路径仍可进入。 | #26 / `93d1db6` |
| 高 | 大范围破坏负载控制 | `BulletdiveTerrain` Forge 仍为每 tick 2048 块/16ms；主线 512 块/8ms，另外根据服务器负载和实体数量预算控制。`BulletdiveBreakEffects` 缺少掉落空间索引合并。不是要删除俯冲功能。 | #26 / `34f4e7f` |
| 中 | 改键支持与技能演示 | Forge 缺 `SkillBindings`、`SkillTutorial`、`TutorialButton`、`TutorialGif` 和 27 个 GIF；攻击/抓取部分路径仍硬编码左右鼠标键，缺鼠标绑定和修饰键分派。主线 U 面板也允许未感染者浏览。 | #26 / `fd5c124`、`93d1db6`、`34f4e7f` |
| 中 | 护盾盾构机及升级/开关 | 缺 `ShieldBorerSystem` 和 `CombatSkill`、设置、网络、移动、界面全链路。需同时适配服务端裁定、默认关闭的独立开关与协议；不能只加按钮。 | #26 / `34f4e7f` |
| 中 | 锤拳贴地表现 | 缺 `HammerGroundContact` 以及 `BlacklightRenderPose` HAMMER_RAM 对应处理；合入时保留 Forge `PlayerMotionController` 的过渡采样。 | #26 / `34f4e7f` |
| 中 | 鞭拳曲线和动作时钟一致性 | `WeaponAttackRenderer` 主线使用 `WeaponMotionClock`；Forge 仍自行计算时间。`WhipCurveContact.attachHand` 主线沿曲线长度平滑挂接，Forge 只校正前四点。新模型挂点已经补齐，但这项动态连接修复仍待适配。 | #26 / `34f4e7f` |
| 中 | 模式切换的视角管理 | 缺 `ModePerspective`、`PrototypeModeCamera`：主线进入模式时切后方第三人称，尊重后续手动切换，退出时按所有权恢复之前视角。主追踪镜头并非整套缺失。 | #26 / `34f4e7f` |
| 中 | 热能/感染视角渲染修复 | `VisionPowerRenderer` 仍是旧的投影/实体派发路径；主线显式处理投影和模型视图并直接调用实体 renderer。此处混有平台 API 差异，必须定向移植验证，不能覆盖整个渲染类。 | #26 / `34f4e7f` |
| 中 | 子弹俯冲加速与高速视觉 | `BulletdivePhysics`：主线初速 .9、加速度 .36 并有 .04 的加加速度项；Forge 初速 .45、加速度 .22。缺 rush/FlightTime 后处理、较强气流表现、相机相对精度处理和 `BulletFlightCullingMixin` 本地主角高速裁剪保护。 | #26 / `7821e72` |
| 中 | 模式内物品/经验拾取 | Forge `PrototypeSurvivalLock` 仍取消拾取事件；主线已移除该限制。影响战利品、投掷方块回收及黑色守望掉落的实际流程。 | #26 / `34f4e7f` |
| 中 | 墙面动作缓存生命周期 | `ClientWallMotion` 缺主线 tick 清除过期项和 logout 清空处理。 | #26 / `93d1db6` |
| 低 | 验收与构建辅助设施 | Forge 没有主线 release audit、教程、模型等部分辅助入口；动作清单本身完整，但 stage 阶段的大招必需文件额外断言未完全一致。已有 Forge 成品完整性校验必须保留。测试源码没有运行，不可沿用来源分支历史通过数量。 | #26 / `93d1db6` 等 |

## 反向缺口：Forge → NeoForge

主要来源是已合并的 [#21](https://github.com/mengyuyuan/blacklight-evolution/pull/21)，实现 `c4f168cc66f84913fc6e34f21a1145f826f17c92`。NeoForge 对应 [#27](https://github.com/mengyuyuan/blacklight-evolution/pull/27) 仍开放。

- `CombatPoseTransition`、`CommittedPoseInterval`、`CombatFacingTransition`、`SourceClawRecovery` 及控制器/渲染接线：攻击之间、攻击转移动、朝向和利爪收招的连续过渡。
- `SourceCameraShake`：Forge 有按时间采样的平滑噪声及震动进入/退出包络，主线仍使用每次更新重新取随机数的版本。
- 骨架挂点及拖尾路径的配套改进。移植新模型时必须保留，不能用旧的主线渲染文件把它们覆盖掉。
- `consumable` 标签：Forge 已含 `minecraft:slime`，主线尚未含。`armadillo` 差异则因为 MC 1.20.1 不存在犰狳，属于正确的平台差异。

Forge 的 capability 失效保护、实体移除后 tick 防护以及本地 dev.7/dev.8 的 kill 崩溃修复也不能被 NeoForge attachment 实现覆盖。它们需按各自加载器生命周期评估，不是逐字同步目标。

## 审计额外发现：平台适配错误（待修）

Forge `PrototypeMovementSystem` 把 NeoForge 的总上台阶高度写进 `ForgeMod.STEP_HEIGHT_ADDITION`：停用恢复路径写 .6，启用路径写 1.0/.6。Forge 47.4.10 源码 `IForgeEntity.getStepHeight()` 明确使用 `vanillaStep + attribute`；默认玩家 .6 的情况下会得到 1.2 / 1.6，而预期是 .6 / 1.0。这不是可忽略的 API 名称差异，需要修正增量语义并检查旧玩家属性和其他模组修饰器。此报告记录问题，尚未改该移动参数。

## 已核实相同或属于合理差异

- `art/motion-profile` 两侧全部 253 个文件（含 README）字节一致；manifest 各有 **252 项资料**。不能称 Forge 还缺那 27 份毁灭技/俯冲资料，它们已由 #21 补齐。
- `assets/blacklight/infected_models` **83 个文件完全相同**；`assets/blacklight/geo` **9 个文件完全相同**。本次查出的旧战甲/武器是另一套独立模型资源和渲染适配的漏同步。
- 两个结构 NBT：`frozen_research_station`、`blackwatch_outpost` 解压解析后去掉 DataVersion，内容完全一致；Forge 3465 / NeoForge 3955 为各自游戏数据版本。研究站结构 JSON 的 `liquid_settings` 为版本格式差异。
- `recipe(s)`、`loot_table(s)`、`structure(s)`、`advancement(s)`、标签单复数目录、`forge/neoforge` biome_modifier 命名空间，以及 recipe result 字段需要各版本格式，不能硬复制。
- Java 17/21、Forge capability / NeoForge attachment、事件注册、网络序列化、MixinExtras、Shader 顶点格式、实体属性 Holder、VertexConsumer API、模型 RGBA 参数、粒子/音效替代等均是平台适配内容。Forge 额外适配类不等于多余文件。
- 血量倍率、感染宿主/巨构 Boss、蜂巢吞噬主体和伪装主体没有发现整套功能缺失；相关公共源文件差异主要是已记录的平台调用和生命周期处理。
- 本地两版均已有攻击放大、方块投掷、黑色守望士兵/哨站。**存在代码不等于已通过实机验收**，本轮发现的方块事件自拦截就是例子。

## 验证与后续执行顺序

本轮验证结果见后续追加的构建记录。默认只执行 assemble、资源/来源静态核对，不运行游戏单元测试或 GameTest。

1. 保留本地新模型及刀臂配套动作，完成目标版本打包与人工外观检查。
2. 优先处理边界/生命周期、输入即时路径、台阶语义、掉落回收与负载预算，分版本独立提交。
3. 同步改键/教程、锤拳/鞭拳/视角表现；单独适配盾构机含网络和保存格式。
4. 审阅 #27，将 Forge 已有攻击衔接改进接入 NeoForge，避免两版来回覆盖。
5. 每个目标版本独立 PR、构建、验收和发布；同步记录引用上表来源 SHA。不要把两个长期分支整支合并。

当前没有 kill/重生回归通过证据；此前客户端仍是 dev.8 测试副本。游戏内操作工具未获准控制 Java，未执行自动 kill/重生或模型外观验收。报告不得替代实际结果。

### 本轮构建实录

- Forge Java 17 `assemble --offline --no-daemon --max-workers=2`：通过；成品完整性任务核对 252 项资料通过。模型适配本地提交 `f5a61a7`。
- NeoForge Java 21 `assemble --offline --no-daemon --max-workers=2`：通过；成品完整性任务核对 252 项资料通过。
- 直接打开 Forge bundled JAR，确认 7 个模型、2 张贴图、2 段刀臂回退动作、armor-growth 与 NeoForge 源资源字节一致；4 个 Forge 攻击过渡/收招类仍在成品中。
- Forge JAR：`Blacklight-Ascension-MC1.20.1-Forge-0.3.0-forge-1.20.1-dev.9-bundled.jar`，SHA-256 `3f451dae4ee3a296934e1cda3abc411b5047766373f0710316556eca6d46b8c7`。
- NeoForge JAR：`Blacklight-Ascension-MC1.21.1-NeoForge-0.3.4-dev.1-bundled.jar`，SHA-256 `cce52872e5a75fe373352d55c7af15778aefce510160da8b3975531cc9c04aeb`。
- 两版 `git diff --check` 通过。未执行单元测试、GameTest、多人验收、kill/重生回归或实机模型外观验收；未发布、推送或合并 PR。
