<!-- Current runtime decision: reference-based authored models, no original FX meshes/textures. -->
# 临界质量、毁灭技与 Bulletdive 的本地源依据

核查日期：2026-10-12 至 2026-10-13。对象为用户本机 2009 年《Prototype》的 Alex Mercer。使用现有私有解包文件和原游戏只读数据，不以普通砸地、普通拍掌、普通 Critical Pain 的现有 Mod 动作充当正式毁灭技。

原始身体曲线与派生 MATRIX 动作继续保存在仓库外。公开的 `tools/export_devastator_motions.py` 只包含可编辑的导出逻辑、资源名称、FIG 节点编号和时序元信息，没有原版网格、纹理或关键帧数组。程序化组织特效与 Minecraft 的伤害、目标筛选和移动物理分别实现，不能称为完整原引擎移植。

## 输入和许可

直接读取已有 `fig-r25-basic/prototype_devastator-owners.json`：

| 正式毁灭技 | 源普通输入分支 | 原版按钮组合 |
| --- | --- | --- |
| Groundspike Graveyard | `ground_pound` | `Attack` + `Special` |
| Tendril Barrage | `sonic_clap` | `Action` + `Special` |
| Critical Pain | 地面 `0x928f080a1094c925`、空中 `0x5e200d8885c9584c` | `Attack` + `Action` |

`Action` 是原作抓取按钮，不是 Minecraft 热键的字面名称。组合必须先于单键攻击/抓取处理。地面 Input 持续充能轨道通常为 0.833333 秒，空中对应 windup 的充能轨道为 1 秒；它们都在满充能 `Attack >= 1`、`devastatorAvailable` 为真且专属 unlockable 已解锁时进入正式 `release/devastator` 分支。普通 release 是不同节点，不能因名称相似就绕过临界质量许可。

源 `Pressed`、`Up`、`Charge`、`SupportingSurface` 条件和事件优先级已读取；按键缓冲、组合仲裁、Minecraft 的 EP 费用和解锁界面仍是适配实现。没有将未知解锁哈希猜成成本。

## 正式身体动作与局部时钟

以下时间相对各拥有节点。父节点开始、Input 充能和后续节点切换需分别相加，不能把所有数字当成一个全局动作时间轴。

| 分支 | 节点 | 正式身体动作与源区间 | 已读到的接触/控制时间 |
| --- | ---: | --- | --- |
| 地面 Groundspike antic | 18420 | `alex_dev_charge`，0 至末帧，1.5 秒 | 无直接伤害记录 |
| 地面 Groundspike release | 19476 | `alex_dev_groundpound`，18–26 帧，0.266667 秒 | 下一节点开始地刺 |
| 地面 Groundspike active | 20164 | `alex_dev_groundpound`，26 帧的末姿态，声明 2 秒 | direct hit 为 0、2.8 秒；目标控制轨道结束字段为 `-1` |
| 地面 Tendril antic | 34608 | `alex_dev_charge`，0 至末帧，1.5 秒 | 无直接伤害记录 |
| 地面 Tendril active | 35696 | `alex_dev_whip`，15–40 帧，`Hold End Frame` | direct hit 为 0.433、2.8 秒；DevastatorTrack 为 0.433333–3 秒 |
| 空中 Tendril antic | 81992 | `alex_dev_charge`，20–70 帧，1 秒；共轨 `alex_loco_jump_long_D`，20–30 帧 | 飞行刹停轨道为 0–1 秒 |
| 空中 Tendril active | 77184 | `alex_dev_whip`，15–25 帧；共轨跳跃 20–30 帧，均保持末帧 | direct hit 为 0.416667 秒；DevastatorTrack 为 0.433333–3 秒 |
| 地面 Critical Pain antic | 2544 | `alex_dev_charge`，0–70 帧，1.5 秒 | 无直接伤害记录 |
| 空中 Critical Pain antic | 52104 | 同样的 charge 0–70 / 1.5 秒；另有原作持枪姿态覆盖 | 无直接伤害记录 |
| 地面 / 空中 Critical Pain active | 3600 / 53552 | `alex_dev_critical_pain`，0–25 帧，0.833333 秒；局部 0.5 秒后接真实 vertical-aim 源轨道 | 两条同刻 direct hit 为 0.666670 秒，条件不同；TendrilDevastatorTrack 为 0.666667–3 秒 |

