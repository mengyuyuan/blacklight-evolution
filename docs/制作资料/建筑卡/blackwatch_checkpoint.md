# blackwatch_checkpoint

| 字段 | 值 |
| --- | --- |
| size | [17,9,17] |
| data_version | 3955 |
| serialized_blocks | 2601 |
| non_air_blocks | 572 |
| palette_size | 13 |
| embedded_entity_count | 3 |
| runtime_slots | 0 |
| source | src/main/resources/data/blacklight/structure/blackwatch_checkpoint.nbt |
| sha256 | 9de7f3c962ef2aaf25592e00c5f6552a111f327b8fdb646973b8026cb71f611d |

![实际NBT离线几何图](../assets/建筑/blackwatch_checkpoint.png)

按实际NBT块生成，原版材质近似，不渲染实体/箱中物资，非实机截图。

## 真实箱原始库存与LootTable

| 局部pos | 方块 | NBT原文 |
| --- | --- | --- |
| [2,1,13] | minecraft:chest | {"id":"minecraft:chest","LootTable":"blacklight:chests/blackwatch_supplies"} |
| [4,1,13] | minecraft:chest | {"id":"minecraft:chest","LootTable":"blacklight:chests/blackwatch_technology"} |

LootTable是延迟战利品表引用，不是已开箱随机结果；Items是模板固定物品。配方/loot原文分别在 [recipes](../data/recipes.json)、[loot_tables](../data/loot_tables.json)，原文件快照保留JSON。

## NBT嵌入实体

| ID | 位置 | NBT完整原文 |
| --- | --- | --- |
| blacklight:blackwatch_soldier | [3.5,1.0,6.5] | {"id":"blacklight:blackwatch_soldier","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"sergeant","MilitaryDeployment":"blackwatch_checkpoint","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":5.0,"MilitaryDeploymentY":0.0,"MilitaryDeploymentZ":2.0} |
| blacklight:blackwatch_soldier | [13.5,1.0,6.5] | {"id":"blacklight:blackwatch_soldier","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"private","MilitaryDeployment":"blackwatch_checkpoint","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":-5.0,"MilitaryDeploymentY":0.0,"MilitaryDeploymentZ":2.0} |
| blacklight:blackwatch_heavy | [8.5,1.0,12.5] | {"id":"blacklight:blackwatch_heavy","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"corporal","MilitaryDeployment":"blackwatch_checkpoint","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":0.0,"MilitaryDeploymentY":0.0,"MilitaryDeploymentZ":-4.0} |

[解析JSON（含palette/块计数/箱与实体原文）](../data/nbt/blackwatch_checkpoint.json)；[规则总览](../建筑与生成.md)。
