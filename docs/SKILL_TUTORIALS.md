# 技能页与操作教学

默认按 **U** 打开「能力与教程」，未感染时也能查看。左侧选择分类，中间选择技能，右侧查看循环演示、当前操作组合、说明与前置条件。只有右下方的购买按钮会发送购买请求；查看未解锁的技能不会消费资源。

上方「修改按键」进入游戏的键位设置，返回后说明立即显示当前绑定。教程读取 Minecraft 的攻击、使用、跳跃、移动和模组操作键；键盘、鼠标侧键、组合修饰键和清空绑定都按实际配置显示。攻击、使用、抓取的实际输入也读取相同 KeyMapping，避免只改文字、实际操作仍固定鼠标按钮。

点击演示暂停或继续。右侧内容独立滚动；方向上下键切换技能，PageUp / PageDown 翻阅详情。技能页自身也使用重绑定后的开关键。

重复的吸收、抓取、身份和模式消息保留状态结果，移除操作教学文字。首次登录的成长引导移入基础操作说明。原版 Minecraft 的新手教程由原版设置管理。

## 演示资源

27 个 320 × 180 的 GIF，约 6.4 MiB。大招和子弹俯冲来自独立 QA 存档里的实际技能释放与收回过程；其他动作使用本模组实际骨骼与模型，生命、储备、感官等被动概念使用标注为「机制示意」的动画。等级升级共享对应基础动作演示，并分别显示本等级效果。

GIF 不包含写死的按键。仅异步解码当前选择的演示，使用一张可更新的 GPU 纹理；换技能或关闭页面释放纹理。GIF 只在客户端显示；伤害、升级购买、方块破坏选项仍由服务器判定。

`assets/blacklight/tutorials/manifest.json` 记录帧数、时长与摘要。`tools/encode_tutorial_gifs.py` 从独立捕获目录编码素材。`StandaloneTutorialSmoke` 与 `StandaloneTutorialInspectSmoke` 是开发验收入口，拒绝正常存档，并排除在可安装 JAR 之外。

## 验收

- 全技能、两种语言、按键占位符与 GIF 素材覆盖检查。
- NeoForge 和 Forge 实际客户端：分类与技能点击、GIF 上传、不同 GUI 缩放、实时改键、鼠标侧键、清空绑定、查看不扣点。
- 方块破坏选项通过原服务器消息往返同步；未修改伤害或购买权威边界。
- 独立技能场景录制同时通过大招释放、实际伤害、收回与俯冲验收。

### 本轮自动检查范围（2026-10-13）

两个版本各有 18 项技能教程资源、输入路由、武器轮、技能状态和设置专项测试通过，独立边界检查与可安装包构建通过。两个原生客户端完成 GIF 纹理显示、技能浏览、实时改键、未绑定与侧键、紧凑布局、关闭按键及服务端设置往返检查。最终截图恢复默认左键、右键；未绑定截图来自隔离测试，不是出厂键位。

全量回归未全部通过：NeoForge 263 项中 7 项失败，Forge 278 项中 4 项失败。两个版本共同失败为 MovementTuningDefaultsTest.shippedPresetMatchesCompiledMvpDefaults、MovementUpgradeTest.sprintWallAndGlideScalesFollowOwnedTiers、MovementUpgradeTest.jumpMultiplierPicksTheHighestOwnedTier、BlacklightStateTest.alertBreaksAndBlocksDisguise。NeoForge 另有 WeaponComboTimingTest.basicLinksKeepActiveFramesAndVanillaHurtResistance、WeaponMotionProfileTest.retimingKeepsAnticipationContactAndRecoveryInOrder、WeaponSpecialRoutingTest.bladeDashIsOneContactWithForwardMovementAndACompleteRecovery。

上述失败涉及的移动配置、状态与武器动作源文件已核对与本轮分支基点一致，本轮没有改动；未在独立基点 checkout 重跑全套测试，因此不能将其当作已验证的基线结果。此包是开发试玩包，不能宣称全量回归通过。本轮未重新执行独立服务器启动或多人压力测试；客户端已验证设置请求经集成服务器返回。
