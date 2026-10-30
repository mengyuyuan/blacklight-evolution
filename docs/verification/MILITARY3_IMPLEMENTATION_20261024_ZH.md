# military.3 双阵营军官实施与验证记录

当前目标：用户在 military.2 完成后追加军方、黑色守望军官与队长；继续完整实装并交付，不恢复旧的等待用户测试步骤。项目 https://github.com/mengyuyuan/blacklight-evolution ，共享经验 https://github.com/yfcyy889-ops/AI-Engineering-Lab 。

## 实现及决定

两方各新增队长、野战军官、基地指挥官实体和刷怪蛋；角色为 team_leader、officer、commander，默认军衔中士、少尉、少校。普通兵角色 trooper 保留。角色、八级军衔、实际指挥任命独立，军衔不提供额外生命或伤害。

队长首次配步枪30+90，军官/指挥官首次配卡宾24+72；只有首次初始化发放。role 命令保留原枪栈、隐藏枪栈、弹量、冷却和单位，补足最低军衔但不免费换枪补弹。默认名字随角色变化，自定义名保留。旧档缺角色字段时按军衔和注册默认身份迁移，合法显式身份保持独立。

新自动小队接收军官后按实际军衔择优；死亡/失联沿用已有合法接替链。成功手工任命后持久 CommanderManual 标记保护玩家选择；旧非自动单位缺标记时保守保护历史任命。新基地仍非 automatic，避免外兵混入；在原16/20槽内把门岗首位换队长，command岗位换军官，operations岗位换指挥官。未添加无限刷新的指挥官。

双端分层模型有242 UV岛，六身份分别有帽具、通信、识别条、指挥携行装具和肩领区别。脱装仅保留完整制服/面部；角色装具随 equipped 隐藏，空手无假枪。原六套兵装像素兼容检查、48角色×军衔组合静态检查及实际资产预览已完成；这不是 Minecraft GL 实机结论。

## 实际失败与修复

1. 角色纯规则测试先缺类 RED，再由双端实现通过；日志 roles-red.log 与 build/military3-rules/role-checks-green.log。
2. 新9场景真实运行，8通过、伪装外观失败；DisguiseIdentity 白名单遗漏 MilitaryRole、MilitaryEquipped。仅加入这两个外观键，不复制编制、备用弹和隐藏枪。修复后完整96场景通过。
3. 独立审阅发现六新实体缺战利品表。新增真实 FakePlayer 玩家伤害击杀场景；夹具初次 AABB 类型错误单独保留日志，纠正后10场景中唯一失败为六杀电路 actual=0。补齐各阵营普通兵等价表，保留玩家击杀固定1电路条件。修复后的完整97场景在 Neo 已实际通过。

独立审阅详见 MILITARY3_FINAL_REVIEW_20261024_ZH.md；运行时、美术报告各自记录文件所有权与静态边界。

## 验证现场

2026-10-24 07:54 本地：Neo 525项JUnit，0失败/0错误/0跳过；97/97实际GameTest通过。包含六身份有限库存、权限、身份转换、伪装、旧档迁移、两方军官晚到接管/死亡接替、完整16/20驻防、手工任命跨完整集结及SavedData序列化、真实玩家击杀电路，以及原有87场景。

证据位于 .superpowers/military3-qa/neo-regression.log 与 build/test-results/test/TEST-*.xml。Forge完整回归正在运行，尚未计为通过。普通服新审计已扩展为真实军官身份、脱装和较低军衔手工指挥官的磁盘恢复；待执行。

Gradle严格串行；中文路径的JUnit采用GBK JVM参数文件，资源模板固定UTF-8；最终普通发行使用默认UTF-8。JDK21/17和Gradle缓存均在 F:/MC建筑/.blackwatch-tools/gradle。保留原build/libs，不用clean删除历史产物。

## 交付、现场与边界

版本独立标识0.3.4-preview.20261024-military.3，包含military.1/.2完整功能。目标 F:/MC建筑/military-officers-preview-20261024 及同名ZIP。收集器额外保存实际JUnit XML和TEST-RESULTS.json，并检查完整GameTest日志与普通服审计PASS，不以Gradle退出0代替场景通过。

保护military.1 ZIP SHA256 3ef369f0f347562896226fc77b62cdc4035c1ad61b2dae07f45a91f9262dc031；military.2 ZIP SHA256 412732809bf4bbdb9f6e082075b937761dcdcf33c07519535483c50db1ec8119。已本轮再次核对，收集前后也核对。

Neo仍在feat/neoforge-1.21.1-blackwatch-squads，HEAD cdf5eb15628594ca82ad9b35bd11d3d34993cfc0；Forge仍在feat/forge-1.20.1-blackwatch-squads，HEAD a4f388282af0ad76875716d99ac2534c08c56ee3。源码未提交增量包含military.1/.2/.3，changes.zip按真实HEAD记录所有未提交文件，tracked.diff不含未跟踪文件，不能单独替代快照。没有提交、推送、公开发布、修改PCL或重复安装GeckoLib。

客户端动态穿模、极端俯仰/抓取帽具与外套、物理按键和实玩表现尚需Minecraft客户端检查；静态资产和普通服不能代替。没有把既有改军衔命令改成强制重新任命，也未新增载具驾驶或导弹锁定。

