# 大型军事基地资产与离线预览核验

日期：2026-10-24（Asia/Shanghai）。范围：大型基地结构、专属场景方块、有限战利品、可复现离线预览。城市选址、驻军持久化和真实服务端放置由主任务另行核验。

## 已制作资产

| 结构 | 尺寸（x/y/z） | 实体列表 | 驻军槽位 | 真实箱子 | 模块 |
| --- | --- | --- | --- | --- | --- |
| `blacklight:large_military_base` | 65×18×65 | 空 | 16 | 13 | 双层指挥、双层营房、独立弹药掩体、军械、医疗、开放补给棚 |
| `blacklight:large_blackwatch_base` | 65×18×81 | 空 | 20 | 14 | 双层指挥、双层营房、独立弹药掩体、军械、双层医疗隔离、双层封控科研 |

两套基地均有三个可攀爬警戒塔、可达屋顶岗、三格室内入口与楼间道路、七格南入口、营房东侧三格宽楼梯、指挥楼通信桅杆和屋顶排风设备。医疗屋顶白十字、科研排气与玻璃采光、弹药掩体顶肋用于区分模块。停机坪仅是场景，没有可驾驶飞行器。

本项目采用原创混凝土、钢网、军事色带、工业机组及封控设施语汇。具体布局、尺寸、床位、岗位、弹药数量和装饰模型均为本项目设计；不将其写成《虐杀原形 2》官方规格，没有提取或分发原游戏模型、贴图或地图。参考边界见 [P2 城市与兵装参考核查](../research/PROTOTYPE2_VISUAL_CITY_REFERENCE_20261024_ZH.md)。

## 路径与运行时契约

- NeoForge 模板：`src/main/resources/data/blacklight/structure/large_military_base.nbt` 与 `large_blackwatch_base.nbt`。
- Forge 对应模板目录为 `data/blacklight/structures/`；模板 DataVersion 分别为 3955 / 3465。
- 统一元数据：`src/main/resources/data/blacklight/military/large_bases.json`，顶层 `schema=1` 与 `bases` 数组；每模板含尺寸、入口、模块、`ammo_chests` 与 `guards`。
- 坐标为模板局部坐标，x 向东、z 向南；y=0 是连续地板，哨位的 y 表示脚底格。军方南门为 `(32,1,64)`，黑色守望为 `(32,1,80)`。
- 两模板东北弹药库均有真实箱子 `(52,1,12)` 与 `(54,1,12)`，使用 `blacklight:chests/base_ammunition`。军械箱使用独立 `base_armory`；医疗与普通补给使用 `base_medical` / `base_supplies`。
- NBT 中 `entities=[]`，不保存或拷贝 NPC UUID。16 / 20 个槽位只是运行时创建驻军的输入，不代表资产文件已创建士兵。
- 结构显式包含整个包围体积内的 air。必须由主任务在确认的新地块或明确管理命令目标放置，不能因旧区块加载而覆盖玩家建筑。

## 场景块：装饰与功能

独立注册类为 `registry/MilitaryFacilityBlocks.java`。主模组入口须调用 `MilitaryFacilityBlocks.register(bus)`，本资产任务没有改写共享入口或 ModBlocks / ModItems。自有创造标签为 `blacklight:military_facilities`，待合并语言键见 `src/main/facility-localization.json`。

| 方块 ID | 实际行为 |
| --- | --- |
| `field_barrier` | 有碰撞的混凝土路障，原创黄色黑条纹 |
| `sandbag_cover` | 有碰撞的分层沙袋掩体 |
| `military_crate` | 纯装饰军箱，无库存、无方块实体 |
| `command_terminal` | 装饰终端，发出等级 4 光照，无 Blacknet 任务或科研自动化 |
| `communications_antenna` | 纯装饰通信天线 |
| `military_searchlight` | 发出等级 12 光照的装饰探照灯，无扫描或追踪 AI |

全部六类拥有独立方块状态、JSON 几何、物品模型、可挖掘掉落与原创 PNG 纹理；支持水平放置、结构旋转与镜像。它们没有继承原版箱子/木桶，也没有引用原版 BlockEntityType。真实库存一律使用原版箱子。

