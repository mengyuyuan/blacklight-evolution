# 黑光维度：全市洛杉矶 Arnis 地图

2026-10-26 更新：默认发布包已改为冻结地理瓦片加程序化建筑，保留市政中心的
详细 Arnis 场景；不再随包携带下文的全市方块档案。道路、建筑轮廓和地形数据
继续使用本页记录的来源。下文全市 Arnis 档案格式和批处理记录作为历史构建
资料保留。当前生成方式、精度与存档边界见 [包体与生成说明](../COMPACT_RESOURCES_ZH.md)。

目标：Minecraft 1.21.1 / NeoForge 21.1.244 / Java 21。洛杉矶加入现有
`blacklight:world`，深圳和旧版 `blacklight:city` 继续可用。

## 进入与返回

- `/blacklightcity losangeles`：市政中心 Grand Park。
- `/blacklightcity losangeles places`：查看分区入口。
- 分区：`downtown`、`hollywood`、`van_nuys`、`lax`、`venice`、`san_pedro`。
  例如 `/blacklightcity losangeles hollywood`。
- `/blacklightcity shenzhen`：深圳福田。
- `/blacklightcity travel` 或通行信标：回到进入黑光维度前的世界。
- `/blacklightcity credits`：地图署名。

切换城市和分区保留原世界返程位置。传送沿用异步区块准备、超时与安全落脚逻辑。
默认信标、未绑定九头蛇和已绑定九头蛇的既有目的地规则继续适用。

## 全市覆盖与生成方式

采用 OSM 洛杉矶市行政关系 **207359**，包括山谷、海滨、机场和港区，保留市界
孔洞与分离地块。范围是洛杉矶市；洛杉矶县和大洛杉矶都会区是更大的区域。
https://www.openstreetmap.org/relation/207359

全市统一运行原生 **Arnis 3.3.0**，开启 `--interior`。使用原始 OSM 建筑轮廓、
高度/层数、道路、地表和设施标签，生成楼层、房间、门、家具与树木，再把方块状态
转换为模组内置资源。缺失高度或内部资料时由 Arnis 推断；这些室内是程序化结果，
不代表真实建筑内部测绘。OSM 没有记录的建筑不能凭此数据自动恢复。

全市分为 **133 批**原生生成任务，每批 4096 × 4096 格，使用同一 One World 坐标框架。
物件输入保留完整 OSM ID、全部标签、节点和多边形成员，跨批物件带 128 格余量。
市界覆盖 **5,344,322 个原生区块**，装入 **5,732 个区域资源**；边界按相交的
16 × 16 原生区块覆盖，因此边缘可能延伸一个区块。完成的资源索引必须包含
`schema=2, complete=true`，部分生成结果不能作为全市包交付。

原生坐标：Web Mercator，原点纬度 34.05575°、经度 -118.243°，Arnis 平均地球半径
**6371000 m**，scale=1。地形海拔使用统一 `min_height_m=0`、
`blocks_per_meter=0.8`、`ground_level=63`，避免本维度 Y=0..1535 高度溢出；
建筑自身仍由 Arnis scale=1 生成。海底落到维度以下的部分由维度地板替代。

游戏保留深圳既有固定投影：原点东经 114.048°、北纬 22.552°，经差归一化至
[-180°,180°)。每个目标方块中心投影回经纬度，再取对应原生 Arnis 方块。
因此洛杉矶东西向比例沿用现有世界坐标；全市共享同一变换，不对分区单独拉伸。

模板格式为 LAR2 区域索引 + 每区块 GZIP/LAC3。按需解码并限制缓存，游戏不会把
整座城市一次放进内存。只读取方块状态；不导入原存档实体、方块实体 NBT、
箱内物品或可执行数据。床、箱子等需要方块实体的家具由生成器创建空的原版实例。

所有地图资源随 JAR 提供；运行游戏无需联网下载地图。只有玩家到达的新区块才
写入存档，不默认预生成全市。已有区块不会被更新包强制重写；旧存档中已探索过的
洛杉矶需在新存档或尚未生成的区域查看更新。地图指纹改变会依既有规则暂停旧预生成
任务，由管理员重新开始。全市地图与全市母巢精修是不同内容，本次没有逐栋母巢精修。

## 来源、时间与署名

主 OSM 数据：Geofabrik Southern California **2026-10-24**，671991463 bytes。
https://download.geofabrik.de/north-america/us/california/socal-261024.osm.pbf

SHA-256：`93373c1762187568c6a6554308760d340c83347c5739131b60d6c7b8b7b65fa1`。
行政关系 XML 于 **2026-10-25** 单独冻结；它与 PBF 属于不同时间点。
原始来源和变换清单见 `docs/worldgen/sources/los_angeles/` 与 `los_angeles_full/`。

- 地图数据 © OpenStreetMap contributors，ODbL 1.0。
  https://www.openstreetmap.org/copyright
  https://opendatacommons.org/licenses/odbl/1-0/
  派生地理数据库继续以 ODbL 1.0 提供；JAR 中的资源与仓库转换器为可编辑数据形式。
  项目代码许可证不替代数据库及第三方素材的许可。
