# military.5 本地交付收集器

脚本只核对当前源码、正式构建与已有验证证据，然后在 `F:/MC建筑` 直接子目录创建全新的本地交付。默认目录为 `military5-reference-homes-preview-20261025`。已有目录、`.staging`、ZIP或校验文件均拒绝覆盖，不自动删除失败现场。它不运行Gradle，不改PCL或玩家存档，不提交、推送、创建PR或上传。

当前适用工程：`.blackwatch-worktrees/neoforge-military5` 与 `.blackwatch-worktrees/forge-military5`；版本 `0.3.4-preview.20261025-military.5`。两端脚本和测试应相同。它要求Neo作者HEAD `996dffdf2d56091cbb01ed9f871bc440ed571a1a`、Forge作者HEAD `59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4`；不会把旧military.4证据改名后算作本版通过。

## 本版范围

此工具只收集military.5的生产代码与本轮真实验证证据，功能是否完成以该代码和通过的验证为准。新增载具、玩家驾驶与抢夺、新增重武器和Prototype外观扩展属于后续military.6设计；即使相关方案进入本版`docs`，也只作为制作资料，不能据此宣称military.5已实装这些功能。工具不会拉取、合并或更新作者版本。

## 执行

先完成生产源冻结、两端正式构建、验证和资料刷新。最终JUnit数量必须从本轮完整XML实际汇总，不能使用中间669或历史554/551作为未经核实的固定值。下面在Neo工程执行，读出实际数量后先预检：

```powershell
$military5NeoCount = (Get-ChildItem -LiteralPath 'F:/MC建筑/.blackwatch-worktrees/neoforge-military5/build/test-results/test' -Filter 'TEST-*.xml' | ForEach-Object { ([xml](Get-Content -LiteralPath $_.FullName -Raw)).testsuite.tests } | Measure-Object -Sum).Sum
$military5ForgeCount = (Get-ChildItem -LiteralPath 'F:/MC建筑/.blackwatch-worktrees/forge-military5/build/test-results/test' -Filter 'TEST-*.xml' | ForEach-Object { ([xml](Get-Content -LiteralPath $_.FullName -Raw)).testsuite.tests } | Measure-Object -Sum).Sum
python tools/collect_military5_delivery.py --check-only --neoforge-junit $military5NeoCount --forge-junit $military5ForgeCount
```

`--check-only`不创建交付目录、staging、ZIP或校验文件。全部门槛通过并确认源仍冻结后，用相同参数去掉该选项才实际收集：

```powershell
python tools/collect_military5_delivery.py --neoforge-junit $military5NeoCount --forge-junit $military5ForgeCount
```

如果输出名称已经存在，使用新的 `--output F:/MC建筑/military5-reference-homes-preview-20261025-revision2`；只能放在工作区直接子目录并沿用此前缀，不能写入原工程、玩家实例或任一开发工程。参数 `--workspace`供显式指定工作区，默认仍为 `F:/MC建筑`。

## 必须已有的证据

### 构建、单元检查与游戏场景

- 两端各一个本版本正常`bundled`完整主JAR及对应distribution。再次只读执行各自 `package_release.py --profile bundled --verify-only`，要求当前生产class、资源、完整distribution文档及原动作manifest逐字节匹配。其QA/privateFX排除、所有Mixin存在及资源冲突检查继续生效。
- 两端最终完整JUnit XML，精确数量与传入参数相等，零失败、错误、跳过。必须包含参考profile、肩/握点、支撑手、真实手臂变换、开火视图和基地核心状态套件。最终XML不能早于当前生产源/资源或JUnit源码的修改。
- 默认日志为 `.superpowers/military5-qa/{neo,forge}-unit-final.log`。日志须正常完成。可用 `--neo-junit-log`、`--forge-junit-log`指定本工程内的另一份真实最终日志。
- 两端完整 **132/132** GameTest（原111、驻地17、身份4），包含第二箱第54槽仅7发库存的650tick真实行走补给回归。默认日志为 `{neo,forge}-regression-final.log`，同在上述QA目录。必须记录本版本、全部required通过、正常保存维度及GameTest停服、Gradle成功；旧111、旧131完整通过、132中存在失败、启动期失败均拒收。当前生产源/资源修改不能晚于该日志。可用 `--neo-game-log`、`--forge-game-log`指定另一份真实最终日志，不能通过参数降低132门槛。

### 三份military.4实际QA存档副本升级

