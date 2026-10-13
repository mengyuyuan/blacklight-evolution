# 黑光独立化与姿态修复

2026-10-12，基线为用户现有 RC59 工作区。开发目录：`D:\Blacklight-Standalone\blacklight-standalone-neoforge`，版本 `0.3.0-standalone-dev.4`。

## 独立化实现

核心运行时已移除 Epic Fight、Epic ParCool、Weapons of Miracles。元数据与运行类路径仅要求 Minecraft、NeoForge、GeckoLib；ParCool 只保留可选兼容 API。旧适配器和专用旧截图工具移到源码目录之外的本地归档，正常构建不编译、不打包。

| 原职责 | 当前实现 |
| --- | --- |
| 动作矩阵、姿态、骨架 | 原创 MatrixMotionClip、BoneTransform、BlacklightPose、BlacklightSkeleton；数学生成的项目自有绑定 |
| 玩家皮肤与生物武器 | 原创连续 CPU 蒙皮、原生 PlayerRenderer、独立挂载变形、装甲、盾牌和第一人称手部 |
| 客户端动作与镜头 | PlayerMotionController、共享 WeaponMotionClock、自有目标锁定和原版镜头 Mixin |
| 服务端动作与旁观同步 | NativeActionState 附件及时间轴，后进入视野可定位已有动作 |
| 攻击推进 | NativeRootMotion，服务器执行真实世界碰撞与目标间距；自有位置同步避免旧输入回退 |
| 抓取、吞噬、受害者 | 原有玩法系统配独立姿态、双骨 IK、原生受害者渲染与击退保护 |
| 墙面移动 | 原生 WallTraversal / WallPoseState，独立 RUN / CLIMB 动作和表面姿态约束 |

五种攻击形态、徒手、装甲和护盾，抓取投掷吞噬、身份、感知、升级、感染与蜂巢逻辑都保留。框架替换没有通过删去这些系统实现。

## 用户反馈对应修复

- 皮肤出现肩、颈、腰缝隙：分离硬块会在骨骼旋转时露底，改为连续网格和共享权重，补齐颈部连接。
- 走跑滑行：按动作实际 Coord 步幅校准速度，墙面计入切向垂直距离，动作切换保留周期；利爪移动连击保留腿部步态。
- 墙跑变爬或身体横躺：RUN 与 CLIMB 分开选片段，校准源动作轴和墙面朝向，避免装甲闲置姿态覆盖墙跑；完整皮肤避让墙面。
- 第一人称复查：直接使用真实 RenderHand 事件。F1 会让 Minecraft 跳过整个手部流程，截图工具因此改为第一人称保留 HUD。

修复画面和持续移动必须分别核对。固定时间截图证明模型姿态，不能单独证明连续跑步的节奏。

## 验证记录

编译与 `verifyStandaloneBoundary` 已通过，客户端在没有旧动作前置的类路径下进入复制的 QA 世界，专用 GameTestServer 也实际启动。

原 RC59 861 个复制源文件 SHA-256 已重新核对，未修改；基线提交 `228da9c` 与旧含前置试玩包保留。

最终本轮数值结果由交付目录中的 `验证结果.json` 和日志记录。基线对照使用相同本地动作资料，单元测试有 7 个既有失败；旧 94 项 GameTest 基线有 35 个失败。独立化引起的新增失败单独排查，不把全部失败笼统归为历史问题，也不跳过失败来制造全绿报告。

完整多人观战、重连、高延迟、所有吞噬组合、长期性能及生存全流程尚未作为正式发布验收通过。当前是可复查的独立开发版。私人动作资料的发行状态保持原样，见 `THIRD_PARTY_ASSETS.md`。

## dev.3 验证基线

| 检查 | 结果 |
| --- | --- |
| Java 编译、独立运行边界、JAR | 通过；无旧框架类引用，未包含 QA/GameTest 类 |
| 全部单元测试 | 167 项，160 通过、7 个 RC59 基线既有失败；无新增失败 |
| 模型资源与连续利刃插值 | MvpResourceTest 17 项全部通过；真实成品刃部与头部间距 >= .068 m |
| 独立核心专用服务器 | 7 项全部通过，含真实装甲减伤、护盾、碰撞推进、吞噬与同步 |
| 旧 94 项 GameTest 对照 | 60 通过、34 既有失败；RC59 对照 59 通过、35 失败；无新增失败 |
| 持续实机疾跑 | 普通与装甲各 120 tick，全程落地，各 115 tick 使用 RUN，270.642 / 205.004 格；0 时钟/方向异常 |
| 实机画面 | 25 个完整形态画面，修复后另复跑 7 个第一人称/墙面画面及 3 个抓取/插值画面 |
| 原工作区保护 | 861 个源文件 SHA-256 与迁移前完全一致 |

