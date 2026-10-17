# 双会话、双版本本地整合审计（2026-10-17）

本次任务：整合 Mercer 人物／战甲、武器与防御音效、攻击中切换形态，以及另一会话的君临之翼／飞行系统。两个 Minecraft 平台分别维护本地集成分支，不把 Forge 和 NeoForge 的平台实现互相覆盖。本报告记录静态调用路径、现有自动化测试与成品检查；不把编译通过等同于实机验收。

## 整合来源

| 平台 | 模型与音效来源 | 翅膀来源 | 本地集成分支 |
| --- | --- | --- | --- |
| NeoForge 1.21.1 | PR #51，`841e3d547dfd1a69b8c3578d9f5337180791c109` | `1729b1bb`，含 `d7a41c01`、`ed9a7082`、`fb4a4709` 及审计反馈修复 | `audit/neoforge-1.21.1-mercer-wings-20261017` |
| Forge 1.20.1 | PR #52，`f9874c4781993db267f12437dae38bfd556a4049` | `7844a7d9f8f252f57284f38f36f25a9c1048f980`，含四项交叉功能修复 | `audit/forge-1.20.1-mercer-wings-20261017` |

工作目录分别为 `E:/Blacklight-Mercer-20261016/neoforge`、`E:/Blacklight-Mercer-20261016/forge`。未修改另一会话工作目录。NeoForge 最终代码合并提交为 `f3c126c7`；Forge 合并提交为 `21dd86d4`、版本标记提交为 `1c9128d7`。集成包版本号独立使用 `integration.1`，便于与原预览包区分。

## 审计发现与修复复核

本次发现四项交叉功能缺陷，已反馈给另一会话，并将其修复提交合入两个本地集成分支。下面保留缺陷原因和回归步骤；定位行号以修复前 NeoForge `67e38bae` 为准，避免把旧问题写成最终代码仍然存在。步骤是后续实机回归用例，不代表本次已经逐项录屏复现。

| 编号 | 严重度 | 最终代码复核 |
| --- | --- | --- |
| A1 攻击移动锁被绕过 | P1 | 两端均去除输入事件的提前返回；客户端和服务端的飞行控制让位于武器动作 |
| A2 地面冲刺升级失效 | P2 | 两端恢复地面增强移动 Tick；飞行客户端补齐冲刺预测和装甲减速 |
| A3 伪装保留飞行 | P2 | 两端伪装入口停飞、收翼并清除翅膀位；飞行资格同时排除伪装和变形过程 |
| A4 捕获／吞噬与飞行抢控制 | P1 | 两端新增 `consumeOwnsMovement`，受控阶段让位并清除未执行的起飞请求；HOLD 与恢复阶段不被一概锁死 |

A1–A3 的 Neo 修复来源为 `fb4a4709`，A4 为 `1729b1bb`；Forge 适配提交 `7844a7d9` 包含全部四项。没有发现上述修复在平台移植中漏掉的分支。复核证明的是代码控制路径已调整，游戏手感仍需下面的实机矩阵确认。

### A1 / P1：装备翅膀就绕过攻击的移动控制

- `src/main/java/com/codex/blacklight/client/ClientMovementEvents.java:66`：`lockWeaponMovement` 在 `WingFlightSystem.eligible` 为真时直接返回，未执行 `beginWeaponMovementOwnership`、待确认攻击保护和 `clearWeaponTravelInput`。
- `src/main/java/com/codex/blacklight/movement/WingFlightSystem.java:22`：`eligible` 只判断装备和环境条件，不要求实际飞行，也不检查武器动作。
- `ClientMovementEvents.java:274` 及 `client/ClientWingFlight.java:25`：飞行输入分支又在正常攻击／吞噬控制前接管客户端 Tick，并按移动输入重写朝向。
- 影响：地面装备翅膀后，挥刀／锤击等动作期间仍保留原版走动输入，与攻击位移轨迹争夺控制，存在滑步、动作和位移不一致风险；这会破坏此前修好的攻击移动约束。
- 回归步骤：地面装备翅膀，长按 W/A/D，分别使用刀锋和锤拳攻击；对比收起翅膀后的脚步、动作朝向和实际位移。
- 建议：把飞行按键采集与移动所有权分离，保留攻击／吞噬／待确认动作的优先级，只在允许的实际飞行阶段接管移动。

### A2 / P2：装备翅膀后地面冲刺升级失效

