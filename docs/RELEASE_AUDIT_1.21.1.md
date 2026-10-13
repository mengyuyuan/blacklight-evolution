# NeoForge 1.21.1 正式版收口审计（2026-10-13）

## 结论与范围

**尚不能发布正式版。** 编译、独立边界和动作打包校验通过，但完整单元测试仍有 2 项失败，服务端 GameTest 仍有 33 项失败。保留失败断言，没有跳过或把这些失败改成警告。

本次基于 `e664451`，包含 `origin/main` 的新武器/装甲模型及本地技能教程、极高空俯冲更新。修订分支为 `fix/neoforge-release-audit`，版本仍为开发版 `0.3.0-standalone-dev.20`，没有创建正式版标签或 GitHub Release。

覆盖方式：全量生产代码清单与静态风险检索（341 个 Java 文件、32,037 行）、382 个资源 JSON 解析、251 项动作文件的长度/SHA-256 校验、完整 JUnit 和现有独立服务端 GameTest；人工重点追踪网络输入、持久化、战斗/抓取/吞噬、移动、巨型感染体、地形破坏、客户端缓存/特效和打包边界。这不是逐行人工证明，也不等同于多人实机、画面和所有整合包兼容性验收。

Forge 1.20.1 仅同步动作资料，不改玩法代码；独立资源 PR：<https://github.com/mengyuyuan/blacklight-evolution/pull/21>。该 PR 的 GitHub Build 和 Version policy 已通过，尚未合并；本次未重做 Forge 游戏回归。

## 已修正的实际问题

| 问题 | 修正与验证 |
| --- | --- |
| 默认仓库动作包缺少 27 份大招/子弹俯冲动作，干净构建与本机外部资料构建不一致 | 两版补齐主体、蓄力、飞行/落地、瞄准、收回动作；更新清单；保留各分支的装甲数据。NeoForge 默认构建也强制检查完整阶段，251 项哈希一致。 |
| 极大攻击半径让空间索引遍历大量空坐标，服务端工作量随空区域增长 | 大范围改为遍历已加载实体，保留实际部位碰撞和谓词过滤，没有限制俯冲高度/威力；十亿格范围 GameTest 通过。 |
| 远距离可见性射线可能同步获取沿途缺失区块 | 射线只读已加载区块，缺失地形作为遮挡；百万格射线测试确认目的区块仍未加载。 |
| 演化点、生物质、警戒值加法可能先溢出再钳制，正奖励反而归零 | 先以 long 相加，再按各自上限钳制；边界回归通过。 |
| 飞行/鞘翅/骑乘输入在 tick 拒绝前仍能写入蓄力边沿 | 输入入口先拒绝这些状态；独立服务端飞行输入回归通过。 |
| 极端地形半径在 `radius + 2` 时发生 int 溢出 | 加法前转为浮点，避免范围变负。 |
| 非法爪动作 ID 被当作切换爪子，合法 ID 0 又误报未知指令 | 显式处理 0，拒绝未知 ID；12 个合法 ID 和异常边界测试通过。 |
| 快速切换教程时，多个已失效的 GIF 解码可能并行占用内存 | 串行后台解码、关闭页面取消标志、每帧分配前尺寸检查；27 份教程均能在既有预算内解码。 |
| 墙面动作缓存保留过期实体条目，并可能在退出后仍引用旧世界 | 客户端 tick 清理过期条目，退出立即清空世界引用。 |
| 发布预设的蓄力跳参数仍是旧值 | 与当前独立版默认值一致：0.55 / 1.25 / 24；不改用户现有配置。 |
| 同时开启三个 GameTest 集时重复加入结构资源，任务在测试前失败 | 合并为一次测试资源目录注册；增加独立运行目录参数和审计测试开关。 |

## 测试校准与剩余发布阻塞

已校准有代码证据的旧断言：移动升级倍率、警戒与伪装外观分离、新模型部件名/锚点、原生攻击接触窗口，以及冲上墙顶的 `TOP` 状态。删除的是无生产调用的 `WeaponMotionProfile.sourceTime` 遗留重定时方法及其专属测试；实际播放走 `MotionLibrary`，没有删除运行时动画路径。

### B1：刀臂与旧抓取/投掷姿态兼容性

两个 `MvpResourceTest` 失败：`sizedBladeKeepsHeadClearanceBetweenGrabKeys` 和 `newConsumeFormsKeepTheirOwnMeshesClearOfTheHead`。

按新模型 `approvedProportions` 的实际尺寸计算，旧 `grab_blade` 起始姿态、`grab_blade_throw` 约 0.62 秒的部分顶点落入头部盒，测得间距 0。该检查只采样资源姿态；当前抓取还经过 `NativeConsumePose` 替换/混合、`GrabHoldPose` IK，因此**不能把这个结果直接当作最终游戏画面已复现穿头**。正式发布前应对最终骨骼姿态做抓取、持有、投掷、收回的连续采样及第三人称验收，再决定重定向哪个动作。没有为通过测试缩小已确认的新模型。

