# military.3 军官运行时接入（2026-10-24）

双端增量修改 military.2 现有源码，未改版本/构建脚本、主代理测试、BlackwatchModel/Renderer、美术纹理或 PCL，未触碰 military.1/.2 旧发行包；未提交、未推送。

## 六实体与刷怪蛋

| ModEntities 常量 | blacklight 注册 ID | 默认身份/军衔 | 首次有限武器 |
|---|---|---|---|
| MILITARY_TEAM_LEADER | military_team_leader | TEAM_LEADER / SERGEANT | 步枪30+90 |
| MILITARY_OFFICER | military_officer | OFFICER / LIEUTENANT | 卡宾24+72 |
| MILITARY_COMMANDER | military_commander | COMMANDER / MAJOR | 卡宾24+72 |
| BLACKWATCH_TEAM_LEADER | blackwatch_team_leader | TEAM_LEADER / SERGEANT | 步枪30+90 |
| BLACKWATCH_OFFICER | blackwatch_officer | OFFICER / LIEUTENANT | 卡宾24+72 |
| BLACKWATCH_COMMANDER | blackwatch_commander | COMMANDER / MAJOR | 卡宾24+72 |

刷怪蛋 ID 为各实体 ID 加 `_spawn_egg`，对应 ModItems 常量加 `_SPAWN_EGG`。六实体沿用所属阵营普通兵属性，不按角色/军衔加血或加伤害。已接六属性、刷怪蛋、创意栏、现有 Renderer、中英实体/物品名、六标准刷怪蛋模型。

## API、迁移与转换

- MilitaryRole：TROOPER / TEAM_LEADER / OFFICER / COMMANDER；id 为 trooper/team_leader/officer/commander。
- enum.id()、defaultRank()、parse(String)，另有纯 migrate(MilitaryRank, MilitaryRole) 辅助。
- MilitarySoldier.commandRole(): MilitaryRole；setCommandRole(MilitaryRole)。同步角色字段，持久字符串键固定 `MilitaryRole`。
- RegularSoldier / BlackwatchSoldier 新增 (EntityType, Level, MilitaryRole) 构造，保留原双参。
- 命令 `/blacklight military role <entities> <role>` 沿用管理员权限，先验证全部实体，只补足角色最低军衔，保留原枪栈/隐藏枪栈、弹匣、备用弹、装填时间、阵营与单位；不降已有军衔、不调用武器重新初始化。默认名称按当前阵营/角色解析已有实体翻译键；TROOPER按普通/重装键显示，Entity.getName仍优先显示用户CustomName。

旧档没有/无效角色键时，PRIVATE/CORPORAL→TROOPER、SERGEANT→TEAM_LEADER、LIEUTENANT/CAPTAIN→OFFICER、MAJOR/COLONEL/GENERAL→COMMANDER，再与注册实体默认角色取较高者；迁移不重发武器。合法已存角色与已存军衔保持独立，包括用户显式降低军衔的情况。

自动单位接收新成员后均按实际军衔、UUID稳定平局择优，不再仅最初20ticks；角色转换仅对未手工任命且明确由系统择优的单位重新选任。自动加入增加显式同阵营检查。死亡/失联仍沿用现有合法接替，不增兵。

## 基地与持久手工任命

LargeBaseBlueprint.Guard 新增 String role()，读取原 metadata，并保留四参兼容构造。既有slot0为队长，command岗位为军官，operations岗位为基地指挥官，其余普通/重装不变；metadata仍16/20槽，固定UUID、检查站、归岗、预算、损失与补给逻辑保留。

MilitarySavedData.commanderManual(UUID) / setCommanderManual(UUID,boolean) 保存逐单位 `CommanderManual`。appoint事务成功后置true；新基地创建和入编事务成功后置false；失败事务不写标记，删除单位清理。基地主单位保持非automatic，避免外兵混入；后续高阶岗位只在标记false时按真实军衔择优，不抢用户指定的指挥官。

旧档无 CommanderManual 时按主代理裁定：automatic单位默认false，非automatic单位保守默认true，以保留历史任命。因此旧非automatic基地历史任命不会被新增高阶岗位强抢；新基地明确false，可由队长→军官→指挥官合法提升。手工任命标记跨保存/重启保留。

## 实际验证与风险

主代理已观察 MilitaryRoleTest 缺类的RED（`.superpowers/military3-qa/roles-red.log`），本实现者已读取该日志及两条实际测试。使用缓存JDK21与17，独立 `javac --release 17 -encoding UTF-8` 编译真实Role/Rank与主代理测试，再执行原2条JUnit测试方法及11条迁移/空ID边界，两端均输出：

`MilitaryRole: existing 2 JUnit methods + 11 independent migration/parse checks passed`

日志分别为两端 `build/military3-rules/role-checks-green.log`，临时调用器为 `build/military3-rules/RoleChecks.java`。未修改主代理测试，未运行Gradle、全套JUnit或Minecraft场景。六注册/属性/蛋/Renderer、中英名称、物品JSON和16/20 metadata已作静态核查；这不等同生产编译或客户端实机。

兼容风险已通知主代理：DisguiseIdentity旧白名单只有MilitaryRank，需主代理加入MilitaryRole与仅外观MilitaryEquipped，避免转换角色或角色/军衔分离后的伪装克隆走旧档迁移；本实现者未改该非所属文件。新增身份若迁移缺失角色键会有预期外观变化；不会改变作战属性。全套真实任命、死亡接替、保存及基地岗位断言由主代理验证。

唯一下一步：主代理独立审阅，串行运行双端生产编译、完整JUnit及军官/既有军事GameTest，完成美术接入后打包新的military.3，保留旧包。项目：https://github.com/mengyuyuan/blacklight-evolution.git；共享经验库：https://github.com/yfcyy889-ops/AI-Engineering-Lab。

运行时修改清单：MilitaryRole；MilitarySoldier/RegularSoldier/BlackwatchSoldier；ModEntities/ModItems；ClientClawEvents（仅六注册）；MilitaryCommands；MilitaryOrganizationSystem/MilitarySavedData；CityMilitaryService/LargeBaseBlueprint；中英lang与六种spawn_egg模型。双端生产源已冻结交回主代理，后续运行结果以主代理新日志为准。
