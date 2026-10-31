# M6.1：载具、车队与车库玩家指南

当前玩法是**放置完整车辆、无需燃油驾驶、实物补弹与维修、原生闲兵搬运后勤**。本文提供手工操作与预期；本轮完整测试、GL画面复核、发行JAR与交付结果集中在[主记录末节](verification/MILITARY6_VEHICLES_20261026_ZH.md)及包外回执，由root按最终实际结果填写。旧M6.0验收只对应旧交付。全部兵种、建筑与模型资料继续从[制作资料入口](制作资料/M6_载具与车库.md)查阅；当前玩法以本指南为准。

## 1. 安装

NeoForge对应Minecraft 1.21.1 / Java 21，Forge对应Minecraft 1.20.1 / Java 17。按主记录与交付回执选择对应加载器的完整主JAR，同一实例只启用一份黑光主JAR，保留已有的匹配GeckoLib。源码、文档、bbmodel与交付ZIP不放进mods；载具不需要另装“卓越前线”等车辆模组。

## 2. 新世界与测试平台

新建**创造、允许作弊、普通难度的超平坦测试世界**，下面各行单独输入聊天栏，多人需管理员权限。放置建筑、铺地与拆核心会实际改方块。新车体更大，旧停放范围或道路可能变窄，建议先用新测试世界。

先铺Y80单层平台，车辆站立高度Y81；中央X=-8至8留给后面的AI车队，玩家放车选平台侧边：

```mcfunction
/gamemode creative
/difficulty normal
/tp @s 0 82 -12
/fill -30 80 -36 50 80 70 minecraft:stone
```

平台为81×107。提示未加载时先飞近相关范围再执行。第6节基地另放在X=200附近，与这里分开；全流程直接用聊天命令与游戏交互。

## 3. 放置完整车，零油直接开

```mcfunction
/give @s blacklight:military_truck_chassis
/give @s blacklight:military_apc_chassis
/give @s blacklight:military_tank_chassis
/give @s blacklight:blackwatch_truck_chassis
/give @s blacklight:blackwatch_apc_chassis
/give @s blacklight:blackwatch_tank_chassis
```

物品显示名是军方/黑色守望运输卡车、装甲战斗车、主战坦克；`_chassis`注册ID继续兼容，但放下就是完整车辆。手持车辆物品，对实心平地右键，朝向随玩家；长车身与炮管都要有空间。初试车间留至少12格中心距，炮塔转动另留余地；拒绝放置时换宽阔位置。

| 车型（两阵营相同） | 车身长×宽×高，格 | 满耐久 | 固定武器 |
| --- | --- | ---: | --- |
| truck 卡车 | 7.5×2.9×3.7 | 100HP | 无 |
| apc 装甲战斗车 | 7×3.3×3.3 | 240HP | 自动炮、火箭 |
| tank 主战坦克 | 8×4×3.4 | 420HP | HE/AP主炮、同轴机枪 |

炮管伸出长度另计。玩家放置及普通命令新车均为**满HP、空乘员、初始零弹、零油**；零油可直接驾驶，创造也不会免费补弹。原生车库首次派发的弹药另见第6节。

普通空手右键上车，按顺序进入空席，空车通常先坐司机。默认W/S前进/倒车、A/D转向、空格刹车、Shift下车，读取当前Minecraft移动/跳跃/潜行绑定。先验证零油前进、倒车、转向与停车，再检查F5。主副武器读取当前攻击/使用绑定，默认左键/右键。

打开“选项→控制→按键绑定”，找到“黑光：地面载具”，为**切换载具座位**、**切换主炮弹种**各绑定一个没有冲突的键。两项初始没有默认键，不能假定按某个固定字母就会切换；换座只能进入空席，不能挤掉已有乘员。坦克武器操作员可在HE/AP之间切换，两种弹共享装填冷却；卡车没有固定武器。

司机在炮手席空时可操作武器；炮手占席后司机继续驾驶，火力归炮手。普通乘客、卡车军官席、坦克指挥席没有驾驶/火力权限。乘车输入会让原型体抓取与徒手技能让位；开菜单、下车、死亡或掉线后旧输入应停止。

