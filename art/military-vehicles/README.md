# 军用载具作者源

这是一套依据《Prototype》一、二代实机画面重新创作的 MC 方块几何。不是导出的商业模型、贴图、概念图，也不是游戏客户端验收结果。18 张内部参考的公开源地址、完整 SHA-256、实际观察与限制保存在 `reference-observations.json`；原图只留在被 Git 忽略的 `.superpowers/vehicle-reference-cache/`，不会打包进模组。

## 唯一作者源与生成

`vehicle-authoring.json` 是几何、材质色板、骨骼与座位/视口/出车/炮口点的作者源。三个 `.bbmodel` 是可编辑的 Blockbench Bedrock 格式模型，每个内含两张原创涂装贴图；修改派生模型后须把改变反映回 JSON 再生成，目前没有自动反向导入器。

在项目根目录运行：

```powershell
python tools/build_military_vehicles.py --preview
python tools/build_military_vehicles.py --check
```

基础生成与只读检查仅用 Python 标准库；`--preview` 使用 Pillow。脚本不联网、不运行 Gradle 或 Minecraft。生成十个文件：三个 `.bbmodel`、本目录两张 PNG、两个运行时 PNG、客户端 `assets/blacklight/military/vehicle-models.json`、服务端 `data/blacklight/military/vehicle-mounts.json`，以及仅同步物理尺寸的 `data/blacklight/military/vehicles.json`。额外的离线预览是 `previews/vehicles-isometric.png`。

当前卡车 78 个方块、APC 138 个方块、坦克 161 个方块；军方橄榄色和 BW 深灰色使用同一几何与 UV。所有原始 PNG 均由本地色板和确定性的微小像素变化生成，未使用参考图像素。车体长×宽×高分别为卡车 7.5×2.9×3.7、APC 7×3.3×3.3、坦克 8×4×3.4 格；JSON 与预览数组沿用宽/长/高顺序。尺寸已烘焙到几何与挂点，车轮径向保持圆形，武器炮管允许伸出车体。

物品使用 `python tools/build_vehicle_items.py --preview` 生成原创 32px PNG 与 `minecraft:item/generated` 模型；`--check` 只读比较资源、配方和本工具负责的语言条目。当前每端 10 套 active 物品模型/贴图/配方（六车、三弹药、一维修件），另保留 1 套 preserved 燃料罐资源和未来配方。燃料罐注册 ID 和磁盘文件保留，根构建负责排除其配方并取消正常游戏获取；驾驶无需燃油。`previews/inventory-icons.png` 展示真实 32px 与降至 16px 的像素对照，不是游戏截图。六车显示名去掉“空底盘”，持久化 `_chassis` ID 保持兼容。

## 坐标与客户端接口

- 作者坐标：格；X 为车辆右侧、Y 向上、Z 朝车头；原点是底盘水平中心地面。
- 每个作者 `bone.pivot` 与所有锚点 `position` 均为车体局部的绝对静止坐标。`seat_*` 明确是实际乘员 Entity 的脚部／骑乘基点，不是座垫或骨盆，服务端不再添加另一份偏移。正角方向跟随服务端 `mountWorld`：yaw 0 朝 +Z，正90度朝 +X，客户端必须使用同一约定。
- 客户端清单的骨骼 `pivot` 已转换成父骨骼相对坐标并乘 16；`pivotBlocks` 保留作者坐标。`part.box` 是该骨骼相对的 `[x,y,z,w,h,d]`，也乘 16，`part.parent` 是所属骨骼，`uv` 是 2048×2048 图集中的起点。
- 自定义 renderer 直接在地面零点渲染 ModelPart。不要套用 Humanoid 的负 Y/Z 缩放或额外的 24 像素偏移，否则模型、席位与炮口会反向或错层。
- 主武器骨骼链是 `root → body → turret_yaw → gun_pitch`。卡车是六个独立轮组；APC/坦克有 `track_left/right` 和其下的负重轮。舱门/舱盖有独立 `door_*` / `hatch_*` 骨骼。座位和出车点绑定 `body`，主炮口绑定 `gun_pitch`，侧发射架/上置副武器绑定 `turret_yaw`。
- 挂点文件只保存服务端所需的 `turretPivot`、`gunPivot`、`barrelRadius` 与 `anchors`。规则配置引用该资源和底盘键，不再抄一份坐标。主副炮口由实际炮管末端中心验证。
- 每席对应独立的 `camera_<seat.id>` 观察点。卡车使用 BODY 实际眼窗位置；APC/坦克使用 BODY 或随炮塔转动的 `turret_yaw` 光学点，全部位于车体包络内且距原席位眼睛不超过 1.8 格。精确坐标、坐标框架与当前 source SHA 见 `.superpowers/military6-art-revision.md`；实际出膛遮挡由客户端 owner 验证，不能把镜头移出车体透墙。

## 实机形态与改编边界

卡车取六轮、高斜窗驾驶室和长货厢；实机运输型是开放栏板货厢，本模组增加篷布和六个后舱座位。APC 取履带、六个可见负重轮、斜前甲、小炮塔、细炮管、单侧双联发射盒和后舱门。坦克取低宽履带、七个可见负重轮、宽炮塔、长主炮、上置多管枪、推铲齿、侧裙板和后部储物。

尺寸、内舱布局、座位、炮口距离、弹量、军方涂装和统一识别条都是本模组值。没有把图库的现实车型名称、搜索图注、任务目标文字、被拔下的炮塔或远景当成精确整车比例证据。

## 实际验证与待办

`--check` 验证所有十份派生文件字节确定性；生成过程检查骨骼父子顺序、有限坐标、车体边界、8/8/3 座位与实际炮口末端。Neo 的 `tools/test_build_military_vehicle_dimensions.py` 与 `tools/test_build_vehicle_items.py` 共 11 项公开生成器测试，覆盖运行时几何反解、车库静态净空、席位/视口、APC 驾驶潜望端口高于前甲且位于其后方、轮廓、Blockbench 层级/UV/嵌入图集、配方版本、文案及只读过期资源检查；同一组测试也针对 Forge 的实际生成器运行。`validation-receipt.json` 保存本轮结果与哈希，不能沿用旧尺寸回执。

离线预览只证明作者源形状与两涂装可生成。Blockbench GUI 打开、真实游戏多视角、乘员姿势、转塔/俯仰、驾驶视角、受损状态、炮口遮挡和两加载器 GL 渲染仍需根代理逐步验证，不能借用旧 M5 的结果。