空中 Groundspike 的正式许可节点为 73368，antic 节点 73508 有 `SupportingSurface` 条件的序列和另一条空中序列。当前 owner IR 的本地 branch index 未还原为完整执行图。导出使用已读到的 common air antic、原版 groundpound 下落末姿态和正式地刺身体节点；实际接地后恢复正式地刺时钟由 Minecraft 控制。这一点属于明确的分支/物理适配，不能声称空中所有原生回调已经重建。

Groundspike 的 2 秒身体保持与 2.8 秒 sibling hit 不一致。导出选择保留末姿态至 3 秒，覆盖该晚期记录；这个 3 秒租约是本轮调度适配，不是擅自把 `-1` 解释为 3 秒。Tendril 和 Critical Pain 的 3 秒控制终点有直接轨道值依据。

### Critical Pain 的真实瞄准输入

`AnimationBlendVerticalAimTrack` 的本地 EditP3D 字段布局与 172 字节源 payload 对齐：

| 节点 / 轨道 | 中立 | 向上 | 向下 |
| --- | --- | --- | --- |
| 地面 3600 / 3836 | `alex_dev_critical_pain` | `alex_dev_critical_pain_aim_up` | `alex_dev_critical_pain_aim_down` |
| 空中 53552 / 53916 | `alex_dev_critical_pain_forward_air` | `alex_dev_critical_pain_up_air` | `alex_dev_critical_pain_down_air` |

六条资源均引用 31–65 源帧，speed 1；上/中/下基准角分别是 +90 / 0 / -90 度。导出保留三个源姿态作为可混合的独立动作，没有用手工抬臂代替。源码数组在个别瞄准资源的末端会早于声明 endFrame 65 结束，采样维持该资源已有末值。原作目标高度偏移、插值距离、角速度与所有 blend driver 状态尚未完整移植。

## 组织特效与目标控制参数

由本地 `Prototype1/Track/DevastatorTrack.cs`、`TendrilDevastatorTrack.cs` 和 `BranchReference.cs` 的序列化布局核对源 payload，读取了具名字段；不是把任意浮点槽当成伤害参数。

| 参数 | Groundspike | Tendril，地面和空中 | Critical Pain，地面 / 空中 |
| --- | ---: | ---: | ---: |
| 半径 / 距离，源单位 | Radius 40 | Radius 30 | Distance 200，非爆炸半径 |
| 目标 / 组织条数 | TargetCount 40，MinTargetCount 50 | TargetCount 50，MinTargetCount 60 | TendrilCount 10 |
| 目标 AttackTimeBeginStart–Stop | 0.333333–1 | 0.666–1.5 | AttackTimeBegin 为 0 / 0.333333 |
| 单目标 AttackTimeDuration | 0.5 秒 | 0.5 秒 | 1.333333 / 1.666667 秒 |
| AttackDamage，源单位 | 500 | 400 | 1250 |
| RandomDamage，源单位 | 25 | 20 | 此轨道无该字段 |

源目标分配包含真实目标与随机目标分支，不等于简单圆形范围内每实体重复受到这些伤害。同步 hit 记录、生成/回收时钟、原作优先级与受害者回调需要区别；目前没有完整重建原作所有目标类别和回调。Mod 的半径、数量上限、伤害单位换算、脉冲及每目标去重规则须标为 Minecraft 适配，不能把上表的 500/400/1250 直接当作 Minecraft 生命点。

关键 FX 相对 active 节点：

- Groundspike：`devastator/ground_pound/critical` 与 `devastator/charge/arms_hold` 从 0 秒开始。TOD 确有 Small/Mid/Large 的 `DevastatorGroundSpikeSingle*` 对象和 Random 版本。
- Tendril：`devastator/sonic_clap/critical` 从 0.4 秒开始；`charge/upper_hold` 从 0.333333 秒开始；`sonic_clap/retract` 从 2.333333 秒开始。地面和空中都存在。
- Critical Pain：`critical_pain/attack` 在 0、0.666667、1.333330 秒分批启动；`critical_pain/critical` 在 0.666667 秒开始，空中使用其 `/effects` 子路径。attack 的结束字段 `-0.033333` 保持未知，不推断负时长或倒放。

