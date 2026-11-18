"""Record the two-loader native item evidence after reviewing the contact sheets."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = Path('D:/blacklight-evolution-forge-1.20.1')
EVIDENCE = Path('D:/CodexCache/vehicle-item-qa')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reviewed', action='store_true', help='Confirm the two GUI boards and six context sheets were visually inspected')
    args = parser.parse_args()
    if not args.reviewed:
        parser.error('Review the actual images before recording a visual pass')
    unit = Path('D:/CodexCache/vehicle-item-unit.log')
    text = unit.read_text('utf8')
    assert 'Ran 8 tests' in text and '\nOK\n' in text, 'Generator tests did not pass'
    report = {
        'schemaVersion': 1,
        'date': '2026-11-16',
        'scope': '11 three-dimensional vehicle and supply item models; six miniatures use unchanged production vehicle meshes',
        'overallGoalComplete': False,
        'generatorTests': {'passed': 8, 'bothRecipeFormats': True, 'log': str(unit), 'sha256': sha(unit)},
        'generatorSource': {name: sha(ROOT/name) for name in ('tools/build_vehicle_items.py', 'tools/vehicle_item_models.py',
                                                           'tools/test_build_vehicle_items.py', 'tools/record_vehicle_items.py')},
        'preservedContract': {'activeModels': 10, 'reservedInactiveModels': ['military_fuel_can'],
                              'drivingRequiresFuel': False, 'persistedIdsChanged': False},
        'limits': [
            'No player world opened; static native ItemRenderer contexts only.',
            'Actual player-arm grips, dropped-item animation and item-frame world lighting remain unverified.',
            'Placed vehicle geometry, helicopter flight/delivery, soldier AI and sounds were not changed by this batch.',
            'Hidden QA profile does not produce a release jar; no player installation was updated.'
        ],
        'platforms': {}
    }
    common_hashes = None
    for platform, root in (('neo', ROOT), ('forge', FORGE)):
        evidence = EVIDENCE/platform
        assert (evidence/'status.txt').read_text('utf8').startswith('PASS:'), platform
        log = Path(f'D:/CodexCache/creature-combat-{platform}-vehicleItemVisual.log')
        assert 'BUILD SUCCESSFUL' in log.read_text('utf8', errors='replace'), platform
        rows = json.loads((evidence/'baked-models.json').read_text('utf8'))
        assert len(rows) == 11 and len({row['item'] for row in rows}) == 11
        resources = {}
        parts = {}
        captures = ['inventory-board.png']
        for row in rows:
            for resource, digest in row['resourceSha256'].items():
                namespace, path = resource.split(':', 1)
                relative = 'src/main/resources/assets/'+namespace+'/'+path
                assert sha(root/relative) == digest, ('Stale runtime resource', relative)
                resources[relative] = digest
            model = json.loads((root/f'src/main/resources/assets/blacklight/models/item/{row["item"]}.json').read_text('utf8'))
            parts[row['item']] = len(model['elements'])
            assert row['quads'] == parts[row['item']]*6, row['item']
            assert row['gui3d'] and all('missingno' not in sprite for sprite in row['sprites'])
            captures.append(row['item']+'-contexts.png')
        atlas = 'src/main/resources/assets/minecraft/atlases/blocks.json'
        resources[atlas] = sha(root/atlas)
        if common_hashes is None:
            common_hashes = resources
        else:
            assert common_hashes == resources, 'Runtime model resources differ between loaders'
        reviewed = ['inventory-board.png']+[f'context-sheet-{i}.png' for i in range(1, 4)]
        source_paths = ['src/vehicleItemVisual/java/com/codex/blacklight/client/vehicle/VehicleItemVisual.java',
                        'tools/vehicle-item-visual.init.gradle']
        report['platforms'][platform] = {
            'root': str(root), 'nativeEvidence': str(evidence), 'models': 11, 'guiIcons': 11,
            'displayContexts': 88, 'nativeScreenshots': 12, 'geometryParts': parts,
            'loadedResourcesSha256': resources,
            'qaSourceSha256': {p: sha(root/p) for p in source_paths},
            'capturesSha256': {p: sha(evidence/p) for p in captures},
            'reviewedSheetsSha256': {p: sha(evidence/p) for p in reviewed},
            'nativeReceiptSha256': sha(evidence/'baked-models.json'), 'buildLogSha256': sha(log),
            'review': 'All eleven forms visible; no missing textures or clipped geometry in the boards and static context contact sheets.'
        }
    for root in (ROOT, FORGE):
        target = root/'art/military-vehicles/item-model-validation.json'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', 'utf8')
    print(json.dumps({'modelsPerLoader': 11, 'nativeModelLoads': 22, 'displayContexts': 176,
                      'generatorTests': 8, 'overallGoalComplete': False}))


if __name__ == '__main__':
    main()
