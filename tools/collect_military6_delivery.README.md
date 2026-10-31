# military.6.1 本地交付收集器

本工具只读取最终冻结源码、已构建的完整主 JAR 与实际验证证据。它不启动 Gradle、Minecraft 或服务器，不操作 PCL、玩家实例和存档，不执行 Git 添加、提交、拉取或推送，不安装或打包 GeckoLib。已有交付目录、staging、ZIP 和校验文件均拒绝覆盖。失败现场保留；脚本不会清理旧包。

适用目录为 `F:/MC建筑/.blackwatch-worktrees/neoforge-military6` 和 `forge-military6`，版本固定为 `0.3.4-preview.20261028-military.6.1`。NeoForge 对应 MC 1.21.1，Forge 对应 MC 1.20.1。M5 和已真实交付的 M6.0 ZIP、整个目录均冻结；旧工具/资料仍保留在旧包。本轮仅准备工具，未冻结新源码、未生成新的 VERIFIED 输入或发布包。

## 执行前提与命令

最终验证负责人先完成本版生产源码、完整 JUnit、覆盖旧 M5 与当前 M6 的 GameTest、普通服真实两进程保存/重启、CPU 预览、独立客户端真实 Player GL 及逐图复核、资料更新和两端正式 bundled 构建，再审核冻结输入。低主弹20%/HP35%返库、真实ItemEntity搬运仍以root本轮实际runtime为准，旧M6.0通过记录不能证明新功能通过。收集器不会制造 VERIFIED 或把源码字符串、javac、CPU图当作运行通过。

在任一 M6 工程运行：

```powershell
python -B -X utf8 tools/collect_military6_delivery.py --verified-manifest F:/MC建筑/.blackwatch-worktrees/neoforge-military6/.superpowers/military6-revision-verified.json --output F:/MC建筑/military6-vehicles-preview-20261028-r2 --check-only
```

将来由root在全部门槛满足后决定收集。默认新输出为 `F:/MC建筑/military6-vehicles-preview-20261028-r2`，已存在时必须换全新后缀。输出只能位于工作区直接子目录；旧 `military6-vehicles-preview-20261028` 名称始终禁止。

`--check-only` 不创建交付目录、staging、ZIP 或校验文件。没有输入、PENDING、存在失败或跳过、计数不符、源码/证据哈希变化、缺少保存重启、缺少真实版本日志都会拒收。

## 输入 JSON 契约

以下为结构说明，`<...>` 均须由负责人以本轮实际观察结果替换，不能直接用作通过证据。所有文件路径采用 `/`，相对于对应工程；只有 `repo` 使用真实绝对路径。所有 SHA256 为小写 64 位十六进制。清单应通过读取当前磁盘文件计算；不得把作者 HEAD 称为 M6 功能提交。

