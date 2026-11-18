import fs from 'node:fs';
const roots=['D:/blacklight-evolution','D:/blacklight-evolution-forge-1.20.1'];
const report=JSON.parse(fs.readFileSync(roots[0]+'/art/encounters/flyer-handoff-validation.json','utf8'));
const frames=report.platforms.reduce((sum,p)=>sum+p.frames,0);
const grip=Math.max(...report.platforms.flatMap(p=>p.cases.map(c=>c.stableGripMax||0)));
const transient=Math.max(...report.platforms.flatMap(p=>p.cases.map(c=>c.gripMax||0)));
const first=report.firstHoldWithinBound?'本轮包含首帧的全部抓持样本均在 0.1 格检查界限内':'首帧仍有超出 0.1 格检查界限的样本，未通过';
const section=`<!-- flyer-handoff-start -->
### 飞行体抓取、吸收与致死切割交接（2026-11-15，阶段检查点）

- 抓取位置改用当前飞行体头部／躯干的实际表面，渲染和抓取位置共用接收姿态。水平躯干朝向抓取者，间距按躯干宽度计算，避免把整片翅膀当成实体躯干而将抓点推到手臂够不到的位置。翅膀使用解包曲线 flyer_com_idle 的本体收翼链，不再把米级翼膜直接套入人形前臂旋转。头部和躯干、双腿仍接收共用交互轨道；这是 MC 骨架适配，不是声称已复原原作完整飞行体吸收动作。
- 双版原生隐藏静音客户端合计 ${frames} 帧，五场景均实际经历锤击倒地，再进行利爪横切吸收、刀刃吸收、利爪捏头吸收、活体释放或刀刃斩杀。进入 HOLD 一 tick 后，真实绘制的武器抓点到原生烘焙网格三角面最大距离为 ${grip.toFixed(4)} 格；含切换首帧最大值为 ${transient.toFixed(4)} 格，${first}。QA 专用 mixin 在 BlacklightClawLayer.renderMesh 内记录当帧模型矩阵和腕部变形；每个抓持帧都验证探针同帧、武器完全生长且非液化。帧后重采样只保留作诊断，不作为验收依据。距离可能包含锚点位于皮肤内的情况，也不代表整个吸收过程和所有模组抓持都已通过。
- 绘制探针发现刀刃抓点与旧腕骨测量不一致：刀刃模型固定在前臂，利爪才接受腕部变形。刀刃现在按实际前臂抓点求解，转换时保留原始锚点，防止对同一坐标重复去除绑定变换。飞行体模型和抓点查询也在配对状态到达时立即采用配对朝向，避免等待实体转向插值后再突然翻转身体。
- 抓取及保持阶段没有启动碎块渲染；利爪横切和刀刃吸收出现实际新断面，捏头流程没有凭空增加刀刃切面。释放后目标仍存活并恢复飞行。致死场景通过第一次真实锤击自然耗掉濒死吸收保留机会，再由真实刀刃接触完成死亡，未注入强制死亡或切割调用。回执、鲜血、红色受击和物理碎块均由实际链路产生。
- 致死测试复现了客户端世界时间同步回拨导致 RagdollPoseHistory 抛异常的崩溃。现在回拨时清空失效的旧时钟姿态，再记录当前身体；不再把未来皮肤位置用于迟到的切割路径。Forge 中吸收后仍短暂绘制的残骸也不再查询已注销实体的抓持能力。两版各执行 47 项相关单元检查，包含时钟回拨、旧位置不可切、新历史插值、碎块继承、扫刃、关节约束和腕部不同旋转下的刚性刀刃接触，保持臂长不变。另修复客户端时钟回拨导致尸体长时间保持红色的问题：闪伤按剩余时长结束，回拨不延长也不复活已结束的红色覆盖；真实命中仍保留短促变红。
- 吸收切割改用与实际绘制相同的关节闭合和整组地面抬升结果，避免在修正前的单独骨块位置判定刀刃接触。单场诊断确认旧位置可偏离绘制位置约 1.16 格；双版完整流程新增同帧几何核对，结果见报告 cutQueryToDrawMax。统计 cuts 指被断面切开的模型部件数，不代表刀数或独立切面数。此修正统一查询与绘制的位置，不等于整段吸收动作或最终分段已经达到目标观感。
- 本轮直接在一代 alex_fig.p3d.rz 的解压数据偏移 498548、498668 核对到 0.833333 秒的两条事件；名称与归属按既有解包审计解析。两条原作碎块生成事件不等于两次整身切面。刀刃适配保留该时刻，按实际锋利轮廓求解持刀手臂，保持臂长、腕部、左臂和根骨不变；同一刀刃两侧可分别提供真实接触，但只完成一次断面，利爪各爪刃仍独立。两版均检查首切时真正 torso 骨骼形成正负两侧断面，而不再只以“发生过切割”作为通过条件。
- 切口喷血现在绑定实际断面的三角面与碎块速度：两侧分别喷出，并跟随翻转和分离。锋利吸收不再用计时器凭空在身体中心生成替代喷血；捏头等非切割效果保留。报告记录双侧喷血锚点到真实物理封口面的距离。
- 原动作姿态下新增的可达接触检查复现旧迭代求解的局部停滞，现改用保持臂长的双骨骼求解。错误的翼片根节点随后得到修正：最新双版斩断前刀锋到躯干中心距离分别约 ${report.platforms.map(p=>p.cases[1].bladeSection.preSeverEdgeToThoraxCenter.toFixed(3)).join("／")} 格（此前诊断约 1.5 格），见报告 preSeverEdgeToThoraxCenter。该距离是中心接近程度，不能替代逐帧皮肤接触证明；切割仍由已确认的真实接触授权，任意模型的整段连续贴合尚未验收。
- 通用物理根节点按骨架祖先关系选择，并在并列的独立骨块中优先躯干；不再仅按包围盒体积把大翼片设为躯干的父节点。新增宽翼、空骨骼中介、并列人形和异常循环层级检查，实机逐帧记录 rootBone 并要求本场景为 torso。
- 证据为 art/encounters/flyer-handoff-validation.json、两版帧目录 evidence.json 与各五页实机截图。此轮重新运行上述 47 项单元检查及五条原生流程；前一检查点的 100 项服务器回归与 41 项接触检查没有冒充为本轮执行。
- 仍未验收：本轮飞行体刀刃场景已确认躯干形成两侧断面，且没有模型两侧重复生成整身断面；这不代表任意模型或最终分段观感全部通过。接收者动作与物理驱动交接仍需检查；倒地进入抓取、保持转吸收的连续过渡；整个过程中所有翼膜／碎块与不平地形的接触；高速薄障碍、多人及并发、其它模组与剩余生物、原版音效。保留总目标，继续处理，不打包也不宣称整套布娃娃完成。
<!-- flyer-handoff-end -->`;
for(const root of roots){
 for(const file of ['docs/CREATURE_COMBAT_DESIGN_ZH.md','docs/CREATURE_COMBAT_SOURCE_AUDIT_ZH.md']){
  const path=root+'/'+file;let s=fs.readFileSync(path,'utf8').replace('其它七处入口的所有场景','其它入口的所有场景');
  s=s.includes('<!-- flyer-handoff-start -->')?s.replace(/<!-- flyer-handoff-start -->[\s\S]*?<!-- flyer-handoff-end -->/,section):s.trimEnd()+'\n\n'+section+'\n';fs.writeFileSync(path,s);
 }
 const path=root+'/art/encounters/creature-source-comparison.json',matrix=JSON.parse(fs.readFileSync(path,'utf8')),flyer=matrix.species.find(s=>s.id==='flyer');
 flyer.runtime=flyer.runtime.replace(/ Native handoff checkpoint:[\s\S]*$/,'')+` Native handoff checkpoint: ${frames} fresh frames, actual weapon-draw anchor to native skin triangles, rigid blade contact, synchronized receiver facing, source wing folds, live release, sharp absorption and real lethal body capture; 47 focused units per platform. See flyer-handoff-validation.json; transitions and full terrain/network acceptance remain open.`;
 flyer.next='Extend the verified two-sided native flyer thorax section to varied receiver rigs and sizes; smooth and verify physical knockdown→capture→hold→consume transitions, including terrain clearance and contact clocks; high-speed thin obstacles, network/crowds/mod matrix and remaining original audio. Whole species and active goal remain incomplete.';
 fs.writeFileSync(path,JSON.stringify(matrix,null,2)+'\n');
}
console.log('Recorded bounded flyer handoff checkpoint and remaining full goal.');
