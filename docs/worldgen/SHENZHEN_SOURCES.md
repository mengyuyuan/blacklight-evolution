# 黑光维度：深圳数据来源和生成约定

## 维度与兼容

正式名称「黑光维度」，稳定 ID `blacklight:world`。深圳是世界地图的首个区域，
数据目录 `data/blacklight/worldmap/shenzhen/`，通过 `worldmap/atlas.json` 注册。
旧 `blacklight:city` 继续保留；新生成器不会重写旧维度或已生成区块。
通行信标和 `/blacklightcity travel` 进入黑光维度，再次使用返回原维度。
`/blacklightcity futian` 从外部进入旧福田维度，供旧存档继续使用。

## 固定地理坐标

- 原点：东经 114.048°、北纬 22.552°，沿用福田样板原点。
- x 向东、z 向南，纬向每度 111319.49079327358 格，经向每度 102811.19191866614 格。
- 经度差归一化至 [-180°, 180°)，全球坐标可以放进原版世界边界。
- 这是在深圳附近约 1 格/米的固定等距圆柱投影，不是全球各纬度都保持 1:1 距离。
- 海平面 y=63，维度高度 1536；市民中心核心保留原有方块，周边 192 米过渡高程。
- 后续区域沿用同一原点和比例，不能随意修改投影，否则新旧区块会错位。

## 数据与校验

OSM 原始文件：
https://download.geofabrik.de/asia/china/guangdong-261014.osm.pbf

- 大小 173757527 bytes，下载日期 2026-10-15。
- SHA-256 `ea808414adad03a2079a281610d801cf1183bbf313e3fa310c8e9b5577eadfad`。
- 使用深圳行政关系 3464353 边界，包含海域和深汕特别合作区。
- © OpenStreetMap contributors / ODbL 1.0。
  https://www.openstreetmap.org/copyright
- 派生地图数据库继续以 ODbL 1.0 提供；JAR 内的 JSON/GZIP 就是可编辑数据库形式。
  不要把这一数据授权与模组代码授权混同。

提取结果：84363 建筑要素、101284 道路要素、1475 铁路线、2223 水面、
2262 水道、2002 林地、3406 公园绿地、1590 铺装地块，以及 world.2 补入的 10740 个城市用地多边形。
这些是 OSM 要素数量，建筑与建筑部件可能重叠，不能称为独立楼栋总数。
原始文件中有 29 个几何构造失败要素，已记录而未伪造补全。

高程：AWS 公共 `elevation-tiles-prod` Terrarium z12，336 个源瓦片。
https://registry.opendata.aws/terrain-tiles/
https://github.com/tilezen/joerd/blob/master/docs/formats.md
https://github.com/tilezen/joerd/blob/master/docs/attribution.md

解码 `R×256+G+B/256−32768`，像素中心双线性插值，再投影为 32 米采样网格。
Mapzen Terrain Tiles；全球 SRTM/GMTED2010 courtesy USGS；ETOPO1 NOAA。
源瓦片哈希清单随构建资料保存。重采样、核心区平滑和方块化由本项目完成。

world.2 生成了 6204 个 1024×1024 米地理分区，压缩数据合计 19126887 bytes（约 18.2 MiB）。
每个分区在 `index.json` 中带 SHA-256。运行时不联网，不要求另装地图。

## 性能与玩法

只生成 Minecraft 请求的区块；最多缓存 16 个地理分区和 64 个栅格化区块。
默认只后台准备出生点附近 49 个区块，每次一个非实体 ticking ticket，服务器繁忙时等待。
全图预生成必须由管理员手动执行；全图约 2540 万个区块，绝不默认提前生成。
完整计划使用惰性索引，不在内存中创建数千万个坐标对象。
任务保存地图指纹，未来扩展其它地区导致分区索引变化时，旧任务自动暂停，管理员重新
开始全图计划即可；已存在区块只会读取，不会因此被重新生成或覆盖。
`/blacklightcity pregen start near|full`、`pause`、`resume`、`status` 操作当前城市维度。

福田核心保留现有两处母巢的孵化口、扩散、摧毁玩法。外围按种子划分正常、冲突、
感染区域；仅在已经 ticking 的街道附近尝试生成居民、黑色守望、感染者。
全维度共享生物上限，最多每五秒尝试一次外围生成。

## 精度边界

行政范围覆盖不等于每栋楼精修。OSM 缺失的建筑不会凭空标成实测建筑；无高度标签时
采用程序化楼层。外墙、窗户、楼板是 Minecraft 风格程序化结构，室内布局不是实景复刻。
高程原始分辨率与 32 米采样不支持逐米精确地貌；海岸低地可能需要后续精修。
world.2 对城市用地、公园和林地优先按陆地处理；城市地块外、地面道路两侧补 24 米的程序化过渡绿地，
它是连接遗漏街道边角的景观处理，不是新增的实测土地边界。显式水面/河道优先于该过渡。
城市用地提取另外记录了 3 个无效 area，并未猜测补全。复现顺序为
`extract_landuse.py` → `build_atlas.py`；使用与首版相同的固定 PBF。

平安金融中心与中国华润大厦新增独立的方块轮廓，位置、高度与占地来自 OSM，
收尖外形参考建筑师公开资料，再进行 Minecraft 风格简化；不是官方建筑模型或逐构件测绘：

- KPF/CTBUH《Designing China's Tallest: DNA of the Ping An Finance Center》：
  https://global.ctbuh.org/resources/papers/download/2232-designing-chinas-tallest-dna-of-the-ping-an-finance-center.pdf
- KPF《中国华润大厦竣工公告》，竹笋轮廓与斜交网格结构：
  https://www.kpf.com/wp-content/uploads/imported-files/20190103_CRHQ-Opening-Announcement.pdf

命名地标的轮廓覆盖同占地内的 OSM 结构分件，避免旧直筒分件把新收尖轮廓重新填满。
地铁隧道暂不生成。全市母巢系统尚未逐区精做，现有母巢完整玩法集中在福田核心。
地图以外目前为海洋占位，后续加入其它区域，不代表世界地图已经复刻完成。

验收须分别记录：数据完整性、服务器生成/保存、客户端实景。服务端测试不能代替视觉验收。