研究曾直接导出本机原作地刺与须状组织的三角网格、法线、UV 和其明确绑定的 DDS 贴图。用户随后明确要求“照着原版建模”，这些派生网格和贴图现仅作本机私有参考，不进入新的运行时 JAR。生产地刺已经改为在仓库内独立创作的三维模型，组织驱动、湿材质、形成/回收与 Minecraft 世界碰撞由 Mod 重建；不声称移植了原版 Shader、原生 sequencer 或全部颗粒特效。

### 原创生产地刺

`tools/author_devastator_spikes.py` 只读取公开的 `art/devastators/spikes.model.json` 建模配方，不读取任何私有几何数据。十二个 OBJ 同目录可编辑，生成的 `assets/blacklight/models/devastators/spikes.json` 保存原创三角网格、法线、占位 UV 和逐顶点 RGB。模型结构包括厚组织根与肌束、非圆截面的双叉反曲骨刃、偏心分叉和真实凸起的红色筋络；红筋有体积，未复用原作贴图。

六个 id 保持 `groundspike_small/mid/large` 与对应 `groundspike_cluster_*`，另有六个 `_lod`。单刺地上主尖高 2.90 / 4.15 / 6.95 格，完整簇为 3.45 / 6.35 / 7.75 格，根部留有地下体积供地面遮挡。模型已经按 Minecraft 世界坐标创作，`sourceScale=1`；不能再乘研究用的原作单位比例。攻击范围仍可独立沿下面的人体一致比例标定，模型几何尺度与攻击半径不是同一个字段。

```text
python tools/author_devastator_spikes.py
```

每项模型采用 flat `positions/normals/uvs`（每顶点 3/3/2）、每顶点 packed RGB `colors`、flat 三角 `indices`、`bounds.min/max`。使用白纹理与原创顶点色，无 `textureName`；完整单刺为 2186 三角、簇为 7594 三角，对应 LOD 为 806 / 2134 三角。LOD 沿相同控制曲线重建闭合截面，保留主轮廓与凸起红筋，未通过丢三角降密度；同尺寸模型共用保守包围盒。

建模工具检查顶点/索引有限性、单位法线、开放叉刃/凸起红筋组件及 LOD 三角预算。离线轮廓图已经检查，游戏湿材质、增长/回收、GPU、联机和帧率需要运行时验收。

### 真实地刺与组织资源

`tools/export_devastator_fx.py` 只包含格式解析、明确的资源名称和导出逻辑。原作派生数据保存于私有 profile 的 `devastator-fx.data` 和 `devfx_*.png`，不放入公开仓库：

| 私有 mesh id | 原资源 / 三角形数 | 明确的原材质绑定 |
| --- | --- | --- |
| `groundspike_small` / `groundspike_mid` / `groundspike_large` | `alex/alex.p3d.rz` 的 `groundspike_single_*` Skin，59 / 73 / 73 | Shader `color=groundstab_color.dds`；`normal=groundstab_nm.dds` |
| `groundspike_cluster_small` / `groundspike_cluster_mid` / `groundspike_cluster_large` | 同包的完整 `groundspike_*` 簇 Skin，768 / 769 / 765 | 同上 |
| `tendril_segment` | `startup_effects.p3d.rz` 的 `Tendril_SegmentShape`，416 | `diffuseTexture=tendril_color.dds` |
| `tendril_spike` | 同包的 `Tendril_SpikeShape`，52 | `diffuseTexture=tendril_color.dds` |
| `whipfist_segment` | 同包的 `Whipfist_Segment_AShape`，916 | `diffuseTexture=whip_spike_c.dds` |

