# 第三方生物感染外形兼容实测（2026-10-27）

本次修复针对 NeoForge 1.21.1 的普通 `EntityModel` 模型读取缺口。不能将“允许感染”当成“器官外形已经适配”。

## 实际结果

客户端：Minecraft 1.21.1 / NeoForge 21.1.244 / GeckoLib 4.9.2。

| 模组与注册 ID | 实际感染并转换 | 修复前附着区域 | 修复后附着区域 | 可读取供体结构快照 |
| --- | --- | ---: | ---: | --- |
| Spore `spore:inf_human`（感染人类） | 是 | 0 | 16 | 是 |
| Spore `spore:leaper`（Leaper） | 是 | 0 | 16 | 是 |
| 暮色森林 `twilightforest:deer` | 是 | 10 | 10 | 是 |
| 暮色森林 `twilightforest:minotaur` | 是 | 7 | 7 | 是 |
| Koopa's Critters `koopascritters:pallas_cat`（GeckoLib） | 是 | 0 | 0 | 否 |
| Koopa's Critters `koopascritters:gentoo_penguin`（GeckoLib） | 是 | 0 | 0 | 否 |

“16 个区域”是附着节点的数量，不代表新做了 16 种器官。牛头人使用既有 Humanoid 读取分支，`nativeRoot=false` 不代表不支持，其区域与结构快照均正常。

## 修复

- 新增只读模型字段读取：识别第三方模型私有及继承的 `ModelPart` 字段，沿实际子节点关系确定根部。
- 按对象身份去重并排除已经属于其他节点的后代，避免把同一条手臂重复挂成多个根。
- 使用无几何的虚拟父节点接入既有组织引擎；不修改第三方原始骨骼、子节点表或绑定姿态。
- 弱引用缓存按模型实例保存，资源重载时清理；原有 Hierarchical、Humanoid 和 Ageable 分支保留。
- 没有新增 Spore 或暮色森林物种白名单。GeckoLib 的 `GeoBone` 不属于本次普通 `ModelPart` 适配，仍需单独实现。

## 验收方法与边界

实际加载的第三方 JAR：`spore_1.21.1_2.2.0j_neo.jar`、暮色森林 4.8.3345、`KoopasCritters_1.21.1Neoforge_V0.5.jar`。Koopa's Critters 来源：https://modrinth.com/mod/koopas-critters 。

在独立测试存档内依次生成六种原始生物，调用正式感染入口并等待真实转换。之后显式设置组织年龄 48000、240000，分别记录成熟、后期外形，再启用行走 AI，使用无敌牛作诱饵记录两个行走阶段。每种五张原生客户端截图（原貌、成熟、后期、行走 A、行走 B），共 30 张；修复前对照也为 30 张。

六种均完成转换，行走时钟均增加；四种普通模型产生附着区域与供体快照。已查看 Spore 两种的附着与行走画面。独立窗口隐藏并释放鼠标，测试后正常保存退出，未操作玩家正在玩的窗口。

这批预设年龄与诱饵测试只证明上述样本的感染转换和模型绑定，不能证明自然生态、六次自主吞噬、所有模组生物或特殊 Boss 机制均已适配，也未验收第三方原有特殊攻击 AI 的保留。

构建 `test jar verifyStandaloneBoundary` 通过，760 项单元测试通过，无失败、错误或跳过。新增三项检查覆盖私有与继承根、后代去重、循环与空模型回退、身份去重和只读结构。当前代码仅修改客户端模型读取，本轮没有重跑旧批次 133 项服务端战斗测试。

与 `late-body.1` 已验收包比较，18922 个运行时资源文件逐字节相同，其中动作、模型和地图保护项 18220 个；只增加两个生产类并修改两个生产类及版本元数据。JAR 增加 4270 字节。

## 本机证据

- 修复前：`E:/Blacklight-Completion-20261027/native-foreign-model-baseline-r3/foreign-model-report.json`
- 修复后：`E:/Blacklight-Completion-20261027/native-foreign-model-fixed-r1/foreign-model-report.json`
- 两目录的 `screenshots/` 内均含原貌及变化后的画面，文件名前缀能对应上表的注册 ID。
- 构建：`E:/Blacklight-Completion-20261027/foreign-models-build-r1.log`
- 资源比较：`E:/Blacklight-Completion-20261027/foreign-models-asset-check.json`
- 本地修复包版本：`0.3.5-preview.20261027-foreign-models.1`
- JAR SHA-256：`9c4ae7b02efde2a720e45095cb529733318840873d720ee1f7af1f82b273c02d`

本次没有发布、推送或创建 PR，也没有移植 Forge 1.20.1。
