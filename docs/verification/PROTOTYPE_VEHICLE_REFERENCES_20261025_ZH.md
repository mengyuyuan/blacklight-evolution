# 原作地面载具与当前代码核查

任务起始日2026-10-25；原始代码核查截止2026-10-26 00:02，M6 实际目视及原创资产增量记录于2026-10-26 12:55（北京时间）。这是研究、作者源和离线资产证据，不是驾驶、GL 客户端验收或兼容模组1.2.1的证明。

## 当前现场与授权边界

- 当前 M6 Neo工作目录：`F:/MC建筑/.blackwatch-worktrees/neoforge-military6`，分支`feat/neoforge-1.21.1-blackwatch-vehicles`，作者HEAD `996dffdf2d56091cbb01ed9f871bc440ed571a1a`。M5 原现场 `neoforge-military5` / `feat/neoforge-1.21.1-blackwatch-reference-homes` 保留，未由本次美术任务修改。
- 设计起始时的Forge M5工作目录：`F:/MC建筑/.blackwatch-worktrees/forge-military5`，其作者基线由根代理记录为`59f8fb10f2b79473fd81dd65e9f2fea3c4b6bee4`。本次经根代理授权的同源资产目标为`F:/MC建筑/.blackwatch-worktrees/forge-military6`；美术未切分支或改动M5。
- 工作树有前序军事功能和并行 M6 未提交改动，不能把HEAD说成全部功能对应版本。此次美术代理仅写 `art/military-vehicles/`、车辆生成器、车辆几何/挂点资源、两张原创运行时 PNG，以及本文和设计的视觉依据；未改注册、Java 模型或其他代理的规则文件，未运行Gradle。M6 整体实现状态以[根记录](MILITARY6_VEHICLES_20261026_ZH.md)及实际文件为准。
- 用户说的“最新1.2.1”明确指模组自身版本/标签，拉取交给用户。没有fetch、pull、merge或核验该标签，也没有确认该标签实际提交、平台或API；当前开发环境仍是Neo MC1.21.1/Java21和Forge MC1.20.1/Java17。
- 用户明确要求AI车队、玩家驾驶与操作武器，并可像Prototype主角一样抢夺载具。平民及两方敌我政策仍按根代理已记录的待用户决定边界处理。

## 研究方法与实际可访问范围

初始文档研究读取过agent-reach、web-access、research与writing-plans。M6 美术增量重新读取并使用 agent-reach 的搜索/公开网页路由和 web-access：Doctor确认公开网页后端为 Jina，Exa 实际请求仍因`mcp.exa.ai` DNS不可达失败；CDP 前置检查显示无已启用调试的浏览器。本次仅进行公开搜索、静态页面取证与公开 JPG 普通 HTTP 获取，不登录、不操作账号，不尝试绕过 Cloudflare。Supercheats 已403的路径未重试；New Game Network / GodisaGeek 页面403后不继续尝试页面挑战，已有公开直链图可单独正常获取。Agent Reach 更新检查因 GitHub API 限额失败，没有安装或更新软件。

此前官方发行正文可读但原图/视频和浏览器读取失败，导致初版写成“未目视”。M6 阶段已用 `view_image` 实际核对 M5 三张已有缓存，并从公开页面提取原始 JPG 地址，下载至 M6 被 Git 忽略的 `.superpowers/vehicle-reference-cache/` 后逐张查看，共18张。完整源 URL、缓存名、SHA-256、尺寸和逐图观察见[元数据](../../art/military-vehicles/reference-observations.json)。原图不进入正式美术或发布包，也不以原图在答复中冒充模组效果。

## M6 实际目视结果与建模对应

