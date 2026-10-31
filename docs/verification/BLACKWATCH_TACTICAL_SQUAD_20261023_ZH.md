# 黑色守望战术小队实施与验证记录

状态：基础小队、模型与军事设施已在两端完成定向测试（各 15 单元 + 27 实际服务端场景），最终审阅与最新安装包尚待完成。用户又新增双阵营、可变人数、班排连/中队大队和军衔，正在调研设计；不能将原 4 人设计作为总体人数上限，也不能将新要求当成已实现。未授权自动提交、推送或公开发布。

## 基线与工作区

- NeoForge：`F:/MC建筑/blacklight-evolution`，独立新克隆，功能分支 `feat/neoforge-1.21.1-blackwatch-squads`；基线 `cdf5eb15628594ca82ad9b35bd11d3d34993cfc0`。
- Forge：`F:/MC建筑/blacklight-evolution-forge-1.20.1`，独立新克隆，功能分支 `feat/forge-1.20.1-blackwatch-squads`；基线 `a4f388282af0ad76875716d99ac2534c08c56ee3`。
- 已批准设计：`docs/superpowers/specs/2026-10-23-blackwatch-tactical-squad-design.md`。
- 实施计划：`docs/superpowers/plans/2026-10-23-blackwatch-tactical-squad.md`。
- 两端基线单元测试：`test --tests com.codex.blacklight.infection.InfectionRulesTest` 均通过，见各工作区 `baseline-full-ascii.log`。

## 任务与决定

- Task 1: complete — `task1-red.log` 因新 `BlackwatchSquad` 不存在而编译失败；实现后 `task1-green.log` 15/15，无失败/错误。此阶段未修改士兵的运行行为。
- Task 2: in progress — 服务端场景与 opt-in 构建接入先行，下一步观察缺少实体接入时的 RED。
- Task 2 RED：`task2-red.log` 因协调器与驻守点接口缺失而编译失败；接入后 `task2-first-run.log` 实际启动 Minecraft 服务端，11 项中 8 通过、3 失败。
- 首次失败分别是：测试服没有载入自定义第二维度；伪装夹具缺少 `BlacklightState.captureIdentity` 的身份记录；真实抓取没有即时清掉旧枪械阶段。前两项修正测试前置条件（使用真实下界、调用生产的身份捕获流程），未改预期行为。
- 新增实际周期与换弹移动场景后，`task2-regressions-red.log` 为 13 项中 11 通过、2 失败：立即抓取暂停与 64 tick 周期。周期观测为 `[36, 100, 165]`，暴露重启时多等一 tick；换弹移动场景实际通过，没有为猜测的移动问题修改产品代码。
- 当前正在验证抓取入口的即时暂停回调与周期重启修复；新增越过 48 格驻守边界会清除目标和许可的实际场景。结果以 `task2-green.log` 为准，尚未在本记录宣称通过。
- NeoForge `task2-green.log`：实际独立服务端 **14/14 通过**，进程退出码 0。包含实际下界、真实抓取入口、64 tick 周期、换弹位移、48 格追击边界等。未据此宣称真人联机或客户端观感已通过。
- 后续 `task2-home-cover-red.log` 的 16 项场景定位了跟随者驻守点初始化延迟、原生视线缓存泄露动态掩体后新位置，以及换弹导航被下一 tick 停止的问题。已分别改为每兵首个服务端 tick 初始化驻守点、观察时直接执行真实视线检测、保留换弹/等待阶段的导航。
- 周期与长时间搜索的夹具问题另有实际证据：普通感染牛在 48 tick 后会由现有感染系统替换为新的感染宿主，`neoforge-cadence-diagnostic.log` 明确记录原靶标 `removed=true`。最终夹具改为用生产 `inherit` 流程创建的成熟感染宿主，保留真实伤害、视线和生命判断；没有禁用产品的感染转换。周期场景使用可开火但不能走出的观察室，单独的开放场地场景验证换弹移动。巡逻按整个轨迹的最大位移验证，允许巡逻途中回到原处；重装周期观察窗口修正为 200 tick。
- NeoForge 最终定向结果：`neoforge-mature-targeted.log`，**15 项单元 + 17/17 实际服务端场景通过**，进程退出码 0。此前 14 场景阶段曾成功 `clean test assemble` 并核对 253 份捆绑动作资料；后续源代码修复后仍须重建最终安装包，旧中间产物不作为最终交付。
- Forge 已完成 `forge-red.log` → `forge-rule-green.log` 的规则 RED→GREEN（15/15）；`forge-integration-red.log` 确认缺少协调器接口的预期编译失败。逐文件平台适配后，`forge-targeted-first.log` 于 2026-10-23 13:42:52 记录 **15 项单元 + 17/17 实际服务端场景通过**，BUILD SUCCESSFUL。最终正常安装包仍待构建。
- Pre-flight: Task 2 consumes Task 1 的 `Contact`、成员与许可接口；Task 3 保持接口并适配平台，无签名冲突。
- Ruling: 使用两个新克隆隔离各平台 — 不触及用户原有仓库或实例 — 代价是多占一个工作目录。
- Ruling: 用户已授权按设计直接实现，计划按相同范围执行，不重复请求同一授权 — 代价是执行方法由本会话承担。
- Ruling: 用户没有授权 commit，保留未提交 diff 和验证记录，不运行技能中的自动提交/删除 scratch — 代价是任务进度依赖此记录和工作目录而非提交历史。
- Ruling: 新成员加入要求队长实际可见 — 避免隔墙编组和测试间跨墙串队 — 代价是被墙分隔的友军需要先相互接近可见才能自动成组。
- Ruling: 在现有 `ConsumeControl.releaseControls` 增加士兵暂停回调 — 抓取能在两次实体 tick 之间接管，必须即时清掉枪械阶段和许可 — 代价是控制入口增加一处针对士兵的生命周期衔接；不改变抓取门槛、奖励或轨迹。

