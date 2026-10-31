# 黑色守望模型与军事设施实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 两平台完善士兵装备轮廓、升级哨站并加入检查站和补给循环，保留已验证的小队。

**Architecture:** 沿用原版 HumanoidModel 与原创 atlas；结构由标准库脚本生成确定性 NBT，通过现有 jigsaw 池投放。GameTest 调用实际池元素放置并检查真实驻军/箱子。

**Tech Stack:** Java 21 / NeoForge 21.1.244 / MC 1.21.1；Java 17 / Forge 47.4.10 / MC 1.20.1；Python 标准库；现有 JUnit、GameTest。

**Spec:** `docs/superpowers/specs/2026-10-23-blackwatch-model-facilities-design.md`，用户已确认并将具体细节交给实现者。

## Global Constraints

- 保留两个实体 ID、伤害/碰撞/小队规则与 PHASE；动画只做简单姿态。
- 保留结构 ID、群系与 32/12 间距；池权重哨站 2、检查站 1。哨站 4 普通 + 1 重装、两科技箱 + 一补给；检查站 2 普通 + 1 重装、一科技 + 一补给。
- 不新增依赖、强制区块加载或补兵机制；原创资产沿用 GPL-3.0-only；不提交/推送/公开发布。
- 两端使用实际版本目录与 API；记录真实失败和结果，不宣称客户端或真人联机验收。

## Review Focus

- 陡坡、海岸、水面等真实地形：原生 jigsaw 高度适配应保持有限范围，广域地形尚需真人抽查。
- 四种旋转后的守军支撑、头部净空及箱子可开：Task 1 实际放置检查。
- 结构模板中实体持久性及首次 tick 驻守点：Task 1 检查后以原小队场景回归。
- 客户端 UV、普通/重装轮廓和持枪方向：Task 2 生成实际源几何预览，客户端实机状态明确列为未验证。
- QA 类/设施测试模板误装入玩家包、Forge 路径错用新版本：Task 3 检查正常 JAR 与实际两端测试。

### Task 1: 设施数据及实际放置验证

**Files:** 新增 `src/blackwatchTest/java/com/codex/blacklight/gametest/BlackwatchFacilityGameTest.java`；修改 `tools/build_blackwatch_outpost.py`；新增检查站 NBT、补给 loot；修改现有哨站 NBT、池和 zh_cn/en_us 语言。测试夹具扩大到足够覆盖 25 格模板，独立命名并排除发行包。

**Interfaces:** 数据 ID `blacklight:blackwatch_checkpoint`（模板）、`blacklight:chests/blackwatch_supplies`（loot）；原池仍 `blacklight:blackwatch_outpost`；脚本执行一次生成两模板。测试使用真实 `StructurePoolElement.place`，平台方法签名由实际依赖确定。

- [ ] 增加设施 GameTest：实际注册池包含两尺寸与权重、哨站/检查站四向原生池放置、驻军兵种/持久性/安全空间、箱子实际开出科技或补给、扫描仪实际配方可匹配。
- [ ] 运行 `runGameTestServer -PblackwatchTest -PgameTestRunDirectory=run-blackwatch-gametest`，记录缺少检查站/补给/家具导致的 RED；夹具或 API 编译失败与业务 RED 分开。
- [ ] 重构脚本为明确的模板构建器，保留原哨站主体，添加床铺/供应区/翻译标牌；生成 17×17 检查站及补给表与池权重。
- [ ] 重跑同一实际测试至 GREEN；必要时将放置缺陷细化为测试。脚本重跑 SHA-256 一致。

### Task 2: 装备几何与轻量姿态

**Files:** 修改 `src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java`；新增 `tools/preview_blackwatch.py` 与 `docs/previews/blackwatch-model-facilities.html`。

**Interfaces:** 保持 `BlackwatchModel.layer(boolean heavy): LayerDefinition`、构造和 `setupAnim`；装备使用命名子部件，预览从同一 Java 源读取立方体尺寸，从生成脚本读取布局。不引入另一套运行时几何数据。

- [ ] 细化头盔、护目镜、双滤罐、胸挂、腰带、膝甲/靴子、背包/天线与步枪；重装附加厚甲和供弹背包/加厚枪械。
- [ ] 保持原 atlas，调整简单待机/瞄准/轻微后坐/换弹左手姿态。按低影响可逆几何改动处理，不添加仅复述立方体名称的测试。
- [ ] 生成并检查普通/重装、设施布局的独立 HTML/SVG 源几何预览；`compileJava` 成功，记录实际客户端观感未验收。

### Task 3: Forge 适配、整体回归与交付

**Files:** 对应 Forge 文件，双方 README/玩法和原验证记录，必要的 `build.gradle` QA 排除。

- [ ] 迁移平台无关模型/脚本/资源，保留 Forge `structures`、`loot_tables`、原生放置 API 和配方 `item` 字段。
- [ ] 两端各运行 `test --tests com.codex.blacklight.entity.BlackwatchSquadTest runGameTestServer -PblackwatchTest -PgameTestRunDirectory=run-blackwatch-gametest`；15 单元和原 17 小队场景全部通过，加上实际设施场景。
- [ ] 派一个新上下文的整支变更审阅者，检查原小队计划及本计划 Review Focus、两个未提交 diff 和新增文件。对重要问题一次性补 RED→GREEN 回归并修复。
- [ ] 两端正常 `clean assemble` 并验证 JAR 排除 QA、版本/类文件、保留 253 个捆绑动作文件，Forge 重混淆包。
- [ ] 更新原验证记录/计划复选框；提供两平台安装包、源补丁、预览、依赖/哈希与真实未验证项。保留未提交变更和 scratch，不自行提交或删除唯一记录。

## 执行约定

用户已明确“按设计直接实现，细节由你决定”，沿用会话内直接实施及一次最终审阅，不重复要求实施方法审批。原小队已通过的 15+17 测试不重写；因新增资源与最终审阅修复而做集成回归。每任务结果在既有验证记录追加。无自动提交授权，计划中不包含 commit。
