# military.5 独立最终代码审阅（验证状态仍待刷新）

审阅日期：2026-10-26（Asia/Shanghai）。这是 M5 实现相对已交付 M4 的代码审阅结论，文件名中的 FINAL 不表示所有运行验收已经通过。审阅者独立只读生产源码、QA 与日志，不运行 Gradle；本次仅新增本审阅文档。

## 基线、规格与现场

- Neo：`F:/MC建筑/.blackwatch-worktrees/neoforge-military5`，作者 HEAD `996dffdf2d56091cbb01ed9f871bc440ed571a1a`，分支 `feat/neoforge-1.21.1-blackwatch-reference-homes`。
- Forge：`F:/MC建筑/.blackwatch-worktrees/forge-military5`，作者 HEAD `59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4`，分支 `feat/forge-1.20.1-blackwatch-reference-homes`。
- 本轮尚无 M5 新提交，实际实现含 index、worktree 和 untracked，不能把 `HEAD..HEAD` 空差异当作没有实现，也不能仅审未暂存差异。
- 精确 M4 对照为原工程 `F:/MC建筑/blacklight-evolution` 与 `F:/MC建筑/blacklight-evolution-forge-1.20.1` 的已交付源码；作者更新另外按现有三方整合记录核对，没有把作者原改动误算作 M5 需求。
- 审阅依据：`docs/superpowers/specs/2026-10-25-military-homes-design.md`、`2026-10-25-blackwatch-reference-rig-design.md` 及同日对应 plans；同时读取本轮 IMPLEMENTATION、HOMES、UPSTREAM_REVIEW 记录，以当前文件和实际日志复核。
- 已读取共享接续与安全入口并执行只读 freshness 工具，返回 `changed`；保留本轮实际目标与证据，没有读取历史归档正文或恢复旧下一步。

## Standards：工程约束审阅

未发现可确认的重要约束违规，未提出泛化命名、重复或个人风格意见。

两端 API 差异逐项核对：Neo 使用 `LevelTickEvent.Post`，Forge 使用 END phase；SavedData 分别采用 registries/provider 与旧 factory 签名；手臂皮肤分别采用 PlayerSkin 与旧玩家皮肤 API；FacilityBlock 的 `isPathfindable` 采用各版本真实签名。服务注册分别存在于两端 `BlacklightNeoMod`，同一行为没有因跨端整份复制而丢失事件注册。

两端 `build.gradle` 均排除 `city/*GameTest*.class`、`CityMilitaryServerAudit*.class`、gametest 包与 QA arena；新 home/identity/server 夹具由专用 init 加入，普通构建不加入这些源码。此项是源码与配置核对，最终成品 JAR 内容仍须由主代理打包后检查。

平民及军方/BW/感染者敌对政策未因 M5 调整；`MilitarySoldier` 与原 M4 源码无差异。作者新 DisguiseLibrary 路径仍保留 MilitaryRole、MilitaryRank、MilitaryEquipped 白名单和军事实体外观捕获，没有把持久归属、槽位、UUID、真实备弹等业务状态当作伪装外观复制。

## Spec：实现契约审阅

未发现可确认、未解决的 P1/P2 实现缺陷。以下是具体核对项，而非用测试数量代替代码判断。

