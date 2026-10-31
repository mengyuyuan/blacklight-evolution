# military.2 玩家安装与测试指南

建议按下面顺序，在一个**全新、创造模式、允许作弊的超平坦世界**测试。先看兵模、枪械和手动基地，再去城市；旧存档先保留备份。

## 1. 安装：只替换黑光主 JAR

1. 完全退出游戏和正在运行的服务端。
2. 打开这次要玩的启动器实例的 `mods` 文件夹，将旧的黑光主 JAR 移到文件夹外备份，放入本次交付的、带 `military.2` 标识的对应主 JAR。`mods` 内只保留一个黑光主 JAR。
3. **保留这个实例原来已能正常运行的 GeckoLib**，不要再叠放一份。无需为本轮枪械、兵模或基地另装素材模组。
4. NeoForge 1.21.1 使用 NeoForge 包；Forge 1.20.1 使用 Forge 包。两种主 JAR 不能混放，也不要把两个游戏版本的世界互相搬过去测试。
5. 启动后在模组列表确认加载的是本次新包。联机时，客户端与服务端都换成对应的新主 JAR。

本文不指定最终交付文件名；以本次交付目录中对应加载器、游戏版本和 `military.2` 标识的主 JAR 为准。

## 2. 创建测试世界

新建世界，选择创造模式、允许作弊、超平坦。难度选择普通，不用和平模式。进入后可以执行：

```mcfunction
/gamemode creative
/difficulty normal
/time set day
```

创造物品栏中有三个相关栏目：

- **黑光：进化**：包含军方步兵、军方重装兵、黑色守望步兵、黑色守望重装兵的刷怪蛋。
- **军事枪械**：八种枪械与七种弹药。
- **黑光：军事设施**：路障、沙袋、装饰军箱、终端、通信天线和探照灯。

找不到栏目时，可以先用下面的 `/summon`、`/give` 命令测试。如果命令也提示未知实体或未知物品，先检查是否仍装着旧主 JAR，或把另一个加载器的包放进了当前实例。

## 3. 看四种兵装，再切换制服与全装

站在空地上，依次召唤四种士兵：

```mcfunction
/summon blacklight:military_soldier ~3 ~ ~
/summon blacklight:military_heavy ~6 ~ ~
/summon blacklight:blackwatch_soldier ~9 ~ ~
/summon blacklight:blackwatch_heavy ~12 ~ ~
```

先切成只穿基础制服的状态。这里的“脱装”会收起护具和武器，**仍有完整制服、面部与靴裤**。

```mcfunction
/blacklight military outfit @e[type=blacklight:military_soldier,distance=..24] uniform
/blacklight military outfit @e[type=blacklight:military_heavy,distance=..24] uniform
/blacklight military outfit @e[type=blacklight:blackwatch_soldier,distance=..24] uniform
/blacklight military outfit @e[type=blacklight:blackwatch_heavy,distance=..24] uniform
```

绕到正面、侧面和背面，查看脸、制服、裤腿、靴子是否完整；再穿回全装：

```mcfunction
/blacklight military outfit @e[type=blacklight:military_soldier,distance=..24] equipped
/blacklight military outfit @e[type=blacklight:military_heavy,distance=..24] equipped
/blacklight military outfit @e[type=blacklight:blackwatch_soldier,distance=..24] equipped
/blacklight military outfit @e[type=blacklight:blackwatch_heavy,distance=..24] equipped
```

查看头盔/面罩、护甲、弹袋、背包与护膝；普通兵和重装兵应有不同的装备体积，军方与黑色守望也有不同外观。手持枪的枪口应朝前，手部与握把位置合理，护具应随身体运动。

通用写法是 `/blacklight military outfit <实体选择器> uniform` 或 `equipped`。选择器必须只选中军方/黑色守望士兵；直接写 `@e` 混入玩家、动物或掉落物时，整次操作会被拒绝。上述命令会改变 24 格内该类型的全部士兵，测试基地时注意这一点。

新生成普通兵的游戏化初始库存是**弹匣 30 + 备用 90 发**；重装兵是**弹匣 60 + 备用 180 发**。这不是原作弹药参数。换装只是收起/取出原来的枪，不应免费补满弹药。

## 4. 最快试枪：给枪、给弹、装填、射击

先拿一把长步枪和一组步枪弹：

