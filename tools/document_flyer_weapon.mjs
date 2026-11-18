import fs from 'node:fs';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const r=JSON.parse(fs.readFileSync(roots[0]+'/art/encounters/flyer-weapon-validation.json','utf8'));
const count=r.platforms.reduce((n,p)=>n+p.totalFrames,0),gap=Math.max(...r.platforms.map(p=>p.scenes[4].maxSkinToReceiverDistance));
const section=`<!-- flyer-weapon-receiver-start -->
### 飞行体真实武器受击：普通命中通过，重击物理未通过（2026-11-15）

- 实际运行利爪普攻、刀刃普攻、锤拳普攻和锤拳终结攻击，并保留一组利爪终结挥空。使用真实玩家形态和 WeaponCombatSystem 时序，没有用直接扣血代替动作命中。
- 查明并修复：有效武器伤害刚触发原作前／后受击片段，硬直禁用 AI 后的 Goal.stop 与 cancelAttack 又把它清成振翅待机。现在取消攻击保留受击阶段，硬直期间推进受击—恢复—待机；再次有效命中可重启受击，并在原姿态间混合。普通受击判定和渲染使用同一套姿态；击退、生命值和无敌窗口保留。
- 双版本最终各 98 项战斗 GameTest 及 35 项接触单元检查通过。本轮共 ${count} 张原生截图，十张接触审查页已查看；普通三种形态分别实际扣 40／45／55 生命，创口血效跟随受击表面并有一次接触声音，挥空没有成功命中回执。各动作退出硬直并回到待机。证据为 \`art/encounters/flyer-weapon-validation.json\`。
- **重击物理未通过**：锤拳终结实际扣 70 生命，客户端身体进入布娃娃后保持一个连接组件，未发生切割；但服务器飞行载体仍无重力，受击判定继续走飞行骨架。原生可见皮肤到判定表面最大相距 ${gap.toFixed(2)} 格，并在物理结束时跳回载体。这是已复现的缺陷，不能被服务器测试通过或普通受击通过掩盖。
- 下一项必须统一活体物理姿态与服务器接收表面，验证落地碰撞、后续攻击、受击特效锚点和回收过渡，再继续抓取／吸收／致死切割。不能通过关闭重击布娃娃或扩大隐形判定来视作修复。完整生物验收与最终双版本打包仍未完成。
- 验证过程边界：新增受击测试最初因远离假玩家的生物被清除而失败，已为测试实体设持久化；旧主宰悬空砸地测试曾在世界延迟 25 tick 时未能开始，现等待实体实际运行 25 tick，未改主宰生产行为。第一次武器录制的相机方向错误，该次图片不用于验收；已修复独立相机并重新录制。常规接收报告是先前检查点，其哈希及 97 项计数保留为历史记录。
<!-- flyer-weapon-receiver-end -->`;
for(const root of roots){
 for(const file of ['docs/CREATURE_COMBAT_DESIGN_ZH.md','docs/CREATURE_COMBAT_SOURCE_AUDIT_ZH.md']){
  const path=root+'/'+file;let s=fs.readFileSync(path,'utf8');s=s.includes('<!-- flyer-weapon-receiver-start -->')?s.replace(/<!-- flyer-weapon-receiver-start -->[\s\S]*?<!-- flyer-weapon-receiver-end -->/,section):s.trimEnd()+'\n\n'+section+'\n';fs.writeFileSync(path,s);
 }
 const path=root+'/art/encounters/creature-source-comparison.json',matrix=JSON.parse(fs.readFileSync(path,'utf8')),flyer=matrix.species.find(x=>x.id==='flyer');
 if(!flyer.runtime.includes('Ordinary weapon receiver fixed:'))flyer.runtime+=' Ordinary weapon receiver fixed: AI stun no longer erases source hurt; repeat hits restart/blend. 98 GameTests per platform, 1052 native weapon frames; flyer-weapon-validation.json.';
 flyer.next='FAILED heavy physics: connected client body separates from no-gravity server carrier / source hurt surfaces by up to 6.62 blocks, then snaps back. Unify physical pose and authoritative receiving geometry, including floor contact, follow-up hits and wound anchors. Then grab/consume/lethal, multiplayer/crowds/legacy/mod matrix and original audio. Whole species is not complete.';
 fs.writeFileSync(path,JSON.stringify(matrix,null,2)+'\n');
}
for(const f of ['record_flyer_weapon_validation.mjs','document_flyer_receiver.mjs','document_flyer_weapon.mjs','review_flyer_weapon.py'])fs.copyFileSync(roots[0]+'/tools/'+f,roots[1]+'/tools/'+f);
console.log('Recorded ordinary reaction acceptance and explicit heavy-physics failure in both repositories.');
