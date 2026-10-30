# 服务端平衡配置

NeoForge 1.21.1。本体新增统一的 `blacklight-gameplay-server.toml`，把此前写死的感染、环境出生、成长和建筑繁殖参数接入服务端配置。原有分模块文件继续保留，避免同一开关出现两个互相覆盖的来源。

配置模板见仓库 `config-presets/defaultconfigs/`；这些文件由实际 ModConfigSpec 导出，包含默认值、中文用途说明和合法范围，而不是只有名称、未接入逻辑的示例。

## 放置位置

- 当前单人存档：`<游戏实例>/saves/<世界名>/serverconfig/`。
- 当前专用服务器世界：`<服务器>/<level-name>/serverconfig/`，通常是 `world/serverconfig/`。
- 新世界默认模板：`<游戏实例或服务端>/defaultconfigs/`。已有世界不会被该目录覆盖；修改已有世界请修改其 `serverconfig/`。

先备份对应文件。停止游戏或服务器后编辑 TOML，再启动，最便于确认整套参数一致生效。配置文件首次启动自动生成；数值越界会由 NeoForge 校正。多人游戏以服务器为准，客户端接收服务器配置，玩家本机的同名文件不能决定服务器刷怪或伤害。

不要把一整套默认模板直接覆盖自己的定制配置。只需要改变一个参数时，修改该项即可；概率、名额、地形条件、养分和世界阶段会共同影响最终结果。

## 文件分工

| 文件 | 可调整内容 |
| --- | --- |
| `blacklight-gameplay-server.toml` | 接触/摔瓶感染概率与半径、世界天数加速、成长和器官整合倍率、养分需求与修复、红黄绿区出生率、阶段日期与各类出生权重、出生距离、建筑核心/孵化/供能 |
| `blacklight-player-evolution-server.toml` | 无限进化单次价格、两阶被动门槛、六种无限属性的每级收益、脱战恢复等待 |
| `blacklight-outbreak-server.toml` | 世界感染总开关、一天长度、刷怪周期、维度及局部上限、共享出生批次、普通补给预留、吞噬普通感染者的回血 |
| `blacklight-evolution-server.toml` | 同类竞争、器官继承、搜索预算、融合系数、强大器官/吞噬/分数门槛、濒死阈值、吞噬回血与短暂保护 |
| `blacklight-building-infection-server.toml` | 建筑感染、体积组织、建筑吞噬、自主生长与繁殖开关、工作预算、菌落数量、半径、高度、成熟时间和附近生物上限 |
| `blacklight-city-server.toml` | 城市人口、扩散、总人口、环境人口周期、城市母巢繁殖间隔与九头蛇概率、扩散周期、地图缓存与后台预生成、抵达等待时间 |
| `blacklight-evolved-server.toml` | 进化者的生命/伤害倍率、护甲、韧性、速度、击退抵抗和对玩家单击上限 |
| `blacklight-combat-server.toml` | 外部模组伤害输入倍率、单击/时间窗保护、黑光对外输出和真伤开关 |

## 一级 50、二级 150

`blacklight-player-evolution-server.toml`：

```toml
passiveTierOneAttributeRank = 50
passiveTierTwoAttributeRank = 150
repeatableAttributeCost = 200
keepLegacyPassivesActive = false
```

先完成有限基础项目，再让生命、普攻、技能、防御、恢复、机动六项无限属性**分别达标**，不是六项相加。一阶同时开放适应性 I 与组织再生 I；二阶同时开放适应性 II 与组织再生 II，仍需购买相应一阶技能。

旧版 `passiveAttributeRank` / `percentAdaptationAttributeRank` 两个 20/50 键不再使用，加载时生成新的 50/150 键；旧配置自定义值请迁入新键。旧 20/50 里程碑保留为历史，但不能直接冒充新资格。学习记录与现有层数保留，未达新资格时暂停，达标后自动恢复。取得新版资格后继续保存，不因后来新增城市或改动基础清单而回锁。`keepLegacyPassivesActive=true` 是服主明确选择的门槛例外，默认关闭。

