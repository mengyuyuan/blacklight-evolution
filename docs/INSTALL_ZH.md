# 独立开发版安装

适用：Blacklight: Ascension 0.3.0-standalone-dev.9 · Minecraft 1.21.1 · NeoForge 21.1.244 · Java 21。

这是从 RC59 迁移的独立开发版。黑光现在使用自身的动作、玩家渲染、输入、镜头、目标锁定与服务端同步。Epic Fight、Epic ParCool 和 Weapons of Miracles 已不是前置，首次验证使用仅含黑光与 GeckoLib 的实例。

dev.6 新增五种攻击武器的液态变形，重点展示第三人称的组织推进、波形塑形、附着四股组织流和湿润反光；第一人称保留现有原版手部位置。效果由程序化几何与湿材质重建，原版全部软组织碎片、charge/burst 和 Shader 尚未一比一还原。源依据与具体范围见 [液态武器变形说明](LIQUID_WEAPON_MORPH_SOURCE.md)。

dev.7 接入临界储备及容量升级、肾上腺激增、三种正式毁灭技及其空中版本、子弹俯冲。临界储备显示为红血条后方延伸的蓝色额外血量。身体动作取自现有本地解包资料；不是将普通招式改名。按键、资源与适配边界见 [源依据](CRITICAL_MASS_SOURCE.md)、[实机检查](CRITICAL_MASS_QA.md) 和包内 README。

dev.9 合并上游的 80 种感染体原版模型、变种／资源包贴图与感染结构组合，以及新的原生模型部件绑定，见 [感染模型说明](STANDALONE_INFECTED_MODELS.md)。同时保留 dev.8 普通持有阶段的镜头相对 W/A/S/D 转向：人物、握持点和受害者同步转身，捕获、吸收、投掷与攀附仍遵守各自朝向约束，见 [持有转向说明](CARRY_STEERING_DEV8.md)。

## 安装文件

- `Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.9-external-profile.jar`：本轮使用精确 244 文件资料构建，包含新增正式毁灭技、俯冲与垂直瞄准动作的完整试玩 JAR。
- `Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.9-bundled.jar`：默认构建，包含仓库 `art/motion-profile/` 的 224 个既有动作、装甲、镜头与 HUD 文件；没有上述新增的 20 份动作，不能当作本轮完整技能包。
- `geckolib-neoforge-1.21.1-4.9.2.jar`：唯一额外的必需 Mod；其原始 JAR 和完整 MIT 许可一起保留。

默认 `assemble` 不需要本机路径，会按仓库清单核对 224 文件。完整新技能需要通过 `-PbundleMotionDirectory=<完整资料目录>` 整套替换为 244 文件；本机完整验证使用 `D:/Blacklight-Standalone/motion-profile-dev7`。不要把两套资料按单个文件混装。

```powershell
.\gradlew.bat clean assemble "-PbundleMotionDirectory=D:/Blacklight-Standalone/motion-profile-dev7" --no-daemon
```

## 安装步骤

1. 退出游戏，在启动器建立 Minecraft 1.21.1 / NeoForge 21.1.244 实例，并使用 Java 21。
2. 把对应范围的 Blacklight JAR 和 GeckoLib JAR 放入实例的 `mods` 文件夹。黑光只保留一个版本；本轮完整新技能选择 244 文件的 `external-profile` 包。
3. 首次验证先使用独立实例或已有存档副本。不要在 RC59 常用实例中混装旧黑光、新黑光与旧动作前置。
4. 删除旧的 `-Dblacklight.localWeaponMotions=...` 参数，除非正在有意使用完整的外部开发资料目录。该目录会替代包内资料。
5. 检查模组菜单中的版本和控制设置，先确认普通模式、B 黑光模式、五种攻击形态、装甲、盾牌、抓取吞噬和墙面动作。

ParCool 3.4.3.3 的兼容 API 仍可选；黑光原生移动不要求安装。其他版本和额外动作 Mod 尚未作为此独立开发版的兼容组合验收。

本页只适用于 NeoForge 1.21.1。Forge 1.20.1 使用 [`forge-1.20.1` 分支](https://github.com/mengyuyuan/blacklight-evolution/tree/forge-1.20.1) 的依赖、Java 版本与安装文档；各版本同步情况见 [多版本规范](MULTIVERSION_WORKFLOW_ZH.md)，贡献工具见 [快捷工具](VERSION_TOOLS_ZH.md)。

## 新技能操作

在感染后按 B 进入黑光模式，U 面板先学临界质量与对应毁灭技。满血吸收的溢出按源 0.55 因子形成主红血条后方的蓝色额外血量，受伤先消耗蓝血；容量升级增加可存次数，每次正式释放只扣一份。

按住组合至满蓄：左键＋右键为地刺墓地，右键＋E 为触须弹幕，左键＋E 为致命痛楚。离地时使用相应空中版本；提前松键接普通招式，不扣临界。E 有绑定冲突时在控制设置调整抓取键。

子弹俯冲先在 U 学滑翔 I，再学子弹俯冲，不要求临界质量。真实滑翔中按住右键蓄势，松开或满蓄释放；实际落地后结算伤害和地形效果。下落超过 6 格才破坏方块，高度增加坑的宽度、深度、数量与硬度能力。基岩、容器和方块实体保留，并遵守已加载区块、交互权限与破坏保护；破坏分批完成，不生成大量方块掉落物。

肾上腺激增提供 1.8 秒保命和一次限时免费毁灭技机会，赠送释放间隔为 22.5 秒；保命窗口用独立黄色文字提示。地刺与组织束按原作参考重新三维建模，红筋有真实体积，原创模型随源码与两类 JAR 打包，不复用原作特效网格或贴图。Minecraft 数值、地形与组织驱动为适配，具体边界见 [源依据](CRITICAL_MASS_SOURCE.md)。

## 升级与回退

先备份整个世界和 config，并保留 RC59 旧包。试用独立版时使用副本；回退时一起恢复旧世界、配置和旧包。新的动作附件不表示旧存档中的动作前置内容可以由黑光替代。

客户端与服务端必须使用相同黑光版本和完整动作资料。无 Epic Fight 的专用 GameTestServer 已启动；远程玩家实机、高延迟、重连、多人观战和完整生存流程仍需继续验收。当前结果见 [独立化记录](STANDALONE_MIGRATION.md)。

## 故障排查

| 表现 | 检查 |
| --- | --- |
| 提示缺 GeckoLib | 是否放进正确实例；必须是 NeoForge 1.21.1 的版本 |
| 提示缺 Epic Fight | 是否仍在运行 RC59 旧 JAR，或存在另一个附属模组 |
| Missing Blacklight motion / Incomplete motion profile | 是否误装旧 source-only 包，或设置了不完整的外部动作目录；完整新增技能需要 244 文件资料 |
| 能启动但新增毁灭技／俯冲动作缺失 | 检查是否使用默认 224 文件 bundled 包，或旧的外部目录覆盖了 244 文件资料 |
| E 打开背包 | 抓取与背包绑定冲突，检查控制设置 |
| 装甲、墙跑或跑步画面异常 | 保存截图、具体动作、完整文件名及 latest.log，以同一状态复现 |

上游 dev.7 的 224 文件仓库清单与默认构建方式见 [资料整合说明](BUNDLED_PROFILE_DEV7.md)；本轮 20 份新增动作和此前 244 文件实机检查见 [源依据](CRITICAL_MASS_SOURCE.md) 与 [实机检查](CRITICAL_MASS_QA.md)。dev.9 合并后的启动和回归另行记录。
