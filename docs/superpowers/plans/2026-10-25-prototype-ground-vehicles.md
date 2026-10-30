# Prototype Ground Vehicles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development for independent tasks, or superpowers:executing-plans for inline execution. Keep production ownership explicit and execute one verified step at a time.

**Goal:** 完成军方/BW三类地面载具、玩家驾驶与抢夺、军官车队、有限炮弹/燃料及真实车库后勤，保留既有人员与阵营边界。
**Architecture:** 独立机械Entity+真实士兵乘员；纯规则域负责座位、控制、移动、库存、火力与车队，city只读基地门面负责有限派发和驻地；两个加载器分别适配注册/网络/保存接口。
**Tech Stack:** 当前Neo MC1.21.1/Java21、Forge MC1.20.1/Java17、JUnit5、实际GameTest/独立服务器、原生ModelPart/Blockbench源，不增加大型枪械/载具依赖。
**Spec:** [完整设计](../specs/2026-10-25-prototype-ground-vehicles-design.md)
**Research:** [实际来源与代码核查](../../verification/PROTOTYPE_VEHICLE_REFERENCES_20261025_ZH.md)

本文是待执行计划，所有checkbox初始未完成；没有运行车辆测试或产出车辆成品。任务开始2026-10-25，依据更新2026-10-26。

## Global Constraints

- 先完成military.5收尾并保存其独立证据。新载具称下一阶段，不复用M5“通过”来声称新功能已测。
- 本计划下生产写入以执行时确认的隔离工作树为准；当前路径是`F:/MC建筑/.blackwatch-worktrees/neoforge-military5`与`F:/MC建筑/.blackwatch-worktrees/forge-military5`。根代理决定是否另建隔离工作树，不在用户PCL实例直接开发，不覆盖旧交付包。
- 模组自身1.2.1由用户亲自拉取。禁止自动fetch/pull/merge，不把MC1.21.1、作者HEAD或当前0.3.4预览说成模组1.2.1。真实拉好后另做兼容评估。
- 不自动提交/推送。现有并行代理、原有QA和资料生成器由其当前负责人拥有；本阶段先新增独立文件，公共入口修改由根串行整合。Gradle只由根代理串行执行。
- 每阶段先提交失败测试给根确认逻辑RED，再写生产最小实现；先Neo适配，再验证Forge，不复制Neo专有类到Forge。
- 车辆为独立Entity；不吞噬/感染整车，不新建平民阵营，不改变现有盟友/合法敌人判定，不默认扩展玩家占车PvP。
- 不免费补弹/补燃料/发车/发乘员，不因未知或卸载认定已死，不强载未知区块，不跨维度取箱或寻家。
- 原作视觉目前未真正看图。必须补真实画面与模型对照；不可用图注、概念图或通用坦克替代原作参考验收。

## Review Focus

1. **实体语义与权限：** 机械目标如何被枪/超能力击中，却不会进入感染/吞噬/伪装；玩家炮击是否仍走真实玩家权限。
2. **资源与持久化：** 改弹种、抢夺、重登、失效驻地和未知UUID是否克隆车辆/乘员/炮弹，初始化是否只有一次。
3. **控制与网络：** 真实席位/租约/序号；两人同抢、旧司机包、跨维度射手、无炮手与取消路径是否残留控制。
4. **空间与预算：** 长车碰撞、转弯、炮口、车库进出与未加载地形；车队是否有界且不拖垮步兵预算。
5. **参考落实：** 原作画面实际看过什么，哪些几何/骨骼/纹理落实；实际游戏资产而非文档图是否通过。

## Paths and verification contract

下面Java路径均相对各工作树的`src/main/java/com/codex/blacklight/`；测试路径与资源另行写全。新类名是拟新增接口，不表示当前存在。

当前工程已经有JUnit5的`test`任务。后续新增三个独立QA init：

- `tools/military6-vehicle.init.gradle`：只在显式使用时编入`src/vehicleTest/java`与夹具，分别配置Neo/Forge的GameTest运行，实际production output以新staging目录运行。
- `tools/military6-vehicle-server.init.gradle`：只编入`src/vehicleServerTest/java`，读取明确`vehicleAuditDirectory`/`vehicleAuditPhase`，启动两次独立服务器，对同一份测试世界做prepare/verify。
- `tools/military6-vehicle-visual.init.gradle`：只编入`src/vehicleVisualTest/java`，实际加载生产模型与实体；不把客户端预览/QA类打入发行JAR。