```json
{
  "schemaVersion": 1,
  "status": "VERIFIED",
  "version": "0.3.4-preview.20261028-military.6.1",
  "finalSourceFreeze": true,
  "protectedFiles": {
    "military5-reference-homes-preview-20261026.zip": "fb7abd664b7c1dfeea840fcf699112678e0a9ba90fa30718ebe0c2b8d89cc01e",
    "military5-reference-homes-preview-20261026/MANIFEST.json": "<sha256>",
    "military5-reference-homes-preview-20261026/SHA256SUMS.txt": "<sha256>",
    "military6-vehicles-preview-20261028.zip": "6b22bc4d17253a33066bdf18f78f68377a73ba4a733dca89b345149fe3f5f991",
    "military6-vehicles-preview-20261028/MANIFEST.json": "<sha256>",
    "military6-vehicles-preview-20261028/SHA256SUMS.txt": "<sha256>"
  },
  "platforms": {
    "neoforge": {
      "repo": "F:/MC建筑/.blackwatch-worktrees/neoforge-military6",
      "head": "996dffdf2d56091cbb01ed9f871bc440ed571a1a",
      "branch": "<actual branch>",
      "sourceFiles": {"<every current allowed source relative path>": "<sha256>"},
      "jar": {"path": "build/libs/Blacklight-Ascension-MC1.21.1-NeoForge-0.3.4-preview.20261028-military.6.1-bundled.jar", "sha256": "<sha256>"},
      "junit": {
        "expectedTests": 0,
        "log": {"path": ".superpowers/military6-qa/<actual final complete unit run>.log", "sha256": "<sha256>"},
        "xmlFiles": {"build/test-results/test/TEST-<actual suite>.xml": "<sha256>"}
      },
      "gameTests": [{
        "label": "<actual final complete run>",
        "expectedTests": 0,
        "runDirectory": ".superpowers/<actual game run directory>",
        "log": {"path": ".superpowers/military6-qa/<actual run>.log", "sha256": "<sha256>"},
        "versionLogs": [{"path": ".superpowers/<same actual game run directory>/logs/debug.log", "sha256": "<sha256>"}]
      }],
      "garageAudit": {
        "runDirectory": ".superpowers/<actual two-process normal-server directory>",
        "json": {"path": ".superpowers/<same directory>/vehicle-garage-audit.json", "sha256": "<sha256>"},
        "prepareLog": {"path": ".superpowers/military6-qa/<prepare>.log", "sha256": "<sha256>"},
        "verifyLog": {"path": ".superpowers/military6-qa/<verify>.log", "sha256": "<sha256>"},
        "versionLogs": []
      },
      "vehiclePreview": {
        "manifest": {"path": "art/military-vehicles/previews/production/manifest.json", "sha256": "<sha256>"},
        "capture": {"path": "build/military6-visual-capture/actual-vehicle-meshes.json", "sha256": "<sha256>"},
        "sourceFiles": {"<every actual capture/model/rig/texture source>": "<sha256>"}
      },
      "clientView": {
        "runDirectory": ".superpowers/run-military6-client-<actual fresh run>",
        "report": {"path": ".superpowers/run-military6-client-<actual fresh run>/vehicle-view-evidence/vehicle-view-report.json", "sha256": "<sha256>"},
        "images": {"<all 50 PNG paths named in actual report.frames>": "<sha256>"},
        "review": {"path": ".superpowers/<actual root visual review>.json", "sha256": "<sha256>"},
        "log": {"path": ".superpowers/military6-qa/<actual client log>.log", "sha256": "<sha256>"},
        "versionLogs": [],
        "sourceFiles": {"<all CLIENT_VIEW_SOURCES plus actual render and asset inputs>": "<sha256>"}
      },
      "evidenceFiles": {"<every actual log, exact final XML, restart JSON, preview manifest and captured vertex JSON above>": "<sha256>"}
    },
    "forge": {"<same structure with actual Forge paths, branch and results>": "<replace entire object>"}
  },
  "docFiles": {"<every docs/ file from final Neo sourceFiles>": "<sha256>"}
}
```

Forge 作者 HEAD 固定为 `59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4`。两端 HEAD 只是原作者基线；完整源码包含 dirty 叠加。可直接导入本脚本，使用 `source_inventory(repo)` 计算完整源清单；该函数包含允许路径下全部 Git tracked 与 untracked 文件，不包含运行目录。`required_evidence(platformEntry)` 返回必须收集的证据映射；额外证据也须逐文件明确冻结，仅接受本地 QA 的 `.log/.json/.xml/.txt/.md`，不接受世界、配置、缓存、完整私人实例日志。

原作者 tracked 的两个运行产物 `logs/debug-1.log.gz`、`logs/debug-2.log.gz` 已由根逐路径审查，允许正常 JUnit 日志轮转留下 dirty 状态。仅这两个精确路径从 `changed_paths` 输入清单过滤；`allowed_source` 仍拒绝它们，`source_inventory` 仍不包含任何根 `logs/` 文件。文件原样保留，不删除、不还原、不改 Git 索引。`logs/debug-3.log.gz`、备份名、其他未经允许的目录中同名文件及任何意外/私密改动继续硬拒；这不是放行整个 `logs/`。

`tracked.diff` 显式限定为固定 `SOURCE_ROOT_FILES` 和 `src/`、`tools/`、`docs/`、`art/`、`gradle/`、`.github/` 六个源码目录的短 pathspec，不使用全量 `git diff --binary HEAD`。该限制同时防止已过滤的 tracked 运行日志以 Git binary patch 泄漏，保留正常源码的 staged/unstaged 差异。两个运行文件不得进入 full-source ZIP、changes ZIP、tracked diff 或最终交付包的文件条目/负载。文件名可以出现在源码排除规则或 `reviewedRuntimeChanges` 审查元数据中；此可选元数据仅记真实路径、当前 SHA256 和原因，不收集日志内容，也不构成运行时源码证明。