### B2：历史服务端用例与当前玩法尚未全部对齐

最后一次完整运行 109 项，76 项通过、33 项失败。已修正墙顶状态识别后，原来的 2 项跑墙失败通过。其余失败不能直接称为 33 个实际 bug：

- 已确认旧规则冲突：普通受伤应中断抓取/吞噬的断言，与 `ConsumeSystem.hurt/protectReceiver` 当前保留已确认演出的规则相反。
- 明显需要复核的旧常数：伤害/生命规模、旧配方、成长收益、抓取/投掷固定帧数及手掌位置。必须按玩法和资源核对，不能只按当前输出改期望值。
- 尚未独立定因：移动期间动作互斥、远距离空中追击、蜂巢捕获、感染转换，以及若干抓取/投掷结果。完整套件会并发运行邻近结构，广域技能与实体可能串扰，需隔离夹具后复现。

完整失败名称和断言见同目录 `RELEASE_AUDIT_1.21.1_FAILURES.md`。当前保留这些失败作为正式版门槛。

额外做了目标查询实现对照：用审计前 `HEAD` 的 `CombatTargeting.java` 替换编译输入，保持其它代码和动作资料相同，运行原有 106 项服务端测试，仍是相同的 33 项失败，没有新增/消失的失败名称。这排除了这次目标查询性能修正对该失败集合的影响；不代表其它问题已经定因。对照源只在 `build/` 中，未覆盖工作区生产源。

### B3：正式发布验收未完成

- 两个真实客户端与独立服务端：同时大招/俯冲、观察者动画与锁定、死亡重生、换维度、断线重连、设置同步。
- 老存档升级后感染、成长、技能购买、伪装、装甲、临界质量是否保留；禁止用测试世界代替用户存档验收。
- 第一/第三人称武器及新装甲、蓄力/落地连续性、抓取全流程、F5/自由视角、资源重载及退出重进。
- 极高空破坏开启/关闭、服务器地形任务预算、保护事件、掉落实体和长期 TPS；本次三项定向 GameTest 不代表已经做过多人压力测试。

## 证据与复现

以下路径相对本工作区，日志不作为生产资源发布：

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 起始 JUnit | 268 项，12 失败、5 跳过 | `build/release-audit-baseline.log` |
| 补齐动作与修正后的 JUnit | 272 项，270 通过、2 失败、0 跳过 | `build/release-audit-final-unit.log` |
| 独立边界、assemble、动作哈希 | 通过；251 项一致 | 同上（`--continue` 保留测试失败状态） |
| 首轮完整 GameTest | 109 项，35 失败 | `build/release-audit-gametest2.log` |
| 修正墙顶测试后完整 GameTest | 109 项，33 失败 | `build/release-audit-final-gametest.log` |
| 审计前目标查询实现对照 | 原有 106 项中同样 33 项失败 | `build/release-audit-targeting-baseline.log` |
| 本次新增服务端风险回归，隔离运行 | 3/3 通过 | `build/release-audit-isolated.log` |
| 全量文件/资源清单及风险检索位置 | 341 Java；382 JSON 均可解析 | `build/release-audit-inventory.json` |
| 默认发行包完整性 | 251 项动作、607 编译类、449 生产资源、1458 源码/资料文件一致；QA/私有特效排除通过 | `tools/package_release.py --profile bundled --verify-only` |

使用 Java 21，在仓库根目录执行：

```text
gradlew.bat test verifyStandaloneBoundary assemble --continue
gradlew.bat runGameTestServer -PstandaloneTest -PwallTraversalTest -PhiveFeedingTest -PreleaseAuditTest -PgameTestRunDirectory=build/release-audit-server
gradlew.bat runGameTestServer -PreleaseAuditTest -PgameTestRunDirectory=build/release-audit-isolated
```

新增手动 `Release audit (NeoForge 1.21.1)` 工作流，分别运行完整单元/打包检查和完整服务端测试，失败时仍保留报告。现有日常 Build 只编译打包，绿灯不能替代这两项发布门槛。本机完成不代表已经在 GitHub 上运行过新工作流。

## 后续收口顺序

1. 隔离 B2 中未定因的失败；逐个修正实际问题或有证据的旧夹具，完整回归清零。
2. 验证 B1 的最终渲染姿态，修正后保留连续动作防回归测试。
3. 执行 B3 多人与存档/画面验收；之后才冻结版本号、生成正式发布清单。

不以旧 `AUDIT_REPORT_ZH.md` 的历史“已修复”结论、单独编译成功或单机试玩，替代本次正式版验收。
