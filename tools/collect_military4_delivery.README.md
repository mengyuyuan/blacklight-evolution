# military.4 本地收集器

脚本只读取两端当前生产构建、QA证据、制作资料和Git工作树，在 `F:/MC建筑` 下创建新的独立交付目录与ZIP。它不会运行Gradle、修改PCL/用户存档、提交/推送或公开上传。默认输出 `military-feedback-preview-20261025`，已有目录、staging、ZIP和校验文件一律拒绝覆盖。

两端脚本内容相同，默认工作区为脚本上两级目录。在任一工程执行：

```powershell
# 所有门槛均完成后，先只读核查；不建staging或交付ZIP
python tools/collect_military4_delivery.py --check-only

# 确认输入已冻结后实际收集
python tools/collect_military4_delivery.py

# 需要新名称时显式给工作区内、两仓库外的全新目录
python tools/collect_military4_delivery.py --output F:/MC建筑/military-feedback-preview-20261025-revision2
```

## 必须具备的实测证据

- 当前版本 `0.3.4-preview.20261024-military.4` 的正常bundled主JAR，两端JAR各一个，无GameTest或ServerAudit类。
- Neo 554、Forge 551项实际JUnit XML，零失败、错误和跳过。
- `.superpowers/military4-qa/{neo,forge}-regression.log` 各111/111 required passed，且有正常保存退出和 `BUILD SUCCESSFUL`。
- Neo `.superpowers/military4-qa/neo-terrain-final.log` 10/10通过，证明最后的跨维度/方块夹具版本已实际运行。
- `.superpowers/military4-server-{platform}-final/city-military-audit.json`：revision4、City维度、PASS，实际80个原UUID、无关Mob至少80、两名初发守军、射线玻璃/木板、所有磁盘重启标记为true。
- Neo `.superpowers/military4-world-server-neoforge/city-military-audit.json`：同样完整通过，实际维度为 `blacklight:world`、生成器为 `BlacklightWorldGenerator`。Forge不存在该大世界，不伪造此项。
- `.superpowers/military4-upgrade-{platform}/MILITARY3-BASELINE.json` 与新 `city-military-audit.json`：原始M3来源文件哈希未变；新结果为PASS且时间晚于原记录、文件哈希不同，guard UUID、角色/装备/弹量、单位、指挥官、标记和箱子位置保留。
- 上述迁移日志 `.superpowers/military4-qa/{neo,forge}-upgrade-phase1.log` 含当前military.4版本、实际审计PASS及正常Gradle结束；未运行的原M3 PASS副本会被拒绝。
- `.superpowers/military4-qa/{neo,forge}-release-verify.log` 为当前正常发行核验的JSON，253个动作及原仓库动作完整；脚本还会只读再次执行现有 `package_release.py --profile bundled --verify-only`，要求结果相同。
- `docs/制作资料/data/baseline.json` 的 `final_source_freeze=true`。通过现有归档器 `--verify` 校验544份来源快照/当前源、预览输入与图片哈希、PNG完整性、相对链接和真实条目数；脚本不刷新档案。
- 最终实施记录、独立审阅记录（文件名匹配 `MILITARY4*FINAL*REVIEW*.md`）与military.2/3/4玩家指南。

所有输入门槛先于staging创建。收集过程中再次核查旧包、JAR、工作树和资料哈希；若此时失败，保留staging供排查，不自动递归删除或重写，下一次应使用新的输出名。

## 交付内容与边界

保存两端完整主JAR、全部未提交military.1至military.4覆盖快照（含PATCH-BASE、删除清单、逐文件哈希）、tracked diff、全部military4-qa的本地QA日志/辅助JSON/TXT、原JUnit XML、City/World审计及M3迁移基线、完整Neo文档树和制作资料。文档树完整保留，旧测试指南、预览和spec的相对链接仍可用；参考BB原件、作者许可裁图与用户故障截图作为已获准的档案资料保留，未原样复制用户完整私人PCL日志。

`MANIFEST.json` 记录文件清单和真实验证结果；`SHA256SUMS.txt` 含清单及MANIFEST本身的SHA。新ZIP以排他方式创建，运行CRC完整性核查并写独立 `.zip.sha256`。三个旧military.1/.2/.3 ZIP在收集前后保持规定哈希。

本收集器验证文件与已有实测证据，不替代客户端GL外观、鼠标输入和真实游戏手感验收；离线预览不能被称为游戏截图。部分QA失败日志会随成功证据保留，以便追溯已修复缺陷和夹具问题。
