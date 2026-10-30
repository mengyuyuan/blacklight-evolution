# M6 地面载具实施记录

当前任务：按用户授权制作运输卡车、装甲车、坦克、玩家驾驶/武器/抢夺、军官车队、多武器步兵和有限车库后勤。细节由实现者决定；平民、两方同盟和合法感染目标政策保持原规则。

## 现场与隔离

- Neo：`F:/MC建筑/.blackwatch-worktrees/neoforge-military6`，分支 `feat/neoforge-1.21.1-blackwatch-vehicles`，作者基线 `996dffdf2d56091cbb01ed9f871bc440ed571a1a`。
- Forge：`F:/MC建筑/.blackwatch-worktrees/forge-military6`，分支 `feat/forge-1.20.1-blackwatch-vehicles`，作者基线 `59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4`。
- 本地 M5 未提交改动通过二进制 diff 与未跟踪源码复制，复制时两端分别对 14842/8550 个 src 文件逐个校验 SHA256。证据在各自 `.superpowers/military6-bootstrap/manifest.json`。没有提交、推送、fetch、pull 或 merge。
- 原生工作树工具在本项目无 Git 根目录，沿用已核实的本地 linked worktree 方式；新目录不在任何旧试玩包内。M5 工作树及旧包保留。
- 模组自身 1.2.1 标签由用户之后亲自拉取，当前未知其真实 SHA、接口差异及兼容性。

## 边界与分工

设计权威：[完整设计](../superpowers/specs/2026-10-25-prototype-ground-vehicles-design.md)；任务：[实施计划](../superpowers/plans/2026-10-25-prototype-ground-vehicles.md)。

Ruling：M5 生产源码冻结且双端 132/132 已有真实通过日志后，复制其运行源码启动 M6 纯规则与资产制作；M5 剩余单元刷新、两进程旧档和打包继续在原工作树串行执行。这样可以并行推进，不将 M5 的结果当作 M6 验证，也不覆盖 M5 后续文档。

- 根代理独占所有 Gradle、公共注册/整合与最终打包；任意时刻只运行一个 Gradle。
- 规则代理只写车辆定义、库存、座位、控制权、车体几何及对应纯测试，先提供 RED 回执。
- 美术代理只写车辆 authoring、生成工具、几何/挂点资源和实际参考记录；原游戏截图仅存 ignored 研究目录，禁止进入发布资产。
- 文档代理只收尾 M5；不修改 M6 生产。

## 任务进度

| Task | 状态 | 已有证据 |
| --- | --- | --- |
| 0 隔离与原作目视 | 隔离已完成，目视继续补齐 | bootstrap 逐文件哈希；坦克拆塔/APC参考缓存待核对归档 |
| 1 纯规则/有限库存/控制 | 正在编写失败测试 | 尚无车辆测试通过声明 |
| 2 独立机械实体/移动/模型 | 资产前置研究 | 尚未注册实体 |
| 3 玩家与真实 AI 乘员 | 待实现 | 无 |
| 4 火炮与机械伤害桥 | 待实现 | 无 |
| 5 玩家抢夺/真实乘员 | 待实现 | 无 |
| 6 步兵多武器/分弹种备弹 | 待实现 | 无 |
| 7 军官车队/清剿 | 待实现 | 无 |
| 8 真实车库/有限派发/后勤 | 待实现 | 无 |
| 9 双端审阅/客户端/资料/包 | 待实现 | 无 |

## 本阶段验证

M5 已刷新单元：Neo 670、Forge 600，均 0 失败/错误/跳过；双端真实 GameTest 各 132/132。这是复制基线的证据，**不等于车辆已实现或通过**。旧档重启验证与交付由 M5 记录继续收尾。

当前唯一下一步：对 Task 1 实际生产入口运行失败测试，再实现纯规则；并行补原作车辆实机依据与原创可编辑模型。驾驶、抢夺、两端载具和真实客户端仍未完成。


## 2026-10-26 根代理接续与实际结果

M5已经独立交付：`F:/MC建筑/military5-reference-homes-preview-20261026.zip`，512606406字节，SHA256 `fb7abd664b7c1dfeea840fcf699112678e0a9ba90fa30718ebe0c2b8d89cc01e`。1687项文件校验与ZIP CRC已由根独立复核；两个主JAR、完整源码、制作资料、测试证据共1688文件。旧包及用户PCL/存档未修改。M5生产与交付目录冻结，不能让M6改写其已验证结果。

M6新证据（这些不是M5结果）：

- Task1六类纯规则：Neo/Forge独立Java17各33/33，0跳过；原29及三个P2逐项RED/GREEN保留。审阅发现炮管OBB空角误命中、坏快照丢冷却键、HE/AP冷却组误配，已补失败例再修。日志在`build/military6-task1/review-p2-*.log`；公开sweep几何接口保留。原审阅记录在`.superpowers/military6-task1-review.md`，修复仍可定向复核。
- 原作18张实机图已实际目视；作者源SHA `7c3c0604e96998403fb51124f74e68e623633079061db03b7938b4f60fec1137`。3个可编辑bbmodel、2涂装、377方块几何、同源座位与炮口挂点，双方14资源一致；`art/military-vehicles/validation-receipt.json`、`reference-observations.json`保存来源/命令。商业截图仅ignored研究缓存，不进发行资源。
- 根已写真实`MilitaryVehicleModel`和renderer，直接烘焙该377几何。新增3项真实MC ModelPart单元先3/3 RED后3/3 GREEN（`build/military6-model-{red,green}`）；实际绘制顶点数、全部骨骼挂点多组车体/炮塔/俯仰与服务端mountWorld一致。不是另造预览算法，也未做OpenGL试玩。
- 控制/换座payload和Neo/Forge注册已接入；协议版本分别更新standalone-24/forge17。驾驶包只发UUID/lease/seq/有限输入，连接决定真实玩家，拒绝无乘坐/错世界/坏数；非驾驶席换座另用真实乘员消息，不需驾驶lease。5项编解码/边界检查先4过1失败，后5/5 GREEN（`build/military6-payload-{red,green}`）。Windowsjava参数文件需GBK，javac参数可UTF8；初次长命令WinError206及UTF8java参数找不到类属于工具错误，均保留。
- 实际客户端controls/HUD及两涂装renderer注册已写两端。WASD驾驶、空格制动、Shift原版下车；换座/选弹默认未绑定以免占用已有超能力键。鼠标主/副武器意图存在，但火力尚未实现。没有实际真人客户端驾驶验收。

### Task2真实游戏诊断链

1. 首轮8项RED有5个明确未实现库存错误，其后Neo未配置的mock连接导致附件同步崩服；不是8项正常完成。改官方configureMockConnection。
2. 最小实现8场景7过1失败，未知边界夹具的setBlock本身可加载邻块；首个9场景诊断则7过2失败（保持换座、夹具未知块前置）。
3. 根收回实体写入后，夹具改只编辑已加载LevelChunk，11场景实际7过4失败：未知区块读取多加载1块、长车跨section查询漏候选、按住换座在新lease重复触发、Prototype玩家被感染身份误禁驾驶。日志`neo-entity-root-red3.log`。
4. 修复Prototype身份与NPC感染禁用分开；保留同一实际乘员动作边沿；原生VehicleHullPart只代理碰撞/命中/交互，健康/库存/乘员/存档仍唯一在parent；返回null原版controllingPassenger继续封堵客户端坐标包。第一轮根GREEN仍有2失败（未知块及尚未真正tracked的查询夹具）。
5. `Level.getBlockCollisions`会走getChunk(FULL,false)等待未来区块，改为LoadedView所有terrain读只走getChunkNow，形状需要未知邻块则保守停车；不使用任意缩小车身或强加载。长车查询夹具等待20tick且检查真实entity管理器登记，排除未跟踪的假场景。Neo11/11真实GameTest已正常保存退出：`.superpowers/military6-qa/neo-entity-root-green5.log`。
6. Forge第一轮11项8过3失败，均mock连接channel为null导致测试清理失败，真实机械场景未失败。已给官方mock helper补EmbeddedChannel，正在根唯一Gradle新世界复验：`.superpowers/military6-qa/forge-entity-green2.log`。不得在无回执时写Forge11全部通过。