`jar.path` 必须是 `package_release.py --profile bundled --verify-only` 实际验证的唯一 canonical JAR。名称按冻结的 `gradle.properties` 中 `mod_archive_name`、`minecraft_version`、`mod_version` 和当前 loader 精确生成，位于本工程 `build/libs`；Forge 使用 `MC1.20.1-Forge`。同后缀的 `stale-...`、其他名称或目录的副本均拒收，即使内容相同。SHA256 在 package verification 前后均须等于 manifest；收集末尾再次执行完整预检。不能用另一个正确 JAR 的 verification 替代待交付文件。

`gameTests` 可以列多个真实完整运行。每个条目的日志真实路径、原始字节 SHA256、`runDirectory` 都须分别唯一；SHA256 还跨两端统一去重。同一日志重复八次、复制后换路径、同一运行目录重复声明或借用另一 loader 的日志均拒收。每份日志只能包含一次完整运行的完成摘要，完成数、required 全通过数、实际 batch 数量之和均须严格等于 `expectedTests`；缺 batch、重复 batch ID、拼接多个运行或计数不符均拒收。

覆盖从实际 `Running test batch '<batch>:<index>' (N tests)...` 行计算，支持现场 Neo 的 `minecraft/GameTestRunner`、Forge 的 `minecraft/GameTestBatchRunner`，同名 batch 的不同索引会合计（例如 Neo `defaultBatch:0` 的50项和 `:1` 的10项）。收集器读取已冻结源码中静态 `@GameTest(...)` 的实际 batch，未填写 batch 的注解属于 `defaultBatch`。M5 的 source set 与 `tools/military5-regression.init.gradle` 一致：`blackwatchTest`、`cityMilitaryTest`、`officersTest`、`garrisonTest`、`terrainTest`、`homeTest`、`identityMilitaryTest`；M6 为 `tools/military6-vehicle.init.gradle` 的 `vehicleTest`。M5 至少132项，M6 数量按该端当前源读取，不能硬填旧102/101或靠总数大于132代替覆盖。

每套 suite 的全部 batch 及精确数量必须在至少一份单次完整运行日志中出现；可以用两次独立运行分别覆盖 M5、M6，也可用一份确实运行两套的组合日志。零散定向运行不能累计冒充完整 suite。`covers` 可省略，由收集器输出实际覆盖；如保留，则必须与计算结果完全相符，手写标签不能证明通过。当前实现针对现场的静态 `@GameTest` 注解与 batch 日志，不是通用 Java 解析器或运行时源码 digest 证明。最终完整 JUnit XML 仍须包含旧参考/手臂/基地套件和新模型、乘员姿态、自然车库与控制消息套件，零失败、错误、跳过。

版本必须出现在实际运行日志中；Forge 标准输出缺少版本时，可引用同一 `runDirectory/logs/debug.log` 或 `latest.log` 的冻结哈希，不能借用别的运行目录。每次服务器日志必须正常保存全部维度、停止服务器并 `BUILD SUCCESSFUL`；GameTest 还须正常结束 GameTest 服务器。

`garageAudit` 必须是实际 `VehicleGarageServerAudit` 的 schemaVersion 1、正确 loader、第二进程 `PASS`，preparePid/verifyPid 为两个不同的正整数。审计须包含实际卸载 APC、有限步兵补弹、三个身份槽、两辆存活载具、原生人员、容器、世界标记与销毁车辆 UUID。仅第一阶段 `PHASE1_SAVED` 不合格。JSON 须位于所声明的同一运行目录。

`vehiclePreview.sourceFiles` 至少包含 `tools/VehicleMeshCapture.java`、生产 `MilitaryVehicleModel.java`、`BlackwatchModel.java` 与两张 `military_vehicles_{blackwatch,military}.png`；负责人还应包含实际 BlackwatchRigPose 和两兵种贴图。捕获 JSON 的 SHA 须等于图片 manifest 的 capture_sha256；manifest 为 8 个实际生产 ModelPart CPU 场景与一张总览 PNG，所有源和图片仍须匹配哈希。预览 manifest 不能早于相关源的修改。

