# military.5：玩家测试指南

本文用于收到 military.5 安装包后的实际试玩，覆盖10种士兵、双手持枪、归宿、有限驻军和基地补给。它是操作与预期清单，不代表这些步骤已经由玩家逐项验收。最终交付、普通服重启及完整验证状态以[实施记录](verification/MILITARY5_IMPLEMENTATION_20261025_ZH.md)为准。

## 1. 安装与准备

先退出游戏，备份原黑光主JAR，再把对应版本的 military.5 主JAR放入同一实例的 `mods`。同一实例只启用一个 Blacklight/黑光主JAR，保留该版本已有的 GeckoLib；源码、文档、交付ZIP不用放进 `mods`。NeoForge包用于MC1.21.1，Forge包用于MC1.20.1。不要把两种加载器的包混用。

本指南无需修改PCL设置。先用允许作弊、普通难度的创造测试世界；基地放置与拆核心会真实修改方块，使用空白测试世界或存档副本。单人聊天栏每次输入一行，以 `/` 开头；服务器需要管理员权限。

本版工作树版本是 `0.3.4-preview.20261025-military.5`。作者模组的“1.2.1”由用户之后自行拉取，标签、提交及兼容结果尚未核实；不要同时放入另一份作者主JAR来代替兼容验证。运输卡车、APC/装甲车、坦克、玩家驾驶与抢夺属于后续M6设计范围，本指南没有已实装的车辆测试命令。

## 2. 生成10种士兵，分别检查两套外观

先在空地运行下面10行，给这些测试兵统一加上 `m5_visual` 标签。每行都是独立命令；不要在原有驻军旁反复执行，以免把重复生成误认为自动复刷。

```mcfunction
/summon blacklight:military_soldier ~3 ~ ~ {Tags:["m5_visual"]}
/summon blacklight:military_heavy ~7 ~ ~ {Tags:["m5_visual"]}
/summon blacklight:military_team_leader ~11 ~ ~ {Tags:["m5_visual"]}
/summon blacklight:military_officer ~15 ~ ~ {Tags:["m5_visual"]}
/summon blacklight:military_commander ~19 ~ ~ {Tags:["m5_visual"]}
/summon blacklight:blackwatch_soldier ~3 ~ ~8 {Tags:["m5_visual"]}
/summon blacklight:blackwatch_heavy ~7 ~ ~8 {Tags:["m5_visual"]}
/summon blacklight:blackwatch_team_leader ~11 ~ ~8 {Tags:["m5_visual"]}
/summon blacklight:blackwatch_officer ~15 ~ ~8 {Tags:["m5_visual"]}
/summon blacklight:blackwatch_commander ~19 ~ ~8 {Tags:["m5_visual"]}
```

| 身份 | 军方实体ID | 黑色守望实体ID |
| --- | --- | --- |
| 普通兵 | `blacklight:military_soldier` | `blacklight:blackwatch_soldier` |
| 重装兵 | `blacklight:military_heavy` | `blacklight:blackwatch_heavy` |
| 小队长 | `blacklight:military_team_leader` | `blacklight:blackwatch_team_leader` |
| 军官 | `blacklight:military_officer` | `blacklight:blackwatch_officer` |
| 指挥官 | `blacklight:military_commander` | `blacklight:blackwatch_commander` |

站在这些兵附近，分别执行并观察一次：

```mcfunction
/blacklight military outfit @e[tag=m5_visual,distance=..48] uniform
/blacklight military outfit @e[tag=m5_visual,distance=..48] equipped
```

正确参数只有 `uniform` 和 `equipped`，不是 `unarmed`、`armor` 或兵种名。先看完军服状态再执行第二行。`uniform` 收起真实武器并卸下战斗护具；`equipped` 取回原武器，换装不免费补弹、不改身份。两种状态都检查人脸、两阵营服装、军衔与角色标志，转到正面、侧面、背面看是否穿插或残留一只悬空手臂。

选择器只找当前维度、48格内带该标签的实体。提示“未找到实体”时先走近；不要改成无条件 `@e`。用role命令改身份不会改注册实体ID，例如普通兵改成军官后仍要按 `military_soldier` 选择。

## 3. 看NPC双手、转头、行走和换弹

让测试兵处于 `equipped`，观察待机和自然巡逻时，枪与双手是否连在一起，肩根是否留在躯干上，手肘是否自然弯折。靠近后从多个方向观察转头，尤其看大角度转向时肩膀、前臂、枪托与支撑手是否突然分离。

真实开火测试请另找空地，单独生成一名普通兵，在其附近放一名感染宿主：

```mcfunction
/summon blacklight:military_soldier ~3 ~ ~ {Tags:["m5_action"]}
/summon blacklight:infected_host ~12 ~ ~
```

保持创造模式观察。感染者死亡后可再次生成，直到看到真实射击、换弹和恢复持枪；新兵有有限的初始弹匣与备弹，换弹并不等于无限弹药。留意开火时持枪手与支撑手是否共同跟随，装填时支撑手是否离握、工作、回握，动作衔接是否突跳。再用 `blacklight:blackwatch_soldier` 在另一片空地重复。

