# military.3：军官与队长快速测试

本包是完整黑光主模组，已包含 military.1 的编制与 military.2 的枪械、大型基地、兵模；不是额外叠加的补丁模组。

## 安装

退出游戏，在当前 PCL 实例的 mods 中，把旧黑光主 JAR 移到外面备份，再放入对应版本的 military.3 主 JAR。你已经装好的 GeckoLib 保留即可。NeoForge 1.21.1 与 Forge 1.20.1 使用各自目录，不能混放。

旧 military.1、military.2 交付保留作回退。先在新建的创造、允许作弊、普通难度超平坦世界测试。

## 先看六种新身份

创造栏“黑光：进化”可以找六个刷怪蛋，也可逐条输入：

```mcfunction
/summon blacklight:military_team_leader ~3 ~ ~
/summon blacklight:military_officer ~6 ~ ~
/summon blacklight:military_commander ~9 ~ ~
/summon blacklight:blackwatch_team_leader ~3 ~ ~6
/summon blacklight:blackwatch_officer ~6 ~ ~6
/summon blacklight:blackwatch_commander ~9 ~ ~6
```

每方分别为队长、野战军官、基地指挥官。绕到正面、侧面、背面看帽具、识别条、肩领、通信与指挥携行装具；黑色守望保留防毒面罩。可对照 `previews/military3-officers-viewer.html` 的实际资产预览。

新队长默认中士，步枪弹匣 30 发、备用 90 发；新军官默认少尉，新指挥官默认少校，两者卡宾枪弹匣 24 发、备用 72 发。这些是本模组游戏化库存。身份和军衔不会额外增加战斗属性。

## 脱装、换身份与保存

下面只选择 24 格内的军方军官：

```mcfunction
/blacklight military outfit @e[type=blacklight:military_officer,distance=..24] uniform
/blacklight military outfit @e[type=blacklight:military_officer,distance=..24] equipped
```

脱装应收起帽具、护具和枪，保留完整面部与制服；穿回后取出原来那把枪，不会补满弹药。

把附近军方步兵改成军官：

```mcfunction
/blacklight military role @e[type=blacklight:military_soldier,distance=..24] officer
```

可选身份为 `trooper`、`team_leader`、`officer`、`commander`。转换会补足身份最低军衔，保留已有枪、弹匣、备用弹与单位，**不会把旧步枪免费换成满弹卡宾枪**。实体类型 ID 仍然是原来的类型，外观和默认显示名称按当前身份变化，自定义名字保留。

记录一名军官的样子与穿装状态，保存退出，再进入同一世界，应保持一致。已有旧兵会按原军衔迁移身份，保留实际库存。

## 看他们实际带队

在远离其他兵的空地，先放两三名同阵营步兵和一名队长，再在 16 格内、互相可见处放一名军官，等待几秒。自动小队会优先选择实际军衔较高的成员负责指挥；军官死亡时由合法的存活成员接替，不会因此刷新一名新军官。

想检查单位，先读取最近军官的单位 UUID：

```mcfunction
/data get entity @e[type=blacklight:military_officer,sort=nearest,limit=1] MilitaryUnit
```

该输出是 NBT UUID 数组，日常操作更适合按下面方式建立有名字的手工测试队：

```mcfunction
/blacklight military unit create military tactical_squad 测试队 8
/blacklight military unit assign 测试队 @e[type=blacklight:military_soldier,distance=..24]
/blacklight military unit assign 测试队 @e[type=blacklight:military_officer,distance=..24]
/blacklight military unit commander 测试队 @e[type=blacklight:military_soldier,sort=nearest,limit=1]
/blacklight military inspect 测试队
```

这会明确任命最近步兵为指挥官。此后加入或转换出更高军衔军官，不应抢走这个手工任命；保存重进后任命也应保留。单位名若已存在，另取一个名字。只有士兵能入编，两方不能混编。

## 基地里的军官

在足够大的空地落地后执行，第二处基地请换到至少 100 格外：

```mcfunction
/blacklight military base place military ~8 ~-1 ~8 0
/blacklight military base place blackwatch ~8 ~-1 ~8 0
```

门岗首位为队长，指挥楼有军官和基地指挥官。军方仍是 16 个有限驻军槽，黑色守望仍是 20 个；逐步生成、受区块加载和总人口预算限制。新基地默认由更高军衔人员指挥，你明确任命后保留你的选择。旧基地不会额外无限刷军官。

枪械、补给、八种枪/七种弹、65×65/65×81 基地、城市自然生成的完整测试沿用 [military.2 指南](MILITARY2_PLAYER_TEST_ZH.md)，安装时以本次 military.3 为准。

本轮自动检查覆盖服务器行为和保存；尚未把 Minecraft 客户端的动态穿模、抓取时的帽具及外套表现声明为通过。实机若有问题，记录实体类型、身份、穿装状态、动作与截图即可。
