# Military Homes Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or executing-plans, task-by-task.

**Goal:** 给独立士兵持久归属与巡逻，基地失效后可寻找新驻地或独立作战。
**Architecture:** 军事驻地服务与原生出生槽分离，目标扫描和路径有界，保持原武器/组织状态。基地终端的加载状态驱动健康状态。
**Tech Stack:** NeoForge1.21.1/Java21，Forge1.20.1/Java17，SavedData/Goal/实际容器。
**Spec:** ../specs/2026-10-25-military-homes-design.md

## Global Constraints

- 只改两个military5隔离目录，不改原工作区和mil4包；不提交/推送。
- 不新增平民或改阵营/敌对；军方/BW仍分开绑定，不自行扩充作者设定。
- Gradle仅主代理串行；先交RED测试/测试init，收到主代理逻辑RED后再改生产。
- 16/20初发槽、原生UUID和已确认死亡不再发契约保留；不免费补弹、不远程取箱。

## Review Focus

卸载误报毁灭；原生出处与当前驻地混淆造成重刷/损失丢失；手工命令被家园AI覆盖；跨阵营/跨维度传送或强载；旧档没新字段导致空兵/重复发弹。

## Task 1：基地有效性与数据

Files: city/CityMilitaryData.java、CityMilitaryService.java、LargeBaseBlueprint.java、large_bases元数据/生成器（如有必要）、新纯状态规则及src/homeTest真实测试。

- [ ] 写6终端全缺失/单处仍在/未加载/修复/保存往返的失败测试。
- [ ] 根运行RED；新增状态与兼容读取，暂停失效处新发兵，状态输出解释判据。
- [ ] 根运行GREEN并更新最小场景夹具的真实终端；原有测试的业务断言保留。

## Task 2：士兵归属和有限巡逻

Files: 新city/MilitaryHomeSystem.java（或清晰等价类）、必要Goal/查询命令、src/homeTest。

- [ ] 写实际独立刷兵自动选同阵营基地/无基地野外巡逻、持久保存、真实补给的失败测试。
- [ ] 根确认RED后实现；原出生标签不可替换成新驻地，不碰军事单位/弹药初始化。
- [ ] 检查有命令/被抓取/感染/脱装时不抢移动控制，实体不因普通Monster远离而丢失。

## Task 3：失效转移与验证

- [ ] 真实拆除终端、等待确认、转移目标与移动方向；无新基地可独立战斗；卸载不误转移。
- [ ] 验证原生槽、死亡记账、角色、单位和实际武器/弹量跨保存不变，旧mil4副本可迁移。
- [ ] 双端全回归、普通服落盘、独立审阅与易懂玩家测试流程。