实施者先创建/核对这些init中的参数契约，再执行下述计划命令；这些新文件当前尚不存在。每条命令在对应工作树执行、使用对应Java版本，实际路径与日志保存在本阶段verification目录，不能并行抢同一run/world。运行过滤器必须得到非零的指定测试数，skipped不算通过。

```powershell
.\gradlew.bat --no-daemon test --tests "com.codex.blacklight.military.vehicle.*"
.\gradlew.bat --no-daemon -I tools/military6-vehicle.init.gradle -PvehicleTest runGameTestServer
.\gradlew.bat --no-daemon -I tools/military6-vehicle-server.init.gradle -PvehicleAuditPhase=prepare -PvehicleAuditDirectory=build/vehicle6-audit runServer
.\gradlew.bat --no-daemon -I tools/military6-vehicle-server.init.gradle -PvehicleAuditPhase=verify -PvehicleAuditDirectory=build/vehicle6-audit runServer
```

两次服务器必须真实退出/重启进程，不能只做NBT对象往返或同进程假重载。每阶段只运行有关用例，稳定后进入下一步；最终再做一次两端完整回归，不无理由重复耗时测试。

## Task 0：冻结现场与补原作目视依据

Files: 本阶段三份设计/研究文档；新增`art/military-vehicles/REFERENCES_ZH.md`；执行时新建`docs/verification/MILITARY6_VEHICLES_20261026_ZH.md`和本阶段证据manifest。不要编辑M5验收记录制造新结果。

- [ ] 根确认military.5实际全回归、独立服务器与真实模型证据，记录两工作树Git状态、作者HEAD、未提交diff摘要和源码/资源哈希。不得把当前HEAD当成所有军事功能的提交。
- [ ] 用已可访问官方页面/原实机截图/公开视频正常原生展示，实际看见truck/APC/tank侧前、车体和炮塔关键轮廓。先从研究文档给出的官方Steam/Activision入口和辅助实机页查，不下载商业网格/贴图。
- [ ] 每辆写来源、证据等级、画面/时间点、目视观察、还未确认内容和拟改成MC的具体几何。卡车不能仅凭另一游戏的通用卡车凑数。原图看不到则明确该项阻塞，内核可继续，原作模型验收保持未通过。
- [ ] 审阅原作事实与拟定数值是否分开，用户明确范围是否全覆盖；不重复索要已授权的驾驶/实施许可。

Exit: M5与车辆工作证据分离，模型没有未经目视的“已还原”承诺。

## Task 1：纯车辆规则、库存与座位权威

Create:

- `military/vehicle/VehicleDefinition.java`、`VehicleCatalog.java`、`VehicleInventory.java`、`VehicleSeats.java`、`VehicleControlAuthority.java`、`VehicleHullShape.java`。
- `src/test/java/com/codex/blacklight/military/vehicle/VehicleCatalogTest.java`、`VehicleInventoryTest.java`、`VehicleControlAuthorityTest.java`、`VehicleHullShapeTest.java`。
- `src/main/resources/data/blacklight/military/vehicles.json`，只含有界拟定配置，不含原作宣称数值。

- [ ] RED：真实catalog入口分别断言三底盘、两涂装、唯一席位/挂点、未知ID拒绝、NaN/负值/超上限拒绝；真实库存断言错弹拒绝、上限截断、零弹拒绝、扣一发只减一发、保存冷却不重置。
- [ ] RED：纯控制权对象断言同席位排他、旧租约/倒序/重复包/超频/无乘坐/错误世界拒绝，5tick输入过期制动；不得通过包内玩家UUID冒名。
- [ ] RED：真实车身盒体对0/45/90度AABB、局部射线、炮口/座位转换与不缩小长车碰撞的几何边界；失败原因须为缺少规则，不是只缺依赖。
- [ ] 根运行JUnit确认RED；实现实际生产规则与有界codec，保留实体注册之外的纯层。
- [ ] GREEN同组测试；由独立审阅者检查初始化/库存数据是否可能同时拥有两个真相，三底盘挂点是否共源。

Exit: 纯层可复用、未知数据失败关闭，所有拟定值集中，不复制测试版算法。

## Task 2：独立实体、移动碰撞与真实模型

Create:

