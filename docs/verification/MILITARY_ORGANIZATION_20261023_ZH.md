# 双阵营编制与军衔实施记录

状态：已按批准设计完成双端实现、重要修复与定向验证，交付本地开发预览。目标是军方+黑色守望、1–32人单队、班排连/中队大队、8级游戏化军衔、层级命令/存档与两方设施。原4人仅作为火力分片，不再作为总编制上限。

## 真实入口

- 项目：https://github.com/mengyuyuan/blacklight-evolution
- Neo工作区 `F:/MC建筑/blacklight-evolution`，分支 `feat/neoforge-1.21.1-blackwatch-squads`，HEAD `cdf5eb15628594ca82ad9b35bd11d3d34993cfc0`。
- Forge工作区 `F:/MC建筑/blacklight-evolution-forge-1.20.1`，分支 `feat/forge-1.20.1-blackwatch-squads`，HEAD `a4f388282af0ad76875716d99ac2534c08c56ee3`。
- 已批准spec：`docs/superpowers/specs/2026-10-23-military-organization-design.md`；plan：`docs/superpowers/plans/2026-10-23-military-organization.md`。
- 原成果：`BLACKWATCH_TACTICAL_SQUAD_20261023_ZH.md` / `BLACKWATCH_REVIEW_20261023_ZH.md`；最终基础两端15单元+29真实服务器场景通过，硬直Important已RED→GREEN。
- 未授权提交、推送、PR或发布。保持两端未提交工作树，扩展前保留基础构建/源码快照。

## 当前任务

Task1：两端已完成。`military-task1-red.log` 确认新类缺失而编译失败；首实现 `military-task1-green.log` 实际 38 测试中18失败，栈定位根节点null父UUID被TreeMap拒绝；修正遍历终点后 `military-task1-green-fixed.log` 为 **19组织 + 4预算 + 15旧火力规则 = 38通过**，XML均failures=0/errors=0。独立 Forge Java17 的 `military-task1-forge-java17.log` 同样通过；该时点尚未增加Task2的第16个火力许可测试。

Task2：NeoForge已完成，Forge运行接入留到Task5逐项适配。`military-task2-red.log` 确认尚无共同实体/军方注册而编译失败。抽取共同 `MilitarySoldier`，保留旧Blackwatch类与ID，新增 `RegularSoldier` 的两个注册ID；Faction/Rank/Profile分别表达阵营、军衔、兵种数值。军衔和unitId真实NBT，现有抓取、硬直、伪装类型检查均接入共同基类；原4人协调类名称暂留，参数已接受共同实体且招募限制同阵营。

`military-task2-first-integration.log` 的38场景中3失败。保留产品逻辑并在 `military-task2-fixture-diagnostic.log` 记录实际 **age=0、phase=1、aim=16、health=max=5000**，定位现有 `CombatScale.join` 为感染者加5倍生命；新测试硬编码1000错误。改为比较真实加入世界后的初始生命，不禁用缩放、不放宽射击年龄。`military-task2-green.log` 于 **15:42:15** 实际 **39规则(19组织+4预算+16火控) + 38/38服务端**通过，含军方普通/重装周期与第32tick真实末发、军衔8级保存、两方友军、身份、抓取/硬直、取得许可后友军临时进入射线。

两方模型保持原atlas，军方灰绿色调、简化滤罐与较轻装备，8组游戏化军衔几何按同步rank切换。已调整徽记到重装胸甲前，`military-task2-model-compile.log` compileJava通过；已读取更新后的双阵营 `docs/previews/blackwatch-equipment.png`。这不是客户端截图，UV/照明/徽记实际观感仍未实机验收。

Task3：NeoForge已完成。`military-task3-red.log` 为缺少SavedData/System的接口RED；首次实现因重载 `distanceToSqr` 方法引用推断成Object而编译失败，明确Comparator类型后 `military-task3-storage-runtime.log` 实际46场景只剩3个未实现指令失败，其余存档/自动8+4/代理/卸载场景通过。完整接入Brigadier草稿事务后，`military-task3-commands-first.log` 于 **16:20:30** 实际 **39规则 + 46/46服务端**通过。包括真实12人单队、真实72人组织为3排9班、权限不足无写入、混阵营转移失败原名册完整、正式与代行切换、运行时卸载保留SavedData、实际下界组织数据隔离。

Task3 API：`MilitarySavedData.get(ServerLevel)/organization()/replace(...)`、`load(CompoundTag,HolderLookup.Provider)`、`save(...)`；Schema=1，Units按UUID稳定写Id/Faction/Type/Name/Capacity/X/Y/Z/Parent/Commander/Members(Id,Rank)/Order，另存Automatic单位UUID。不重复保存children；恢复时按相同领域规则重建，拒绝重复/未知阵营/循环非法边。`isDirty`包含纯模型revision，避免漏存。

