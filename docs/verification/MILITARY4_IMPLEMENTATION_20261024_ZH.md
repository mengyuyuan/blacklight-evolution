# military.4 实机反馈实施与接续记录

## 目标与现场

截至2026-10-24，当前工作为：修复大型基地空驻军、简要改进第一人称枪械与开火表现、加入有界且按材质区分的枪弹/爆炸方块破坏，并把实际兵种、枪弹、建筑数据和外观归档。

NeoForge目录 `F:/MC建筑/blacklight-evolution`，分支 `feat/neoforge-1.21.1-blackwatch-squads`，基线 `cdf5eb15628594ca82ad9b35bd11d3d34993cfc0`；Forge目录 `F:/MC建筑/blacklight-evolution-forge-1.20.1`，分支 `feat/forge-1.20.1-blackwatch-squads`，基线 `a4f388282af0ad76875716d99ac2534c08c56ee3`。military.1至military.4的变更尚未提交；不推送、不修改PCL及用户存档、不覆盖旧发行档案。

## 已落实的决定

- 驻军使用单独有界额度 `militaryGarrisonLimit`，只计算本维度实际已加载、存活、被基地槽位持有的守军UUID；史莱姆等环境生物不占此额度。默认80，上限200；保持每次最多2名、有限槽位、死亡不补发、卸载不算死亡。状态给出阻塞原因。
- 第一人称采用独立客户端物品姿态，八枪调整比例/朝向并细化几何；观察服务端弹匣与冷却数据驱动短后坐力和简易装填，左右手分别维护。完整双手互换重建基线，保留单手真实射击反馈。弹道原点、实体伤害、库存规则保持原实现。
- 方块伤害区分易碎、木质、土质、砖石、金属和保护类；短时累积裂痕，每维度最多512条、200 tick过期。爆炸半径按弹种限定，默认每次最多处理48个候选。遵守已加载区块、世界边界、玩家建造权限/破坏取消事件、NPC mobGriefing与破坏钩子；默认保护方块实体，感染弹不破坏建筑。
- 用户提供的 `blackwatch_two_hand_sneak.bbmodel` 及作者许可截图归档。借鉴持枪/蹲伏动作，不将5365方块的整模直接塞入现有士兵渲染。截图中的其他聊天内容不扩充为本轮车辆需求。
- 档案保存来源、实值和离线预览，明确区分实机截图、实际资产离线渲染、尚未实装内容。生产源冻结后重新生成并核验。

## 已有实测证据与失败尝试

全部日志位于两端 `.superpowers/military4-qa/`。场景运行使用独立临时世界；Gradle串行执行。

- 驻军RED：Neo准确夹具4场景中2失败，Forge4场景中3失败；无关Mob挤占旧额度和负Y旧0 issued基地不发放已实际复现。首次夹具同tick遍历实体不足，已改为等实际实体进入管理器，不把夹具错误算产品证据。
- 首轮GREEN：两端均12/12定向场景通过（4驻军、8方块破坏），Neo 548、Forge 545项JUnit通过。后续增加边界测试，以上不是最终回归数量。
- 拆换木板RED：真实玩家拆掉中弹木板，再用真实BlockItem放相同木板，下一发错误击碎新木板。`neo-terrain-replacement-red.log` 9场景1失败。
- 跨维度首轮测试没有确认弹体实际tick/命中，PASS已作废。增强夹具强制源与目标区块，断言实际弹体注册、存活射手换维度、命中前移除且未到寿命上限；`neo-terrain-boundaries-accurate-red-run.log` 10场景2失败，明确复现新世界同坐标木板被旧世界火箭破坏，以及上述拆换残留。
- 上一条运行前有一次未完整引用PowerShell `-P...` 参数，Gradle配置期报 `Cannot convert '' to File`，未进入测试；单独保留 `neo-terrain-boundaries-accurate-red.log`，不用它作为产品失败证据。
- 第一人称完整互换RED后，两端各22项直接Java检查通过，客户端类对真实缓存Minecraft API编译通过；详见独立反馈报告。跨tick分裂同步若与真实单手开火完全同数据，仍有不可无歧义区分的视觉边界。

## 2026-10-25 现场接续

用户补充要求维度内军事设施及提供PCL日志。实际日志仍为military.3；两份日志各8个相同ERROR来自账号/Realms与scguns配方/loot，不含黑色守望异常栈。旧0/20基地失败仍记录在日志中，与已确认人口额度问题吻合。详见 [用户日志核查](MILITARY4_USER_LOGS_20261025_ZH.md)。没有修改用户PCL、其它枪械模组或存档。

两端完整回归目前已实际通过：Neo 554项JUnit、Forge551项JUnit，零失败/错误/跳过；两端111/111 GameTest且正常保存退出。Neo完整回归证据为2026-10-24；Forge修正准备条件后于2026-10-25重跑通过。Neo最终10项方块/跨维度夹具又于2026-10-25通过并正常退出。生产替换缓存、same-level射手检查均已经修复；此前“下一步先修复”已经被这些新结果取代。

