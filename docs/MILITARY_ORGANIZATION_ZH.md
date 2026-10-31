# 军方与黑色守望：编制、军衔与设施

适用：本次 `military.1` 开发增量。Forge 1.20.1与NeoForge 1.21.1分别构建。两平台不能混装；依赖各自匹配的GeckoLib。验证证据见 [阶段记录](verification/MILITARY_ORGANIZATION_20261023_ZH.md)。

## 人员与外观

军方步兵、军方重装兵、黑色守望普通兵和重装兵共用协同行为，保留不同属性与射击节奏。双方未感染时互为友军，不穿过友军身体开火。新增两种军方刷怪蛋；原黑色守望注册ID保持兼容。

军方采用灰绿色、较轻的防护轮廓；黑色守望保留面具双滤罐、胸挂与重装护甲。两方都有枪械、通信/供弹背包、胸口军衔标识和简单持枪、瞄准、射击、换弹姿态。该几何沿用项目原64×64图集，军衔图形是游戏设计。离线 [源码几何与四种设施预览](previews/blackwatch-model-facilities.html) 可切换阵营、姿态和前后视角；它不是实机截图。

八级军衔：列兵、下士、中士、少尉、上尉、少校、上校、将军。军衔不提供伤害/生命加成。军衔与职务分离：任命指挥员不会自动改军衔，改军衔也不会抢走现任指挥职务。正式指挥员被抓、硬直或卸载时，从可行动成员按军衔与UUID稳定选代理；恢复后回任。死亡或永久移除才修复正式任命。

## 编制与行动

一个叶子单位有1–32人及一名正式指挥员；可以欠员，管理指令可调整容量。内部每组最多4人负责交替射击，不额外生成正式队长。自动散兵按阵营、可见邻近范围组成容量8的战术小队。

| 预设ID | 显示名 | 默认人数/组成 |
| --- | --- | --- |
| `tactical_squad` | 战术小队 | 8，可指定4/6/8/12或1–32 |
| `section` | 班 | 8，可指定1–32 |
| `platoon` | 排 | 24，3班 |
| `company` | 连 | 72，3排 |
| `squadron` | 中队 | 24，3战术小队 |
| `battalion` | 大队 | 军方216：3连；黑色守望72：3中队 |

单节点最多8个下级、整棵树最多4层/256人，每维度最多2048单位。编制只组织真实成员，不凭空补齐人数；两阵营不能合为同一个编制树。上述人数、八级军衔和组织映射是本模组的可玩化设定。[原作资料与证据边界](research/PROTOTYPE_MILITARY_REFERENCE_20261023_ZH.md) 区分了官方资料和未确认细节。

命令由最近明确下令的祖先继承，子队可以覆盖，`inherit`恢复继承。`guard`驻守、`patrol`巡逻、`move`行军、`regroup`集结、`return`归驻地。驻守/巡逻/集结可指定新坐标；在新据点建立对应警戒中心。正式上级指挥员所在子队独立留守时，其余继承行军令的子队采用行进代行，保留正式任命与通信节点。长途行军沿指挥关系分散跟随，交战时冻结行军警戒中心，仍有48格追击边界。只使用已加载区域中的原生寻路，不传送、不强制加载远方区块。遇到未加载地形或无路可走时会停止/重试，需要地形和正常区块加载支持。

警报沿已加载、同维度且距离不超过64格的上下级指挥链转发。保留最初观察位置和时间，100tick过期；看不到目标时只能搜索旧位置。接收者会重新判断伪装、友军和目标合法性。超出本单位48格追击范围的警报只触发警戒，不获取实时敌人坐标。每维度每tick最多8次导航服务，单兵间隔至少10tick；邻近发现最多4次/tick，单成员间隔至少20tick。

## 管理指令

以下指令要求权限2；单位可用名称（有空格时加引号）或UUID。`inspect`查看人数、已加载成员、正式/当前指挥员和上级。选择器只选本维度存活、未感染的军事NPC。一次转移、组合或错误操作采用事务，失败不会留下半个编制。`organize`重新组织现有人员，建议先用原生命令给需要的两类士兵打相同tag。

