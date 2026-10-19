# 毁灭技许可、资源消耗与伤害回调核查

2026-10-13；本机《Prototype》一代 PC Alex。补查现有 FIG、C# 序列化布局及原 DLL 的实际回调，未以 Wiki 数字或轨道名称猜结算。原始网格、纹理、身体曲线和反汇编全文不进入此文档。

## 核验对象与地址约定

本机 `prototypeenginef.dll` SHA-256 为 `8cba8da3412df4f49cd61d02445e87a0cda551dea4f2fc1e66b19c1bfe28a760`。下列地址是首选基址 `0x10000000` 下的 VA，只对该文件有效。健康配置来自既有 `alex_tod.p3d`，SHA-256 为 `383b26f83fcc87cf5146604dddd28219a520a33129ea6510484e1d36e9e9ed78`。

FIG 引用既有 `fig-r25-basic/prototype_devastator-owners.json`；直接碰撞补查从同一 FIG blob 读取原 payload，并按 `CollisionHitTrack.cs` 的完整 276 字节布局及 `BaseProperty.cs` 的 enum/bitfield 宽度核对。骨点名与 67 关节 Alex 骨架做精确哈希匹配，不以序号猜左右手。

## 任意正临界质量可以释放，完整花费仍然扣除

`DevastatorAvailableCondition` 的 RTTI/vtable 实现为 `0x100b3c50`。它向 actor 发送 group `0x14`、type `0x22` 的可用性查询。`PrototypeHealthBehaviour` 的分发 `0x102c3c50` 经 jump table 把该消息送到 `0x102c29f0`；非 Adrenaline 许可最终是 `currentHealth`（成员 `+0x0c`）严格大于普通最大生命（`+0x14`）。没有检查临界储量是否足够支付 400。

使用消息是 group `0x14`、type `0x21`，经同一分发表送到 `0x102c3180`。非 Adrenaline 分支先确认生命仍高于普通上限，再减 `devastatorHealthCost`（成员 `+0x1b8`）。该字段的注册 `0x102c40b6..40d4` 与本机配置值 400 相符。

### 2026-10-19 复核与当前结算规则

重新读取上述 SHA-256 的本地 DLL，确认 `0x102c3180` 在正临界储量时扣除 `+0x1b8` 的完整花费；`0x102c2ef0` 确实存在跨越普通生命上限后再减 `+0x220` 的分支。`0x102c2a40` 另有依赖消息参数的退出处理。字段存在不等于已经验证全部普通受击调用路径；此前“所有伤害耗尽临界都应额外扣血”的推断不足，撤回这一泛化结论。本轮为静态核对，没有原游戏运行时伤害追踪，不声称当前规则与原引擎逐路径完全一致。

按用户明确的玩法要求，Mod 将两种结算分开：普通受击先消耗蓝色临界储量，只有未吸收的伤害进入红血；大招允许任意正储量发动，扣完整一次花费，红血只支付储量缺口。两者均不额外收取固定退出损失。以一次花费为 1：储量 0.001 发动需补 0.999 次花费；恰好 1 则不扣红血；大于 1 则保留余量。空储量仍不能发动普通临界大招，既有肾上腺素分支保持独立。

单位仍采用 Minecraft 适配：一次花费为最大生命的 25%。资源支出走现有服务器生命扣除与致死事件，不经过护甲减伤或制造新的受击事件。蓝条归零本身不再生成额外伤害。

### 花费发生在哪个原生阶段

`DevastatorAction` 开始回调 `0x100b5f60` 在 `0x100b5ff2..6024` 发送使用消息，之后才选靶；`TendrilDevastatorAction`（Critical Pain）开始回调 `0x100b7120` 在 `0x100b7187..71aa` 同样发送消息。花费对应控制轨道的开始：Graveyard active 局部 0，Tendril 局部 0.433333，Pain 局部 0.666667；前面仍需加正式 antic/释放段。刚进入 antic 即扣花费属于提前的调度适配。

## 直接碰撞不是目标控制轨道

下表全部是各 active 节点的局部时间；伤害、半径为源单位。`CollisionCountMax=-1`、`TimeBetweenHits=-1`，变身/武器伤害倍乘开关均关，`FriendlyFire=1`、`DamageType=Crush`、`IgnorePowerTarget=false`。所有起点均为 `Motion_Root`，不是手腕。

| 分支 / FIG offset | 开始–结束 | 半径 | 源伤害 | 原碰撞类别与形状 |
| --- | --- | ---: | ---: | --- |
| Groundspike early / 20756 | 0–0.133333 | 5 | 250 | mask `0x4030`，Health / HitReaction / PedBehaviour；offset `(0,-1,0)`，arc 180° |
| Groundspike late / 21052 | 2.8–3 | 25 | 150 | mask `0xc072`，SimulatedObject / Health / HitReaction / Prop / PedBehaviour / VehicleBehaviour；offset 0，arc 360° |
| 地面 Tendril early / 36344 | 0.433–0.5 | 5 | 100 | mask `0x30`，Health / HitReaction；offset `(0,0,-0.25)`，arc 360° |
| 地面 Tendril late / 36640 | 2.8–3 | 25 | 75 | mask `0xc072`；offset 0，arc 360° |
| 空中 Tendril / 78020 | 0.416667–0.533333 | 5 | 20 | mask `0x4030`；offset `(0,0,-0.25)`，arc 180°，AttackType 为 Bare |
| 地面 Pain living / 4456 | 0.666670–2.5 | 1.5 | 50 | mask `0x30`；ExtendingCapsule，shapecast，TravelVelocity 30 |
| 地面 Pain static / 4752 | 同上 | 1.5 | 50 | mask `0x1`，StaticObject；Sphere，shapecast，TravelVelocity 30 |
| 空中 Pain living / 54708 | 同上 | 1 | 50 | mask `0x30`；ExtendingCapsule，shapecast，TravelVelocity 30，arc 180° |
| 空中 Pain static / 55004 | 同上 | 1 | 50 | mask `0x1`；Sphere，shapecast，TravelVelocity 30，arc 180° |