## 环境与失败尝试

- 直接 GitHub 连接先遇 DNS/连接失败。发现 Windows 已配置 `127.0.0.1:19828` 代理后，Git 命令临时复用代理成功克隆并检出源码；未修改系统或 Git 全局代理。
- 缓存固定在 `F:/MC建筑/.blackwatch-tools/gradle`，通过命令环境让 Gradle/Java 复用上述代理。系统启动 JDK 为 25，项目工具链按各自配置准备 Java 21/17。
- 构建环境失败：中文路径的 Java `@argfile` 无法加载 Gradle Worker；直接 `-cp` 同一 JAR 可加载，临时英文路径 `@argfile` 也可加载。随后测试类也受到同一编码问题影响。已在本任务临时目录建立缓存和两个源码目录的英文 Junction 别名，从英文别名构建；未复制/移动原项目、改全局编码或改变测试断言。两端基线随后均通过。
- 本机复现入口为 `.superpowers/run-gradle.ps1`（任务 scratch，不分发），临时别名为 `%TEMP%/blackwatch-gradle-cache`、`blackwatch-neoforge`、`blackwatch-forge`，实际数据仍在本任务的 F 盘目录。

## 验证结果

当前两端分别 15 项单元 + 17 项实际服务端场景通过，证据见上述日志和 `build/test-results/test/TEST-com.codex.blacklight.entity.BlackwatchSquadTest.xml`（各 15，failures=0，errors=0）。最终审阅、正常打包与客户端观感尚未完成。

## 2026-10-23 范围增补

用户新增模型、动作与军事设施，且明确瞄准/射击/换弹可简做或暂缓。现场确认两端已有立方体模型、25×25 哨站及驻军、科技箱、军用电路与扫描仪配方。增补设计为 `docs/superpowers/specs/2026-10-23-blackwatch-model-facilities-design.md`，独立检查通过，用户明确“按设计直接实现，细节由你决定”。

