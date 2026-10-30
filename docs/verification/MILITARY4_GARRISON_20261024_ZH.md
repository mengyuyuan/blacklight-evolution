# military.4 基地驻军修复与验证

## 当前目标与现场

修复军方/黑色守望大型基地在大量无关生物存在时保持 `issued0` 的问题。用户实机反馈位于超平坦 `Y=-61`，基地显示 `issued0/20 loaded0 lost0 MUSTERING`，周围有大量史莱姆；该反馈保留为失败事实。

本记录对应 NeoForge 1.21.1，分支 `feat/neoforge-1.21.1-blackwatch-squads`，核对 HEAD `cdf5eb15628594ca82ad9b35bd11d3d34993cfc0`。military.1/.2/.3 既有未提交源码保留。仅修改驻军服务、配置、专属真实 GameTest 与本记录；Gradle 由主代理严格串行运行。

## 已确认根因与 RED

原 `CityMilitaryService.maintain` 遍历维度中全部 `Mob`，使用普通人口配置 `cityMobLimit`，默认 80。无关生物达到上限后直接返回，连第一次发放都不执行。

2026-10-24 09:34 本地，主代理实际运行 4 个专属 MC 场景：2 条目标 RED、2 条原有契约通过。证据：`.superpowers/military4-qa/neo-garrison-red.log`。

- `unrelatedPopulationCannotConsumeFirstGarrisonIssue`：等待真实 tick，确认普通人口计数实际达到上限，再运行维护；预期 2，实际 0。
- `negativeYEmptySavedBlackwatchBaseMustersFromActualPlayerTicks`：真实 `CityMilitaryPlacement.place` 的 `Y=-61` 黑色守望模板；无环境人口时同位置主动维护成功发放 2 人，随后仅重置该测试基地，真实 SavedData 往返保存 `issued0`，加入已实际加载的 80 个环境生物，再加入真实 FakePlayer。31 tick 内只由已注册事件驱动，预期至少 2，实际 0。
- 两方 16/20 槽、死亡和卸载不重复、SavedData 往返的契约通过。
- 200 个真实已登记基地槽位驻军的有界额度与释放恢复对照通过。

第一次夹具错误单独保留在 `.superpowers/military4-qa/neo-garrison-initial-fixture.log`：同 tick 加入实体、或放在未强制 ticking 的区块外，不能作为实际人口已进入 `getAllEntities` 迭代的证据。修正为已 ticking 足迹内、等待 2 tick、检查实际计数。没有把这一夹具失败当成生产根因。

## 批准的实现决定

- 独立配置 `militaryGarrisonLimit`，默认 80，范围 0..200，作用于每个维度的自然/管理员放置基地。
- 只统计 SavedData 基地槽位 UUID 可解析为存活、已加载 `MilitarySoldier` 的集合，按 UUID 去重；无关生物、非基地刷怪蛋人员与仅带同名标签的实体不占额度。
- 保留全局 `population`、城市每世界人口开关、每 10 tick 最多 2 个新实体、军方 16/BW 20 个一次发放槽与既有 UUID。死亡/卸载已发槽不补刷；旧 `issued0` 基地在条件满足时继续集结，无须覆盖放置。
- 状态继续报告 `issued/loaded/lost/unloaded`，补充有效额度与配置、预算、玩家距离、区块未加载/未 ticking、岗位占用/实体碰撞原因。状态查询不请求新区块或创建实体。
- 不删除史莱姆、不自动修改玩家配置、不操作 PCL 或用户存档、不提交或推送。

2026-10-24 09:40，Forge 同组 4 场景也实际完成：无关人口首批发放和负 Y 玩家事件均 `actual=0`；新增的真实预算阻塞状态断言失败，实际状态仍只有 `Military [23600, 80, 0] issued 0/16, loaded 0, lost 0, unloaded 0 state MUSTERING`。生命周期场景通过。证据：Forge 目录 `.superpowers/military4-qa/forge-garrison-red.log`。主代理确认后，两端生产补丁按以上方案冻结。

实际新增状态 `muster` 值：`CONFIG_POPULATION_DISABLED`、`CITY_POPULATION_DISABLED`、`GARRISON_LIMIT`、`NO_PLAYERS`、`NO_NEARBY_PLAYER`、`POST_CHUNK_UNLOADED`、`POST_NOT_TICKING`、`POST_BLOCKED`、`POST_COLLISION`、`READY`、`COMPLETE`，并显示 `loaded_garrison 当前数/上限`。`COMPLETE` 表示一次发放槽已全部发出，不表示死亡驻军会重生。维持已有 UUID 认回先于实际实体碰撞检查，避免恢复索引时被实体自身占位挡住。

## 当前验证边界与唯一下一步

两端目标 RED、Forge 状态解释 RED 已确认。两端生产修复已冻结交给主代理；定向 GREEN、原 97 场景回归和普通服验证尚待实际结果，不得将本记录解释为修复验收通过。

唯一下一步：主代理严格串行执行定向 GREEN、原 97 场景回归及普通服事件/磁盘验收，并将实际证据补入本记录。


## 2026-10-25 主代理验证补充

此前“待GREEN”的段落保留为交回时状态。本轮两端先各12/12定向通过，最终全部111/111场景正常结束，包含4个准确驻军场景；Neo554/Forge551项JUnit零失败/错误/跳过。NeoCity、NeoWorld与ForgeCity普通服在80个真实无关Mob下仍首次发放2守军，原UUID落盘重启验证通过状态见主实施记录；不依赖删除史莱姆。实际日志在各端 .superpowers/military4-qa/，完整收集前还要严格核对普通服JSON。