`MilitaryOrganizationSystem` 每维度弱索引只留SavedData、UUID、时间和预算；`ensureMember`、`organization`、`data`、`budget`、`commander`、`transaction(Predicate)`、`manual`为后续接入入口。transaction私有copy全部成功才replace并同步已加载实体unitId/rank；自动空叶子清理，主动unassign禁用该实体自动重入（MilitaryAutomaticJoin，旧档默认true）。死亡/销毁/换维度移出持久名册，普通卸载只清运行时；临时控制不改正式军衔或指挥官。

Task3完成时的历史状态：Neo源版本设为 `0.3.4-preview.20261023-military.1`，当时尚无该版本最终安装包，Forge只有基础及Task1纯规则、层级移动未接入。当前进展以本记录末尾新证据为准，不能把这个历史时点当作当前下一步。

基础快照已保存 `F:/MC建筑/blackwatch-foundation-snapshot-20261023`。两端blackwatch.2正常assemble及原package_release --verify-only通过，各253原动作文件一致；自定义快照核对QA排除、两结构、Java主版本65/61。每端37个修改/新增源码文件，patch含未跟踪文件，`git apply --check --cached`对原索引和`--reverse --check`对当前工作树均通过，索引未改变。快照内有匹配GeckoLib、LICENSE/第三方notice、日志、哈希与manifest。构建期间新增设计文档曾使Neo ZIP核查缺文档，刷新distributionZip后核查通过；未修改安装JAR以掩盖问题。

Task1公开预算接口：`MilitaryWorkBudget.enqueue(Kind,UUID,long)`、`drain(Kind,long): List<UUID>`、`remove(UUID)`，Kind为NAVIGATION(8/10tick)、DISCOVERY(4/20tick)，重复合并、FIFO、回退时钟可恢复。组织提供不可变UnitView、copy事务草稿、resize/空节点removeUnit/named/rootOf/ancestors辅助入口；其他接口同plan。

## 已作决定

- Ruling: 沿用直接实施与最后独立审阅 — 用户批准设计并将细节交给实现者 — 代价是各任务不各买一轮独立审阅。
- Ruling: 不自动commit或删除scratch，阶段证据保存为源码快照/日志/本记录 — 用户未授权提交 — 代价是接续需以现场和记录而非提交历史核对。
- Ruling: 保留原代码/存档资源ID，将4人分组作为内部火控 — 用户要求更大且可变编制 — 代价是旧Blackwatch命名可能作为兼容入口暂留。
- Ruling: 每维度纯组织最多2048节点、显示名64字符且不重复 — 限制无实体空节点记录增长并给命令稳定名字 — 代价是极大型管理场景需另行评估容量。
- Ruling: 暂保留BlackwatchSquadSystem和BlackwatchModel/Renderer类名作为现有注册/测试入口，类型扩展到MilitarySoldier — 减少无功能收益的接口搬迁 — 代价是内部名字比实际阵营范围窄，后续可单独重命名。
- Ruling: 管理维护新增resize/unassign/detach/remove空节点与inherit命令 — 让可变人数和手工编制能够实际维护 — 代价是命令面增大；remove不删除有成员或有子单位的树。
- Final: Ruling: 客户端材质/UV/军衔/动作只提供源码预览并明确未实机验收 — 目前证据不足以判定实机观感 — 代价是仍需玩家客户端检查。
- Final: Ruling: 广域复杂地形和自然生成布局留待抽查，普通楼梯在本次修复 — 区分实际已测场景与广域表现 — 代价是复杂地形仍可能有局部问题。
- Final: Ruling: 不将压力结果当真人联机/整合包兼容或20TPS保证 — 无这些环境证据 — 代价是实际部署仍需试玩与性能评估。
- Final: Ruling: 最终包必须实际正常构建并核验重混淆/QA排除/动作/补丁后才交付 — 审阅未代替产物验证 — 代价是核验失败就不能交付中间包。
- Final: Ruling: 车辆/直升机/超级士兵/完整战役留到后续 — 已批准设计明确不在此次增量 — 代价是本包不含这些内容。
- Final: minor (deferred): inspect尚不显示军衔，保留现有军衔命令和实体标识。
- Final: minor (deferred): 未实际测试实体卸载重载/换维度往返；现有证据只到运行时清理和跨维度数据隔离。

## 唯一下一步

Task4 NeoForge已完成。`military-task4-red.log` 缺少编队接口；初次运行发现靠墙槽位没有可站位置，增加邻近安全站位并按正式指挥关系修正旧夹具。`military-task4-green-candidate.log` 17:03:12通过39规则+55实际场景，涵盖12/32叶子火力组、24/72层级步行、命令继承覆盖、未加载目标、原始警报快照/64格/100tick以及已有合法观察后生效伪装。

