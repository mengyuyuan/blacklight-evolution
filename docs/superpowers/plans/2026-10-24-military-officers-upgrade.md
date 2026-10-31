# 军官增强实施计划

Spec: ../specs/2026-10-24-military-officers-design.md

当前基线为已验证、已打包的military.2，源码仍在两端原feature分支且未提交。用户最新追加是当前目标；不退回旧的等待用户测试步骤。

- [x] 先实现有意义的角色/任命/持久化测试并观察RED，记录稳定接口。
- [x] 双端角色、六实体/刷怪蛋、角色转换、有限军官武器、自动任命与基地既有岗位接入。
- [x] 双端指挥人员分层外观、独立UV和真实资产预览，保留普通兵和脱装状态。
- [x] 新增真实场景与全部原军事回归、独立审阅及双端普通服重启/旧档迁移。
- [x] 双端正常构建、发行核验与独立交付。
- [x] 独立生成military.3交付与完整JUnit/XML/原生服务器证据，保护military.1/.2，更新最新记录。

Ruling: 用户已直接要求新增军官且曾授权细节由实现者决定；沿用完整实施授权，不重复询问审批。角色与军衔分离、军衔不改战斗数值的现行规则优先。

文件所有权拟定：运行时实现者拥有双端角色/实体/注册/命令/编制/基地接入；美术实现者仅拥有双端BlackwatchModel/Renderer、art/military2工具/新贴图/预览；主代理拥有测试、文档、集成验证、版本与交付。共享接口为MilitarySoldier.commandRole() -> MilitaryRole、setCommandRole(MilitaryRole)，enum常量如设计；编译和Gradle始终串行，不自动提交。

2026-10-24 08:02 本地：Neo已525 JUnit/97场景全部通过；独立审阅P2掉落表经真实RED修复。Forge首轮96/97，唯一失败为测试使用原版SpawnEggItem.byId，Forge源码明确要求ForgeSpawnEggItem.fromEntityType；已修正测试且加getType(null)实际类型断言，重跑中。普通服重启与发行仍待执行。

最终：两端完整验证、发行核验、独立交付已完成；当前回执与唯一下一步见docs/verification/MILITARY3_IMPLEMENTATION_20261024_ZH.md最新段落。先前进行中条目为历史过程，不恢复旧下一步。
