# 双版本收口进行记录

目标清单及私有测试资料位于 E:/Blacklight-Dual-Closeout-20261031。Forge 从 26cf538a 独立适配，来源候选为本分支当前 NeoForge。当前记录不是最终发布证明。

## NeoForge 服务端回归

2026-10-31，完整收口套件与枪炮／机械目标套件一起运行，526 项必需 GameTest 通过。涉及 standalone、wall traversal、hydra、feeding、city、feedback、release audit、building 及 gunplay；使用全新隔离目录，最大游戏堆 1280 MB。

退出动作模式时，只有普通持枪或驾驶者可以保留普通模式锁定；空手状态必须清除动作与锁定。修正了判断曾把仍带虐杀模式状态的 API 调用也视作普通锁定的边界。原有 ordinary gun lock 与五种变形武器实际命中回归同轮通过。

地区测试过去断言“清除组织不降低压力，等待军事开发”，与现在已接入的清剿行为冲突。更新为在军事活动维度严格检查降低真实存档压力，以及同一死源不重复降低；下界等非军事维度保留历史压力。没有删除地区检查或改变生产清剿规则。

早一轮全套运行还捕捉过一次母巢 join 回调断言失败；保留该测试并增加 child、回调数、重入结果、出生数、食物和 brood 诊断。最终本轮通过，仍要后续重复和 CI 核对，不能仅凭一次通过断言随机性问题永不发生。旧 CI 中的城市往返与出生预算断言本轮通过，尚不宣称全部跨环境结果一致。

## Forge 移植基线

原分支 Java 17 编译通过。第一阶段同步不依赖游戏平台的进化、器官、军方与机械规则及配置，保留 Forge 的 capabilities、SimpleChannel、Java 17 与 1.20.1 着色器适配。阶段编译通过；新增共享规则测试和资源正在验收，不能据此宣称整个移植已完成。

## 仍需完成

完整 Forge 功能、状态、网络、注册、渲染、城市和生态适配；双版本全量 JVM 与客户端／多人实际检查；网站 #26 所述精确 Epic Fight/BK 组合及相关可选模组回归；最终安装产物、动作完整性和依赖核对。Android FCL 设备行为不能由桌面客户端验收替代。
# Additional validation and fixture diagnostics

The full NeoForge JVM suite completed with 1,104 tests, zero failures/errors/skips. A fourth local combined server run did not reproduce the earlier clean 526-test result: nine tests failed, mostly paid city-hive births plus chunk-activation and travel waits. CI on `06ea95b8` separately failed the reentrant hive birth before the entity-join callback was reached. These results supersede any implication that one local clean run proves the full audit stable.

The hive reentry test now waits for actual entity ticking and a legal shared population slot while retaining all reentry, material and single-birth assertions. Its fixture restores state on GameTest pass/failure as well as explicit cleanup; diagnostic output records the exact active/difficulty/spawning/sanctuary/population budgets when birth is blocked. This is test isolation/diagnostic work, not a claimed production fix. The changed test compiles; repeated actual-server and CI verification remain pending.

## 最新本机重复结果

`neo-closeout-r9.log`：526 项实际服务端必测通过，BUILD SUCCESSFUL。测试使用另一份全新临时世界与 2 GB 游戏堆；仍保留此前失败记录，最终成品和兼容组合尚未验收。

多次重复定位到测试环境的具体差异：GameTestServer 不执行正常 50 ms tick 等待，异步实体存储尚未读取时可能已耗尽 600 tick。往返测试现在独立分批，只在 sprinting GameTestServer 上给予正常 tick 的墙钟时间，保留实际区块／实体就绪、坐标、返回记录和九头蛇动画断言。建筑 fixture 等待实际实体更新时也给予同样的 I/O 时间，并在失败时释放状态与票据。生产传送不跳过实体存储检查；仅增加只读诊断。

城市组件测试的旧场景可留下真实感染体并占满绿区名额。仅在一次性 GameTestServer 中重置组件测试的城市生物场景，密度回归仍自己加入真实居民／感染体检查双预算、拒绝和回调。普通独立或联机存档不会执行该清理。蝙蝠起飞测试改为给真实战斗 AI 一个可见、静止的空中目标，保留实际位移与无重力断言，避免把合法的随机水平／下降漫游误判为不会起飞。

Forge 完整运行时 checkpoint `e818b46c` 的 1,118 项 JVM 测试通过；草稿 PR #97 仍等待实际 Minecraft 验收，不以单元测试代替可玩证明。