| 实际查看的来源 | 画面确实可见的内容 | 原创几何对应与限制 |
| --- | --- | --- |
| [二代运输卡车实机图库50329](https://www.igcd.net/vehicle.php?id=50329)的前、后斜侧 JPG | 六轮（前单轴、后双轴）、高斜窗驾驶室、分开的两块前挡风窗、低前鼻、开放栏板货厢和上缘栏杆 | `truck`的六个轮组、阶梯式斜窗驾驶室、侧栏板、尾门；篷布和六个后舱座位是本模组改编，不能宣称原作该图有篷布 |
| [同底盘Gentek箱体50328](https://www.igcd.net/vehicle.php?id=50328)、[Blacknet终端50330](https://www.igcd.net/vehicle.php?id=50330)各两图 | 六轮共用底盘与高驾驶室，后部各为设备箱/卷帘门、屏幕终端等特定变体 | 只用于核对共同驾驶室/轮组，未把设备车箱体当作运输车，也未照搬品牌、标记或原图材质 |
| [二代APC实机图库50318](https://www.igcd.net/vehicle.php?id=50318)的前、后斜侧 JPG | 履带、六个可见负重轮、斜前装甲、低炮塔、细炮管、单侧双联发射盒、后舱门及侧裙板 | `apc`的`track_left/right`、六负重轮、阶梯斜甲、`turret_yaw → gun_pitch`、发射盒、后舱门；实际内部布局、比例和口径未由图确认 |
| [二代普通坦克实机图库50317](https://www.igcd.net/vehicle.php?id=50317)的前、后斜侧 JPG | 低宽履带车体、七个可见负重轮、独立宽炮塔、长主炮、上置多管枪、前推铲齿、侧裙板和后部储物 | `tank`的七负重轮、宽炮塔、独立俯仰主炮、上置副武器、简化前推铲和储物块；不把网页现实车型标签写成一手确认事实 |
| [首代官方Steam发行截图](https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/10150/0000008860.1920x1080.jpg?t=1655760765) | 前景是履带APC式车体、斜前甲、小炮塔、可见负重轮；背景另一辆车和战斗环境 | 支持首代装甲车的基本轮廓；缓存名`prototype1-steam-tank.jpg`只是早期文件名，不能据此把前景认作所需主战坦克 |
| [Gamepressure登车/驾驶](https://www.gamepressure.com/prototype2/main-mission-14-enemies-and-allies/z93aa4)及[战斗页](https://www.gamepressure.com/prototype2/blacknet-mission-9-operation-spotted-cat-p-2/zd3acf)的六张图 | 登车图可见顶舱口、低前舱、侧双联发射盒；远景驾驶图可见操作情境；战斗近景前方车辆是APC式车体 | 任务文字“Destroy the tank”并不证明画面前景车是坦克。远景和HUD不用于推算轮数或精确尺寸 |
| [New Game Network公开图](https://www.newgamenetwork.com/app/uploads/2025/10/prototype2_img04_2.jpg) | Heller 举起一整座被拔下的炮塔，下面是被破坏的车体；并非普通整车静止图 | 仅支持破坏交互情境，不当作完整炮塔与底盘相对比例；不从搜索图注声称看到了未出现的正常整车 |

关键清晰图 SHA-256：卡车前/后 `cb8e4b41daa69c856af2997214746677168fd60d5d9c907ade5ce919061e01ca` / `15257e8849b79281408e5a3fa071612aa45a130cc631570453e8c47ab52e51a1`；APC前/后 `31e6509e56e67e29d84b4935c0120f4c5e02aa5edf60975ef08ab84531728a42` / `15e61fff4e4711e8a8705a9ba2e120ba3bfb192c01e30e23468bf92b7fa89a46`；坦克前/后 `5157d90f90ee29d7a202c2017077e637cc1cb4b21862288f8ec306260f10935c` / `4c24b70396fea6d56a04ddea00cb50d3c6e3dd5b7b2090456986dcc5d23ceec2`。完整18项见元数据，记录的是实际获取字节，不能凭哈希宣称网页永久不变。

M5 旧APC登车图与本次公开直链下载字节一致；旧驾驶和NGN缓存与本次下载 SHA 不同，但两版都实际打开且场景构图一致，元数据保留两种 SHA，不伪造为字节一致。

## 原作一手依据与视觉入口

| 来源 | 已确认的内容 | 用途与限制 |
| --- | --- | --- |
| [Activision官方发布：Hardened Steel Vehicle Armor](https://investor.activision.com/news-releases/news-release-details/armor-radical-entertainments-prototyper-2-best-buy)，2012-01-27 | Radical工作室负责人介绍Heller可进入军事载具；列出坦克与APC等Blackwatch威胁 | 可支持“玩家进入/驾驶”和坦克/APC属于原作军用载具；具体强夺过程另需实机核对。没有给出本模组耐久、炮弹量、编制或准确口径 |
| 同官方发布链接的[演示视频](https://www.youtube.com/watch?v=_ERiLXecRk0) | 官方网页确实给出此视频入口 | 该视频本轮直接读取失败，不能声称已经逐帧看完 |
| [《Prototype 2》官方Steam商品页](https://store.steampowered.com/app/115320/Prototype_2/?l=english) | 发行方/开发方信息及军用车辆可参与超能力破坏的游戏介绍 | 原作视觉总入口；商品页动态截图未完整展现，不以此捏造特定车辆细节 |
| [《Prototype》官方Steam截图入口](https://store.steampowered.com/app/10150/Prototype/?l=english)与[发行资源截图URL](https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/10150/0000008860.1920x1080.jpg?t=1655760765) | 已实际查看1280×720发行截图，前景为APC式履带车体 | 用于首代装甲车形态研究，不能凭旧缓存名称作主战坦克；不纳入模组资产 |

为查找原游戏画面，还找到以下转载/实机入口，它们是辅助索引，不替代官方事实，也不用于复制原图材质：

- [Gamepressure《Enemies and Allies》实机攻略](https://www.gamepressure.com/prototype2/main-mission-14-enemies-and-allies/z93aa4)：APC登车和驾驶图已实际查看，限制见上表。其“无限弹药”等原作玩法不是本模组的供应设计。
- [New Game Network二代截图](https://www.newgamenetwork.com/media/5518/ps3/prototype-2-ps3-screenshots/)：原图已实际查看，内容为拔下炮塔的破坏情境；页面403没有尝试绕过。
- [原作军火海报转载](https://www.planetadejuego.com/descubrimos-el-arsenal-que-utilizaremos-en-prototype-2)：检索到带发行标识的步枪/机枪/发射器海报入口，原图未成功读取；不能据自动描述断言本轮已核对所有外形。
- [Blacknet任务实机画面入口](https://www.vgfaq.com/prototype-2-operation-jack-of-all-trades-blacknet-mission-walkthrough/)：用于进一步定位军事卡车画面。当前不足以确定游戏内卡车的现实车型、是否本身可驾驶或车队编制。

目前没有可靠一手材料证明“全部车辆的准确现实型号/口径/装甲数值”“固定车队人数”“卡车可驾驶”和“本模组炮弹量”。设计统一使用军用运输卡车、装甲输送/战斗车、主战坦克这些游戏角色名；具体现实型号不写进已确认事实。卡车玩家驾驶、有限燃料、车库维修和车队任务属于用户要求下的本模组扩充。

## 设计起始时的代码核查（截至2026-10-26 00:02）

以下保留设计起始时读取工作树的结果，未把规划类名当作当时已存在类。M6 现已并行实施，不能据本节历史状态否定本轮实际文件；当前入口见根记录。

| 已有接口/资源 | 实际能力 | 对载具工作的影响 |
| --- | --- | --- |
| `registry/ModEntities.java` | 有两方普通/重装/队长/军官/指挥官、感染体和`military_ordnance`；未找到卡车/APC/坦克注册 | 需新增地面载具实体与客户端渲染；不能说已有空车模型只差AI |
| `firearms/WeaponCatalog.java`、`FirearmsRegistry.java` | 八枪七弹；步枪/卡宾共用rifle_round，机枪及各发射器有各自弹种 | 可复用机枪/火箭弹实物库存；自动炮弹、坦克HE/AP弹需新ID |
| `MilitarySoldier.initializeWeapon()` | 一次性发主武器并给容量×3备弹；军官/指挥官卡宾、重装机枪、其余步枪 | 已有有限弹药，不应每tick重抽武器。增加主/副武器需分弹种储存，避免把90发步枪备弹转换成90枚火箭 |
| `MilitarySoldier.fire()`与`FirearmsCombat.fire(LivingEntity, WeaponDefinition, Vec3, float)` | NPC能够使用当前手持FirearmItem；统一入口以LivingEntity眼位发射 | 不能直接用作独立车辆的真实炮口入口；非子弹弹体当前不沿用该scale伤害乘数 |
| `OrdnanceProjectile.definition()` | 同步Kind枚举，再由WeaponCatalog按kind找一把武器；弹体寿命100tick | 两种同kind载具炮会混成同一武器，需要显式武器ID/独立载具弹体，而非仅改名字 |
| `FirearmBlockDamage` | 材质/容器保护、已加载范围、破坏权限、有限候选、同维度活射手 | 应复用这一权限和有界破坏原则，但不能将玩家驾驶的炮击伪装成NPC来绕过玩家权限 |
| `MilitarySoldier.tacticsAvailable()` | 乘坐载具时现有步枪/巡逻AI不可用 | 可作为真实乘员停止个人枪击的边界，乘员身份/库存仍保留 |
| `MilitarySoldier.validTarget(LivingEntity)`、`BlacklightHunter.beginAttack(..., LivingEntity)`、`CombatTargeting` | NPC目标/猎手攻击/玩家锁定及大量命中查询以LivingEntity为中心 | 独立机械Entity需要明确伤害桥和机械威胁Goal；只加`hurt`不会自动让现AI攻击车辆，也不能把车塞入生物锁定/吞噬链 |
| `ConsumeSystem.eligibleBiomass()`、`request()` | 拒绝骑乘者及载有乘员的生物；玩家自己骑乘时也不能直接开始抓取 | 抢夺/吞噬驾驶员需窄的服务器验证入口与现有Consume session共享排他、完成和清理，不能假装直接request即可 |
| `DisguiseSystem` | 有实际捕获/身份库、目击暴露和`consumed`/`speciesAttack`入口；部分hostileAct方法为private | 夺车曝光接入需明确公开的窄事件入口，不能从车辆模块直接调用private方法或授予伪装者管理员编制权限 |
| `MilitaryOrganization` | 单队最多32，树成员最多256、最多2048单位；已有军衔、手工任命、继承命令 | 车辆不冒充一个士兵UUID加入现有人员表；用独立车队表关联已有人员单位 |
| `CityMilitaryData`及M5 Home | 原生人员槽与当前驻地分开；核心/基底数据在city包内有包访问限制 | 用只读基地门面接入车库，保持原16/20人员槽；新增独立有限车辆槽，不把归队重发 |
| 大型基地NBT/生成器 | 65×18×65军方、65×18×81 BW；7格门、9格主路；13×13露天停车/降落标线 | 当前露天垫不是功能车库；新增附属车库要独立测地形/出入口，不能自动抹平旧基地 |

设计起始核查时没有车辆AI路径、驾驶授权包、炮手席、车辆炮弹库存、车库维修库存或抢车状态机。模组代码使用的“vehicle”还包括原版骑乘通称，不能把这些零散变量当作已有军车系统。本节不替代M6当前实际实现核查。

## 技术一手依据

- [NeoForge 1.21–1.21.1 Payload注册文档](https://docs.neoforged.net/docs/1.21.1/networking/payload/)：本端继续使用RegisterPayloadHandlersEvent、CustomPacketPayload和StreamCodec；车辆输入必须在服务端核验真实座位/控制权，不能信任客户端位置或射速。
- [Forge 1.20.1 SimpleImpl文档](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/)：Forge使用自身SimpleChannel/消息处理与主线程工作排队。协议适配须保留平台差异，不能照搬Neo类。
- 本地已映射Minecraft/加载器源码及现项目实际编译契约是具体Entity/NBT方法签名的最终核对对象；本研究没有编译车辆API，也未读未拉取的模组1.2.1源码。

## 设计交接

[载具设计](../superpowers/specs/2026-10-25-prototype-ground-vehicles-design.md)与[实施计划](../superpowers/plans/2026-10-25-prototype-ground-vehicles.md)将原作参考、用户要求、本模组拟定值和未验证项分开。M6 独立车辆内核和原创资产现已并行实施，根代理继续按阶段验证座位/驾驶/火炮/抢夺/车队/车库；不从初始设计文字恢复旧下一步，也不以离线预览代替游戏资产或驾驶验证。

## 初始文档审阅记录与M6资产增量

- 最初只新增本文、载具设计与实施计划三份Markdown；当时相对链接检查通过，Task 0–9完整。这是初始文档结果，不能当作之后M6资产的验证。
- 独立审阅提出抢夺过程持续接触、AI乘员忙碌限制、长车旋转中间碰撞三项意见；设计及相应RED用例补齐后，审阅者逐项复核关闭。这是文档审阅，不是生产测试。
- M6 美术作者源已落地：`art/military-vehicles/vehicle-authoring.json` → 三个可编辑 `.bbmodel`、两涂装原创PNG、客户端几何和服务端挂点。卡车/APC/坦克为78/138/161个方块，尺寸按设计，主副炮口由实际几何末端校验；生成与坐标契约见[作者说明](../../art/military-vehicles/README.md)。
- 实际执行 `python tools/build_military_vehicles.py --preview` 与 `--check` 通过。离线预览已实际打开；独立坐标反解、UV/PNG、Blockbench层级及双端同源核查证据见[回执](../../art/military-vehicles/validation-receipt.json)。这是离线资源检查，未由美术代理运行Gradle、GameTest、服务器、GL或Blockbench GUI。
- 作者源、生成资源及文档仍未提交；没有提交/推送，也未读取/合并模组1.2.1标签。Forge仅复制本代理拥有的同源美术及挂点，不覆盖其规则配置。
- 三类车的实际目视缺口已补齐。仍未验证的是 MC 客户端多视角、乘员/驾驶姿势、炮塔俯仰/受损状态、模型/炮口和实际碰撞一致性。唯一下一步是由根代理在既定M6实施中消费这些同源资源并做真实客户端验收，不能把离线预览宣称为整套载具完成。
