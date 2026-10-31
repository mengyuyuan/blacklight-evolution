# Blackwatch Reference Rig Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development or executing-plans, task-by-task.

**Goal:** 将BB人物骨架、皮肤依据、握点与动作曲线实际用于两端士兵/第一人称表现。
**Architecture:** 可复现导出器产生生产profile与参考纹理映射；纯姿态求解器与客户端模型/手部渲染分离。保留现有角色、护具和玩法。
**Tech Stack:** NeoForge1.21.1/Java21；Forge1.20.1/Java17；原生ModelPart/ItemInHandRenderer。
**Spec:** ../specs/2026-10-25-blackwatch-reference-rig-design.md

## Global Constraints

- 实际工作目录：F:/MC建筑/.blackwatch-worktrees/neoforge-military5 与 forge-military5；原military.4目录/JAR/ZIP不改。
- 作者新基线996dffdf/59f8fb10；不提交、推送、发布或运行用户PCL。
- Gradle仅根代理串行运行。实现者先交RED测试，根确认失败后再写生产代码。
- 不改军事关系、伤害、弹药、命令/驻地系统；不导入重枪5349cube。

## Review Focus

共享模型实例跨士兵串姿态；左右手/副手占用；脱装脸与军衔被覆盖；实际开火与全局时钟混淆；原UV变更导致帽具/抓取穿模。

## Task 1：可追溯映射和纯姿态

Files: 新建 tools/export_blackwatch_reference.py、client/model下独立reference/pose类、src/test下对应测试、assets/blacklight/military下映射资源。

- [ ] 解析12组与16人物cube/10贴图/7动画，保留SHA与来源，编写对原曲线/握点关系的失败测试。
- [ ] 根运行RED；实现实际运行时加载与姿态求解，确定手到握点误差可测且在支持范围内连续。
- [ ] 根运行GREEN；导出原始/调整参数对照，明确reload为新增。

## Task 2：NPC分段骨架与纹理

Files: BlackwatchModel.java、BlackwatchRenderer.java（必要时）、两张soldier atlas及可编辑生成器/预览器。

- [ ] 将现有整臂改为上臂/前臂、共同携枪基准，保留Humanoid接口和装备显示语义。
- [ ] 映射实际衣料区域，校准轻量武器/双握点，输出站立、行走、跑步、瞄准、站射、蹲射、装填与脱装多视图。
- [ ] 实际API编译与两端回归，检查隐藏态不残留关节旋转。

## Task 3：第一人称支撑手和衔接

Files: firearms/FirearmViewEvents.java、FirearmViewState.java、对应测试。

- [ ] 写左右主手、空副手、有副手/双枪、装填离握/回握、空仓/菜单不假射的行为测试。
- [ ] 根确认RED后实现第二手的原版皮肤渲染与参考动作衔接，不动服务端发射与库存。
- [ ] 独立审阅、最终生产资源核验；实际GL仅按真实运行记录标记。