坦克/APC第一人称用与模型同源的观察口/潜望镜挂点，观察位置与真实座位脚点分开；F5保留完整车体与原生坐姿。原版皮肤及原生Mercer乘车外观按实际玩家身高呈现；下车后的Mercer外观沿用原设置。任意第三方YSM骨架、替换渲染器与自定义身高仍未验证。HUD显示耐久、座位、主副武器及坦克另一弹种余量、补给提示，取消油量；缩放与长文本、F5取景以本轮最终GL复核为准。

在宽路、对正的7格车库门、1格台阶、窄门、水边与坑沿慢试。人走过横向炮管下的空地或斜车包围盒空角时，应按实际车身/炮管接触；整车行驶仍用保守地形净空检查，不能据此保证擦墙、斜门或炮管越障通行。

## 4. 弹药配方、对应弹药与维修

**燃油已停用**：旧 `blacklight:military_fuel_can` 的注册、代码、模型、贴图、磁盘配方和存档数值保留；正常创造物品栏、游戏配方、战利品及新车库补给不提供燃油。旧罐对车右键无效，玩家实物与箱中旧罐都不被后勤取走，行驶不消费旧 `FuelTicks`。

下表是当前工作台无序配方；无需JEI，已有JEI时可查对应物品。卡车不收任何弹药，两阵营同车型共用弹种。

| 实物ID | 对应车载武器／单车上限 | 一次合成原料 → 产量 |
| --- | --- | --- |
| `blacklight:autocannon_round` | APC主自动炮，120发 | 铁锭×2、铜锭×1、火药×1 → 8发 |
| `blacklight:rocket_round` | APC副火箭，4发 | 铁锭×2、火药×1、纸×1 → 2发 |
| `blacklight:tank_he_shell` | 坦克主炮HE，12发 | 铁锭×2、铜锭×1、火药×2 → 1发 |
| `blacklight:tank_ap_shell` | 坦克主炮AP，8发 | 铁锭×3、铜锭×1、火药×1 → 1发 |
| `blacklight:machine_gun_round` | 坦克副同轴机枪，240发 | 铁锭×1、铜锭×1、火药×1 → 16发 |
| `blacklight:vehicle_repair_parts` | 三车型维修件 | 铁锭×2、红石×1 → 1件 |

火箭弹、机枪弹与步枪弹、榴弹、导弹各用不同原料组合，工作台无序摆放即可；创造测试可用下面的`give`领取明确弹种。

创造测试先领实物，**停稳、下车、主手拿对应物品，近距离右键车体**：

```mcfunction
/give @s blacklight:autocannon_round 64
/give @s blacklight:rocket_round 4
/give @s blacklight:tank_he_shell 12
/give @s blacklight:tank_ap_shell 8
/give @s blacklight:machine_gun_round 64
/give @s blacklight:vehicle_repair_parts 8
```

补弹按实际接受量扣手中物品，满额、错车型/错弹、移动中或超距拒收；**创造也扣实物**。维修每次成功扣1件、最多恢复20HP且不超过上限，缺口不足20HP也用1件；满血或已毁拒收，不复活车辆。玩家手动维修不要求车库。

补弹后上车，在远离友军与建筑的方向试射：APC主/副分别扣自动炮弹/火箭，坦克主炮扣所选HE或AP、副武器扣机枪弹。HE/AP共享主炮冷却；**装填冷却结束只允许下一发，不会从箱取弹或增加库存**。空弹、冷却或炮口/射线不安全时不开火。

靠近唯一一辆玩家军方APC（8格内无其他同类），加标签并记录补给/开火前后：

```mcfunction
/tag @e[type=blacklight:military_apc,distance=..8,sort=nearest,limit=1] add m6_player_apc
/data get entity @e[type=blacklight:military_apc,tag=m6_player_apc,limit=1] UUID
/data get entity @e[type=blacklight:military_apc,tag=m6_player_apc,limit=1] VehicleHealth
/data get entity @e[type=blacklight:military_apc,tag=m6_player_apc,limit=1] VehicleInventory
```

保留同车UUID、HP、各弹种、`CooldownUntil`，旧 `FuelTicks` 仅作兼容数据。实际开火与维修后查同车，库存只按消耗/实物补给变化。

## 5. 单AI与三车命令

