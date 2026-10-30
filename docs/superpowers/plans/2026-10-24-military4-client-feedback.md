# military.4 实机反馈实施计划

> For agentic workers: use superpowers:subagent-driven-development or executing-plans, task-by-task.

Goal: 修复实机空驻军，简要改进持枪/射击，完成可维护制作资料并交付两端。
Architecture: 驻军服务、客户端物品反馈、源数据资料归档互不写入；主代理串行集成。
Tech Stack: NeoForge1.21.1/Java21，Forge1.20.1/Java17，Python/Pillow离线资料。
Spec: ../specs/2026-10-24-military4-client-feedback-design.md

## 约束与分工

- 根主代理：测试执行/整合脚本、版本/build、发行、最终记录；Gradle严格串行。
- 驻军实现者：双端CityMilitaryService、CityConfig、仅src/garrisonTest与必要纯规则；先提交新RED夹具给主代理运行，再改生产源。
- 第一人称实现者：双端独立客户端类/纯反馈规则测试、八枪物品模型/贴图/声音、FirearmsCombat仅视觉音效部分；不改伤害/库存/驻军/共享ClientClawEvents，必要接点先通知主代理。
- 档案实现者：tools/archive_military_catalog.py、docs/制作资料、根入口与Forge指针；先写提取器草稿，生产冻结后刷新数值/图片哈希；不改运行时。
- 用户的PCL、存档、旧交付归档不变；不commit/push，不取消先前档案目标。

## 任务

- [x] 新场景复现80个无关Mob挤占驻军额度；覆盖超平坦负Y、真实玩家tick入口。
- [x] 修复有界驻军额度及状态解释；验证旧未发放基地补齐、死亡/卸载不重复。
- [x] 观察客户端反馈规则RED，改进八枪持枪比例、开火/装填与贴脸粒子；冻结后验证左右手/空仓/切枪。
- [x] 归档数据、配方、图片、截图、模块与来源；核对每个兵种/建筑有明确入口。
- [x] 双端全部回归、普通服、独立审阅、正常发行/253动作核验；完整交付由严格收集器和MANIFEST最终确认。

Review focus: 大量史莱姆但驻军应集结；总驻军额度仍有界；负Y和已有0issued存档；空仓/切枪/重连的视觉状态；JSON数据/预览与最终生产源一致。


