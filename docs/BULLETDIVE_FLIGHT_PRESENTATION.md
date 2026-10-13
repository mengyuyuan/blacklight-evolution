# 子弹俯冲的空中表现

真人反馈前，`DevastatorEffectRenderer` 将所有 Bullet 的非 LAND 阶段直接跳过，空中没有世界几何。10 格落差、2 格/tick 下落时旧响应约 .145；世界 FOV 70 只增大约 1 度，Flight×.014 的模糊又乘较弱混合，在天空渐变上几乎看不见。仅凭 GPU 就绪或纯天空截图不能验证动态体感。

本机原 FIG 核对到 entry 的 `shockWave001`（127832）、cycle 的 motiontrails/fall、dash、loco/dash，以及 `MotionTrail` 128920 的三点 Spine_3/2/1。fall/dash 的有效三点是左右 Shoulder→Elbow→Wrist，第四槽不生效。原材质关联是 motionTrail007 / fxmotiontrail007.dds，未解出原生宽度、颜色、shader 或长期循环调度，生产没有复制该贴图或原始几何。

`BulletFlightVisualRenderer` 在真实 FLIGHT 阶段绘制原创方块式短气流、薄八角冲击环和实际骨点上的窄湿组织尾。气流位置由真实下落距离推进，在短周期渐隐的壳内保持世界位置，角色和自由镜头经过附近墙面或地面时能看到向上的相对流动。气流条数、宽度、组织色和有界尾长是 Minecraft 呈现适配。第一人称不绘制本地角色组织尾，世界气流仍在原生手臂绘制前完成，不能替代或修改手臂投影。

光学响应分别读取真实落差与格/tick 下落速度，保持有界；物理、能量和地形不使用这个饱和响应。新镜头读取 `DevastatorVisual` 的 release-age，绕过 body holdTime=2.15 的冻结时钟。source cycle Zoom=.6、In=2.5、Out=.166667 作为渐进视野变化依据；短俯冲的额外压力响应和延续至真实 LAND 属于适配。entry 冲击环用 release-age，cycle 用 release-age−1/6；原 sequencer 完整仲裁仍未知。

镜头仅调整 `ComputeFov.usedConfiguredFov=true` 的世界投影，尊重 FOV Effect Scale；原生手臂的 `getFov(...,false)` 直接跳过。没有 F5、鼠标、相机位置、yaw/pitch/roll 覆盖，也没有把源一次 large/medium shake 改成持续随机抖动。场景后处理读取相机方向，仅加强边缘的向上/径向流动，中心保留清晰；遵守 Distortion Effects 设置，仍以 alpha=1 输出世界 RGB，并保留 depth 保存、恢复和销毁后主缓冲重绑。

诊断提供世界几何 quad/vertex 数、真实速度/落差、source release/cycle 时钟、近环境距离，以及世界 FOV 实际输入/输出。纯数学检查证明气流的世界位置、短俯冲可见响应、source zoom 完成、用户效果关闭和真实 LAND 清理；最终需在纹理墙面、建筑或地面附近连续下落并真人回看，不能用静态天空图声称体感验收完成。