```mcfunction
/give @s blacklight:military_rifle
/give @s blacklight:rifle_round 64
```

1. 把枪拿在手中。新领取的枪需要先装填。
2. **按住潜行键，再按右键**装填；默认键位通常是 Shift + 右键。库存里的对应弹药会减少，装填有等待时间。
3. 松开潜行键，**右键射击**。每次射击会减少弹匣里的实际弹药；先逐次点击右键，便于核对每次消耗。
4. 看屏幕的弹匣提示，或把鼠标移到物品上查看弹量、弹种与装填时间。
5. 装填和射击在创造模式下也消耗实际弹药。空仓且没有对应库存弹药时，不能继续射击。

八种枪与七种弹药对应如下。物品栏里按中文名搜索即可；需要命令时给物品 ID 加上 `blacklight:` 前缀。

| 枪械 | 枪械 ID | 对应弹药 ID | 弹匣容量 |
| --- | --- | --- | --- |
| 长步枪 | `military_rifle` | `rifle_round` | 30 |
| 紧凑卡宾枪 | `military_carbine` | `rifle_round` | 24 |
| 班用机枪 | `military_machine_gun` | `machine_gun_round` | 60 |
| 榴弹发射器 | `grenade_launcher` | `grenade_round` | 6 |
| 火箭发射器 | `rocket_launcher` | `rocket_round` | 1 |
| 导弹发射器 | `missile_launcher` | `missile_round` | 1 |
| 温压榴弹发射器 | `thermobaric_launcher` | `thermobaric_round` | 1 |
| 感染榴弹发射器 | `infection_launcher` | `infection_round` | 1 |

例如测试火箭：

```mcfunction
/give @s blacklight:rocket_launcher
/give @s blacklight:rocket_round 8
```

导弹当前是**沿瞄准方向直射，不锁定、不追踪目标**；榴弹类有抛物运动。感染榴弹使用现有感染系统。这轮没有把坦克、直升机或停机坪做成可驾驶载具。

想确认有限弹药，可以用步枪装填一次后执行下面的命令，只清掉背包里剩余的步枪弹，再把弹匣打空；之后潜行右键不应凭空装出弹药：

```mcfunction
/clear @s blacklight:rifle_round
```

## 5. 放置大型基地

先飞到一块远离刚才测试兵和已有建筑的平地，落地站稳。基地会检查整片空间，有建筑、流体或方块实体占用时可能拒绝放置；换一块空地即可，不需要拆掉现有建筑来迁就测试。

最简单的军方基地命令如下，会将基地放在你旁边，留出 8 格距离：

```mcfunction
/blacklight military base place military ~8 ~-1 ~8 0
```

飞进去查看军营、指挥楼、独立军械区、东北弹药库、医疗与补给棚。试着走过南门、爬警戒塔、登营房东侧楼梯、进入二层并到屋顶，确认入口和箱子没有堵住。

再找一块至少相隔 100 格的空地，落地后放置黑色守望基地：

```mcfunction
/blacklight military base place blackwatch ~8 ~-1 ~8 0
```

它有额外的双层科研、医疗隔离场景。科研终端和实验装置属于场景表现，没有 Blacknet 任务或自动实验玩法。

**坐标不是基地中心，而是整片占地矩形的最小 x、最小 z 角，y 是地板高度。** 不填坐标时，使用玩家脚下方块作为这个最小角；因此站着直接使用无坐标命令，玩家可能正好站进新围墙。上面的相对坐标写法留出了间距。

完整格式是：

```text
/blacklight military base place military|blackwatch [x y z] [turn]
```

其中 `military|blackwatch` 是二选一，不能连竖线一起输入；方括号也不输入。要指定旋转，前面必须先填三个坐标。`turn` 是整数 `0`、`1`、`2`、`3`，不是文字 `turn0`。

| 基地 | turn 0 / 2 的占地 | turn 1 / 3 的占地 |
| --- | --- | --- |
| 军方 | 65×65 | 65×65 |
| 黑色守望 | 65×81 | 81×65 |

无论怎样旋转，占地都从指定最小角向 **+x、+z** 扩展，最高到地板上方 17 格；`turn 0` 的入口在南侧。只在新的空地测试不同旋转，不要在已放好的基地上反复执行 `place`。

在基地附近执行：

