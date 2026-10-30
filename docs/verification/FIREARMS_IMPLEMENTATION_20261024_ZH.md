# 自研枪械实施与验证（2026-10-24）

## 范围与来源边界

双平台：NeoForge 1.21.1 / Java 21 与 Forge 1.20.1 / Java 17。保留现有 military.1 未提交基线，不提交、推送或修改旧发行包。类别名称与参数为项目原创玩法配置；长步枪、紧凑卡宾、机枪、榴弹、火箭、导弹、温压榴弹、感染榴弹不对应未经核验的真实型号。温压/感染类别依据既定需求，精确外观不声称复制原作。固定炮塔与载具武器不在本次可驾驶载具范围内。

## 操作与物品

独立“军事枪械”创意栏；全部枪械初始空仓。右键开火；潜行右键从库存装填。装填立即转移有限弹药，并使用保存于同一物品的 WeaponReadyAt 禁止装填时开火；切换快捷栏不能绕过。原型模式仅为自研 FirearmItem 开放使用：客户端武器/爪输入路由不再吞枪械按键，服务端生存锁仍阻止方块互动而允许后续物品使用。吞噬控制期间枪械 use 返回失败。没有新增 R 键或客户端伤害网络包，创造模式也不凭空补弹。物品提示显示弹种、容量与装填秒数。

| 枪械 ID | 弹药 ID | 容量 | 装填 ticks |
|---|---|---:|---:|
| military_rifle | rifle_round | 30 | 40 |
| military_carbine | rifle_round | 24 | 32 |
| military_machine_gun | machine_gun_round | 60 | 65 |
| grenade_launcher | grenade_round | 6 | 55 |
| rocket_launcher | rocket_round | 1 | 65 |
| missile_launcher | missile_round | 1 | 75 |
| thermobaric_launcher | thermobaric_round | 1 | 70 |
| infection_launcher | infection_round | 1 | 60 |

命名空间均为 blacklight。每种枪械/弹药均有游戏内配方；弹药战利品表为 blacklight:chests/military_ammunition，可被军事设施调用。独立原创 cuboid 3D 枪械模型具有不同枪管、弹匣、肩射管、瞄具与特殊弹标记，贴图由项目代码生成，未使用外部枪模素材。

## 服务端规则

WeaponDefinition / AmmoState 不依赖 Minecraft。普通枪弹使用服务端射线，先裁切实心墙体，再选择第一实体；友军和创造/旁观玩家可以遮挡且不会受伤。NPC 保留 validTarget、clearShot、小队轮换许可、现有抓取/硬直禁用；普通未感染玩家不会因枪械模块自动成为敌人。保留原 NPC 伤害配置，玩家使用统一武器定义的基础伤害。

榴弹/火箭/导弹/特殊弹使用有主人的 OrdnanceProjectile，持久保存种类与年龄；最大 100 ticks 后清理。一次碰撞仅产生有限半径实体效果，执行墙体与友军/创造玩家保护，不破坏方块、不放火、不产生二次投射物。感染弹调用现有 BlacklightInfectionSystem.infect（现有系统仅感染符合条件的非玩家生物）。这不关闭现有感染系统本身的后续感染玩法。

## NPC、装备与基地接口

普通兵首次初始化长步枪，重装首次初始化机枪；初始弹匣容量与三弹匣备用弹仅发放一次。枪丢失、空仓、每 tick 或补给均不会重发枪栈或补满弹匣。真实弹匣保存在物品 CustomData（NeoForge）/NBT（Forge），备用弹持久保存在士兵数据。缺弹装填消耗备用弹；缺少备用弹即停止开火。

- equipped()/setEquipped(boolean)：旧存档默认 true。false 保存实际主手栈并清空主手，防护/击退抗性回落为 0；true 恢复同一个栈；不复制枪、不重置弹匣。装备状态、隐藏枪栈与备用弹保存。
- requiredAmmoItem()：当前/隐藏枪械需要的弹药 Item。
- ammoDeficit()：备用弹距容量 × 3 的缺口。
- receiveAmmo(int offered)：只接收缺口内非负弹药，返回实际接收数；基地调用者必须只扣除返回数量。
- reserveAmmo()/magazineAmmo()：读取备用弹/弹匣。
- initializeWeapon()：首次新兵或旧存档初始化，卸装时不执行；不会重新填充已初始化士兵。

## 测试证据

修改前先提供有限装填规则测试，空实现 RED：`AssertionError: reload must transfer exactly seven finite rounds`。实现后运行 `tools/check_firearms_rules.ps1`，两端均输出 `FirearmsRulesChecks: 11 checks passed`，编译目标为 Java 17，实际分别由 JDK 21 / 17 运行。测试覆盖空仓、有限转移、装填延迟、早发保护、单发扣弹/冷却、部分装填、满仓不消耗、库存上限、负补给与状态字段往返。

