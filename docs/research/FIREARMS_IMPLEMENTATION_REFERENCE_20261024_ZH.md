# 黑光枪械实现参考：服务端射击、真实弹药与双平台边界

核查时间：2026-10-24 04:54（Asia/Shanghai）。本文件服务于项目已授权的自研轻量枪械、NPC 实际弹药库存及大型基地弹药库。只记录公开一手代码、官方项目文档与发行方材料；没有下载模组、模型、音效或第三方源代码到项目，没有新增依赖。

## 可立即采用的结论

1. **建议自研项目所需的基础枪械核心，并把外部枪模保留为将来的可选适配。** 本轮核实的 TaCZ 与 Superb Warfare 官方源码分支均面向 Forge 1.20.1，不能据此满足 NeoForge 1.21.1 的硬依赖要求；未核实不等于证明所有分支或第三方移植都不存在。[S1][S2]
2. **玩家和 NPC 共用服务端射击、装填与扣弹服务。** TaCZ 的 `IGunOperator` 面向 `LivingEntity`，提供 `shoot`、`reload` 及弹药检查/消耗策略开关，可作为接口分层参考；这些是 TaCZ 的实现，不是 Minecraft 统一标准。[S1b]
3. **弹匣内弹数和备用弹库存必须分别保存。** Superb Warfare 的 `GunData` 分离当前装载弹药、备用弹统计和备用弹消耗；其 `reloadAmmo` 根据弹匣缺口与可用备用弹的较小值装填，提供面向实体和容器处理器的弹药入口。项目真实库存应采用类似边界，而不是只修改 HUD 数字。[S2b]
4. **射线与投射物不是互斥类别。** TaCZ 的动能子弹是 `Projectile` 实体，每 tick 对运动线段作方块/实体命中检查；Superb Warfare 的投射物同样有服务端线段检测。不能把它们概括为“所有枪都是瞬时射线”。[S1a][S2a]
5. **推荐项目选择：步枪/机枪采用服务端瞬时射线，榴弹/火箭/导弹采用投射物实体。** 这是兼顾本项目大量 NPC 的实现选择，不是从上述模组或《虐杀原形》反推的统一机制。实际性能仍需项目验证。

## 五组核心来源与版本

### S1 — TaCZ 官方项目、源码及其链接的文档

