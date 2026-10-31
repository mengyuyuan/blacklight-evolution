# 2026-10-25 用户日志核查

用户提供的 `latest.log` 和 `debug.log` 均记录2026-10-24约09:05开始的同一次客户端运行；两份不是两次独立复现。实际加载 Blacklight `0.3.4-preview.20261024-military.3`，MC1.21.1/NeoForge21.1.244，已加载GeckoLib4.9.3。此结果不代表尚未交付的military.4已安装。

只读来源：用户当前PCL实例 `logs/latest.log`（125541字节，SHA256 `3b904408b022affcd8835714cb7d9164033bfa4d8293e4bde46cc1e1ed9ebbfa`）与 `logs/debug.log`（1363197字节，SHA256 `b903e307e8ef0a999f3265a2355ea6b76df70c0d04b71b37da37c62e47d4d663`）。不复制完整私人日志到交付包；仅保留问题摘要与来源哈希。

## ERROR的实际归属

两份各8个ERROR头，内容相同；未找到黑色守望自身异常堆栈或本次崩溃证据。

| latest.log行 | 错误 | 归属与结论 |
| --- | --- | --- |
| 171 | Failed to fetch user properties | 账号属性网络请求超时，不是黑色守望实体或基地代码 |
| 264 | Couldn't connect to realms | Realms认证失败；离线身份不能据此认定本地模组故障 |
| 370 | scguns:entities/blunderer，minecraft:looting_enchant未知 | Scorched Guns战利品数据与当前注册表不匹配 |
| 372、374 | c:tools/knives、c:tools引用缺失 | 与日志中缺失的scguns/farmersdelight刀具项相关，不能归因黑色守望 |
| 376 | scguns:anthralite/anthralite_knife_smithing | 缺失scguns:anthralite_knife和farmersdelight:iron_knife |
| 431、486 | scguns的两种depleted_diamond_steel配方 | 缺失create:experience_nugget；属于可选联动/移植数据问题 |

本轮只修用户授权的黑光/黑色守望范围，不改Scorched Guns JAR、安装其他模组或修改账号配置。Blacklight出现 `deferred task 3.074s` 的WARN是初始化耗时提示，不是运行异常；不通过隐藏日志声称修复。

## 与黑色守望相关的实际失败

约09:12:49，用户在超平坦Y=-61放置黑色守望大型基地；约09:13:33显示 `issued 0/20, loaded 0, lost 0, unloaded 0 state MUSTERING`。这与已准确RED复现的“全部Mob共享旧人口额度”一致，military.4已经改为独立基地驻军额度；验收以本轮最终GameTest/普通服结果为准，不把来源日志中的旧失败抹掉。

约09:09至09:10的“未找到实体”是选择器没有匹配对象的命令反馈，不是Java异常。普通兵role转军官后仍保留原实体注册类型，使用原type选择器；新建military_officer才由该type选择。

约09:14:50，`locate blacklight:city_military_base` 返回 `[1184, ~, 544]`。因此不能把“维度完全没有军事结构注册”当成已确认根因；仍需以原生FULL区块、真实结构piece、箱子和驻军验证可玩结果。

## 本轮后续

继续核查 `blacklight:world` 与 `blacklight:city` 的原生生成、基地/检查站模块与有限驻军，保留现有区块和玩家建筑。自然生成只对新生成区块生效；不为补建筑自动重置用户维度。最终交付与剩余验证在 [实施记录](MILITARY4_IMPLEMENTATION_20261024_ZH.md)。
