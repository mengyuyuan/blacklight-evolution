# 黑光：进化 — Minecraft 1.20.1 Forge

**Blacklight: Ascension · Forge 1.20.1 独立维护版。** 原作者：末者；本分支维护者：雨夜。模组包含生物武器、抓取吸收、伪装、强化移动、感染生物与组织蜂巢，不要求 Epic Fight、Epic ParCool 或 Weapons of Miracles。

维护版本：**0.3.0-forge-1.20.1-dev.5**，分支：`forge-1.20.1`。已接入上游 dev.7 的动作、镜头、HUD、液态武器变形及感染结构资料。dev.4 同步 NeoForge dev.13 的临界质量、毁灭技、俯冲分层效果、真实破坏和原地蓄力防滑；dev.5 包含红雾和实体删除修复。作者 PR #21 已公开补齐 27 份新技能动作，默认 bundled 构建包含完整 251 个运行资料文件，无需外部动作目录。保留 dev.3 的抓取持有转向与双人姿态插值修复。版本保留 dev 标记，以开发预发布提供；旧 source-only 产物及其缺动作启动失败属于 dev.1 历史。Forge 验收结果见 [移植状态](docs/FORGE_1_20_1_STATUS.md)。

跨版本功能和修复按 [多版本协作规范](docs/MULTIVERSION_WORKFLOW_ZH.md) 单独适配：本分支 PR 的 base 选 `forge-1.20.1`，NeoForge 1.21.1 的 base 选 `main`。各版本进度分别记录，不将整套平台分支互相合并。

开发者可用 [多版本快捷工具](docs/VERSION_TOOLS_ZH.md) 自动准备目标分支、PR 说明和同步清单。

## 目标环境

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| Minecraft Java | 1.20.1 | 游戏版本 |
| Java | 17 | 游戏运行与编译 |
| Forge | 47.4.10 | 加载器 |
| GeckoLib | 4.8.4，Forge 1.20.1 版 | **必装前置模组，需单独安装** |
| ParCool | 1.20.1-3.4.3.3 | 可选兼容 |

**GeckoLib 4.8.4 是必装前置模组，必须选择 Forge 1.20.1 版，并单独放入 `mods`。客户端和服务端都需要安装；完整安装包不内置 GeckoLib。**

Blacklight 内嵌 MixinExtras Forge 0.4.1，由 Forge 自动加载。本维护仓已同步作者 Forge dev.5 和感染实体删除后的 capability 崩溃修复（上游 `2a8c6930d8eb570ef7a3c83c32cedc9135b8315a`），并接入 PR #21 的完整动作资料（`8145022db150253fb152eb0f9cac780a58f40654`）。默认 251 文件包包含新毁灭技、子弹俯冲的主动作、蓄力、恢复与变体，保留 Forge 的装甲成长资料。资源完整不代表玩法全部验收；作者的实机报告属于上游证据，本仓结果单独记录。