## 有限战利品

每个弹药库箱子分别生成一次七类弹药，随后由原版容器存档，不设置周期刷新。下表是每箱范围；重复执行人工结构放置命令会创建新容器，运行时须避免自动重复放置。

| 弹药 ID | 每箱数量 |
| --- | --- |
| `rifle_round` | 24–48 |
| `machine_gun_round` | 24–48 |
| `grenade_round` | 4–8 |
| `rocket_round` | 1–3 |
| `missile_round` | 1–2 |
| `thermobaric_round` | 1–2 |
| `infection_round` | 1–2 |

军械箱按两次抽取，从已核对的 `WeaponCatalog` 八个现有枪械 ID 中抽取每件一把：军用步枪、卡宾枪、机枪、榴弹、火箭、导弹、温压及感染发射器。数量与参数属于本项目平衡设定。

## 实际执行的核验

生成器 `tools/build_large_military_bases.py` 仅用 Python 标准库，不导入旧生成脚本。默认先输出 `build/large-military-bases` 草稿；`--install` 只安装专属新资产，不会重写旧检查站或旧军营。

本次执行：

```powershell
python tools/build_large_military_bases.py --install
python tools/preview_large_military_bases.py
python tools/preview_large_military_bases.py --verify-only
```

- 生成器按实际方块做三维 BFS，检查南门、每个模块、每个箱子的相邻可站立格、箱盖空间、箱底支撑以及全部驻军槽位。最终军方 4801 个、黑色守望 6789 个脚底位置可达；16 / 20 个哨位全部可达。
- 独立预览读取器重新解压并解析实际 NBT，检查尺寸、包围体积所有位置唯一、地板连续、实体为空、13 / 14 个真实箱子的 NBT 与战利品文件存在、固定弹药箱坐标正确。
- 校验六类专属方块的四向 blockstate、实际模型文件与所有贴图 PNG 可解码。结果写入 `docs/previews/large-military-bases/serialized-assets-validation.json`。
- 已用图像查看工具检查两套底层剖视、军方东南外观以及黑色守望完整四向和完善后的东南外观；补充屋顶设备、弹药顶肋与医疗标识后重新渲染。
- 没有启动 Minecraft、Gradle 或改用户 PCL；编译、接线、真实服务端放置、客户端昼夜画面和 NPC 楼梯寻路仍由主任务负责。静态 BFS 是可通行性证据，不能代替实体实际寻路测试。

## 可直接查看的实际资产预览

- [离线 HTML 预览](../previews/large-military-bases/index.html)：可切换四向、底层剖视、通道箱子平面与四向大图，无外部依赖或联网请求。
- [军方四向 PNG](../previews/large-military-bases/large_military_base-fourviews.png)。
- [黑色守望四向 PNG](../previews/large-military-bases/large_blackwatch_base-fourviews.png)。
- [军方底层剖视](../previews/large-military-bases/large_military_base-cutaway.png) / [黑色守望底层剖视](../previews/large-military-bases/large_blackwatch_base-cutaway.png)。
- [军方通道与箱子](../previews/large-military-bases/large_military_base-floorplan.png) / [黑色守望通道与箱子](../previews/large-military-bases/large_blackwatch_base-floorplan.png)。

上述 PNG 从本次实际 NBT 和专属方块模型/纹理渲染。原版材质采用近似颜色与简化形状，正交遮挡和照明也不是 Minecraft 渲染器；所有文件明确标记为离线资产预览，不称作实机截图。

## 接续状态

两端新资产均已落盘，Forge 已分别按 3465 数据版本与 Forge API 生成；在 Forge 重新读取实际 NBT、核查实际资源并独立渲染了全部预览。两端布局和元数据一致，加载器相关目录 / NBT DataVersion / 注册 API 各自适配。未提交、未推送，未改城市 / 驻军 / 枪械 / 兵模 / 旧结构或旧预览文件。唯一下一步：主任务完成两端注册接线与真实服务器四向放置、弹药箱读取和驻军持久化验收，并将其证据追加到本记录或总验收记录。