已保留夹具失败：Forge初次全回归110通过、跨维度准备阶段超时，尚未发射；目标维度只需要block-loaded，不需要entity-ticking，现改为等待真实源弹体起点/命中区块，400tick上限，失败监听幂等恢复票据。Neo一次RED场景结束后卡在ChunkMap.processUnloads；保留线程/堆证据，仅停止已核实的临时PID68476，未碰用户Java25游戏。不能将此栈归因于内存不足。

普通服第一次新夹具0/80失败不是产品驻军故障：复制的QA server.properties为spawn-animals=false，原版shouldDiscardEntity会删除包括持久无敌牛在内的动物；源码已经核对。保留失败世界和日志，新增QA服启用spawn-animals=true，并在夹具早期检查此前提；不改变产品配置或用户设置。新City/World落盘审计正在串行执行。

只读核对确认维度接入已经存在：Neo的world及city、Forge的city均包含原生city_military_bases结构集；Forge没有Neo大世界生成器。大型基地及有条件附属检查站/补给模块随新区块生成。仅QA新增Neo维度选择参数，补测world实际BlacklightWorldGenerator原生Start，不重做已验证的生产接入，不重置旧区块。

## 普通服与资料验证闭环

2026-10-25普通独立服结果已逐项读取JSON核验，不用Gradle退出0替代业务通过：

| 平台/场景 | 实际结果 | 证据目录（各自工程.superpowers下） |
| --- | --- | --- |
| Neo城市原生生成与正常两次启动 | revision4 PASS；80个无关Mob、首批2守军、原80牛UUID/守军UUID/角色脱装/手工指挥/7发箱库存/真实枪击位置全部落盘 | military4-server-neoforge-final |
| Neo大世界原生生成与正常两次启动 | revision4 PASS；实际BlacklightWorldGenerator、blacklight:world，其他同上 | military4-world-server-neoforge |
| Forge城市原生生成与正常两次启动 | revision4 PASS，其他同城市检查 | military4-server-forge-final |
| Neo/Forge的military.3实际QA存档副本升级 | PASS，原军官/脱装/指挥任命/弹量/原UUID保持；新updatedUtc晚于原结果，原M3来源未改 | military4-upgrade-neoforge / military4-upgrade-forge |

新普通服仅通过正常FULL区块请求生成真实原生StructureStart与CityMilitaryPiece，没有管理员base place或显式createStructures冒充自然生成；供弹箱位于起始区块外，均核查真实模板/有限库存。旧升级副本没有revision4字段，不把旧档结果冒充80-Mob新场景。原军方16/BW20名额与生命周期由111场景中两方契约覆盖。

最终资料刷新为final_source_freeze=true，544份源快照、48预览记录、63PNG、31生成文档、195相对链接核验PASS。主代理目视核对步枪、BW军官及大型BW基地实际资产离线图；用户原失败图继续保留。见制作资料人工验收记录。没有新增凭空军械依赖或城市重置步骤。

## 发行与交付依据

两端已使用正常默认UTF-8、无QA init的assemble：Neo成功，Forge包括reobfJarJar/reobfJar成功。package_release的bundled只读核验均通过，253动作及原清单哈希不变，QA/ServerAudit/参考FX没有进入主JAR。Neo核验949个生产class、13940资源；Forge976个生产class、7732资源；文档在最终收集前更新后会重建distributionZip并再核对，实际统计见各端military4-qa的release-verify.log。

本轮本地交付目标为 `F:/MC建筑/military-feedback-preview-20261025` 与同名ZIP，使用当前 `0.3.4-preview.20261024-military.4` 完整主JAR。严格收集器先检查全部实际证据、最终档案和旧三包哈希，再创建全新目录；最终成功以该目录的 `MANIFEST.json`、`SHA256SUMS.txt`、ZIP CRC与外部.zip.sha256回执确认，不用计划文字代替文件。

安装/进入维度/定位基地/射击与破坏的唯一玩家入口是 [简单测试流程](../MILITARY4_PLAYER_TEST_ZH.md)，制作资料入口是 [制作资料](../制作资料/README.md)。代码仍在上述feature分支，全部military.1至.4修改尚未提交；交付sources保存完整覆盖快照及PATCH-BASE，不推送、不修改用户实例。旧三包继续保留。

收集成功后的唯一下一步：玩家在PCL替换完整military.4主JAR，按简单流程核对GL持枪/开火手感、旧0issued基地和模组维度自然基地。服务器/规则验证已完成，不把手工客户端范围提前标为通过。

客户端实际GL画面、手感和按键验收仍需游戏中确认；离线模型投影与服务端验证不替代此项。