```mcfunction
/blacklight military unit create military tactical_squad alpha 12
/blacklight military unit assign alpha @e[tag=alpha]
/blacklight military rank @e[tag=alpha_leader,limit=1] sergeant
/blacklight military unit commander alpha @e[tag=alpha_leader,limit=1]
/blacklight military order alpha move 120 64 240
/blacklight military inspect alpha
/blacklight military unit resize alpha 16

/blacklight military organize military company @e[tag=company]
/blacklight military organize blackwatch battalion @e[tag=blackwatch_battalion]
/blacklight military unit attach "上级名称" alpha
/blacklight military order alpha guard
/blacklight military order alpha inherit
/blacklight military unit detach alpha
/blacklight military unit unassign alpha @e[tag=alpha]
/blacklight military unit remove alpha
```

`unassign`后该成员停止自动入队，防止下一tick立刻回队；显式`assign`/`organize`恢复。`remove`仅删除已清空成员和下级的节点。首次组织按军衔挑选指挥员，之后可任命；命名/预设/阵营/军衔参数使用英文ID。军衔ID依次为`private/corporal/sergeant/lieutenant/captain/major/colonel/general`。

编制、军衔、任命和命令按维度保存到原生SavedData，实体保留单位UUID与军衔。区块卸载保留名册，死亡/移除/跨维度迁出删除旧维度成员。返回旧存档时，未知军衔回退列兵，无效组织边和重复归属会被忽略修复；不要在两个加载器/游戏版本间直接迁移世界。

## 设施与获取链

| 设施 | 驻军 | 主要内容 |
| --- | --- | --- |
| 黑色守望25×25哨站 | 4普通+1重装 | 营房、床铺、塔台、2科技箱、1补给箱 |
| 黑色守望17×17检查站 | 2普通+1重装 | 道路、岗房、1科技箱、1补给箱 |
| 军方21×21营地 | 7普通+1重装 | 营房、指挥岗、塔台、1科技箱、1补给箱 |
| 军方17×17检查站 | 3普通+1重装 | 道路、两岗房、1科技箱、1补给箱 |

每处新设施的一名普通兵为中士指挥员，重装兵为下士，其余列兵。旋转后按实际位置计算驻军单位，避免固定UUID令不同设施混队。已生成旧设施不自动改建；旧兵会按原兼容方式初始化。

黑色守望原结构ID与32/12间距保留，哨站:检查站权重2:1。军方独立`blacklight:military_outpost`结构，营地:检查站2:1，间距40、分离16，单向避让8区块内黑色守望候选点。候选点避让不等于实际建筑之间的精确距离。都使用原5种陆地群系。可在新区域探索，或使用`/locate structure blacklight:military_outpost`及原生`/place structure`管理指令。

复用原军事电路与扫描仪链：科技箱3–6电路、玩家击杀普通/重装兵分别1/2电路；补给箱提供食物、火把和基础材料。扫描仪配方保持2电路、铁、玻璃与红石，原扫描规则不变。

## 验证与限制

定向规则和实际独立服务端验证涵盖两阵营属性/抓取/硬直/伪装/射击、8/12/32人叶子、24/72层级移动、指令/保存/维度隔离、警报/导航预算和四设施四向放置及真实战利品。216实际实体压力单独启用，不把大队塞进日常验证。

216实体在最终平坦加载场景中1310加速GameTest tick耗时约10.22秒，全部行走超过8格、路径服务峰值8/tick、最短单兵间隔10tick。这不是普通服务器20TPS承诺。客户端材质/动画实机、广域复杂地形自然生成、真人联机与整合包兼容仍需实际游玩验收；车辆、直升机、超级士兵和完整战役不在本增量。

本阶段保留两项小问题：`inspect`未显示军衔；实际实体卸载→重载、换维度往返的生命周期回归仍缺（已验证的是运行时清理与维度数据隔离）。完整审阅与处置见 [审阅记录](verification/MILITARY_REVIEW_20261023_ZH.md)。

复现服务端定向验证时，Forge先用 `python tools/prepare_military_gametest.py --run-directory .superpowers/military-qa-001` 准备新的超平坦目录，再执行 `gradlew runGameTestServer -PblackwatchTest -PgameTestRunDirectory=.superpowers/military-qa-001`。目录已有世界时脚本拒绝覆写，请换新目录。NeoForge使用其原生GameTest世界。压力另加 `-PmilitaryStress`。普通构建不要加这些QA参数。