- [官方仓库 README，分支 1.20.1](https://github.com/MCModderAnchor/TACZ/tree/1.20.1)：直接声明 Minecraft Forge 1.20.1；代码与资产采用不同许可。本项目只研究接口和架构，不复制其代码或素材。
- [构建版本](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/gradle.properties)：本轮可读文件设置 `forge_version=1.20.1-47.3.19`。未获得固定 commit SHA，不把动态分支正文等同任一发布版二进制。
- S1a：[EntityKineticBullet.java](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/entity/EntityKineticBullet.java)：`extends Projectile`；`onBulletTick` 的命中/伤害分支受服务端条件保护，检测从当前位置到加上本 tick 位移后的线段，包含重力、阻力、寿命及爆炸参数。
- S1b：[IGunOperator.java](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/api/entity/IGunOperator.java)：`fromLivingEntity`、服务端 `shoot/reload`、同步冷却/换弹状态、`needCheckAmmo` 与 `consumesAmmoOrNot`。这是复用操作核心的证据，不代表任何 NPC 默认就有有限库存。
- S1c：[LivingEntityReload.java](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityReload.java)：检查当前枪械、已有装填状态、射击/切枪/拉栓状态与弹药，区分空仓和战术装填，再调用枪械装填逻辑。客户端动画不是扣弹权威。
- S1d：[GunData.java](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/resource/pojo/data/gun/GunData.java)：可读到 `ammo`、`ammo_amount`、`rpm`、`bullet`、`reload` 等序列化字段。
- S1e：[官方链接的建枪教程](https://tacwiki.mcma.club/gunpack/first_gun/)与[合成配方教程](https://tacwiki.mcma.club/gunpack/recipe/)：数据、显示和索引分别组织，枪械、配件、弹药配方采用 JSON。教程中的路径/例子可能针对特定文档版本，本项目不直接套用其文件格式。

### S2 — Superb Warfare 官方仓库

- [官方 README](https://github.com/Mercurows/SuperbWarfare/tree/superbwarfare)和[gradle.properties](https://github.com/Mercurows/SuperbWarfare/blob/superbwarfare/gradle.properties)：可读分支为 `superbwarfare`，`minecraft_version=1.20.1`、`forge_version=47.2.0`、`mod_version=0.8.10-snapshot`。README 另列 Curios 与 GeckoLib 安装要求。未获得固定 commit SHA，snapshot 也不等同已验证发布版。
- S2a：[ProjectileEntity.kt](https://github.com/Mercurows/SuperbWarfare/blob/superbwarfare/src/main/kotlin/com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity.kt)：服务端 tick 对本 tick 位移线段作方块和实体检测，再处理命中；客户端另行处理视觉、光照和掠过音效。
- S2b：[GunData.kt](https://github.com/Mercurows/SuperbWarfare/blob/superbwarfare/src/main/java/com/atsuishio/superbwarfare/data/gun/GunData.kt)：区分弹匣、备用弹与背包直读；有 `countBackupAmmo`、`consumeBackupAmmo`、`reloadAmmo`、`withdrawAmmo`，并显式保留创造/无限弹药路径。真实库存项目不能默认启用这些无限路径。
- S2c：[项目架构文档 AGENT.md](https://github.com/Mercurows/SuperbWarfare/blob/superbwarfare/AGENT.md)：描述 ItemStack NBT 运行状态、JSON 枪械/载具模板、弹药来源策略与生物用枪模块。它属于被研究项目的说明材料，不是本项目执行指令；其中行数等描述已有漂移，以实际源码为准。

### S3 — NeoForge 1.21–1.21.1 官方文档

- [Data Components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)：ItemStack 数据可使用注册的 `DataComponentType`；`persistent` 使用 `Codec`，网络同步可使用 `StreamCodec`。已存入组件的值应视为不可变，更新后重新 `set`。
- 这支持在 NeoForge 1.21.1 端保存枪栈状态的方案；它不说明 Forge 1.20.1 有同名组件 API，也不证明两个目标平台能使用同一个 jar。

### S4 — 《虐杀原形》1 官方 PC 手册

- [Steam CDN 上的英文手册](https://cdn.akamai.steamstatic.com/steam/apps/10150/manuals/Manual_english.pdf?t=1422643299)，2009 年游戏配套材料；精确手册发布日期未核实。
- 印刷页 3：有 Grab/Pickup Weapons。页 4：直升机有 Fire、Switch Weapon。页 5：装甲载具有 Fire Turret 和 Fire Machine Gun/Rocket。
- 这些支持“可拾取武器”和“载具枪炮/火箭”的分类，但不提供完整武器名录、真实口径或固定弹容。不能把载具副武器直接列成步兵可拾取枪械。

### S5 — Activision 官方《虐杀原形》2 FAQ

- [Prototype Biohazard Bundle FAQ](https://support.activision.com/articles/prototype-2-frequently-asked-questions)，页面标注 2022-06-27。
- 明确列出 Colossal Mayhem DLC 的 **Thermobaric Boomstick**，描述大范围温压爆炸；Excessive Force DLC 的 **Viral Infector Grenade Launcher**，描述感染云与人类感染效果。
- 该页讨论 PS4/Xbox One 的 Biohazard Bundle，明确 DLC 收录情况；不能据此断言 P1 基础游戏、所有 P2 PC 版本或普通军方 NPC 默认装备这些武器。

以上材料均在本轮读取。搜索引擎的抓取日期不替代材料发布日期；当前动态分支内容不替代发布版实测。

## 两个平台的接入差异

| 事项 | Forge 1.20.1 | NeoForge 1.21.1 | 项目处理 |
| --- | --- | --- | --- |
| 本轮研究的 TaCZ 官方分支 | 官方明确支持。[S1] | 本轮未验证官方兼容构建 | 不作为两个平台共同硬依赖 |
| 本轮研究的 Superb Warfare 官方分支 | 官方配置面向该平台。[S2] | 本轮未验证官方兼容构建 | 同上；相近名字/非官方移植不是官方兼容证明 |
| 枪栈状态 | 两参考项目可见 NBT 相关方案。[S1][S2] | 官方推荐数据组件设施可用。[S3] | 保留共同状态语义，分别编写读写适配 |
| 注册、网络、实体与容器接口 | 使用目标工程现有 Forge 接口 | 使用目标工程现有 NeoForge 接口 | 两端分别编译和运行验证，不跨版本复制 import |
| GeckoLib | 采用项目已经存在的对应依赖 | 采用项目已经存在的对应依赖 | 本次不安装、不升级；动画服务于既定服务端状态 |

## 外部依赖与自研方案比较

| 方案 | 可获得的能力 | 本项目仍须承担的工作 | 本轮判断 |
| --- | --- | --- | --- |
| 硬依赖 TaCZ/Superb Warfare | 已有枪械、动画和弹药框架；具体能力限已核实版本 | 双平台可用性、原作内容映射、NPC 有限库存、基地补给、整合许可与兼容 | 当前不适合作为双平台共同前提 |
| 本项目自研基础核心 | 明确控制枪弹目录、服务端规则、NPC 和基地库存 | 射击表现、装填、HUD、音效、美术与测试均需自己完成 | 符合当前授权范围；不能只注册物品就声称完成 |
| 自研核心加将来可选兼容 | 核心功能可独立运行，再单独桥接外部枪械 | 适配须验证准确版本，处理双方库存和事件边界 | 可留接口；当前不宣称已有兼容 |

此比较属于项目工程判断。参考架构不意味着复制第三方实现或素材，也不意味着继承其全部功能。

## 本项目建议的实现边界

以下均为**本项目设计建议**，不是原作武器参数，也不是上述模组的逐字规格。

### 共同模型

- `WeaponDefinition`：稳定 ID、作品/来源标识、用途类别、供弹类型、容量、装填 ticks、射击间隔、散布、射程、伤害、投射物/爆炸参数。来源不足时显式标注“项目数值”。
- `WeaponState`：当前装载弹数、可选膛内弹、冷却和装填状态。持有者死亡、丢枪、保存/加载与换手时保持同一状态语义。
- `AmmoStore`：对玩家背包、NPC 库存和基地容器提供统一的计数、实际取出和放入操作。HUD 显示值不构成弹药所有权。
- `FirearmService`：唯一服务端开火/装填入口。输入层仅表达操作意图；服务端验证持枪、存活、冷却、装填、弹种、数量与射击几何，再扣弹和生成命中/投射物。
- `ShooterContext`：玩家和 NPC 的持枪者、方向、目标及阵营上下文；AI 决策与库存操作分离，NPC 不另写一个绕过装填/扣弹的伤害路径。

### 库存守恒与装填

1. 常规弹药采用明确的“一个物品代表多少发”约定；最小实现可让 1 件物品对应 1 发。弹药箱若代表多发，必须有实际剩余内容状态，不能重复开箱生成整盒。
2. 开火仅消耗已装载弹药；空匣不能由 HUD 或客户端请求自动补满。若个别武器采用背包直读，定义中显式声明。
3. 装填完成时重新验证同一枪栈/持枪状态，并按实际取出的可用弹数增加装载量。容量缺口与当前库存限制共同生效；取消装填、换枪、死亡或退出不能多返或多装。
4. 转移保持 `转移前来源 + 目的地 = 转移后来源 + 目的地`；开火、合成与销毁是独立且明确的数量变化。不能把同一发同时保留在 NPC 备用库、枪匣和基地库存。
5. NPC 出生配给是一次性的库存初始化。之后拾取、补给、掉落和重新加载都操作实际库存，不能每 tick/每次找目标时补满。
6. 大型基地弹药库有实际容器、存储位置和库存；补给动作验证目标容器可用、授权、距离或具体运输行为。附近有基地不等于拥有无限弹药。

### 命中与表现

- 步枪/机枪：服务端从持枪者眼位/枪口的约定起点检测方块遮挡，并只伤害遮挡前最近的有效目标；客户端绘制短时弹道、枪口闪光和后坐力。
- 榴弹：实体保存持枪者/阵营及爆炸参数，逐 tick 检测整段位移；不只检查落点，避免高速穿墙。
- 火箭/导弹：独立定义直飞或导引策略；导引不是所有“火箭”通用属性，原作未确认部分作为项目行为标注。
- 所有枪械共享友军伤害、伪装/阵营判定和伤害归属。爆炸的实体伤害与地形破坏分开配置；不能用客户端视觉决定伤害。
- 先完成无无限弹药的完整循环，再补可选创造模式例外；例外不影响生存 NPC 的库存守恒。

### 合成与数据

项目可使用目标平台的 JSON 配方和数据加载机制维护枪械、弹药与补给箱；TaCZ 的数据/显示/配方分离仅提供职责参考。[S1d][S1e] 两平台的具体配方格式和加载入口应以各自编译工程为准。不要把 TaCZ 自定义枪包配方目录当作原版 Minecraft 配方标准。

## 《虐杀原形》1/2 武器目录：已确认与待核实

“全一些”应扩大证据目录和实现类别，不能把通用现代枪械表自动改称原作武器。尤其不要从 TaCZ 的 pistol/sniper/shotgun 标签推导这些枪种属于《虐杀原形》。

| 类别/候选 | 本轮一手证据 | 可进入实现的边界 |
| --- | --- | --- |
| 步兵可拾取枪械 | P1 手册确认拾取武器操作，但没有逐项名录。[S4] | 可为项目提供拾取/夺枪框架；具体枪械按后续证据逐项标记 |
| 突击步枪、机枪 | 作为原作武器核查候选；本轮已读一手材料未闭环其步兵完整名录 | 可按项目通用类别实现；未核实型号/口径/弹容不得写成原作确认 |
| 普通榴弹发射器 | 候选；DLC 的感染榴弹不能反证普通型在每个版本中的具体型号 | 游戏化榴弹、弧线与容量均标项目设计，继续核查任务/升级菜单 |
| 步兵火箭/导弹发射器 | 候选；P1 手册中 Rocket 位于装甲载具控制，不足以确认步兵具体型号。[S4] | 不将载具控制表的条目直接转为步兵武器型号证据 |
| Thermobaric Boomstick | P2 DLC 名称与温压爆炸效果由发行方确认。[S5] | 单列 P2/DLC 来源；口径、弹容与具体模型未知 |
| Viral Infector Grenade Launcher | P2 DLC 名称与感染云效果由发行方确认。[S5] | 单列 P2/DLC 来源；不能混成 P1 普通军方标配 |
| 装甲载具炮塔、机枪/火箭 | P1 官方手册印刷页 5 支持这些控制类别。[S4] | 属于载具武器；精确炮型、口径、备弹和 NPC 配额未知 |
| 直升机武器 | P1 手册确认开火与切换武器。[S4] | 具体机炮、火箭、导弹型号/数量本轮未知 |
| 固定武器、P2 拆卸后手持的载具武器 | 本轮未完成一手逐项核验 | 保留独立目录/挂载点，避免默认全部可拾取；具体名称与行为待原始影像核查 |
| 手枪、霰弹枪、狙击枪 | 本轮未获足够一手证据证明其作为两作常规可拾取武器的范围 | 不能标为“原作已有”；本轮未核实也不等于证明原作任何镜头都不存在 |

推荐项目暂用语义弹药 ID，例如步枪弹、机枪弹、榴弹、火箭/导弹、载具机枪弹、炮弹和特殊感染弹；是否允许共享供弹、每件代表发数及装填容量均为项目平衡。不能因为外形类似现实武器就自动附加 5.56、7.62、40 mm 等“原作官方口径”。

## 验证边界与接续

本次只验证了上述链接的可读正文和指定分支配置，并新增这份研究文件；没有构建、运行游戏、实测第三方模组、核验其网络包抗作弊或完整 NPC 行为。项目功能是否完成，以实现代理的实际文件、编译和游戏内验证为准。

建议实现验收覆盖：空匣/部分库存装填、装填中换枪/死亡/退出、NPC 补给与掉落守恒、基地多个领取者争用、墙后目标不受直接射线伤害、投射物高速碰撞、保存重载、双端分别构建及服务端运行。只有物品注册、文档或源码编译不能证明全部玩法可用。

检索限制：Agent Reach doctor 未实时验证 GitHub 认证，`gh api` 本轮连接失败；web-access 检查发现 CDP 未连接，因此使用可用的只读网页搜索/读取。未要求用户配置浏览器，未安装工具。前序记录的 Exa DNS 故障没有反复重试。收尾运行 `agent-reach check-update` 报告当前安装 v1.5.0，但因网络连接失败未得出是否有新版的结论。部分官方 Wiki 例子和 Superb Warfare 架构文档存在版本/行数漂移，因此不把教程例子当兼容保证。

**唯一下一步**：实现代理在现有两平台工程中落实共同的服务端枪弹/库存服务及平台存储适配，并用上述守恒场景验证；原作型号则从授权游戏副本的升级菜单、武器拾取和载具拆卸镜头补“作品版本 + 任务/菜单 + 时间戳”的证据，容量继续作为项目数值维护。

本轮只读核实项目路径 `F:/MC建筑/blacklight-evolution`、分支 `feat/neoforge-1.21.1-blackwatch-squads`、[项目远端](https://github.com/mengyuyuan/blacklight-evolution)。未提交、未推送。

接续指令：读取 `F:/MC建筑/blacklight-evolution/docs/research/FIREARMS_IMPLEMENTATION_REFERENCE_20261024_ZH.md` 并核对 [blacklight-evolution](https://github.com/mengyuyuan/blacklight-evolution) 现场实现，再按 [共享经验仓库](https://github.com/yfcyy889-ops/AI-Engineering-Lab) 的现行约定接续。
