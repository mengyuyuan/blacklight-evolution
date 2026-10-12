# 毁灭技、临界质量与落地冲击验证

核查日期：2026-10-13。以下证据来自本机 Minecraft 1.21.1 / NeoForge 单人集成服务器，使用专用 `Blacklight Standalone QA v4` 存档。最终报告 `complete=true`、`passed=true`、`violations=[]`，完成 13 个案例、13,911 个原生渲染帧和 49 张实际 framebuffer PNG。

原始报告与截图：`D:/Blacklight-Standalone/skills-dev7-final-gpu-v3/native-skills-report.json` 及同目录 PNG。本文只记录验证汇总，不包含私有原版曲线、网格或纹理数据。

**针对性单元检查：49 项通过，失败、错误、跳过均为 0。** 本轮 XML 位于 `build/test-results/test/`。

| 检查组 | 项数 | 检查内容 |
| --- | ---: | --- |
| `CriticalMassStateTest`、`CombatSkillStateTest` | 12 | 容量随生命/升级变化、储量先吸收伤害、溢出、一次免费释放、间隔持久化、非法数值与技能解锁 |
| `DevastatorSourceTest`、`WeaponInputRoutingTest` | 11 | 正式充能/输入路由、原版动作时钟与垂直瞄准资源、实际落地 seek、范围缩放、短蓄力回退 |
| `BulletdiveTerrainTest` | 3 | 高度与有界预算、碗状坑形、硬度门槛与受保护材料 |
| `AuthoredGpuTransformsTest` | 2 | 缓存地刺变换保留原创顶点/法线，组织曲线在远离原点时保持世界坐标与稳定参考轴 |
| `HumanoidReceiverLinkageTest` | 3 | 重现旧旋转映射的断腰，验证受击混入全过程的腰/颈/肩连接、slim 参考点和幼年模型缩放 |
| `NativeLandingPoseTest`、`WeaponMotionClockTest`、`FirstPersonPoseTest`、`WeaponMorphTimelineTest` | 18 | 落地姿态、动作时钟、第一人称姿态及变形时间线回归 |

这些数学、路由和状态检查与下面的实际游戏输入、伤害、渲染观察分别记录。

**13 个实机案例全部通过。** `StandaloneSkillSmoke` 通过真实 `WeaponInputPayload` 发起招式，观察生产控制器、同步附件、实际动作 clip 和 `LivingDamageEvent.Post`，没有直接替代正式招式的启动入口。

| 案例 | 观察到的结果 |
| --- | --- |
| `ground_graveyard`、`air_graveyard`、`ground_barrage`、`air_barrage` | 每例 12 次真实目标伤害，含 4 个初始距离 20 格的目标；每目标重复命中为 0；伤害事件总数与控制器账本相同 |
| `ground_pain`、`air_pain` | 每例 1 个真实目标命中，重复命中为 0 |
| `ground_pain_far` | 只生成 1 个距离 100 格的 Husk；客户端同 UUID/位置与客户端/服务器锁定均确认后输入；该目标实际命中 1 次 |
| `ground_pain_up` | 只生成 1 个正上方 12 格、无重力 Husk；同步/锁定确认后实际命中 1 次，服务器与实际渲染客户端均观察到方向 Y > 0.99 |
| `bulletdive` | 真实滑翔输入进入下落，实际接地后命中 5 个目标；不消耗临界储量，最终储量为 1；生产地形任务完成 |
| `graveyard_low_reserve`、`barrage_locked` | 储量不足/技能未解锁时没有正式毁灭技 session 或临界消耗；普通招式回退允许分别产生 8 / 3 次伤害 |
| `pain_charge_cancel`、`pain_short_charge` | 取消充能不造成伤害；短蓄力在源年龄 1 tick 正常松键，回退普通攻击造成 1 次伤害；两者均未正式释放或消耗储量 |

六种地面/空中正式毁灭技及两个额外 Pain 正例均只提交一次储量消耗。空中地刺与 Bulletdive 的接触伤害要求控制器已记录真实落地，不能仅靠动画播放时间触发。完成/取消后同步 session 和可见特效均清理。地面地刺释放期间额外调用旧公开 `WeaponCombatSystem.request(player, ATTACK)`，同一正式 move/session、动作 clip 和视觉状态仍保留。

**临界质量与 Adrenaline 专项经过 486 个真实服务器 tick。** 使用实际 `Player.heal/hurt` 和注册事件处理器，验证 Consume 溢出、取消治疗、普通治疗、储量先于生命吸收、黄金吸收保留，以及较晚的 Pre 事件将伤害清零时不扣储量。真实致命伤触发后，保护在第 36 tick 到期；免费释放只能使用一次，在第 449 tick 仍受限、第 450 tick 重新允许。普通储量释放不受这个 Adrenaline 专用间隔限制；容量/升级与生命增长的映射另有上述单元检查。

