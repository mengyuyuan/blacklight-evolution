# 多版本协作快捷工具

在项目根目录使用 Python 3.10+，工具只依赖标准库。运行 `python tools/version_workflow.py --help` 查看命令。完整规则见 [多版本规范](MULTIVERSION_WORKFLOW_ZH.md)。

## 一条命令生成 PR 草稿

```bash
python tools/version_workflow.py prepare --target forge-1.20.1 --topic held-turning --kind fix
```

默认只生成 `tmp/version-workflow/pr-body.md` 和同步候选清单，不切换分支、不推送。Minecraft、加载器、Java 和 base 从 `docs/versions.json` 自动填写；问题、改动行为与实际验证结果仍需填写。用生成文件替换整个 PR 正文，避免重复粘贴元数据块。

要同时从目标版本最新远程提交建立本地功能分支，补上 `--create-branch`。工具要求工作区干净，fetch 后建分支；已有同名分支时会失败，不覆盖现有工作。个人 fork 的主仓库远程通常为 `upstream`，可加 `--remote upstream`，默认使用 `origin`。

## 准备跨版本适配

下面用已合并的抓取修复 PR #5 作为示例；实际同步时换成自己的来源 PR 与完整合入 SHA：

```bash
python tools/version_workflow.py prepare --target forge-1.20.1 --topic held-turning --source-pr 5
```

工具从版本登记表的主仓库公开 GitHub API 读取已合并 PR，自动填写实际合入 SHA；尚未合并的来源会拒绝生成移植草稿。网络不可用或触发公开 API 限流时，可手动加上完整的 `--source-commit`，例如 PR #5 为 `5ba34fba697c20143e0a6e360c1d2138b57b48b3`；手动来源仍需核对与 PR 一致。

生成的分支使用 `backport/`，正文带有来源记录；CI 会识别这是现有改动的目标适配，不重新向来源版本派发反向同步任务。工具没有 cherry-pick、覆盖源码、推送或创建远程 PR 的操作；在生成的目标分支上完成适配，再按通常流程提交 PR。

首次改动默认 `--scope review`，其他维护版本显示“待评估”。已确认是共享改动时使用 `--scope shared`；确实只影响本平台时使用 `--scope platform --reason "具体原因"`。这些是作者的声明，仍需审阅，不自动证明其他版本没有影响。

## PR 的快速检查

`Version policy` 工作流对 PR 的创建、修改、更新和合并事件执行：

1. 从目标 base 提交读取版本登记，而不是信任 PR 自己改写的目标定义。
2. 核对 PR 中 `gradle.properties` 的 Minecraft / 加载器字段，以及 `build.gradle` 声明的 Java toolchain。匹配不了或 PR base 未登记时检查失败。
3. 在 Actions Summary 输出其他维护版本的同步候选，以及本次来源 PR / 合入 SHA。
4. 上传 `version-report` 的 JSON 和 Markdown，保存 30 天；PR 与 Git 提交本身保留长期历史。合并记录表示“已合并，待发布”，不等于已测试其他版本或已给玩家发包。

缺少元数据的手写 PR 仍可检查平台，候选一律标为“待评估”。使用生成正文时会携带一个 `blacklight-sync` 注释块；不要重复添加。错误平台或错误元数据会使该检查变红，维护者应修正后再合并。是否设为 GitHub 分支保护中的必需检查属于仓库设置，不能仅凭工作流存在就宣称禁止绕过。

该工作流只有 `contents: read` 权限，不写评论、Issue、代码、标签或 Release。生成记录可用于填入同步跟踪 Issue，状态由对应 PR / Release 的实际结果更新。游戏构建仍由 `Build` 工作流独立执行。

## 验证和新增平台

轻量协作工具回归可运行：

```bash
python -m unittest discover -s tools/tests -p test_version_workflow.py -v
```

版本工作流自动运行这些工具回归；不触发游戏单元测试、GameTest、客户端或多人验收。当前覆盖平台匹配、整套平台误投、可信 base 登记、元数据错误、同步候选、移植来源及合并记录。

新增平台先登记 `docs/versions.json` 并同步登记副本。Forge、NeoForge、Fabric 的加载器属性键已提供默认识别；其他加载器需在登记项填写 `loaderVersionProperty`。构建采用其他 Java toolchain 写法时，先适配静态检测并添加回归，再宣称已支持该平台；工具识别字段不代表模组已完成移植。
