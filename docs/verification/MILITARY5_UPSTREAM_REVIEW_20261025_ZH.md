# military.5 作者更新与军事补丁整合审阅

证据日期：2026-10-25。范围为最新作者分支与完整military.4源码覆盖的本地整合，不包括随后制作的模型、驻地与基地失效新功能。

## 核对基线与范围

| 项目 | NeoForge 1.21.1 | Forge 1.20.1 |
| --- | --- | --- |
| 作者HEAD | 996dffdf2d56091cbb01ed9f871bc440ed571a1a | 59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4 |
| 原military.4开发基线 | cdf5eb15628594ca82ad9b35bd11d3d34993cfc0 | a4f388282af0ad76875716d99ac2534c08c56ee3 |
| 新增作者提交数 | 14 | 12 |
| 完整本地作者变化文件数 | 315 | 199 |
| 与military.4跟踪补丁重叠文件数 | 8 | 7 |

NeoForge的GitHub比较API此前仅返回300文件；本次使用本地完整Git差异，因此采用315。各平台保持自己的Java、加载器、构建与协议；没有互相整支合并，没有提交、推送或PR。

重叠文件为build.gradle、gradle.properties、BlacklightNeoMod、DisguiseIdentity、DisguiseSystem、两张语言表；NeoForge另含ModItems。三方应用文本冲突为NeoForge4处、Forge3处（版本、语言，以及NeoForge注册入口）。未解决冲突列表为空，暂存差异的git diff --check无输出。

## 结论

未发现可确认的军事功能语义整合缺陷。作者新增身份库compact/存储/选形、物种能力、网络注册及保护逻辑均保留；军事部分扩充了MilitarySoldier的外观捕获和识别，没有用旧文件整份覆盖新作者实现。NeoForge同时保留BuildingInfectionConfig/System和FirearmsWorldConfig注册。

作者原翻译逐键比较结果：NeoForge两种语言各679键完整保留，合并后各776键；Forge两种语言各650键完整保留，合并后各747键。所有作者原键均无缺失、无值改写。新增97键来自已有军事补丁。

### 提交范围发现：Forge运行日志

审阅时Forge暂存区含logs/debug-1.log.gz、logs/debug-2.log.gz二进制差异。两文件原本已被作者跟踪，.gitignore的/logs/不会排除它们。后续贡献前应只恢复隔离工作目录这两项到作者HEAD，保留原工程和用户日志，防止无关本地日志混入提交。此文记录审阅时发现，不声称恢复操作已完成；由主代理核对并记录实际处置。

### 后续验证边界

1. 本审阅仅运行只读Git比较、源码检查和语言键核验，没有运行Gradle、GameTest、普通服存档或GL客户端，也不把主代理的单元测试结果替代实际玩法验收。
2. 新作者身份库将外观限制到512B，必要时删除HandItems/ArmorItems等外观重字段。MilitaryRole、MilitaryRank、MilitaryEquipped仍在捕获白名单内，但军事实体的capture→存储Codec→其他选形→重新选回，需要独立真实服务端场景验证。
3. 新增src/identityMilitaryTest只验证真实capture/captured/library/select路径，不宣称走过完整抓取吞噬链。其FakePlayer加入实际ServerLevel，按实际服务器时间调用生产伪装tick订阅器，等待两次Morph完成。覆盖Army/BW军官及队长、穿戴及脱装、真实实体外观NBT加载、两方当前伪装识别、原实体装备/有限弹药/身份不变，以及玩家库存不获赠枪弹。
4. 作者允许有界HandItems/ArmorItems作为渲染外观，不能把这些字段的存在误说成向玩家发物品。测试排除组织、UUID、有限备弹、初始化、背包、出生基地和持久归属等业务字段，并检查玩家真实库存；渲染替身不进入世界。
5. 作者新增/隔离了native_animations并修改MotionLibrary。military.5成品资源需以本次作者HEAD和已登记自有资源核对；旧military.4收集器写死版本与历史证据，只保留历史，不可直接证明military.5已验收。

本审阅未改变平民、军方/BW关系或感染者敌对规则；这些政策仍按用户决定处理。

## 精确源码入口

- NeoForge src/main/java/com/codex/blacklight/BlacklightNeoMod.java:31–38、82：感染建筑/枪械配置及系统、物种能力均保留。
- 两端src/main/java/com/codex/blacklight/disguise/DisguiseIdentity.java:23–38、45–61、67–80：字符串与NBT压缩边界、军事外观白名单及MilitarySoldier捕获。
- 两端src/main/java/com/codex/blacklight/disguise/DisguiseSystem.java:38–75、146、168–170：捕获记忆、实际选形与军事识别扩展。
- Forge .gitignore:72–73：忽略本地日志，不能改变已跟踪日志的状态。

唯一下一步：主代理串行运行新身份库真实服务端场景，记录实际结果；随后结合military.5其它变更完成独立回归与成品验收。
## 身份库场景预检查（实现代理）

