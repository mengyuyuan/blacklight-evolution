# Blackwatch Tactical Squad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 用户已于 2026-10-23 明确授权按设计实现并执行定向验证，本轮采用当前会话直接执行。

**Goal:** 两个维护平台的现有黑色守望士兵具备有界巡逻、合法目标共享和交替射击能力，并保留现有伪装、伤害防御及抓取控制权。

**Architecture:** 将队伍成员、情报时效和开火轮转放入无 Minecraft 依赖的 `BlackwatchSquad`；`BlackwatchSquadSystem` 在各服务端维度内管理组队和实体接入；`BlackwatchSoldier` 的巡逻和战斗 Goal 使用该协调器。只有驻守点写入 NBT，队伍和火力名额均为临时状态。

**Tech Stack:** Java 21 / NeoForge 21.1.244 / Minecraft 1.21.1；Java 17 / Forge 47.4.10 / Minecraft 1.20.1；Gradle Wrapper；JUnit 5；独立 GameTest 服务端。

**Spec:** `../specs/2026-10-23-blackwatch-tactical-squad-design.md`（用户已确认）。

## Global Constraints

- 队伍上限 4；成组半径 16 格；巡逻半径 12 格；共享/脱队距离 32 格；搜索与脱队超时 100 tick；追击驻守半径 48 格。
- 2/3/4 人队同时最多允许 1/2/2 名成员瞄准和射击。现有普通/重装 64/76 tick 周期及第 12、20、28 tick 开火点保留。
- 控制状态和实际开火条件逐 tick 检查；共享转发不能刷新真实观察时间；不得以目标实时位置在无视线时导航。
- 成组发现最多每 20 tick；常规协调最多每 10 tick；不扫全维度，不强加载区块，不传送纠位。
- 两个平台独立分支、构建和验证。保持平台注册/附件 API、实体 ID、既有资源及伤害规则。
- 用户已授权第 7 节定向单元/独立服务端验证。没有授权自动提交、推送、发布、合并或向维护者发送消息；所有工作保留为本地未提交改动。
- 两个新克隆分别作为本任务独占工作区，不触及其他现有项目或实例。使用系统已配置的 `127.0.0.1:19828` 代理仅作命令参数，不改全局代理配置。

## Review Focus

- 伪装有效但队友已见到同一个玩家：接收者的目标过滤仍生效（任务 2 服务端场景）。
- 共享目标长时间在墙后：位置快照和原时间戳到期，不通过转发续期（任务 1 单元、任务 2 服务端场景）。
- 占用名额的士兵被抓取或卸载：立即释放许可，剩余成员仍能轮到（任务 1 轮转、任务 2 生命周期场景）。
- 旧 NBT 没有驻守点或实体被送到另一维度：安全初始化驻守点，不能向旧维度坐标无限归队（任务 2 持久化场景）。
- 友军恰在开火 tick 进入射线：停止该发，不穿透友军，仍使用真实方块/实体检测（任务 2 射击场景）。

## 文件与职责

- 新增 `src/main/java/com/codex/blacklight/entity/BlackwatchSquad.java`：成员上限/稳定队长、目标快照及过期、轮转名额。
- 新增 `src/main/java/com/codex/blacklight/entity/BlackwatchSquadSystem.java`：有界本地编组、驻守中心、实体有效性、生命周期及位置建议。
- 修改 `src/main/java/com/codex/blacklight/entity/BlackwatchSoldier.java`：NBT 驻守点、巡逻 Goal、服务器协调、射击周期和最终合法性。
- 修改 `src/main/java/com/codex/blacklight/BlacklightNeoMod.java`（Forge 对应实际入口）：注册平台生命周期事件。
- 新增 `src/test/java/com/codex/blacklight/entity/BlackwatchSquadTest.java`：实际规则行为，不测试源码字符串。
- 新增 `src/blackwatchTest/java/com/codex/blacklight/gametest/BlackwatchSquadGameTest.java`：实际 Minecraft 实体及场景集成验证。
- 修改 `build.gradle`：仅 `-PblackwatchTest` 启用定向 GameTest 源目录及既有测试结构资源，普通包仍排除 QA 类。
- 修改 `gradle.properties`：分别标识本地候选 `0.3.4-preview.20261023-blackwatch.1`；不创建公开 Release。
- 新增 `docs/verification/BLACKWATCH_TACTICAL_SQUAD_20261023_ZH.md`：持续记录命令、结果、决定和交接。设计、计划随两端源代码交付。

### Task 1: 无平台依赖的小队规则

**Files:** Create `BlackwatchSquad.java`; Test `BlackwatchSquadTest.java`，位置如上。

