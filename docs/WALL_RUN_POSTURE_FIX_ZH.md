# 墙跑身体贴墙修正（2026-10-18）

用户确认的问题是 RUN 过程中的姿势：应像在地面跑步，脚踩墙、躯干向外，而不是胸腹贴墙。松开疾跑转为攀爬没有问题。

## 修改

- 恢复客户端原有墙跑/攀爬切换规则及中英文教程，撤回上一轮误解需求而增加的墙跑保持规则。
- `WallSurfacePose.orientRun` 不再依据头—髋连线消除前倾、把躯干摆成平行墙面的姿势。将动作整体旋转 90°：动作 +Y 离地高度映射为墙面向外 +Z，动作 -Z 前进映射为沿墙 +Y，再按实际行进方向旋转。
- 第一轮客户端截图确实发现下半身进入地板：以实体脚下原点旋转会把后摆腿带到地板下。随后将髋部沿墙高度固定在绑定姿势的高度，绕髋部转换相对姿势；原动作的离地高度仍用于离墙距离，不再把地面高度基准一起翻转。
- 保留原有骨骼相对位置、蹬腿、摆臂及身体前倾；`clearSkin` 和最终接触检测仍平移整个人物避免穿墙。攀爬不使用新的 RUN 变换。
- 普通皮肤、装甲和武器层继续共用最终姿势。没有改变速度、攀墙物理、伤害或闪避规则。

## 验证

423 项单元测试全部通过，0 失败、错误、跳过。新增测试覆盖五种方向各 40 个周期采样点、所有骨骼的相对距离、四面墙和不同相机朝向、向下跑与皮肤平面间隙。日志：`E:/Blacklight-Wings-20261016/wall-pose-test.log`。

高度基准二次修正后，`WallSurfacePoseTest` 的 4 项定向测试通过，包含新增的髋部高度与完整步态周期中脚底不低于入口地面的断言。日志：`E:/Blacklight-Wings-20261016/wall-anchor-test.log`。上述 423 项是二次修正前的完整运行，不能混同。

扩大运行的 `wallTraversalTest` 实际包含 94 项移动与战斗测试，其中 85 项通过、9 项失败；墙跑/攀爬、翻越和墙面姿势用例未报告失败。不能把这次扩展套件写成全部通过。日志：`E:/Blacklight-Wings-20261016/wall-pose-gametest.log`。

失败用例（本次未修改这些战斗规则，也未为消除失败而放宽断言）：

- `petsConstructsAndMountedAnimalsAreExcluded`
- `bladeSpecialUsesDashOnceFromStandingAndSprintAndWhipReachesBehind`
- `adaptedMotionsKeepSingleServerHitAndRecovery`
- `eachWeaponHasTimedDamageAndCannotSwitchDuringSwing`
- `biologicalGrowthCostsOnceAndMutantsUnlockGenes`
- `heavySwipeHasOneServerImpact`
- `hunterAttackResistsInterruptionButRecoveryCanBePunished`
- `airborneHammerWaitsForLandingAndHitsOnce`
- `queuedComboLinksFromFollowThroughAndKeepsBothFullHits`

其中猎手测试仍断言旧的 7 点挥击和旧恢复时序，另有旧的通用生物排除及生命单位预期。其余失败需单独核对，尚未完成逐项基线对照，不归因为本次墙跑姿势修正，也不声称已解决。

客户端渲染验收使用独立临时存档。第一轮 `qa/wall-pose-frames` 暴露了入地缺陷，不作为通过证据。高度修正后的正面截图在 `qa/wall-anchor-frames`；随后修正测试观察镜头被追随镜头覆盖的问题，侧面截图在 `E:/Blacklight-Wings-20261016/qa/wall-anchor-side-frames`。

最终六张游戏帧（普通 RUN、CLIMB、装甲 RUN、另一跑步相位、横向和斜向）已生成，报告 `complete: true`。已查看普通、装甲、攀爬及横/斜向侧面截图，身体及双脚均没有进入入口地板；脚朝墙、躯干离墙。日志 `E:/Blacklight-Wings-20261016/wall-anchor-side-client.log`，客户端正常退出。它验证的是真实渲染器的指定姿势采样，不等于按键驱动的连续墙跑试玩；用户随后要求打开当前版本自行测试，已使用不带自动测试参数的启动脚本。

## 试玩后反馈：仍过于贴墙，是否与攀爬冲突

状态机 `WallTraversal.tick` 每次选择单个 RUN / CLIMB / CLING；`ClientWallMotion.modeFor`、动作选择器和姿势渲染使用该状态。本地角色不会被观察者用的服务器模式包覆盖。移动动作切换存在 2.4 tick 混合，但没有查到持续叠加攀爬的路径；试玩日志未开启动作明细，不能声称已排除运行时输入抖动。

仅播放 RUN 的侧面截图也存在身体过于前倾的问题，说明持续贴墙至少有独立于攀爬混合的原因：源墙跑动作自带深度前倾，直接转 90° 后仍接近平行墙面。此次在整体姿势上限制超过 35° 的前倾，以完整刚体旋转保留骨骼长度和四肢关系，再使最近脚底接触墙面；高度仍锚定髋部绑定高度。没有删除攀爬或改变输入规则。

二次试玩反馈后的定向测试：`WallSurfacePoseTest` 4 项、`TraversalMotionSelectorTest` 5 项、`WallTraversalTest` 7 项，共 16 项通过。姿势测试覆盖五种墙跑各 40 个阶段，要求躯干朝墙外、脚底接触、脚不沉入入口地板及骨骼长度保持。日志 `E:/Blacklight-Wings-20261016/wall-run-separation-test.log`。两处 GameTest 姿势断言同步改成新的验收条件，服务端套件尚未重跑。

修正后的六帧姿势验收 `qa/wall-run-stance-frames` 已完成，并查看普通和装甲侧面。随后新增实际按键测试（65 / 66）：不设置动作预览、不注入 WallPoseState，只向正常输入路径提供前进与疾跑按键，带利爪的角色实际沿墙运动。`qa/wall-live-input-frames/standalone-visual-report.json` 报告完成两帧：RUN 输入状态、接触状态和动作 `basic_wall_run_up` 一致，角色从 Y251 上升到 Y264.95；释放疾跑后 CLIMB 状态、接触状态和动作 `basic_wall_climb_up` 一致，上升到 Y253.40。已查看两张游戏截图。本次复测未出现 RUN 被攀爬覆盖，不能据此保证所有输入时序和其他服务器环境都不会触发异常。日志 `E:/Blacklight-Wings-20261016/wall-live-input-client.log`。

## 2026-10-18 收口复核

以上为各阶段历史记录。本轮修正了猎手攻击期间跳过轻击控制的路径，并核对旧测试的普通生物感染倍率、换形中断和升级资源预期；NeoForge 扩展独立服务端 145 项全部通过。最终双版本结果、实机范围与未验证项统一见 `HYDRA_RELEASE_SCOPE_ZH.md`。
