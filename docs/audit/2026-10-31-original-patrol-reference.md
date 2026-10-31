# 一代原作巡逻资源核对（2026-10-31）

本次直接读取本地一代解包，未使用视频或玩家描述代替文件证据。只读 RZ/zlib 解压和 Lua 5.1 字节码解析；没有执行原作脚本，也没有把原作脚本放入模组。

## 直接证据

- `art/startup_fig.p3d.rz` 的 282 个嵌入 Lua 块均解析成功。`path_SetPath`（解压偏移 3142885，四参数）会验证目标，清空旧巡逻路径，从具名定位点表添加巡逻点，或设置已有路径对象；随后调用 `ai_SetPatrolPathClosed` 与 `ai_SetPatrolSpeed`。这证明有具名路点、闭环和速度设置，不证明 C++ 寻路内部算法。
- 同文件 `gsys_RefreshGlobalSystemsState`（3163816）分别调用环境管理、`ai_patrol_Enable`、`ai_EnableStrikeTeams`。`fre_DisableMidLevelSystems`（3321148）保存巡逻/打击队状态，并调用 `ai_patrol_DisableAndDestroy`；恢复函数（3321639）恢复两者状态。常驻巡逻与打击队存在独立开关。
- `art/locations/manhattan/manhattan_fig.p3d.rz` 的 122 个嵌入 Lua 块均解析成功。`meta_ConfigurePatrols`（30879）从阶段的 patrols 表读取 `spawnWait`、`playerBias`、`defeatDelay`、`maxPatrols`、`maxPatrolsStreet`、`maxPatrolsAir`，传给对应原生接口；按 `streetPatrols` 和 `airPatrols` 中的 weight 创建地面/空中编队模板，并把模板内对象及高度加入编队。
- `meta_GetPatrolPackageList`（30438）识别直升机、武装直升机、APC、坦克的资源包；启动脚本 `gameflowExtractPatrolPackagesFromList`（3226083）对这些巡逻资源做独立筛选。这是巡逻资源管理的证据，不等于已经取得 C++ 资源调度源码。
- `.../metagame/Base102/Base102_tod.p3d.rz` 有 `loc_armourSpawner_spawn_1/2`、`loc_keyArmourSpawn1/2/3`、`loc_keyEscortSpawn_1/2`、`loc_patrolAir_1`、`loc_patrolSoldier_1...` 等分别具名的定位点。多个 Base 目录也有对应分类；不能把一个样本定位点数量当成全局同时生成数。

## 与当前模组的差异

当前街头自动车辆依次依赖：有效军事基地、完整加载的车库及容器、可用库存、真实空闲乘员步行上车、载具资源满足条件，然后建立自动巡逻。资源或路径的一处门槛可以让整个街头看不到车。当前一次自然实测还发现测试加载窗口偏移，生成的西侧车库不在完整加载范围；这是观察器问题，不能归因为禁刷怪。

收口顺序：先证明天然基地与车库实际生成、真实招募上车、坦克与另一辆车在水平方向自主移动；再按原作的分离管理方式核对巡逻配置、道路路径、接敌与增援。不能用指令生成车/乘员替代自然出车，也不能仅凭接口名字宣称已完整移植原作 AI。当前实际注册载具为卡车/APC/坦克，空中巡逻仍是原作对照项，不能在测试报告中写成已实现。

## 边界与原始记录

原生管理器内部的出生距离、不可见点筛选、确切时间单位、道路寻路算法和所有警戒转移条件仍未完整还原；不把推测写成原作固定规则。字节码记录保留了分支/循环指令；线性符号注释只辅助阅读，不是完整反编译源码。

本机完整证据在 `E:/Blacklight-Dual-Closeout-20261031/original-patrol-research/`：源 SHA256、解压偏移、定位点、404 个 Lua 块解析记录与有关指令列表。原作资产只在本机研究目录，不作为新发布内容。
