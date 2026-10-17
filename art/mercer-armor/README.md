# Mercer 战甲可编辑源文件

本项目早期方块战甲底稿的精修版，116 个方块，与人物共用 35 骨骼。美术效果仍待精修，不代表用户最终验收。

`mercer_armor.bbmodel` 可在 Blockbench 编辑；运行导出器读取 `layout.json` 与 `armor.png`。修改 bbmodel 后也需同步布局和 UV 数据。

安装 Python 与 numpy 后，在仓库根目录运行 `python tools/export_mercer_armor.py`，生成运行网格与贴图。现有黑光动作文件不在此重复保存，也未被替换。
