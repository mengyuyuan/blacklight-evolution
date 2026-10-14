# 抓取与吸收兼容规则

不再要求将每个 Mod 方块或生物加入白名单。旧 throwable_blocks、consumable 标签保留兼容资源，但不再作为准入条件。

- 方块：默认支持有物品形式、非空气、非流体、可破坏的方块，包含普通 Mod 方块、楼梯、半砖、玻璃、黑曜石。不限制命名空间或挖掘工具类型。
- 方块排除标签：blacklight:ungrabbable_blocks。原有区域权限和 BreakEvent 仍由服务器逐个检查。
- 箱子/机器等方块实体、门/床等跨格结构暂不支持：当前投掷载荷保存 BlockState，不携带库存和机器 NBT；不能把这些数据丢掉后声称支持。
- 生物：普通 Mob（原版及 Mod）默认参与抓取、普通吸收和粘液吞噬判定，不依赖 consumable 名单。
- 生物排除标签：blacklight:unconsumable。生命、无敌、占用、骑乘/载客、拴绳和驯养宠物限制仍保留；高血量目标需要先削弱，体型自动决定普通或大型动作。
- 不包含玩家、盔甲架及不继承 Mob 的特殊 LivingEntity；特殊多部件 Boss 和自定义渲染需要对应 Mod 实机检查。

数据包可向排除标签加入对象，无需改 Java 代码。1.21.1 标签目录为 tags/block、tags/entity_type；1.20.1 为 tags/blocks、tags/entity_types。