独立opt-in `military-task4-216-stress.log` 17:05:50全部56实际场景通过。216实体组织3连大队，1310加速GameTest tick实际耗时36053.7266ms，216人均移动超过8格，6287次路径服务、每tick峰值8、单兵最小间隔10tick，全部216成员获服务。该夹具为平坦加载区域，不能据此承诺普通世界/整合包20TPS。

Task5两端已完成。Neo `military-task5-facility-runtime-red.log`实际17失败，明确缺军方设施、军衔和部署；实现旋转相对偏移计算驻军唯一组织、保留原5/3人数、新军方8/4驻军、40/16分布和单向8chunk避让后，`military-task5-facility-first.log`17:15:51实际64场景全绿。

Forge1.20独立适配了同步数据、附件、SavedData、战利品箱/配方、事件与渲染API。首轮有私有exclusion getter与旧箱getter差异，测试改用实际注册对象Codec和原生NBT；不是修改产品数据掩盖错误。`military-task5-forge-runtime.log`5失败；新隔离世界排除旧固定起点残留后4失败，诊断记录成员只tick351/381次、loaded=true且ticking=false，证明1.20的测试区块票据边界停止实体tick。仅QA行军场景显式加载完整96/128区域，生产路径仍getChunkNow/hasChunkAt，不创建票据。

进一步消除了火力组重复空间发现：只按已知≤32名册分组，唯一空间发现入口受4/tick限制；原硬直夹具在tick4假设全部入队与此预算冲突，延到tick30后验证真实队首硬直释放。原生导航重算只排队，不递归执行后被原生null返回覆盖；stop取消过时移动请求。两端当前共享逻辑等价。

当前证据：Forge `military-task5-forge-loaded-arena.log` **17:39:18，39规则+64/64实际通过**；Neo `military-task5-neoforge-loaded-arena.log` **17:42:39，39规则+65/65实际通过**（64标准+216压力）。最终216场景1310加速tick耗时31887.308999ms，216人全部走>8格，6443次路径服务、峰8/最短10/全部216服务。此前36.05秒为另一轮历史测量，不把差异解释成普通世界性能提升。

Task6实施与回归已完成：独立审阅3Important、2Minor，无Critical；六个真实复现取得正确RED（18:06:38）后，统一修复坐标中心/三维导航/行进代行。最新 Neo `military-final-review-green.log` **18:22:31，39+71全绿**；Forge `military-final-forge-flat-green.log` **18:36:38，39+70全绿**。Neo额外216压力为1310加速tick/10218.2083ms/6374次路径服务，全部216走>8格、peak8/min10。详见 `MILITARY_REVIEW_20261023_ZH.md`。

Forge间歇失败已定位为QA默认普通地下世界：保存的Anvil中原空气射线和行进点出现天然砂砾。确认全新最终世界实际为minecraft:flat后70/70通过，环境JSON保留证据；只修改QA准备，不改兵种数值或降低行为断言。实际实体区域ticking就绪后才开始计时/放置；只有测试源集创建区块票据。

两端 `military-final-assemble.log` 正常无QA `clean assemble`已通过（Neo1m10s、Forge1m36s）。Neo原始 `package_release.py --verify-only` 已验证253动作/902生产类/13842生产资源；文档最终刷新与两端产物/源码补丁交付核验正在进行。

Task6最终产物核验已通过：两端原 `tools/package_release.py --verify-only` 均通过，Neo902生产类/13842生产资源，Forge929生产类/7634生产资源，各253原动作文件逐字节一致。正常JAR排除所有QA类和arena模板；Neo新类Java主版本65、Forge61，Forge最终包含SRG重混淆tick方法。两端各82个修改/新增文件，源码patch同时通过对原索引前向检查和对当前工作树反向检查；两端索引仍为空、HEAD与分支不变。

本地交付：`F:/MC建筑/military-organization-preview-20261023`，配套同名ZIP。各平台目录含可安装bundled JAR、匹配GeckoLib、LICENSE/第三方说明、源码差异patch与ZIP、实际验证日志及SHA256SUMS；根manifest记录平台/基线/分支/字节码/动作数/补丁检查及各文件哈希。preview含双阵营源码几何和四设施离线HTML。原基础快照保持独立，不当成最终双阵营包。

唯一下一步：用所选平台JAR与匹配GeckoLib在客户端试玩验收材质/军衔/简单动作与自然地形表现；两项Minor与联机/整合包边界见审阅记录。当前获准代码实施和定向服务端验证已完成，不重做已过任务，不自动提交/推送/发布。
