# 毁灭技组织回收、身体收招与镜头事件

这份记录补充 [CRITICAL_MASS_SOURCE.md](CRITICAL_MASS_SOURCE.md)，只记录本地原版 FIG、P3D 名称和 Track 序列化能够证明的部分。模型仍按参考重新建模；原版网格、贴图、原始骨骼曲线和 shader 不提交公开源码。`devastator_lens` 是本 Mod 原创的 Minecraft 场景后处理，不是原版 shader 移植。

## 时钟约定

源时间是 **所在 FIG owner 的局部时间**。地面正式 antic 为 1.5 秒；当前空中地刺/触须公共 antic 为 1 秒。`blacklight.devastator` 中的时间以 main 的 `charge.releaseTime` 为零点，不能把 antic、attack owner、landing owner 混成同一时钟。实际接地会重新开始 landing 时钟，不能用名义飞行动画时长代替 `SupportingSurface`。

组织返回、身体恢复、伤害控制器和镜头淡出有不同的结束时间。`DevastatorEffectTimeline.visibleEnd` 只延长观察快照/视觉租期；原伤害窗口保持单独判定，不因特效淡出继续造成伤害。

## 可确认的身体恢复

| 正式大招 | 源恢复 owner / animation | owner 局部恢复段 | 当前 main 中相对 release 的恢复段 |
| --- | --- | --- | --- |
| Groundspike Graveyard | 31348 / `alex_dev_groundpound`、`alex_amb_fight_A` | pound 26→25，0..0.666667；idle 15→20，0.666667..2 | 地面 4.766667..6.766667；空中落地后 3..5 |
| Tendril Barrage 地面 | 46508 / `alex_dev_whip`、`alex_amb_fight_A` | whip 25 保持 0..0.333333；idle 15→20，0.333333..0.833333 | 4.5..5.333333 |
| Critical Pain 地面 | 12632 / `alex_dev_critical_pain_step_back` | 0→64，原非循环 clip 2.133333 秒 | 4.5..6.633333 |
| Critical Pain 空中 | 71068 / `alex_dev_critical_pain` | 65→85，0..0.666667 | 4.5..5.166667 |
| Bulletdive 低高度接地 | 125588 / `alex_loco_jump_heavy_all` | 110→115 并保持至 0.333333；再 115→140，0.833333 秒 | 接地后共 1.166667 秒 |

地刺空中回地面身体段仍是显式重定向/调度适配。空中 Tendril 当前以真实 `alex_loco_jump_fall_cycle` 0→18 / 0.6 秒作为有界恢复，**未证明这是原生正式收招 owner**，元数据 `recoverySourceConfirmed=false`。空中 Pain owner 71068 另有从 0.333333 开始、结束于 0.833333 的未知动画 hash `0xf983dd3c4ed79866`；未解出该 overlay，不把它冒称已完整合成。

恢复不一定在原版完整播放后才能操作：31348 的 `onEvent` timeHook=0.5 可进入 `//prototype_bare/common/exit_active`，SupportingSurface 另可进入 `exit_fall`，opportunity 从 0.666667 起可进入 `exit_passive`。12632 的 active/passive 出口均在 0.333333 开放。它们证明出口记录存在，不证明所有条件和原 sequencer 仲裁已复现。

Bulletdive 接地第一个源动画 track 126244 的 blendIn/Out 都是 0。main 进入真实 `onGround` 时必须直接采样 heavy frame 110；不能保留倒立飞行 frame 57 再跨淡入。导出器的独立源姿态检查保持这一约束，Coord 仅诊断，不再加回实体 Y。

## 触须与地刺的返回

地面 Tendril owner 35696 和空中 owner 77184 都在局部 **2.333333** spawn `//prototype_effects/effects/devastator/sonic_clap/retract`。其源 effect owner 143204 的 RadialBlur 持续 1 秒，因此镜头回收覆盖 attack-local **2.333333..3.333333**，超过 `DevastatorTrack.TimeEnd=3`。不能沿用所有组织在统一 progress=.76 时缩放，也不能在伤害结束时直接清除观察快照。