- [下载 Forge dev.5 完整安装包](https://github.com/lingweiliang2510579032/blacklight-evolution/releases/tag/v0.3.0-forge-1.20.1-dev.5)（开发预发布）

- [安装与故障排除](docs/INSTALL_ZH.md)
- [构建、测试和运行证据](docs/FORGE_1_20_1_STATUS.md)
- [已知问题](docs/KNOWN_ISSUES_AND_SLOTS_ZH.md)
- [完整资料打包](docs/BUNDLED_PROFILE_DEV7.md) · [资源来源](THIRD_PARTY_ASSETS.md)
- [贡献与版本分支](CONTRIBUTING.md) · [dev.3 抓取修复](docs/FORGE_CARRY_STEERING_DEV3.md)

## 生存开始

以下能力说明来自上游；Forge 分支的完整玩法验收尚未完成。

1. 寻找雪地村庄的制图师，升至学徒，取得冰下研究站地图。已生成交易的旧村民不会自动换表；研究站只在符合条件的新区域生成。
2. 按地图探索研究站，在冷藏箱取得病毒安瓿。附近没有可定位的研究站时，制图师可能仍售原版地图。
3. 未感染时，手持安瓿按住使用键约 2 秒主动觉醒。接触感染体或被安瓿溅射不会强制感染玩家。
4. 按 B 进入黑光模式，通过战斗、抓取吸收和 U 面板推进成长。已感染后，潜行使用安瓿可投掷感染生物。

自然开局的交易、结构可达性与完整成长节奏仍需人工验收；调试解锁不能替代生存流程验收。详见 [研究站与觉醒](docs/RC18-survival-opening.md)。

## 默认操作

| 按键 | 用途 |
| --- | --- |
| B | Minecraft 普通模式 / 黑光模式 |
| Z | 按住打开能力轮盘；轮盘内 Tab 切页 |
| U | 升级与属性面板，查看具体生命等数值 |
| Tab | 游戏中锁定目标 |
| 左键 / 右键 | 当前形态的普通 / 特殊攻击 |
| E | 抓取；持有时再次按下投掷 |
| 持有时右键 / 左键 | 吸收 / 投掷 |
| X | 本体与最近一次成功吸收的可用身份切换 |
| G / H | 已解锁的防御能力 / 感知能力切换 |
| 空格 / 奔跑键 | 跳跃、蓄力及移动能力，取决于状态和升级 |
| F8 | 移动引擎调参；普通试玩建议先保留默认值 |

以控制设置中的实际绑定为准。黑光模式中的 E 抓取可能与原背包键冲突，需要时重新绑定。未感染或普通模式保留 Minecraft 生存交互。

抓取需要目标可吸收、距离和视线有效。弱目标可直接抓取；强目标需削弱至最大生命的 15% 或以下。强弱分界使用**排除本 Mod 五倍生命单位后的最大生命 50**，不是把显示的放大生命数直接与 50 比较。大型目标使用攀附流程；宠物、载客、无敌等限制仍存在。

## 常用指令

修改类指令需管理员权限或单人允许作弊，并由玩家执行。status 可查看自己的状态。

| 指令示例 | 用途 |
| --- | --- |
| `/blacklight status` | 查看感染、模式、生物质、EP 与当前形态 |
| `/blacklight grant_ep 1000` | 给自己追加 1000 进化点；单次范围 1～100000 |
| `/blacklight infect` | 让自己感染，供调试使用 |
| `/blacklight spawn_infected zombie` | 在前方生成普通感染僵尸，需空间可用 |
| `/blacklight spawn_colossus iron_golem` | 生成成熟巨构；也支持 warden、wither、ender_dragon，需足够空地 |
| `/blacklight hive locate` | 分段搜索巨巢；默认半径 32768，可追加 1024～65536 的半径 |
| `/blacklight hive status` / `/blacklight hive cancel` | 查看或取消当前搜索、建造任务 |
| `/blacklight colossus_status` | 查看附近核心的成熟、宿主成长、生物质和暂停原因 |
| `/blacklight hive accelerate 20` | 将附近一座巨巢及其配对吞噬者设为 20 倍；1 恢复正常 |

管理员在准备好的空地可用 `/blacklight hive place` 建造巨巢。它会改造约 **113×113 格**范围，要求区域已加载并通过地形保护检查；取消后已建部分保留，不回滚。执行前备份，详见 [巨巢指令](docs/RC54-disguise-and-hive-commands.md)。搜索结果会区分现存巨巢与尚未加载核实的候选位置。

加速不赠送生物质，不跳过真实吞噬、核心、营养节点和空间条件。**体型巨大不等于进化完成**：完成前摧毁核心会导致成长衰退。看生命条阶段和状态指令确认，见 [成长与锁定](docs/RC56-devourer-health-and-targeting.md)。

## 当前内容与边界

已接入利爪、刀锋、拳套、鞭拳、肌肉增强及空手战斗，抓取吸收与受害者表现，护盾、装甲、两种感知，强化移动，最近吸收身份切换，研究站开局、感染传播、巨型组织蜂巢与实际吞噬成长。默认完整资料包提供当前接入的原作派生动作、镜头与 HUD，包括新毁灭技和子弹俯冲资料；这些实现不代表原游戏全部机制已移植。

感染僵尸保留专属模型；其他普通感染体主要是在原生物外形上叠加破损、啃食和组织变化。四类巨构及其成长形态使用专属模型、基础动作与分段判定，**完整破茧演出和巨构专属战斗连招尚未完成**。

军事科技、可建实验室、黑色守望与超级士兵、城市与载具、记忆网和剧情任务尚未完成。伪装只保存最近的可用身份，使用玩家碰撞体与视点，不获得目标技能。攻击裂纹和碎块主要是视觉反馈，不代表通用地形破坏。多人、性能和完整生存平衡仍待验收。

## 源码构建

使用 Java 17 编译目标、Gradle 8.9 和 ForgeGradle 6.0.54。Windows：

```powershell
./gradlew.bat clean assemble --no-daemon
./gradlew.bat runClient --no-daemon
```

Linux 使用 `./gradlew`。默认从仓库 `art/motion-profile/` 打包完整资料，不需指定作者的本机目录。可用 `-PbundleMotionDirectory=<完整资料目录>` 替换整套资料，产物分类为 `external-profile`；不与仓库资料混合。

默认安装候选：`Blacklight-Ascension-MC1.20.1-Forge-0.3.0-forge-1.20.1-dev.5-bundled.jar`。分发 ZIP 包含同一份 Forge 重混淆后的 Jar-in-Jar 成品、安装文档与资源声明；`bundled-slim.jar` 是中间产物，不用于安装。

`assemble` 验证独立运行边界以及最终成品的 251 文件清单、字节数和 SHA-256。完整单元测试、GameTest、客户端及联机验收分别运行和记录，构建成功不代表已全部验收。`tools/package_release.py --profile bundled` 仅收集本地候选与校验值，不上传或批准发行。

## 来源与许可

[维护 Fork](https://github.com/lingweiliang2510579032/blacklight-evolution/tree/forge-1.20.1) · [原作者项目](https://github.com/mengyuyuan/blacklight-evolution)。原作者上游负责 Minecraft 1.21.1 NeoForge，我们独立维护 1.20.1 Forge；保留作者署名、[GPL 代码许可](LICENSE)和[第三方资源声明](THIRD_PARTY_NOTICES.md)。上游截图与历史验收不作为本分支 Forge 实机证据。