单地刺 TOD 的 Small/Mid/Large 及 Random 六种 `DevastatorGroundSpikeSingle*` 都明确引用 `//prototype_claws/ground_spike/devastator_cluster_shard` 和对应 `groundspike_single_*` drawable。三种 Skin 与完整簇均只有一个 identity bind 根关节；目前未确认这些地刺存在独立 baked 生长动画，不能把刚体 bind 几何称为已移植原作生长曲线。`prototype_claws` 的真正 `devastator_cluster_shard` owner 位于 78928，包含 native ground-spike 状态检查及受害者附着/攻击节点；受害者的 puppet 动画不能当作地刺骨骼动作。

已核对 C# 序列化布局的 `GroundSpikeSpawnClusterTrack` 在毁灭技 offshoot 51516/51736/51920 及 Random 分支 52196/52416/52600 声明 `AscendTimeMin=0.2`、`AscendTimeMax=0.5` 秒，`TwistMin=3`、`TwistMax=10`；各次 offshoot 是一个 shard。原普通 master 的 `GroundSpikeClusterDescendTrack` 39256 为 `DescendDuration=0.2`、随机 delay 0–0.25 秒，offshoot 回收节点 53844/54196 为 0.2 秒、delay 0–0.2 秒。它们是各自局部 owner 的配置，不是已经证明的整个毁灭技统一全局回收时刻，不把普通 master 分支盲套给 native 毁灭目标。

私有研究几何保留源 Y-up 坐标，逐顶点 float32 position / normal / UV 与原 uint16 三角索引；导出不按名字猜贴图、不用平滑圆管代替真实有棱组织。原 `groundstab_nm` 已转 PNG 供私有研究，这些贴图不用于新的原创生产模型。原 tendril 材质另有 consume 通道，私有导出明确记录它的 diffuse 绑定，但没有复制原 shader 实现。

世界比例采用与已有身体一致的 `0.8721314614129095 × 0.9375 = 0.8176232450746027`。比例由原 Alex 腿链与现用角色模型标定；原头顶/踝的高度和腿长符合人尺度，但没有单凭此将源单位正式断言为“米”。同一比例下 Radius 40 / 30 映射约 32.705 / 24.529 格，Distance 200 约 163.525 格。原先固定乘 0.25 不是这个身体标定，不能称为已经核准的原作单位换算；Distance 仍是前方控制距离，不能解释为全向爆炸半径。Minecraft 的目标筛选、方块场景与伤害单位仍是适配。

私有研究 JSON 契约是 `version=1`、`coordinateSystem=source_Y_up`、`worldScale`、`meshes` 对象。每项含 flat `positions/normals/uvs`（每顶点 3/3/2）、flat `indices`、`bounds.min/max`、`sourceScale`、`longitudinalAxis` 和 `textureName`；`textures` 记录原 DDS 与 PNG 的哈希。九网格数据约 715 KiB，四张 PNG 留在私有 profile 作参考，新的运行时 JAR 不复制这些派生模型与贴图；运行时使用上面的原创公共模型。

```text
python tools/export_devastator_fx.py --game-art <local Prototype/art> --output <new private profile> --audit-output <private FX audit directory>
```

导出已经完成原 RZ 声明大小、chunk/stream 边界、float 属性描述、顶点数、索引范围、单根 bind、明确的材质链与 PNG 解码检查。GPU 效果、联机和最终游戏观感需要独立验收，这些数据检查不等于画面通过。

### FX 补查：真正的组织束、起点与回收

补查直接使用同一 owner IR 的原始 payload、本地 `Prototype1/Track/TendrilTrack.cs`、`DevastatorTargetGroundSpikeTrack.cs` 字段布局，以及 67 关节 Alex 源骨架的名称哈希；没有依据宣传封面推断模型。新私有 `devastator-fx.data` 已导出真实地刺 Single/Cluster 的 Small/Mid/Large 和 Tendril/Whipfist 的段、尖刺几何及纹理。原版 Shader、目标分配及完整 FX 回调仍需分别处理，几何导出不等于完整特效移植。

`TendrilTrack` 的 168 字节 payload 在基础头之后依次为 `TimeBegin`（+4）、`TimeReverse`（+8）、`TimeEnd`（+12）。旧 owner IR 对该类型的通用 `time_end` 标签实际读取的是 `TimeReverse`，不能把它当作生命周期终点。`TimeReverse=-1/-0.033333` 的原引擎触发语义仍未知；`DevastatorTargetGroundSpikeTrack` 只有 `TimeBegin`、Template、Parameter、Random，没有同布局的 `TimeEnd`。