### 当前写入分工与未完成

原子代理状态长期pending_init；根已发送中断及收回collector、Entity/Kinematics/QA写权限消息。不要从其旧下一步恢复并覆盖以上根改动。当前所有运行/整合由根执行，始终一个Gradle进程。

仍未做完：车辆可取得/放置的底盘与弹药燃料物品/配方/真实补给、车载武器与机械伤害桥、AI驾驶/军官车队/清剿、玩家真实驱逐抢夺、步兵多武器typed备弹、真实车库有限发行/维修/迁移、两进程载具保存和最终M6包/客户端验收。现有库存API只供测试与后续服务接入，不能把它说成玩家已能在游戏正常补给。

唯一下一步：先读Forge第二轮真实回执，然后实现车辆底盘物品/真实有限补给和火力链的RED→GREEN；其后接玩家抢夺、军官车队与车库。不fetch/pull/merge模组1.2.1，不提交推送，不覆写M5交付。


## 2026-10-26 20:51 后续现场证据（覆盖上文旧待办状态）

上文“任务进度表/当前唯一下一步/未完成”保留为较早工作记录，以本节及实际日志为当前状态。仍非 M6 完成/交付声明。

- Forge 的16项底盘/物品/配方/实际补给测试全部通过，`.superpowers/military6-qa/forge-supply-green2.log`；Neo 后续24项全部通过，`neo-fire-impact-green.log`，实际保存退出。
- 五种车载武器已有显式武器ID弹体、装甲系数、同一实体库存/冷却/真实炮口、拒绝加入回滚、旧步枪机械命中。Neo24包括真实自动炮命中不重复扣HP、HE/AP同kind独立NBT、射手死亡不留幽灵炮弹。不能据此宣称所有超能力/感染体机械目标AI已兼容。
- 空底盘、真实燃料/弹药/维修件注册、11配方双端适配已写；玩家停稳近距离转移实际ItemStack。车辆内真实乘员+玩家驾驶/炮手权限；AI车队/车库生产代码当前正在整合。
- 真实服务RED：`neo-integrated-red2.log` 车库4项正常缺实现失败，随后fleet测试用AssertionError造成测试服务器崩溃。已改GameTestAssertException，不能把崩溃算成完整测试结果。首次默认权限Gradle本机daemon无法连接，授权定向验证的本机进程运行后正常，不是模组bug。
- `neo-hijack-red.log` 28项正常结束，3抢夺请求缺实现失败。实现后 `neo-world-green1.log` 39项/5失败（3抢车+2车队）；实际原因之一是原型体生存锁拦截用于抢车的AttackEntityEvent。已改为真正载具交互事件，并仅给机械交互/受控机械火力事件窄入口，保留无关生存限制。
- `neo-world-green2.log` 45项/8失败，抢夺原4项已通过（同车UUID/损伤/库存/冷却，原乘员真实驱逐且保留身份物资、持续接触取消、双玩家排他、拒绝强驱真人）。新增的隔墙/途中乘员变化/实际模式交互测试仍待新回执。抢夺不自动吞噬，不产生任何吞噬收益；被驱逐者成为原来的普通存活实体，吞噬仍须使用原模组正常条件和流程。
- 本轮8失败：两方实际驻军正派发、AI合法目标/炮口瞄准/下车/短间距跟车、未知区块组合断言。代理正在按现场诊断修复：优先地面可达闲兵、测试真实敌人须persistence避免远处自然despawn、全局forced集合被其他独立fixture移除并非加载证据。不能把尚待复测的修复写成通过。
- 两个工作树重新分工：m6_garage独占车库/自然附加piece/新命令/后勤及其QA；m6_fleet独占车队/预算/临时下车部署/新命令及QA；根独占Entity/火炮/抢夺/客户端/注册/主记录与全部Gradle。每次仅一Gradle，所有QA使用独立新世界，用户PCL、已交付M4/M5包、原开发树不动。

Ruling: 本阶段先采用真实驱逐再按原吞噬机制处理人的方式 — 用户要求玩家可抢夺，未要求抢车自动杀人或发吞噬奖励；避免另建绕过healthReady/Assimilation的收益路径 — 代价是需要另行按原吞噬键处理已下车乘员，没有一键抢车兼自动吞噬。

未完成：后续真实GT修复回执、步兵分弹种库存/有限副枪、感染体当前合法车内目标机械攻击、自然车库及命令最终整合、车辆两进程持久化/旧M5副本不增发/压力、全部M5回归、实际模型/骑姿/炮口预览、客户端真人体验、双端发行构建/最终制作资料/M6新包。模组v1.2.1标签仍未拉取/核实，不宣称兼容。没有提交或推送。

唯一下一步：完成正派发与车队真实目标等已暴露问题的下一轮Neo/Forge验证，再继续剩余功能；不能恢复Task1旧“尚未注册实体”待办重做。


## 2026-10-27 后续整合记录

日期已跨到10月27日，仍延续同一用户授权目标。M4/M5包、PCL和用户档继续冻结；没有fetch/pull/merge/commit/push。以下只记实际证据，不把生产代码已写等同世界验收。

- 真正31x27车库/新自然piece独立survey/显式preview-token命令/加载发现已接；实际16/20驻军派发时先选可达地面闲兵。Forge `forge-world1.log`47项46过1失败，8项车库（含两方真实正派发）均过，剩编队格点已修细2格；旧基地没有GaragePiece不自动补建。
- `neo-loadout-hull-red.log`62项12失败；其中Task6五项实际RED（缺实物双枪与旧90步枪备弹被当火箭），机械Goal六项缺入口、1友车挡炮断言被宿主体型成长干扰。随后typed唯一备弹、实物副枪、原生UUID一次发放、有限RPG/卡宾切换与安全爆点都已实现；CityMilitaryService原生birth交易在save/load前接一次initializeNativeLoadout。直接summon/default initializeWeapon仍确定性，不随机篡改旧QA。
- `neo-all-green1.log`76项74通过2失败：一项机械重上车踩原版60tick下车冷却（夹具改等待65而不force上车）；一项射手会正常战斗横移绕开近敌，错误要求全部140ticks不发火箭（夹具movement=0且保留实际射击AI与遮挡前提，未放宽弹药断言）。不能将首轮2失败写成全绿。
- `neo-permission-red.log`34项1失败，真实Prototype枪械BreakEvent被原生生存锁一并拦截。新增仅该实际事件对象的ThreadLocal scope，其他保护插件仍可取消、手工采掘仍被锁；AI mobGriefing与Prototype枪击机械原测试已过。修复后后续整合该3项通过。
- `neo-physical-boarding-red.log`91项9失败：物理重击/真实方块/被投掷Mob6项、Hunter/Brute/成熟Host蓄力上车3项。物理第一轮修复后 `neo-combat-pose-green1.log`92项10失败，6物理仍失败（同帧新force区块实体尚未可查询，已补延迟+实际可见前提），3旧pending尚未接门，另真实乘员转弯身体不随车的RED。
- 修复上述夹具/旧pending/乘员朝向后 `neo-projectile-contact-red.log`97项6失败：原12物理全部通过、3蓄力上车全部通过、body yaw通过、16载荷与8车库均通过。剩1旧猎手自然选敌窗口随机回归（加真实ticking诊断并60→180等待，不setTarget）和新5炮弹边界RED。后者包括旋转包络空角、真实OBB/近墙先后、真实ImpactEvent接触点与取消、AP上下身。第5首次lane擦实际坦克角，已纠正lane并保留原36上下身断言，不能将初次失败单独当directLiving bug证据。
- 两条P2由独立审阅 `.superpowers/military6-independent-review.md`提出：新VehicleOrdnanceProjectile沿保守AABB错误命中/爆炸空角，以及修真实接触点后上身directLiving被脚点半径过滤。已以最窄WrapOperation只替换该新弹体碰撞结果，原Throwable.tick/Event/cancel/drag全保留；其他旧弹体original.call原样。旁溅改真实表面、直接生物结算一次并排除重复，当前两版javac已过，实际GREEN仍待。不得把新Mixin未运行写成运行通过。
- 实际生产模型377几何/两涂装都已捕获为8场景CPU预览（`tools/VehicleMeshCapture.java`与`preview_military_vehicles.py`），不是重新手绘模型/游戏截图。发现驾驶头穿顶后补真实头部顶点/车顶顶点RED→GREEN3/3；按Entity基础位置校正座位，卡车驾驶.55/cargo.9，APC/Tank对应减.75。副武器原平射挂点已补实际ModelPart RED→GREEN4/4，视觉骨骼与服务器都跟随俯仰。当前authoring SHA由本节末回执列出，旧7c3c和5d2a是历史，不是当前assetSHA。
- 抢车采用30tick真实接触、同车UUID/损伤/有限库存不变、真实AI驱逐、真人占席拒绝；不自动吞噬/发奖励。新增墙/乘员中途变化/实际Prototype交互边界已进后续整合通过。动画仅简单坐姿/炮口表现，实际OpenGL驾驶/多人客户端体验未验证。

