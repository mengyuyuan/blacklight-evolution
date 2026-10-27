> 运行进度：感染宿主已接入第一批 12 类，另 60 项仍为设计；参见 [第一批实际作用](../ORGAN_FUNCTIONAL_BATCH1_ZH.md)。新增 7 类尚未售为主角技能。

# 72 种器官模块逐项目录

状态：设计目录，不能将目录条目直接计为已实装。2026-10-27 已逐批验证 33 / 72，剩余 39 项；当前证据以[补完台账](../INFECTION_COMPLETION_WORKPLAN_ZH.md)为准。完整机制、迁移与验收要求见 [设计方案](../ORGAN_DIVERSIFICATION_72_ZH.md)。

左右副本、颜色和等级不另计一类；来源份数与不同类别数分别计数。以下能力与表现均为设计目标。

## 1. 驱动与肢体（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 01 `blacklight:organ_muscle` | 并行肌束 | 骨骼肌收缩与力臂。[S01](https://openstax.org/books/biology-2e/pages/38-introduction) | **muscle_force**：沿原有肩胸和承重肢增厚；保留物种比例 | 持续出力消耗养分和供氧；粗壮不等于摆腿频率加倍；批次 1；旧入口映射 |
| 02 `blacklight:elastic_tendon` | 储能弹腱 | 肌腱的弹性储能与回弹。[S01](https://openstax.org/books/biology-2e/pages/38-introduction)、[S02](https://www.nature.com/articles/428819a) | **locomotor_recoil**：后肢长腱、屈伸关节和落地缓冲共同重塑 | 需要蓄能和回收动作；负载改变起跳距离；批次 1 |
| 03 `blacklight:saddle_spring` | 鞍形储能器 | 螳螂虾弹簧、锁扣和连杆组合。[S02](https://www.nature.com/articles/428819a) | **spring_latch_strike**：承力肢基部形成一体鞍形硬组织和锁扣 | 蓄势后高峰释放；锁定释放方向并有复位时段；批次 2 |
| 04 `blacklight:hydraulic_extensor` | 液压伸肢器 | 蜘蛛部分腿关节的血淋巴压力驱动。[S03](https://journals.biologists.com/jeb/article/215/4/578/11117/Hydraulic-leg-extension-is-not-necessarily-the) | **hydraulic_extension**：足基压力腔与细长节肢连接；伸屈节律可见 | 失压后伸展减弱；大型宿主还要肌肉协同；批次 1 |
| 05 `blacklight:muscular_hydrostat` | 静水肌索 | 肌肉静水骨骼的纵环肌协调。[S01](https://openstax.org/books/biology-2e/pages/38-introduction) | **hydrostat_bending**：腕、长吻或尾部形成连续可弯曲肌索 | 缩短时增粗；伸长受体积和承力约束；批次 1 |
| 06 `blacklight:patagial_wing` | 翼膜复合器 | 蝙蝠翼膜、延长指骨和膜内肌。[S04](https://onlinelibrary.wiley.com/doi/10.1002/ar.23740/full) | **membrane_flight**：肩根固定承力，翼指撑膜；折叠与扑翼分段 | 体重与翼面匹配；大体型先发展滑翔再争取持续飞行；批次 3 |
| 07 `blacklight:caudal_propulsor` | 鳍尾推进器 | 鱼类尾柄与尾鳍传递推进力。[S05](https://openstax.org/books/biology-2e/pages/29-2-fishes) | **caudal_swimming**：尾柄、躯干波动和鳍面协同；水生流线轮廓 | 主要在水中有效；陆地拖尾影响转弯；批次 3 |
| 08 `blacklight:setal_pad` | 刚毛足垫 | 壁虎趾垫刚毛的干式黏附。[S06](https://doi.org/10.1073/pnas.192252799) | **setal_adhesion**：足趾展开成片状黏附面，接触与剥离分开 | 有接触方向和承重要求；需要逐足脱离；批次 3 |

## 2. 攻击与抓握（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 09 `blacklight:cutting_blade` | 切割骨刃 | 角质、齿质等硬组织构成刃缘的仿生扩展。[S01](https://openstax.org/books/biology-2e/pages/38-introduction) | **bladed_sweep**：前肢、尾缘或口缘形成宽刃与埋入式根部 | 硬刃负载增加，转向和收招需要承力；批次 1 |
| 10 `blacklight:chela` | 螯钳复合器 | 甲壳类螯的夹持与杠杆作用。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods) | **pincer_crush**：肢端形成相向闭合双钳，保留夹持空间 | 大开口、闭合速度和夹持力相互取舍；批次 1 |
| 11 `blacklight:ramming_horn` | 撞角复合器 | 角、头骨与颈部的载荷传递。[S01](https://openstax.org/books/biology-2e/pages/38-introduction) | **ramming_charge**：角根、额骨和肩颈一起重构，形成低头冲撞轮廓 | 需要起步距离和制动；转角受体重约束；批次 1 |
| 12 `blacklight:dactyl_club` | 重锤打击器 | 螳螂虾锤状捕食附肢。[S02](https://www.nature.com/articles/428819a) | **impact_club**：肢端为宽厚锤面，近端肌肉与关节提供支撑 | 锤面和复位耗时显著；与储能器组合才获得高峰打击；批次 2 |
| 13 `blacklight:ballistic_tongue` | 投射舌器 | 变色龙舌投射与回收机构。[S08](https://onlinelibrary.wiley.com/doi/abs/10.1111/j.1558-5646.2010.01133.x) | **projectile_tongue**：舌根藏在口咽，伸出捕获后沿原路径回收 | 只处理匹配重量的目标，拉回动作占用口器；批次 2 |
| 14 `blacklight:organ_grasp` | 抓握腕器 | 头足类腕与吸盘的抓握结构。[S01](https://openstax.org/books/biology-2e/pages/38-introduction)、[S35](https://openstax.org/books/biology-2e/pages/28-4-superphylum-lophotrochozoa-mollusks-and-annelids) | **prehensile_grip**：可表达为腕、前足、钳指或缠绕尾；按宿主分配 | 保留原生足的支撑功能；获得抓握不自动改成触须步行；批次 1；旧入口映射 |
| 15 `blacklight:pharyngeal_jaw` | 伸缩咽颚 | 海鳝咽颚将猎物运向食道。[S09](https://www.nature.com/articles/nature06062) | **pharyngeal_transport**：主口器内部第二对颚完成前伸、咬合和回拉 | 已捕获后才工作，单次目标和主颚共享吞噬占用；批次 1 |
| 16 `blacklight:cnidocyte_battery` | 刺丝囊阵列 | 刺胞动物刺丝囊的快速外翻。[S10](https://openstax.org/books/biology-2e/pages/28-2-phylum-cnidaria) | **cnidocyte_discharge**：接触面或短距喷口布置可回收的黑光刺丝束 | 每次释放消耗预制囊；宏观尺寸与射程属于黑光扩展；批次 2 |

## 3. 口器与消化（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 17 `blacklight:organ_jaw` | 吞噬颅颚 | 颅颚、牙列与闭口肌的配合。[S01](https://openstax.org/books/biology-2e/pages/38-introduction)、[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **oral_bite_capture**：按原颅型重构咬合面与下颚，给口咽留下通道 | 咬住和吞入有不同阶段；颚宽与颈部支撑协调；批次 1；旧入口映射 |
| 18 `blacklight:gizzard` | 研磨胃 | 鸟类肌胃的机械研磨。[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **mechanical_grinding**：腹部内部厚壁胃，吞咽后有局部收缩 | 处理坚韧食物耗时；研磨不直接创造矿石能量；批次 2 |
| 19 `blacklight:fermentation_chamber` | 发酵前胃 | 反刍动物前胃的微生物发酵。[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **microbial_fermentation**：腹腔分叶膨大，形成前重或腹重的取食体型 | 慢加工植物与有机组织，低瞬时收益；批次 4 |
| 20 `blacklight:extraoral_gland` | 体外消化腺 | 蜘蛛向猎物释放消化液。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods)、[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **prey_liquefaction**：口器与附着面覆盖猎物，组织沿接触面回流 | 覆盖需要维持；伤害、控制、收益只由服务端结算；批次 2 |
| 21 `blacklight:gill_raker` | 滤食鳃耙 | 鱼类鳃耙拦截食物颗粒。[S05](https://openstax.org/books/biology-2e/pages/29-2-fishes)、[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **particle_filter_feeding**：口咽形成梳状筛面，和水流入口连接 | 只利用真实存在的可用食物资源，不凭空刷进化收益；批次 3 |
| 22 `blacklight:absorptive_gut` | 吸收肠襞 | 肠道褶皱与吸收表面。[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **intestinal_absorption**：内脏分区变复杂，外部只体现腹部运动 | 提升已消化物的转化效率，需要输运能力；批次 4 |
| 23 `blacklight:digestive_pancreas` | 消化胰腺 | 消化酶参与蛋白、脂肪等食物处理。[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **enzyme_allocation**：内部腺叶连接消化通道，按食物改变分泌节律 | 酶储量和处理对象匹配，不能把所有材质都吃成养分；批次 4 |
| 24 `blacklight:crop` | 嗉囊储食器 | 鸟类嗉囊暂存食物。[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **crop_buffer**：颈根或前胸形成可收缩储食袋 | 储存占体积；与后续消化分开，不立即兑现全部收益；批次 4 |

## 4. 防护与体表（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 25 `blacklight:organ_carapace` | 复层甲壳 | 节肢动物外骨骼承重与保护。[S01](https://openstax.org/books/biology-2e/pages/38-introduction)、[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods) | **exoskeletal_bracing**：整片外骨骼分段连接关节，保留活动间隙 | 重甲增加惯性，增长阶段需要重建接缝；批次 1；旧入口映射 |
| 26 `blacklight:osteoderm` | 真皮骨板 | 鳄类等动物的真皮骨质板。[S01](https://openstax.org/books/biology-2e/pages/38-introduction) | **dermal_osteoderm**：皮下骨板与原躯干连为整体，局部厚薄可辨 | 局部抗穿刺强化，关节处保留柔性区；批次 5 |
| 27 `blacklight:nacre_shield` | 层状复合盾 | 贝壳珍珠层的层状材料结构。[S12](https://www.sciencedirect.com/science/article/pii/S0022509606001268) | **lamellar_crack_deflection**：壳面为有方向的连续层片，主要形成宽盾轮廓 | 针对裂纹扩展；侧面、开口与钝击响应不同；批次 5 |
| 28 `blacklight:mucus_sheath` | 黏液护套 | 盲鳗黏液和纤维状防御分泌物。[S13](https://openstax.org/books/biology-2e/pages/29-2-fishes) | **mucin_thread_defense**：体表短暂形成连贯薄护套，运动时拉出丝束 | 可补充但需养分与液体，不直接赋予永久无敌；批次 5 |
| 29 `blacklight:rolling_shield` | 卷曲背盾 | 犰狳等动物卷曲时的防护构型。[S01](https://openstax.org/books/biology-2e/pages/38-introduction) | **defensive_coiling**：背部大盾与腹侧收合，头尾进入保护范围 | 主动卷曲时防守强，行动与视野受姿态约束；批次 5 |
| 30 `blacklight:autotomy_ring` | 自切隔离环 | 部分动物断尾自切的分离面。[S14](https://www.ncbi.nlm.nih.gov/books/NBK9971/) | **autotomy_escape**：尾或附肢出现预设分离环，脱离后保留有限诱饵 | 丢弃真实组织，重建需时间和养分；批次 5 |
| 31 `blacklight:chromatophore` | 色素细胞皮层 | 头足类对色素囊的神经肌肉控制。[S15](https://journals.biologists.com/jeb/article/117/1/415/5164/Chromatophore-Motor-Units-in-Eledone-Cirrhosa) | **chromatic_camouflage**：大片皮肤改变明暗和粗纹，保护原有体表结构 | 对视觉有效，不能同时骗过嗅觉、振动等通道；批次 5 |
| 32 `blacklight:quill_field` | 角质毛棘层 | 豪猪等动物的接触性棘防御。[S01](https://openstax.org/books/biology-2e/pages/38-introduction) | **contact_quill_defense**：毛棘沿背与尾形成有生长方向的防御层 | 接触防御有方向性，区别于远射器官；批次 5 |

## 5. 感知与神经（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 33 `blacklight:compound_eye` | 多透镜复眼 | 节肢动物复眼的分区视场。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods)、[S16](https://openstax.org/books/biology-2e/pages/36-1-sensory-processes) | **compound_visual_tracking**：头侧或既有眼部扩成复眼面，保留遮挡关系 | 广视场与成像精度有取舍，使用实际视线；批次 3 |
| 34 `blacklight:infrared_pit` | 红外窝器 | 蛇类颊窝膜接受热辐射。[S17](https://pmc.ncbi.nlm.nih.gov/articles/PMC2855400/) | **infrared_targeting**：口鼻附近的凹陷感受面有温度变化表现 | 受目标热特征、遮挡与环境影响，游戏距离另行标定；批次 3 |
| 35 `blacklight:echolocation` | 回声定位复合器 | 蝙蝠发声与听觉接收协同。[S04](https://onlinelibrary.wiley.com/doi/10.1002/ar.23740/full)、[S16](https://openstax.org/books/biology-2e/pages/36-1-sensory-processes) | **echolocation_pulse**：喉、耳廓与头部动作共同表达感知脉冲 | 发声会暴露自身，需要间隔与有限查询预算；批次 3 |
| 36 `blacklight:olfactory_turbinate` | 嗅觉涡甲 | 鼻腔嗅觉表面与气味取样。[S16](https://openstax.org/books/biology-2e/pages/36-1-sensory-processes) | **olfactory_trailing**：鼻吻延长或出现取样裂隙，沿实际气味迹移动 | 气味线索过期且随环境衰减，不直接取得全图坐标；批次 3 |
| 37 `blacklight:electroreceptor` | 电感受壶腹 | 软骨鱼等动物的弱电场感受。[S18](https://doi.org/10.1016/j.cub.2015.09.036) | **electroreceptive_tracking**：头部或腹侧分布感受孔，水中局部起伏 | 主要依赖导电介质；离水性能改变；批次 3 |
| 38 `blacklight:lateral_line` | 侧线管器 | 鱼类侧线感受水体机械扰动。[S05](https://openstax.org/books/biology-2e/pages/29-2-fishes)、[S16](https://openstax.org/books/biology-2e/pages/36-1-sensory-processes) | **water_disturbance_sensing**：躯侧沿身体轴线形成连续感受槽 | 对水中动作敏感，静止目标与背景扰动影响判断；批次 3 |
| 39 `blacklight:vestibular_organ` | 定向前庭器 | 内耳前庭的空间与加速度感受。[S16](https://openstax.org/books/biology-2e/pages/36-1-sensory-processes) | **vestibular_stabilization**：头身姿态和落地支撑更稳定，器官位于内部 | 优化协调与恢复动作，保留真实位移和碰撞；批次 3 |
| 40 `blacklight:organ_crest` | 神经冠丛 | 神经节协调与保护腔的黑光外显设计。[S16](https://openstax.org/books/biology-2e/pages/36-1-sensory-processes) | **ganglionic_coordination**：按宿主中轴形成受保护神经腔，冠形仅是外显方案 | 协调分布器官；外部骨冠不是现实神经节的必然形态；批次 1；旧入口映射 |

## 6. 呼吸与循环（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 41 `blacklight:gill_lamella` | 鳃叶换气器 | 鱼类鳃的气体交换表面。[S19](https://openstax.org/books/biology-2e/pages/39-chapter-summary) | **branchial_exchange**：颈侧或躯干侧形成有保护的鳃裂 | 离水后的湿润和结构维持成为限制；批次 4 |
| 42 `blacklight:bronchial_lung` | 支气管肺 | 气道分支与肺泡气体交换。[S19](https://openstax.org/books/biology-2e/pages/39-chapter-summary) | **alveolar_exchange**：胸腔随呼吸起伏，外形围绕受保护内脏空间 | 组织体积与换气表面共同增长；批次 4 |
| 43 `blacklight:avian_airflow` | 气囊肺复合器 | 鸟类气囊驱动经肺的定向气流。[S20](https://openstax.org/books/biology-2e/pages/39-3-breathing) | **flow_through_ventilation**：气囊在胸腹内部配合收缩，不作为额外翅膀 | 气囊参与通气，气体交换发生在肺部；飞行还需驱动和翼面；批次 4 |
| 44 `blacklight:book_lung` | 书肺复合器 | 蛛形动物片层式呼吸结构。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods)、[S19](https://openstax.org/books/biology-2e/pages/39-chapter-summary) | **lamellar_air_exchange**：腹侧形成受保护的片层腔与呼吸口 | 保持开口与湿度，需要与外甲布局协调；批次 4 |
| 45 `blacklight:tracheal_network` | 气管分支网 | 昆虫气门与气管向组织输送气体。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods)、[S19](https://openstax.org/books/biology-2e/pages/39-chapter-summary) | **tracheal_distribution**：体侧气门与内部细管成网，节律性开合 | 巨型体型要额外支持，不能靠比例放大维持全部效率；批次 4 |
| 46 `blacklight:main_heart` | 分区主心泵 | 心脏分区与定向循环。[S21](https://openstax.org/books/biology-2e/pages/40-3-mammalian-heart-and-blood-vessels) | **cardiac_distribution**：主干内部搏动，周边血管随承重部位重排 | 输运能力支撑其他器官，受损后相应区域效率下降；批次 4 |
| 47 `blacklight:branchial_heart` | 辅助鳃心 | 头足类辅助鳃部循环的心泵。[S35](https://openstax.org/books/biology-2e/pages/28-4-superphylum-lophotrochozoa-mollusks-and-annelids)、[S21](https://openstax.org/books/biology-2e/pages/40-3-mammalian-heart-and-blood-vessels) | **branchial_pumping**：靠近鳃区布置小型内部泵腔 | 水生换气和输运协同，不能无条件倍增全部属性；批次 4 |
| 48 `blacklight:splenic_reservoir` | 脾脏储血器 | 部分动物脾脏的血液储存与释放。[S22](https://openstax.org/books/biology-2e/pages/42-2-adaptive-immune-response) | **blood_reserve_release**：腹内储血区在高负荷时收缩 | 提供短时储备，需要恢复；区别于主心泵持续输运；批次 4 |

## 7. 代谢与调节（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 49 `blacklight:hepatic_lobe` | 肝脏解毒叶 | 肝脏代谢与物质处理。[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems)、[S23](https://openstax.org/books/biology-2e/pages/41-chapter-summary) | **hepatic_detoxification**：内部分叶连接循环与消化通道 | 处理有标识的负荷，不能统一清除所有模组效果；批次 5 |
| 50 `blacklight:nephric_filter` | 肾滤单元 | 肾单位的滤过与重吸收。[S23](https://openstax.org/books/biology-2e/pages/41-chapter-summary) | **nephric_filtration**：内部滤管与循环成对连接 | 降低废物积累，过度失水时效率受限；批次 5 |
| 51 `blacklight:salt_gland` | 盐分泌腺 | 部分海生动物特化盐排泄。[S23](https://openstax.org/books/biology-2e/pages/41-chapter-summary) | **active_salt_excretion**：眼眶、鼻吻附近出现小型分泌出口 | 高盐环境适应，消耗输运和代谢能力；批次 5 |
| 52 `blacklight:malpighian_tubule` | 马氏管束 | 昆虫等动物的排泄和水盐调节。[S23](https://openstax.org/books/biology-2e/pages/41-chapter-summary) | **malpighian_recovery**：腹内细管汇入消化排泄通道 | 适合节肢式布局，与肾型调节形成不同适应方案；批次 5 |
| 53 `blacklight:fat_body` | 脂肪体 | 昆虫脂肪体等储能代谢组织。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods)、[S11](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems) | **metabolic_storage**：腹部形成分叶储能带，消耗后逐渐回缩 | 提高储备和长期续航，增加真实组织体积和质量；批次 5 |
| 54 `blacklight:electric_organ` | 发电肌板 | 电鱼由特化电细胞形成的发电器官。[S18](https://doi.org/10.1016/j.cub.2015.09.036) | **electrocyte_volley**：躯侧或尾部叠排电细胞板，释放时逐段收缩 | 脉冲放电需补充，介质与路径影响命中和作用范围；批次 6 |
| 55 `blacklight:countercurrent_rete` | 逆流换热束 | 相邻血流方向形成的换热结构。[S24](https://openstax.org/books/biology-2e/pages/33-3-homeostasis) | **countercurrent_heat_recovery**：内部分布换热血管束，外部通过热脉冲体现 | 减少热散失或调配散热，不产生无限热量；批次 6 |
| 56 `blacklight:adrenal_complex` | 应激腺复合器 | 肾上腺等内分泌器官参与短时应激。[S25](https://openstax.org/books/anatomy-and-physiology-2e/pages/17-6-the-adrenal-glands) | **endocrine_burst**：内部腺体驱动短促全身动作变化 | 短时输出后有恢复和养分负荷；批次 6 |

## 8. 分泌与化学攻防（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 57 `blacklight:venom_gland` | 毒液腺 | 毒液产生与毒牙、毒刺递送。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods) | **venom_injection**：腺体靠近递送器，牙槽或刺根可辨 | 必须实际刺入或咬合；与体表毒物、腐蚀分泌区分；批次 2 |
| 58 `blacklight:corrosive_gland` | 腐蚀分泌腺 | 动物酸性等防御分泌物的仿生扩展。[S26](https://pmc.ncbi.nlm.nih.gov/articles/PMC12092131/) | **corrosive_exudation**：口器或腹侧出现耐受分泌槽，组织有明确出口 | 有限储量，需保护自身管路；强腐蚀性为黑光扩展；批次 2 |
| 59 `blacklight:bipartite_reactor` | 双腔反应腺 | 放屁虫分腔、阀门与脉冲喷射。[S26](https://pmc.ncbi.nlm.nih.gov/articles/PMC12092131/) | **valved_reactive_spray**：两腔藏于体内，喷口定向开合并有间歇收缩 | 反应与冷却有周期，不能每帧触发爆炸；批次 2 |
| 60 `blacklight:silk_gland` | 丝蛋白腺 | 蜘蛛丝腺生产纺丝材料。[S27](https://www.nature.com/articles/ng.3852) | **silk_material_synthesis**：腹内丝腺形成供料体积，连接外部纺丝出口 | 生产材料耗养分，区别于控制丝形与落点的纺丝器；批次 2 |
| 61 `blacklight:spinneret` | 纺丝器 | 蜘蛛纺器控制纤维输出。[S27](https://www.nature.com/articles/ng.3852) | **fiber_extrusion**：腹端、腕端或口侧形成有动作的出丝部件 | 丝线需实际锚点；断裂、回收与实体接触统一结算；批次 2 |
| 62 `blacklight:adhesive_papilla` | 黏胶喷孔 | 绒蠕虫口乳突喷出的蛋白黏液射流。[S28](https://www.nature.com/articles/ncomms7292) | **oscillating_adhesive_jet**：成对软喷口摆动，射流结成接触网 | 黏附要实际碰撞和持续时间，不隔墙锁住目标；批次 2 |
| 63 `blacklight:alarm_gland` | 警戒信号腺 | 化学信号参与动物群体通讯。[S29](https://openstax.org/books/biology-2e/pages/45-7-behavioral-biology-proximate-and-ultimate-causes-of-behavior) | **chemical_alarm_recruitment**：小型信号出口配合收缩，同族沿局部线索响应 | 信号范围与存活时间有限，不扫描或加载全世界；批次 6 |
| 64 `blacklight:ink_sac` | 墨囊 | 头足类墨囊的防御性释放。[S35](https://openstax.org/books/biology-2e/pages/28-4-superphylum-lophotrochozoa-mollusks-and-annelids) | **ink_screen**：体内储墨腔与释放通道相连，形成局部遮蔽 | 改变视觉判断，其他感知通道仍可工作；批次 3 |

## 9. 修复、繁育与殖群（8 种）

| 编号 / ID | 器官 | 生物学依据 | 独立作用与外形 | 支撑与使用代价 |
| --- | --- | --- | --- | --- |
| 65 `blacklight:blastema_niche` | 再生芽基 | 有尾两栖类再生中的芽基与细胞来源。[S30](https://publications.mpi-cbg.de/Kragl_2009_1409.pdf) | **tissue_regrowth**：伤口先封闭再长出连续组织，按原部位重建 | 器官和肢体分阶段修复，不能瞬间凭空补出全身；批次 6 |
| 66 `blacklight:hematopoietic_marrow` | 造血髓核 | 骨髓等造血组织产生血细胞。[S31](https://openstax.org/books/biology-2e/pages/42-2-adaptive-immune-response) | **hematopoietic_replenishment**：髓腔受骨架保护，内部缓慢脉动 | 恢复输运细胞储备，与快速止伤和肢体再生分工不同；批次 6 |
| 67 `blacklight:thymic_organ` | 胸腺样免疫器 | 胸腺参与T细胞成熟和免疫耐受。[S31](https://openstax.org/books/biology-2e/pages/42-2-adaptive-immune-response) | **immune_maturation**：内部腺叶与淋巴结构连接，外部只需小幅体态变化 | 建立响应需要时间和来源信息；不等于对所有伤害免疫；批次 6 |
| 68 `blacklight:lymph_node_chain` | 淋巴结链 | 淋巴结过滤淋巴与组织局部免疫反应。[S31](https://openstax.org/books/biology-2e/pages/42-2-adaptive-immune-response) | **lymphatic_filtering**：沿宿主淋巴路径布置内部结点，局部感染时有反馈 | 对局部污染与寄生负荷工作，避免所有减伤重复相乘；批次 6 |
| 69 `blacklight:ecdysial_complex` | 蜕壳重建器 | 节肢动物生长中旧表皮分离与新表皮形成。[S07](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods) | **ecdysial_remodeling**：旧甲与新甲有连续过渡，尺寸和关节接缝一起更新 | 需要准备期；保留原有器官数据与实体身份；批次 6 |
| 70 `blacklight:brood_pouch` | 育囊 | 部分动物以育囊保护发育中的后代。[S32](https://openstax.org/books/biology-2e/pages/43-1-reproduction-methods) | **brood_protection**：腹侧或体内形成有开口的保护腔，亲体轮廓随负载变化 | 真实投入养分与组织；服务器对活体繁育数量设预算；批次 6 |
| 71 `blacklight:budding_disc` | 出芽生殖盘 | 水螅等动物的无性出芽。[S33](https://openstax.org/books/biology-ap-courses/pages/34-1-reproduction-methods) | **asexual_budding**：局部先长芽再形成完整个体，最后有分离过程 | 亲体承担材料消耗，分离个体不继承虚假的吞噬次数；批次 6 |
| 72 `blacklight:vascular_stolon` | 殖群贯通索 | 群体海鞘的血管连通、融合与识别。[S34](https://www.sciencedirect.com/science/article/pii/S0092867405011669) | **colony_vascular_link**：组织索连接成熟感染组织与建筑附着点，形成供养网络 | 需要真实连接和同化识别；跨物种强制融合是黑光设定；批次 6 |

## 来源说明

来源表记录已找到的依据和仍需补核的专门解剖问题。基础教材用于器官分工；特化结构尽量使用原始研究。文献支持生物学依据，不支持黑光跨物种重组、游戏射程、秒级再生或现成游戏能力。

- **S01** [OpenStax Biology 2e: Musculoskeletal System](https://openstax.org/books/biology-2e/pages/38-introduction)（original_textbook）
- **S02** [Patek et al. 2004: Deadly strike mechanism of a mantis shrimp](https://www.nature.com/articles/428819a)（primary_research）
- **S03** [Hydraulic leg extension is not necessarily the main drive in large spiders](https://journals.biologists.com/jeb/article/215/4/578/11117/Hydraulic-leg-extension-is-not-necessarily-the)（primary_research）
- **S04** [Assessment of the Hindlimb Membrane Musculature of Bats](https://onlinelibrary.wiley.com/doi/10.1002/ar.23740/full)（primary_research）
- **S05** [OpenStax Biology 2e: Fishes](https://openstax.org/books/biology-2e/pages/29-2-fishes)（original_textbook）。implementation_before_species_asset
- **S06** [Autumn et al. 2002: Evidence for van der Waals adhesion in gecko setae](https://doi.org/10.1073/pnas.192252799)（primary_research）
- **S07** [OpenStax Biology 2e: Superphylum Ecdysozoa: Arthropods](https://openstax.org/books/biology-2e/pages/28-6-superphylum-ecdysozoa-arthropods)（original_textbook）
- **S08** [Claverie et al.: Modularity and scaling in fast movements](https://onlinelibrary.wiley.com/doi/abs/10.1111/j.1558-5646.2010.01133.x)（primary_research）。储能放大框架；变色龙舌的具体建模应继续核对 de Groot / van Leeuwen 原始资料。
- **S09** [Mehta and Wainwright 2007: Raptorial jaws in the throat help moray eels swallow large prey](https://www.nature.com/articles/nature06062)（primary_research）
- **S10** [OpenStax Biology 2e: Phylum Cnidaria](https://openstax.org/books/biology-2e/pages/28-2-phylum-cnidaria)（original_textbook）
- **S11** [OpenStax Biology 2e: Digestive Systems](https://openstax.org/books/biology-2e/pages/34-1-digestive-systems)（original_textbook）
- **S12** [On the mechanics of mother-of-pearl: A key feature in the material hierarchical structure](https://www.sciencedirect.com/science/article/pii/S0022509606001268)（primary_research）
- **S13** [Hagfish defensive slime](https://openstax.org/books/biology-2e/pages/29-2-fishes)（original_textbook）。needs_primary_slime_paper_before_implementation
- **S14** [Autotomy / regeneration distinction](https://www.ncbi.nlm.nih.gov/books/NBK9971/)（original_textbook）。needs_autotomy_plane_paper_before_implementation
- **S15** [Chromatophore Motor Units in Eledone Cirrhosa](https://journals.biologists.com/jeb/article/117/1/415/5164/Chromatophore-Motor-Units-in-Eledone-Cirrhosa)（primary_research）
- **S16** [OpenStax Biology 2e: Sensory Processes](https://openstax.org/books/biology-2e/pages/36-1-sensory-processes)（original_textbook）
- **S17** [Gracheva et al. 2010: Molecular Basis of Infrared Detection by Snakes](https://pmc.ncbi.nlm.nih.gov/articles/PMC2855400/)（primary_research）
- **S18** [Catania 2015: Electric Eels Concentrate Their Electric Field to Induce Involuntary Fatigue in Struggling Prey](https://doi.org/10.1016/j.cub.2015.09.036)（primary_research）。放电作用有直接依据；壶腹电感受和具体受体布局需补专门来源。
- **S19** [OpenStax Biology 2e: Respiratory Systems](https://openstax.org/books/biology-2e/pages/39-chapter-summary)（original_textbook）
- **S20** [OpenStax Biology 2e: Breathing](https://openstax.org/books/biology-2e/pages/39-3-breathing)（original_textbook）
- **S21** [OpenStax Biology 2e: Heart and Blood Vessels](https://openstax.org/books/biology-2e/pages/40-3-mammalian-heart-and-blood-vessels)（original_textbook）。辅助鳃心需要头足类专门资料补核，不能按哺乳类心脏直接套用。
- **S22** [Spleen and blood storage](https://openstax.org/books/biology-2e/pages/42-2-adaptive-immune-response)（original_textbook）。needs_diving_or_exercise_splenic_contraction_paper_before_implementation
- **S23** [OpenStax Biology 2e: Osmotic Regulation and Excretion](https://openstax.org/books/biology-2e/pages/41-chapter-summary)（original_textbook）
- **S24** [Thermoregulation and homeostasis](https://openstax.org/books/biology-2e/pages/33-3-homeostasis)（original_textbook）。needs_rete_anatomy_before_implementation
- **S25** [OpenStax Anatomy and Physiology 2e: The Adrenal Glands](https://openstax.org/books/anatomy-and-physiology-2e/pages/17-6-the-adrenal-glands)（original_textbook）
- **S26** [Molecular basis of the explosive defence response in the bombardier beetle Brachinus crepitans](https://pmc.ncbi.nlm.nih.gov/articles/PMC12092131/)（primary_research）
- **S27** [Babb et al. 2017: The Nephila clavipes genome highlights the diversity of spider silk genes and their complex expression](https://www.nature.com/articles/ng.3852)（primary_research）
- **S28** [Concha et al. 2015: Oscillation of the velvet worm slime jet by passive hydrodynamic instability](https://www.nature.com/articles/ncomms7292)（primary_research）
- **S29** [Chemical signaling](https://openstax.org/books/biology-2e/pages/45-7-behavioral-biology-proximate-and-ultimate-causes-of-behavior)（original_textbook）
- **S30** [Kragl et al. 2009: Cells keep a memory of their tissue origin during axolotl limb regeneration](https://publications.mpi-cbg.de/Kragl_2009_1409.pdf)（primary_research）
- **S31** [OpenStax Biology 2e: Adaptive Immune Response](https://openstax.org/books/biology-2e/pages/42-2-adaptive-immune-response)（original_textbook）
- **S32** [Animal development and reproductive strategies](https://openstax.org/books/biology-2e/pages/43-1-reproduction-methods)（original_textbook）。needs_selected_brood_pouch_taxon_before_implementation
- **S33** [OpenStax: Reproduction Methods / budding in Hydra](https://openstax.org/books/biology-ap-courses/pages/34-1-reproduction-methods)（original_textbook）
- **S34** [Stem Cells Are Units of Natural Selection in a Colonial Ascidian](https://www.sciencedirect.com/science/article/pii/S0092867405011669)（primary_research）
- **S35** [OpenStax Biology 2e: Mollusks and Annelids](https://openstax.org/books/biology-2e/pages/28-4-superphylum-lophotrochozoa-mollusks-and-annelids)（original_textbook）。supplement_taxon_specific_cephalopod_musculature_before_modeling
