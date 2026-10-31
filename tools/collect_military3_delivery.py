"""Collect a new local military.3 handoff; never modify an installed game or old release."""
from __future__ import annotations
import argparse
import hashlib
import json
import shutil
import subprocess
import re
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def git(repo: Path, *args: str) -> bytes:
    return subprocess.check_output(['git', '-C', str(repo), *args])

def collect() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--output',type=Path)
    args=parser.parse_args();workspace=args.workspace.resolve()
    output=(args.output or workspace/'military-officers-preview-20261024').resolve()
    if not output.is_relative_to(workspace):raise SystemExit('Delivery must remain in the workspace')
    old=workspace/'military-organization-preview-20261023.zip'
    expected='3ef369f0f347562896226fc77b62cdc4035c1ad61b2dae07f45a91f9262dc031'
    if not old.is_file() or sha(old)!=expected:raise SystemExit('Protected military.1 archive has changed')
    old2=workspace/'military-upgrade-preview-20261024.zip'
    expected2='412732809bf4bbdb9f6e082075b937761dcdcf33c07519535483c50db1ec8119'
    if not old2.is_file() or sha(old2)!=expected2:raise SystemExit('Protected military.2 archive has changed')
    stage=output.with_name(output.name+'.staging')
    if output.exists() or stage.exists() or output.with_suffix('.zip').exists():raise SystemExit('Choose a new output; existing artifacts are never overwritten')
    stage.mkdir(parents=True)
    summary={'createdUtc':datetime.now(timezone.utc).isoformat(),'version':'0.3.4-preview.20261024-military.3',
             'publicUpload':False,'installedGameModified':False,'oldReleaseSha256':expected,'military2Sha256':expected2,'platforms':{}}
    repos={'neoforge':workspace/'blacklight-evolution','forge':workspace/'blacklight-evolution-forge-1.20.1'}
    for platform,repo in repos.items():
        jars=list((repo/'build/libs').glob('*-military.3-bundled.jar'))
        if len(jars)!=1:raise SystemExit(f'Expected one normal release JAR for {platform}: {jars}')
        jar=jars[0]
        with ZipFile(jar) as archive:
            if archive.testzip() is not None:raise SystemExit(f'Corrupt JAR: {jar}')
            if any('GameTest' in n or 'CityMilitaryServerAudit' in n for n in archive.namelist()):raise SystemExit(f'QA leaked into {jar}')
        destination=stage/platform;destination.mkdir();shutil.copy2(jar,destination/jar.name)
        tracked=git(repo,'diff','--name-only','-z','HEAD').decode('utf8').split('\0')
        untracked=git(repo,'ls-files','--others','--exclude-standard','-z').decode('utf8').split('\0')
        changed=sorted(set(filter(None,tracked+untracked)));deleted=[];inventory={}
        source=stage/'sources'/f'{platform}-changes.zip';source.parent.mkdir(exist_ok=True)
        with ZipFile(source,'w',ZIP_DEFLATED,compresslevel=6) as archive:
            for name in changed:
                path=(repo/name).resolve()
                if not path.is_relative_to(repo.resolve()) or path.is_symlink():raise SystemExit(f'Unsafe source path: {name}')
                if not path.exists():deleted.append(name);continue
                if path.is_file():archive.write(path,name);inventory[name]=sha(path)
            base={'head':git(repo,'rev-parse','HEAD').decode().strip(),'branch':git(repo,'branch','--show-current').decode().strip(),
                  'remote':git(repo,'remote','get-url','origin').decode().strip(),'files':inventory,'deleted':deleted,
                  'instructions':'This is an overlay of all uncommitted military.1 + military.2 + military.3 changes. Start at head, overlay files, review deleted explicitly. It is not a standalone Git checkout.'}
            archive.writestr('PATCH-BASE.json',json.dumps(base,ensure_ascii=False,indent=2)+'\n')
        diff=git(repo,'diff','--binary','HEAD');(stage/'sources'/f'{platform}-tracked.diff').write_bytes(diff)
        logs=stage/'evidence'/platform;logs.mkdir(parents=True)
        for path in sorted((repo/'.superpowers/military3-qa').glob('*.log')):shutil.copy2(path,logs/path.name)
        audit=repo/f'.superpowers/military3-server-{platform}/city-military-audit.json'
        result=json.loads(audit.read_text(encoding='utf8'))
        if result.get('status')!='PASS':raise SystemExit(f'Ordinary server restart audit is not PASS: {platform}')
        shutil.copy2(audit,logs/'city-military-audit.json')
        xmls=sorted((repo/'build/test-results/test').glob('TEST-*.xml'))
        if not xmls:raise SystemExit(f'Missing actual JUnit XML: {platform}')
        junit={'tests':0,'failures':0,'errors':0,'skipped':0,'suites':len(xmls)}
        raw=logs/'junit';raw.mkdir()
        for path in xmls:
            suite=ET.parse(path).getroot()
            for key in ['tests','failures','errors','skipped']:junit[key]+=int(suite.get(key,'0'))
            shutil.copy2(path,raw/path.name)
        if junit['failures'] or junit['errors']:raise SystemExit(f'JUnit failed: {platform}: {junit}')
        regression=logs/f'{"neo" if platform=="neoforge" else "forge"}-regression.log'
        log=regression.read_text(encoding='gbk',errors='replace')
        matches=re.findall(r'(\d+) GAME TESTS COMPLETE',log)
        if not matches or int(matches[-1])!=97 or 'All 97 required tests passed' not in log:raise SystemExit(f'Expected 97 actual passing scenes: {platform}')
        migration=repo/f'.superpowers/city-military-server-{platform}/city-military-audit.json'
        migrated=json.loads(migration.read_text(encoding='utf8'))
        if migrated.get('status')!='PASS' or not migrated.get('legacyRoleMigrationVerified'):raise SystemExit(f'Missing actual military.2 disk migration proof: {platform}')
        if not result.get('officerRoleUniformManualAppointmentRestartVerified'):raise SystemExit(f'Missing actual officer disk restart proof: {platform}')
        shutil.copy2(migration,logs/'military2-upgrade-audit.json')
        results={'military2DiskMigration':migrated,'junit':junit,'gameTest':{'tests':97,'requiredPassed':97,'log':regression.name},'ordinaryServerAudit':result}
        (logs/'TEST-RESULTS.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        summary['platforms'][platform]={'base':base['head'],'branch':base['branch'],'jar':f'{platform}/{jar.name}',
            'jarSha256':sha(jar),'sourceFiles':len(inventory),'ordinaryServerRestart':result['status'],'tests':results}
    neo=repos['neoforge'];docs=stage/'docs';docs.mkdir()
    shutil.copy2(neo/'docs/MILITARY2_PLAYER_TEST_ZH.md',docs/'MILITARY2_PLAYER_TEST_ZH.md')
    shutil.copy2(neo/'docs/MILITARY3_PLAYER_TEST_ZH.md',docs/'MILITARY3_PLAYER_TEST_ZH.md')
    shutil.copy2(neo/'docs/MONSTERS_CURRENT_ZH.md',docs/'MONSTERS_CURRENT_ZH.md')
    verification=docs/'verification';verification.mkdir()
    for name in ['MILITARY3_IMPLEMENTATION_20261024_ZH.md','MILITARY3_FINAL_REVIEW_20261024_ZH.md','MILITARY3_OFFICERS_RUNTIME_20261024_ZH.md','MILITARY3_OFFICERS_VISUALS_20261024_ZH.md','MILITARY2_IMPLEMENTATION_20261024_ZH.md','MILITARY2_FINAL_REVIEW_20261024_ZH.md',
                 'SOLDIER_VISUAL_UPGRADE_20261024_ZH.md','LARGE_MILITARY_BASES_20261024_ZH.md','FIREARMS_IMPLEMENTATION_20261024_ZH.md']:
        shutil.copy2(neo/'docs/verification'/name,verification/name)
    previews=docs/'previews';previews.mkdir()
    for path in (neo/'docs/previews').glob('military2-soldiers*'):
        if path.is_file():shutil.copy2(path,previews/path.name)
    for path in (neo/'docs/previews').glob('military3-officers*'):
        if path.is_file():shutil.copy2(path,previews/path.name)
    shutil.copytree(neo/'docs/previews/large-military-bases',previews/'large-military-bases')
    (stage/'README.md').write_text('''# military.3 双阵营军官完整预览交付

完整黑光主包，替换旧主JAR即可。退出游戏，把对应加载器目录里的唯一JAR放进实例mods，原来的黑光主JAR移到外面备份；保留你已有的GeckoLib。两端主JAR不能混放。本交付不修改PCL、不公开上传。

- [军官新增内容与快速测试](docs/MILITARY3_PLAYER_TEST_ZH.md)
- [军官实际资产预览](docs/previews/military3-officers-viewer.html)
- [完整原有功能安装与测试指南](docs/MILITARY2_PLAYER_TEST_ZH.md)
- [士兵实际资产预览](docs/previews/military2-soldiers-viewer.html)
- [大型基地实际NBT预览](docs/previews/large-military-bases/index.html)
- [实施、验证及边界](docs/verification/MILITARY3_IMPLEMENTATION_20261024_ZH.md)

预览为离线资产渲染，非Minecraft实机截图。客户端外观/物理按键仍需实机验收。

sources中的changes.zip是完整未提交增量的逐文件覆盖快照，包含原military.1、military.2与本轮military.3；PATCH-BASE.json记录真实Git基线、分支、逐文件哈希及删除列表。tracked.diff仅覆盖已跟踪文件，不能单独替代changes.zip。evidence保存实际成功与失败记录，不将Gradle退出0单独当成场景成功。
''',encoding='utf8')
    files={p.relative_to(stage).as_posix():sha(p) for p in sorted(stage.rglob('*')) if p.is_file()}
    summary['files']=files;manifest=stage/'MANIFEST.json';manifest.write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    files['MANIFEST.json']=sha(manifest)
    (stage/'SHA256SUMS.txt').write_text(''.join(f'{value}  {name}\n' for name,value in sorted(files.items())),encoding='utf8')
    stage.rename(output);archive_path=output.with_suffix('.zip')
    with ZipFile(archive_path,'x',ZIP_DEFLATED,compresslevel=6) as archive:
        for path in sorted(output.rglob('*')):
            if path.is_file():archive.write(path,output.name+'/'+path.relative_to(output).as_posix())
    with ZipFile(archive_path) as archive:
        if archive.testzip() is not None:raise SystemExit('Delivery ZIP integrity check failed')
    archive_path.with_suffix('.zip.sha256').write_text(sha(archive_path)+'  '+archive_path.name+'\n',encoding='ascii')
    if sha(old)!=expected or sha(old2)!=expected2:raise SystemExit('Protected archive changed during collection')
    print(json.dumps({'directory':str(output),'archive':str(archive_path),'sha256':sha(archive_path),'platforms':summary['platforms']},ensure_ascii=False,indent=2))

if __name__=='__main__':collect()