玩家放车有玩家归属，`fleet crew` 拒绝接管。下面另召唤无玩家归属、满HP零弹的新车；它们没有原生基地/驻地关联，不能替代第6节原生后勤测试。各组标签只执行一次，保持本组命令乘员与车辆已加载。

### 单辆APC

先在中央空地逐行召唤；军人显式身份用于命令乘员，不是无NBT新生装备测试：

```mcfunction
/summon blacklight:military_apc 0.5 81 0.5 {Tags:["m6_car"]}
/summon blacklight:military_soldier -3 81 0.5 {Tags:["m6_driver"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"trooper",MilitaryRank:"private"}
/summon blacklight:military_officer 3 81 0.5 {Tags:["m6_officer"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"officer",MilitaryRank:"lieutenant"}
```

先按第4节持实物给这辆停稳APC补弹，不加油，自己保持下车。两次分开让普通兵先坐司机、军官后坐炮手；均须活着、同阵营、未感染、可用AI、未乘别车且距车≤8格：

```mcfunction
/blacklight military fleet crew @e[type=blacklight:military_apc,tag=m6_car,limit=1] @e[type=blacklight:military_soldier,tag=m6_driver]
/blacklight military fleet crew @e[type=blacklight:military_apc,tag=m6_car,limit=1] @e[type=blacklight:military_officer,tag=m6_officer]
/blacklight military fleet create @e[type=blacklight:military_apc,tag=m6_car,limit=1] @e[type=blacklight:military_officer,tag=m6_officer,limit=1]
/blacklight military fleet list
```

`create` 输出实际车队UUID。下面的 **`fleet_UUID` 必须整段替换为本次车队UUID**，不是车辆/军官UUID；不要连占位词照抄。先移动到侧边，等实际到位再下`guard`，让中央留给三车：

```mcfunction
/blacklight military fleet order fleet_UUID move 20 81 35
/blacklight military fleet inspect fleet_UUID
```

到位后停车；其他任务按需一次试一项，每次等实际动作后再换：

```mcfunction
/blacklight military fleet order fleet_UUID guard
/blacklight military fleet order fleet_UUID patrol 20 81 55
/blacklight military fleet order fleet_UUID clear_area 20 81 55
/blacklight military fleet order fleet_UUID return
```

`move`需坐标，`guard`停车，普通命令车的`return`回车队出发点。上面单车只有司机/炮手，没有可下车部署的步兵。试三车前，把单车停回侧边`20 81 35`并`guard`。

### 三车：APC、卡车、坦克

三车起点Z依次为0.5、-11.5、-23.5，**坦克在卡车后12格**。不要套用旧尺寸车距。先召唤车辆与六名真实乘员，再手动补满APC/坦克对应弹药；卡车无武器：

```mcfunction
/summon blacklight:military_apc 0.5 81 0.5 {Tags:["m61_three","m61_apc"]}
/summon blacklight:military_truck 0.5 81 -11.5 {Tags:["m61_three","m61_truck"]}
/summon blacklight:military_tank 0.5 81 -23.5 {Tags:["m61_three","m61_tank"]}
/summon blacklight:military_soldier -3 81 0.5 {Tags:["m61_da"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"trooper",MilitaryRank:"private"}
/summon blacklight:military_officer 3 81 0.5 {Tags:["m61_oa"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"officer",MilitaryRank:"lieutenant"}
/summon blacklight:military_soldier -4 81 -2 {Tags:["m61_pa"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"trooper",MilitaryRank:"private"}
/summon blacklight:military_soldier -3 81 -11.5 {Tags:["m61_dt"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"trooper",MilitaryRank:"private"}
/summon blacklight:military_soldier -3 81 -23.5 {Tags:["m61_dk"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"trooper",MilitaryRank:"private"}
/summon blacklight:military_soldier 3 81 -23.5 {Tags:["m61_gk"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"trooper",MilitaryRank:"private"}
/blacklight military fleet crew @e[type=blacklight:military_apc,tag=m61_apc,limit=1] @e[type=blacklight:military_soldier,tag=m61_da]
/blacklight military fleet crew @e[type=blacklight:military_apc,tag=m61_apc,limit=1] @e[type=blacklight:military_officer,tag=m61_oa]
/blacklight military fleet crew @e[type=blacklight:military_apc,tag=m61_apc,limit=1] @e[type=blacklight:military_soldier,tag=m61_pa]
/blacklight military fleet crew @e[type=blacklight:military_truck,tag=m61_truck,limit=1] @e[type=blacklight:military_soldier,tag=m61_dt]
/blacklight military fleet crew @e[type=blacklight:military_tank,tag=m61_tank,limit=1] @e[type=blacklight:military_soldier,tag=m61_dk]
/blacklight military fleet crew @e[type=blacklight:military_tank,tag=m61_tank,limit=1] @e[type=blacklight:military_soldier,tag=m61_gk]
/blacklight military fleet create @e[tag=m61_three] @e[type=blacklight:military_officer,tag=m61_oa,limit=1]
```