- `entity/MilitaryVehicle.java`、`military/vehicle/VehicleKinematics.java`。
- `client/model/MilitaryVehicleModel.java`、`client/renderer/MilitaryVehicleRenderer.java`。
- `art/military-vehicles/vehicle-authoring.json`、`tools/build_military_vehicles.py`；产物为可编辑`.bbmodel`、`assets/blacklight/military/vehicle-models.json`与服务器挂点数据。
- `src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/MilitaryVehicleGameTest.java`；`src/vehicleVisualTest/java/com/codex/blacklight/client/MilitaryVehicleVisualAudit.java`；独立QA init。

Modify: `registry/ModEntities.java`、实际客户端renderer注册入口；构建只加入显式新QA源集/排除项，不改原M5测试init。世界/服务器QA统一放`com.codex.blacklight.gametest.vehicle`，沿用发行JAR已有gametest整包排除；另显式排除`MilitaryVehicleVisualAudit*.class`及新测试结构，避免opt-in运行后的残留产物打包。

- [ ] RED实际实体测试：非LivingEntity、不可感染/吞噬/伪装；100/240/420耐久可受损，零耐久只销毁一次，不自动重生；生成默认弹药/燃料为0。
- [ ] RED地形夹具：7格直门、9格路、90度转角、斜转、1格台阶、2格门、3格深坑、水边、边界/未加载邻区；另测起止yaw都无碰撞、旋转中间长车角撞薄墙/门柱的场景。断言真车身位置/box、停止原因与区块加载数，不只看导航方法返回true。
- [ ] 根确认RED后实现保守旋转AABB与≤0.25格平移扫掠、车角弧长≤0.125格的分段旋转包络/误差膨胀、支撑采样、有限掉落/环境行为；不只测旋转终点，不从Mob继承假身份。
- [ ] 通过实际ModelPart生成与游戏加载三类底盘、军方/BW涂装、轮履/炮塔/炮管/舱门骨骼。原作视觉若仍未补齐，标记资产外观不合格，不能发布为原作还原。
- [ ] GREEN定向GameTest；实际客户端输出多视角和实体box叠加，确认尺寸、炮管、seat/muzzle挂点与车辆真实碰撞对齐。
- [ ] Forge按其实际Entity/renderer API做等价适配并运行同一业务测试；不要为方便另改车身尺寸。

Exit: 三车作为机械实体真实出现、移动且不穿墙；实际资产预览与来源映射可审阅。

## Task 3：真实乘员、玩家驾驶/炮手与网络

Create: `network/VehicleControlPayload.java`、`client/VehicleControls.java`、`client/VehicleHud.java`，必要的座位交互payload；`src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/VehicleSeatsGameTest.java`。
Modify: `network/ModNetwork.java`的各平台实际注册、`MilitaryVehicle`骑乘/存档入口、现有客户端按键接入；不复用超能力移动输入包作为车辆包。

- [ ] RED服务端行为：两个玩家同时占司机；司机/炮手/指挥席互换；枪手占座后司机无开火权；单人司机可操炮；非真实乘员/假UUID/远处/旧租约/NaN/超频包无移动或射击；真实AI无司机停车；座位job不改变MilitaryRole/军衔。
- [ ] RED乘坐士兵不会个人枪击、不遗失原unit/home/ammo；死亡/感染/眩晕/NoAI/被抓的真实crew不能驱车或操炮，crewOperational与因骑乘禁用的步兵tacticsAvailable分开；对同一玩家断线/下车/死亡/换维度立刻撤销输入，缺席UUID不生成复制乘员。
- [ ] 根确认RED后实现实际座位到passengerUUID映射、主线程排他换座、全新控制租约和输入超时；NeoCustomPacketPayload与ForgeSimpleChannel分别实现上下文验证。
- [ ] 仅客户端插值方向/相机，服务端决定速度与炮塔角；HUD读真实库存，主/副开火/换座/弹种动作可重绑且不会吞掉未骑乘时的超能力输入。
- [ ] GREEN实际服务端+两个客户端验证：多人换座、司机掉线、重进、第一/第三人称、载有士兵的实际车辆；记录实际输入延迟，不造FPS承诺。

Exit: 玩家真的可驾驶与占炮手席，没有隐形司机或客户端瞬移。

## Task 4：车载炮弹、机械目标伤害与方块权限

Create:

- `military/vehicle/VehicleAttackService.java`、`VehicleDamageBridge.java`、`VehicleThreatBridge.java`、`VehicleThreatGoal.java`、`VehicleWeaponDefinition.java`、`VehicleRegistry.java`。
- `firearms/VehicleOrdnanceProjectile.java`；新增自动炮/HE/AP弹、空底盘/燃料/维修件及对应配方、语言/模型资源。
- `src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/VehicleFireGameTest.java`与`VehicleDamageGameTest.java`。

