# NeoForge 1.21.1 发布失败定位

2026-10-13，基于 `93d1db6`。本轮只做定因、隔离运行与临时诊断，没有修改生产玩法，也没有将原有失败断言删除或改成通过。

## 结论

后续收口校准与完整复测已完成，见 [0.3.0 发布记录](RELEASE_0.3.0.md)。以下保留定位时的证据边界，并修正复测发现的描述误差。

上一轮的 33 项服务端失败不是 33 个已确认的游戏 bug。逐项追踪后，主要原因是旧用例仍采用早期抓取时长、伤害单位、目标种类和动作规则；另有蜂巢用例把测试开场时间当作捕食开始时间。不能为了让这些测试通过而恢复旧玩法。

两项模型单测指向同一个待验收问题：新刀臂模型与旧抓取/投掷资源姿态有重叠。它们没有执行游戏中的原生配对姿态和 IK 修正，所以还不能断言最终画面已经穿头，也不能据此忽略风险。

以下定位解释的是每个用例当前首先失败的断言。静态定位不代表该用例后续所有分支已通过；尤其是长循环的武器测试，在第一个形态失败后没有覆盖其余形态。完整套件目前仍保留上一轮的 **109 项 / 33 失败** 和 **272 项 / 2 失败** 基线，尚未完成正式版回归。

## 独立运行得到的证据

| 实验 | 观测 | 结论 |
| --- | --- | --- |
| 单独运行 `allAirSpecialsChaseDistantMovingTargets`，保留原断言 | 仍在 WHIPFIST、12 格目标处失败，选中目标为 -1 | 这项失败不依赖其它测试串扰；RC45 已明确取消鞭拳空中特技的远距离追击。 |
| 两个伤害用例仅增加日志，保留原断言 | BLADE_RUSH 接触 1 次、伤害 70、名义伤害 14；BLADE_CUT 命中 1 次、伤害 45、名义伤害 9 | 当前失败是未换算五倍生命单位，不是日志文字所暗示的重复伤害。 |
| 蜂巢单独运行，保留原断言 | 有时停在“捕获”，有时停在“抬升”；临时检查在测试第 55 tick 时，捕食实际只开始 5 tick | 固定开场 35 tick 不等于捕食进行到 35 tick；接触点本身在捕食第 20 tick。 |
| 蜂巢临时副本等待真实 `feedingStarted`，32 tick 后检查抬升、140 tick 后检查结算；总超时改为 600 tick | 抬升 1.259 格，无提前奖励；猎物移除、一次进食、两单位生物质、主体不移动、会话清理全部通过 | 该用例的首要问题是准备/阶段计时。延长的是夹具准备的总等待边界，阶段断言与奖励条件没有删除。 |

蜂巢较短的中间诊断版本曾因原有 220 tick 总超时而失败，不能算通过；最终有界版本才得到 `All 1 required tests passed`。该结果尚不能证明所有蜂巢并发、卸载和中断场景均正常。

日志：`build/diagnose-air-special.log`、`build/diagnose-damage-units.log`、`build/diagnose-hive-phase.log`、`build/diagnose-hive-relative.log`、`build/diagnose-hive-relative-bounded.log`。临时诊断源位于 `build/isolated-gametests/`，不进入发行源或动作资源。

## 33 项服务端失败逐项定位

“静态”表示已找到断言与当前实现/版本说明之间的具体差异，尚未把整项测试按新规则重写并跑通。