旧测试中仍存在模拟玩家永久无敌、过期数值/时序及场景断言。没有通过关闭失败测试来使汇总变绿；旧失败日志及逐名基线对照随验证资料保留。对本次引入的地面与真实攻击推进问题，补足真实场景后复测；没有改变游戏碰撞语义。

## 复现

使用 Java 21 和本机 ASCII 临时目录：

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:TEMP='D:\Blacklight-Standalone\tmp'
$env:TMP=$env:TEMP
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\Blacklight-Standalone\tmp'
.\gradlew.bat compileJava test verifyStandaloneBoundary '-PbundleMotionDirectory=D:\Blacklight-Standalone\motion-profile' --console=plain
.\gradlew.bat runGameTestServer -PstandaloneTest '-PbundleMotionDirectory=D:\Blacklight-Standalone\motion-profile' --console=plain
.\gradlew.bat runClient '-PstandaloneVisual=D:\Blacklight-Standalone\visual-qa-final' '-PquickPlayWorld=Blacklight Standalone QA' '-PbundleMotionDirectory=D:\Blacklight-Standalone\motion-profile' --console=plain
.\gradlew.bat assemble '-PbundleMotionDirectory=D:\Blacklight-Standalone\motion-profile' --console=plain
```

截图工具只允许以 `Blacklight Standalone QA` 开头的世界文件夹，使用存档副本。普通构建不包含该工具；无需设置这些 QA 参数即可正常游玩。

## dev.4 第一人称、落地与镜头回归修复

第一人称改为调用 Minecraft 1.21.1 的原生 `ItemInHandRenderer.renderPlayerArm`，通过 `RenderArmEvent` 挂接变形模型。普通原版模型单独准备手臂坐标，不读取第三人称动画模型的 ModelPart 状态。移除眼高偏移、.82 缩放、统一手腕角度、全身 Root 与视口大小拟合。保留真实网格的近裁剪面保护；原生挥手中的局部出屏属于正常动作，不人为缩小整条手臂。左右手各用原版对应的挥手和装备切换进度。护盾单独采用原生持物变换和 Minecraft 护盾的显示变换，将已有护盾网格的轴与中心换到内置盾牌模型坐标。

跑跳落地时，实际接触地面的当帧继续腿部步态，短暂缓冲只叠加上半身。移动落地的水平 Root 位移被移除；重落地仍保留其原有冲击缓冲。

目标锁定增加 requestId 与对应的服务器 ACK，补回原生角标、名称和取消提示。取消后到达的旧回复不能恢复已取消的目标。dev.7 新增临界质量与技能同步字段，客户端与服务器协议均为 `standalone-3`，必须同时更新。

镜头方面，动作编辑器不再自动强制背面或关闭后恢复旧视角；站立时不再将人物方向同步到绕镜头方向，有移动输入时保留相机相对移动。实机原生鼠标绕行 180 度后，人物 yaw 保持 180，镜头 yaw 为 0，正脸法线与镜头方向点积 .934。

验证：183 项单元测试，176 通过，7 个失败逐名与 RC59 基线一致；本轮新增手臂、持盾、落地、ACK 和投影检查通过。独立核心专用服务器 7 项通过。原生第一人称 6 个平视/俯仰画面以及 21 个形态/攻击/防御画面已录取；护盾另按持物链路复查。真实 Tab/F5、取消与重生流程为 0 异常。连续普通/装甲疾跑和跑跳落地整轮为 0 异常，实际跳跃 25 tick 离地，接触地面当帧已是 RUN/basic_sprint。完整生存、远程多人、高延迟和长期性能仍不属于本地开发版的已验收范围。旧 94 项 GameTest 表格是 dev.3 的既有对照，本轮未重复这些旧断言。