Modify: `FirearmsCombat.java`和适用超能力实际命中查询的最窄机械目标桥；`MilitarySoldier`、`BlacklightHunter`/`BlacklightMutant`等实际拥有当前合法敌人的机械目标适配；`FirearmBlockDamage.java`仅在需要可显式射手/炮口参数时扩展，不删已有保护。

- [ ] RED炮口/弹药：炮管斜转后实际弹体起点与射线正确；舱内眼位不影响炮口；墙挡住炮膛/炮口不能穿墙；一次只扣一种真实弹药，零弹/冷却/错弹禁止，连点不会多发。
- [ ] RED命中/权限：同kind不同武器ID不串伤害；真实玩家炮击尊重拒绝事件/容器保护；AI遵守mobGriefing；友军/创造/旁观保护；玩家打载有真人的车不能绕过PvP/canHarmPlayer；卸载目标区块不加载；射手死亡或跨维度后的弹体不幽灵伤害。
- [ ] RED机械目标：步枪、火箭与适用重击/投掷能按设计伤害车；吞噬/感染/身份捕获永远排除整车；闭舱车体不重复伤害全员；毁车只一次掉落/损失。
- [ ] RED实际猎手/适用感染体与士兵面对当前规则已合法敌对的车内乘员，可以接近车身表面并攻击机械耐久，不追座位中心空挥；空车不因此新增主动敌人、友军/伪装继续保护。测试是真实AI与实体，不能只直接调用车辆受伤方法。
- [ ] 根确认RED后实现ShotContext、真实射手归责、显式武器ID弹体、有限候选爆炸/装甲矩阵和窄的机械威胁Goal；不能把玩家炮击传NPCowner来绕保护，不能`setTarget`强转车辆为LivingEntity。
- [ ] 配方按两个版本实际格式验证，输出都是空底盘/真实消耗品。只接受空底盘升级，不收纳已放置车辆导致库存复制；玩家近距离停稳后可真实转移匹配弹药/燃料，菜单错距离/错物品/超上限/伪造数值必须拒绝，不强制先造车库才能开空底盘。
- [ ] GREEN实际测试；客户端复核炮口/后座/弹道与HUD一致。记录离车后同世界存活的已发射炮弹仍按原射手归责，旧司机不能再发新炮。

Exit: 玩家的驾驶、武器与破坏是可用完整链，库存和权限没有捷径。

## Task 5：抢夺、驱逐/吞噬真实乘员和取消恢复

Create: `military/vehicle/VehicleHijackSystem.java`、`src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/VehicleHijackGameTest.java`。
Modify: `combat/ConsumeSystem.java`窄的已验证乘员入口/完成回调，`disguise/DisguiseSystem.java`窄的车辆敌对事件入口；现有排他/忙碌/目击规则保留。

- [ ] RED两个玩家同抢一车、重复请求、隔墙/过远/错席位/无能力/忙碌/玩家占有席位、目标中途死亡、车辆中途毁坏与世界卸载；动作已开始后车辆驶离、玩家退开、视线被挡/席位变化，在每tick或COMMIT必须取消，不能远程完成。断言只有一次控制移交和真实乘员结果。
- [ ] RED在受损、耗油、已开炮、装填冷却中抢车，断言前后同UUID/HP/各弹种/fuel/cooldown完全保留；旧司机/炮手包及AI本tick之后都不再能开火。
- [ ] RED驱逐活人保留其unit/home/装备/弹药，无安全出口不塞墙；吞噬必须通过现有healthReady/Assimilation及同一session，收益/身份只一次；抢夺取消释放三方锁。
- [ ] RED伪装无目击/有目击，分别遵循现暴露逻辑；伪装军官不能越权管理车库或编制。吞噬成功但占座失败不复活/复制司机。
- [ ] 根确认RED后实现VALIDATE/EXTRACT/COMMIT/CANCEL、持续接触核验、真实安全出口、窄Consume适配与新租约。驱逐时长不是吞噬完成条件，必须收到原session真实结果；原passenger全局拒绝默认保留，只有持有效抢夺上下文的入口可操作指定乘员。
- [ ] GREEN服务端测试+多人客户端实际抢车，验证第一/第三人称动作只是表示，服务器才做控制转移；动画取消不会留下无敌/鬼乘员。

