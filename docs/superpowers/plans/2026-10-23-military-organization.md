# 双阵营编制与军衔 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 用户已批准完整设计并将细节交给实现者；会话内实施，阶段结果写入文件，最后一次独立审阅。不自动提交。

**Goal:** 常规军方和黑色守望具备可变人数单队、军衔、班排连/中队大队、继承命令、持久编制和相应设施。

**Architecture:** 纯 Java 组织模型负责不变量、任命和命令继承；共享军事实体负责两方战斗与军衔同步；每维度 SavedData 与运行时协调层衔接指令、有限导航和原 4 人火力分组。客户端模型和数据设施按阵营分开。

**Tech Stack:** 原 Java 21/NeoForge 1.21.1 与 Java 17/Forge 1.20.1，JUnit、GameTest、原版 SavedData/Brigadier；无新运行依赖。

**Spec:** `docs/superpowers/specs/2026-10-23-military-organization-design.md`（已批准）。

## Global Constraints

- 一名队长的叶子队伍容量 1–32；内部火力组 4；父节点最多 8 子单位，深度最多 4，单树最多 256 实员。
- 默认班8/排24/连72/军方大队216；黑色守望战术小队8/中队24/大队72，可缺员/调整，不自动补兵。
- 同阵营组织、双方未感染成员友好；原伪装与抓取/硬直/射线仍有效，不授予玩家服务器权限。
- 原 Blackwatch ID、NBT、24/60生命/4/10甲、1.6/2.2伤害、64/76周期保留；军方20/48生命、2/6甲、1.4/2.0伤害、72/84周期，瞄准16 tick、许可33 tick。
- 每维度每 tick最多8新路径、4单位发现；单兵寻路≥10 tick、单位发现≥20 tick；只用已加载区块。
- 军衔/单位是模组扩展，原作证据边界见调研。两端分别适配/验证，未提交，不推送或发布。

## Review Focus

- 指挥官被抓取/硬直、卸载而非死亡：代行可用且恢复后归还正式岗位，不永久升衔。
- 既有驻军已自动归队后再 organize/assign：原组织和新树原子变更，不出现幽灵成员或双重归属。
- 军方16 tick瞄准：许可必须覆盖第32 tick末发，不能沿用黑色守望29 tick提前失效。
- 跨叶子/上层情报：原时间戳不续期；接收者仍有效伪装时不被识破，友军在开火前进入射线仍安全。
- 大编组慢路径/无法加载目的地：有界公平处理，不传送、强加载或永久饥饿；压力记录不泛化为20 TPS保证。

## Task 1: 平台无关的编制、军衔和公平预算

**Files:** 新建 `src/main/java/com/codex/blacklight/military/{MilitaryFaction,MilitaryRank,MilitaryUnitType,MilitaryOrder,MilitaryOrganization,MilitaryWorkBudget}.java`；`src/test/java/com/codex/blacklight/military/MilitaryOrganizationTest.java` 和 `MilitaryWorkBudgetTest.java`。

**Interfaces:**
- `MilitaryFaction` 为 MILITARY/BLACKWATCH；`MilitaryRank` 为 PRIVATE/CORPORAL/SERGEANT/LIEUTENANT/CAPTAIN/MAJOR/COLONEL/GENERAL，稳定小写 id。`MilitaryUnitType` 为 TACTICAL_SQUAD/SECTION/PLATOON/COMPANY/SQUADRON/BATTALION，叶子前两种，层级值0/0/1/2/1/3。
- `MilitaryOrder(Mode mode, Point target)`，Mode GUARD/PATROL/MOVE/REGROUP/RETURN；`Point(double x,double y,double z)`。空 order 表示继承，target可按模式使用单位原点；拒绝非有限位置。
- `MilitaryOrganization.create(UUID,MilitaryFaction,MilitaryUnitType,String,int capacity,Point origin): boolean`；`assign(UUID unit,UUID member,MilitaryRank): boolean`；`attach(UUID parent,UUID child): boolean`；`detach(UUID child): boolean`；`appoint(UUID unit,UUID member): boolean`；`rank(UUID member,MilitaryRank): void`；`removeMember(UUID): void`；`order(UUID,MilitaryOrder): boolean`（null清除）。
- 查询 `unit(UUID): Optional<UnitView>`、`units(): List<UnitView>`、`leafOf(UUID): Optional<UUID>`、`allMembers(UUID): List<UUID>`、`actingCommander(UUID,Set<UUID> available): Optional<UUID>`、`effectiveOrder(UUID): Optional<MilitaryOrder>`。UnitView是不可变快照，包含id/faction/type/name/capacity/origin/parent/children/members及rank/commander/order。`copy()` 深复制供命令事务草稿；恢复可通过相同不变量重建，不暴露可变集合。
- `MilitaryWorkBudget` 按gameTime重置token，导航/发现请求按UUID公平排队、合并重复请求；每tick返回可执行UUID，具体方法签名由实现者写入验证记录再被Task4使用。