| 正式分支 / 原始轨道偏移 | 源 StartJoint → GrabSlotJoint | 直接读到的形成参数（源单位） |
| --- | --- | --- |
| Critical Pain，6552、6864、7176、7488、7800、8112、8424、8736；空中同族 56724 起 | `Wrist_R` / `Wrist_L` → `Spine_3` | Scale 1–2；ForwardVelocity 30，首变体 35；ReverseVelocity 200；CircleRadius 0.5；SegmentCount 5，两个变体为 15；首变体 TimeReverse=0，其余 -1，TimeEnd 均 -1 |
| Tendril，车辆族 41176、41384 | `Shoulder_R` → `Motion_Root` | Scale 0.5 / 0.7；ForwardVelocity 0；ReverseVelocity 350；CircleRadius 1；SegmentCount 10；两条均 TimeReverse=-0.033333、TimeEnd=-1 |
| Tendril，hunter 42272、42480 | `Shoulder_R` → `Neck` | 两束 Scale 0.3；ForwardVelocity 0；ReverseVelocity 350；ZAxisRotation +90 / -90；TimeReverse=-0.033333、TimeEnd=-1 |
| Tendril，prop 43492 与随机族 44028、44340、44652、44964、45276 | `Shoulder_R` / `Shoulder_L` / `Spine_1` / `Spine_2` / `Spine_3` → `Spine_3` | Scale 0.3–0.7；ForwardVelocity 30；ReverseVelocity 350，prop 500、首随机变体 1000；CircleRadius 1；SegmentCount 10 |

Pain 变体的 WaveSpeed 为 2–7、WaveLength 7–16、WaveAmplitude 0.15–0.5；Tendril 的上述随机变体也使用不同的这些值，形成不同波形。共同 WaveAmplitudeRampUp 为 0.2、RampDown 为 0.5，CorkscrewAngle / RotationSpeed / TravelingSpeed 全为 0。它们支持不同粗细和波动、较快回收的组织束，不支持用一个全局缩放包络同时缩小所有长管，也不支持擅加持续螺旋。字段名能证明速度、波形和骨点名称；运行时哪方拥有骨点、实际积分、负时间哨兵及被执行目标的特殊连接回调尚未完整还原。

三种正式 FX 的调度应分别跟随：地刺 active 节点 20164 的 arms_hold / ground_pound critical 从 0 开始，目标控制在 0.333333–1 秒依次起动；Tendril active 35696 / 77184 的 upper_hold 为 0.333333、critical 为 0.4，目标为 0.666–1.5，独立 retract FX 为 2.333333；Pain active 3600 / 53552 的 attack FX 在 0、0.666667、1.333330 分批起动，critical 为 0.666667，目标控制终点为 3 秒。这里全部是 active 局部时间，前面还要加正式 antic 和身体释放段；单目标 AttackTimeDuration 与整招时长不同。

范围也不是同一个概念：20500 的 Radius=40、35932 / 77248 的 Radius=30；4128 / 54208 的 Distance=200 是定向距离，TendrilCount=10 是束数。地刺 RandomMinRadius=2、RandomHeightArc=0；Tendril RandomMinRadius=10、RandomHeightArc=80、MinHeightArcOnGround=5，显示源控制具有高度与内圈参数，不能只画水平等间距圆环。TargetCount / MinTargetCount 和真实/随机分支存在，但分配回调未知，不能宣称会找齐 50 或 60 个敌人。旧 `sourceRadius * 0.25` 的 Minecraft 范围换算没有等比例依据。私有导出的 actor 一致 worldScale 约 0.817623，地刺 Small/Mid/Large 源高度 3.577 / 5.092 / 8.547 对应约 2.925 / 4.163 / 6.987 格；玩法缩放需另外标明适配。

