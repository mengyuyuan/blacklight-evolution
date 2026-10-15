# Blacklight: Ascension

> **正式版 `0.3.0` · NeoForge 1.21.1。** 整合最新感染生物模型、持有生物转向、临界质量、正式毁灭技和子弹俯冲。核心、动作、模型、输入、镜头与服务端同步由黑光自身驱动，运行不再要求 Epic Fight、Epic ParCool 或 Weapons of Miracles。当前验证范围见 [独立化记录](docs/STANDALONE_MIGRATION.md)。

**黑光：进化 · 作者：末者**
Minecraft 1.21.1 / NeoForge 的黑光病毒同人 Mod，包含生物武器、抓取吸收、伪装、强化移动、感染生物与组织蜂巢。

## 当前版本与发布状态

项目按 Minecraft / 加载器维护独立分支：`main` 为 NeoForge 1.21.1，[`forge-1.20.1`](https://github.com/mengyuyuan/blacklight-evolution/tree/forge-1.20.1) 为 Forge 1.20.1。各版本独立构建、同步和发布；开发贡献请先阅读 [多版本协作规范](docs/MULTIVERSION_WORKFLOW_ZH.md)，可用 [快捷工具](docs/VERSION_TOOLS_ZH.md) 自动准备贡献分支和 PR 说明。

当前正式版为 **0.3.0 · NeoForge 1.21.1**。包含独立战斗与移动、新武器模型、液态变形、毁灭技、子弹俯冲、技能教程与当前按键显示。构建与验证记录见 [0.3.0 发布说明](docs/RELEASE_0.3.0.md)。本地打包不等同于已上传 GitHub 或其它平台；多人实机、长期性能和整合包兼容性没有全量验收。

默认 `bundled` 包包含完整 **251 份动作与配套数据**，包括大招、俯冲、瞄准、恢复、镜头、HUD 和装甲资料。直接从仓库构建即可获得完整运行资料，不再要求本机外部目录。额外必需 Mod 只有 GeckoLib；不依赖 Epic Fight。原始 FX 研究网格和贴图不进入运行包。

dev.9 同时保留上游的 80 种原版生物贴图与感染结构组合、新的原生模型部件绑定，以及 dev.8 持有生物时按镜头方向移动的修复。

- [dev.10 真实俯冲破坏、设置与第三人称修正](docs/DEV10_GAMEPLAY_QA.md)
- [dev.8 持有生物转向修复](docs/CARRY_STEERING_DEV8.md)
- [dev.7 完整资料与感染模型](docs/BUNDLED_PROFILE_DEV7.md)
- [安装、依赖与故障排除](docs/INSTALL_ZH.md)
- [液态武器变形的源依据与适配范围](docs/LIQUID_WEAPON_MORPH_SOURCE.md)
- [临界值、子弹俯冲与毁灭技的源依据](docs/CRITICAL_MASS_SOURCE.md)
- [毁灭技花费、直接碰撞与主轨结算边界](docs/DEVASTATOR_DAMAGE_RESEARCH.md)
- [身体恢复、组织回收与场景扭曲](docs/DEVASTATOR_EFFECT_SOURCE.md)
- [子弹俯冲的高度、材料与流式地形算法](docs/BULLETDIVE_ALGORITHM.md)
- [dev.9 本轮实现与验收清单](docs/DEVASTATOR_REFINEMENT_PLAN.md)
- [正式毁灭技、临界质量与地形冲击实机检查](docs/CRITICAL_MASS_QA.md)
- [80 种感染体的原生贴图与感染结构](docs/STANDALONE_INFECTED_MODELS.md)
- [dev.6 液态变形实机检查](docs/LIQUID_WEAPON_MORPH_QA.md)
- [当前已知问题与发布阻塞](docs/KNOWN_ISSUES_AND_SLOTS_ZH.md)
- [0.2.0 人工验收表](docs/ACCEPTANCE_0.2.0.md)
- [发布准备](docs/RELEASE_PREPARATION_ZH.md) · [平台计划](docs/PLATFORM_PLAN_ZH.md)
- [截至 RC56 的完整历史记录](docs/history/README-through-RC56.md)

## 安装环境

| 项目 | 当前使用版本 | 是否必需 |
| --- | --- | --- |
| Minecraft Java 版 | 1.21.1 | 是 |
| Java | 21 | 是 |
| NeoForge | 21.1.244 | 是 |
| GeckoLib | 4.9.2，NeoForge 1.21.1 版 | 是，单独安装 |
| ParCool | 3.4.3.3 | 可选兼容；不是动作来源 |

Blacklight JAR 不内置前置 Mod。请使用上表的明确版本，不以“最新版”代替；可选模组装入后也要满足兼容范围。安装包不是完整游戏或完整整合包。多人客户端和服务端需要匹配的 Mod 版本、依赖与动作资料；联机行为仍待验收。

退出游戏并备份存档、配置，把对应 NeoForge 版本的依赖与 Blacklight JAR 放入实例的 mods 目录，移除旧的 Blacklight JAR，只保留一份。正式版默认 `bundled` 包包含完整 251 文件资料。具体步骤见 [安装文档](docs/INSTALL_ZH.md)。

## 生存开始

1. 寻找雪地村庄的制图师，升至学徒，取得冰下研究站地图。已生成交易的旧村民不会自动换表；研究站只在符合条件的新区域生成。
2. 按地图探索研究站，在冷藏箱取得病毒安瓿。附近没有可定位的研究站时，制图师可能仍售原版地图。
3. 未感染时，手持安瓿按住使用键约 2 秒主动觉醒。接触感染体或被安瓿溅射不会强制感染玩家。
4. 按 B 进入黑光模式，通过战斗、抓取吸收和 U 面板推进成长。已感染后，潜行使用安瓿可投掷感染生物。

自然开局的交易、结构可达性与完整成长节奏仍需人工验收；调试解锁不能替代生存流程验收。详见 [研究站与觉醒](docs/RC18-survival-opening.md)。

## 默认操作

| 按键 | 用途 |
| --- | --- |
| B | Minecraft 普通模式 / 黑光模式 |
| Z | 按住打开能力轮盘；轮盘内 Tab 切页 |
| U | 升级、属性与设置面板；设置页可开关子弹俯冲破坏方块 |
| Tab | 游戏中锁定目标 |
| 左键 / 右键 | 当前形态的普通 / 特殊攻击 |
| E | 抓取；持有时再次按下投掷 |
| 持有时右键 / 左键 | 吸收 / 投掷 |
| 持有时 W/A/S/D | 普通持有阶段按实际控制镜头方向移动与转身 |
| X | 本体与最近一次成功吸收的可用身份切换 |
| G / H | 已解锁的防御能力 / 感知能力切换 |
| 空格 / 奔跑键 | 跳跃、蓄力及移动能力，取决于状态和升级 |
| F5 | 原版第一人称、第三人称背面与正面切换 |
| F8 | 移动引擎调参；普通试玩建议先保留默认值 |

以控制设置中的实际绑定为准。黑光模式中的 E 抓取可能与原背包键冲突，需要时重新绑定。未感染或普通模式保留 Minecraft 生存交互。

抓取需要目标可吸收、距离和视线有效。弱目标可直接抓取；强目标需削弱至最大生命的 15% 或以下。强弱分界使用**排除本 Mod 五倍生命单位后的最大生命 50**，不是把显示的放大生命数直接与 50 比较。大型目标使用攀附流程；宠物、载客、无敌等限制仍存在。

持有转向沿用 dev.8 修复：普通 HOLD 阶段的 W/A/S/D 按镜头解释，人物每 tick 最多转 45 度，握持点与受害者同步跟随。捕获、吸收、投掷和攀附仍遵守各自的动作朝向约束，见 [持有转向说明](docs/CARRY_STEERING_DEV8.md)。

## 临界值与正式毁灭技

在 U 面板学习临界值、对应毁灭技及容量升级。满血时，成功吸收的溢出治疗按源配置的 0.55 因子转成红血条后方的蓝色储备；基础容量为一份花费，三档容量升级各增加一份。**只要蓝色储备大于零就允许正式出招，不要求攒满一格。** 控制器实际开始时仍扣完整花费；蓝血不足的差额扣红血，耗尽蓝血退出临界时另扣半份花费，伤害耗尽蓝血也有退出损耗。一份花费在 Minecraft 中换算为最大生命的 25%，这是单位适配。

按住组合键至满蓄：左键＋右键为地刺墓地，右键＋E 为触须弹幕，左键＋E 为致命痛楚；离地时使用对应空中动作。提前松键接普通招式，不花临界。满蓄进入正式动作后，花费等待各招的实际控制器起点；此前取消不扣费，已经支出的费用不因取消返还。F5 保留原版视角切换，大招不锁鼠标。

学习滑翔与子弹俯冲后，在真实滑翔中按住右键蓄势、松开或满蓄释放俯冲。子弹俯冲不消耗临界；持续加速并累计真实落差，伤害与落地效果只在实际接地后触发。dev.9 移除了旧高度、下落速度及地形半径、深度、总破块数的固定威力上限。超过 6 格才破坏地形：高度越高，碗状坑、材料能力与可用能量继续增加。冲击只流经落点连通的已加载区块，不强行加载区块；世界高度、材料、权限和逐块保护仍限制实际破坏。dev.10 使用原生方块破坏、反馈与掉落，箱子和可破坏的方块实体同样受冲击；基岩保留。U → 设置可关闭自己的俯冲破坏，服务端保存并同步。全服按 tick 预算分批处理，不削减高度决定的总能量。`doTileDrops` 控制方块掉落，容器库存按原版散落规则处理。详见 [俯冲算法](docs/BULLETDIVE_ALGORITHM.md)。

肾上腺激增提供 1.8 秒伤害保护和一次限时免费大招机会，免费释放间隔为源配置的 22.5 秒；残血触发阈值、五秒释放机会和最大生命换算是 Minecraft 适配。

本轮身体动作使用本地核对的正式分支、充能、俯冲、接地与恢复曲线，致命痛楚接入向上／向下瞄准姿态。墓地复用项目已有利爪几何与贴图重建方块地刺，dev.10 单根高度为 2.6／3.6／4.8 格、簇高为 2.9／4.0／5.2 格，主根宽约增加到旧版的 3.8 倍；生成与回收沿地面上下平移，不压扁模型。组织束按各自的传播和返回时钟收回。场景扭曲使用原创 Minecraft 后处理；身体收招结束会释放操作，剩余视觉尾迹单独淡出。地刺与触须主效果范围约 33／25 格，视觉规模不扩大伤害判定。

主目标控制与直接碰撞分轨记账、各轨对每目标去重，主轨只在启动时选一批目标。原始接触窗口已核对，但最终 receiver 回调、部分原引擎时间原点和跨通道共享记录尚未完全闭环；当前 Minecraft 结算与传播调度如实标为适配。详见 [伤害研究](docs/DEVASTATOR_DAMAGE_RESEARCH.md) 与 [效果依据](docs/DEVASTATOR_EFFECT_SOURCE.md)。

## 常用指令

修改类指令需管理员权限或单人允许作弊，并由玩家执行。status 可查看自己的状态。

| 指令示例 | 用途 |
| --- | --- |
| `/blacklight status` | 查看感染、模式、生物质、EP 与当前形态 |
| `/blacklight grant_ep 1000` | 给自己追加 1000 进化点；单次范围 1～100000 |
| `/blacklight infect` | 让自己感染，供调试使用 |
| `/blacklight spawn_infected zombie` | 在前方生成普通感染僵尸，需空间可用 |
| `/blacklight spawn_colossus iron_golem` | 生成成熟巨构；也支持 warden、wither、ender_dragon，需足够空地 |
| `/blacklight hive locate` | 分段搜索巨巢；默认半径 32768，可追加 1024～65536 的半径 |
| `/blacklight hive status` / `/blacklight hive cancel` | 查看或取消当前搜索、建造任务 |
| `/blacklight colossus_status` | 查看附近核心的成熟、宿主成长、生物质和暂停原因 |
| `/blacklight hive accelerate 20` | 将附近一座巨巢及其配对吞噬者设为 20 倍；1 恢复正常 |

管理员在准备好的空地可用 `/blacklight hive place` 建造巨巢。它会改造约 **113×113 格**范围，要求区域已加载并通过地形保护检查；取消后已建部分保留，不回滚。执行前备份，详见 [巨巢指令](docs/RC54-disguise-and-hive-commands.md)。搜索结果会区分现存巨巢与尚未加载核实的候选位置。

加速不赠送生物质，不跳过真实吞噬、核心、营养节点和空间条件。**体型巨大不等于进化完成**：完成前摧毁核心会导致成长衰退。看生命条阶段和状态指令确认，见 [成长与锁定](docs/RC56-devourer-health-and-targeting.md)。

## 当前内容与边界

已接入利爪、刀锋、拳套、鞭拳、肌肉增强及空手战斗，抓取吸收与受害者表现，护盾、装甲、两种感知，强化移动，最近吸收身份切换，研究站开局、感染传播、巨型组织蜂巢与实际吞噬成长。包内资料提供当前接入的动作、镜头与 HUD；这些实现不代表原游戏全部机制已移植。

dev.6 为五种攻击武器加入液态变形，以第三人称为主要展示：组织沿手臂推进，波形几何鼓动并逐渐塑形，附着四股组织流，湿材质随光照和观察角度产生细微反光。第一人称沿用现有原版手部位置。这是依据本地原作资料重建的效果，尚未一比一还原原版全部碎片、蓄势和爆发特效，详见 [源依据与适配范围](docs/LIQUID_WEAPON_MORPH_SOURCE.md)。

80 种普通感染体将已确认的感染结构、啃食缺口和局部腐败血痕结合到实际原版模型及当前变种／资源包贴图，保留原版像素风格。新部件绑定跟随原生行走、飞行与游泳姿态；羊毛染色、史莱姆外层、潜影贝开合等各自保留原生部件表现，见 [感染模型说明](docs/STANDALONE_INFECTED_MODELS.md)。四类巨构及其成长形态使用专属模型、基础动作与分段判定，**完整破茧演出和巨构专属战斗连招尚未完成**。

军事科技、可建实验室、黑色守望与超级士兵、城市与载具、记忆网和剧情任务尚未完成。伪装只保存最近的可用身份，使用玩家碰撞体与视点，不获得目标技能。普通攻击裂纹和碎块主要是视觉反馈；正式子弹俯冲按上述高度与保护规则进行实际方块破坏。多人、性能和完整生存平衡仍待验收。

## 构建与命名

~~~text
gradlew.bat clean assemble --no-daemon
~~~

默认编译打包并检查独立运行边界及仓库的 224 文件资料，不运行单元测试、碰撞测试、GameTest 或自动游戏验收。完整新增技能与身体恢复需要整套 251 文件资料；本机开发构建示例：

~~~powershell
.\gradlew.bat clean assemble "-PbundleMotionDirectory=D:/Blacklight-Standalone/motion-profile-dev9" --no-daemon
~~~

外部资料整套替代默认资料，不按单个文件混用。主线整合已合并为 `21b2074`。本轮最终 59 项针对性测试和 17 个实机用例通过，20 项真实临界质量事件检查通过，原生 v3 为 17,141 帧／142 图片／零 violations；251 文件已逐字节核对。完整结果及明确未解边界见 [dev.9 验证](docs/DEVASTATOR_DEV9_QA.md) 和 [完成清单](docs/DEVASTATOR_REFINEMENT_PLAN.md)。此前 dev.7 的 244 文件材料单独保留为历史记录。

文件保留 Minecraft 和 Mod 两个版本号：

- 默认 224 文件构建：`Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.9-bundled.jar` / `.zip`。
- 本轮完整 251 文件构建：`Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.9-external-profile.jar` / `.zip`。
- 原始源码快照：`Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.9-sources.zip`。

这些名称区分用途，不表示完成全部玩法验收。代码许可见 [LICENSE](LICENSE)，资源记录见 [资源来源说明](THIRD_PARTY_ASSETS.md)。[公开仓库](https://github.com/mengyuyuan/blacklight-evolution) · [贡献说明](CONTRIBUTING.md)。

## 独立被动药水

永久被动药水和击杀成长已拆为 [Adaptive Passives](standalone-passives/README.md)，独立构建与安装。黑光保留进化点技能，两者同装不会重复结算。
