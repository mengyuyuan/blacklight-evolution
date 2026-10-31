# military.4 普通独立服审计夹具

日期：2026-10-24。此记录只说明夹具实现和独立 API 编译；运行时验证由主任务串行执行，不能把本记录当作服务端场景 PASS。

## 影响范围

只修改两端 `src/cityMilitaryTest/java/com/codex/blacklight/city/CityMilitaryServerAudit.java`。不修改生产逻辑、用户 PCL、用户存档、旧交付包或旧审计证据。夹具继续由 `tools/city-military-server.init.gradle` 和 `blacklight.cityMilitaryAudit=true` 显式启用，正常发行仍应排除 `CityMilitaryServerAudit*.class`。

## 新增实际检查

1. 自然基地整片范围按原流程加载并强制保持后，首次启动在基地中央上空按两格间隔放置 80 只真实牛。牛设置 NoAI、持久、无重力和无敌，避免岗位碰撞、下落、实体挤压及寻路干扰；不用这些设置推导驻军业务结论。
2. 沿用至少 40 个服务器 tick 的等待，之后从 `ServerLevel.getAllEntities()` 实际遍历，要求标记牛恰为 80、非军人无关 Mob 至少 80，再调用一次真实 `CityMilitaryService.maintain`，要求原空基地恰好发放 2 个有限岗位。
3. 记录 80 个真实 UUID；正常重启后要求同一组标记 UUID 仍存在并存活，不创建新的牛补数。
4. 在已加载基地中央、地板上方 30 格检查既有空气通道。夹具不会清理原建筑来制造通道；若此处不安全则明确失败。
5. 放置玻璃后调用实际 `FirearmsCombat.fire(..., WeaponCatalog.RIFLE, ...)`，要求一发后为空气；在同位置放置橡木板，要求三发后仍是木板，第四发后为空气。此路径调用生产 hitscan 和方块破坏逻辑，不直接设置空气冒充枪击。
6. 保存破坏位置，正常保存退出后再次启动，要求该位置仍为空气；重复发现基地两次后再次核对，保持原有两名守军 UUID、军官角色/脱装、手动指挥官、7 发箱内弹药和钻石块编辑等检查。

新检查使用未注册到玩家列表的创造模式 FakePlayer 调用生产射线；验证命中、方块规则及落盘，不验证鼠标输入、枪械消耗、第一人称画面或真实玩家传送。

## JSON 与旧版本兼容

首次新建结果继续使用 `schema: 1`，增加 `auditRevision: 4`，便于原军官/驻军审计字段兼容。新版本结果必须具备：

- `ambientFixtureUuids`：80 个原始实际 UUID。
- `ambientFixtureMobsAtFirstMuster: 80`、`unrelatedMobsAtFirstMuster >= 80`。
- `ambientMobsDoNotConsumeGarrisonBudget: true`。
- `actualRifleGlassAndWoodVerified: true`、`structuralImpactMarker`。
- 二次启动得到 `ambientFixtureRestartVerified: true`、`structuralImpactDiskRestartVerified: true` 和 `status: PASS`。

没有 `auditRevision` 的 military.2 / military.3 结果走原有保存验证分支，不伪造新增字段，也不据此声称通过新的 80 Mob / 方块检查。旧审计状态为 FAIL 或未完成时仍拒绝继续覆盖，应保留证据并使用新目录。

## 已执行的检查

两端已用各自实际缓存 Minecraft/加载器 classpath、现有生产 class 独立编译此夹具，未运行 Gradle或启动游戏：

| 平台 | JDK / javac | 结果 | 本地证据 |
| --- | --- | --- | --- |
| NeoForge 1.21.1 | Java 21，`--release 21 -proc:none` | exit 0，有已知过时 API 提示 | `build/military4-server-audit-checks/neo-api-compile.txt` |
| Forge 1.20.1 | Java 17，`--release 17 -proc:none` | exit 0，有既有 ResourceLocation 过时警告 | `build/military4-server-audit-checks/forge-api-compile.txt` |

两端逻辑一致，差异仅为事件注解/事件阶段、FakePlayerFactory 命名空间和 ResourceLocation 构造 API。静态检索未发现直接设置 AIR 或手动创建自然结构的执行代码。

本次冻结 SHA256：

- Neo：`7e3785f132949a6cb7d0d75f3db71503a2653bd2cb96ebeab97de92c4f936e25`。
- Forge：`730552ebcb4bfead1ef55e95180acf5e6d38dcf7d8926886f16786ced2b73bb6`。

## 主任务下一步

先分别准备全新的 `.superpowers/military4-server-neoforge` 和 `.superpowers/military4-server-forge`，沿用原审计的 seed 0、允许结构生成、平坦主世界、`server-ip=127.0.0.1`、`server-port=0`、离线认证、view/simulation 2 和 EULA。不要用 GameTestServer 替代普通服务端。

在对应仓库、配置好对应 JAVA_HOME / GRADLE_USER_HOME 后，严格串行各运行同一命令两次，第一次结果应为 PHASE1_SAVED，第二次必须读实际 JSON 为 PASS：

```powershell
# NeoForge 仓库，Java 21
.\gradlew.bat --offline --no-daemon '-Dorg.gradle.jvmargs=-Xmx4G -Dfile.encoding=GBK' -I tools/city-military-server.init.gradle '-PserverRunDirectory=.superpowers/military4-server-neoforge' runServer

# Forge 仓库，Java 17
.\gradlew.bat --offline --no-daemon '-Dnet.minecraftforge.gradle.check.certs=false' '-Dorg.gradle.jvmargs=-Xmx4G -Dfile.encoding=GBK' -I tools/city-military-server.init.gradle '-PserverRunDirectory=.superpowers/military4-server-forge' runServer
```

迁移验证应把原 military.3 临时服的实际存档、JSON 和必要配置复制到新的 migration 目录，再用新代码启动，保留原对照世界与原证据。旧 JSON 没有新增字段，只证明旧保存状态兼容；新目录的 revision 4 审计单独证明本轮新增行为。Gradle 正常退出不等于审计成功，最终收集器必须检查 JSON 的 PASS 与上述新字段。

接续记录：本文件。项目：https://github.com/mengyuyuan/blacklight-evolution 。共享经验：https://github.com/yfcyy889-ops/AI-Engineering-Lab 。