源码目录中的所有文档均须列入 `docFiles`，保留相对链接，包括新中文玩家指南、`docs/制作资料/M6_载具与车库.md`、M6 制作数据、新源文件索引、M5 原始 BB 模型归档和来源/验证记录。禁止商业参考截图缓存、密钥、PCL、玩家世界和测试运行类进入正式 JAR。旧 M5 历史资料保留其原意和历史标识。

## 收集内容与复核

- 两端实际 bundled 完整主 JAR，各对应正确版本/MC/loader；只读执行 `package_release.py --profile bundled --verify-only`，要求 `outputWritten=false`、`geckoLibPackaged=false`。核对10项active配方、fuel配方不在JAR、6实体标识、原弹体碰撞Mixin，以及新 `VehicleEntityCollisionMixin` 和 `client.VehiclePlayerPoseMixin` 各在正确数组恰好一次。模型/挂点 `sourceSha256` 与完整源authoring均须为 `4ccafec7230accdac6d2d84c7ffbd043f2ece8d2b2848e9fe9eb98aa13523a21`。原作者253个motion和native动作继续逐字节保全；Windows只接受原blob或精确Git filters字节。
- 两端完整 source ZIP 与逐文件 SOURCE_MANIFEST；相对 HEAD 的 changes ZIP、删除列表、tracked 二进制 diff、完整 untracked 文件名单。不会把只包含 tracked 文件的 diff 当作全功能源码。
- 完整中文资料、原 M5 参考模型、原创载具 `.bbmodel`、原贴图、8 个实际 CPU 场景与总览、所列实际 QA 证据及其哈希。
- MANIFEST、SHA256SUMS、外部 ZIP SHA256，最终 ZIP CRC 验证。收集结束前重新跑全部只读门槛；任一输入变化就停止。

Forge 仅精确允许正式 `META-INF/jarjar/mixinextras-forge-0.4.1.jar`（`forge` 后有连字符），并要求 `META-INF/jarjar/metadata.json` 的解析对象精确等于：

```json
{"jars":[{"identifier":{"group":"io.github.llamalad7","artifact":"mixinextras-forge"},"version":{"range":"[0.4.1,)","artifactVersion":"0.4.1"},"path":"META-INF/jarjar/mixinextras-forge-0.4.1.jar","isObfuscated":false}]}
```

依赖自身的 SHA256 固定为 `9d48cb0a40299d283248fdad8b02c6d175c45b27f9bec48ef63d7ee8a4ee3066`，183573字节；来源为冻结 M5 Forge 正式包内的依赖，和当前 `build.gradle` 的 `io.github.llamalad7:mixinextras-forge:0.4.1`、range/pin 配置一致。该正式依赖自身含 `META-INF/jars/MixinExtras-0.4.1.jar`，以整个正式依赖的精确哈希放行，不递归放开任意嵌套。缺失/多余/不符的 metadata、同名改包、其他任意 nested JAR（含 GeckoLib）、QA/preview 和打入主包的 GeckoLib 类仍拒收；Neo 不允许此 Forge 布局。此处不要求 manifest 新增 dependency 字段。

源文件、正式构建、最终完整 XML、游戏日志和普通服重启日志的时点也会核对，防止验证后再改生产或测试源。源与运行的关联仍依赖冻结清单、mtime 和最终验证负责人的审核，不是运行时记录完整 source digest 的端到端证明。尚未真实通过的项目保持失败；本脚本自身测试通过不代表游戏功能通过。

收集前后只读执行 `archive_military6_catalog.py --verify`：检查双端当前来源快照、30张标注CPU/NBT主图、13张原库存sprite及相对链接，要求每端10项active配方及1份inactive磁盘配方、零错误、`outputWritten=false`、`gameplay_pass_claimed=false`。来源数以真实导出为准，224/226均非标准。新physics、collision/PlayerPose/camera、Logistics/SupplyRules及实际cargo测试均进入来源索引。最终catalog和玩家guide须等root完成源码后刷新；本轮没有生成或覆盖它们，也不写旧M5目录。

