# 2026-10-28：组合服务端回归修正

目标：NeoForge 1.21.1。修正测试夹具，不放宽生产端的出生限额、伤害判定、AI 或原版生物清除规则。

## 发现与修正

- 完整 Closeout audit 初次运行 463 项，失败 34 项；本机全套复现相同结果。
- 旧母巢用例在同一个世界时间点手动调用母巢数千次 `tick()`。新的共享出生窗口按世界时间结算，因此人工循环无法释放下一窗口的名额。
- 该断言失败后没有移除远处的 FakePlayer，后续手工创建且未持久化的 Husk 靶子被原版远距离清除。诊断确认它们带有 `DISCARDED` 状态；不是取消了伤害断言来让测试通过。
- 母巢改用独立批次、实际服务端 tick、实体存储与运算就绪检查，并恢复出生规则、原始 SavedData、玩家、实体、监听器及自有区块票据。异步准备有单独的总超时余量。
- 用实际 `EntityJoinLevelEvent` 的守卫 UUID 统计出生，严格检查三波共 3–6 个守卫，以及重载后没有新守卫；正常行走离开局部统计框不再被误判为补刷。
- 明确用途的 Husk 战斗靶子设置持久化，保留真实导航、攻击、碰撞与伤害流程。生产生物的清除规则没有改动。

## 验证

- 母巢独立真实服务器回归通过。
- 全部组合套件：**All 463 required tests passed**，本机最终运行 `combined-r6`。
- 套件包含 standalone、wall traversal、hydra release、hive feeding、city、feedback、release audit、building biomass。
- 完整资源任务执行时另验证 17,931 份地图资源及 256 份动作的无损编码。没有改动动作品质。

本机详细日志：`E:/Blacklight-Full-Audit-20261028/combined-r6.log`。远端 PR 的 Closeout audit 另行运行同一组套件。
