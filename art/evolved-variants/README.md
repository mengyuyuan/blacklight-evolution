# 进化者可编辑造型源

八个 `.bbmodel` 是 Generic Model 静态表面，按运行关节分组；材质全部内嵌，可复制到其他机器继续编辑。源色区映射后的顶点色在编辑文件中保存成微型色板，运行版本仍使用真实顶点色。

这不是一套从 Blockbench 直接导出的动画工程。运行模型位于 `src/main/resources/assets/blacklight/models/evolved_variants`，包含完整索引、双权重驱动与源文件指纹；运行骨架及原采样动作保持既有 67 关节数据，特殊动作另由 `EvolvedVariantPose` 适配。

重建入口：`tools/build_evolved_variants.py`。实际原作表面读取及肩肘适配：`tools/p2_evolved_variant_surfaces.py`。本机研究数据路径由该脚本的 `REFERENCE` 指定，不属于可发布资源。

实际采用的二代表面与来源说明见 `THIRD_PARTY_ASSETS.md` 和 `docs/design/evolved-variants-geometry.json`。角色衣服、靴子、兜帽细节与调色板为本模组制作。此轮美术仍需用户评审，不把技术检查通过等同于用户接受造型。