- 已完成：普通/重装兵装备几何细化与简单姿态；保留原 atlas，生成 `docs/previews/blackwatch-model-facilities.html` 和 `blackwatch-equipment.png` 源几何示意，已读取 PNG 检查轮廓。该示意不是 Minecraft 客户端截图。
- 已完成：哨站保留 5 名驻军和两科技箱，新增床铺/供应区/标牌与补给；新增 17×17 检查站，2 普通 + 1 重装、一科技箱、一补给箱；共用旧池与结构 ID、生成频率，池权重 2:1。
- `neoforge-facilities-red.log` 实际 27 场景中 9 失败，均是缺少检查站/补给/新池配置；原 17 小队场景和扫描仪配方通过。新增资源后 27 通过。
- 后续真实标牌生存规则暴露无挂墙支撑，`neoforge-sign-support-red.log` 4 个旋转场景失败；将补给标牌移至背墙，`neoforge-expanded-targeted.log` 于 14:13:43 记录 **15 单元 + 27/27 服务端通过**。
- Forge 首次增补编译因旧版配方不是 RecipeHolder、箱子没有新版 getLootTable 接口而失败；适配为真实旧 API 后，`forge-expanded-targeted-green.log` 于 14:16:46 记录 **15 单元 + 27/27 服务端通过**。逐项迁移语言新 key，保留 Forge 自己的城市文案。
- Neo 两模板生成脚本重跑 SHA-256 一致：哨站 `bba19e404f26a235e79cb16223498cba17b10768b8c401b24761d19ba6f92b5c`，检查站 `e3f613881616236263182888e9feed80dd92b8fa59ea57318009c82425fabfdc`。Forge 使用自己的 DataVersion 与 `structures` 目录。
- 旧小队正常构建已两端通过，日志 `neoforge-squad-normal-assemble.log` / `forge-squad-normal-assemble.log`，各保留 253 个原有捆绑动作文件。增补后候选版本改为 `0.3.4-preview.20261023-blackwatch.2`，最新正常包尚待重建，不能拿 blackwatch.1 代替。
- Ruling: 沿用已授权的会话内实现，不重复询问执行方法 — 用户将细节交给实现者 — 代价是只有最后整体验证有独立代码审阅。
- Ruling: 保留代码立方体和原 atlas、只做简单姿态 — 遵循用户轻量动作要求且两端无新依赖 — 代价是细节与 UV/照明仍需实际客户端评审。
- Ruling: 哨站与检查站复用旧池且保留频率 — 避免叠加自然设施密度 — 代价是旧结构定位指令可能找到其中任一种。

## 最新目标：双阵营组织与军衔

用户明确“既有多种人数一个队长，也能多个小队组成中队大队，队列做好班排连、战术小队等及军衔，参考虐杀原形；军队和黑色守望两者都做”。这是比基础协作更大的新增范围，尚未实现。游戏原设定的可证实部分与模组编制/规模设计必须区分，当前正在核对一手来源；已完成的模型、设施和 15+27 验证保持有效。

已形成并通过独立 builder 检查的完整设计：`docs/superpowers/specs/2026-10-23-military-organization-design.md`；用户已明确批准，当前开始实施。设定调研在 `docs/research/PROTOTYPE_MILITARY_REFERENCE_20261023_ZH.md`。原 4 人上限保留为内部火力分组，不作为正式单位总人数上限。

基础独立代码审阅处置见 `BLACKWATCH_REVIEW_20261023_ZH.md`；重要的硬直轮转问题已补真实失败用例并修复。最终基础两端各 **15 单元 + 29/29 实际服务端通过**，日志 `neoforge-impact-review-green.log` / `forge-impact-review-green.log`。两个 minor 覆盖缺口保留在审阅记录。扩展前先构建并保存基础快照，避免接续时丢失已验证成果。

## 当前下一步

形成双阵营、可变人数/多层组织与军衔的具体设计；独立继续审阅和打包已完成基础。不能据早期 Task 1/2 的中间状态重做已完成实现，也不能用历史范围排除用户最新要求。
