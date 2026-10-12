# Blacklight: Ascension

> **独立开发版 `0.3.0-standalone-dev.8`。** 基于现有 RC59 工作区，核心、动作、模型、输入、镜头和服务端同步已改由黑光自身驱动，运行不再要求 Epic Fight、Epic ParCool 或 Weapons of Miracles。无这些模组的客户端和 GameTestServer 已实际启动。当前仍在检查脱离前置后的姿态与移动回归，结果见 [独立化记录](docs/STANDALONE_MIGRATION.md)。

**黑光：进化 · 作者：末者**
Minecraft 1.21.1 / NeoForge 的黑光病毒同人 Mod，包含生物武器、抓取吸收、伪装、强化移动、感染生物与组织蜂巢。

## 当前版本与发布状态

项目按 Minecraft / 加载器维护独立分支：`main` 为 NeoForge 1.21.1，[`forge-1.20.1`](https://github.com/mengyuyuan/blacklight-evolution/tree/forge-1.20.1) 为 Forge 1.20.1。各版本独立构建、同步和发布；开发贡献请先阅读 [多版本协作规范](docs/MULTIVERSION_WORKFLOW_ZH.md)。

当前为 **0.3.0-standalone-dev.8，独立开发版**，尚未公开发行，也未将完整人工验收标记为通过。项目按现有黑光能力与感染生存玩法收尾，不宣称完整复刻原游戏。

**dev.7 已将完整运行资料纳入源码。** `art/motion-profile/` 包含 224 个动作、装甲、受害者、镜头与 HUD 文件，默认构建会完整打包；无需本机外部资料目录。所有文件保持此前试玩版内容，构建会检查包内文件与仓库 SHA-256 清单一致。此版整合 dev.6 液态武器变形和 80 种原版贴图结合感染结构的修复。

- [dev.8 持有生物转向修复](docs/CARRY_STEERING_DEV8.md)
- [dev.7 完整资料与感染模型](docs/BUNDLED_PROFILE_DEV7.md)
- [安装、依赖与故障排除](docs/INSTALL_ZH.md)
- [液态武器变形的源依据与适配范围](docs/LIQUID_WEAPON_MORPH_SOURCE.md)
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

退出游戏并备份存档、配置，把对应 NeoForge 版本的依赖与 Blacklight JAR 放入实例的 mods 目录，移除旧的 Blacklight JAR，只保留一份。当前构建使用资料完整的 bundled 包。具体步骤见 [安装文档](docs/INSTALL_ZH.md)。

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
| U | 升级与属性面板，查看具体生命等数值 |
| Tab | 游戏中锁定目标 |
| 左键 / 右键 | 当前形态的普通 / 特殊攻击 |
| E | 抓取；持有时再次按下投掷 |
| 持有时右键 / 左键 | 吸收 / 投掷 |
| X | 本体与最近一次成功吸收的可用身份切换 |
| G / H | 已解锁的防御能力 / 感知能力切换 |
| 空格 / 奔跑键 | 跳跃、蓄力及移动能力，取决于状态和升级 |
| F8 | 移动引擎调参；普通试玩建议先保留默认值 |

以控制设置中的实际绑定为准。黑光模式中的 E 抓取可能与原背包键冲突，需要时重新绑定。未感染或普通模式保留 Minecraft 生存交互。

抓取需要目标可吸收、距离和视线有效。弱目标可直接抓取；强目标需削弱至最大生命的 15% 或以下。强弱分界使用**排除本 Mod 五倍生命单位后的最大生命 50**，不是把显示的放大生命数直接与 50 比较。大型目标使用攀附流程；宠物、载客、无敌等限制仍存在。

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

80 种普通感染体将已确认的感染结构、啃食缺口和局部腐败血痕结合到原版模型及变种贴图，保留原版像素风格。四类巨构及其成长形态使用专属模型、基础动作与分段判定，**完整破茧演出和巨构专属战斗连招尚未完成**。

军事科技、可建实验室、黑色守望与超级士兵、城市与载具、记忆网和剧情任务尚未完成。伪装只保存最近的可用身份，使用玩家碰撞体与视点，不获得目标技能。攻击裂纹和碎块主要是视觉反馈，不代表通用地形破坏。多人、性能和完整生存平衡仍待验收。

## 构建与命名

~~~text
gradlew.bat clean assemble --no-daemon
~~~

默认编译打包并检查独立运行边界及完整动作资料，不运行单元测试、碰撞测试、GameTest 或自动游戏验收。需要替换整套开发资料时可使用 `-PbundleMotionDirectory=<完整资料目录>`，产物标为 external-profile。

文件保留 Minecraft 和 Mod 两个版本号：

- 默认完整构建：`Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.8-bundled.jar` / `.zip`。
- 外部开发资料构建：对应 `-external-profile.jar` / `.zip`。
- 原始源码快照：`Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.8-sources.zip`。

这些名称区分用途，不表示完成全部玩法验收。代码许可见 [LICENSE](LICENSE)，资源记录见 [资源来源说明](THIRD_PARTY_ASSETS.md)。[公开仓库](https://github.com/mengyuyuan/blacklight-evolution) · [贡献说明](CONTRIBUTING.md)。
