# blackwatch_outpost

| 字段 | 值 |
| --- | --- |
| size | [25,9,25] |
| data_version | 3955 |
| serialized_blocks | 5625 |
| non_air_blocks | 1375 |
| palette_size | 21 |
| embedded_entity_count | 5 |
| runtime_slots | 0 |
| source | src/main/resources/data/blacklight/structure/blackwatch_outpost.nbt |
| sha256 | fc6b2eb87373e4e09edbb27d2a15fcabdef45c510cdb5d2bd5ca8c96bd980742 |

![实际NBT离线几何图](../assets/建筑/blackwatch_outpost.png)

按实际NBT块生成，原版材质近似，不渲染实体/箱中物资，非实机截图。

## 真实箱原始库存与LootTable

| 局部pos | 方块 | NBT原文 |
| --- | --- | --- |
| [6,2,20] | minecraft:chest | {"id":"minecraft:chest","LootTable":"blacklight:chests/blackwatch_technology"} |
| [13,1,21] | minecraft:chest | {"id":"minecraft:chest","LootTable":"blacklight:chests/blackwatch_supplies"} |
| [18,1,20] | minecraft:chest | {"id":"minecraft:chest","LootTable":"blacklight:chests/blackwatch_technology"} |

LootTable是延迟战利品表引用，不是已开箱随机结果；Items是模板固定物品。配方/loot原文分别在 [recipes](../data/recipes.json)、[loot_tables](../data/loot_tables.json)，原文件快照保留JSON。

## NBT嵌入实体

| ID | 位置 | NBT完整原文 |
| --- | --- | --- |
| blacklight:blackwatch_soldier | [7.5,1.0,7.5] | {"id":"blacklight:blackwatch_soldier","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"sergeant","MilitaryDeployment":"blackwatch_outpost","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":5.0,"MilitaryDeploymentY":0.0,"MilitaryDeploymentZ":5.0} |
| blacklight:blackwatch_soldier | [17.5,1.0,7.5] | {"id":"blacklight:blackwatch_soldier","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"private","MilitaryDeployment":"blackwatch_outpost","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":-5.0,"MilitaryDeploymentY":0.0,"MilitaryDeploymentZ":5.0} |
| blacklight:blackwatch_soldier | [9.5,1.0,17.5] | {"id":"blacklight:blackwatch_soldier","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"private","MilitaryDeployment":"blackwatch_outpost","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":3.0,"MilitaryDeploymentY":0.0,"MilitaryDeploymentZ":-5.0} |
| blacklight:blackwatch_heavy | [12.5,1.0,19.5] | {"id":"blacklight:blackwatch_heavy","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"corporal","MilitaryDeployment":"blackwatch_outpost","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":0.0,"MilitaryDeploymentY":0.0,"MilitaryDeploymentZ":-7.0} |
| blacklight:blackwatch_soldier | [2.5,7.0,19.5] | {"id":"blacklight:blackwatch_soldier","PersistenceRequired":1,"Rotation":[180.0,0.0],"MilitaryRank":"private","MilitaryDeployment":"blackwatch_outpost","MilitaryDeploymentCapacity":8,"MilitaryDeploymentX":10.0,"MilitaryDeploymentY":-6.0,"MilitaryDeploymentZ":-7.0} |

[解析JSON（含palette/块计数/箱与实体原文）](../data/nbt/blackwatch_outpost.json)；[规则总览](../建筑与生成.md)。