接下来：完整Neo/Forge单元与最新世界GREEN → 同artifact两次normal dedicated-server保存/验证（额外比对typed备弹与真实枪组件）→ M5完整132场景各端回归及旧M5副本不增车辆 → 正常发行/资源无QA泄漏/资料卡与新M6包。实际压力3/12/24只有只读收集器准备，场景/数据仍待，不能承诺FPS。模组v1.2.1仍由用户后拉，未核实兼容。

当前车辆authoring SHA256：`08cdbb982d27d98b88ee70a24652267d435c9fca845e774e7787c4754ad85e4b`。


## 2026-10-27 交接给 cc-haha（最新现场；覆盖上文旧待办）

用户最新指令：准备让另一 agent 接手，要求可复制的提示词。现在只交接，不继续实施。以下记录由 Codex 根在本机读取文件/Git/真实日志后补充；不能从本文件较早“尚未注册实体/Task1”恢复重做。当前 M6 **没有完成或交付**。

### 交接时现场与保护范围

- Neo 工作树 `F:/MC建筑/.blackwatch-worktrees/neoforge-military6`，分支 `feat/neoforge-1.21.1-blackwatch-vehicles`，HEAD `996dffdf2d56091cbb01ed9f871bc440ed571a1a`。
- Forge 工作树 `F:/MC建筑/.blackwatch-worktrees/forge-military6`，分支 `feat/forge-1.20.1-blackwatch-vehicles`，HEAD `59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4`。
- 两端 origin 本地配置已复核 `https://github.com/mengyuyuan/blacklight-evolution.git`。未访问远端；模组自身 v1.2.1 标签存在性/SHA/兼容性未知，用户要求之后亲自拉取，由接手者指导，不能自行 fetch/pull/merge。
- HEAD 只是原作者基线；本地存在大量 staged、unstaged、untracked 的 M1–M6 叠加。**完整文件是工作成果，不能只取 git diff，不能 reset/clean/checkout 覆盖它们**。包括未跟踪的 MilitarySoldier、车辆目录、资产、文档及测试。不要把暂存区误认成已经提交。
- 原开发树 `F:/MC建筑/blacklight-evolution`、`.../blacklight-evolution-forge-1.20.1` 和所有旧 M4/M5包冻结。PCL、用户 mods/存档未改。当前两端 version `0.3.4-preview.20261026-military.6`；开始于26日、27日继续，并非日期必须立即改名。
- M5已独立交付 `F:/MC建筑/military5-reference-homes-preview-20261026.zip`，512606406字节，SHA256 `fb7abd664b7c1dfeea840fcf699112678e0a9ba90fa30718ebe0c2b8d89cc01e`；其同名解压目录也保护。两个主JAR、完整源码/制作资料/证据共1688文件；无GeckoLib。M6不得覆盖此包。
- 交接检查时 Get-Process java 没有返回进程；上一 exec session 55429 已不存在，最后服务器已正常保存退出。子代理为 completed/interrupted，未继续写入；接手前仍须再次核对进程/目录。**整个项目任何时刻仅一个 Gradle**，两个工作树也必须串行；不与另一 agent 共写。
- 已读 `E:/全项目经验库/README.md` 接续节及安全入口，并以最新实测时间运行只读 freshness 脚本，返回 changed、snapshot_is_current=false、may_resume_from_archived_next_step=false。中央 minecraft 索引还含 MC-My-Worlds 历史，不能当本项目当前状态。以这两工作树现场及本节为准。

### 用户已授权的完整范围与已实现内容（不是全部验收通过）

用户要求按《虐杀原形》参考做原创卡车/APC/坦克（军方和BW共6实体），AI车队+玩家驾驶、武器、抢夺、车库/有限补给、军官带队巡逻/清剿、步兵多武器弹种；已经授权完整制作和定向验证，不必重新提大方案求确认。

现生产代码已包含：独立机械实体（非LivingEntity）、原生多部位碰撞代理/OBB与炮管、服务端真实乘员/驾驶输入/座位lease、有限油弹/装甲系数、HE/AP共享主炮冷却、5车载武器、6底盘及11配方、实际玩家加油补弹和野外维修；步兵实物主副枪及typed备弹；真实司机/军官车队、加载区块规划/预算、合法感染目标、下车作战归队；31x27车库/真实3车一次性派发/真实库存维修/原生新自然piece。旧无GaragePiece基地不自动补建。

三种可编辑原创 bbmodel、2涂装、377cuboid（卡车78/APC138/坦克161）、实际生产ModelPart与同源挂点、实际军人乘车姿态均已写；修正过炮管俯仰骨骼、司机穿顶与身体不随车转。当前authoring SHA `08cdbb982d27d98b88ee70a24652267d435c9fca845e774e7787c4754ad85e4b`，应接手再核验；旧SHA是历史。

军事/BW维持原规则：两独立阵营、默认同盟、合法感染目标/伪装政策沿用；没有另建平民阵营。原16/20驻军名额不改，不复制真人/乘员，不免费增弹。抢夺需原型体空手Shift右键接触30tick，驱逐同一真实NPC并接管原车UUID/损伤/库存；不自动杀死或发吞噬奖励，下车NPC仍可按原正常条件吞噬。玩家归属车辆优先，不被AI自动征用。

关键边界：规划全服2次/tick、单次2048节点、加载区块only，未知地形安全停；不强载追击。CLEAR_AREA目前安全停车/真实下车作战，尚未实现设计中的14米宽水平散开阵形，不能宣称该形态完成。新车底盘零油零弹，普通召唤新车应满HP但零油弹，坏半存档仍fail-closed。野外维修现为每1实际维修件恢复20HP，满血/远程/死车拒收；这部分指南尚有旧“只能车库维修”说法，需纠正。

### 最新真实验证与失败链

所有下列日志以 Neo/Forge各自 `.superpowers/military6-qa/` 为根。日志名字带green不意味着通过，以结尾计数/失败/版本为准；0 tests、崩服或 BUILD SUCCESSFUL单独均不算验收。