```mcfunction
/blacklight military base status
```

`issued` 是已发放的驻军槽位，`loaded` 是当前已加载的存活驻军，`lost` 是已记录损失，`unloaded` 是尚未加载的已发放驻军。`state WATCH` 为警戒、`ENGAGED` 为实际接敌、`FALLEN` 为已全损失守，`UNLOADED` 为驻军卸载、不能据此判断安全，`MUSTERING` 为尚未集结。军方有 16 个槽位，黑色守望有 20 个；驻军逐步创建并受人口预算与区块加载限制，欠员不等于结构缺失。

## 6. 查军械、弹药与保存重进

弹药库是东北侧独立掩体，里面两个原版箱子有七类有限弹药；军械库有独立枪械箱。**绿色装饰军箱不能打开存物品**，真正的库存是原版箱子。终端、天线属于装饰，探照灯提供光照。

建议按顺序检查：

1. 打开一个真实弹药箱，记下步枪弹数量，拿走一部分，再关箱重开，数量不应自己恢复。
2. 玩家从箱子取得对应弹药后，潜行右键给自己的枪装填，背包弹药应实际减少。
3. 留一把已经打掉几发的枪在快捷栏，记录弹匣提示；也记录剩余库存和基地状态。
4. 在附近留一名士兵处于 `uniform` 状态，记住是哪一名。
5. **保存并退出到标题界面，再进入同一个世界**。核对枪中弹量、背包和箱中库存、士兵制服状态；不应免费补弹或把取空的箱子重新刷满。
6. 再看 `base status`。同一个已发放槽位不应因重进又创建一名新兵；`loaded` 会随区块加载变化，尚未发放的剩余槽位可以继续正常创建，`issued` 不应超过该基地的槽位总数。

想进一步观察驻军交战，可在**一次性测试世界**的基地外围、士兵视线内执行：

```mcfunction
/blacklight spawn_infected zombie ~ ~ ~8
```

观察士兵朝感染目标射击、装填；持续交战消耗弹药后，驻军需要靠近可达的弹药箱才能从实际库存补给，空箱不会提供免费弹药。感染目标可能攻击或感染士兵，这一步建议在完成上面的外观与保存测试后再做。没有感染目标时，普通原版动物/僵尸不一定会自动触发军方开火。

## 7. 最后再试城市

先完成平地测试，再从主世界或其他非城市维度使用对应命令：

| 版本 | 进入城市 | 城市内容 |
| --- | --- | --- |
| NeoForge 1.21.1 | `/blacklightcity travel` | 新版 `blacklight:world` 城市 |
| NeoForge 1.21.1 | `/blacklightcity futian` | 原有 `blacklight:city` 福田城市 |
| Forge 1.20.1 | `/blacklightcity travel` | 现有 `blacklight:city` 福田城市；没有 `futian` 子命令 |

在城市里再次执行 `/blacklightcity travel` 返回进入前的维度与位置。NeoForge 想从新版城市切到福田时，先返回，再从非城市维度执行 `futian`。旅行正在加载时稍等，不要连续重复输入或跑远。

新城市基地若附近存在足够安全的道路地块，还会安排独立道路检查站，关联门岗使用同一份有限编制。默认驻军守在保存的岗位，补给后返回；显式编制命令可以覆盖默认岗位。

新增城市基地面向合适的新生成地块；旧城市已经看过、已经建设的街区，不会因为这次换 JAR 就强行覆盖成基地。想马上看基地，继续使用第 5 节的手动空地测试，不需要靠反复重进旧城区寻找变化。

## 8. 预览文件与反馈

- [士兵、制服与枪械外观预览](previews/military2-soldiers-viewer.html)。
- [两套大型基地的四向、剖视与平面预览](previews/large-military-bases/index.html)。

这些本地 HTML/PNG 来自模型、纹理和 NBT 的离线渲染，**不是 Minecraft 实机截图**；游戏内实际持枪、动作、光照与寻路以你的游玩画面为准。

遇到问题时，记录：游戏版本与加载器、主 JAR 文件名、所用命令、所在维度、预期和实际结果；外观问题尽量拍正面/侧面/背面，基地问题附坐标和 `turn`，库存问题附保存前后数量。本文是操作指南，不把正在进行中的服务端测试写成已经完成的玩家实机验收。
