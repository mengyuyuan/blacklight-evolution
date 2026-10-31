# 黑色守望野战军官

`blacklight:blackwatch_officer`；blackwatch；出生role `officer`，默认rank `lieutenant`。

| 项目 | 当前值 |
| --- | --- |
| health | 24.0 |
| armor | 4.0 |
| npc_effective_bullet_damage | 1.6 |
| movement_speed | 0.29 |
| knockback_resistance | 0.1 |
| aim_ticks | 12 |
| cycle_ticks | 64 |
| burst_shot_ages | [12,20,28] |
| burst_end_ticks | 29 |
| registered_width | 0.6 |
| registered_height | 1.95 |
| initial_weapon | military_carbine |
| initial_magazine | 24 |
| initial_reserve | 72 |
| npc_scale | 0.4571428571428572 |

![黑色守望野战军官离线四视图](../assets/兵种/blackwatch_officer.png)

生产几何：`src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java`；纹理：`src/main/resources/assets/blacklight/textures/entity/blackwatch_soldier_layered.png`。正面、背面、脱装、持枪均离线渲染，不是实机截图。

[完整结构数据](../data/soldiers.json)；[军衔/编制/弹药/命令规则](../兵种与编制.md)。实体与资源原文及SHA在[来源清单](../data/source-manifest.json)。
