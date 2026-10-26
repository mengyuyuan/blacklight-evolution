# 黑光病毒安瓿：原作参考与资产计划

目的：替换原来的宽底药水瓶。Minecraft Java 1.21.1 的真实三维物品模型；以识别原作容器为优先，保留像素材质与常规游戏光照。

## 实际参考

本地《虐杀原形 1》`Prototype/movies/NIS_4-5_Gentek4_48fr.bik` 的 0.7 秒、
`NIS_7_3_FMV.bik` 的 3.2 秒。前者可见握持的细长容器，后者可见落地破损的金属护架及红色内容物。
本地只读截图：`E:/Blacklight-Completion-20261027/reference/`。
过场存在模糊和破损：不宣称逐字复刻不可辨识的标识，也不宣称液面和零件尺寸是原作精确尺寸。
运行资产中的几何和像素均为重新制作；不打包过场、提取网格或原作贴图。

## 制作约束

| 项目 | 选择 |
|---|---|
| 平台 | 桌面 Java 1.21.1；物品、掉落物、投掷实体 |
| 设备/数量 | 普通桌面显卡；背包图标、单个手持、数个同时投掷 |
| 光照 | Vanilla diffuse / cutout；不依赖 PBR、全亮或光影包 |
| 特殊材质 | 玻璃的实际 alpha 孔隙；不叠加半透明混合层 |
| 预算 | 不超过 44 元素、320 三角面；实际 42 元素、92 四边面、184 三角面 |
| UV | 单张 128×128 atlas；约 4 px/模型单位，短边按整像素取整；重复护架共享 UV |
| 几何 | 八面细管、上下金属封头、四根保护轨、可见液面；无独立螺钉和装饰碎块 |
| 运动 | 刚性物品显示变换；投掷时按实际速度定向并绕长轴轻转 |

`blacklight_vial.bbmodel` 是可编辑源；`blacklight_vial.png` 为同一张自制贴图。
`build-report.json` 保存实际面数和每块 UV 的有效密度。
`tools/build_virus_vial_model.py` 可确定性重建源文件、atlas 和游戏 JSON；Blockbench 源必须为省略的面明确写入 `texture: null`。
运行时物品 ID 仍为 `blacklight:blacklight_sample`；投掷、破碎感染和存档逻辑不因换模型而更名。

## 验收记录

- Blockbench 原生 Java Block/Item 导入后显示 42 元素，完整轮廓和 atlas 可见。
- NeoForge 1.21.1 原生客户端完成第一人称、第三人称、GUI、掉落物和实际飞行投掷物检查。
- 第三人称原本沿手掌横躺，已补偿原版手持层的旋转，最后一轮截图中为正常竖握。
- 当前单张贴图 1525 字节；新增模型不依赖光影包。实际记录：`E:/Blacklight-Completion-20261027/native-organs-vial-r3/`。
