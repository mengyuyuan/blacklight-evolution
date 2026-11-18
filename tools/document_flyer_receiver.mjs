import fs from 'node:fs';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const report=JSON.parse(fs.readFileSync(roots[0]+'/art/encounters/flyer-receiver-validation.json','utf8'));
const section=`<!-- flyer-receiver-start -->
### 飞行感染体接收表面与命中特效（2026-11-15）

- 接收判定随同一套原作飞行／受击曲线驱动现有 63 个方块；翼膜空角不会挡住后方目标，原生瞄准、普通剑、飞箭及利爪／刀刃／锤拳的实际表面接触共同进入本体生命与无敌判定。代理部位不另设血量、掉落、存档或物理免疫。修复出生／传送时把旧坐标零点误当成扫掠起点的问题。
- 只有成功扣血才发送对应部位的血花和接触声音；创口在最初四 tick 跟随原动画表面，飞离的血滴自由运动。射空、墙挡和无敌不发成功命中特效。此项是按既有模型做的 MC 接收适配，并非解出了原版部位血量表。
- 两版各通过 97 项实战 GameTest、35 项接触单元检查。当前原生截图：接收 723 帧（Neo 362 / Forge 361），对外俯冲回归 1822 帧（Neo 902 / Forge 920）。接收六种场景的 12 张逐帧审查页均已查看；对外回归另查看两版命中、挡墙及撞地六页。接收骨骼位置最大误差 0.000030 格，三次有效命中每版均只扣一次 60 生命，特效回执无提前，最大延迟 0.81 tick。
- 以上截图使用原生投射物；真实形态武器的服务器接触已测，但物理倒地／抓取／吸收／致死切割组合仍未验收。已发现武器硬直分支可能重置原作受击阶段，下一轮专门录制真实武器时序并修复，不据此宣称该生物完整完成。多人延迟、密集飞行、旧武器路径、模组矩阵及原始音色仍待验收。

证据：\`art/encounters/flyer-receiver-validation.json\`；原生截图在 \`D:/CodexCache/creature-combat-qa/{neo,forge}-flyer-hurt-frames\`。保留总目标继续，不生成最终发布包。
<!-- flyer-receiver-end -->`;
for(const root of roots){
 for(const file of ['docs/CREATURE_COMBAT_DESIGN_ZH.md','docs/CREATURE_COMBAT_SOURCE_AUDIT_ZH.md']){
  const path=root+'/'+file;let s=fs.readFileSync(path,'utf8');s=s.includes('<!-- flyer-receiver-start -->')?s.replace(/<!-- flyer-receiver-start -->[\s\S]*?<!-- flyer-receiver-end -->/,section):s.trimEnd()+'\n\n'+section+'\n';fs.writeFileSync(path,s);
 }
 const path=root+'/art/encounters/creature-source-comparison.json',matrix=JSON.parse(fs.readFileSync(path,'utf8')),flyer=matrix.species.find(x=>x.id==='flyer');
 if(!flyer.runtime.includes('Receiving-surface checkpoint:'))flyer.runtime+=' Receiving-surface checkpoint: native picking/projectiles, sword and actual Prototype server weapon sessions; 97 GameTests and 35 unit checks per version, 723 incoming and 1822 outgoing native frames; flyer-receiver-validation.json.';
 flyer.next='Real weapon native frame/contact agreement under live physics, interrupted source-hurt phase, grab, consume and lethal cutting; multiplayer/crowds/legacy/mod matrices and original audio remain pending. Receiving proxies do not establish physical takeover correctness.';
 fs.writeFileSync(path,JSON.stringify(matrix,null,2)+'\n');
}
for(const file of ['record_flyer_receiver_validation.mjs','sync_flyer_receiver.mjs'])fs.copyFileSync(roots[0]+'/tools/'+file,roots[1]+'/tools/'+file);
// Keep only the images named in the current evidence, never traverse a directory.
for(const platform of ['neo','forge'])for(const type of ['flyer','flyer-hurt']){
 const dir=`D:/CodexCache/creature-combat-qa/${platform}-${type}-frames/`,e=JSON.parse(fs.readFileSync(dir+'evidence.json','utf8')),keep=new Set(e.frames.map(f=>f.image));
 const absolute=fs.realpathSync(dir).replaceAll('\\','/').toLowerCase();if(!absolute.startsWith('d:/codexcache/creature-combat-qa/'))throw Error('Unexpected cleanup root');
 let count=0;for(const ent of fs.readdirSync(dir,{withFileTypes:true}))if(ent.isFile()&&/^\d+-.*\.png$/.test(ent.name)&&!keep.has(ent.name)){fs.unlinkSync(dir+ent.name);count++;}
 fs.writeFileSync(dir+'status.txt',type==='flyer-hurt'?'Validated source-pose receiving matrix; physical/consume takeover pending':'Validated outgoing regression; whole species acceptance pending');console.log(platform,type,'obsolete frames removed',count);
}