1. Neo `neo-unit-all2.log`：实际765/765 JUnit，零失败/错误/跳过。但此后改了新车NBT和野外维修、测试场景，最终应刷新。先前765两失败是vanilla Language资源缺失的QA类路径与旧座位Y期望；已用真实对应版本client-extra资源和真实座位修正，不编造翻译。
2. Neo `neo-soldier-summon-red.log`：102完整结束，100通过、2失败，分别 bare soldier召唤查询前置和Prototype权限前置。12物理/5炮弹（含取消后原版飞行/真实ImpactEvent）、11车队、8车库、16typed载荷等已在这轮通过；不能写102全过。
3. 最新 Neo `neo-soldier-red2.log`，2026-10-27 12:58:50完成、12:58:55正常保存：定向20，19通过、1失败。唯一失败 `actualvanillabaresummonfinalizesnewsoldierandofficerwithdeterministicfinitedefaultguns`：`count=0 fixtureX=285000`，仍在真实/summon后的实体查询前置，**还没有得到“枪弹或军衔行为”有效RED**。先把查询放5tick仍不足，生产MilitarySoldier仍未为该问题修改。不要据此直接修武器逻辑。
4. root最新仅测试修正 VehiclePermissionGameTest：先等真实 `VehicleTestReadiness.run` 的entity-ticking，再生成tank和prototype玩家并立即射击；去固定20tick，前置诊断输出position/queryable。两端源均改，Neo刚才20项中该权限测试已过，完整102及Forge仍须复验。Prototype枪击机械/玻璃窄许可不能解锁手工攻击/采矿或绕第三方取消。
5. Neo `neo-usability-green.log`：18/18实际通过（新车普通summon、坏半NBT拒收、有限维修、核心座位/权限）。`neo-usability-red2.log`曾是错误filter导致0测试但Gradle成功，**不能计入**；目录filter必须先排除isDirectory，避免剪掉全部包路径。
6. Forge 最近完整 `forge-world1.log`：47中46通过1失败（旧编队格点已修细2格），8车库通过；后续大批新生产/物理测试尚无全端实际通过回执，不能拿javac代替。
7. M5旧发行自身曾Neo670/Forge600单元、两端132/132游戏测试、三种旧档副本两进程通过；这些属于M5，**尚未替代M6上重跑132回归**。
8. 生产车辆CPU预览Neo已实际捕获8场景+总览，`tools/VehicleMeshCapture.java` / `tools/preview_military_vehicles.py`。`art/military-vehicles/previews/production`，实际顶点JSON在`build/military6-visual-capture/actual-vehicle-meshes.json`。Forge仍需实际运行本版本capture，不能把拷贝Neo图片当Forge运行证据。CPU预览不是GL截图/真人驾驶验收。没有实际双客户端驾驶/炮手验收。

最新失败源码：`src/vehicleTest/java/com/codex/blacklight/gametest/vehicle/MilitarySummonLoadoutGameTest.java`。真正无NBT命令、finalizeSpawn=true；应确认所用CommandSource真实世界/位置、实体ticking和索引就绪、实体实际产生位置，再断言恰好2人。现有 `VehicleTestReadiness` 是有界80轮真实区块ticking前置，可复用但不要让它自动succeed早于异步断言。

MilitarySoldier `readAdditionalSaveData` 对全无Military数据可能仍无条件weaponInitialized=true、缺role/rank读0，此为源码疑点，不是已被最新GT验证的产品缺陷。有效RED后仅对真正fresh vanilla NBT保留constructor默认值；任何半旧Military*/已知Blackwatch字段仍严格迁移、不补发。军官要保持OFFICER/LIEUTENANT；实物HandItems不得复制、免费填弹；旧空兵不得补枪。子记录 `.superpowers/military6-loadout-handoff.md`。

### 尚未运行的必要验收/收尾

A. 修正上述召唤夹具、得到有效行为RED再按需修复；两端完整M6 GT全绿（Neo目前102，Forge可能101，按真正发现数量），最终完整JUnit、M6上的M5 132项回归。
B. 冻结同一生产artifact后双端正常DedicatedServer两进程存档审计：`tools/military6-garage-audit.init.gradle`，`src/vehicleServerTest/.../VehicleGarageServerAudit.java`，same新隔离dir、先prepare再verify，必须不同PID、实际APC卸载、有限步兵补弹计数>0及UUID/HP/油弹/冷却/手中与收起枪/身份/旧世界标记不重造。脚本/独立只读审阅已准备，**实际prepare/verify未跑**。
C. 压力场景已写并独立编译/脚本解析，但**3/12/24实际压力还没有运行**。脚本`tools/military6-fleet-stress.init.gradle`，真实正常服务器12原步兵+相应数量车/真实司机军官，默认100warmup+600采样tick；每档新dir。读取`vehicle-fleet-stress-N.json` status=`OBSERVED_PASS` **且**`vehicle-fleet-scene-N.json` status=`SCENE_OBSERVED_PASS`，实际每车持续移动、12步兵移动、规划预算/2048nodes/有限库存/强载集合不变等同时满足。记录实际硬件和p50/p95，不能宣传未测客户端FPS。
D. 双端原创资产生成工具--check、Forge实际CPU捕获；M6玩家指南和制作资料同步更新（野外维修、普通召唤检查结果、普通officer命令含MilitaryAutomaticJoin字段可能触发半旧存档分支，要按真正验收设计步骤）。`docs/MILITARY6_PLAYER_TEST_ZH.md`、`docs/制作资料/M6_载具与车库.md`、`docs/制作资料/M6/`、`tools/archive_military6_catalog.py`已存在，档案208源码快照/30PNG中源码因后续改动已陈旧，须重新生成再`--verify`。别宣称现快照等于当前源。
E. 最终独立代码/打包审阅、正常UTF8发行build+distributionZip、package_release verify-only，排除全部QA类/模板。最后生成真实VERIFIED manifest并收集**全新M6**源码/两个bundled主JAR/模型/资料/实测证据包，SHA256/CRC复核，给易懂安装和测试流程。GeckoLib用户已安装，不重复附赠；不碰PCL安装与用户档。真GL可明确留用户测试，不冒充已测。

### 打包器独立审阅：3个未修复实质点

`tools/collect_military6_delivery.py`、`tools/test_collect_military6_delivery.py`、README此前23fixture两端通过，但独立审阅发现下列缺口，已经告诉打包agent，后被中断。交接再次查collector两端SHA仍为旧 `68a0f48e7ff6411527c46e242147c1a814fe7989a81cfffaa92f74319e3af4a7`，**未落地这些修复**。从测试覆盖不足继续，别信23/23即安全：

- manifest.jar只查名字符合后缀，而package_release_gate验证canonical JAR，没有绑定相同真实路径/哈希，可交付另一个stale同后缀副本。应精确绑定并补RED fixture。
- gameTests只信手填covers并累计数量，同一18PASS日志重复8次=144可假装覆盖两suite。只读内存实证已确认。须唯一运行证据及日志中实际suite覆盖，不让手填标签证明132旧回归与M6；以真实运行结果而非示例计数冻结。
- vehicle_jar_gate拒所有nested.jar，而Forge正式jarJar包含声明的`META-INF/jarjar/mixinextras-forge0.4.1.jar`，正确包误拒；最小内存JAR已复现。仅精确允许项目正式依赖布局并核验metadata，不放开QA/Gecko/任意nested。

不要夸大source freeze：目前源清单比当前hash、运行/JAR关联主要靠mtime和root审核，不是运行时记录完整源digest的严格端到端证明。不得捏造VERIFIED JSON或复用历史PASS满足门槛。收集器契约见README；子记录`.superpowers/military6-packaging-handoff.md`和`.superpowers/military6-docs-handoff.md`。

### 根独占Gradle命令与已知工具陷阱

```powershell
# 始终显式workdir，两个工作树全局串行，一个Gradle。
$env:GRADLE_USER_HOME='F:/MC建筑/.blackwatch-tools/gradle'
$env:JAVA_HOME="$env:GRADLE_USER_HOME/jdks/eclipse_adoptium-21-amd64-windows.2" # Neo; Forge改17
# 游戏内新隔离世界；Forge另加 -Dnet.minecraftforge.gradle.check.certs=false
python -X utf8 tools/prepare_military_gametest.py --run-directory .superpowers/自行取全新目录
.\gradlew.bat --offline --no-daemon '-Dorg.gradle.jvmargs=-Xmx3G -Dfile.encoding=GBK' -I tools/military6-vehicle.init.gradle '-PgameTestRunDirectory=.superpowers/同一全新目录' runGameTestServer
# 最终完整JUnit（独立于游戏world init）
.\gradlew.bat --offline --no-daemon -I tools/military6-unit.init.gradle test
# 正常DedicatedServer重启：先在新隔离目录准备eula/properties，读audit源码确认要求，同dir先prepare再verify
.\gradlew.bat --offline --no-daemon -I tools/military6-garage-audit.init.gradle '-PserverRunDirectory=.superpowers/新audit目录' -PvehicleGarageAuditPhase=prepare runServer
.\gradlew.bat --offline --no-daemon -I tools/military6-garage-audit.init.gradle '-PserverRunDirectory=.superpowers/同一audit目录' -PvehicleGarageAuditPhase=verify runServer
# 压力：脚本自己准备eula/flat properties，每档新目录；N为3/12/24
.\gradlew.bat --offline --no-daemon -I tools/military6-fleet-stress.init.gradle -PvehicleFleetStressVehicles=N '-PserverRunDirectory=.superpowers/新stress-N目录' runServer
# CPU模型（无Gradle/GL）与制作档案
python -X utf8 tools/preview_military_vehicles.py
python -X utf8 tools/archive_military6_catalog.py --skip-render
python -X utf8 tools/archive_military6_catalog.py --verify
```