**Interfaces:**
- Produces: `BlackwatchSquad.add(UUID): boolean`、`remove(UUID): void`、`members(): List<UUID>`、`leader(): UUID`。
- Produces: 嵌套 `Position(double x,double y,double z)` 与 `Contact(UUID observer,UUID target,Position position,Position observerPosition,long observedAt,boolean retaliation)`；`observe(Contact): void`、`contact(long now): Optional<Contact>`、`forget(UUID): void`。
- Produces: `requestFire(UUID,long now): boolean`、`releaseFire(UUID): void`、`hasFirePermit(UUID,long now): boolean`。许可有效期覆盖 29 tick 的瞄准/射击段；队列轮转，不允许非成员申请。

- [ ] 写行为测试：第 5 人被拒；重复成员不重复；稳定队长及移除接任；过期/未来情报无效；转发不更新观察时间；非成员报告被拒；两人/四人火力上限；归还和过期可被下一成员取得；死者或被控制者释放后队列不阻塞。
- [ ] 运行 `gradlew.bat test --tests com.codex.blacklight.entity.BlackwatchSquadTest --no-daemon --console=plain`，确认因缺少新规则而失败，保存日志。
- [ ] 实现上述接口，保持 Java 17 可编译，集合对外不可变。
- [ ] 运行同一定向测试，预期全部通过；在验证记录中填写实际结果。

### Task 2: NeoForge 实体、巡逻与真实服务端验证

**Interfaces:**
- Consumes: Task 1 全部接口。
- Produces: `BlackwatchSquadSystem.tick(BlackwatchSoldier): void`、`squad(BlackwatchSoldier): BlackwatchSquad`、`contact(BlackwatchSoldier): Optional<Contact>`、`release(BlackwatchSoldier): void`；实体可读取的 `home(): BlockPos` 与 `validTarget(LivingEntity): boolean`。
- 平台事件订阅卸载和停止，静态索引只保存 UUID 和位置，不保存实体/Level 强引用。确切导航辅助方法名留给实现者。

- [ ] 先写定向 GameTest 和 opt-in 构建接入，使用现有 `wall_test` 结构，覆盖组队/巡逻、遮挡/搜索、轮转与换弹、伪装、受击报复、友军挡线、抓取/释放、移除/重选队长、旧档驻守点与跨维度状态隔离。必须断言实际实体状态/位置/承伤及生产代码效果。
- [ ] 执行 `gradlew.bat runGameTestServer -PblackwatchTest -PgameTestRunDirectory=run-blackwatch-gametest --no-daemon --console=plain`，确认功能缺失导致失败并留证。
- [ ] 实现有界协调器，复用原生导航与既有目标/伪装/控制权逻辑；修改士兵 Goal，开火许可结束后立即换弹并释放名额，控制/无视线/撤离不补发攻击。
- [ ] 实现驻守点 NBT，附带维度标识；缺键或维度不匹配时初始化到当前位置。
- [ ] 注册清理生命周期；普通 JAR 排除 QA 类；只在 `-PblackwatchTest` 添加测试源码/结构。
- [ ] 重跑定向单元、GameTest，核对全部预期。对发现的每个实际缺陷先补能复现的失败用例再修复。
- [ ] 运行 `gradlew.bat assemble --no-daemon --console=plain`，检查 bundled 安装 JAR 资源和 QA 排除，保留产物与 SHA-256。

### Task 3: Forge 独立适配、交付与最终审阅

**Interfaces:** Consumes Task 1/2 最终规则及行为；平台 API 以 Forge 目标源代码为准。

- [ ] 从 `forge-1.20.1` 建立独立本地功能分支；逐文件迁移新增逻辑和测试，不覆盖加载器、Java、网络和其他现有内容。
- [ ] 先运行对应定向用例确认 Forge 基线缺少新行为，再完成 Forge 的事件、附件/capability、实体同步定义和 GameTest API 适配。
- [ ] 用 Java 17 分别执行定向单元、独立服务端 GameTest 和 `assemble`；核对最终重混淆安装 JAR，不用 slim 中间产物代替。
- [ ] 更新两端设计/验证记录：真实命令、用例、失败与修复、未提交文件、产物路径、哈希、尚未完成的真人联机/客户端观感验证。
- [ ] 按 `requesting-code-review` 技能派一个新审阅代理，对两个未提交 diff 和设计做整体验证；只修复有实际影响的重要问题并补定向回归。
- [ ] 最后执行 `git diff --check`、定向回归和必要构建；整理两个可玩安装包与源改动补丁，不自动提交或公开上传。

## 执行说明与自检

- 用户确认设计时已授权完整实施与专项验证；不再另起一轮相同范围的审批。实施计划是落实已批准方案，不扩大目标。
- 三项任务覆盖设计第 4–7 节；任务 2 使用任务 1 的快照和名额接口，任务 3 保持同一接口仅适配平台调用。
- 采用新克隆的两个独立工作目录隔离本任务，避免修改用户已有分支和其他项目。因为没有授权提交，技能中的自动 commit、基于 commit 的进度工具和最终删除 scratch 调整为未提交 diff 与保留验证记录。
- 基线及新测试输出写入本工作区日志后只读取尾部。工具或环境失败与功能断言失败分别记录，不将依赖下载失败算作功能 RED。