`TendrilTrack` 的真实布局是 `TimeBegin@4 / TimeReverse@8 / TimeEnd@12`。部分旧 generic owner IR 将 offset8 误名为 time_end；`-0.033333` 是 reverse 字段 sentinel，不是实际终止时间。Pain wrist→Spine3 轨道 reverse 字段为 0/-1、end=-1；Barrage 部分 Shoulder→Motion_Root/Neck 轨道 reverse=-0.033333、end=-1。这些值不能直接当正向秒数或全模型淡出时刻。

可确认速度：Pain 逆向 200 源单位/秒，Barrage 多数 350、prop 500、首条 random 1000。当前组织按实际端点距离和返回速度缩回附着点，保持三维截面；Barrage 使用具名 retract 事件，少量分支错开属于 Minecraft 分支调度。Pain 依据三批 FX 发射及实际到达距离逐批回腕，接触停留 0.2 秒是 Minecraft 表现适配；原生 controller 到达/反向 callback 尚未解码，三次 FX spawn 也不等于三次伤害。

地刺 `GroundSpikeClusterDescendTrack` 可确认 `DescendDuration=.2`，普通 master 的 DelayMin/Max=0/.25，offshoot=0/.2。原生的 Descending、ClusterMotionComplete、ClusterInactive 等状态以及 `devastator_cluster_shard` owner 78928 已找到；它没有一条可直接借用的完整地刺缩放动画。**正式 Graveyard 的全局 descend 触发回调尚未证明**。当前在效果租期末尾错开安排 .2 秒下沉，是明确的 Minecraft 调度；模型沿地面平移收回，保持厚根/刃尖形状，不压扁高度或宽度。抓住受害者的 puppet clip 不能当成地刺生长动画。

## 原版 RadialBlur 与空间扭曲

字段依据本地 `RadialBlurTrack.cs`、`EffectTrack.cs`、`PunchinCameraTrack.cs`、`CameraShakeRequestTrack.cs` 的序列化及原 FIG 字节边界。所有表内 pulse 的 Wait/FadeIn/FadeOut、随机量、flicker 均为 0；AttachSpace=GameObjectSpace、Offset=(0,0,0)、Patch 空、InGameCameraOnly=false。

| 原版 FX 节点 / track offset | 所在 owner 起始 | Duration | Intensity start→end | Size start→end | Falloff start→end |
| --- | ---: | ---: | --- | --- | --- |
| sonic_clap / critical / 138880 | 0 | .5 | 100→0 | 0→2 | 0→10 |
| sonic_clap / retract / 147684 | 0 | 1 | 30→30 | 2→0 | 10→0 |
| ground_pound / critical / 155644 | 0 | .5 | 100→0 | 0→2 | 0→10 |
| ground_pound / critical / 155816 | .166667 | .5 | 20→0 | 0→2 | 0→10 |

Tendril critical FX 在身体 attack-local .4 spawn，所以其爆发 pulse 开始于 .4；retract pulse 开始于 2.333333。地刺 critical FX 在 impact/active owner local0 spawn，第二次弱 pulse 晚 .166667。

**Critical Pain 分支没有 RadialBlur 轨道。它有真正的 `distortionObject004` emitter**：`startup_effects.p3d.rz` 的名称经 `hash64` 精确匹配 `0x5290ab5eee1cea14`，命中 FIG `prototype_effects/devastator/critical_pain/critical/effects` track163500。该 track local0..2.333333、FadeTime=2、Scale=1、Offset=(0,0,-.536)、Orientation=(-90,0,0)、Emit/AttachSpace=GameObjectSpace、AbortWhenInterrupted=false。主体 critical FX 在 attack-local .666667 spawn。P3D 名称和 source emitter 参数得到证明；原 distortion shader 的采样、幅度和粒子 fade callback 没有移植或完整解码。当前把 emission lease 与 FadeTime 作为连续场景扭曲/淡出的包络，强度 .35 属于 Minecraft 表现。