将`fleet_UUID`换成**这次三车create的新输出**，先move并等停靠，再按需试patrol、clear_area、guard、return，不能连续瞬间发完：

```mcfunction
/blacklight military fleet order fleet_UUID move 0 81 45
/blacklight military fleet inspect fleet_UUID
/blacklight military fleet order fleet_UUID clear_area 0 81 45
/summon blacklight:infected_host 0 81 65 {Tags:["m61_enemy"],PersistenceRequired:1b}
```

自己保持创造并离开射线与爆炸邻域。`clear_area`接敌后左右展开，相邻目标车位14格，实际到位停稳后才下放那名APC普通乘客。路/转向受阻时安全停并可报`BLOCKED`。车队登记/任务不复制人、不生成库存。

军方与黑色守望仍独立且默认同盟；平民、合法感染目标、有效伪装、友军、受保护者及创造/旁观保护沿用原规则，关闭PvP时仍保护真人乘员。车辆是机械，不能整车感染/吞噬。缺合法操作员、空弹、墙或友军遮挡、近距爆炸风险都可能使AI不开火。

## 6. 原生车库与闲兵后勤

### 放基地、预览并确认车库

保持普通模式；若已切原型体，按当前“原型体模式”键（默认B）切回，再用`/blacklight status`确认`mode=vanilla`。下面在另一片空地铺131×137的Y80平台，基地与车库只各建一次：

```mcfunction
/tp @s 275 82 76
/fill 190 80 -8 320 80 128 minecraft:stone
/blacklight military base place military 200 80 0 0
/blacklight military base status
/blacklight military garage bases
/blacklight military garage preview "200:80:0:0:false" 280 80 34 0
```

军方基地65×65，`base place`直接放置并补下方支撑；只有车库有预览/确认。上述固定基地ID是`200:80:0:0:false`。若改坐标/阵营/朝向，使用`garage bases`的实际ID。车库31×27、出口另需9格平地，预览拒绝时按原因检查加载、地块、核心与冲突。

成功后在约60秒内复制**本次输出的完整`garage attach`命令**执行，再查状态；不要照抄占位词或复用旧令牌：

```mcfunction
/blacklight military garage attach token_UUID
/blacklight military garage status
```

`token_UUID`只替换为这次预览令牌，与车队UUID无关。留在基地/车库附近等待真实原生人员生成、步行到车位：卡车需1人、APC/坦克各2人；缺人、路堵或加载车辆预算满时等候。军方/黑色守望原生人员名额仍为16/20。每车库三车型各1个一次性槽，首次派发给本车一次有限初始弹药，燃油为0；后续补给不刷新这份初始配额。

### 看真实搬运，再看自动返补

打开车位后端的真实补给箱，记录弹药与维修件。当前固定车库的箱位：卡车`285 81 36`、坦克`295 81 36`、APC`305 81 36`；对应车位中心分别为`285.5 / 295.5 / 305.5，Y81，Z46.5`。箱子开关不刷新物资，任意另放的箱子不会被认证为补给源。

原生车须无玩家归属、无真人占车/控制锁、同维度，当前驻地匹配；**完整车身与炮管进入对应维修位、摆正停稳**后，才征用该基地已有、同阵营、可用且空闲的原生普通兵。兵要先走到认证箱实际取料，再走到关联车交付。忙于命令、战斗、自身补弹或已乘车的兵不被抽走；不强拆司机/炮手，不新生后勤兵，不覆盖原枪械。玩家放置/抢到的车按第4节手动补给。