- `movement/PrototypeMovementSystem.java:75`、`:125`：符合翅膀条件就跳过原移动输入与 Tick 流程。
- 因此 `tickSprint`（`:525`）及升级倍率（`:550`）不可达；客户端也在 `ClientMovementEvents.java:274` 提前返回。
- `client/ClientWingFlight.java:28` 只恢复原版 `setSprinting`，没有替代增强冲刺的速度计算。
- 影响：翅膀尚未起飞时，地面冲刺退回原版奔跑逻辑，升级效果和原地面跑酷行为不一致。
- 回归步骤：购买冲刺升级，在同一路面分别收起／装备翅膀后按奔跑键移动，比较距离及爬坡行为。
- 建议：保留地面增强冲刺流程；仅用翅膀起飞输入替代蓄力跳跃，不以“已装备”跳过全部地面移动。

### A3 / P2：伪装未退出翅膀飞行

- `disguise/DisguiseSystem.java:61`：进入伪装只清除 `UtilityPowerSystem.DEFENSE`，保留 `SOVEREIGN_WINGS`。
- 同文件 `:213`、`:217` 的持续可见能力检查也未包含翅膀。
- `movement/WingFlightSystem.java:21-24` 未排除伪装／变形状态；反向流程 `power/PowerFormSystem.java:43` 在选择翅膀时明确调用可见能力暴露逻辑，两条路径不对称。
- `client/renderer/DisguiseRenderer.java:67` 取消玩家渲染并改用伪装生物渲染，所以飞行能力会与原玩家翅膀渲染脱节。
- 影响：先装备翅膀、再进入伪装可以保留飞行状态，形成伪装生物飞行且缺少正常翅膀层的组合。
- 回归步骤：已有有效伪装身份，装备翅膀悬停后按 X 伪装，确认飞行输入、暴露状态和外观。
- 建议：明确统一伪装与翅膀的互斥规则；进入伪装时收翼并停止飞行，或在伪装状态拒绝飞行输入。

### A4 / P1：捕获／吞噬期间仍由翅膀分支改写输入

- `client/ClientMovementEvents.java:274` 的飞行 Tick 位于原有吞噬输入保护之前；最初的攻击修复只检查武器动作，尚未检查 `CONSUMING && ConsumeVisual.blocksInput(GRAB_STAGE)`。
- 飞行客户端会持续按输入转向，并在地面冲刺预测中写入速度；服务端 `combat/ConsumeSystem.java:234` 会取消移动距离超过阈值的普通捕获，`:238` 又会清除受控阶段水平速度，两套控制相互冲突。
- 未执行的起飞请求也可能在进入捕获后继续被飞行 Tick 消费。
- 回归步骤：装备翅膀，奔跑中抓取小型生物，捕获阶段持续按移动／跳跃键；再测大型骑乘吞噬，确认无突然滑走或自行松手；HOLD 阶段仍可正常携带移动。
- 已采纳修复：客户端 `ClientWingFlight.tick` 对受控吞噬阶段返回 false；服务端飞行 Tick 在相同条件下让位并清除起飞请求。复用已有阶段定义，不新增“一受攻击就中断吞噬”等玩法。

## 已核对且未发现新增缺陷的路径

- 普通武器切换先检查权限和解锁，再取消旧攻击、命中会话和按键缓存；不清空空中连击次数。吞噬、抓取和毁灭技的保护仍在。
- Mercer 外观为本地可选项，默认关闭；普通玩家继续使用原方块战甲。新模型绑定的武器与翅膀使用同一套转换后矩阵。不能据此宣称所有外部 YSM 模型均已适配。
- 武器／防御音效由服务端广播，防御接触共享 3 Tick 去重；音效变化没有改写防御数值。
- 飞行仍调用原版碰撞移动；区块探测在服务端使用就绪缓存而非同步生成区块。数据包放宽针对有权限的飞行状态，未整体禁用服务端移动检查。
- 飞行状态是瞬态同步数据，不持久化／继承死亡状态；客户端特效跟踪数量有限，并处理换世界清理。
- Forge 使用自己的能力数据与事件适配，注册的 27 个同步字段未超过协议 32 字段上限；`WING_FLIGHT` 进入跟踪同步且不进入持久化死亡复制。
- 五个核心文件（飞行物理、速度调校、区块就绪走廊、翅膀运动、变形时序）在去除 Java 17 数学兼容写法后与 NeoForge 相同。
- Mercer 人物：35 骨骼／262 部件；战甲：35 骨骼／116 部件。无重复骨骼、缺失父节点、父子循环、无效绑定名、不完整四边形或非有限顶点数值。
- 未发现动作或模型被本次合并替换回旧版；合并保留双方素材署名与许可记录。

## 验证记录

### NeoForge 1.21.1