该流程不改变作者当前敌我规则；没有开火时记录当时目标与命令输出，不靠攻击无关NPC来认定阵营政策。CPU预览或自动GameTest通过不能代替这一步的Minecraft画面检查。

## 4. 玩家第一人称：双手、左手与副手

```mcfunction
/give @s blacklight:military_rifle
/give @s blacklight:rifle_round 128
```

将步枪放主手，副手清空。按住潜行键再右键装填，松开潜行后右键逐发射击；新领的枪需要装填，创造模式也扣真实弹药。观察真实皮肤手臂、袖层、支撑手和枪是否一起运动，换弹后是否回到正确握点。

依次检查：

1. 主手枪、副手空：应出现持枪手与支撑手；空仓右键不应冒充成功开火。
2. 在“选项→皮肤自定义→主手”改为左手：枪和真实手臂应镜像适配。按F只是交换主副手，不能替代这项左手设置。
3. 主手枪、副手放石头等物品：副手物品保留，枪不再额外占用同一只支撑手。
4. 按F把单枪换到副手，主手空：副手枪只绘自己的持枪手；再看双枪时是否每把枪各用一只手，避免出现第三只手。
5. 切换快捷栏、开关菜单、暂停、切第三人称、保存退出重进：不应凭空播放一次射击，也不应补满弹药。标准手臂和纤细手臂皮肤有条件时各看一次。

若使用光影、资源包或其他第一人称模组，反馈时写明组合；本指南不预先宣称所有组合都通过。

## 5. 放基地，检查有限发放与真实补给

影响范围：军方基地占65×65，黑色守望基地占65×81（本节旋转参数均为0）；命令会实际放置建筑及下方支撑。选择足够大的平地，留空上方，不要在正式建筑或旧基地上重复覆盖。

落在平地，先放一座军方基地A：

```mcfunction
/blacklight military base place military ~8 ~-1 ~8 0
/blacklight military base status
```

如提示位置被占，换一片空地；不要用清除方块强行覆盖旧建筑。走到基地内或入口附近，等约15秒再查状态。军方一次最多发放16人，黑色守望20人；分批发放，死亡不免费补刷。16/20是原生发放槽，不是能住多少人的建筑容量。

要看另一方，在另一片足够远且开阔的平地运行：

```mcfunction
/blacklight military base place blackwatch ~8 ~-1 ~8 0
/blacklight military base status
```

主要看这些字段：

| 输出 | 如何理解 |
| --- | --- |
| `issued` | 累计已发放的原生名额，不能因离开、重进或修复核心而归零重发 |
| `loaded / lost / unloaded` | 当前加载、已确认死亡、暂未加载；卸载不当作死亡 |
| `loaded_garrison` | 本维度加载的原生守军数/额度，默认上限80 |
| `muster COMPLETE` | 原生有限名额已发完 |
| `command_core ACTIVE` | 六处原指挥终端中至少一处存在 |

`GARRISON_LIMIT` 是额度已满，`NO_NEARBY_PLAYER` 是玩家不够近，`POST_CHUNK_UNLOADED / POST_NOT_TICKING` 表示岗位尚未加载或运行，`POST_BLOCKED / POST_COLLISION` 表示岗位受阻。人口开关关闭会显示相应 `CONFIG_POPULATION_DISABLED / CITY_POPULATION_DISABLED`；先记录原因，不把每种暂停都当作刷兵故障。

补给请在军方基地A测普通步枪兵。打开基地原有的登记弹药箱，记下 `rifle_round` 数量，让士兵经真实战斗消耗弹药，再观察他是否走到仓库箱旁取弹，箱内相同弹种是否实际减少。初始满备弹且无补给缺口的兵不会为了演示立即取弹；交战、显式组织命令也可能让补给暂缓。多名士兵共用库存时不能把全部扣库算给同一人。

不要把外面随手放的普通箱子当成自动仓库。箱子空了或被拆不会免费生弹；换装、改归宿、换基地也不会补弹。第二个登记弹药箱也应从地面靠近，不应寻路到仓库屋顶。

## 6. 独立士兵有没有归宿

在有效军方基地附近另刷一个兵，不要用它替代原生守军：

```mcfunction
/summon blacklight:military_soldier ~3 ~ ~ {Tags:["m5_home"]}
/blacklight military home @e[tag=m5_home,distance=..32] status
```

新兵约20 tick后初始化，等两秒再查询。`home` 应关联256格内已登记、同阵营、有效的基地；外来兵不增加该基地原生 `issued`。输出里的UUID是同一个人的标识，`origin` 是原生出处，与当前 `home` 分开；独立刷出的兵没有原生发放来源是正常的。

再去离所有已登记基地足够远的空地（离基地中心超过300格），另刷一名：

```mcfunction
/summon blacklight:military_soldier ~3 ~ ~ {Tags:["m5_field"]}
/blacklight military home @e[tag=m5_field,distance=..32] status
```