已逐帧查看本机原游戏 `movies/attract.bik` 的 50.5–53 秒连续片段：先形成胸腹组织团，再爆出密集、尖齿和分叉的黑色组织束，向真实目标伸展，外围细须逐渐绷直，命中端有缠绕组织与碎片。局部红筋与湿亮不同于整根霓虹轮廓。此片是原版宣传剪辑，其镜头移动和剪切不能证明完整回收时间、目标总数或 Pain 全流程；17–21 秒地刺片段还混有普通爪击，不据此冒认正式 Graveyard。参考逐帧仍留在当前聊天的 `work/devastator-original-reference/`，原视频及派生画面没有加入发布包。

## Bulletdive Drop 的真实下落与接地段

依据 `fig-r25-basic/prototype_air-owners.json`，真正的 Bulletdive 位于 `air/actions/skydive/common/windup/0xe4d0b57ec32add99`，不是 `alex_bullet_smash_stomp` 的普通空中踩踏。

- skydive 入口 110100 包含 `Run Down`、`Jump Released`、`Action Up` 及其他场景/持有条件。Bulletdive 节点 116696 另要求 `Special Pressed` 和独立 unlockable `0xcf81c8621e663432`。
- windup 116832：`alex_bullet_smash` 源 35–43 帧在 0–0.266667 秒，43–46 帧在 0.266667–0.666667 秒。独立充能轨道也为 0.666667 秒。
- drop 118004：`Special Up` 或 `Attack charge >= 1`；127116 使用 46–49 帧 / 0.166667 秒；cycle 128496 使用 50–57 帧并保持末帧，窗口 1.333330 秒。
- hit_ground 118172 明确要求 `SupportingSurface`。分级检查源 Locomotion Y 速度 <= -135、-100、-70、-50 以及默认低速分支。原作速度单位未换算为 Minecraft 格/tick。
- 接地身体为 `alex_loco_jump_heavy_all`，110–115 帧后保持，再播放 115 至末帧。默认低速节点 125588 的保持窗口 0.333333 秒，高度更高的分支具有 0.666667、0.833333、1 秒等保持窗口。

导出将默认低速 landing 身体拼入同一个 `bulletdive_drop` main，并带固定 `blacklight.landing.holdTime/impactTime` 标记。固定时间是采样位置，不是固定落地时刻：飞行时保持真正源 fall-cycle 姿态，收到真实 `onGround` 后才从 impactTime 开始接地段和伤害。不得用名义动作计时器假装接地，或同时追加源 Coord 与服务器下落速度。

落地 animation 126244 明确 `blendInTime=0`、`blendOutTime=0`。导出必须在真实接地后立即切入 `alex_loco_jump_heavy_all` 源 110 帧；不能用导出器的默认 0.033333 秒跨段混合保留倒立的 fall 57 首帧。源 110 是身体低伏、脚撑地的姿态，不是头朝地下扎的翻滚起始。此修正只将第一段 landing 的源混入时间设为 0，`impactTime=2.166667`、effect/damage 时钟及总片段长度保持不变，没有通过跳进几帧绕过错误。

导出器新增首个 impact 样本的姿态质量校验：将全部 67 个源世界矩阵与独立解码的 heavy 110 加单次 stage root 重定位比较，再核对输出 native biped 在 Root/Coord 分离和 0.9375 模型比例下的脚点接地、头高于脚。具体数值写入私有 audit。这个检查证明该采样点使用真正源落地姿态，不替代实机 `onGround`、插值或 GPU 画面验收；也不将此前 flight 的 diagnostic Coord 再加到落地实体位置。

其他 skydive/glide 方向混合和 57–75 源帧的额外分支没有在本轮当作通用 Bulletdive 身体强行串联。Bulletdive 的伤害/半径控制字段仍未完成窄范围解码，因此 `radiusSource/damageSource/targetCountSource=0` 只是导出元信息的未知标记，不表示源伤害为零。

## 临界质量与 Adrenaline 的配置数值

本机 `alex_tod.p3d.rz` 的 `PrototypeTemplate` 含 `proto::PrototypeHealthBehaviour`。将其六条 unlockable 记录与后面的 19 个字段，与本机 DLL 的属性注册表（VA `0x102c40ac..0x102c43c6`，首选基址 `0x10000000`）逐项核对：