| 用例 | 首个失败的原因及处理方向 | 证据 |
| --- | --- | --- |
| `everyFormConsumesPassiveAndRewardsOnce` | 用完整演员动作时长判断奖励时间。当前 `completionTick` 是吸收结算点，早于演员收回动画结束；应分别检查结算和动作恢复。 | 静态：`ConsumeSequence.duration/completionTick`、`ConsumeSystem.finish` |
| `consumptionClaimsAndPaysOnlyOnce` | 猎杀者走大型攀附及专用吸收，仍按通用旧 `DURATION=46` 判断完成；专用完成点为 50 tick，演员尾段 71 tick。结束后猎杀者先进入遗骸状态，不会立即移除；该 FakePlayer 未应用生命单位属性，治疗仍应按其实际单位验证。 | 静态：`ConsumeSequence.large`、`HunterConsume`；RC24、RC40 |
| `petsConstructsAndMountedAnimalsAreExcluded` | 把铁傀儡和骷髅一概列为不可吸收；当前标签纳入铁傀儡，仍排除骷髅。宠物和骑乘保护应单独保留。 | 静态：实体标签、`ConsumeSystem.eligible`；RC16 |
| `bladeSpecialUsesDashOnceFromStandingAndSprintAndWhipReachesBehind` | 首次冲刺命中一次，实际 70 对比旧期望 14。后续横扫等分支尚未跑到。 | 日志实测；`CombatScale`；RC40 |
| `committedSpecialOwnsMovementUntilRecovery` | “只测试疾跑互斥”的循环同时输入新的跳跃按下沿；地面蓄力允许跳跃取消，产生跳跃位移。应将普通移动互斥与合法跳跃取消分开测试。 | 静态：`WeaponCombatSystem.movementInput/jumpOut`；RC20 |
| `whipPullsOwnerToHeavyVictimBeforeHolding` | 该夹具使用嗅探兽，体积达到大型目标门槛，优先选择 MOUNT，而不是普通重型目标的 WHIP_PULL。 | 静态：`ConsumeSystem.request`、`ConsumeSequence.large`；RC24 |
| `survivalRecipeAndGuidanceAreLoaded` | 要求旧病毒样本合成配方；RC18 已取消该配方并改用感染开局。 | 静态：资源目录；RC18 |
| `primaryAndRepeatedGrabThrowAtReleaseFrameWithoutRewards` | 只等待 12 tick 就投掷，此时仍在 CAPTURE；当前普通抓取是 26 tick，尚未进入可投掷 HOLD。 | 静态：`ConsumeVisual.captureDuration`、`ConsumeSystem.throwTarget` |
| `basicHammerHasContactSplash` | 对已换算生命的实体使用名义伤害上限，误判主目标受到重复溅射。应同时核对命中次数和换算后的扣血。 | 静态：`HammerImpactGameTest`、`CombatScale`；RC40 |
| `hammerKeepsSmallVictimLiftedUntilTheDownstroke` | 固定要求前 14 tick 保持旧高度、第 18 tick 才下砸；当前走原生演员/受害者配对场景，不再走旧固定下砸定位。最终连续对位仍需采样。 | 静态：`ConsumeSystem.pairedPlacement`、`ConsumeSequence.paired` |
| `adaptedMotionsKeepSingleServerHitAndRecovery` | BLADE_CUT 已实际命中一次；45 对比旧期望 9 使复合断言失败。其它循环动作尚未验证完。 | 日志实测；RC40 |
| `healthyFarAndInterruptedTargetsGiveNoReward` | 把 48 当作猎杀者满血，当前满血为 240；此外当前削弱门槛按原始最大生命判断，48 低于 50，本就允许直接抓。 | 静态：`ConsumeSystem.healthReady`、`CombatScale.originalMaximum`；RC16、RC40 |
| `normalEnemyNeedsWeakeningAndClaimsAreExclusive` | 要求满血普通僵尸必须先削弱；当前原始最大生命低于 50 的非 Boss 可直接抓。认领独占仍应验证。 | 静态：`ConsumeSystem.healthReady`；RC16 |
| `carryingCanSprintTurnAndThrowWithoutStoppingOrHittingVictim` | 等待 12 tick 就按 HOLD 的可移动状态测试，实际还在 26 tick 的抓取阶段，移动被占用。 | 静态：`ConsumeVisual.captureDuration/blocksTravel` |
| `heavySwipeHasOneServerImpact` | 期望剩余 13，实际剩余 65，正好是 (20−7)×5。 | 完整套件日志；RC40 |
| `fixedHiveFeedsOnceWithoutMovingBoss` | 用测试开场的绝对 tick 检查异步获取猎物后的捕食阶段；启动时机变化导致捕获/抬升误报。 | 原断言单独运行失败；以真实阶段起点计时的临时副本通过 |
| `infectionCreatesOneIndependentSpecies` | 用例寻找 `BlacklightHunter`；当前感染替换创建 `INFECTED_HOST`，并保留来源物种。 | 静态：`BlacklightInfectionSystem.convertHost`；RC16 |
| `biologicalGrowthCostsOnceAndMutantsUnlockGenes` | 仍期望生命强化后最大生命 24；当前一级强化是 (20+10)×5=150。 | 静态：`GrowthSystem.updateHealth`；RC40 明确列出 150 |
| `bladeImpalesWithRightArmThenConsumesWithoutDroppingVictim` | 总共等待 20 tick 就断言持有；当前抓取 26 tick。后续旧手掌锚点与吸收时长也需校准。 | 静态：`GrabInteractionGameTest`、`ConsumeVisual.captureDuration` |
| `heldClicksDoNotStartGroundSpikeOrClawAttack` | 12 tick 尚未抓稳；而且测试把主攻击也归入“必须继续持有”，与持有时左键投掷的输入规则冲突。 | 静态：`GrabInteractionGameTest`、`ClawCombatSystem`、GRAB_R11 操作说明 |
| `smallVictimsStayAtPalmHeightAndFollowTurns` | 12 tick 后要求鸡处于 HOLD，早于当前 26 tick。 | 静态：`GrabInteractionGameTest`、`ConsumeVisual.captureDuration` |
| `bladeThrowReleasesOnRightArmSwingWithoutConsumeRewards` | 刀臂仍按旧 20 tick 抓取后投掷；提前输入没有进入投掷阶段，随后的“出手前保留身体”判断落在错误阶段。 | 静态：`GrabInteractionGameTest`、`ConsumeSystem.throwTarget` |
| `airborneHammerWaitsForLandingAndHitsOnce` | 落地扣血仍按未换算名义伤害比较。 | 静态：`HammerImpactGameTest`、`CombatScale`；RC40 |
| `captureDoesNotPersistNoAiAndDamageRestoresMob` | 要求非致命伤立即取消抓取；RC49 明确保留已认领会话，死亡/移除才释放。 | 静态：`ConsumeSystem.hurt/protectReceiver`；RC49 |
| `utilityPowersCoexistAndResetWithNormalMode` | 用装甲属性增加至少 12 作为防御判据；RC29 已改为直接承伤系数 0.525，仍有减速和抗击退。 | 静态：`UtilityPowerSystem`；RC29；现有实际减伤测试已通过 |
| `whipGrabsAtTwelveBlocksButNotBeyondAndReelsInBeforeConsuming` | 将牛中心放在 12.25 格就当作超距；当前距离取视点到碰撞盒最近表面，牛的半宽使它仍在 12 格范围内。边界夹具应按包围盒摆放。 | 静态：抓取距离判定及牛碰撞尺寸 |
| `heldTargetFollowsWalkingAndTurning` | 断言比较旧 `GrabPlacement.anchor`；当前优先使用原生 `carryAnchor`。该假玩家循环只推进实体 tick，没有推进原生配对所用世界时钟。 | 静态：`ConsumeSystem.anchor`、`GrappleMotion.progress`；需真实 tick 下验证对位 |
| `whipCapturesSmallAndLargeBodiesAtTwelveBlocks` | 已通过持有断言，但“到达手掌”仍比较旧锚点，与当前配对轨道不一致；同样存在假玩家/世界时钟不一致。 | 静态：`CombatTravelGameTest`、`ConsumePlacementTrack.carryAnchor` |
| `queuedComboLinksFromFollowThroughAndKeepsBothFullHits` | 每次完整命中都与未换算名义伤害比较。连击命中窗口和次数应保留，单独核对单位。 | 静态：`WeaponCombatGameTest`、`CombatScale`；RC40 |
| `consumeCannotGrabOrQueueWhileReaching` | 要求抓取途中右键不能预输入；RC49 明确新增会话内排队、抓稳后再验证并吸收。 | 静态：`ConsumeSystem.consume/consumeQueued`；RC49，合入于 `db617a1` |
| `damageInterruptsAndReloadRestoresAi` | 同样要求普通受伤立即取消，与 RC49 相反；重载恢复 AI、死亡释放仍需独立保留断言。 | 静态：`ConsumeSystem.hurt`；RC49 |
| `allAirSpecialsChaseDistantMovingTargets` | 把所有空中特技都定义为远距追击；RC45 已将鞭拳空中特技改为悬停出招。 | 原用例单独复现；`CombatMotion.canApproach`；RC45 |
| `allAirOpenersChaseDistantMovingTargets` | 把普通空中左键也定义为远距飞行追击；RC45 已明确移除，以解决“人平移、动作不动”。 | 静态：`CombatMotion.canApproach`；RC45 |