Tendril retract 节点还含 8 条 `debrisMoving001`，名称 hash `0x881968e8753fba4c` 精确匹配；每条 local0..0.666667，FadeTime=2、Scale=1、AbortWhenInterrupted=false。这是粒子/碎片发射记录，不是让组织回收持续 2 秒的证明。charge 的 upper_hold/arms_hold 附着轨道则 `AbortWhenInterrupted=true`、FadeTime≈1.06667，应在动作退出后清理。

Bulletdive flight owner127116 的 RadialBlur128316：local0，FadeIn=.1、Duration=.5、FadeOut=.3，Intensity15→0、Size2→2、Falloff0→4。cycle128496 的 track129508：local .033333..2.666667，Duration=5，Intensity9→9、Size0→1、Falloff0→1，附着 JointSpace/Motion_Root。它证明下落的视觉反馈独立于落地脉冲；不能从视觉饱和推导物理下落限速。

## 镜头参数及中断边界

Pain 的 punchin4196：attack-local .5..3.666667，In=.333333、Out=.833333、Dolly=1.1、Zoom=1.5、ReAim=false、VectorY=.6。air54536 同窗口，Out=.5、Dolly=.8、Vector=(0,0,0)。地刺 camera 子分支三条 punchin25456/25880/26092 是不同候选，局部 end=2/2.333333/2.666667、Zoom=.6/.6/.7；未恢复其完整条件选择，不能把三条同时相加。Tendril 候选45872/46148/46360 起于 .166667，end=2.666667/2.666667/3.333333，Zoom=.65/.7/.65；air80132 起于 .166667、end2.666667、Zoom=.65。

CameraShakeRequest 可确认 Pain4260/54600 local .8 small；地刺21964 local .366667 large；airTendril79044 local .566667 medium。源轨道的存在和时刻，不证明所有相机仲裁均由当前镜头模块覆盖。

本次扫描未在正式 devastator 或其具名 FX 子树找到专属 `AbortEffectTrack`。没有“未找到”就假定原版永远不清理：源 exit 路径、EffectTrack 的中断标志和未解原生 teardown 仍在。Mod 只在当前同步动作绘制组织/post effect，取消、死亡、离开关卡或资源重载会清理，既不锁 F5，也不修改鼠标输入。额外 Bullet FOV 用累计 fallHeight/downSpeed 的有界响应，幅度为适配，服务器下落物理保持独立。

## 导出、可复查与运行时

### 可视触碰与服务器固定选靶

`DevastatorVisual.contacts` 保存服务器在 target controller 开始时一次选定的 `Contact(targetId, at)`；`at` 是绝对 release-age。Barrage 与 Graveyard 按这个列表的顺序解析实体，不再每个渲染帧搜索附近对象、重新排序或补选受害者。随机填充的组织/地刺只表示视觉，不能造成伤害。目标死亡后仍在客户端实体表内时保持该目标引用；已经卸载/移除的实体不换成另一个目标。

Barrage 将 `at` 转为 attack-local `at - (effectStart - .4)`。先使用 `30 × .8176232451` 的源前向速度，按 `arrival - distance / speed` 倒推发射，最早 attack-local .4；当服务器接触窗口不够覆盖真实距离时，只提高该可视分支的推进速度以保证按时到达，明确属于 Minecraft 画面/服务器时钟对齐。回收仍使用具名 2.333333 事件与源反向速度；若一个同步接触点晚于回收事件，会让该分支至少接触并完成短停留后再收回。