上次定向RED用`.superpowers/military6-summon-only.init.gradle`加到vehicle init后，20项，源里保留核心/权限/新车维修/召唤/Readiness。该init仅Neo，不能当完整回归。不要重复用旧world。日志保存后保留真实LASTEXITCODE，末尾Get-Content会掩盖PowerShell命令退出码。

- 当前权限下默认Gradle daemon本机连接可能受限，Codex通过已授权local escalation才运行成功；cc-haha按自己真实权限处理，不改防火墙或用户配置。
- Python均`-X utf8`；长Java CP必须@args避免Windows32768长度；java参数文件GBK，javac NeoUTF8/ForgeGBK。Forge必须其真实1.20.1 classpath，不能混Neo authlib/ModLauncher。
- QA输出字节编码可GBK，但resources filtering=UTF8；**正式发行构建不带QA init/GBK override**，按正常项目build。ordinary distributionZip先生成，再package_release verify-only，不能拿过期distribution反复收集。
- `src/main/java/com/codex/blacklight/gametest.*` QA类可能通过init进main输出；正式JAR必须按仓库规则排除，不能以源码存在就判泄漏，按真实ZIP entries验证。

### 唯一立即下一步

先在两个真实M6工作树读本节并核对Git/活动进程，再只修正无NBT召唤测试的实际世界/位置/ticking查询前置，取得有效行为RED后再最窄修 MilitarySoldier；随后依次完成A–E。不要从旧表恢复“未注册实体”，不要跳过真实验证直接打包。

接续指令：按 `F:/MC建筑/.blackwatch-worktrees/neoforge-military6/docs/verification/MILITARY6_VEHICLES_20261026_ZH.md` 最末“2026-10-27 交接给cc-haha”现场记录继续；项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

## 2026-10-28 Codex 接续实绩（本节覆盖旧待办，验收继续中）

沿用用户完整实施与定向验证授权。两端分支、作者 HEAD 和 origin 与交接一致；所有暂存、未暂存、未跟踪成果保留。未操作远端、提交、PCL、用户 mods/存档或旧 M4/M5 交付。根独占全部 Gradle，两个工作树全局串行，新世界均在各自 `.superpowers/`。

- 收集器三个缺口已逐项取得 RED 后修复，两端最终31/31。交付 JAR 精确绑定 canonical 路径及 SHA；日志路径、内容和运行目录去重，并依据真实 batch 核对完整 M5/M6 覆盖；Forge 只精确允许正式 MixinExtras jarJar 布局、metadata 和依赖 SHA。详见 `collector-gaps-green-final.utf8.log` 与 collector README。
- 普通士兵召唤已形成有效行为 RED：`neo-resume-behavior-red2.log` 实际33项30过3失败，命令在真实世界/坐标创建且索引可见的两人经过真实 tick 后，普通兵仍空手零备弹。仅对无 HandItems 且无 Military*/Blackwatch* 自有字段的新生 NBT 保留构造默认发枪；旧字段、空 HandItems、实物枪及坏半存档不补发。之后 Neo 定向20/20通过。
- 测试前置的两个实际问题已纠正：GameTestServer 的快速 tick 可先于区块完成任务，readiness 显式处理真实区块完成任务再检查可见性；GameTestInfo 执行后会移除同一 Runnable，阵形轮询改为每次登记新回调。原超时和实体查询失败都没有冒充枪弹/阵形行为 RED。
- CLEAR_AREA 相邻14格战斗间距已按原设计完成，三车外侧参考点约28格；复用真实司机、有限油弹、现有规划器、全服2次/tick、同车20tick、2048节点和已加载地形边界。阻塞安全停为 BLOCKED，整队停稳再下运输兵，不瞬移、复制人员或强载。两项场景均先取得真实行为 RED 后通过。
- 独立审阅发现并修复客户端车辆输入被原型体个人战斗截走、以及有炮手时司机鼠标位连带拒绝驾驶输入。两端各16项针对性测试通过；原生鼠标/改键/网络与真人手感仍需客户端复核，不能用纯条件测试冒充。
- 实际全身顶点检查发现卡车手臂、腿和军官外观穿壁/地板，已最小调整同源座位和坐姿，没有缩放整个人物。128组席位/阵营/岗位/轻重装组合通过净空断言。新 authoring SHA256 为 `df546a58efaddcdba90f717587876602c68e42af2bcc68a70d7a60fbec4542da`，上文08c旧值保留为历史。
- 模型双端各实际运行本版本捕获，canonical各8场景/9主图，并实际查看剖视。车辆9项资源、物品22项资源检查通过；车库现有4测试及隔离生成的2 NBT/metadata与生产字节比较通过。它们均为 CPU/资产证据，不是游戏截图或真人驾驶验收。
- 刷新资产后的完整车辆 GameTest：`neo-final-vehicles2.log` 104/104，`forge-final-vehicles2.log`103/103，均正常保存退出。后续修改了 M5 回归夹具，最终冻结仍将刷新完整套件，不能仅依据较早回执打包。
- M5 回归首轮 `neo-final-m5-regression1.log` 132项118过14失败。13项为旧夹具只改 MilitaryAmmoReserve、没有建立新 typed 库存的前提；已按实际弹种修改 fixture 或构造真实旧 NBT，原断言保留。剩余小队成员归属失败已加诊断，等待复跑；没有为通过测试改成信任旧整数库存。
- 双端玩家指南、生成模板及制作资料已更新，每套224条当前源快照、30主PNG和65个相对链接核查通过。8/12步兵组合仍明确只是策略工厂，未宣称已自然派发。

当前仍需真实完成：最新双端 M5 132回归、完整 JUnit、正常服同源码两进程车库持久化、3/12/24车辆压力、三份旧M5副本的两进程读取与不增发、正常发行构建/资源泄漏核验和全新M6收集。队列遇失败即停止；日志名带 final 不等于通过。实际进度以 `.superpowers/military6-qa/*-queue-status.json`、原日志与独立 JSON 为准。v1.2.1仍由用户之后亲自拉取，当前未核实兼容性。

## 2026-10-28 最终冻结验收与交付入口（覆盖上文待办）

生产冻结后的22份有效回执见 `.superpowers/military6-qa/refresh-queue-status.json`，全部PASS。原final队列保留历史：Forge unit4因manifest类路径JAR未指纹引用类而沿用旧XML；已给测试任务声明真实运行时输入，unit5实际执行全部702项，生成晚于最后源码修改的全套XML。以下为真实自动验证结果；GL、真人驾驶和双客户端炮手尚未验收，不能将本节理解为全部客户端体验已通过。

| 项目 | NeoForge 1.21.1 / Java21 | Forge 1.20.1 / Java17 |
| --- | --- | --- |
| 完整M6车辆GameTest | 104/104，`neo-final-vehicles6.log` | 103/103，`forge-final-vehicles6.log` |
| M6上的M5完整回归 | 132/132，`neo-final-m5-regression5.log` | 132/132，`forge-final-m5-regression5.log` |
| 完整JUnit | 772，零失败/错误/跳过，`neo-final-unit2.log` | 702，零失败/错误/跳过，`forge-final-unit5.log` |
| 正常服两进程车库存档 | PASS，实际PID 21272→78704 | PASS，实际PID 43272→36908 |
| 有限步兵补弹与卸载 | 16次，APC真实卸载 | 16次，APC真实卸载 |
| 3/12/24车+12步兵 | 六份观测/场景JSON均PASS | 六份观测/场景JSON均PASS |
| 收集器当前回归 | 33/33，`collector-runtime-green.utf8.log` | 33/33，同名日志 |