| 源字段 | 读到的值 |
| --- | ---: |
| `devastatorHealthCost` | 400 |
| `defaultHealthPercentage` | 0.7 |
| `criticalMassHealthGainFactor` | 0.55 |
| `criticalMassExitHealthLoss` | 200 |
| `consumeHealthMultiplier` | 1.3 |
| `adrenalineSurgeThresholdHealth` | 300 |
| `adrenalineSurgeHealthOnEntry` | 200 |
| `adrenalineSurgeUndamageableTime` | 1.8 秒 |
| `minTimeBetweenAdrenalineDevastators` | 22.5 秒 |
| `lowHealthThreshold` | 0.3 |
| `adrenalineSurgeUnlockable` | 21 |

六条 unlockable 中，15、16、17 各 `healthAdded=300`、`criticalMassHealthAdded=0`；18、19、20 各 `healthAdded=0`、`criticalMassHealthAdded=400`。这是三档普通生命增长和三档临界容量增长的源配置。

`minTimeBetweenAdrenalineDevastators` 确属这个 health behaviour 的实例字段（member offset `0x208`），完整名字为 Adrenaline 专属毁灭技间隔，不能套成所有毁灭技的通用冷却。配置字段得到证明，不代表原作的致命伤拦截、阈值进入、再武装、离开临界与消耗回调全都已经解码。基类生命字段/升级后的运行时上限尚未全部匹配，不把 300、200 直接宣称为当前 Minecraft 固定血量或已经证明的百分比。

Mod 使用基于自身最大生命与升级的容量/花费映射，EP 费用和残血触发策略属于 Minecraft 适配；可以按已确认的 0.55 因子及 1.8 秒保护时间对齐部分数值，但应分别说明仍未恢复的原生语义。

## 导出与接入契约

```text
python tools/export_devastator_motions.py --private-source <local-private> --retarget <private numpy_retarget directory> --baseline <old complete profile> --output <new private profile> --audit-output <private report> --health-source <local alex_tod.p3d.rz>
```

本轮本地目录为 `D:/Blacklight-Standalone/motion-profile-dev7`，已复制旧 profile 的 224 个文件并新增 20 个动作，共 244 文件；旧 224 文件在原目录和新目录逐个 SHA-256 相同，没有改原来的 `motion-profile`。private audit 在 `D:/Blacklight-Standalone/dev7-source-research/export-audit_PRIVATE.json`，不混进可发布源码或动作包。

主要动作键为 `dev_groundspike`、`dev_air_groundspike`、`dev_tendril`、`dev_air_tendril`、`dev_critical_pain`、`dev_air_critical_pain`、`bulletdive_drop`。每个主动作有对应 `_charge`；Critical Pain 地面/空中另有 `_aim_up`、`_aim_down` 真源姿态；Bulletdive 另导出 `_land`、`_flight` 供研究，实际 landing 仍已包含在 main。

主动作包含 Input 起手、正式 antic、释放与收招；可变 Input hold 存于独立 `_charge`。`blacklight.charge.releaseTime` 是 main 中初始 Input 起手结束点。客户端与服务端消费临界并进入 main 时从该采样点继续，`blacklight.devastator` 的 `effectStart/effectEnd/damageStart/damageEnd/targetBeginStart/targetBeginStop` 全部相对这个 release 点；`landing` 与 `travelPose` 则是 main 的绝对采样位置，不能混用。

动作使用已核查 67 关节 Alex 源骨架和既有数值重定向，保持源躯干、手臂、根姿态与足部曲线的关系。按 FIG 源帧与时间段采样，不用无关推进曲线；源 Motion_Root 的全轴位移只分离一次到 diagnostic Coord，Root 保留躯干/盆骨的相对姿态。服务器实际移速、下落与碰撞由 Minecraft 负责。空中 co-owned 跳跃腿部曲线的目标骨架合成属于明确的重定向适配，未声称已恢复原生分区混合器。

导出会检查源 SHA-256、30 FPS、完整通道、矩阵有限性、root/Coord 重组误差、LocalWeaponMotion 时钟和 landing 标记，并逐字节确认旧 224 文件不变。这些是数据与时钟检查，不是最终游戏姿态、命中、联机或效果验收。