Graveyard 使用同步目标的位置作为对应地刺落点。源 rise 值 .2.. .5 秒按实例选取，再以 `at - effectStart - rise` 倒推发射；接触早于完整 rise 时缩短这一次 rise。因模型只平移出地面，到达接触时刻时地刺已形成完整高度，避免伤害发生时才开始慢慢冒尖。全局 descend 触发仍是上述明确适配，.2 秒下沉形状保持不变。

Pain 的第 0 条主组织轨使用唯一同步 `target_control` 接触对象/时刻，终点按该目标实际最近点；其余束保留 `frame.targetId/direction` 所表示的 direct lane 和源 FX 发射批次作为视觉。首次主轨到达使用同样的倒推发射/必要加速，回腕使用源速度 200。**target_control 与直接攻击是两条判定 lane，不能把多个可视束或三次 spawn 写成多次原生 hurt。** Source HitTrack 的 Damage、Volume 与 controller 参数由服务端分别处理，本 renderer 不触发伤害回调。

目前原版 wrapper 启动 offset 的字段已能定位，但外部 scheduler 传入 owner-clock 还是 controller-clock 尚未闭环；尤其不能把 `.433333` 额外相加直接宣传为已经确认的原作规则。画面只跟随本次服务器实际同步的 Contact.at。若一个同步包在接触时刻之后才到客户端，只能在首个可见帧呈现已到达状态，不能在客户端预选另一批对象伪造过去的过程。

`tools/export_devastator_fx.py --fig-audit-only --private-source <local-private> --fig-parser <reviewed audit_weapon_fig.py> --track-definitions <Nixson.Prototype.Fight directory> --game-art <local art> --audit-output <private directory>` 只导出字段/owner 路径和 SHA-256，不生成网格、贴图或曲线。Effect prefix 只解析至 CloneTint（172 bytes），剩余 particle biases/overrides 不声称完整恢复。

本轮新目录 `D:/Blacklight-Standalone/motion-profile-dev9` 先保留 dev7 文件，再从原 224 profile 重新导出 20 条 main/charge/aim/landing 与新增 7 条 `_recovery`。旧 224 文件逐字节一致，旧 dev7 未覆盖。新元数据含 recoveryStart/End/Animation、source retract 与 radialBlur。私有 FX 数据/PNG 即使保留在该私有目录也不能被构建打入 JAR。source motion 仍遵循当前项目的私有动作资源边界。

`ClientDevastatorPostEffect` 以 AFTER_LEVEL 场景色缓冲→swap→main 做径向模糊/扭曲，独立保存并恢复 depth；HUD 在后续阶段绘制。原版正向爆发/反向收束时钟控制原创 lens shader，纹理没有来自原版。`ClientDevastatorCamera` 只调整 Bullet flight 的 FOV 表现，保持用户 FOV 和自由视角。

当前地刺资源已改为本 Mod `geo/ground_spike.geo.json` 的同系 cuboid 建模/UV，使用公开 `claw_biomass.png`，单体 144 三角面，small/mid/large 高度 1.8/2.5/3.2 格；GPU/CPU 都把完整模型平移入土，未直接套用私有原作网格或特效贴图。这是资源/变换静态核对，实际 Minecraft 风格与接触画面仍需要本轮实机验收。

本轮已做导出数据、字节边界和本机 Minecraft/NeoForge API 静态核对；数学测试覆盖组织反向长度、.2 秒地刺下沉、视觉越过伤害 lease、Pain emitter 淡出和高度/速度视觉响应。最终原生 v3 的 shader、后处理、HUD、组织返回和身体恢复结果见 [dev.9 验证](DEVASTATOR_DEV9_QA.md)。lens 以 alpha=1 输出完整世界 RGB，避免透明天空经末级 alpha blit 变黑；target 销毁后重新绑定主缓冲，保护后续手臂绘制。第三人称锚点依据见 [触须连接](TENDRIL_PRESENTATION.md)，不将原生 GPU 就绪等同于原作液体效果完整复刻。
