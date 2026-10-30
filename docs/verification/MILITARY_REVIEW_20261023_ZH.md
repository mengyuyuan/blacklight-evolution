# 双阵营军事增量：独立审阅与处置

本阶段一次独立审阅，模型gpt-6-astra/high、新上下文、只读两端HEAD到工作树及全部预期新增文件；没有运行构建、修改Git或另派审阅者。结论With fixes：无Critical，3Important，2Minor。原基础硬直问题已先前修复，未把旧审阅作为本阶段新审阅。

## Important：同一修复通道内处置

1. `MilitaryFormationSystem` 忽略PATROL/REGROUP目标坐标，GUARD移动到新坐标后仍按旧中心48格过滤目标。回归：`guardCoordinatesEstablishANewFiringTerritory`、`patrolCoordinatesMoveTheActualPatrolCenter`、`regroupCoordinatesMoveToTheSpecifiedPoint`，要求实际指令接受、走到新据点，并实际守卫开火。
2. 16格三维步进与4格站位高差限制相冲突，原生可达长楼梯没有任何路径请求；XZ到达判定忽略楼层。回归：`soldierWalksALongNativeReachableStaircase`、`sameXZUpperFloorStillRequiresVerticalMovement`，先证明未改造原生导航可达，再检查实际步行。
3. 正式上级指挥员所在子队独立GUARD时，其他继承父MOVE的子队仍跟随静止的他。回归：`commandersGuardingChildDoesNotStopTheOtherInheritedMarch`，要求留守与其余16人行军同时成立，正式任命保持不变。

第一轮 `military-final-review-red.log` 70场景6失败，但新夹具在Neo随机远坐标把Double打印为科学记数法，原生命令解析器拒绝；这不是三项产品问题的有效RED。随后固定小数并断言实际指令成功；楼梯夹具再固定初始NoAI和原生onGround前提，避免下令前自行巡逻/尚无地面标记导致原生参考路径为空。

正确RED为 `military-final-review-grounded-red.log`：**18:06:38，70场景恰6项新增行为失败**；两种原生楼梯路径可达和所有实际指令接受的前提均已通过。没有将前提失败冒充产品回归。

修复：统一显式坐标的驻守/巡逻/集结中心与目的地；原生局部步进16格的站位允许相同16格高差，由原生导航决定可达性，抵达检查保留Y；按命令作用域挑选行进代行，与正式任命/通信资格分开，按tick和组织revision缓存UUID结果（不保存实体/世界引用）。`inspect`当前指挥员随行进代行显示，军衔显示仍按Minor保留。

Neo `military-final-review-green-candidate.log`：**18:09:09，39规则+71/71实际场景通过**，含六项复现及216压力。最终压力1310加速tick耗时7794.6988ms、6385次路径服务、peak8/min10/全部216步行>8。Forge独立同一回归随后记录；不据缓存前后不同轮次耗时外推普通服务器性能。

最新完整Neo记录改以 `military-final-review-green.log` 为准：**18:22:31，39规则+71/71实际通过**，压力1310加速tick为10218.2083ms、6374次路径服务、peak8/min10/全部216步行>8。上一段7.79秒是此前同源产品运行，不是本次最终测量。

Forge扩大到70场景后的初次运行有就绪时序与环境干扰。夹具已先等待实体区域ticking才计行军1050tick/放置驻军，保持原到达、人数、射击断言。随后检查失败世界 `level.dat`，确认Forge默认为 `minecraft:noise` 普通地下世界；离线解码保存的Anvil块，发现原本空气的 `726/-58/248` 和射击线 `138/-56/157` 等被天然砂砾填满，解释了堵路、视线丢失及指挥员丧失，而不是军衔/枪械周期故障。新增 `tools/prepare_military_gametest.py` 显式建立新的超平坦QA服，不改生产逻辑、不覆写旧世界。

最终 Forge `military-final-forge-flat-green.log`：**18:36:38，39规则+70/70实际通过，进程退出0**。已从最终 `level.dat` 实读并断言 `minecraft:flat`，记录在 `MILITARY_TEST_ENVIRONMENT_20261023.json`。这是在受控平坦场景的验证；不把修正QA世界条件描述成生产导航解决了所有自然地形。

三项Important均由上述六项正确RED→GREEN复现与两端完整回归处置；没有第二轮审阅。

## Minor：本阶段保留

- `inspect`当前没有军衔显示，只能看阵营/单位/人数及正式/当前指挥员UUID；仍可通过军衔命令与实体外观管理。查询补充军衔留待后续。
- 生命周期覆盖是“运行时unload清理但实体仍在世界”和“真实下界独立SavedData”，不是实际指挥官实体卸载→重载或实体换维度往返。没有据此判定产品生命周期逻辑有错；这两种真实生命周期回归仍缺证据，不宣称已测。

## 审阅未作结论的行为与本会话决定

- 客户端材质/UV/军衔可读性/动作：保留源码几何预览，标为未实机验收；代价是仍需玩家客户端检查实际观感。
- 广域复杂地形与自然生成队形布局：直接放置/平坦行军之外仍待抽查；普通楼梯属于本次修复范围；代价是广域地形中仍可能有局部导航/观感问题。
- 真人联机、整合包兼容、普遍20TPS：压力结果只属于所记场景，不外推；代价是部署到实际服务器前仍需试玩与性能评估。
- 最终安装包clean assemble、Forge重混淆、QA排除、动作与源码补丁：本阶段Task6实际执行后才宣称交付通过；代价是构建/核验失败就不能把中间包当最终产物。
- 车辆、直升机、超级士兵与完整战役：按已批准设计留到后续；代价是本包不含这些扩展。

不另发第二轮审阅。重要修复以正确RED→GREEN与完整回归确认，小问题和边界保持可见。

两端最终正常clean assemble与package_release --verify-only已通过。QA排除、253动作原样、Java65/61、Forge重混淆及82文件源码补丁双向检查均完成；交付路径与基线见主记录。未提交、推送或发布。