| 契约 | 核对结果与源码入口（路径均相对所在工作树） |
| --- | --- |
| 当前归属与原生出处分离 | `city/MilitaryHomeSystem.java:21–24,64–85,96–125` 单独保存 HOME、FIELD、MODE、ORIGIN_DIMENSION；bind/field/auto 不改 BASE_TAG、POST_TAG、slots、武器、弹量、角色或单位。旧档先用所有既存维度 SavedData 的实际 UUID 槽确定唯一出处，不据同坐标 base id 猜来源。 |
| 独立兵初始化、持久化与有界选择 | `MilitaryHomeSystem.java:33–60,96–125` 在 join 即防远距消失，延迟初始化后只选同阵营、operational、256 格内已登记基地；否则保留实际出生 field anchor。未获预算者不推进期限，按旧期限优先，每次最多8次全基地候选查询；周期100tick，调度每20tick。 |
| 核心健康、卸载与修复 | `MilitaryBaseHealth.java:10–23` 任一真实 terminal 存续即 ACTIVE；全已加载且全缺失观察200tick才 INACTIVE；未知观察清 missingSince，inactive 的保存值保留。`CityMilitaryService.java:276–286` 先查已加载区块，读取6个真实终端位；元数据两方均为 `(11/13/15,2/7,9)`。`CityMilitaryData.java:23,33` 只保存已确认失效，未保存旧观察期限。 |
| 有限原生槽及跨维度死亡记账 | `CityMilitaryService.java:58–77,144–149` 缺失观察期间即暂停新发；非空 slot 永不补发，修复恢复尚未发放的槽，不清死亡记录。实际 destroy 的损失回记 originLevel 的实际 UUID 槽，卸载/换维度不算死亡。Neo/Forge 行号在事件适配后最多相差1行。 |
| 原岗位、命令和物理转移 | `MilitaryHomeSystem.java:127–153,181–201` 保持有效原兵岗位；失效只换当前 home 或 field，以普通 Goal/导航行走，保留单位与命令。存在继承/显式 effectiveOrder 时 home、补给与默认巡逻均让位；field 战斗中心固定，转移的战斗范围只沿原出发点到新驻地的有限走廊。 |
| 不强载和有限工作 | 新核心读取、候选交互点、直线 clip 前均有 loaded 检查；迁移只读取现存 ServerLevel/SavedData；实际寻路继续使用原 MilitaryFormationSystem 的 loaded standing、16格局部推进与统一工作预算，没有传送或新增区块 ticket。 |
| 真实库存补给与第二箱 | `CityMilitaryService.java:214–225,228–273` 目标必须是当前有效 home 元数据登记箱，实取仍检查真实3格、LOS、Container 和有限弹量缺口，再真实扣栈。候选只取同层8邻、实际支撑/无流体/碰撞可站、LOS的地点，距箱中心≤1.5格；共享 follow 距终点<1.5格结束，故不靠扩大原3格扣弹范围兜底。跨短角点 clip 前还有 loadedBetween。 |
| 路障路径分类修复 | `registry/MilitaryFacilityBlocks.java:80` 附近仅把真实有碰撞的设施 LAND 标为不可直接穿行；其它类型沿用父类规则，未改外形/碰撞/门/屋顶/NBT。补给局部交互点没有改通用 standing、650tick测试上限或库存断言。 |
| BB 真正运行时接入 | `client/model/BlackwatchReferenceProfile.java` 从生产 assets 加载经 SHA 限定的 profile。原件 SHA 为 `4d1b2213f160aa7a5ece31131c77102d654474277b46a6f93c8624cba71b4696`；导出保留12骨骼、16人物cube、2174原关键帧，当前所有原插值确为 linear。6类动作进入 runtime；第7个 dodge 仅保存，不宣称新增闪避。 |
| 双肘、握点与大角瞄准 | `BlackwatchModel.java:77–94,342–375` 是两段六单位手臂及共同carry/双握点；`BlackwatchRigPose.java` 真实用原骨骼旋转/曲线，肩锚固定在躯干、大角由上身承担，IK到同一刚体握点；支撑手装填离握/弹匣工作/回握明确是本模组新作。 |
| 共享模型及原外观保护 | `BlackwatchModel.java:378–403` 每次重置新增层级，按弱引用实体分别观察弹匣/冷却与动作平滑；脱装、无枪和抓取保护状态清 view 基线与carry。军衔、两阵营护具/角色筛选和原人脸保留，新增装备不会独立留在前臂。 |
| 第一人称真实玩家手及清理 | `firearms/FirearmViewEvents.java:25–44,64–115` 使用真实 PlayerModel 手臂/袖层；标准/纤细采用真实FK腕点。主手枪且副手空才绘支撑手；副手物品/双枪一件物品一臂，副手单枪不添支撑；隐身不绘皮肤臂。finally 恢复臂/袖pose、scale、visible与PoseStack；菜单/暂停/换世界/玩家/视角、伪装和控制状态重置观察。 |

