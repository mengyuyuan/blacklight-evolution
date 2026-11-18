import fs from 'node:fs';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const r=JSON.parse(fs.readFileSync(roots[0]+'/art/encounters/contact-particles-validation.json','utf8'));
const count=r.platforms.reduce((s,p)=>s+p.frames,0),forge=r.platforms.find(x=>x.platform==='forge');
const section=`<!-- contact-particles-start -->
### 命中粒子与真实触地扬尘复核（2026-11-15，阶段验收）

- 更正上一观察：Forge 的白色命中烟粒并未停止生成或提交绘制。隐藏测试观察器只通过 ComputeCameraAngles 转动画面，Camera.rotation 仍取盔甲架原朝向，导致相机朝向粒子不可见。现在测试观察器同步真实相机旋转；绘制探针记录实际 SingleQuadParticle.render 调用、粒子队列、透明度、视图矩阵及相机四元数，禁止用只统计包数代替画面确认。修正后 Forge 粒子平面与显示视图的最大非轴向误差 ${forge.forgeCameraOffAxisMax.toExponential(3)}，普通重拳白色命中烟粒重新可见。没有给生产渲染器加绕过深度或关闭裁剪的补丁。
- 真正的版本差异另外修复：1.20.1 先前以 POOF 替代 DUST_PLUME；双版本现在注册同一 contact_dust，并在原有七处地面扬尘入口使用相同升起速度、衰减、尺寸、寿命和帧序列，覆盖重拳、刀刃、利爪、移动触地以及生物触地反馈。方块碎屑继续使用接触方块的状态；没有更改伤害值、地形破坏规则或将落地效果提前到蓄力完成。
- 两版最终共 ${count} 张原生截图，逐帧核对粒子与身体数据，人工检查十页武器接触图及两页落地恢复图。各有四次成功接触、一次挥空；利爪／刀刃伤口血花、普通锤击烟粒、重击连接身体下坠、触地扬尘、方块碎屑和恢复飞行可见。每版落地帧实际绘制 24 个扬尘粒子、48 个地表碎屑粒子，地面回执有 48 个真实表面样本和 2 个声音事件。扬尘出现在已确认触地回执之后；挥空没有成功命中回执或粒子。
- 证据：art/encounters/contact-particles-validation.json；每版帧目录内 evidence.json 与六张 review JPG。双版本原生客户端本轮重新编译并运行，物理皮肤／接收面、存活完整性、落地时序检查也用新帧重算。100 项服务器战斗检查和 37 项接触单元检查沿用前一物理检查点，本轮未重复运行，不能将其写成此次重新执行的结果。
- 边界：此扬尘是参考本机 Minecraft 1.21.1 粒子参数的跨版本表现适配，不是声称已提取《虐杀原形》的扬尘常量。截图也没有证明其它七处入口的所有场景、光影包和各模组均已验收。下一项仍是飞行体倒地后的抓取、保持、吸收与致死切割交接，随后验证高速薄障碍、多人及并发；完整生物目标继续，不提前打包。
<!-- contact-particles-end -->`;
for(const root of roots){
 for(const file of ['docs/CREATURE_COMBAT_DESIGN_ZH.md','docs/CREATURE_COMBAT_SOURCE_AUDIT_ZH.md']){
  const path=root+'/'+file;let s=fs.readFileSync(path,'utf8');s=s.includes('<!-- contact-particles-start -->')?s.replace(/<!-- contact-particles-start -->[\s\S]*?<!-- contact-particles-end -->/,section):s.trimEnd()+'\n\n'+section+'\n';fs.writeFileSync(path,s);
 }
 const path=root+'/art/encounters/creature-source-comparison.json',matrix=JSON.parse(fs.readFileSync(path,'utf8')),flyer=matrix.species.find(s=>s.id==='flyer');
 flyer.runtime=flyer.runtime.replace(/ Native particle follow-up:[\s\S]*$/,'')+` Native particle follow-up: ${count} fresh frames, shared contact_dust profile, confirmed tissue/impact/ground layers, corrected disposable observer billboard orientation; see contact-particles-validation.json.`;
 flyer.next='Native downed-flyer grab/hold/consume/lethal-cut handoff; high-speed thin slabs, stairs and narrow walls; multiplayer/crowds/legacy/mod matrix and remaining original audio. Particle rendering and shared ground plume were verified in actual flyer weapon/landing scenes; whole species remains incomplete.';
 fs.writeFileSync(path,JSON.stringify(matrix,null,2)+'\n');
}
console.log('Recorded particle diagnosis, shared plume, native evidence, and remaining scope.');