Pain 两条同刻记录按生物/静态类别分开；不能给同一 Minecraft 生物各算一次 50。它们是向目标方向传播的碰撞形状，不能改说两腕或仅玩家身边的小球。地面 Pain 的 ArcRange 为 0，空中为 180；其它 Grave/Tendril 碰撞不 shapecast、不旅行。Grave 的 AttackType 为 DevastatorGroundSpike，地面 Tendril 为 DevastatorTendril，Pain 为 DevastatorExplosion。

### 原碰撞记录与重复伤害边界

CollisionHit 的 `timeBetweenHits` 属性注册在 `0x1014c5c2`，setter `0x1014b740` 写轨道成员 `+0xf4`；运行时 getter `0x100a8ca0` 读该值。碰撞有效性检查 `0x100a3810` 通过 `IsValidCollisionMessage`（构造 `0x100a2a40`）送入记录检查 `0x100654c0`。若记录已含对应对象且间隔为负，`0x100654fe..6550a` 拒绝，不会据 -1 解释成每 tick 重伤。

该记录受动作开始/结束计数与清理控制（`0x100a4970`、`0x100a3780`、`0x10065470`），不是已经证明的全招全通道永久去重。源中另有 `clearCollisionRecord` 分支。跨轨道、原部件、主目标与直接碰撞的记录共享尚未全部还原；每轨/每目标一次及跨通道分开记账属于可审查的 Minecraft 适配，不能据此声称原作完全没有多路命中。

## 原目标控制窗口与尚未闭环的最终结算

| 主轨道 / FIG offset | 控制开始–结束 | 各目标开始 | 各目标控制时长 | 主伤害参数 |
| --- | --- | --- | --- | ---: |
| Groundspike / 20500 | 0–`-1`，负结束仍未知 | 0.333333–1 | 0.5 | 500；RandomDamage 25 |
| 地面/空中 Tendril / 35932、77248 | 0.433333–3 | 0.666–1.5 | 0.5 | 400；RandomDamage 20 |
| 地面 Pain / 4128 | 0.666667–3 | 0 | 1.333333 | 1250 |
| 空中 Pain / 54208 | 0.666667–3 | 0.333333 | 1.666667 | 1250 |

`DevastatorTrack` 的属性 setter 已核对：`0x101563b0 / 3d0 / 410 / 430` 分别写开始区间 `+0x5c`、停止 `+0x60`、时长 `+0x64`、伤害 `+0x58`。Pain 对应 `0x101566a0 / 6c0 / 6f0` 写开始 `+0x38`、时长 `+0x3c`、伤害 `+0x34`。

`DevastatorAction` 在开始回调一次性筛选目标并创建分支：`0x100b65a8` 调 `0x100b4200`；`DevastatorTargetParams` 构造 `0x100b3a10` 把传入的目标时钟界限写入 `+0x2c/+0x30`、伤害写入 `+0x38`。这些时钟入参在 `0x100b4200` / Pain 的 `0x100b4f10` 中先减子动作启动 offset，不是直接复制原始 AttackTimeBegin / End。Update `0x100b6ef0` 更新相机并检查子动作是否尚存，没有每 tick 再选整批目标并重复发主伤害。

目标 wrapper 的 `+0x10` 由 `0x100b4323..432d` 设为选定 targetBegin；开始区间和 duration 分开传入。外部通用 sequencer 怎样把 owner / 控制轨道 / 目标 wrapper 三者的时钟合成，还没有完整闭环；不能仅因 Tendril 控制轨道从 0.433333 开始就断言目标时间必再加 0.433333，也不能把 Params 的相对调整又等待一次。公开表保留字段原值，运行时映射须明确标作适配并独立验收。

Pain 的开始回调 `0x100b7120` 先处理有效真实锁定目标：`0x100b735a` 创建一条真实目标参数，其余 `TendrilCount-1` 走随机/虚目标分支（`0x100b7370..73b3`）；无有效真实目标时走随机分支（`0x100b73c1..7421`）。TendrilCount 10 不能说成自动选十个真实敌人。

通用 `DevastatorTargetHitAction` 更新 `0x100b5320` 会等待 `UseAttackTimeBegin/End` 条件，随后发送伤害/效果并返回 false，是单次动作而非持续 DPS。`DevastatorTargetExecuteAction` 的 `0x100b5130` 也按目标时钟等待后执行 receiver 一次。然而已读完整 FIG 中没有发现 TargetHit track hash `70290145709413083`，不能把此通用实现直接冒认本次三种主轨道的最终伤害回调。GroundSpike 的原对象行为、receiver 执行、跨部件碰撞和伤害参数进入最终生命结算仍有未解边界。`AttackTimeDuration` 能证明控制窗口，不能单独证明每 tick / 多次 / 恰好一次 500、400、1250。

本轮没有修改 controller 或 renderer，也没有运行 Gradle/游戏验收。资源单元测试及隔离 helper 已补正储量、完整花费、仅缺额代偿、真实致死花费事件；执行结果由统一构建/实机验收另外报告。