- [x] 先写纯行为测试：人数1/8/12/32及欠员、32边界；重复/跨叶归属拒绝；各层预设、混阵营/循环/过深/超子单位/总人数拒绝且无部分变化；正式/代行军衔排序及恢复；命令覆盖/继承；不可变快照/复制隔离；预算8/4、重复合并、20/10间隔和公平无饥饿。
- [x] `test --tests 'com.codex.blacklight.military.*'` 确认因缺少新类而RED；实现后相同测试GREEN，记录接口与结果。

## Task 2: 共享军事实体与双阵营军衔表现

**Files:** 新建共同实体/军方实体与profile；修改旧 BlackwatchSoldier、ModEntities/ModItems、client model/renderer、DisguiseIdentity/System、ConsumeControl/ImpactControl 的类型边界；按实际需要提取旧协调器，保留原测试查询兼容入口。

**Interfaces:** 共同实体提供 `faction(): MilitaryFaction`、`rank(): MilitaryRank`、`setRank(MilitaryRank)`、`unitId(): UUID`（可空）、`setUnitId(UUID)`，原 `heavy()/phase()/home()/validTarget()/tacticsAvailable()/suspendTactics()` 语义保留。阵营/兵种决定profile，军衔不改战力。

- [x] 新增真实GameTest覆盖军方/黑色守望属性、瞄准/周期/末发、双方友军/感染敌人、军方身份可捕获与伪装、真实抓取/硬直暂停。先RED再接入生产逻辑。
- [x] 抽取共同AI，旧ID与旧NBT可加载；新军方注册2实体+刷怪蛋，军衔NBT/synched data默认PRIVATE、未知安全处理。
- [x] 统一友军和控制入口，伪装只复制军衔外观、不复制单位树/权限。保留Blackwatch原火控回归和29场景。
- [x] 模型按两方轮廓/色调与8级几何徽记区分，简单动作；客户端编译与源几何预览，实际观感仍标未知。

## Task 3: 组织持久化、自动归队与实际管理命令

**Files:** `military/MilitarySavedData.java`、`MilitaryOrganizationSystem.java`、`command/MilitaryCommands.java`；平台注册与命令入口；GameTest扩展。

**Interfaces:** 每维度获得organization；`ensureMember(MilitarySoldier)` 自动同阵营可见16格归入8容量叶子或新单位，`organization(ServerLevel)`、`assign/organize`原子草稿事务；实际执行后同步已加载实体unitId/rank，markDirty。SavedData只含UUID/值，临时实体视图不持久化。

- [x] 先写保存读取、旧键/无效枚举、成员双归属、卸载/恢复与跨维度、正式/代行、命令权限/失败原子性等实际回归并RED。
- [x] 按两端SavedData API实现编码和恢复，单位保存一次，实体只留rank/unitId；死亡与卸载分开。
- [x] 实现spec示例指令，提供create可选capacity、resize、unassign/detach等必要维护入口；organize按预设构建欠员树，从已归队现有实体迁移，不生成新实体。
- [x] 实际Brigadier执行验证单队12/32、24/72组织、任命/军衔、查询、错误输入无部分写入；GREEN。

## Task 4: 层级行军、警戒与有界路径工作

**Files:** `military/MilitaryFormationSystem.java` 与Task1 budget、共同实体/旧火力协调器；大编组GameTest、压力opt-in标记。

- [x] 先写12人一队长多火力组、24/72分层行军/集结、父命令继承和子队覆盖、指挥官失能代行、节点距离64/TTL100、非对称伪装共享及末刻友军挡线回归。
- [x] 巡逻/行军中心和层级疏散槽位接入原生导航；原站岗48边界保留，行军中心跟随有效指挥官；不会因重新编制跳过换弹。
- [x] fire cell最多4，成员仅限同叶子单位；军方许可33；上级警报保留原快照和时间，逐接收者合法性检查。
- [x] 实际每维度budget统一发放路径/发现执行权，不能在旁路直接额外createPath。测试超预算公平、目的地区块未加载不请求新chunk、不可达重选。
- [x] 完成标准8/12/32单队与24/72场景GREEN；216实体另opt-in运行并保存真实测量和限制。

## Task 5: 两方自然设施、说明和独立Forge适配

**Files:** 原黑色守望设施脚本/模板加入rank/unit部署；新增军方营地/检查站NBT、结构/池/集合/群系数据，语言与指南；对应Forge文件逐项适配。

- [x] 真实设施先RED：阵营、军衔、人数、支撑/箱子、结构注册和间距/单向排除规则。
- [x] 保留Blackwatch 5/3，军方营地8/检查站4；独立军方40/16生成、单向排除Blackwatch8区块，复用电路/补给链，脚本确定性重建。
- [x] Forge保持其平台注册/附件/同步字段、SavedData、配方和目录；不整文件覆盖平台差异。两端分别GREEN全部定向回归、216压力证据（至少一个平台实际压力，另平台标准大编组），明确任何未验证差异。
- [x] 更新实际使用指令、军衔/编制预设、源几何预览、原作证据与设计分界。