新增的4项场景已经在两端分别使用缓存映射Minecraft/加载器API及现有生产class直接javac；Java21/Java17均exit0，实际生成MilitaryIdentityLibraryGameTest.class。此项只证明测试源码能编译，不代表GameTest通过，实际执行交主代理串行完成。

预检查曾发现并修正测试自身问题：EntityType.create返回值需显式收窄为MilitarySoldier；存储结果读取采用两端均支持的result().orElseThrow()；Java17的中文路径参数文件需明确UTF-8。这些不是生产缺陷，也没有通过修改生产逻辑掩盖测试失败。
## 首次真实服务端失败与测试夹具修正

主代理首次执行Forge身份库4场景全部失败，日志保留于Forge隔离目录.superpowers/military5-qa/forge-identity.log（18:52:01，4 required tests failed），失败点均为最终unchanged()的首条复合断言。此前capture、库/存储Codec、两次Morph、外观及两方fooled均已通过。此处不能把整个场景记为通过。

只读解析该失败世界.superpowers/military5-identity-forge/world/entities/*.mca，确认4个源士兵均仍存活、MilitaryRole/Rank/Equipped和11发备弹正确，但都没有MilitaryUnit。测试原先仅调用setUnitId(randomUUID)，没有创建组织节点或登记成员。MilitaryOrganizationSystem.ensureMember先处理该unit hint：不存在的节点会清空绑定，然后才检查NoAI和automaticUnitJoin。因此NoAI不能使无效的组织引用存续；这是测试夹具违反正常组织约束，不能归因于capture克隆/修改了原兵。

修正仅限两端新增身份库测试：通过真实transaction创建1人TACTICAL_SQUAD，assign并appoint源兵，标记manual，并在拍快照前调用真实ensureMember验证归属合法。最终断言拆开生存、UUID、实体unit、实际roster/岗位、role、rank、outfit；每项保留原强度并输出预期/实际值。结束清理实体、成员和空手工单位。没有修改生产逻辑、没有跳过源兵归属不变要求，也没有删除或改写首次失败日志/世界。

修正后两端直接缓存API javac均exit0且class存在；真实服务端复跑尚交主代理执行，不能把编译成功算成场景通过。
## NeoForge真实身份库复跑证据

主代理于2026-10-25执行修正后的NeoForge专用身份库套件；本审阅者重新读取.superpowers/military5-qa/neo-identity.log确认：19:13:04记录All 4 required tests passed，随后正常Saving players/worlds、全部维度保存、Game test server shutting down，Gradle记录BUILD SUCCESSFUL。四场景包含两方军官/队长、穿戴/脱装、实际库Codec与两次Morph、真实手工组织归属和源兵/弹药/玩家库存不变。

该结果采用本次整合代码和当时已落地的home初版；只证明这4条身份库场景通过，不代表home全部场景、后续模型变更、完整吞噬链或GL画面已验收。Forge修正版此时仍待复跑，不能先记为通过；首次Forge失败证据继续保留。
## 可选普通服归属落盘伴随验收（源码已交回，运行待主代理）

新增src/cityMilitaryTest/.../CityMilitaryServerAuditHomes.java与tools/military5-homes-server.init.gradle。旧CityMilitaryServerAudit仅增加可选beforeHalt/WAIT_HOMES续行与伴随失败记录；原city-military-server.init.gradle不变。未启用blacklight.militaryHomesAudit时维持原来的检查和正常保存停服行为；新init才同时打开两个audit flag。

伴随检查在旧native audit达到PHASE1_SAVED或PASS后，创建同阵营外来军官（真实弹匣5、备弹11）与距所有已登记同阵营基地大于300格的野外队长（弹匣3、备弹9、脱装）。两人NoAI/无重力/持久保存，用于保存字段和有限装备验证，不能据此声称寻路、作战或补给已测。等待至少60实际server tick，让生产home系统自己写归属/野外锚点；不伪造home字段、不分配不存在的unit、不消耗native slots。

首次仅写military-homes-audit.json的PHASE1_SAVED并请求原流程正常保存停服。即使旧M4 audit本来已PASS，新增home字段仍必须另一次实际进程启动才可能获得伴随PASS。第二次必须有不同process id，独立的EntityJoinLevelEvent观察器在延迟home初始化之前拍实际首次加载快照，对比phase1保存的HOME/origin/field及维度、岗位/军衔/穿戴、实际持枪/收起枪NBT与弹量，同时复核原生slots/losses不变。因此不是等AI重新填好字段后才假装验证了落盘。

存在MILITARY4-BASELINE.json时，记录其SHA256和实际来源目录，并在首次加载的原驻军快照中确认新HOME/ORIGIN_DIM字段确实尚不存在，随后验证兼容迁移。重启时要求同一baseline文件未改。两端qa主类+伴随类已直接缓存API javac成功；尚未记录真实普通服伴随执行结果，由主代理在已有三份隔离M4复制品中串行运行新init两次并核验JSON及正常停服日志。正式JAR仍按CityMilitaryServerAudit*.class排除全部这些QA类。