- 修复后的本地完整 `gradlew build --offline --no-daemon --console=plain --max-workers=1`：通过。Java 21，构建进程限制 1 GiB 堆，未跳过资源处理或测试。
- 402 项现有测试，0 失败、0 错误、0 跳过。此前单独跑过 74 项飞行与攻击相关测试；这些包含在 402 项内，不累加计算。
- 构建内 `verifyStandaloneBoundary`、`verifyBundledMotionResources` 通过，252 个捆绑动作／配置文件校验通过。
- 成品检查：41 个声音事件对应 123 个 OGG，资源路径均存在；两种语言未发现重复键；注册 Mixin 类在 JAR 内；Mercer 战甲与翅膀资源和源码目录一致；飞行测试夹具未混入发行 JAR。
- JAR：`neoforge/build/libs/Blacklight-Ascension-MC1.21.1-NeoForge-0.3.4-dev.11-integration.1-bundled.jar`
- SHA-256：`02087ba37577c12bf09f68eb78dadf4b75099f654d1701191b9010edabb19df0`
- 构建日志：`E:/Blacklight-Mercer-20261016/neoforge-integration-fixed-build.log`；成品检查：`neoforge-artifact-audit.json`。202 个模型／贴图／声音／新动画资源与源码逐字节核对通过。

### Forge 1.20.1

- 本地 Java 17 完整 `gradlew build --offline --no-daemon --console=plain --max-workers=1 -Dnet.minecraftforge.gradle.check.certs=false`：通过。未跳过资源处理或测试。
- 403 项现有测试，0 失败、0 错误、0 跳过；Forge 能力数据变换测试正常执行。
- `verifyStandaloneBoundary`、`verifyBundledMotionResources`、`jarJar` 和 `reobfJarJar` 均完成；252 个捆绑动作／配置文件完整。
- JAR：`forge/build/libs/Blacklight-Ascension-MC1.20.1-Forge-0.3.4-dev.11-integration.1-bundled.jar`。同目录的 `bundled-slim.jar` 是构建中间产物，不作为这里的整合成品。
- SHA-256：`49b0f6f1b169d61e3fe6f692f9d75f759db815cdf1cca7be4370b256bde8a86b`
- 构建日志：`E:/Blacklight-Mercer-20261016/forge-integration-build.log`；成品检查：`forge-artifact-audit.json`。
- 最终两端 202 个模型／贴图／声音／新动画资源逐字节相同；两端声音均为 41 个事件／123 OGG。比较记录为 `cross-version-asset-audit.json`。
- 重新执行只读成品检查：`C:/Python314/python.exe E:/Blacklight-Mercer-20261016/audit_integration_artifacts.py`。

### 测试覆盖差异（不等于运行时功能缺失）

两端测试数量不能直接证明覆盖完全相同。NeoForge 独有 `ModePerspectiveTest`、`ShieldBorerTest`、`SkillTutorialResourceTest`、`HammerGroundContactTest`、`McWeaponAssetTest`；Forge 独有能力／网络适配测试及 `BoneWorldPointTest`。部分共同测试类的用例数量也不同。本轮已跑各自全部现有测试，但没有把两个平台的测试套件完全对齐；镜头模式、盾钻、锤拳地面接触、武器资源等场景的 Forge 自动化覆盖仍应补齐。此项属于审计保留事项，不据此宣称对应功能已坏或已通过跨平台同等验收。

## 发布前实机回归矩阵

| 场景 | 应确认的结果 |
| --- | --- |
| 地面收翼／装备翅膀，刀锋和锤拳边攻击边按 WASD | 攻击位移和转向归攻击系统；结束后正常恢复跑动 |
| 翅膀装备状态下不同冲刺等级，装甲开／关 | 升级倍率正常；装甲减速只应用一次；起飞输入不触发普通蓄力跳 |
| 空中悬停时伪装、伪装时选择翅膀 | 收翼／暴露规则对称；无隐形翅膀飞行或残留悬停 |
| 奔跑抓取、携带、吞噬恢复、大型骑乘吞噬 | 捕获阶段不被飞行输入打断；允许移动的 HOLD／恢复阶段仍可移动 |
| 玩家皮肤、Mercer 可选外观，各自装甲／武器／翅膀组合 | 默认方块战甲保留；附件不漂移；第一人称不挡满视野 |
| 死亡重生、换维度、重进、另一玩家中途进入跟踪 | 临时飞行状态清理；动作和外观可恢复；观察者能看到一致翅膀状态 |
| 专用服务器高速飞行、区块边界和墙体碰撞 | 不穿墙，不同步阻塞生成区块，不因合法飞行被错误踢出 |
| YSM／史诗战斗／光影分别组合 | 核实外部渲染器和镜头兼容性；本轮未覆盖该组合验收 |

## 验证边界

本会话未启动集成后的游戏客户端、专用多人服务器，也未重新测 YSM／史诗战斗／光影组合。另一会话的飞行实机记录单独保留，不能替代上述四项交叉功能的回归。当前结论是完成静态审计并纳入修复的集成构建，不是全面实机验收通过。
