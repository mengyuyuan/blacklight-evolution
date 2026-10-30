# military.4：简单测试流程

本次是完整黑光主模组，包含之前的兵种、编制、军官、枪弹与基地。只替换旧黑光主JAR，保留你已安装的GeckoLib。对应关系：NeoForge版用于MC1.21.1；Forge版用于MC1.20.1。

## 1. 安装与准备

退出游戏，把旧黑光JAR移出当前实例的 `mods` 备份，再放入本次名字带 `military.4-bundled.jar` 的对应主JAR。`mods` 中只保留一个启用的黑光主包；源码、文档、交付ZIP不用放进去。

先开一个允许作弊的创造测试世界，难度选普通。方块破坏测试请用新世界里的临时靶墙。按T打开聊天栏，每次输入一行命令。

## 2. 先检查旧基地是否来人

直接回到之前那座没人、显示 `issued 0` 的基地，落到门口附近，等约15秒，然后输入：

```mcfunction
/blacklight military base status
```

看距离最近的一条：

- 军方最多发放16人，黑色守望最多20人，会分批出现；已有0发放基地无需重新覆盖放置。
- `issued` 是累计已发出的名额，`loaded` 是当前加载的人数；`lost` 是已确认死亡，`unloaded` 是暂未加载。已发放后死亡的名额不会无限补兵。
- `muster READY` 表示可以继续集结，`COMPLETE` 表示有限名额发完。`loaded_garrison` 是本维度已加载的基地守军数/上限，默认上限80；史莱姆不占这个额度。
- `GARRISON_LIMIT`：基地守军额度已满；`NO_NEARBY_PLAYER`：离基地中心太远；`POST_CHUNK_UNLOADED`/`POST_NOT_TICKING`：岗位区块还没加载/运行，走近等一下；`POST_BLOCKED`/`POST_COLLISION`：岗位被方块或实体挡住。
- `CONFIG_POPULATION_DISABLED`/`CITY_POPULATION_DISABLED`：人口开关关闭。不要把这种配置停用误认为刷兵故障。

想试新基地：在开阔平地落地，再输入一条：

```mcfunction
/blacklight military base place military ~8 ~-1 ~8 0
```

另一方请去至少100格外再放，避免重叠：

```mcfunction
/blacklight military base place blackwatch ~8 ~-1 ~8 0
```

## 3. 看枪和射击表现

```mcfunction
/give @s blacklight:military_rifle
/give @s blacklight:rifle_round 128
```

把枪拿在主手：按住潜行键再右键装填，松开潜行键后右键逐发射击。新领的枪需要先装填，创造模式也消耗实际弹药。

看这几项：枪口应朝前、比例不要挡住大片视野；成功开火有短促后坐力和简易音效/枪口反馈；装填有简单下压动作。空仓、切快捷栏、保存重进时不应凭空播放一次开火，也不应免费补弹。可以按F换到副手，再试一次。镜头方向和子弹落点不会被这段物品后坐力改写。

本轮是简要表现改进，没有声称已制作完整专业枪械动画。还请实际看近墙、左右手及你的光影环境是否穿模。

## 4. 测试方块破坏

在空地自己摆一排相隔两三格的玻璃、橡木木板、石头、铁块，再摆一个装有物品的箱子。站约5至10格外，瞄准每一块单独射击。

| 靶子 | 默认预期 |
| --- | --- |
| 玻璃 | 步枪1发打碎 |
| 橡木木板 | 步枪连续4发；前三发有裂痕 |
| 石头 | 比木板耐打，普通步枪需要很多发 |
| 铁块 | 普通枪弹不能直接打掉，爆破也更耐受 |
| 箱子、基岩等保护块 | 默认不破坏，箱内物品保留 |

累积裂痕在最后命中约10秒后过期。额外试一次：对木板打3发，自己拆掉，再放相同木板；下一发不应直接打掉新木板。玩家真实放置和多块放置会清旧裂痕；管理员直接改区块、其他模组不发放置事件的替换没有统一世代标记，不能把这一项扩大为任意外部替换都能识别。

然后给火箭筒和弹药，用临时木墙测试：

```mcfunction
/give @s blacklight:rocket_launcher
/give @s blacklight:rocket_round 8
```

同样潜行右键装填，再松开潜行右键射击。命中附近木墙应受损，远处墙不应整片消失。榴弹/火箭/导弹/温压弹的建筑影响半径分别最多2.5/3/3.5/4格，并受遮挡、材料与默认48块处理额度限制；不是一次必拆48块。感染弹不破坏建筑，不会由这些弹体生成火灾。方块破坏会产生真实地图改动，不是临时特效。

NPC射击还遵守 `mobGriefing`；玩家射击检查建造权限和模组破坏取消事件。没有另行宣称兼容每一种领地保护插件。

## 5. 保存后再进一次

记住一处破坏后的靶墙、自己的弹匣、一个箱子的库存和基地人数，保存退出，再进同一世界。墙不应重新长出来，弹药不应自动补满，已发放守军不应重复刷一批。短时裂痕本身不保存。

## 看资料与其他功能

军事设施会随模组维度的新区块自然生成，不必每座都用命令放：NeoForge支持 `blacklight:world` 大世界和 `blacklight:city` 城市；Forge现有版本支持城市维度，没有Neo的大世界维度。大型基地可带安全位置的道路检查站，基地内已有指挥楼、弹药库等模块。自然生成不会覆盖之前探索过的旧区块。

从主世界输入 `/blacklightcity travel`：Neo进入大世界，Forge进入城市。Neo还可从主世界输入 `/blacklightcity futian` 进入城市。在目标维度中输入：

```mcfunction
/locate structure blacklight:city_military_base
```

按返回的坐标飞过去，看真实基地、箱子和驻军，再执行 `/blacklight military base status`。没有合适的道路旁安全空地时，候选位置会被跳过；不是每个区块都会有基地。

- [兵种、军衔、枪弹、建筑和外观资料](制作资料/README.md)
- [之前的军官和换装测试](MILITARY3_PLAYER_TEST_ZH.md)
- [编制、合成、补给、城市等完整测试](MILITARY2_PLAYER_TEST_ZH.md)

`未找到实体` 通常是选择器附近没有那种注册类型。要测试军方军官，先 `/summon blacklight:military_officer ~3 ~ ~`，再操作该类型；把普通兵用role命令改成军官后，实体类型仍为原来的 `military_soldier`，要按原类型选它。

配置位置是当前存档 `serverconfig/blacklight-firearms-server.toml`：`bulletBlockDamage`、`explosiveBlockDamage` 可分别关闭；`protectBlockEntities` 默认true，`maxBlocksPerBlast` 默认48。驻军上限在同目录 `blacklight-city-server.toml` 的 `militaryGarrisonLimit`。退出世界/停服后再修改。

反馈时记录：使用哪个主JAR、发生在哪个步骤、命令输出和截图；启动失败附当前实例的 `logs/latest.log`。
