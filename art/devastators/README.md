# 与爪子共用的毁灭技地刺模型

dev.9 按用户要求改为与现有爪子地刺统一的 Minecraft 方块结构。六种变体由本仓库已有 `geo/ground_spike.geo.json` 的独立旋转方块生成，使用同一 `claw_biomass.png` 和方块 UV；不读取原作 P3D、私有顶点、UV、三角、贴图或形变数组。

`spikes.model.json` 选择共享几何和变体高度，`tools/author_devastator_spikes.py` 调用 `author_block_spikes.py` 把既有爪子骨点与每个方块转换成 GPU 索引三角模型。OBJ 可在三维软件中编辑；重新生成会覆盖同名 OBJ。配方中旧有机 loft 参数仅保留设计历史，本格式不使用它们。

```text
python tools/author_devastator_spikes.py
```

输出为同目录十二个带 UV / 硬面法线的 OBJ，以及 `src/main/resources/assets/blacklight/models/devastators/spikes.json`。坐标 Y-up，单位为 Minecraft 格；生产模型引用已有模组贴图，未引用原作贴图。

地上单刺最高为 1.8、2.5、3.2 格，簇为 2.0、2.7、3.4 格；根部有地下体积。单刺 144 三角、簇 576 三角，远近层级保留相同低面数轮廓。生成／回收只把完整模型上下平移进出真实地面，不压扁高度或缩小宽度；返回速度与每实例延迟分开。

生成检查覆盖有限坐标、索引和法线。GPU 与 CPU 平移、贴图 UV、真实触碰和画面仍以本轮实机验收为准。
