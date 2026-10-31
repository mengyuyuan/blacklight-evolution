# Military.2 原生分层兵模工作文件

这是本项目原创的 P2 风格适配。没有下载、提取原作模型或贴图；具体颜色、护具型号与原作最终外观未完成逐项视觉核实，不作为严格复刻。

## 编辑与重现

- `build_soldiers.py`：可编辑几何、材质、制服迷彩与脸部绘制。默认只生成本目录草稿；`--install --platform neoforge`、`--install --platform forge` 分别写入对应端的两个生产 Java 文件与两张生产贴图。`--platform all` 会同时写两端，构建期间不要运行。
- `material-map.json`：逐块几何、UV、材质和语义的可读清单，由上述脚本导出。编辑权威是 Python 脚本；修改导出清单不会自动回写模型。
- `BlackwatchModel.java` / 两个 Renderer 草稿：用于复查。生产权威位于 `src/main/java/`，不是本目录的草稿。
- `military_soldier_layered.png` / `blackwatch_soldier_layered.png`：完整 512×512 RGBA 像素贴图，可直接在图像编辑器中编辑。游戏模型使用 256×256 UV 单位，因此每个模型 UV 单位有 2×2 像素；两方独立上色，不经过运行时阵营染色。
- `preview_soldiers.py`：默认读取**生产 Java 的 piece(...) 调用**和**生产 PNG**，生成八张大图、离线 HTML 查看器与来源 SHA-256 清单。`--staged` 可在接入生产前检查本目录草稿。
- `inspect_soldiers.py`：读取生产资产，核查 UV 岛分离、贴图尺寸、各面不透明、脱装完整基础层与空手隐藏枪模；结果写 `inspection.json`。这是静态资产核查，不代替客户端。

运行环境为 Python 3 + Pillow + NumPy，无联网、无 Blender、无原版游戏资产依赖。可使用本机已有 Python 环境。

```powershell
python art/military2/build_soldiers.py
python art/military2/preview_soldiers.py --staged
python art/military2/build_soldiers.py --install --platform neoforge
python art/military2/preview_soldiers.py
python art/military2/inspect_soldiers.py
```

HTML 完全离线，无 CDN、外链脚本、服务端或浏览器登录依赖。支持拖动旋转、三视图角度、六套外观、四种枪型及空手、低位持枪/瞄准/简化换弹。PNG 与 HTML 都明确标注非实机。需要单独检查 HTML JavaScript 语法时，可运行 `preview_soldiers.py --check-js` 后对临时 `viewer-check.js` 使用 `node --check`，检查结束可删除该临时文件。

## 骨架与交互边界

保留 Humanoid 的 head/body/四肢名字与原始枢轴；装备使用各身体部件的 `equipment` 子节点，军衔与枪械使用独立子节点。`MilitarySoldier.equipped()` 隐藏护具、军衔与枪械，基础制服/面部/靴始终保留。主手必须是真实 `FirearmItem` 才显示枪模；步枪与卡宾枪共享步枪族，机枪、榴弹与肩扛发射器另有轮廓。四种肩扛弹种仍共用发射器外壳。

保持实体碰撞、抓取与既有移动规则；没有单独肘关节或完整手指 IK，换弹与射击仅作简化姿态。抓取/硬直时模型跟随原骨架，但是否所有摄像距离和动作都无穿插仍需客户端实机检查。没有新增独立可穿戴的护甲物品；穿脱状态由本轮实体装备管理接口驱动。

## Military.3 军官与队长

`build_soldiers.py` 的 `command_geometry()` 在原 110 块几何之后追加两方角色装具，原 UV/纹理区域保持原位。模型以 `MilitarySoldier.commandRole()` 选择 TROOPER、TEAM_LEADER、OFFICER 或 COMMANDER 外观，八级真实军衔仍独立显示。每个身体节点有独立 `role_team_leader` / `role_officer` / `role_commander` 子节点。角色/全装状态每帧重置，不根据渲染器注册时的实体名字固定身份。

- 队长：头部和双臂识别条、附加通信组件与电台。
- 军官：军方檐帽或 BW 指挥兜帽、肩章、指挥携行包、地图袋与腕式终端。BW 保留原密封面罩与双镜片。
- 指挥官：进一步增加肩领识别、短式指挥外套、后背识别与帽具细节。
- 军官/指挥官替换常规头具外壳和巡逻背包，避免在原头盔/背包外继续堆叠；脱装隐藏所有角色装具，保留基础制服。

`preview_officers.py` 读取生产源码与 PNG，生成六种身份的正、侧、背、制服、持枪五视图以及独立离线 HTML。`inspect_officers.py` 检查六身份 × 八军衔、全装/制服/空手与 BW 呼吸器；结果写 `officers-inspection.json`。

```powershell
python art/military2/preview_officers.py
python art/military2/inspect_officers.py
```

`military2-compatibility.json` 是在 military.3 安装前，以原 military.2 生产源码/PNG 对候选资产做的兼容证明：原 110 块几何/UV、原贴图占用区像素，以及原六套外观的纹理采样渲染均一致。它是已完成的基线证据；现在不要再运行 `inspect_officers.py --baseline`，该入口仅用于仍是 110 块原生产模型的安装前阶段。
