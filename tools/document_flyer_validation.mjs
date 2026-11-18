import fs from 'node:fs';
import path from 'node:path';
const roots=['D:/blacklight-evolution/','D:/blacklight-evolution-forge-1.20.1/'];
const section=`
<!-- flyer-dive-start -->
### 飞行感染体俯冲与接触反馈（2026-11-15）

- 直接读取 P2 \`flyer.p3d\`，导出待机、振翅、滑翔、加速、冲撞、撞墙和前后受击等 9 段、334 帧；出生片段仅导出，普通刷怪蛋不使用。保留既有模型全部 63 个方块、旋转、UV 和贴图，将翅膀拆为肩—肘—腕骨链接入原曲线。没有重新制作外形，也没有把原版蒙皮直接搬入 Minecraft。
- 飞行体先抬升和准备，释放时确定方向，再加速俯冲；目标此后侧移可以躲开。身体及翼膜的实际旋转方块与生物、地形做扫掠接触；最近实体或墙面先处理。命中一次后拉起，撞墙中止并播放原始碰撞片段，受到有效伤害会取消正在释放的俯冲并接前／后受击动作。地面生成后可以起飞。
- 命中仍通过原生命、护甲与无敌判定。新增血效、冲击闪光和接触声音共用一个回执，并等待客户端可见身体接近接触点再一起播放；避免高速移动时特效提前约一格出现。挥空、取消和免疫不产生成功命中效果。撞墙用墙面材质，实际向下碰到地面才触发地面碎屑、尘土和冲击。
- 两版各通过 89 项战斗 GameTest（包含 9 项飞行专项）、24 项动作单元测试、35 项接触及九头蛇聚焦单元测试，共 148 项。主宰中断测试改为等待实体实际运行 25 tick，避免世界时间与实体预热不同步；没有因此改动主宰生产逻辑。
- 隐藏静音实例保留 1837 帧（1.21.1 918／1.20.1 919）；逐帧计算渲染骨骼与判定的误差，最大 9.316e-6 方块。已人工检查两版各六组时序审查页：命中、撞墙、侧移躲避、受击中断、地面起飞、触地。普通和地面起飞场景各仅造成一次 8.05 伤害，其余场景没有对目标造成俯冲伤害。
- 增强血效首次出现时与可见身体表面距离，两版分别不超过 0.149／0.085 方块；特效与声音回执最迟为服务端接触后 1.340／2.080 tick，无提前派发。MC 原生红色受伤染色仍可能先到；此处只验收新增接触效果的呈现门控，不声称全部网络表现零延迟。
- 原作证据：FIG 中两项不透明俯冲控制器位于 984801、985329，碰撞动画记录位于 985865，引用 idle、attackspeed、air_ram 和 surface_collision。控制器内未解析数值不赋予速度／伤害等语义；准备时间、速度、锁向、盘旋高度、伤害倍率和拉起时长均明确属于 MC 适配。未确认的旧喷吐分支不再用于该物种。
- 尚待验收：玩家攻击翼缘的精确接收、抓取／吸收及飞行死亡布娃娃组合、多人延迟、拥挤飞行性能、完整模组模型矩阵、原版音频 Patch 映射与音轨／音色。事件派发检查不是听感验收。留存画面为每游戏 tick 1–2 张，不等于逐张检查所有 60 FPS 显示帧。

证据：\`art/encounters/flyer-source-motion.json\`、\`flyer-dive-validation.json\`；原生截图在 \`D:/CodexCache/creature-combat-qa/{neo,forge}-flyer-frames\`。本项为飞行感染体对外俯冲子项验收，完整物种及总目标仍在进行，不打包最终双版本。
<!-- flyer-dive-end -->
`;
for(const root of roots){
 for(const name of ['CREATURE_COMBAT_DESIGN_ZH.md','CREATURE_COMBAT_SOURCE_AUDIT_ZH.md']){
  const file=root+'docs/'+name,old=fs.readFileSync(file,'utf8'),pattern=/\n<!-- flyer-dive-start -->[\s\S]*?<!-- flyer-dive-end -->\r?\n?/;
  fs.writeFileSync(file,pattern.test(old)?old.replace(pattern,section):old.trimEnd()+'\n'+section);
 }
 const file=root+'art/encounters/creature-source-comparison.json',data=JSON.parse(fs.readFileSync(file,'utf8'));
 const find=x=>{if(Array.isArray(x)){for(const y of x){const f=find(y);if(f)return f;}}else if(x&&typeof x==='object'){if(x.id==='flyer')return x;for(const y of Object.values(x)){const f=find(y);if(f)return f;}}};
 const flyer=find(data);if(!flyer)throw Error('Missing flyer comparison');
 flyer.sourceMapping='P2 flyer.p3d: 9 clips / 334 source frames, shoulder-elbow-wrist retarget. FIG controller references at 984801, 985329 and collision at 985865; opaque fields retain unknown semantics.';
 flyer.runtime='Source flight, committed accelerating dive, collision and front/back hurt. Swept authored body/wing cuboids, actual terrain contact, accepted damage once and visible-contact-gated blood/impact/audio. Existing 63 authored cubes preserved. Dual-version 148 checks each and 1837 native frames; see flyer-dive-validation.json.';
 flyer.next='Incoming wing-edge picking and weapon contact; flying ragdoll/consume combinations, multiplayer latency, crowded-flight performance and mod-model matrix. Original audio mapping/timbre and opaque dive AI remain unverified. Controller tuning is MC adaptation; species is not complete.';
 fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');
}
for(const platform of ['neo','forge']){
 const folder=path.resolve(`D:/CodexCache/creature-combat-qa/${platform}-flyer-frames`);
 if(!folder.startsWith(path.resolve('D:/CodexCache/creature-combat-qa')+path.sep)||!fs.statSync(folder).isDirectory())throw Error('Unsafe evidence directory');
 const evidence=JSON.parse(fs.readFileSync(path.join(folder,'evidence.json'),'utf8')),retained=new Set(evidence.frames.map(f=>f.image));let removed=0;
 for(const item of fs.readdirSync(folder,{withFileTypes:true}))if(item.isFile()&&/^\d+-\d+-\d+\.png$/.test(item.name)&&!retained.has(item.name)){fs.unlinkSync(path.join(folder,item.name));removed++;}
 fs.writeFileSync(path.join(folder,'status.txt'),'Validated outgoing dive submodule. Six scene contact sheets reviewed; retained frame bone/contact metrics passed. Full species acceptance remains pending.\n');
 console.log(platform+': retained '+retained.size+' frames; removed '+removed+' obsolete capture frames');
}
for(const file of ['record_flyer_validation.mjs','review_flyer.py','document_flyer_validation.mjs','sync_flyer.mjs'])fs.copyFileSync(roots[0]+'tools/'+file,roots[1]+'tools/'+file);