| 平台/维度 | 本工程.superpowers下目录 | QA目录中两次日志前缀 |
| --- | --- | --- |
| Neo城市 | `military5-upgrade-neoforge` | `neo-upgrade-phase1.log` / `neo-upgrade-phase2.log` |
| Neo大世界 | `military5-world-upgrade-neoforge` | `neo-world-upgrade-phase1.log` / `neo-world-upgrade-phase2.log` |
| Forge城市 | `military5-upgrade-forge` | `forge-upgrade-phase1.log` / `forge-upgrade-phase2.log` |

每份要求 `MILITARY4-BASELINE.json`、`city-military-audit.json`、`military-homes-audit.json`。读取其固定原military.4 QA来源，核对全部原world/config/审计文件的清单和哈希，原证据不许改变；新native结果须PASS并保留原UUID、组织、手工任命、角色/穿戴、编辑标记和7发箱库存。

homes结果必须是第二个真实进程的PASS：两个有效且不同的process ID、基线哈希与实际运行目录相符、初次EntityJoin的旧M4无新字段观察及新字段落盘读取均通过。外来军官5+11、脱装野外队长3+9的UUID、有限枪弹、驻地/野外维度必须保留，不能占用原生槽位。第一轮`PHASE1_SAVED`不能算最终PASS。两次日志均须本版本、对应homes状态、原native PASS与正常保存退出。

这些普通服场景使用NoAI固定角色以准确验证落盘，不宣称测试了它们的移动；实际行走、补给、转移和交战由132场景中的真实AI检查承担。不凭空要求Forge不存在的大世界，也不把没有运行的新建世界场景写成已通过。

### 来源、资料与预览

- 本次作者HEAD真实Git树中所有`native_animations`和`art/motion-profile`及其manifest逐字节保全；所有作者native动画还须与当前JAR相同。使用本地`git ls-tree`及批量blob读取，不使用有300文件截断的API结果，不把“253动作”写死成全部作者资源数量。
- `docs/制作资料/data/baseline.json`明确`final_source_freeze=true`、当前隔离工程/分支/HEAD相符，归档器`--verify`实际通过当前源快照、图片和链接验证。
- 两端默认预览清单为 `docs/制作资料/参考模型/生产预览-military5/manifest.json`。来源必须是实际生产ModelPart/PlayerModel与物品JSON的CPU捕获，包含至少24个NPC姿态、8个第一人称姿态，以及两方和第一人称总览PNG；所有源与图片哈希仍相符。可用 `--neo-preview-manifest`、`--forge-preview-manifest`指定本工程内的最终预览清单。原BB及生产profile均须对应已获准的来源SHA，保留16人物cube/5349枪cube的实际分离和7原动画。
- Neo文档中有 `MILITARY5_PLAYER_TEST_ZH.md`、`MILITARY5_BASES_AND_HOMES_ZH.md`、实施记录及文件名匹配 `MILITARY5*FINAL*REVIEW*.md`的最终独立审阅记录。收集器不会把离线预览或这些文档的存在宣称成客户端GL验收。

## 实际保存的内容

- 两端唯一完整主JAR，不额外安装依赖；用户已经装好的GeckoLib继续保留。
- `sources/*-changes.zip`：相对作者HEAD的全部暂存、未暂存、未跟踪源变更；PATCH-BASE记录真实分支/HEAD、文件SHA和删除列表。`*-tracked.diff`使用`git diff --binary HEAD`，覆盖暂存与未暂存，仍不能单独替代覆盖快照。
- `sources/*-full-source.zip`：调用现有发行核验器的受审计完整源快照和SOURCE_MANIFEST，只含允许的工程源、文档、工具与资源，不包含`.git`、运行目录或原玩家私人日志。
- 两端本地QA日志、JSON/TXT/XML、最终JUnit XML、三份升级结果/原基线和来源动画保全记录；完整Neo文档、参考BB与已经裁剪的署名/许可相关证据。
- MANIFEST、全部载荷与MANIFEST的SHA256SUMS、ZIP CRC核查和外部`.zip.sha256`。收集期间再查源、JAR、最终QA、资料和预览哈希；变动即停，保留新staging排查。

四个旧military.1/.2/.3/.4 ZIP在收集前后均须保持规定SHA。收集器不会清理旧包，也不会通过跳过失败门槛生成“先用着”的包。

## 收集器自身验证

```powershell
python -X utf8 -m unittest discover -s tools -p test_collect_military5_delivery.py
```

这些是收集器的拒收/路径/来源完整性测试，只使用`build/military5-collector-tests`下的临时文件和独立Git仓库，保留以便排查；不代表模组功能或新安装包已通过验收。首次RED、夹具自身的临时目录权限/平台换行修正和最终GREEN日志保存在本地QA目录。实际模组证据不齐时仍应拒收。
