import fs from 'node:fs';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const report=JSON.parse(fs.readFileSync(roots[0]+'/art/encounters/generic-ragdoll-terrain-validation.json','utf8'));
const total=Object.values(report.platforms).reduce((n,p)=>n+Object.values(p.runs).reduce((s,r)=>s+r.frames,0),0);
const section=`<!-- generic-ragdoll-support-start -->
### 通用模型与台阶支撑回归（2026-11-15，有限范围检查点）

- 新增真实皮肤覆盖范围的台阶支撑检测。原先只沿碎块中心向下探测，在中心悬空、边缘压在半砖上的情况下漏判；实机四条流程分别记录约 0.098、0.114、0.246、0.101 格的网格顶点嵌入。现在按实际网格面与附近方块碰撞形状的水平重叠区域计算支撑高度，并对仍有连接的整组身体统一修正，保留关节连接。
- 使用实际皮肤，而非包围盒空角；斜面先裁剪到真正覆盖的地面区域，避免用远处较低顶点错误抬升。高于身体的墙顶和顶棚不作为支撑；超出局部扫描预算的大型模型保留既有受限回退，不宣称全部大型模型地形已通过。
- 最新双版本共 ${total} 个原生截图帧、31 条场景（Neo 17、Forge 14），逐帧检查元数据并人工审查 31 页、每页 20 个选定关键帧。包含猪、牛、村民、骷髅、带成熟组织的感染宿主；Neo 另装 Ars Nouveau 5.13.3 检查 wilden_hunter 与 drygmy，Forge 本轮没有装外部模组，不能把 Neo 结果算作 Forge 的模组验收。
- 31 条场景的伤害、活体完整性、吸收提交、真实分段、附着组织绑定与平地落地断言通过。双版各四条半砖／阶梯／邻墙流程均未出现超过 0.02 格阈值的绘制顶点嵌入；测量只代表已采样顶点，不是任意面／边的连续碰撞证明。相关单元检查为双版各 47 项，其中新增 6 项覆盖边缘支撑、斜面覆盖、旋转模型空角、不同高度台阶、顶棚和分离网格岛。
- 人工审图仍见活牛的布娃娃恢复到实体动作时出现明显位置跳变，Forge 邻墙样本恢复期间还出现向上弹起。**这部分恢复连续性仍是待修缺陷**，不能以台阶支撑断言通过代替整套物理验收。还需检查任意侧墙、薄面高速碰撞、水中、大型模型、多人、Forge 外部模组以及所有生物的完整动作与技能流程。
- 证据：art/encounters/generic-ragdoll-terrain-validation.json；每平台 D:/CodexCache/creature-combat-qa/*-generic-ragdoll-frames 内的场景 JSON、case-checks.json 与截图。全部使用独立隐藏静音 QA 存档；本检查点未打包，完整目标继续进行。
<!-- generic-ragdoll-support-end -->`;
for(const root of roots)for(const file of ['docs/CREATURE_COMBAT_DESIGN_ZH.md','docs/CREATURE_COMBAT_SOURCE_AUDIT_ZH.md']){
 const p=root+'/'+file;let s=fs.readFileSync(p,'utf8');s=s.includes('<!-- generic-ragdoll-support-start -->')?s.replace(/<!-- generic-ragdoll-support-start -->[\s\S]*?<!-- generic-ragdoll-support-end -->/,section):s.trimEnd()+'\n\n'+section+'\n';fs.writeFileSync(p,s);
}
console.log('Recorded support fix and explicit remaining live recovery defect.');