- Arnis 3.3.0：Apache-2.0，https://github.com/louis-e/arnis/releases/tag/v3.3.0 。
  使用原生生成器，没有内嵌 Arnis 可执行文件。
- 地形：Mapzen/AWS Terrain Tiles；SRTM/GMTED2010 courtesy USGS；ETOPO1 NOAA。
  https://registry.opendata.aws/terrain-tiles/
  https://github.com/tilezen/joerd/blob/master/docs/attribution.md
  原生全市生成使用 `--aws-only-elevation`。旧地理图层另外冻结了 77 张 Terrarium 瓦片。
- 地表：ESA WorldCover 2021 v200（10 m），contains modified Copernicus Sentinel data (2021)，
  CC BY 4.0。https://esa-worldcover.org/en
- 气候：Beck et al. (2023) Köppen-Geiger climate classification，CC BY 4.0。
  https://www.gloh2o.org/koppen/
- 生态区：RESOLVE Ecoregions 2017 / Dinerstein et al.，CC BY 4.0。
  https://ecoregions.appspot.com/
- 原生树木模板：paleozoey，https://www.planetminecraft.com/member/paleozoey/ 。
  Arnis 自带模板署名见
  https://github.com/louis-e/arnis/blob/v3.3.0/assets/tree-packs/ATTRIBUTION.md 。
  该署名文件未单列树木美术许可证；不能把程序的 Apache-2.0 自动标为树木作者的独立授权。

CC BY 4.0：https://creativecommons.org/licenses/by/4.0/ 。地理数据经过选择、重投影、
垂直缩放和 Minecraft 方块转换，提供者未对这些修改背书。
Overture、外部 3D、Canopy Height、Mapillary 和标牌内容在本次生成中关闭。

## 原始面积资料异常

预处理逐项复查了 11 个无法组成有效面积的 OSM 关系。没有建筑关系失败。
其中有 3 个关系的成员线与市界相交：1 个灌木地表关系（14277154）和 2 个边界说明
（16741092、17463174）。这些无效面积没有强行补成多边形；对应区域仍由全市 Arnis
地形和 ESA WorldCover 覆盖。详细 OSM ID、原标签和检查结果随来源清单保存。

## 复现

准备 Python（osmium、shapely、numpy、Pillow、requests、nbtlib）、JDK 21、原版
Minecraft 1.21.1 JAR 和 Arnis 3.3.0。原始数据、SQLite、原生世界和工具 class
保存在仓库外的工作盘；不要放在游戏存档目录。脚本不在游戏中运行。

1. 冻结上述 PBF、行政 XML/GeoJSON 和来源 JSON，核对 SHA-256。
2. 按原有 `tools/worldmap/build_losangeles.py --source <source> --output
   <repo>/src/main/resources/data/blacklight/worldmap/los_angeles --phase all`
   构建地理图层，保留 NPC 道路定位等既有用途。
3. `python tools/worldmap/prepare_los_angeles_arnis.py --source <source> --output <work>`
   选择全市原始物件，输出完整依赖的原生输入与 `inputs-index.json`。
4. `python tools/worldmap/create_los_angeles_coverage.py --work <work>` 生成市界区块位图。
5. `javac -d <classes> tools/worldmap/ArnisArchivePacker.java`。
6. 先用相同 Arnis 版本生成一个很小的空白/测试区域，取得有效 `level.dat` 骨架。
   然后运行 `python tools/worldmap/run_los_angeles_arnis.py --work <work> --repo <repo>
   --arnis <arnis-exe> --java-home <jdk21> --vanilla-jar <minecraft-1.21.1.jar>
   --skeleton <level.dat> --tool-classes <classes>`。
   如需网络代理，由环境变量设置；生成器的地形、地表和生态资料需要网络及其缓存。
7. 工具检查每个所需区块、列高度、方块名、维度高度和区域覆盖，完成全部任务后才写
   全市索引与图层指纹。`jobs-v2/*/status.json` 记录输入/工具哈希和检查点；若手动修改
   输出，应删除对应 packedComplete 检查点并重新转换，不能保留旧成功标记。
8. 运行 `python tools/worldmap/verify_los_angeles_arnis.py --data
   <repo>/src/main/resources/data/blacklight/city/los_angeles_full` 复核全部区域和覆盖。
   `gradlew.bat assemble --no-daemon`，然后
   `python tools/package_release.py --profile bundled --verify-only`。
   大包启用 ZIP64，源码快照和校验使用有界流式读取。


构建目录优先用同盘硬链接复用不可变的 `.lar` 区域文件，文件系统不支持时使用字节复制。
`ArnisArchivePacker` 重新生成时通过临时文件替换路径；维护模板时也应使用这一方式。
JAR 内包含区域文件的实际字节，安装包不依赖仓库或构建目录中的链接。

离线完整性和编译打包检查不等于游戏内或多人联机验收。
