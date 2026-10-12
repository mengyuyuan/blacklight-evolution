## 目标版本与分支

可先运行 `python tools/version_workflow.py prepare --target 目标分支 --topic 简短主题` 自动生成平台、base 与同步清单；将生成文件替换整份正文，再填写问题和验证结果。

- Minecraft / 加载器 / Java：
- 目标 base 分支：
- [ ] 从对应长期版本分支开发，base 正确；本 PR 的差异仅包含本次改动。

## 改动解决的问题

## 修改后的行为

## 来源与跨版本同步

- 来源 PR / 实际合入提交 SHA（首次实现填“无”）：
- 同步跟踪 Issue（无跨版本影响可填“不适用”）：
- 其他维护版本的影响、同步 PR / 计划或不适用原因：

按 `docs/versions.json` 逐版本判断。一个目标版本一个 PR；“已合并”和“已发布”分别记录。不要整条版本分支互相合并。

## 验证

- [ ] `assemble` 编译打包通过
- [ ] 实际执行的验证、已有失败与未验证项已如实列出（纯文档标明无需编译）。
- [ ] 涉及动作或模型时，提供可编辑源文件，并注明已有实际效果材料或尚未试玩

协议 / 存档兼容与迁移影响（无则填“无”）：

默认仅编译打包。单元测试、碰撞测试、GameTest 和游戏内验收仅在用户明确要求时运行；未运行的检查应标为未验证。

实际执行的验证及尚未验证的部分：

## 素材来源与许可（如涉及）

协作流程：[多版本开发、同步与 PR 规范](https://github.com/mengyuyuan/blacklight-evolution/blob/main/docs/MULTIVERSION_WORKFLOW_ZH.md)。

<!-- blacklight-sync
{"scope":"review"}
-->