运行日志均位于相应工作树 `.superpowers/military6-qa/`。车库存档目录为 `.superpowers/m6-final-{neo|forge}-garage2-20261028`；同目录prepare/verify采用相同生产源码，实际正常保存退出，核对UUID、HP、油弹、冷却、真实手持/收起枪及typed备弹、身份、原生槽/损失、容器与世界标记，不按附近空缺重造。

三份旧M5副本也各完成两个新的普通服进程，旧PASS仅作为输入，没有冒充M6进程：Neo city PID29572→12960，Neo world PID72284→65652，Forge city PID85868→38716。停服时所有实际加载维度车辆/车库/车队均为0，旧人员、基地、有限资源与世界编辑通过原审计；原M5源目录逐文件哈希不变。证据为各 `.superpowers/military6-legacy-*-20261027-entry1/military6-legacy-audit.json`，范围限已加载的QA副本，不是扫描用户所有未加载区块。

### 压力实测及边界

真实DedicatedServer、平坦地形、每车真实司机/军官及另12名步兵；100tick预热、600tick采样，4GiB堆。Windows11，AMD Ryzen9 9955HX、16核32线程；Neo JVM21.0.12.1，Forge JVM17.0.20.1。以下是本机单次观察，单位ms：

| 端/车辆数 | p50 | p95 | max |
| --- | --- | --- | --- |
| Neo / 3 | 25.56 | 50.13 | 502.26 |
| Neo / 12 | 9.86 | 50.34 | 717.95 |
| Neo / 24 | 5.41 | 271.57 | 672.58 |
| Forge / 3 | 3.52 | 48.82 | 506.17 |
| Forge / 12 | 4.18 | 49.96 | 631.39 |
| Forge / 24 | 6.51 | 61.13 | 810.79 |

六档均实际持续移动车辆及12步兵，车辆规划峰值≤2/tick、最大搜索37节点（规则上限2048），原步兵峰值8、最短观察间隔10tick；库存不增、准备后强载集合不变。每档必须同时有 `OBSERVED_PASS` 和 `SCENE_OBSERVED_PASS`，证据目录 `.superpowers/m6-final-{neo|forge}-stress-{3|12|24}-r2-20261028`。这些判据没有毫秒合格线；24车尤其Neo存在明显尖峰，不能承诺稳定20TPS。24为超过默认自动预算12的人工场景；客户端FPS/输入延迟和同车实际规划间隔未由观测器测得，不能用服务器耗时推算。

### 修复、资产与失败证据

普通无附加NBT召唤先取得有效空枪RED，再最窄保留fresh初始化；旧HandItems/Military*/Blackwatch*及坏typed记录继续不补发。最终召唤放在原生已就绪区块，跨维度炮弹使用已就绪X/Z及双方合法Y180。小队观察回调在成功清理后停止，避免将原版DISCARDED误报为人员损失；旧库存夹具按真实弹种修改，原行为断言保留。尝试合并M5/M6同进程曾有7项失败，未计入验收；最终使用项目现有独立新世界完整套件。

完成相邻14格CLEAR_AREA展开、真实停车下兵及受阻BLOCKED；修复原型体车辆use/火力输入与司机驾驶包连带拒绝。修正卡车座位/全身净空，并将NPC司机姿态读取改为客户端同步 `seatOf`。最终独立静态复核确认接线项已修复，报告 `.superpowers/military6-final-independent-review.md` 仍明确GL未测。

当前authoring SHA256 `df546a58efaddcdba90f717587876602c68e42af2bcc68a70d7a60fbec4542da`，两端BlackwatchModel SHA256 `2cca5e2f489f66a44dcf01763743037e64c6ef31e0cfd424caf8d02ef172b988`。两端各实际重跑8项几何测试、128组合、8场景9主图+2剖视、资源checks及4项车库测试；新证据 `visual-seat-sync-final-receipt.json` 对应当前源码，旧GREEN按历史保留。没有缩模、商业网格或商业截图进入发行资产。制作资料226来源快照/30主PNG由现有工具重新导出核验，CPU图不能替代游戏截图。

收集器精确绑定已核验canonical JAR、真实完整suite及唯一日志，严格验证Forge正式MixinExtras布局。新增两项RED/GREEN处理已跟踪轮转日志：仅 `logs/debug-1.log.gz` / `logs/debug-2.log.gz` 按经审查运行产物排除，原文件保留，不进入source/changes ZIP或tracked.diff；其他意外/私密改动仍拒绝。

### 发行与接续

正常UTF8、无QA init的 `assemble distributionZip` 最终日志为 `neo-final-release2.log` / `forge-final-release2.log`；随后实际 `package_release.py --profile bundled --verify-only`、最终冻结输入与collector门槛决定是否收集。核对 `.superpowers/military6-final-verified.json` 及 `.superpowers/military6-final-delivery-receipt.json`，不要依据较早BUILD SUCCESSFUL推断交付。新包约定 `F:/MC建筑/military6-vehicles-preview-20261028.zip`，实际ZIP SHA256/CRC以外部 `.zip.sha256` 和交付回执为准。

两端作者HEAD/分支、暂存/未暂存/未跟踪成果全部保留；完整源码、overlay、二进制diff及untracked清单一起交付。未提交、推送、fetch、pull、merge，未修改PCL/mods/用户档或旧M4/M5包；GeckoLib不重复附带。模组自身v1.2.1兼容性未知，由用户之后在另一个干净目录亲自拉取并记录真实SHA/加载器，再对照本包迁移。

唯一玩家下一步：在新测试世界按 `docs/MILITARY6_PLAYER_TEST_ZH.md` 验证真实驾驶、主副武器、换座、抢夺和双人炮手，记录实际GL/输入结果。

接续指令：按本文件最末“最终冻结验收与交付入口”及实际交付回执继续；项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

## 2026-10-28 玩家试玩修订 M6.1（当前现场，覆盖旧玩家下一步）

用户已实际试玩M6.0，提供六车物品栏、坦克第一/第三人称截图，以及 `F:/MC建筑/屏幕录制 2026-10-28 074618.mp4`。当前目标是继续修复图标/名称、真人视角/头穿顶、两侧空气墙、按现实比例放大、真实军人有限弹药后勤。随后用户明确简化：**游戏中停用燃油，保留代码、资源文件和旧档燃油数据以后使用；弹药继续完成**。用户再次说“继续”，授权持续有效。

旧M6.0交付目录/ZIP冻结，SHA256仍为 `6b22bc4d17253a33066bdf18f78f68377a73ba4a733dca89b345149fe3f5f991`；M5及用户PCL/mods/存档不写。实际仍在原两个dirty工作树，不能reset/clean或只取tracked diff。新版本为 `0.3.4-preview.20261028-military.6.1`，尚未产生新VERIFIED交付包。全部Gradle/游戏QA由根串行运行。

