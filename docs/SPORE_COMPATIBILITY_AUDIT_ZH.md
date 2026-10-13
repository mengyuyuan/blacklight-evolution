# Spore 混装崩溃路径审计（2026-10-13）

## 证据与范围

用户提供的分析截图提到 Forge 1.20.1、Spore 的 `spore:inf_human`，以及 `ConsumeSystem.grappleProtected → ModAttachments.getData → storage` 的 `Missing Blacklight entity capability`。尚未收到原始 crash-report 和实际 JAR 清单；截图中的黑光版本是 dev.3，用户仅能确认大约为 1.20.1。

当前 Forge 源码的 `AttachCapabilitiesEvent<Entity>` 对所有实体挂载数据，并非只支持原版实体。缺失/失效 capability 为什么出现，现有材料不足以归因于 Spore、TACZ 或某个 Mixin。PR #18 处理的是另一条实体死亡后访问数据的路径，不足以覆盖此次报告。

## 本次改动

- 抓取保护、配对碰撞、受伤回调、冻结判定、受击清理和感染状态查询改为读取已有状态；缺失状态按未抓取、无受击动画、无感染起始标记处理，不创建或同步空状态。
- Forge 的缺失/失效 capability 不再让上述被动查询直接抛异常；开始抓取、开始受击控制及感染写入前检查实体状态。真正的状态写入仍使用持久/同步存储，不使用会丢失修改的临时替身。
- 被移除实体只清理会话引用，不再写入或同步失效数据；追踪时跳过不可用的 Forge 数据。
- NeoForge 使用内置 attachment，未确认存在截图中的 Forge 异常；同步落实只读查询和移除保护，不宣称复现了相同崩溃。

感染概率、接触传播、物种规则、建筑/蜂巢生成与组织覆盖代码没有被删除或禁用，也没有添加 Spore 黑名单。运行中的 Forge 实体确实缺失黑光存储时，不会强行创建抓取/感染会话。完整感染适配、特殊实体生命周期、吞噬动画和混装兼容性仍需针对具体第三方版本验证。

## 验证边界

已执行 assemble；用户随后明确要求安装真菌测试，于 2026-10-13 完成实际 Spore 2.2.0j 的隔离 GameTest 服务端测试：Forge 1.20.1 / 47.4.10 通过 6 项，NeoForge 1.21.1 / 21.1.244 通过 5 项。两边使用对应 GeckoLib 4.8.4 / 4.9.2。尚未验证客户端渲染、多人网络、TACZ/TACZ Tweaks 混装或用户原整合包，不能据此宣布“全面兼容所有模组”。

本次测试覆盖：寄染人的真实近战攻击与 AI tick、正常击退、已写入抓取状态时的击退保护、受击控制与实体移除、黑光感染寄染人并转化为保留原物种 ID 的宿主。Forge 额外让受害生物 capability 失效，再调用真实寄染人攻击，确认伤害照常发生且没有复活失效 capability。抓取保护测试直接设置场景状态，不等同于 E 键选取、完整吸收动作或奖励流程验收；退出重进、第三方特殊物种与多人同步仍需另测。

## 本分支构建记录

- 平台：Forge 1.20.1 / Java 17。
- 结果：assemble 成功，含 Forge 重混淆与 224 份资料校验。
- 静态检查：`git diff --check` 通过。
- 测试产物沿用基线开发版本号；此记录仅证明本地构建与列出的服务端用例通过，不代表已发布新版本。PR、合并与发布状态以跨版本跟踪 Issue 为准。
- 首轮 Forge 构建因本机内存及 C/D 盘空间不足失败；限制构建内存并把本次 build 输出转移到 E 盘后完成构建。这不是混装运行验收。

## 混装测试复现

测试源位于 `src/sporeTest/java`，由 `tools/spore-test.init.gradle` 显式加入；不在普通源码集和分发 JAR 中。使用官方 CurseForge Spore 文件：Forge 文件 ID 8342816、NeoForge 文件 ID 8342823。

```powershell
./gradlew.bat runGameTestServer --offline --no-daemon --max-workers=1 -I tools/spore-test.init.gradle -PsporeTestJar=<对应平台测试JAR绝对路径> -PsporeTestDirectory=<全新隔离目录>
```

Forge 的服务端测试可以加 `-x downloadAssets` 跳过不需要的客户端音画资源下载。Forge 开发环境需要用该分支 `build/createMcpToSrg/output.tsrg` 的反向映射，将原始 Spore JAR 转为开发命名，测试调用的仍是真实 Spore 实现；正式试玩安装目录保留官方原始 JAR。NeoForge 直接使用官方 JAR。没有将第三方 JAR 加入仓库、发行 ZIP 或正式依赖。

Forge 日志：`E:/Blacklight-Spore-QA/forge-gametest-r2.log`，结果 `All 6 required tests passed`。
NeoForge 日志：`E:/Blacklight-Spore-QA/neoforge-gametest.log`，结果 `All 5 required tests passed`。

启动日志包含 Spore 包中部分未安装模组联动配方（Create、Farmers Delight）及 Forge 灯具配方的解析错误；本轮服务端继续启动且所有用例通过。未安装额外联动模组、未修改 Spore JAR 的玩法或配方来掩盖这些错误。