本次导出报告中 Root/Coord 重组误差最大约 `2.3e-16`。既有定长腿 IK 的足端误差在两条地刺主体约 0.011 格以内，在多数其他身体轨道接近浮点误差；Bulletdive 的源姿态与目标腿部比例使最大误差约 0.158 格。这个误差没有用无关腿姿态掩盖，仍需游戏画面验收。空中腿部共轨合成后不是原生完整分区求解，不能把主重定向的足端报告当作其完整运行时验收。

## 当前模型与高度破坏实现

当前模型由 `tools/author_devastator_spikes.py` 与 `tools/author_devastator_tendrils.py` 原创建立，可编辑配方、OBJ 与 Blender 文件保存在 `art/devastators`。运行时加载公开的 `assets/blacklight/models/devastators/*.json`；私有 `devastator-fx.data` 与 `devfx_*.png` 只供对照，不打进 JAR。

范围以现有 Alex 重定向实测比例 `.8721314614 × .9375 = .8176232451` 换算：地刺半径约 32.70 格、触须半径约 24.53 格，Critical Pain 的 Distance 上限约 163.52 格，仍是前方组织束距离而不是圆形爆炸半径。Minecraft 目标数量及接触波以服务器判定，并不是承诺原引擎回调已经恢复。

Critical Pain 使用锁定目标的实际中心（含部件最近点），无锁定时沿视线选择目标。远目标的可视推进速度取已知源速度与“距离 / 单目标控制窗口”的较大值；命中等待对应推进时间，并限制在地面 1.333333 秒、空中 1.666667 秒的目标窗口内。这是 Minecraft 调度适配，用来避免沿用 30 源单位/秒却让距离上限内的远目标永远到不了；不是已恢复原作的速度积分回调。实机额外检查 100 格目标和玩家正上方 12 格目标，见验收记录。

临界储备显示为原生红血条背后的蓝色额外血量，沿同一基线向右延伸；每一格释放量映射为自身最大生命的 25%，容量升级增加蓝条可用长度。受到伤害先扣该储备，花费毁灭技则减少一次释放量。黄色肾上腺激增文字是独立的保护/释放状态，不替代蓝色临界血条。

公开模型使用缓存的索引三角形 GPU 缓冲，形成、回收和组织曲线变形由 `rigid_biomass` 顶点着色器处理；材质沿用本 Mod 的湿润组织片元着色器，颜色来自模型顶点。模型、着色器和编辑配方均可随源码构建，未使用私有特效纹理。GPU 未就绪时仍有 CPU 绘制回退，回退性能不等于 GPU 实测性能。

组织主段按整条路径变形时，侧刺的轴向长度也会被放大。重新建模将侧刺限制为归一化轴长的 0.0024，保留近圆多叶横截面、独立短横刺、凸起红筋和横肋，避免出现长薄板。没有通过扩大已解码的波幅来掩盖模型问题。

大招击倒会使用已有 `human_fall` 接收姿态。原 Humanoid 映射只修改平级部件旋转，躯干弯转后腿仍停在原腰点，会出现断腰。修正保留当前模型的参考骨点，以源 root-relative hips/torso 旋转重建腰、肩、颈部的共同连接；外层服装跟随基础部件，绘制后仍恢复保存姿态。receiver 数据没有完整源关节点平移，这一骨点适配保留 Minecraft 身体比例，不是完整源骨架移植；其他模型类型的映射未扩展。

子弹俯冲在实际 `onGround` 后仅启动一次 `BulletdiveTerrain`，取本次会话的最高 Y 与接地 Y 的差。6 格及以下不破坏；之后高度提高能量、碗状坑半径、深度与数量预算，材料硬度消耗能量并受局部强度限制。基岩、方块实体与高耐爆方块保留，逐块经过交互权限和 BreakEvent；被保护方块会保留其下方支撑。全局每 tick 最多处理 192 候选并限时 3ms，最多 8 个声效/碎块反馈点，不生成大量掉落物。半径 12 格、深度 10 格、预算 4096 及伤害映射为 Minecraft 方块玩法适配。