- 真实试玩实例日志确认NeoForge1.21.1/21.1.244、旧M6.0；用户实例Java25/GeckoLib4.9.3与开发QA Java21/缓存GeckoLib不同，只读核对未改设置。录屏已实际抽取26帧并打开，文件未修改，诊断缓存位于 `.superpowers/military6-qa/player-video-20261028/`。
- 新车体 L×W×H：卡车7.5×2.9×3.7、APC7×3.3×3.3、坦克8×4×3.4，炮管另计。模型、服务端尺寸/枢轴/座位/炮口/出口/观察口同源；当前authoring SHA256 `4ccafec7230accdac6d2d84c7ffbd043f2ece8d2b2848e9fe9eb98aa13523a21`。参考来自KNDS/Rheinmetall/Oshkosh官方数据表，原创车不是现实车型复制。
- 图标改原创32px像素图，六车两涂装及弹药/维修件可区分。显示名改完整车辆名称，旧 `_chassis` ID保留兼容；tooltip解释放置完整车、初始无弹、无需燃油和手持匹配实物补给。燃料罐registry/文件/NBT/API保留，但creative/search/正常配方、生成箱料、HUD与驾驶消耗/缺油判断停用，旧油137等数值保持精确。
- 两侧空气墙已有效RED：`neo-revision-red1.log` 玩家原生move请求-3，实际仅-.51137，撞到总AABB。总包围盒把侧伸炮管下的空气填实。现在广相仍保留总bbox，原生接触换全尺寸有向车体+独立炮管；其他实体保持原版。Neo原生六个接触场景已在 `neo-revision-green1/2.log`通过。Forge旧Mixin注解处理器不支持interface injector，已最窄改mapped default overwrite，真实AP及 `forge-revision-green1.log`43项运行通过；不是跳过AP。
- 真实闲兵现在到认证车库箱取实物，携带同一ItemEntity/ItemStack到已停靠关联原生车补弹/维修。无人、玩家占车、无料/路堵/核心失效均不远程补给、不增发。触发主弹≤20%或HP<35%；本次返补达到主弹≥50%且HP≥60%才恢复原mission/target。现有schema1可选SupplyReturn保留跨重启迟滞，库存仍唯一在车与真实物品。取消/死亡/正常停服在当地释放同一份物资，不远程返箱。
- 后勤/碰撞/停油首轮Neo24项7失败为真实RED及一个旧台阶夹具重叠；修复后Neo定向40/40通过（当时尚是旧恢复线）；最新Forge定向43/43包含三项迟滞用例全部通过。两端后勤正常服新两PID审计源码已就绪，**尚未实际运行**，不能用NBT交接冒充持久化。
- 玩家视觉独立测试最初15/15但真实Neo50帧揭示HUD重叠/镜头太远/能力手仍绘制；旧50帧仅自动断言通过，根已实际打开全部5页且不将其判最终视觉PASS。修复后HUD限宽换行避开actionbar、缩短F5距离、乘车前置阻止能力手绘制，独立两端19/19。新Neo `run-military6-client-neo-revision3`实际GL正在验证；Forge第一GL因AP失败无画面，后续须新dir补齐。任意第三方YSM替代骨架仍是单独未验证范围。
- 发现旧步枪/机枪/榴弹/火箭/导弹配方原料重复，已通过公开生成器有效RED再区分原料；现十类弹药各自唯一，增加十项真实RecipeManager/assemble场景，双端已编译，**实际服务器匹配仍待运行**。原自动炮/HE/AP/温压/感染弹字节保持。当前配方工具回执 `art/military-vehicles/ammunition-recipe-receipt-20261028.json`，不能把原38文件旧工具hash当当前。
- M6.1收集器两端42/42，保留JAR精确绑定/日志与suite去重/Forge内嵌依赖/完整dirty源码/旧包保护，新增inactive fuel与真实50帧GL+逐图复核门槛。当前还没有M6.1正式构建、完整回归或最终VERIFIED清单。

当前分工成果入口：`.superpowers/military6-{art,client,logistics,collision}-revision.md`、`military6-fuel-disabled.md`、`military6-fixture-revision.md`、`military6-revision-packaging.md`、`military6-revision-docs.md`。以各最末节/实际文件为准，不从较早“生产未写”恢复重做。所有生产已获准修改归根整合，代理离线编译不等于运行验收。

唯一工程下一步：完成新HUD/视距/手部的双端真实GL并逐图复核，然后当前完整车辆/弹药配方/旧M5/JUnit与两进程cargo审计，刷新资料/正式build并收集全新r2包。接续项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

### 2026-10-28 18:03 根整合进度（后续最终表覆盖本段状态）

- 双端先前各50张真实GL已打开全部boards，Neo revision3、Forge revision2均自动完成。复核发现Forge观察口遮罩覆盖原版快捷栏底板，已在两端将遮罩移至RenderGuiEvent.Pre、车辆文字留Post；新最终GL在当前串行队列末尾重新捕获，旧画面只作修复前证据。
- `neo-m61-vehicles-r2`完整137项实际3失败：三车夹具中放大坦克炮管已碰前卡车，隔墙爪击的旧墙放入新车身内。坦克起点改z=-20并断言至少1格净空，墙改z=-4且断言不与人/车交叠。定向37项后仅正常展开未到位；诊断确认480tick末坦克仍距目标1.8格、yaw18度，测试时限改560tick，保留完整到位、停车20tick及下兵断言。Neo `neo-m61-vehicles-r3`实际137/137通过。
- Forge `forge-m61-vehicles-r3`实际134/136，另两项后勤超时：交付一包就释放搬运兵被招作车组，以及返箱转角恰贴车头、原版导航节点取整进入实车。修复后同一真实搬运兵继续本车有限多趟服务，只在真实交付后续期；返箱先离开端面1格再侧移。没有增加库存、远程补给或放宽原物资/原任务断言。mode0新增必须由同一原生兵完成HE/AP/coax/维修的实际运行检查。
- 最新 `forge-m61-vehicles-r4`已实际136/136、exit0，18:00:21–18:03:24 +08；Neo同一r4仍在执行。当前队列 `.superpowers/military6-qa/revision-validation-r4.json`，共22次串行调用，包括完整M6/M5/JUnit、cargo/garage各真实两PID、3/12/24压力和最终双端50帧。仅已完成日志可算通过，队列未完成不打包。
- 双端真实CPU重新捕获8场景/9主图，均实际打开总览；制作资料刷新390份源快照、30张标明离线图、13图标、10项车辆active配方，双端只读verify为PASS。资料提取器现区分20%/35%触发与50%/60%出发线；工作台冲突修复后的机枪/火箭原料已同步玩家指南。双端collector本次实际各42/42、零失败，回执 `collector-m61-root-final1.{log,result.json}`。

本段没有宣称M6.1已交付。后续唯一下一步是跟进r4实际门槛，处理真实失败，最终逐图复核、冻结资料/正式bundled构建/全新r2收集；用户PCL、存档、mods与M5/M6.0旧交付保持只读。项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

### 18:28 审计脚本修订与r5（覆盖上段运行中的r4）

r4的双端完整车辆实际137/136、旧M5各132、JUnit807/737均通过且零跳过。随后Neo普通服cargo审计在前置OFFICER阶段5000tick超时，正常保存停服，报告为FAIL（不能因Gradle BUILD SUCCESSFUL算通过）。只读解析该隔离档MCA，实际原军官`8098faa0-484c-3cda-accf-a034755c3cb5`仍位于基地`492017.53808978776,81,23.2688228662029`，距中间路点`492017.5,81,23.5`约.234格；QA OfficerWalk错误地对所有中间点使用.2格终点精度，原生寻路已停在节点附近而无法推进。实际位置回执`.superpowers/military6-qa/neo-m61-cargo-r4-saved-position.{log,result.json}`，原失败世界只读保留。

两端仅审计源改为中间路点1格、最终路点仍.2格，与现有登车推进方式一致；增加隔离QA路点/运行goal诊断，生产登车、库存和持久化断言未改。根新队列`revision-validation-r5.json`现先跑cargo、garage和压力，再刷新完整M6/M5/JUnit及最后GL；保持全部mtime门槛，不拿修改审计前的r4日志冒充最终冻结。本轮正式构建、GL逐图review与r2交付仍待最终实际通过。

接续：先核对`F:/MC建筑/.blackwatch-worktrees/neoforge-military6/.superpowers/military6-qa/revision-validation-r5.json`及对应真实结果，继续当前串行验证；项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

### 18:52 r6真实货物重启进度（覆盖r5待办）

r5证明中间点修复有效，原军官已走至车库外场；随后最后20格直线导航的16格局部落点落近实车，军官仍未完成登车，审计按原5000tick上限真实FAIL。两端仅QA OfficerWalk追加车外侧路点，让原军官先侧移再沿车侧步行，不改生产、不瞬移、不降低登车距离或持久化断言。原r4/r5日志与世界保留，当前唯一队列`revision-validation-r6.json`。