Exit: 玩家真的能夺取AI车，能保留战损而不是生成新车；整车不是可吃生物。

## Task 6：普通兵的多武器与有限备弹迁移

Create: `military/MilitaryLoadoutProfile.java`、`MilitaryLoadoutInventory.java`；`src/test/java/com/codex/blacklight/military/vehicle/MilitaryLoadoutTest.java`；`src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/MilitaryLauncherGameTest.java`。
Modify: `entity/MilitarySoldier.java`实际主/副枪切换、单int备弹迁移与补给接口；生成/编组的最窄一次性角色入口。

- [ ] RED默认8/12人角色组成、配置权重拒绝负数/全零、按UUID/任务只初始化一次；军衔不因为换武器变化。
- [ ] RED老兵30发枪膛+90步枪备弹迁移后仍同量，换火箭副枪绝不出现90火箭；掉枪/空枪不重发；错弹箱不补；主副枪各持真实ItemStack。
- [ ] RED火箭安全距离、友军遮挡、受保护/伪装目标、空火箭近距用真实卡宾、导弹/温压/感染枪不进入普通随机池；不能只测一个mock的“可开火”布尔值。
- [ ] 根确认RED后实现角色组合、分弹种库存和有限换枪；归宿/编制/原生槽逻辑不动。非子弹伤害scale与现火力入口的差异须显式处理。
- [ ] GREEN纯规则/实际NPC战斗、换弹补给及旧档；原M5步枪/重装兵回归无新增免费资源。

Exit: 普通兵确实有可配武器体系，有限反装甲支援不变成全员无限火箭。

## Task 7：军官车队、道路预算与清剿任务

Create:

- `military/vehicle/VehicleFleet.java`、`VehicleFleetSavedData.java`、`VehicleFleetSystem.java`、`VehicleWorkBudget.java`。
- `src/test/java/com/codex/blacklight/military/vehicle/VehicleFleetTest.java`和`VehicleWorkBudgetTest.java`；`src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/VehicleFleetGameTest.java`。

Modify: 现有军事命令入口只增加显式vehicle分支或独立fleet命令；不把车写入人员members，不无审查更改旧`MilitaryOrder.Mode`序列。

- [ ] RED真实车辆+真实人员关联，1–4车编队间距、按慢车速度、无司机停车；军官手工任命保留，死亡后已加载高军衔临时代理，同级稳定；玩家占车优先。
- [ ] RED车队离原基地96格以外交战、耗弹、司机受伤；原基地卸载不瞬移/重发；斜路窄门、3次路径失败进入BLOCKED；未知区块不强载。
- [ ] RED32格清剿扫描只复用现有合法感染目标，不误打盟友/伪装/平民，不清全城数据；单位RETURN/PATROL/MOVE指令和车队任务对应明确。
- [ ] RED2次规划/tick、20tick同车间隔、2048节点/96格半径的实际计数器与公平轮转，不能只在测试构造另一个预算对象；步兵导航仍获自身预算。
- [ ] 根确认RED后实现独立FleetMission与有界路线；停车后真实下车，不复制士兵。持久化只存索引/任务，不另存一份车辆库存。
- [ ] GREENGameTest；实际3/12/24车有界压力（24为人工压力场景，默认自动预算12仍须限制派发），记录硬件、实体数、tick耗时和工作计数，无硬件无关FPS宣称。

Exit: 军官领导的车队可离基地巡逻/作战，失败安全停止，规模有明确预算。

## Task 8：可进出的车库、有限派发、补给维修与新驻地

Create:

- `city/MilitaryBaseAccess.java`、`VehicleGaragePiece.java`；`military/vehicle/VehicleGarageData.java`、`VehicleGarageService.java`。
- 独立`tools/build_vehicle_garages.py`、`src/main/resources/data/blacklight/military/vehicle_garages.json`及附属结构NBT；不要把当前13×13露天垫改名当成功能车库。
- `src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/VehicleGarageGameTest.java`；`src/vehicleServerTest/java/com/codex/blacklight/gametest/vehicle/VehicleServerAudit.java`。

Modify: `CityMilitaryData`/服务的窄只读门面及显式车库附属注册；原人员16/20派发槽、损失/归宿数据不复制或替换；必要的自然结构入口由根串行整合。