常规 JUnit 测试位于 src/test/java/com/codex/blacklight/firearms/FirearmsRulesTest.java。真实服务端新增 FirearmsGameTest 共八场景，覆盖装备保存/恢复、有限补给、墙体/友军射线、玩家库存装填与物品持久化、爆炸所有者/半径/地形保护、NPC 有限库存、原型模式事件/实际配方注册、普通未感染玩家中立/创造玩家保护。

已完成首轮生产编译与全套单元测试：NeoForge 512 tests / 0 failures / 0 errors；Forge 509 tests / 0 failures / 0 errors。双端六个基础 GameTest 已实际通过。

新增原型模式事件测试先得到 RED：`prototype can route firearm use through survival lock`，对 PrototypeSurvivalLock / ClientWeaponCombat / ClientClawEvents 作自研枪械范围内输入接入后，Forge 七项 GameTest 与完整 509 项 JUnit 再次通过。Forge 最终八场景于本轮实际通过，见 `firearms-forge-eight-green.log`（`All 8 required tests passed`）。NeoForge 对应输入修复后最终重跑待主代理给稳定编译窗口。

构建使用 `GRADLE_USER_HOME=F:/MC建筑/.blackwatch-tools/gradle`，JDK 路径为同根目录 `jdks/eclipse_adoptium-21-amd64-windows.2` 与 `eclipse_adoptium-17-amd64-windows.2`。完整测试命令：

```powershell
./gradlew.bat --offline --no-daemon --no-parallel '-Dorg.gradle.jvmargs=-Xmx4G -Dfile.encoding=GBK' -I tools/firearms-gametest.init.gradle test runGameTestServer '-PgameTestRunDirectory=.superpowers/firearms-gametest-<neoforge或forge>' --console=plain
```

`tools/firearms-gametest.init.gradle` 只增加枪械 GameTest 源集、UTF-8 资源模板处理及（NeoForge）build 内暂存运行根目录，不更改生产构建脚本。首次默认 UTF-8 Gradle 在本机中文缓存路径启动 JUnit worker 失败，GBK 参数文件适配解决；GBK 不能用于资源模板，因此 init 显式 UTF-8。普通最终打包应使用默认 UTF-8，并重新处理资源。

主要失败尝试已保留；另有一次编译发现玩家不能传给仅接收 Mob 的 ImpactControl.stunned，已移除该不适用调用，玩家使用现有 ConsumeControl.ownsMotion 边界，NPC 仍保留 tacticsAvailable 的完整硬直保护。主要日志：Neo `firearms-neoforge-first.log`（SoundEvent holder API 编译错误，已修正）、`firearms-neoforge-green.log`（worker 路径编码）、`firearms-neoforge-integration.log` / `firearms-neoforge-runtime.log`（GBK 元数据造成 FML 启动失败，Gradle 自身错误返回 0，未计为验收通过）、`firearms-neoforge-runtime-utf8.log`（5/6 通过，NPC 健康基线误用 1000；现有 CombatScale 会在生物入场时缩放，改为入场后真实初始健康）。生产行为未为通过该断言而改弱。

GREEN 日志：Forge 额外最终 `firearms-forge-eight-green.log`（8/8）；Neo `firearms-neoforge-runtime-green.log`（6/6）；Forge `firearms-forge-first.log`（完整 JUnit 与6/6）、`firearms-forge-final-green.log`（原型模式修复后完整 JUnit 与7/7）。尚未运行图形客户端实机；不能声称 OpenGL 渲染、第一/第三人称实际持枪显示、物理按键或动画已经实机通过。JSON 模型校验仅证明八套模型结构与纹理引用存在、几何不同且坐标合法。导弹目前为直射碰撞弹体，没有目标锁定或追踪引导功能；未把这一能力列为已经实现。

## 修改所有权与交接

武器实现者仅改 firearms 包、ModItems/ModEntities 注册接线、MilitarySoldier 武器/装备接入、独立武器资源/配方/战利品、FirearmsRulesTest/FirearmsGameTest 和本报告。兵模 Renderer、城市/基地生成、管理命令由主代理维护。新增输入兼容修改范围为 PrototypeSurvivalLock、ClientWeaponCombat、ClientClawEvents，均只识别自研 FirearmItem。未提交改动包含原有 military.1 与本轮其他任务，不能把所有 Git 未提交文件归为本模块。


唯一下一步：主代理独立审阅本报告和 firearms/、MilitarySoldier、三个输入接线文件，将基地/城市/模型集成后执行最终联合构建与客户端实机检查；武器实现者不提交、不推送，也未覆盖旧发行包。

项目现有 Git remote 为 https://github.com/mengyuyuan/blacklight-evolution.git（本轮仅本地读取，未推送）；共享经验仓库为 https://github.com/yfcyy889-ops/AI-Engineering-Lab。
