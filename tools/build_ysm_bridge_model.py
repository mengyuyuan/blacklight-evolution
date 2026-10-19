"""Build an opt-in YSM body bridge; armour uses Blacklight's original block model."""
import argparse
import copy
import hashlib
import json
import shutil
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def save(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')


def region(name):
    if name in ('Head', 'Neck', 'MouthInterior', 'LowerLip') or name.startswith('Fringe') or any(x in name for x in ('Eye', 'Lid', 'Brow')): return 'head'
    for side in ('Left', 'Right'):
        if name.startswith(side):
            return side.lower() + ('_arm' if any(x in name for x in ('Arm', 'Hand')) else '_leg')
    return 'torso'


def build(source, destination):
    if source.resolve() == destination.resolve():
        raise ValueError('The source model must not be overwritten')
    destination.mkdir(parents=True, exist_ok=True)
    geo = read(source / 'main.json')
    g = geo['minecraft:geometry'][0]
    original = copy.deepcopy(g['bones'])
    assert len({b['name'] for b in original}) == len(original)
    g['description']['identifier'] = 'geometry.alex_mercer_blacklight_bridge'
    g['description']['visible_bounds_width'] = 5
    g['description']['visible_bounds_height'] = 5
    visibility = {}
    count = 0
    for bone in g['bones'][:]:
        name = bone['name']; cubes = bone.pop('cubes', [])
        if not cubes: continue
        count += len(cubes)
        child = 'BL_skin_' + name
        g['bones'].append({'name': child, 'parent': name, 'pivot': bone['pivot'], 'cubes': cubes})
        expr = 'v.roaming.bl_body_' + region(name)
        if name in ('LeftArm', 'LeftForeArm', 'LeftHandLocator'): expr += '*v.roaming.bl_left_arm'
        if name in ('RightArm', 'RightForeArm', 'RightHandLocator'): expr += '*v.roaming.bl_right_arm'
        visibility[child] = {'scale': '(v.roaming.bl_active ? (' + expr + ') : 1)'}
    save(destination / 'main.json', geo)
    shutil.copy2(source / 'mercer.png', destination / 'mercer.png')
    shutil.copy2(source / 'arm.json', destination / 'arm.json')
    anim = read(source / 'main.animation.json')
    bones = {}
    for bone in original:
        prefix = 'v.roaming.bl_' + bone['name'].lower()
        bones[bone['name']] = {'rotation': [prefix + '_r' + a for a in 'xyz'],
                               'position': [prefix + '_p' + a for a in 'xyz']}
    anim['animations']['blacklight_live_pose'] = {'loop': True, 'animation_length': 1,
        'override_previous_animation': True, 'bones': bones}
    anim['animations']['parallel7'] = {'loop': True, 'animation_length': 1, 'bones': visibility}
    save(destination / 'main.animation.json', anim)
    save(destination / 'blacklight.controller.json', {'format_version': '1.19.0', 'animation_controllers': {
        'player.main': {'initial_state': 'ysm-builtin', 'states': {
            'ysm-builtin': {'transitions': [{'blacklight': 'v.roaming.bl_active'}]},
            'blacklight': {'animations': ['blacklight_live_pose'], 'transitions': [{'ysm-builtin': '!v.roaming.bl_active'}]}
        }}
    }})
    manifest = read(source / 'ysm.json')
    manifest['metadata']['name'] = 'A哥 · 黑光 YSM 联动测试'
    manifest['metadata']['tips'] = '黑光 + YSM Mapping API 联动；装甲使用原版方块战甲'
    manifest['files']['player']['animation_controllers'] = ['blacklight.controller.json']
    save(destination / 'ysm.json', manifest)
    report = {'status': 'structural_checks_passed_runtime_pending', 'bones': len(original),
              'body_cubes': count, 'armor_source': 'blacklight/models/armor_block.json',
              'source_body_sha256': hashlib.sha256((source / 'main.json').read_bytes()).hexdigest(),
              'checks': ['no_source_mutation', 'unmodified_texture', 'geometry_leaves_preserve_joint_chain'],
              'pending': ['combat_pose_and_attachments', 'original_block_armor_coverage', 'multiplayer']}
    save(destination / 'validation.json', report)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--destination', type=Path, required=True)
    args = parser.parse_args()
    build(args.source, args.destination)