- [ ] RED31×27附属结构单独地形测量、3车位实际进出/停靠/炮管转向；旧基地附加预览拒绝冲突，不覆盖旧地块；生成失败不取消原基地。
- [ ] RED每车库3个一次性原生车辆槽，在载具开走/卸载/被抢/已死/重启后都不重发；只分配已有真实空闲人员，缺司机/炮手不生成人或覆盖手工命令。
- [ ] RED正确弹药/油/零件实际库存逐件扣除；车不在维修位、箱未加载/被拆、错阵营/失效基地不给资源；维修不能复活车；物品不足只接受真实余量。
- [ ] RED原核心毁坏后停车防御/到另一处已加载同阵营车库；无可达地时野外停车并保留战斗能力；不跨维度找家，不拉回玩家控制车，不改原生归属/损失。
- [ ] 根确认RED后实现注册结构+真实容器+有限账本；车辆实体库存为唯一真相，SavedData仅索引槽和任务。预留UUID未知时保留状态不重发，明确文件间保存非强事务限制。
- [ ] GREENGameTest+真实两进程prepare/verify，第二进程核对车/乘员UUID、余弹、HP、燃料、冷却、基地归属、损失、被取走箱物资。旧M5副本读取后没有自动生成车/车库/额外士兵。

Exit: 车库能供养真实车队，建筑认证依据和派发名额有可查询数据，不是凭空刷新机制。

## Task 9：双端整合、实际客户端验收和制作资料

Create/update: `docs/verification/MILITARY6_VEHICLES_20261026_ZH.md`、本阶段可复核证据manifest、制作资料目录的车辆/弹药/兵种/车库数据卡、`docs/verification/MILITARY6_PLAYER_TEST_FLOW_ZH.md`。在已有资料索引加新阶段链接时与现资料负责人串行，不擅改M5生成器。

- [ ] 独立审阅Spec+Standards：五项Review Focus逐条检查，需修复者先形成实际失败用例再改。不得把未看的原作图片或未运行测试写成完成。
- [ ] Neo/Forge分别执行新增JUnit、定向GameTest、现M5完整回归、普通发行构建及两个进程存档验证。检查JAR无vehicleQA类/测试结构、含实际运行模型和新配方。
- [ ] 实际客户端验收三底盘两涂装、空车上车/换座、司机/炮手、射击/补弹、抢夺/驱逐/吞噬、军官死亡、旧基地失效与另一车库补给。检查走出基地的车队仍保存损伤弹药。
- [ ] 易懂流程按“新测试世界→放车/补给→驾驶开火→AI车队→抢夺→破坏基地→退出重进”编写，命令从生产注册实际导出，不能预先编造命令或实体ID。说明现有世界不会自动翻新旧基地。
- [ ] 资料卡记录真实实体/物品ID、模型源与几何映射、尺寸/耐久/速度/席位/弹种/上限/配方/派发/维修/失效规则；来源与拟定值分开。贴出真实游戏截图而非仅文本或概念图。
- [ ] 记录所有实际失败尝试及修复，不只报最终绿灯；标清硬件与压力测试条件、尚存限制（斜道保守碰撞、未核实原作参考、尚未拉的模组1.2.1）。根据通过证据打新交付包，不覆盖M4/M5包。
- [ ] 用户拉好1.2.1后，指导其在干净独立目录保留旧军事改动/包，提供实际路径与`git status`/tag所指SHA；再只读对比该版本真实注册、网络、city/Consume接口，制定迁移补丁。该步骤未发生前不宣称兼容，不替用户执行拉取。

Exit: 新车辆功能的代码、资源、资料与验证互相对应；用户能用交付包按流程复测；需要用户进行的1.2.1拉取另有明确指导。

## Self-review checklist for this plan

- [ ] 三底盘、两方外观、真实军官/乘员、空车驾驶/炮手/抢夺、有限弹/油/维修、车库、清剿、归宿、保存、两个加载器都有任务归属。
- [ ] 所有新类都写为拟新增；检查真实现接口可见性与版本签名，不照抄本文猜测方法名当已编译API。
- [ ] 原作目视/模型门槛有实际退出条件；库存、权限、双司机、卸载误死与预算都有生产入口级RED。
- [ ] 数据名词一致：origin不等于current home，初始槽不等于持续驻车上限，车队不等于人员单位，吞噬乘员不等于吞噬机械。
- [ ] 没有要求自动提交、推送、fetch、拉标签或在用户现存世界直接生成/覆盖建筑；实现与研究阶段的完成声明分开。