等两秒后，没有合适基地时可显示 `home=FIELD_OR_PENDING`，但应已有 `anchor` 坐标，而不是一直 `PENDING`。自动模式仍可显示 `mode=AUTO`；这不表示已经找到基地。士兵以保存的野外锚点附近巡逻，不应走到哪里就把锚点滑到哪里。

可对该名兵依次试：

```mcfunction
/blacklight military home @e[tag=m5_field,distance=..32] field
/blacklight military home @e[tag=m5_field,distance=..32] status
/blacklight military home @e[tag=m5_field,distance=..32] auto
```

`field` 把当前位置选为固定野外锚点，输出 `mode=FIELD`；`auto` 恢复自动找基地。它们不发武器、不补弹、不撤销组织命令。`order=EXPLICIT` 表示组织命令优先，此时不能用是否自动回家来判断失效。

## 7. 六处核心失效后，步行迁移与修复

先用两座同阵营军方基地测试。在第5节最初放A时的原站位、尚未移动的情况下，可再运行下面一行放B；A与B的原点相隔120格且不重叠：

```mcfunction
/blacklight military base place military ~128 ~-1 ~8 0
```

若已经离开原站位，先找两片不重叠的空地，使两座基地中心相距约120格。保持两座基地和沿途区块加载，确认B为 `ACTIVE`。不要用跨山、未加载长路或另一阵营基地来替代本项。

在A附近查 `base status`，记下A的原点 `[X, Y, Z]`、`issued / lost`，再给一名A的原生普通守军加标签并保存UUID：

```mcfunction
/tag @e[type=blacklight:military_soldier,distance=..24,sort=nearest,limit=1,tag=!m5_home,tag=!m5_visual,tag=!m5_action,tag=!m5_field] add m5_migrate
/blacklight military home @e[tag=m5_migrate] status
```

先确认输出 `origin` 确实属于A；选错了不要继续拆核心。不要对测试兵下达手工行军/守卫命令，否则它会优先执行该命令。

仅针对本指南 `turn=0` 放置的基地，六处真实终端坐标如下；`X/Y/Z` 是状态里基地原点，不是玩家脚下：

| 下层 | 上层 |
| --- | --- |
| `X+11, Y+2, Z+9` | `X+11, Y+7, Z+9` |
| `X+13, Y+2, Z+9` | `X+13, Y+7, Z+9` |
| `X+15, Y+2, Z+9` | `X+15, Y+7, Z+9` |

在创造测试世界手动拆这六格的 `blacklight:command_terminal`。可先保留一格，确认仍为 `ACTIVE`；再拆最后一格，留在附近让六处区块连续加载约10秒。预期先 `OBSERVING` 并暂停新发兵，随后 `INACTIVE`。只拆围墙或仓库不会使整座基地失效；远离导致卸载也不等于被毁。

继续查询：

```mcfunction
/blacklight military base status
/blacklight military home @e[tag=m5_migrate] status
```

同一UUID的守军应保留原 `origin`，当前 `home` 改为有效同阵营B，并正常步行转移；观察过程中不要用传送来代替行走。没有可用B时，改为固定野外锚点也符合规则。转移期间仍能按现有规则作战，剩余弹药、身份和编制不因转移重置。

最后取一个终端，放回A上述六个原位置之一：

```mcfunction
/give @s blacklight:command_terminal
```

A应恢复 `ACTIVE`。在别处随意放同名方块不算修好原核心。修复不会重建建筑、补箱库存、复活死者或重置已用名额；若A尚有从未发放的槽，恢复后可继续发放这些剩余槽。

## 8. 保存退出、重进与无复刷

记下测试兵UUID、`home / anchor / origin`、基地 `issued / lost`、弹药箱库存和玩家弹匣。保存退出，再进同一测试世界后查询；不要再次执行 `summon` 或 `base place` 来“恢复”场景。

预期同一批士兵与字段保留，已用名额不重新发一批，已死者不因重进复活，核心修复不重置库存。离开时尚未确认的缺失观察不跨卸载/重启累计；已经确认失效的核心不会仅因卸载自动恢复。单人保存重进与普通专用服务器两个独立进程的重启是两种检查，前者不能替代后者。

反馈时附主JAR完整文件名、MC/加载器版本、失败步骤、命令输出和截图或短视频；启动失败附当前实例 `logs/latest.log`。本指南源码核对入口为 `command/CityMilitaryCommands.java`、`registry/ModEntities.java`、`city/MilitaryHomeSystem.java`、`city/CityMilitaryService.java`、`firearms/FirearmItem.java` 与 `FirearmViewEvents.java`。

- [基地与归宿规则解释](MILITARY5_BASES_AND_HOMES_ZH.md)
- [归宿、补给与实际回归记录](verification/MILITARY5_HOMES_20261025_ZH.md)
- [实施与待验收记录](verification/MILITARY5_IMPLEMENTATION_20261025_ZH.md)
- [独立代码审阅及其验证边界](verification/MILITARY5_FINAL_REVIEW_20261026_ZH.md)