## 当前验收结果补记（11:27）

- `069f1b5d1d7bd8655d58dae4e07be76d87c6bc70` 的云端普通构建、版本策略及收口检查通过；最新主套件为 524 项，另有独立被动 13 项及共存被动 17 项通过。此前的 526 项与失败轮次保留，不能混用套件数量。
- 普通安装环境的 NeoForge Epic Fight 21.17.3.1、BK Armaments 4.3.0、Poise 4.2.0、Weapon Arts 1.3.1、Shoulder Surfing 与 HMI 组合完成实际新世界检查：42 个采样，B 键进入／退出、实际前进疾跑、转向、装甲和外部模式切换；稳定移动检查点的身体朝向误差小于 0.005 度，退出后不再使用黑光主体渲染。五张实机静态图已检查，静态图及离散采样不能证明每一种瞬时镜头抖动均已排除。
- YSM 2.6.5 在普通 Forge 安装中主动显示与 Epic Fight 不兼容的警告，八模组全组合未通过；因此拆分验收，未屏蔽厂商警告。Forge 的独立 YSM 组合已实际选中 `alex_mercer_blacklight_bridge` 模型，28 个动作模式采样发布桥接姿态，退出后清理；NeoForge 对应组合尚待复测。
- NeoForge 实际 TaCZ 1.1.8 hotfix r7、Spore 2.2.0j、暮色森林 4.8.3345、TheTitansNeo 1.0.8 组合：`neo-compat-server-r1.log` 中 **31 项必需检查全部通过，正常退出**。真实 30 发 TaCZ 攻击、吞噬控制、暮色机制与泰坦伤害路径均执行。实际僵尸泰坦经 60,000 格俯冲命中前为 19,000 生命，收到 44,022 伤害后生命归零；原生近战 1,500 伤害在对应桥接下实际造成 3,000 并可用足够伤害击杀。
- 上一轮 8 项联测的唯一失败是测试仍把新窗口第一击当成 15% 连击窗口上限。现与 Forge 一致，等待实际配置窗口后按单次上限（默认 6%）检查，没有提高生产限伤。该修正已由上述 31 项实际组合复验。
- TheTitansNeo 原始包的 Minecraft 上限元数据排除 1.21.1，但本次未改包的开发运行环境实际加载并执行其实现；普通安装器是否同样接收此包仍须另验，不能据 GameTest 结果宣传任意泰坦版本兼容。

证据根目录：`E:/Blacklight-Dual-Closeout-20261031`。网站状态未改，#26 保持处理中；Android/FCL 未做设备测试。两份最终普通安装目录、最终产物身份核对及剩余联机检查仍未完成。

## 后续实际验证与退出阻塞（12:51）

- Forge 当前完整实际服务端 `forge-server-r13.log` 为 535 项通过；完整 JVM `forge-final-jvm-r9-results.json` 为 1,119 项／251 类，零失败、错误、跳过。实际 TaCZ／Spore／暮色／Potentia 服务端组合 32 项通过。
- 独立服务端与实际客户端通过回环网络的俯冲检查：Forge r2 与 NeoForge r2 都通过，且两进程正常退出。OFF 不破坏或掉落；ON Forge 实际破坏 225 方块，Neo 实际破坏 205 方块并观察到 2 个泥土掉落实体；真实受击、单次命中与磁盘玩家设置均已检查。只连接了一个真实客户端，不能据此声称多玩家偏好隔离已在实机执行。
- Neo 联机 r1 曾未观察到 ON 的方块与掉落，保留失败；随后没有改变生产逻辑的 r2 通过。r3 诊断重测因进程未留下完整退出报告而无结论。当前 fixture 增加首次落地位置、是否落在准备基底范围内、物品栏泥土和完整地形任务快照，尚待重新执行。
- Neo 普通安装实际能加载未修改的 TheTitansNeo 原始包。带泰坦的组合 r2 在游戏内动作采样通过，但保存退出卡住；不再以“能够加载”代表整体兼容验收通过。后续没有泰坦的 YSM 组合 r10 也在退出卡住，不能归因于泰坦。
- Neo YSM r10 实际选中 `alex_mercer_blacklight_bridge`，模型、形态姿态和退出桥接清理采样通过，图片已查看。启动初期服务端只知道 default 模型，等待异步加载后第二次真实命令成功；旧 r3 的“模型不存在”是测试过早，不是注入模型状态修复。该轮仍因退出问题整体未通过。
- 纯 SDK 的独立新世界对照 r11 加载普通黑光包但不感染、不启动虐杀模式，840 tick 完成后仍卡在退出。只读 trace 明确定位 `blacklight:world` 预生成区块（529 个已加载，生成引用计数 1，保存 future 已完成）；主世界等其他维度可继续卸载。无黑光对照 r5 正常退出。没有在退出时捕捉到新增阻塞 getChunk 请求。
- 当前尝试把预生成票据释放前移到 `ServerStoppingEvent`，避免等 Minecraft 排空生成工作之后才来的 `LevelEvent.Unload`；保留存档进度，禁止停止后继续发任务。编译通过，实际新世界退出验证尚未完成，不视为已修复。