## Task 6: 最终审阅、正常安装包与接续记录

- [x] 一个新上下文独立审阅当前双阵营全部增量，对照Review Focus；重要问题一次RED→GREEN修复，minor保留可见，不用第二次审阅代替测试。
- [x] 两端正常clean assemble；最终重混淆/Java字节码/QA排除/253捆绑动作文件/新资源ID核验。
- [x] 可安装两平台JAR、GeckoLib说明或匹配依赖、SHA256、源补丁前向/反向校验、源码快照与验证日志；不自动提交/推送/发布。
- [x] 更新 `docs/verification/MILITARY_ORGANIZATION_20261023_ZH.md`，原基础记录指向该记录，保留原15+29结果及扩展前快照；列明未验证客户端、广域地形、整合包/真人联机。

## 自检与执行方式

各任务消费前项显式接口；Task1纯值可在Java17运行，Task2暴露共同实体供Task3/4，SavedData与预算不持有世界/实体强引用。Task4产生实际编队后才修改自然设施，避免未实现行为被包装为战利品/美术交付。用户已经批准完整设计并授权自主细节，直接执行，不再次请求执行方法确认。原基础重要问题已一次修复，两端15+29通过；新设计是下一阶段而非重做旧成果。

## Task4 接入笔记（实现前决定）

- 新 `MilitaryFormationSystem` 负责组织命令落地、位置快照警报、导航工作队列；沿用 `MilitaryOrganizationSystem.budget(level)`，所有状态只存UUID/位置/时间，注册同一平台生命周期清理。
- 用 `MilitaryGroundNavigation extends GroundPathNavigation` 覆盖最终的protected `createPath(Set<BlockPos>,int,boolean,int,float)`，将原生内部重算也纳入许可。无许可时排队并返回null或可复用路径；仅导航服务在领取token后以try/finally作用域允许一次规划。这样不只给外层API计数而漏掉原生recomputePath。公共BlockPos入口先检查已加载，避免GroundPathNavigation先读未知目标。
- 导航服务每tick最多8个规划、同兵≥10tick；远目的地按≤16格的安全局部步骤推进，站立点先验证加载/边界/支撑/两格空间/流体，必要时在已加载地形用heightmap，不传送、不强加载。Neo的实际PathNavigationRegion源码使用getChunkNow；Forge另核对并实际测未加载目标。
- 把旧协调器所有显式createPath移入上述队列；巡逻选点只做廉价支撑/视线检查，规划失败后有限重选，不能再一次尝试8条路径绕过预算。记录实际规划计数、每tick峰值与成员间隔，供真实场景断言。
- 组织系统补已加载军事NPC的16格空间桶（值仅UUID，出生/移动更新，移除清理），供正式自动入队与未初始化火力组找附近成员；不遍历全维度。正式入队仍最多4次发现/tick，已知单位内部火力组按≤32成员名册查找，不反复做全场实体查询。未拥有单位且NoAI的静态NPC不自动招募；已有编制的失能成员仍保留名册。
- 不为了元数据初始化暂停旧射击：初次未登记时可保持原火力组；得到formal unitId后绑定或分离不同叶子的成员。同叶子保留原组与许可，加入新成员优先填已有组，避免无意义夺队/重置瞄准。正式单位可12/32人，内部每组≤4。
- 行动优先级：有效个人交战仍由FireTeamGoal处理；无目标时按最近明确命令的祖先或root默认巡逻。正式/代行指挥官走主目标，子队长按树分配疏散槽位，成员跟随自己的队长。行进方向用稳定目标向量，不随随机看头转动整支队伍。
- MOVE等长途命令用持久意图+运行时进度中心。仅在可行动指挥官没有交战目标时沿原路线有界推进中心；交战期间冻结中心，防止把“中心跟随自己”变成无限追击。每叶子48格追击边界以命令中心及层级偏移为准。静态巡逻中心仍为驻守原点，宏观步进和实际导航不读敌人实时坐标。
- 警报按单位节点存原Contact、原时间戳、已访问节点/跳数、上次转发位置；沿parent/children边、指挥员已加载且≤64格传播，不把转发当新观察，TTL100不延长。每节点转发≥10tick并有队列上限；接收者重新validTarget/伪装，超出自身追击范围只警戒/集结。无视线仅可用快照。
- 补真实“先有合法观察记录，后接收者有效伪装”用例，避免只从开场就伪装导致根本没有共享记录。若现有全局伪装规则无法产生同时不对称状态，应如实按可达的先后状态验证，不能伪造源玩家知识。
- 新行军夹具建议96×12×96（仅QA资源并排除JAR），12/72人从中部向远于原48格边界的目标移动；216人压力另opt-in且不影响日常回归。场景断言真实位移、无大步瞬移、不同子队站位、许可上限、计数/公平性；不靠只看名字/名册算AI已完成。