专项还验证 codec 与生命周期处理后保留间隔、清除临时许可。生命周期部分通过派发生产生命周期事件完成，QA 客户端保持连接；没有据此宣称验证了真实断线、重连或多人服务器。

**落地破坏专项使用生产队列、实际方块与真实 `BlockEvent.BreakEvent`。** 下表 12 / 80 为该专项传入生产地形队列的高度参数；正常 Bulletdive 案例另外通过真实下落/接地路由验证队列启动。

| 专项 | 计划半径 / 深度 | 实际破坏方块 | 实际最大半径 / 深度 |
| --- | --- | ---: | --- |
| 高度 12 | 3 / 1 | 8 | 1.414 / 1 |
| 高度 80 | 8 / 5 | 231 | 4.123 / 5 |
| 高度 80、保护事件取消的整条测试区域 | 8 / 5 | 0 | 0 / 0 |

实际空气方块计数分别为 8 / 231，与生产任务破坏账本完全相同。低高度保留铁块，高高度可破坏铁块；基岩、箱子和被保护事件取消的方块保留。重复 session 不增加或替换任务。正常 Bulletdive 案例记录的实际高度约 6.795 格，混凝土地面破坏为 0，任务正常完成；破坏仍受硬度和能量限制。

**原创三维特效与 GPU 路径。** 最终报告 `authoredModel.available=true`、液体材质就绪、`gpuMeshesReady=true`；实际有特效的原生帧断言 `RigidBiomassMaterial.ready()`。运行时使用项目重新制作的三维地刺与组织模型，相关生成脚本在 `tools/author_devastator_spikes.py`、`tools/author_devastator_tendrils.py`，可编辑模型在 `art/devastators/`。本轮 FX 不加载原版解包网格或纹理。最终最大三角索引引用数 219,672，低于每帧 384,000 的预算。

画面复核纠正了触须长板问题和活靶受击时的断腰：主段改为圆厚组织、短横刺和体积红筋，Humanoid 姿态以腰点重建连接。临界蓝条位于原生红条背后并向右延伸，文字增加暗阴影。地刺/触须的单帧宽景相机仅在 QA 中临时切到未加入世界的 ArmorStand，绘制后恢复；距离为 38 / 30 格，Pain / Bullet 为 7 格，方向与采集信息写进 views。宽景能够核对整体范围，中央角色仍会被组织遮住。Bullet 的侧图被前景活靶遮挡，包内选取常规落地图，不以被遮挡侧图证明所有脚部接地细节。这里没有修改生产镜头。

帧时间直接取连续实际 `RenderFrameEvent.Post` 的 `System.nanoTime` 差值；JSON 保留每个原始帧间隔，未用游戏界面的平滑 FPS 代替。

| 范围 | 前次 CPU 模型路径 P95 | 最终 GPU 模型路径 P95 |
| --- | ---: | ---: |
| 整次 QA | 13.8154 ms | 10.6359 ms |
| 特效实际可见的帧 | 35.1698 ms | 10.5058 ms |

可见特效帧的 P95 对应速率按 `1000 / P95` 换算约为 28.4 / 95.2 帧每秒。前次 CPU 报告为 `D:/Blacklight-Standalone/skills-dev7-authored-qa/native-skills-report.json`（11 案例、39 PNG），最终 GPU 报告为前述目录（13 案例、49 PNG）。两次模型、案例和截图数量不同，帧间隔包含原生截图读回、报告写入等 QA 开销；这些数字描述该机器上的实际验收过程，不作为普遍性能保证。最终整次 QA 的最大帧间隔为 304.0954 ms，可见特效帧最大为 44.2791 ms。

**构建边界。** 可编辑 `.blend/.obj` 保存在资源 source set 外的 `art/`，运行资源为自行导出的模型 JSON；Blockbench 项目文件也由资源规则排除。`build.gradle` 的 JAR 规则排除 `Standalone*Smoke`、两个专项 Checks、GameTest，以及旧原版 FX 数据、纹理、Shader 和 reader。实际无私有 profile 的 `assemble` 已通过，source-only JAR 扫描确认私有动作条目为 0、无上述旧 FX 或 QA 类；原创模型和 GPU Shader 仍在，全部生产 class 与编译输出相同。完整试玩包另由打包脚本逐项校验生产 class、244 个动作/profile 哈希、模型、Shader、许可和依赖。

不传 `bundleMotionDirectory` 的源码编译配方为 `./gradlew.bat clean assemble`，会排除私有动作命名空间并产出 `source-only` 包。上述游戏 QA 使用完整本地动作资料；source-only 仍缺少运行必需动作，不能凭本次验证视为可直接游玩的公开成品。第三方资料边界继续以 `THIRD_PARTY_ASSETS.md` 为准。

验证范围限于本机单人集成服务器与这一专用 QA 存档。单元结果、真实网络输入、服务器伤害与 native framebuffer 共同支持上述结论；没有覆盖独立多人服务器、真实断线重连、所有武器/地形/目标类别，或原游戏所有回调与特效的完整复刻。