Neo当前真实cargo两PID已PASS：preparePid34808、verifyPid62816，同原坦克UUID`48872d8b-6248-40cb-bf62-0ba13138986f`、原MOVE目标及16个人员槽。选定十发HE在正常停服前箱0/车8/在途2；停止事件在当地释放原ItemEntity `786c43af-775c-46bb-9f7b-356a0474eadc`，第二进程确认同UUID原物资与旧FuelTicks137、原SupplyReturn均持久化，再显式把同一2发散落物回收进认证箱，由真实原生兵实际搬运至车内10并恢复原MOVE。其他原始箱料移至真实stash且逐项保持；这不是重启时凭元数据重造两发。报告`.superpowers/m61-neo-logistics-r6/vehicle-logistics-audit.json`及`neo-m61-logistics-{prepare,verify}-r6.{log,result.json}`，两进程均正常保存。Forge第一进程已`PREPARE_STOP_OBSERVED`，第二进程仍待实际结果。

390份资料来源已再次刷新至当前QA源，双端只读verify均PASS。完整M6/M5/JUnit、车库两PID、六档压力及最终双端GL以r6后续真实完成项为准；r4的137/136、132/132、807/737保留实际已通过历史，不冒充更新审计后的最终冻结。正式构建与r2交付仍待全门槛完成。

接续：核对`F:/MC建筑/.blackwatch-worktrees/neoforge-military6/.superpowers/military6-qa/revision-validation-r6.json`，继续当前串行队列、最后逐图复核及全新收集；项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

### r6双端真实重启与六档压力已完成（最终完整回归/GL仍在队列中）

四组普通服两PID均实际PASS、正常保存全部维度。后勤：Neo34808→62816、Forge59060→51104，选定10发HE的箱/车/在途守恒、原ItemEntity在停服时当地释放、第二PID实物核对及回收后原MOVE恢复均通过，旧FuelTicks137保持。车库：Neo9320→71300、Forge21648→55288，每端16次原生步兵真实有限换弹、原16人员UUID、三车型一次性槽、真实APC卸载、原两存活车损伤/库存/冷却及毁车不复活均通过。对应报告位于各树`.superpowers/m61-<neo|forge>-<logistics|garage>-r6/`，原始准备/验证日志为同根QA目录`<neo|forge>-m61-<logistics|garage>-<prepare|verify>-r6`。

压力每档600个普通服真实tick、对应3/12/24原车及12名下车步兵，均持续移动、库存守恒、最多每tick2次车辆规划、每搜索不超2048节点，实际最大节点37；setup后强制区块未变，生产自动加载派发上限12未改。24车是明确的人工场景。下表仅为本轮同机实测，不是20TPS或客户端FPS保证：

| 加载器/车辆数 | tick p50 ms | tick p95 ms | 最大tick ms |
| --- | ---: | ---: | ---: |
| Neo / 3 | 23.4964 | 50.2114 | 1140.9792 |
| Neo / 12 | 4.7444 | 50.7401 | 1313.3566 |
| Neo / 24 | 5.7309 | 332.9230 | 1160.9736 |
| Forge / 3 | 3.7993 | 39.5241 | 810.6322 |
| Forge / 12 | 5.2389 | 47.5178 | 1068.4759 |
| Forge / 24 | 7.5715 | 52.5534 | 1306.3325 |

环境记录为Windows11 amd64、AMD Family26 Model68、32逻辑处理器、普通服最大堆4GiB，Neo Java21.0.12.1/Forge17.0.20.1；每档一次独立新世界，人工铺设并setup强制814个区块。原始测量与场景JSON为`.superpowers/m61-<neo|forge>-stress-<3|12|24>-r6/vehicle-fleet-{stress,scene}-<tier>.json`，均OBSERVED_PASS/SCENE_OBSERVED_PASS。这些PASS只说明场景身份、持续移动、资源及预算条件满足；有明显尾部卡顿，输入延迟和每车规划间隔未测，不能外推真实多人低延迟或完整自然城市场景的表现。

唯一下一步：让当前r6剩余完整车辆/M5/JUnit与双端新GL结束，实际打开所有新帧并写绑定review，冻结当前资料后正常UTF-8正式build、收集全新r2并独立核验CRC/SHA。未产生新M6.1交付前保持未交付状态。项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

## M6.1 最终自动验证与画面复核（覆盖上文未完成验证状态）

根串行r6队列22次实际运行全部PASS，结束于2026-10-28 19:29:09 +08。两端当前生产与测试源对应下列结果，零失败、错误、跳过；真实运行元数据和原始输出保存在各树`.superpowers/military6-qa/`，总回执为Neo同目录`revision-validation-r6.json`。没有继续重跑已通过门槛。

| 最终检查 | NeoForge 1.21.1 | Forge 1.20.1 |
| --- | --- | --- |
| 完整载具GameTest，含新停油/接触/后勤/十类真实配方 | 137/137 | 136/136 |
| M6.1上旧M5完整回归 | 132/132 | 132/132 |
| 完整JUnit，实际执行且零跳过 | 807/807 | 737/737 |
| 原生闲兵货物两进程停服/重启 | PASS，34808→62816 | PASS，59060→51104 |
| 车库派发/实际卸载/有限库存两进程 | PASS，9320→71300 | PASS，21648→55288 |
| 普通服3/12/24车，各600tick、12名步兵 | 三档均PASS | 三档均PASS |
| 当前生产真实Player/Camera/GL帧 | 50/50并逐图复核 | 50/50并逐图复核 |
| 交付收集器回归 | 42/42 | 42/42 |

最终客户端目录为`.superpowers/run-military6-client-neo-r6`及Forge树的`run-military6-client-forge-r6`。根实际打开每端5页共50帧，另原尺寸打开APC司机与坦克炮手图；确认快捷栏底板不再被观察口遮罩覆盖，车辆HUD与原版下车提示分开，驾驶/炮手前视、F5取景、原生/Mercer乘坐未出现先前头穿顶与能力手挡视线。复核文件`visual-review-neo-r6.json` SHA256=`ba55ec83d675bf8305e6589d3357d80f803d85427e93a5937d2d81573919b61c`；Forge=`525247cbe065b1e779309988d837783be3506f2635eb870a3feaf4e9c203528b`，文件均绑定各自真实report与完整50图SHA。队列中的`visualReviewPending`描述捕获完成当时的状态，随后独立review才是最终开图凭据，不重写原运行回执。

两端当前CPU捕获仍为8场景/9主图，制作资料390份源快照、30张明确标注的离线图、13原图标与10项active车辆配方经只读verify通过。已修复的关键玩法是：完整车名称与32px图标、放大同源模型/座位/炮口、原生接触空气墙、乘坐显示和HUD、真实有限多趟弹药/维修搬运、主弹20%或HP35%返补及50%/60%出发迟滞、十类弹药工作台输入冲突。燃油玩法停用，但原注册、源文件、有限API与旧FuelTicks保存。

最后交付步骤使用不带QA init的正常UTF-8 `assemble distributionZip`，然后`package_release.py --profile bundled --verify-only`、最终源码/证据冻结、全新`F:/MC建筑/military6-vehicles-preview-20261028-r2`收集与独立CRC/SHA核验。实际构建、成品JAR/ZIP哈希及最终是否交付，以包外`.superpowers/military6-revision-delivery-receipt-r6.json`和包内`evidence/VERIFIED-INPUT.json`为准；本文不预填未生成的成品哈希。旧M5及M6.0包保持冻结。

自动画面检查不等于真人驾驶/改键或两个远程客户端司机炮手手感；任意第三方YSM替代骨架、用户Java25/GeckoLib4.9.3组合、用户后续自行拉取的v1.2.1迁移保持未验证。车辆自身行驶仍用保守地形净空；24车实测有明显尾部卡顿，完整性能表见本记录上一节。正常推荐先按当前玩家指南在新世界试玩。本轮没有提交、推送、安装到PCL或改玩家存档。

接续：先读本节与`F:/MC建筑/.blackwatch-worktrees/neoforge-military6/.superpowers/military6-revision-delivery-receipt-r6.json`实际状态；若成品已交付，唯一玩家下一步是按`docs/MILITARY6_PLAYER_TEST_ZH.md`在新世界体验；项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。