## M6.1资源和GL门槛

`package_release.py`仅精确允许Neo `data/blacklight/recipe/military_fuel_can.json`、Forge `data/blacklight/recipes/military_fuel_can.json`这一个inactive排除项；必须仍在各自 `src/main/resources`，记录其SHA，并断言JAR缺席。fuel模型/32px贴图、原动作、其他配方和普通loot继续逐字节核验。目前Gradle动态fuel loot filter没有匹配文件，因此没有额外loot白名单；后续新增匹配资源需要另行明确适配，不能跳过其他原资源。正式JAR拒绝smoke `.java`、`.class`、内部类和额外nested依赖；完整SOURCE仍保留包括QA在内的原始tracked/untracked源码。

`clientView`是必需门槛，缺失直接失败。实际smoke输出使用 `frames[].image`，不是手填covers；collector据此核对50张独立PNG与 `images` SHA映射。报告须complete/automaticPass，来自独立 `.superpowers/run-military6-client-*`，对应当前源码与实际版本日志；CPU manifest不能代替。root实际打开全部图片后，另写review对象，字段为 `report`（同一path/SHA对象）、`images`（同一完整映射）、`openedAllImages=true`、`status=PASS`、真实 `reviewer` 和带时区 `reviewedAt`。该复核仍是root的人工作证，collector校验绑定关系和时点，不声称自己看过图；任意YSM替代骨架兼容性保持未验证。

## 仅准备PENDING输入

新增 `prepare_military6_revision_manifest.py` 只接受root明确选择的真实运行输入，不自动猜日志或复用M6.0回执。输入沿用上面的platform结构，但顶层须为当前version、`status=PENDING`、`finalSourceFreeze=false`；文件record可给path字符串或path/SHA对象，source/evidence映射可先给真实路径列表。保留实际expectedTests、runDirectory和其他真实回执。脚本计算当前完整tracked/untracked库存、XML、指定证据和两个旧包的保护哈希，从实际GL report提取图片名单；不会生成visual review、runtime JSON或VERIFIED，也不执行Gradle、catalog导出、collector/preflight和打包。

```powershell
# 以下仅供root在实际验证及资料齐全后使用；本轮未对真实工作树执行。
python -B -X utf8 tools/prepare_military6_revision_manifest.py --observations .superpowers/military6-revision-observations.json --output F:/MC建筑/.blackwatch-worktrees/neoforge-military6/.superpowers/military6-revision-pending.json
```

root核对所有实际结果、严格source/mtime/JUnit零skip/完整suite/不同PID条件后，才可自行审核成一份全新VERIFIED输入再执行check-only。旧 `.superpowers/military6-qa/create_final_manifest.py` 和 `military6-final-verified.json` 对应M6.0，保持历史，不用于本轮。

## 收集器定向测试

```powershell
python -B -X utf8 -m unittest discover -s tools -p test_collect_military6_delivery.py -v
```

测试只在 `.superpowers/military6-qa/collector-fixtures/<uuid>` 创建隔离文件与独立 Git fixture，保留诊断，不接触用户实例或工程 Git 暂存区；fixture 基线用 Git plumbing 建立，不执行 `git commit`。新增测试通过公共 `changed_paths`、归档接口和完整 fixture `collect` 验证两个已审查 tracked 日志在修改后仍被排除、其他私密/意外路径被拒，以及 full-source/changes ZIP、tracked diff 和交付 ZIP 均无日志文件条目或测试日志负载，允许的源码路径仍保留。fixture 内的 Git 索引仅用于构造隔离基线，不操作两端工程索引。

新运行产物修复的日志为同目录 `collector-runtime-paths-{red,green}.utf8.log`、`collector-runtime-{red,green}.utf8.log`，最终进程退出码另记 `collector-runtime-green.exit.txt`。这些文件以独占新建方式保存真实输出，不覆盖旧 `collector-gap*.log`、旧31项回执或 `build/military6-collector-*.log`。最终测试数量与结果以新实际 GREEN 为准，旧31项仅作历史附证。没有真实 VERIFIED 输入时，不生成真实工作树的最终发布包。
