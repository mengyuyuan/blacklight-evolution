# frozen_research_station

| 字段 | 值 |
| --- | --- |
| size | [23,21,23] |
| data_version | 3955 |
| serialized_blocks | 4363 |
| non_air_blocks | 2821 |
| palette_size | 19 |
| embedded_entity_count | 2 |
| runtime_slots | 0 |
| source | src/main/resources/data/blacklight/structure/frozen_research_station.nbt |
| sha256 | 0dc5f32b6582f66384870df8c90de2f41d334de0486482d01a69fa95db7b795a |

![实际NBT离线几何图](../assets/建筑/frozen_research_station.png)

按实际NBT块生成，原版材质近似，不渲染实体/箱中物资，非实机截图。

## 真实箱原始库存与LootTable

| 局部pos | 方块 | NBT原文 |
| --- | --- | --- |
| [3,1,14] | minecraft:chest | {"id":"minecraft:chest","LootTable":"blacklight:chests/research_supplies"} |
| [11,1,19] | minecraft:chest | {"id":"minecraft:chest","LootTable":"blacklight:chests/research_samples"} |

LootTable是延迟战利品表引用，不是已开箱随机结果；Items是模板固定物品。配方/loot原文分别在 [recipes](../data/recipes.json)、[loot_tables](../data/loot_tables.json)，原文件快照保留JSON。

## NBT嵌入实体

| ID | 位置 | NBT完整原文 |
| --- | --- | --- |
| blacklight:infected_host | [4.5,1.0,11.5] | {"id":"blacklight:infected_host","PersistenceRequired":1,"Health":30.0,"Rotation":[0.0,0.0],"BlacklightHost":{"Type":"minecraft:zombie","Family":"ground","Humanoid":1,"Quadruped":0,"Width":0.671999990940094,"Height":2.184000015258789,"Visual":{}}} |
| blacklight:infected_host | [18.5,1.0,6.5] | {"id":"blacklight:infected_host","PersistenceRequired":1,"Health":30.0,"Rotation":[0.0,0.0],"BlacklightHost":{"Type":"minecraft:zombie","Family":"ground","Humanoid":1,"Quadruped":0,"Width":0.671999990940094,"Height":2.184000015258789,"Visual":{}}} |

[解析JSON（含palette/块计数/箱与实体原文）](../data/nbt/frozen_research_station.json)；[规则总览](../建筑与生成.md)。