本次不改适应性/组织再生的文档公式，不添加自然衰减。独立药水仍采用其独立解锁方式。

## 常用调整

以下是 `blacklight-gameplay-server.toml` 的局部示例，修改已有段落内的同名键即可，不要重复创建同名 TOML 段落。

```toml
[infection]
contactChance = 0.08
contactCooldownTicks = 200
vialRadius = 16.0
vialDirectHitChance = 1.0
vialCenterChance = 1.0
vialEdgeChance = 0.5

[evolution]
growthSpeedMultiplier = 1.0
worldAgeAcceleration = 1.0
worldAgeReferenceDays = 7.0
organIntegrationMultiplier = 1.0

[outbreak]
greenSpawnChancePercent = 12
yellowSpawnChancePercent = 55
redSpawnChancePercent = 100
stage1Day = 1
stage2Day = 3
stage3Day = 7
stage4Day = 14
```

- `infection` 概率使用 0～1；`outbreak` 区域概率使用 0～100。0 与 1／100 的含义见各字段注释。
- 刷怪概率针对每轮候选位置，不是每个方块或每秒的必然出生量。和平难度、`doMobSpawning`、已加载区块、碰撞、保护区和人数共享预算仍然有效。
- 环境感染概率 = 基础概率 + 阶段 × 每阶段增量，最高 1；还要通过所在区域的概率检查。接触传播有独立的失败冷却，不能靠同一刻围攻无限重掷。
- `growthSpeedMultiplier=2` 将自然生长、食物加速和器官整合整体加速；0 暂停这些成长推进，保存重载不会通过未结算时钟偷长。修复和消化食物不是离线年龄追赶。
- 小于一倍时按小数累计生长与整合进度，并保存不足一刻的余量；例如 `0.01` 不会被每秒至少增长一刻的取整规则放大，重载也不会丢掉已累计的余量。
- 世界天数速度为 `总倍率 × (1 + 加速强度 × sqrt(世界天数/基准天数))`；`worldAgeAcceleration=0` 关闭随世界日期加速。此处世界日照天数和感染阶段的释放后计时是两套时钟。
- 生长阶段与外形按已有生物年龄演算；改变速度影响未来推进，不把老感染体年龄归零。喂养需求仍渐进支付，不按最大养分储备一次扣费。
- 各 `stageNWeights` 的 `common/growing/mature/hunter/ancient` 是相对权重，允许不加到 100；支持很小的非零比例，0 对应不参加抽签。全部为 0 时安全回退普通感染者。
- 默认 `lineageGuarantees=true` 仍会补足普通补给和成长谱系名额，优先于抽签。若要严格由权重控制类型，设为 false；建筑的定期猎手优先项另由 `buildings.hunterEveryBirths` 控制，0 关闭。
- `broodChance=0` 关闭建筑及城市母巢的自然繁殖。失败抽签也用掉本轮时间，避免到期后频繁重掷把低概率变成必定。城市环境人口同样遵守区域出生率；普通建筑和原有城市母巢各自有繁殖等待参数。
- 阶段日期、最大/最小出生距离等关联参数有运行时顺序保护；文件注释说明实际采用的值。不会因为提高距离或次数就强制加载远处区块。

## 参数边界

可调项覆盖主要玩法平衡、数量、概率和进程，不将渲染顶点、骨骼绑定、物品唯一性、事务重入保护和动画关键帧变成可破坏存档的配置。器官吞噬仍至少要六个成熟器官；领地取消、真实死亡结算与服务器权威检查继续有效。

配置允许大幅改变难度，范围上限只是输入保护，不能保证所有极端组合都适合当前电脑。工作预算、出生批次、局部数量和维度数量可分别调整，无需修改模型或重新编译模组。


## 城市感染体密度与分组名额

新默认密度、`cityMobLimit` 的分组含义及旧存档配置迁移，见 [感染体密度与城市名额](OUTBREAK_POPULATION_ZH.md)。非感染体不再占用整份感染体预算；两者合计仍有硬上限，生成事件中的预留名额也参与统计。