当前唯一下一步：完成Forge完整回归及双端普通服磁盘重启，然后正常构建、发行排除QA/253动作核验并生成独立新交付；成功后在本记录追加实际结果。

## 双端完整回归追加

2026-10-24 08:04 本地：Neo525、Forge522项JUnit均0失败/0错误/0跳过；两端全部97/97实际GameTest通过，包括六军官真实玩家击杀固定总6个电路。Forge继续使用既有失败种子3169872723588932174。

Forge首轮96/97，唯一失败是测试误用原版SpawnEggItem.byId；本地Forge源码明确要求ForgeSpawnEggItem.fromEntityType，已修正QA并加实际getType(null)类型断言后完整通过，未为此改生产注册。启动曾卡在ForgeGradle EnvironmentChecks.testServerConnection的TLS握手，已保留线程证据、取消并仅终止本次已确认PID45560，后续使用--offline与进程内ForgeGradle联网预检关闭参数；没有修改系统证书或全局安全配置。

独立审阅P2已由真实RED/GREEN及双端完整97场景闭环。普通服审计与发行正在执行。

## 普通独立服与旧档迁移追加

2026-10-24 08:09 本地：两端普通runServer的全新世界均完成PHASE1_SAVED→正常保存退出→同目录重启PASS。原生自然结构实际生成，种子0/start(72,33)/site(1168,63,528)/turn2；第一槽真实队长，第二槽用实际角色转换为军官并脱装，手工任命较低军衔第一槽。磁盘重启后身份、制服、原枪弹量、CommanderManual与该指挥官、原两名UUID、组织、7发箱库存和方块改动均保留；officerRoleUniformManualAppointmentRestartVerified=true。

再用military.2原普通服世界直接升级启动，两端均PASS，legacyRoleMigrationVerified=true；旧兵按军衔迁移、非自动历史编制保护、有限弹药和UUID不复制均实际通过。没有覆盖military.2发行包，变动仅在一次性QA世界。日志为各端.superpowers/military3-qa/*native-server*.log与*military2-migration.log；新JSON在.superpowers/military3-server-{platform}，旧档JSON在.superpowers/city-military-server-{platform}。最终交付保存两套JSON和全部JUnit XML。

行为实现与独立审核现已闭环。下一步只做默认UTF-8的正常assemble、253动作及排除QA的发行核验、收集新包；最终以MANIFEST/SHA256SUMS与实际收集器成功回执确认交付。

## 最终交付回执（当前状态，以本段为准）

military.3已独立生成并核验：F:/MC建筑/military-officers-preview-20261024 及同名.zip，ZIP SHA256 847aae78693cf6c3f592894c62bba5447beadeeacc3ecab13f97b544558ed9a3。归档179356488字节；收集器testzip通过，并再次逐一比对MANIFEST的338个文件哈希及ZIP sidecar。military.1/.2原ZIP哈希在收集前后均保持不变。

Neo主JAR SHA256 0dbab653f6fee65734fcc745cd984d5f99d0457155965ecf0573326318cbb138；Forge主JAR SHA256 df1d65ce2a7b774c2393abd5bb49b74bf76c8d064eb0c8ed859d74ac80183502。正常默认UTF-8 assemble双端成功，Forge执行reobfJarJar/reobfJar；两端253份动作/配套资料原哈希不变，无opt-in GameTest/ServerAudit与参考FX泄入主JAR。发行核验Neo935生产class/13935资源/15512源输入/281文档；Forge962生产class/7727资源/9249源输入/257文档，日志neo/forge-release-verify.log。

最终测试Neo525、Forge522 JUnit均零失败/错误/跳过；两端97/97 GameTest、普通服军官身份/制服/手工指挥磁盘重启、military.2旧世界升级均PASS。交付evidence保存实际XML、TEST-RESULTS.json、两套普通服JSON和成功/失败日志；源快照是收集时真实HEAD上的military.1/.2/.3完整未提交覆盖，来源与每文件哈希见sources内PATCH-BASE.json。新增MONSTERS_CURRENT_ZH.md解释已有怪物与多部位实现，不声称这轮重新验收全部Boss。

本段和计划勾选是归档产生后的文档回执追加；发行文档比对与交付源码快照基于追加前的准确现场，之后没有改生产代码/资源或覆盖已收集产物。未提交、未推送、未公开发布、未改用户PCL。当前源码仍在上述feature分支与HEAD；完整交付可独立保存回退。

当前唯一下一步：用户保留已有GeckoLib，只替换对应加载器的唯一黑光主JAR，在新测试世界按docs/MILITARY3_PLAYER_TEST_ZH.md检查客户端模型、动作、实际按键和游玩；GL动态表现尚未冒充通过。收到反馈后从本记录继续针对实际问题修正，不重复制作已通过的服务器功能。

玩家指导补充：按用户新要求，已核对实际命令与配方，合并完整玩家流程到 F:/MC建筑/military3-完整测试流程.md，覆盖模型、八枪七弹、合成、电路、小队/军官、层级、基地、实际补给、交战、磁盘保存、城市和伪装。仅补充操作文档，未改主JAR或旧交付归档，也不新增玩家实机通过声明；下一步仍为用户按指南实机测试并反馈具体步骤。
