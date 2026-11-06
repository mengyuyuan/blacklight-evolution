# 原生感染兼容与诊断

感染外部生物时保留原实体身份、控制器、Brain、原生目标选择和外部能力数据。黑光只保存自己的感染数据并添加有命名归属的属性修正；治愈只撤销这些修正。不要复制未知能力到替代实体，也不要按每个生物 ID 替换 AI。

## 外观

普通 EntityModel/ModelPart 使用现有组织贴合管线。普通 GeckoLib GeoEntityRenderer 通过只读烘焙骨骼/几何元数据和实际动画后姿态贴合血肉，原网格、UV 和动画仍由原模组绘制。元数据不是原皮肤，不能用它的占位 UV 再绘一遍原表面。框架适配入口是 NativeVisualAdapter；动态资源、换世界和重载会清缓存，嵌套/异常帧会恢复上下文。

当前普通 GeoEntityRenderer 已在 Born in Chaos 的人形、四足、大型和飞行模型上验证。GeoReplacedEntityRenderer、任意自定义网格与 Citadel/其他骨骼框架不能仅凭这次测试宣称全部支持；未识别时保留原模型，不换成静止通用模型。

## 战斗和数值

NativeCombatAccess/DamageMechanismAdapter 是按机制接入生命、吸收生命及特殊储备的入口。NativeHitObservation 区分实际生命损耗、吸收值与护盾储备损耗；观察普通外部攻击不改变它的返回值。当前修仙桥接只按既定 NPC/妖兽机制处理，不改其他玩家的灵盾状态。没有进展的命中要先查免疫、来源、无敌帧和储备，不能用 setHealth 或无条件跳过外部事件兜底。

没有原生攻击的被动动物可得到一个低优先级、可关闭的接触攻击 Goal。已有原生目标与 MOVE/LOOK 控制优先，保留导航、控制器和原目标选择器。原生敌对、远程、具有攻击属性/目标逻辑的生物默认不添加；可按 ID、命名空间或实体类型标签显式扩展。

## 管理员命令（权限 2）

- `/blacklight compat trace <目标选择器或 UUID> 30`：临时观察 30 秒（1–60 秒，最多 32 个目标），正常攻击后检查。
- `/blacklight compat inspect <目标>`：查看原生生命/吸收/已支持储备、感染/保留状态、控制器与最近命中结果。
- `/blacklight compat stop <目标>`：清除临时观察及记录。

观察不发起探针伤害、不遍历/加载世界区块、不强制伤害、不修改外部能力或全服配置。记录是数值快照；被移除的目标不可继续检查。默认关闭全局 nativeHitDiagnostics。

## 配置

世界 `serverconfig/blacklight-ecology-server.toml` 中的 nativeAttackFallback 可控制 enabled、baseDamage、navigationSpeed、targetRadius 和 additionalEntities。全局/默认配置按现有服务端配置系统生成；示例文件不是让玩家覆盖整份自定义配置。preserveExternalEntities 保留外部原生实体，preserveEntities 可额外指定原版种类。

## 可复现测试

`-PnativeFrameworkTest runGameTestServer` 加载原生攻击、诊断和失效对象回归。`-PnativeFrameworkLiveTest runGameTestServer` 是实际 Born in Chaos/Spore 专项，必须另外提供两个对应版本的模组；不存在时测试会失败，不按跳过算成功。隔离测试入口仍须提供正常 GameTest 模板与服务端运行配置，测试类不进入默认发行 JAR。

阶段测试范围：两版本各 8 种真实模组生物的身份/AI/伤害/保存/治愈，两个原生 AI 实际追击命中，以及四种模型在四个阶段的真实动画骨骼捕获与画面检查。测试不是所有第三方模组与所有特殊机制均已兼容的证明。

本适配层的原生感染使用独立年龄/核心成长，完整的原生实体聚合吞噬及跨宿主器官融合流程仍须后续共享状态接口与专项验收；不能把保留原生能力等同于该流程全部完成。


### Respawn animation lifecycle regression

`src/nativeFrameworkVisualTest/java` is an opt-in client probe (`-PnativeFrameworkVisualTest`), excluded from release artifacts. Call `PlayerMotionLifecycleProbe.verify(oldPlayer, newPlayer)` after a real server respawn packet replaces LocalPlayer. It checks animation queries, pose sampling and cleanup against the stale object. Forge additionally invalidates and revives the live player's capability within one synchronous probe, asserting the exact storage object is retained. The installed-runtime fixture exercises both overworld and city deaths, then resumes movement/combat and checks HUD/camera state. The production controller drops only its own clocks; it neither revives capabilities nor replaces player progress.