要让这座车库真正派发的坦克测试自动返补，等三个槽已派发且坦克停在原位，再执行以下登记。额外命令军官只负责车队指挥；**搬运者仍必须是基地原生闲兵**：

```mcfunction
/tag @e[type=blacklight:military_tank,x=295.5,y=81,z=46.5,distance=..5,nbt={VehicleNativeOrigin:"200:80:0:0:false"},sort=nearest,limit=1] add m61_native_tank
/summon blacklight:military_officer 299.5 81 46.5 {Tags:["m61_native_officer"],PersistenceRequired:1b,MilitaryAutomaticJoin:0b,MilitaryRole:"officer",MilitaryRank:"lieutenant"}
/blacklight military fleet crew @e[type=blacklight:military_tank,tag=m61_native_tank,limit=1] @e[type=blacklight:military_officer,tag=m61_native_officer]
/blacklight military fleet create @e[type=blacklight:military_tank,tag=m61_native_tank,limit=1] @e[type=blacklight:military_officer,tag=m61_native_officer,limit=1]
```

把下面`fleet_UUID`换成**刚输出的原生坦克车队UUID**。先令它驶到南侧开阔地，实际到位后再召唤固定合法靶耗弹；观察主炮HE/AP真实减少，留出实际装填时间：

```mcfunction
/blacklight military fleet order fleet_UUID move 295 81 105
/blacklight military fleet inspect fleet_UUID
```

到位、射线上无友军且自己已离开爆炸邻域后：

```mcfunction
/summon blacklight:infected_host 295.5 81 125.5 {Tags:["m61_native_enemy"],PersistenceRequired:1b,NoAI:1b,Invulnerable:1b}
```

观察到自动返补开始后，仅清掉这个固定靶，防止补完后持续交火；保留原MOVE任务：

```mcfunction
/kill @e[type=blacklight:infected_host,tag=m61_native_enemy]
/blacklight military fleet inspect fleet_UUID
/data get entity @e[type=blacklight:military_tank,tag=m61_native_tank,limit=1] VehicleInventory
```

返补与出发规则：

- 仅当前非玩家控制的原生AI车自动征用后勤。主弹总量≤20%或HP<35%触发临时返补；坦克HE+AP合计上限20发，≤4发触发；APC自动炮≤24/120触发。机枪/火箭是副武器，不掩盖主弹不足；无武器卡车不因0弹返补。
- 16格内已有合法近敌时，达到低料/重伤条件但主弹非空先接敌；主弹空可撤回。自动返补选择同维度、同阵营、已加载、核心有效、256格内且有实际有用匹配库存的车库，再按真实道路靠泊，不跨维度/强载/瞬移。
- 自动返补期间保持原`mission/target`。主弹≥50%且HP≥60%、搬运已结束才恢复原任务；坦克至少合计10发且252HP，APC至少60发且144HP。达到出发线即可，不承诺补满；未触发低料的GUARD不因这条出发线被拉走。维修按每件最多20HP计；6发坦克弹或两件不足的维修件不能冒充达线。
- 缺料继续守恒停车等待；把真实匹配物资补入原认证箱后再观察。手动`return`是显式更换任务，不能用它证明原MOVE被保留；新的命令或玩家接管会取代/清除临时返补。

用`fleet inspect`读任务/车队状态及“后勤”字段，并同时看实际乘员、箱子与车，不把单个状态当作全部原因：