所有原始失败保留。私有观察器、一次性世界与辅助 JAR 仅位于验收目录，不进入玩家安装包；鼠标未被系统控制。桌面实测不覆盖 Android/FCL，YSM 与 Epic Fight 的厂商不兼容提示仍未屏蔽。

## 预生成退出根因与联机诊断（13:22）

单独 SDK 观察器＋普通黑光＋GeckoLib 的 r17 通过真实管理员指令启动预生成，观测到 529 个城市区块，在第 87 个客户端 tick 退出即可复现。只读 trace 记录到失效卸载回调仍反复调用 `scheduleUnload`：holder 已不在 `pendingUnloads`（`pendingSame=false`），但新生成引用为 1、保存 future 已完成，于是旧回调在卸载队列无限重排。没有泰坦、Epic Fight 或 YSM 也能复现，不再将其归因于这些模组。

Neo 现在在两处修正：停服前释放本模组预生成票据；仅对黑光维度，忽略 holder 已被重新接管、已不属于 pendingUnloads 的旧卸载回调。仍在 pendingUnloads 的 holder 完整保留原生等待生成、保存、卸载流程。Forge 同步停服清理，不能照搬使用 1.21 ChunkHolder 生成引用语义的 Mixin。新版普通资源输出改为全新 build 内目录，不递归删除旧目录；将重新进行普通安装的退出复现测试，当前不视为验收通过。

联机 r4 的 ON 已实际破坏 231 方块，失败的是悬空测试台的八格高掉落观察框。读取正常退出后实际实体区域存档，发现 6 个泥土物品实体已落到 Y184–194；`neo-sync-evidence-r4/saved-item-forensics.json` 保留位置证据。因此 fixture 改为同一水平范围内的完整下方区柱，并仍要求真实活着的 ItemEntity。没有增加生产掉落或放松 ON 破坏／伤害断言。

r5 则暴露单一固定受击目标可能被真实滑翔水平位移错过。将目标改为起飞前铺设的静止网格，保留真实服务器伤害；起飞后不移动主角或目标去制造命中。Neo fixture 还在断言前保存完整地形和所有目标位置／生命，后续重复仍待执行。r1、r4、r5 的失败结果不改为通过。

## 新普通安装包的退出复现结果（13:45）

`mechanical-lock.2` 普通 JAR 完整构建成功，SHA256 `5f0bcc040867d07389eb9a324c7a4e9e7c22343207bfe1e52e3c20869375a720`。全新资源目录不含私有 QA 模板；17,931 份地图逐份精确还原校验通过。与前一普通 JAR 比较，20,780 个共同条目逐字节一致，动作／地图条目全部一致；变化为版本元数据、Mixin 配置及新增卸载保护类，没有缩减动作与地图。

普通安装退出测试 r18：真实管理员预生成命令返回 1、观察到 529 个世界维度区块，第 80 tick 退出，所有维度保存成功，进程退出码 0。r20 同时通过两条真实命令启动 WORLD 与 CITY 预生成，两边各 529 个区块，第 97 tick 退出，同样全部保存、退出码 0。与 r17 同一类普通安装、独立 SDK 观察器／GeckoLib 场景，修复后的复现检查通过。尚未据此声明整个发布已完成。

联机 r6 使用新版资源及静止目标网格后仍出现零命中，故不能将此前伤害失败仅归因于位移。r7 已增加首次落地的真实候选、可见性、距离、时序和服务器接触计划诊断，继续区分查询／伤害逻辑与场景问题；不改掉落和伤害断言，也不把 Gradle 正常退出当作游戏检查通过。

