"""Prepare a NEW PENDING M6.1 input from root's actual run selections. Never approves, builds or packages."""
from __future__ import annotations

import argparse
import copy
import importlib.util
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True


def collector():
    spec = importlib.util.spec_from_file_location('m61_pending_collector', Path(__file__).with_name('collect_military6_delivery.py'))
    value = importlib.util.module_from_spec(spec);spec.loader.exec_module(value)
    return value


def prepare(workspace, observations, c=None):
    c = c or collector();workspace = workspace.resolve()
    c.require(observations.get('version') == c.VERSION and observations.get('status') != 'VERIFIED'
              and observations.get('finalSourceFreeze') is not True, 'Provide actual M6.1 run selections as PENDING, never an old VERIFIED freeze')
    c.require(set(observations.get('platforms', {})) == set(c.PLATFORMS), 'Actual selections for both loaders are required')
    result = copy.deepcopy(observations)
    result.update(schemaVersion=1, version=c.VERSION, status='PENDING', finalSourceFreeze=False)
    observed = {}
    def record(repo, value):
        name = value['path'] if isinstance(value, dict) else value
        path = c.within(repo, name)
        c.require(path.is_file() and not path.is_symlink(), 'Missing actual selected input: '+str(path))
        item = {'path': name, 'sha256': c.sha(path)}
        if isinstance(value, dict) and 'sha256' in value:c.require(value['sha256'] == item['sha256'], 'Selected input hash changed: '+name)
        observed[path] = item['sha256'];return item
    def mapping(repo, values):
        return {item['path']: item['sha256'] for item in (record(repo, name) for name in values)}
    protected = [name+suffix for name in (c.M5_NAME, c.M6_NAME) for suffix in ('.zip', '/MANIFEST.json', '/SHA256SUMS.txt')]
    result['protectedFiles'] = mapping(workspace, protected)
    c.protected_deliveries(workspace, result['protectedFiles'])
    for platform, entry in result['platforms'].items():
        repo = workspace/'.blackwatch-worktrees'/(platform+'-military6')
        c.require(all(key in entry for key in ('jar', 'junit', 'gameTests', 'garageAudit', 'vehiclePreview', 'clientView')),
                  'Select actual final logs, audits, previews and reviewed GL for '+platform)
        c.changed_paths(repo)
        entry.update(repo=str(repo), head=c.git(repo, 'rev-parse', 'HEAD').decode().strip(),
                     branch=c.git(repo, 'branch', '--show-current').decode().strip())
        entry['jar'] = record(repo, entry['jar'])
        unit = entry['junit'];unit['log'] = record(repo, unit['log'])
        unit['xmlFiles'] = mapping(repo, (p.relative_to(repo).as_posix() for p in sorted((repo/'build/test-results/test').glob('TEST-*.xml'))))
        for game in entry['gameTests']:
            game['log'] = record(repo, game['log'])
            game['versionLogs'] = [record(repo, value) for value in game.get('versionLogs', [])]
        garage = entry['garageAudit']
        for key in ('json', 'prepareLog', 'verifyLog'):garage[key] = record(repo, garage[key])
        garage['versionLogs'] = [record(repo, value) for value in garage.get('versionLogs', [])]
        preview = entry['vehiclePreview']
        for key in ('manifest', 'capture'):preview[key] = record(repo, preview[key])
        preview['sourceFiles'] = mapping(repo, preview['sourceFiles'])
        view = entry['clientView']
        for key in ('report', 'review', 'log'):view[key] = record(repo, view[key])
        view['sourceFiles'] = mapping(repo, view['sourceFiles'])
        report = c.read_json(repo/view['report']['path'])
        base = Path(view['report']['path']).parent
        view['images'] = mapping(repo, ((base/frame['image']).as_posix() for frame in report['frames']))
        view['versionLogs'] = [record(repo, value) for value in view.get('versionLogs', [])]
        extras = mapping(repo, entry.get('evidenceFiles', {}))
        entry['evidenceFiles'] = {**extras, **c.required_evidence(entry)}
        entry['sourceFiles'] = c.source_inventory(repo)
    result['docFiles'] = {name: digest for name, digest in result['platforms']['neoforge']['sourceFiles'].items() if name.startswith('docs/')}
    for path, digest in observed.items():c.require(c.sha(path) == digest, 'Input moved during preparation: '+str(path))
    for entry in result['platforms'].values():c.require(c.source_inventory(Path(entry['repo'])) == entry['sourceFiles'], 'Source inventory moved during preparation')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=Path('F:/MC建筑'))
    parser.add_argument('--observations', type=Path, required=True, help="Root's actual run selections; no synthetic runtime input")
    parser.add_argument('--output', type=Path, required=True, help='A NEW .superpowers/*.json draft in the Neo M6 tree')
    args = parser.parse_args();c = collector();output = args.output.resolve()
    owner = args.workspace.resolve()/'.blackwatch-worktrees/neoforge-military6/.superpowers'
    c.require(output.parent == owner and output.suffix == '.json' and not output.exists(), 'Use a new Neo .superpowers JSON; never overwrite a freeze')
    result = prepare(args.workspace, c.read_json(args.observations), c)
    with output.open('x', encoding='utf-8') as stream:json.dump(result, stream, ensure_ascii=False, indent=2);stream.write('\n')
    print(json.dumps({'status': 'PENDING', 'finalSourceFreeze': False, 'deliveryOutputWritten': False, 'draft': str(output)}, ensure_ascii=False))


if __name__ == '__main__':main()