## 刀臂两项单测：定位到资源姿态，还没有完成视觉定因

`MvpResourceTest.sizedBladeKeepsHeadClearanceBetweenGrabKeys` 和 `newConsumeFormsKeepTheirOwnMeshesClearOfTheHead` 都使用当前 `approvedProportions` 新模型尺寸。失败点是 `action/grab_blade` 起始帧及 `action/grab_blade_throw` 约 0.62 秒，顶点距头盒为 0。

这些测试直接对资源动画做骨骼计算。实际渲染的 `BlacklightRenderPose.sample` 还调用 `NativeConsumePose.actor`、回退时的 `GrabHoldPose.apply` 以及 `ConsumeGripPose.apply`：抓取/持有会采用原生配对姿态，投掷还可能进行握点 IK。因此不能将这两个用例算作两个独立穿模 bug，也不能只改阈值或缩小模型来消除红灯。

下一步应对最终渲染骨骼连续采样，覆盖进入混合的前几帧、持有、投掷 0.62 秒附近及收回；若仍重叠，修正相应肩/肘/手的姿态或 IK 约束。当前仍作为正式发布的视觉待验收项。

## 后续处理顺序

1. 按 RC16/18/20/24/29/40/45/49 已记录的规则校准测试，保留奖励恰好一次、命中次数、死亡/离线清理、目标认领等安全断言；不能以当前输出为唯一正确性依据。
2. 抓取/吞噬测试按阶段时钟运行；原生配对需要推进真实世界 tick，不能只自增 FakePlayer.tickCount。蜂巢等待已开始的捕食阶段，并设置明确的准备超时与清理。
3. 跑完整套件，检查修正首个断言后暴露的后续失败；再排查真正的跨夹具干扰。当前证据不足以宣布所有战斗/抓取流程已经通过。
4. 做最终姿态的刀臂检查及第一/第三人称人工验收；不回退新模型、不恢复史诗战斗依赖。

## 隔离复现工具

新增 `tools/isolate_gametests.py`：接受原 GameTest 方法名，复制测试到 `build/`，仅移除未选中方法的注册注解，保留原方法体和辅助代码；不改 `src/`。默认运行不经过此脚本。

```text
python tools/isolate_gametests.py allAirSpecialsChaseDistantMovingTargets
gradlew.bat runGameTestServer -I build/isolate-gametests.init.gradle -PstandaloneTest -PwallTraversalTest -PhiveFeedingTest -PreleaseAuditTest -PgameTestRunDirectory=build/diagnose-isolated
```

需使用 Java 21。读取日志中的实际 `GAME TESTS COMPLETE` 和测试结果；仅看到 Gradle `BUILD SUCCESSFUL` 不足以证明测试执行过。临时日志注入、阶段探针不由此工具自动加入，本文明确区分原断言复现与改动过夹具的诊断实验。

## 本轮默认构建复核

诊断完成后已移除临时 init script 的构建入口，重新运行 `assemble verifyStandaloneBoundary`，结果通过。`package_release.py --profile bundled --verify-only` 确认 251 份动作、607 个生产类、449 份生产资源一致；发行 JAR 中没有 GameTest 类。没有生成正式版标签或发布。