| 现场 | 实际表现／可见状态 |
| --- | --- |
| 正在自动返补 | `RETURNING_FOR_SUPPLIES`，原任务与目标仍保留 |
| 无合适已加载匹配库存 | 停车，`NO_LOADED_MATCHING_STOCK`；保留原任务 |
| 已靠泊但未达到出发线 | `WAITING_FOR_PRIMARY_AMMUNITION`、`WAITING_FOR_REPAIR_PARTS`或两者合并；等真实交付 |
| 返补完成 | `RESUMED_ORIGINAL_ORDER`，随后继续原任务；原任务是GUARD则继续守卫 |
| 司机死亡/离车/失效 | 缺有效司机时`NO_DRIVER`，不能靠后勤凭空补司机 |
| 正式军官失效 | 仍在车内的有效军人可按军衔/UUID代理，正式任命不改；无有效人员则`NO_COMMANDER` |
| 玩家拥有/占车或控制锁 | AI让位；有有效车内指挥时可见`PLAYER_CONTROL`，不强拉玩家走、不免费补给 |
| 道路/转向堵塞 | 有限重试后`BLOCKED`，返补可见`SUPPLY_ROUTE_BLOCKED`；不穿墙/瞬移 |
| 车辆卸载 | 暂停，可能`UNLOADED`／`SUPPLY_VEHICLE_UNLOADED`；不判死重发 |
| 车库/核心/道路区域未加载 | 拒选目的地或停止行驶，可见无加载库存/受阻状态；不强载补齐 |
| 核心坏、箱被拆、停车无效或无合格闲兵 | 不从箱遥传；取消/等待，必要时重新选合适车库，保留实物与原任务 |
| 搬运NPC死亡、离开、战斗或任务取消 | 取消搬运，原实物包在当地释放，不远程退箱、不另造一份 |

箱子实际取出与车辆实际收到之间，可以存在同一份在途实物包；取消、卸载或正常停服后可能留下当地可回收掉落物，重进不承诺无缝续运，也不按任务元数据重造货物。分别核对箱内、车内和地上数量。

破核心只在测试世界做，并先查`base status`。登记指挥终端可能不止一处，剩一处仍可能有效；以实际状态为准。核心失效停止该处派发/服务，不清车辆身份或库存。`RESERVED`保留已占用车辆UUID，`ISSUED`已派发，`LOST`明确损毁；开走、被抢、卸载、死亡或重启都**不免费重发车辆/人员**。旧基地不自动增盖车库，其他阵营与奖励政策沿用。

## 7. 抢夺

回到第5节有真实AI乘员的命令车，先用它自己的`fleet_UUID`下`guard`停稳。人下车、主手空、结束其他抓取/动作，再执行：

```mcfunction
/blacklight infect
```

此命令只感染。关闭聊天按当前“原型体模式”键（默认B），再查：

```mcfunction
/blacklight status
```

确认`infected=true`、`mode=prototype`。靠近司机舱、视线无遮挡，**空手Shift右键开始后松开Shift**，保持空手并在司机座接触点约3.5格内满30tick（20TPS约1.5秒）；不用持续右键。持续按Shift会在成功后触发原版下车。

成功后同一辆车归玩家，真实AI乘员保持同UUID、伤势、单位、军衔与装备，从安全位置被驱逐。车的HP、弹药、旧燃油数据与冷却不刷新；后续玩家手动补给。抢夺不自动杀人、吞噬或给奖励，仍触发原敌对/伪装识破规则。退远、隔墙、乘员变化、无安全出口或另一人占用动作时取消/拒绝；已有任何真人乘员的车拒抢。

## 8. 重启与双人

正常保存退出前，记录车辆/乘员UUID、HP、各弹种与冷却、原生出处/当前驻地、车库槽、原任务与后勤状态，以及箱内/在途/地上实物。退出整个单人世界或正常停服，再开**同一存档**核对：同车同人、物资不增加、伤势不免费复原、返补中的原任务保留、旧按键输入不恢复。菜单开关不算重启；未知/卸载槽不重发。旧存档只用副本复核。

双人分别检查同时上司机、司机/炮手换席、司机掉线/死亡、改过的攻击/使用键、真人占车拒抢与PvP保护；炮手在席时司机应持续能开车且不能夺火力。分别截图普通皮肤与原生Mercer的第一人称/F5、HUD与原版下车提示位置。任意YSM rig与真人双客户端结果按各自实际回执记录。

反馈带加载器/游戏/Java版本、完整命令与输出、步骤、车/人UUID、位置、模式、弹药/耐久及箱料前后、任务/后勤状态和截图。最终GL、完整suite数量、JAR校验及交付结论统一查[主记录末节](verification/MILITARY6_VEHICLES_20261026_ZH.md)和包外回执；制作资料与历史CPU预览从[原相对入口](制作资料/M6_载具与车库.md)访问。
