import fs from 'node:fs';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const r=JSON.parse(fs.readFileSync(roots[0]+'/art/encounters/flyer-physics-validation.json','utf8'));
const count=r.platforms.reduce((n,p)=>n+p.frames,0),error=Math.max(...r.platforms.flatMap(p=>p.scenes.map(s=>s.skinToReceiverMax)));
const section=`<!-- flyer-live-physics-start -->
### 飞行体活体重击、命中表面与落地反馈（2026-11-15，阶段验收）

- 上一检查点的 6.62 格偏离已修复。活体飞行体重击改为服务端连接关节物理，载体随身体根部移动；Gecko 骨架、原生部位接收面和创口锚点使用同一姿态。普通命中仍播放已核对的 P2 前／后受击曲线；致死和吸收仍保留既有皮肤切割入口，未用关闭布娃娃或扩大隐形判定代替修复。
- 物理在局部坐标计算，修复远坐标下矩阵丢失精度导致陷地的问题。身体和根部位置采用同一快照时钟；旋转翅膀在帧间的地形接触也会约束整具身体，并用于同一时刻的接触查询，避免恢复途中穿地。合并连续共面的方块碰撞体，消除地面接缝的水平推挤，保留洞口和台阶。停顿端点直接保留快照，不反复转换四元数造成漂移。
- 实际接触方块后才调用与玩家重拳共用的 TerrainImpactEffects，按接触方块产生碎屑、落地声和震动；武器命中血花与冲击回执仍落在身体表面。生命未耗尽时不产生切割。
- 双版本各 **100 项战斗 GameTest、37 项接触单元检查通过**。新增验证包括服务端身体下坠、倒地翅膀被真实箭矢补射、接触处血效、停顿恢复、连续受力不重建身体、服务端致死交接保留姿态。最终 ${count} 张原生截图；十张武器接触页和两张落地／恢复页已逐格审查。每帧复核实际烘焙模型的 63 个方体、1512 个绘制顶点，皮肤与接收面最大差 ${error.toFixed(6)} 格；重击保留连接身体、落地后恢复飞行，挥空无成功回执。证据：art/encounters/flyer-physics-validation.json。
- **验证边界**：这套关节求解与恢复时长是 MC 实现，不能据此宣称已解码或完全复现 P2 物理常量。Gecko 世界矩阵会剥离渲染器外层平移，顶点复核明确重新加入同一外层平移；原生截图是独立画面证据。一次停顿端点检查失败时伴随多个后续实体回归失败，修复后完整回归已重跑通过。另将既有无人机呼叫测试改为跟踪本次呼叫的实际时钟及所属无人机，避免固定世界延迟和附近独立测试实体混入判断；未改军方生产逻辑。Forge 一次构建因本机内存不足中止，后续降低独立 QA 内存和并发预算后重跑。
- **粒子复核**：旧 Forge 弱粒子截图源于测试镜头朝向未同步；修正观察器后原生命中粒子可见，生产代码另统一了扬尘参数，见 contact-particles-validation.json。100／37 项回归是前一物理检查点；本次粒子修改只重跑两版原生客户端及全帧皮肤／回执检查。
- **仍须完成**：从此倒地状态进入抓取／保持／吸收与致死切割的原生逐帧交接，高速薄台阶与狭窄墙体的连续碰撞，多人快照时序、多身体并发及模组矩阵，尚未确认的原作音频。上一节“重击未通过”是历史检查点；本节仅解决已实测的重击场景，整个飞行体与全生物目标仍未全部验收，不提前打包。
<!-- flyer-live-physics-end -->`;
for(const root of roots){
 for(const file of ['docs/CREATURE_COMBAT_DESIGN_ZH.md','docs/CREATURE_COMBAT_SOURCE_AUDIT_ZH.md']){
  const path=root+'/'+file;let s=fs.readFileSync(path,'utf8');s=s.includes('<!-- flyer-live-physics-start -->')?s.replace(/<!-- flyer-live-physics-start -->[\s\S]*?<!-- flyer-live-physics-end -->/,section):s.trimEnd()+'\n\n'+section+'\n';fs.writeFileSync(path,s);
 }
 const path=root+'/art/encounters/creature-source-comparison.json',matrix=JSON.parse(fs.readFileSync(path,'utf8')),flyer=matrix.species.find(x=>x.id==='flyer');
 flyer.runtime=flyer.runtime.replace(/ Server-owned live flyer physics:[\s\S]*$/,'')+` Server-owned live flyer physics: connected heavy body and receiving surfaces now share articulated snapshots, including terrain-constrained interpolation and confirmed landing effects. 100 GameTests and 37 contact unit tests per platform; ${count} native frames. See flyer-physics-validation.json.`;
 flyer.next='Native grab/hold/consume/lethal-cut handoff from the synchronized downed body; high-speed thin slabs, stairs and narrow walls; multiplayer/crowds/legacy/mod matrix and remaining original audio. Existing heavy finisher flat-floor scenario passed; whole species remains incomplete.';
 fs.writeFileSync(path,JSON.stringify(matrix,null,2)+'\n');
}
for(const file of ['record_flyer_physics.mjs','document_flyer_physics.mjs','review_flyer_weapon.py','sync_flyer_physics.mjs'])fs.copyFileSync(roots[0]+'/tools/'+file,roots[1]+'/tools/'+file);
console.log('Recorded bounded physics acceptance and remaining native handoff/contact work.');