有限视觉边界：客户端没有新增枪 UUID/射击序号；同槽换入同款枪且所有观察数据恰好模拟一次真实减1弹与冷却推进时，仍可能出现一次无法辨别的视觉反馈。该边界不是服务端扣弹/伤害错误，也不能概括成所有换枪都会故障。

## 亲自复核的证据与尚待项

本审阅没有启动任何 Gradle、游戏、网络、提交或推送。下表只记录实际读取到的文件；运行结果不由文件名中的 green/final 推断。

| 项目 | 本次读取到的实际结果 | 边界 |
| --- | --- | --- |
| 两端规范 CPU 资产预览 | 各自 manifest 的25个源文件 SHA、65张PNG SHA全部与当前文件相符；52场景、10张兵种卡；原BB SHA及两端profile相符 | CPU真实资产捕获，不是Minecraft/OpenGL实机；静态图不证明输入手感 |
| 中间完整 JUnit XML | Neo669、Forge600，失败/错误均0 | 最后新增FP检查后的完整刷新尚待主代理；不能当最终冻结源码回执 |
| Forge最新完整回归 | `.superpowers/military5-qa/forge-regression-ready-final.log` 实际132总、131通过；唯一 `citynativeplacementpipelineanddiscoverykeepedits`，首断言为“actual Futian map has a safe road-adjacent 65-block plot”；正常保存所有维度并退出1 | pair、17home无失败；城市候选前置失败尚未定因，本审阅没有把它归为已确认M5产品缺陷；全套未通过 |
| Neo City旧M4副本普通服 | `.superpowers/military5-upgrade-neoforge/{city-military-audit,military-homes-audit}.json` 均PASS，更新于 `2026-10-25T16:28:15Z/16:28:18Z`；home记录 actualProcessRestartVerified、initialEntityLoadFieldsVerifiedBeforeMigration、legacyMilitary4NativeLoadsObservedBeforeMigration 为true | 两个不同普通服进程；初次加载快照先于迁移，有限弹量/角色/穿戴、field/home/origin及原UUID/slots保存。伴随演员NoAI，此项不证明寻路/补给/战斗 |
| Neo World与Forge City旧M4副本 | 原native audit为历史PASS，尚未读取到本轮新增home伴随PASS | 两端本轮phase1/phase2仍待主代理运行并记录，不拿旧native PASS替代新字段落盘 |
| 最终双端132及成品检查 | 尚待刷新 | 不宣称全绿、已发布、完整GL或用户模组v1.2.1兼容。用户负责标签拉取，本审阅未fetch/pull |

Standards确认缺陷0项；Spec确认未解P1/P2缺陷0项。最终运行验收仍取决于主代理对上述失败和待刷新项的实际处置，代码审阅通过不抹去这些门槛。

唯一下一步：主代理依据城市候选失败的现场证据完成诊断，串行刷新两端完整132、最终JUnit及其余普通服伴随两进程验证，再将实际回执补入本表；不因旧档记录重做已完成M4，不把只有设计的M6车辆/驾驶抢夺列为M5缺陷。

接续指令：按两端 `docs/verification/MILITARY5_FINAL_REVIEW_20261026_ZH.md` 与 `MILITARY5_IMPLEMENTATION_20261025_ZH.md` 的最新实测记录接续；项目 [blacklight-evolution](https://github.com/mengyuyuan/blacklight-evolution)，共享经验 [AI-Engineering-Lab](https://github.com/yfcyy889-ops/AI-Engineering-Lab)。

