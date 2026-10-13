# dev.11：俯冲红雾、深坑与蓄力连续性

## 实际缺陷

dev.10 原地蓄力每 tick `seek(duration * charge / maximum)`，随后又按普通秒钟前进。当蓄力秒数与片段时长不同时，下一 tick 再 seek 会产生锯齿状推进。新实现只在 tick 提交前后充能值，身体及武器共用按 partial tick 插值的姿态；满蓄力停在终态。普通步态改用实体 tick 标记，避免服务器世界时间向后校正后 `tick <= previousTick` 使步态停住。

人类 dev.10 日志还出现音效池达到 247 上限，以及服务器落后 2–21 秒。此证据证明存在负载问题，不能仅用静态骨骼矩阵有效来声称完全没有闪动。dev.11 每 tick 全服最多两次俯冲普通破块音效/碎屑事件；实际破块、邻居更新、保护回调和掉落保留。每个任务只缓存最多 128 个自己的新掉落物，近距离同类物品经原版合并方法合并，保持组件、拾取限制和数量。不会扫描或删除其它来源的世界物品。

## 本地解包依据

检查 `prototype_air-owners.json` 的 `hit_ground` owner 118172：必须 SupportingSurface 才进入；落地分支分别调用 `ground_pound/generic2`、`critical`、`charged`、`high_speed`，最高一支另调用 `sonic_clap/critical`。`high_speed` spawn 120788 从落地局部 0 秒开始。各高度镜头请求在约 .133333 秒，较高分支为 large。

重新读取本地 `startup_effects.p3d.rz` 并按原 hash 算法匹配名称：

| Hash | 名称 | 相关源记录 |
| --- | --- | --- |
| `03501dfbdb799756` | shockWave001 | 通用、飞行以及落地冲击波 |
| `b8577261967b2925` | explosionFireBall003 | high_speed 发射约 .066667..3 秒，FadeTime=3，脚部偏移约 -.919 |
| `3bb7ab9a3668255c` | debrisTransform001 | high_speed 局部 0..1 秒，FadeTime=1 |
| `11547f41dd180ee6` | goreBloodMistFanOut001 | 包内确有该血雾 emitter，但不能因此声称 high_speed 使用它 |

红雾按用户要求以原创程序 shader 和有厚度分布的多层烟团实现，替换原先六簇缩小地刺。它借鉴落地瞬时冲击、随后扩散与淡出层次；颜色、噪声、速度与约 3.3 秒整体期限是 Minecraft 适配，不冒称已移植原版粒子曲线或 shader。原版网格、贴图与 emitter 数据不提交。

所有观察者使用服务器已同步的 session、真实落地点和半径，烟雾不随人物掉进坑里移动。每个效果最多 96 四边形/384 顶点，服从全局特效顶点预算；不发逐粒子网络包，不延长伤害时间，不锁镜头。红雾仅写颜色、不写深度，避免遮挡后续透明效果。

## 弹坑

真实落差超过 6 格后：中心深度从 `1 + floor(cbrt(h-6))` 改为 `2 + floor(.75*sqrt(h-6))`。半径、材料成本、总能量仍按实际高度计算，保持无高度上限、服务器权限及 U 设置开关。预算控制逐 tick 工作速度，不删除最终应处理的任务。

80 格实机落差约 78.35，深度 8；640 格实机落差约 638.80，深度 20。软土、剩余能量和不可破坏块影响最终形状。这些数字来自中心加厚的独立 QA 土层，不是在只有四层泥土的超平坦床岩上声称挖出二十层。

## 验证记录

- `D:/Blacklight-Standalone/gait-dev11-standing/continuous-gait-report.json`：原生输入完整 24 tick 原地蓄力；普通/装甲跑步、蓄力、腾空、落地各 16 张原生截图，119 个 charge 渲染帧及实际身体矩阵，断言通过。截图序列只覆盖部分时段，不能代替全部视频或用户验收。
- `D:/Blacklight-Standalone/skills-dev11-native-v2/native-skills-report.json`：80、640 格真实输入/接地/伤害/破块通过。分别真实破坏 231、2024 块，最大实际深度 8、20。纯泥土禁掉落为零新物品；箱子一份、库存恰好六钻石；保护区和开关取消检查通过。该轮日志未见音效池耗尽或 Can't keep up；不等于多人压力测试。
- 首轮地形测试失败保留于 `skills-dev11-native`：旧测试土层仅六层，高度算法加深到八层后掉落物会落出检查区。修正测试土层厚度与保存/恢复范围后重跑，保留恰好六钻石及实际方块数量断言，没有放宽断言。
- 最终红雾采用更低重叠透明度，并打散烟团朝向，避免中心堆叠成实心红球。`skills-dev11-native-v3/native-skills-report.json` 全部通过：13 张原生截图、最高 384 个效果顶点、箱内六钻石完整保留。人工检查 late_retract 与 body_recovery 截图，人物恢复站立后地面红雾继续淡出。
- 最终 Gradle 回归及 assemble 成功：63 项测试，零失败/错误；251 个动作/配置文件校验通过。日志 `D:/Blacklight-Standalone/dev11-regression-build.log`。早期 smoke opacity 测试因直接初始化 RenderType 而在纯 JVM 缺失 Minecraft bootstrap；已将透明度曲线移到纯时间线函数并重跑通过。
- dev.11 专用服务器开发启动通过：`run-dev11-server/logs/latest.log` 记录监听 127.0.0.1:25570、Done；两项新增 common mixin 成功应用，没有加载客户端 shader 的错误。这是单进程启动验证，不是双端或多人压力测试。验证后终止独立 QA 进程，不触碰人类存档。

尚未在本次验证：高延迟、多真实玩家同时释放、任意其它模组繁重保护回调。之前的人类存档掉落物没有清理或删除。
