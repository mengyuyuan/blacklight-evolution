# 独立开发版安装

适用：Blacklight: Ascension 0.3.0-standalone-dev.8 · Minecraft 1.21.1 · NeoForge 21.1.244 · Java 21。

这是从 RC59 迁移的独立开发版。黑光现在使用自身的动作、玩家渲染、输入、镜头、目标锁定与服务端同步。Epic Fight、Epic ParCool 和 Weapons of Miracles 已不是前置，首次验证使用仅含黑光与 GeckoLib 的实例。

dev.6 新增五种攻击武器的液态变形，重点展示第三人称的组织推进、波形塑形、附着四股组织流和湿润反光；第一人称保留现有原版手部位置。效果由程序化几何与湿材质重建，原版全部软组织碎片、charge/burst 和 Shader 尚未一比一还原。源依据与具体范围见 [液态武器变形说明](LIQUID_WEAPON_MORPH_SOURCE.md)。

## 安装文件

- `Blacklight-Ascension-MC1.21.1-NeoForge-0.3.0-standalone-dev.8-bundled.jar`：当前含完整动作、装甲、镜头与 HUD 资料的试玩 JAR。
- `geckolib-neoforge-1.21.1-4.9.2.jar`：唯一额外的必需 Mod；其原始 JAR 和完整 MIT 许可一起保留。
- 默认 `assemble` 已携带仓库中的全部 224 个资料文件，无需指定本机路径。

## 安装步骤

1. 退出游戏，在启动器建立 Minecraft 1.21.1 / NeoForge 21.1.244 实例，并使用 Java 21。
2. 把 Blacklight JAR 和单独下载的 GeckoLib JAR 放入实例的 `mods` 文件夹。黑光只保留一个版本。
3. 首次验证先使用独立实例或已有存档副本。不要在 RC59 常用实例中混装旧黑光、新黑光与旧动作前置。
4. 删除旧的 `-Dblacklight.localWeaponMotions=...` 参数，除非正在有意使用完整的外部开发资料目录。该目录会替代包内资料。
5. 检查模组菜单中的版本和控制设置，先确认普通模式、B 黑光模式、五种攻击形态、装甲、盾牌、抓取吞噬和墙面动作。

ParCool 3.4.3.3 的兼容 API 仍可选；黑光原生移动不要求安装。其他版本和额外动作 Mod 尚未作为此独立开发版的兼容组合验收。

## 升级与回退

先备份整个世界和 config，并保留 RC59 旧包。试用独立版时使用副本；回退时一起恢复旧世界、配置和旧包。新的动作附件不表示旧存档中的动作前置内容可以由黑光替代。

客户端与服务端必须使用相同黑光版本和完整动作资料。无 Epic Fight 的专用 GameTestServer 已启动；远程玩家实机、高延迟、重连、多人观战和完整生存流程仍需继续验收。当前结果见 [独立化记录](STANDALONE_MIGRATION.md)。

## 故障排查

| 表现 | 检查 |
| --- | --- |
| 提示缺 GeckoLib | 是否放进正确实例；必须是 NeoForge 1.21.1 的版本 |
| 提示缺 Epic Fight | 是否仍在运行 RC59 旧 JAR，或存在另一个附属模组 |
| Missing Blacklight motion / Incomplete motion profile | 是否仍装了 dev.6 以前的 source-only 包，或设置了不完整的外部动作目录 |
| E 打开背包 | 抓取与背包绑定冲突，检查控制设置 |
| 装甲、墙跑或跑步画面异常 | 保存截图、具体动作、完整文件名及 latest.log，以同一状态复现 |

dev.7 源码已携带完整运行资料，普通构建产物为 bundled。完整清单与构建方式见 [dev.7 说明](BUNDLED_PROFILE_DEV7.md)。
