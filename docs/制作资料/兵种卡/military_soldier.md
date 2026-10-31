# 军方步兵

`blacklight:military_soldier`；military；出生role `trooper`，默认rank `private`。

| 项目 | 当前值 |
| --- | --- |
| health | 20.0 |
| armor | 2.0 |
| npc_effective_bullet_damage | 1.4 |
| movement_speed | 0.29 |
| knockback_resistance | 0.1 |
| aim_ticks | 16 |
| cycle_ticks | 72 |
| burst_shot_ages | [16,24,32] |
| burst_end_ticks | 33 |
| registered_width | 0.6 |
| registered_height | 1.95 |
| initial_weapon | military_rifle |
| initial_magazine | 30 |
| initial_reserve | 90 |
| npc_scale | 0.35 |

![军方步兵离线四视图](../assets/兵种/military_soldier.png)

生产几何：`src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java`；纹理：`src/main/resources/assets/blacklight/textures/entity/military_soldier_layered.png`。正面、背面、脱装、持枪均离线渲染，不是实机截图。

[完整结构数据](../data/soldiers.json)；[军衔/编制/弹药/命令规则](../兵种与编制.md)。实体与资源原文及SHA在[来源清单](../data/source-manifest.json)。
