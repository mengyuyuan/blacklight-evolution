"""Prepare version-specific contributions and report CI platform/sync checks.

Uses Python's standard library. Never pushes, ports code, creates remote PRs,
changes issue state, or marks another platform as tested.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import urllib.request
from pathlib import Path

MARKER = re.compile(r'<!--\s*blacklight-sync\s*(\{.*?\})\s*-->', re.S)
LOADER_PROPERTIES = {
    'neoforge': 'neo_version', 'forge': 'forge_version', 'fabric': 'fabric_loader_version',
}
STATUSES = {'experimental', 'active', 'maintenance', 'archived'}


def git(repo: Path, *args: str) -> str:
    return subprocess.check_output(['git', '-C', str(repo), *args], encoding='utf-8').strip()


def registry(repo: Path, ref: str | None = None) -> dict:
    if ref:
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._/-]*', ref):
            raise ValueError('Invalid registry ref')
        raw = git(repo, 'show', f'{ref}:docs/versions.json')
    else:
        raw = (repo / 'docs/versions.json').read_text(encoding='utf-8')
    data = json.loads(raw)
    if data.get('schemaVersion') != 1 or not data.get('branches'):
        raise ValueError('Unsupported or empty version registry')
    names = set()
    for row in data['branches']:
        name = row['branch']
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._/-]*', name) or name in names:
            raise ValueError(f'Invalid or duplicate branch: {name}')
        if row.get('status') not in STATUSES or not isinstance(row.get('java'), int):
            raise ValueError(f'Invalid platform entry: {name}')
        if row.get('loader') not in LOADER_PROPERTIES and not row.get('loaderVersionProperty'):
            raise ValueError(f'Register loaderVersionProperty for loader: {row.get("loader")}')
        names.add(name)
    return data


def platform(data: dict, branch: str) -> dict:
    for row in data['branches']:
        if row['branch'] == branch:
            return row
    raise ValueError(f'Unknown target branch {branch}; register the platform before porting')


def merged_source(data: dict, number: int) -> tuple[str, str]:
    repository = data.get('repository', 'mengyuyuan/blacklight-evolution')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid repository in version registry')
    request = urllib.request.Request(f'https://api.github.com/repos/{repository}/pulls/{number}',
                                     headers={'User-Agent': 'Blacklight-Version-Workflow', 'Accept': 'application/vnd.github+json'})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            pr = json.load(response)
    except OSError as error:
        raise ValueError('Cannot read source PR; retry or supply --source-commit explicitly') from error
    if not pr.get('merged') or not pr.get('merge_commit_sha'):
        raise ValueError('Source PR has not merged; wait for its final merged SHA')
    platform(data, pr['base']['ref'])
    return pr['merge_commit_sha'], pr['base']['ref']


def check_platform(repo: Path, expected: dict) -> None:
    props = {}
    for line in (repo / 'gradle.properties').read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.lstrip().startswith(('#', '!')):
            key, value = line.split('=', 1)
            props[key.strip()] = value.strip()
    errors = []
    if props.get('minecraft_version') != expected['minecraft']:
        errors.append(f'Minecraft: expected {expected["minecraft"]}, found {props.get("minecraft_version")}')
    loader_key = expected.get('loaderVersionProperty') or LOADER_PROPERTIES[expected['loader']]
    if not props.get(loader_key):
        errors.append(f'Loader: expected {expected["loader"]} ({loader_key} missing)')
    foreign = [key for loader, key in LOADER_PROPERTIES.items()
               if loader != expected['loader'] and props.get(key)]
    if foreign:
        errors.append(f'Mixed loader properties: {", ".join(foreign)}')
    build = (repo / 'build.gradle').read_text(encoding='utf-8')
    # Consume quoted strings before comments: a Gradle glob such as 'fx/**'
    # is not a block-comment opener, and quoted Java declarations are not code.
    build = re.sub(r"'(?:\\.|[^'\\])*'|\"(?:\\.|[^\"\\])*\"|/\*.*?\*/|//[^\n]*",
                   lambda match: '\n' * match.group().count('\n'), build, flags=re.S)
    versions = re.findall(r'languageVersion\s*(?:=|\.set\()\s*JavaLanguageVersion\.of\(\s*(\d+)\s*\)', build)
    if not versions or set(versions) != {str(expected['java'])}:
        errors.append(f'Java toolchain: expected {expected["java"]}, found {versions or "undetected"}')
    if errors:
        raise ValueError(f'Platform does not match base {expected["branch"]}:\n' + '\n'.join(errors))


def metadata(body: str) -> dict:
    matches = list(MARKER.finditer(body))
    if len(matches) > 1:
        raise ValueError('Use only one blacklight-sync block')
    info = json.loads(matches[0].group(1)) if matches else {'scope': 'review'}
    if not isinstance(info, dict):
        raise ValueError('blacklight-sync must contain a JSON object')
    return info


def sync_plan(data: dict, source: str, info: dict) -> list[dict]:
    platform(data, source)
    scope = info.get('scope', 'review')
    if not isinstance(scope, str) or scope not in {'review', 'shared', 'platform', 'backport'}:
        raise ValueError('Unknown sync scope')
    if scope == 'platform' and not str(info.get('reason', '')).strip():
        raise ValueError('Platform-only changes need a reason')
    if scope == 'backport':
        number, sha = info.get('source_pr'), info.get('source_commit', '')
        if type(number) is not int or number <= 0 or not isinstance(sha, str) or not re.fullmatch(r'[0-9a-f]{40}|[0-9a-f]{64}', sha):
            raise ValueError('Backports need source_pr and the full merged source_commit SHA')
        if info.get('source_branch') == source:
            raise ValueError('Source PR already belongs to this target version; choose another target')
    selected = info.get('targets')
    names = {row['branch'] for row in data['branches']}
    if selected is not None:
        if not isinstance(selected, list) or any(not isinstance(item, str) for item in selected):
            raise ValueError('targets must be a list of branch names')
        if set(selected) - names or source in selected or len(selected) != len(set(selected)):
            raise ValueError('Unknown, duplicate or source branch in targets')
    rows = []
    for target in data['branches']:
        branch = target['branch']
        if branch == source:
            continue
        if scope == 'backport':
            status, reason = '跟随来源跟踪记录', f'来源 PR #{info["source_pr"]}；不重复产生反向移植任务'
        elif target['status'] in {'archived', 'experimental'}:
            status, reason = '需维护者判断', f'版本状态：{target["status"]}'
        elif scope == 'platform':
            status, reason = '不适用（作者声明）', info['reason']
        elif selected is not None and branch not in selected:
            status, reason = '待确认排除原因', '未列入作者选择的目标；不能默认为已同步'
        else:
            status = '待同步' if scope == 'shared' and target['status'] != 'maintenance' else '待评估'
            reason = '需独立适配与验证；维护版只评估缺陷修复' if target['status'] == 'maintenance' else '需独立适配与验证'
        rows.append({'branch': branch, 'minecraft': target['minecraft'], 'loader': target['loader'],
                     'status': status, 'reason': reason})
    return rows


def cell(value: object) -> str:
    return str(value).replace('\n', ' ').replace('\r', ' ').replace('|', '\\|').replace('`', '\\`')


def report_markdown(report: dict) -> str:
    header = ['# 版本检查与同步记录', '',
              f'- 目标分支：`{cell(report["branch"])}`',
              f'- 平台：Minecraft {cell(report["minecraft"])} / {cell(report["loader"])} / Java {report["java"]}',
              f'- 状态：{report["phase"]}',
              f'- 来源 PR：{report.get("pr_url") or "本地草稿"}',
              f'- 本次合入提交：`{report.get("merge_commit") or "尚未合并"}`', '']
    if report.get('backport_source'):
        info = report['backport_source']
        header += [f'- 移植来源：PR #{info["source_pr"]} / `{info["source_commit"]}`', '']
    header += ['| 其他维护分支 | 同步状态 | 说明 |', '| --- | --- | --- |']
    for row in report['targets']:
        header.append(f'| `{cell(row["branch"])}` | {cell(row["status"])} | {cell(row["reason"])} |')
    header += ['', '清单仅记录候选或作者声明，不表示已移植、已测试或已发布。', '']
    return '\n'.join(header)


def save_report(output: Path, report: dict) -> None:
    output.mkdir(parents=True, exist_ok=True)
    (output / 'error.json').unlink(missing_ok=True)
    (output / 'sync-plan.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'sync-plan.md').write_text(report_markdown(report), encoding='utf-8')


def prepare(args: argparse.Namespace) -> None:
    data = registry(args.repo, args.registry_ref)
    row = platform(data, args.target)
    if row['status'] == 'archived':
        raise ValueError('Archived versions do not accept new development branches')
    if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', args.topic):
        raise ValueError('topic must use lowercase letters, numbers and single hyphens')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', args.remote):
        raise ValueError('Invalid remote name')
    if args.source_pr is not None and args.source_pr <= 0:
        raise ValueError('source-pr must be positive')
    if args.source_commit is not None and args.source_pr is None:
        raise ValueError('source-commit needs source-pr')
    source_branch = None
    if args.source_pr and args.source_commit is None:
        args.source_commit, source_branch = merged_source(data, args.source_pr)
    info = {'scope': 'backport' if args.source_pr else args.scope}
    if args.source_pr:
        info.update(source_pr=args.source_pr, source_commit=args.source_commit)
        if source_branch:
            info['source_branch'] = source_branch
    if args.reason:
        info['reason'] = args.reason
    targets = sync_plan(data, args.target, info)
    kind = 'backport' if args.source_pr else args.kind
    name = f'{kind}/{row["loader"]}-{row["minecraft"]}-{args.topic}'
    if args.create_branch:
        if git(args.repo, 'status', '--porcelain'):
            raise ValueError('Working tree is dirty; save changes before creating a branch')
        git(args.repo, 'fetch', args.remote)
        git(args.repo, 'switch', '-c', name, f'{args.remote}/{args.target}')
    report = {**row, 'phase': '本地草稿', 'targets': targets}
    if args.source_pr:
        report['backport_source'] = info
    save_report(args.output, report)
    source = f'#{args.source_pr} / {args.source_commit}' if args.source_pr else '无，首次实现'
    body = (f'## 目标版本与分支\n\n- Minecraft / 加载器 / Java：{row["minecraft"]} / {row["loader"]} / {row["java"]}\n'
            f'- 目标 base 分支：{args.target}\n\n## 改动解决的问题\n\n待填写\n\n## 修改后的行为\n\n待填写\n\n'
            f'## 来源与跨版本同步\n\n- 来源 PR / 实际合入提交 SHA：{source}\n- 同步跟踪 Issue：待填写或说明不适用\n\n'
            + report_markdown(report) + '\n## 验证\n\n实际执行的检查：待填写\n未验证项：待填写\n协议 / 存档影响：待填写\n\n'
            '## 素材来源与许可（如涉及）\n\n待填写或标明不涉及\n\n'
            '<!-- blacklight-sync\n' + json.dumps(info, ensure_ascii=False) + '\n-->\n')
    (args.output / 'pr-body.md').write_text(body, encoding='utf-8')
    print(f'Target: {args.target}; branch: {name}; PR body: {args.output / "pr-body.md"}')
    if not args.create_branch:
        print(f'Preview only. To create the branch: rerun with --create-branch (remote: {args.remote}).')


def ci(args: argparse.Namespace) -> None:
    event = json.loads(args.event.read_text(encoding='utf-8'))
    pr = event['pull_request']
    data = registry(args.repo, pr['base']['sha'])
    row = platform(data, pr['base']['ref'])
    check_platform(args.repo, row)
    info = metadata(pr.get('body') or '')
    report = {**row, 'phase': '已合并，待发布' if pr.get('merged') else '平台检查通过，尚未合并',
              'pr_url': pr.get('html_url'), 'pr_number': pr['number'],
              'merge_commit': pr.get('merge_commit_sha') if pr.get('merged') else None,
              'targets': sync_plan(data, row['branch'], info)}
    if info.get('scope') == 'backport':
        report['backport_source'] = info
    save_report(args.output, report)
    summary = report_markdown(report)
    print(summary)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as handle:
            handle.write(summary)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
    sub = parser.add_subparsers(dest='command', required=True)
    prepare_parser = sub.add_parser('prepare', help='Generate a filled PR body and sync candidates')
    prepare_parser.add_argument('--target', required=True)
    prepare_parser.add_argument('--topic', required=True)
    prepare_parser.add_argument('--kind', choices=['feat', 'fix', 'docs'], default='fix')
    prepare_parser.add_argument('--scope', choices=['review', 'shared', 'platform'], default='review')
    prepare_parser.add_argument('--reason')
    prepare_parser.add_argument('--source-pr', type=int)
    prepare_parser.add_argument('--source-commit')
    prepare_parser.add_argument('--remote', default='origin')
    prepare_parser.add_argument('--registry-ref')
    prepare_parser.add_argument('--create-branch', action='store_true')
    prepare_parser.add_argument('--output', type=Path, default=Path('tmp/version-workflow'))
    prepare_parser.set_defaults(func=prepare)
    ci_parser = sub.add_parser('ci', help='Validate the PR against its base registry and emit a record')
    ci_parser.add_argument('--event', type=Path, required=True)
    ci_parser.add_argument('--output', type=Path, default=Path('version-report'))
    ci_parser.set_defaults(func=ci)
    args = parser.parse_args()
    try:
        args.func(args)
        return 0
    except (ValueError, KeyError, TypeError, OSError, subprocess.CalledProcessError) as error:
        message = str(error)
        if args.command == 'ci':
            args.output.mkdir(parents=True, exist_ok=True)
            for name in ('sync-plan.json', 'sync-plan.md'):
                (args.output / name).unlink(missing_ok=True)
            (args.output / 'error.json').write_text(json.dumps({'error': message}, ensure_ascii=False), encoding='utf-8')
            encoded = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            print(f'::error::{encoded}')
        else:
            print(f'Error: {message}')
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
