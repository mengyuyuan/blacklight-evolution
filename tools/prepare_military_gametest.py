"""Prepare a NEW disposable, flat Forge GameTest server directory; never rewrites a saved world."""
from pathlib import Path
import argparse
import json

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--run-directory',default='.superpowers/military-flat-gametest')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
target=(root/args.run_directory).resolve()
if not target.is_relative_to(root):
    raise SystemExit('GameTest directory must remain inside this checkout')
if (target/'world').exists():
    raise SystemExit('A world already exists here; choose a new disposable directory')
target.mkdir(parents=True,exist_ok=True)
settings={'biome':'minecraft:plains','layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:stone','height':2},{'block':'minecraft:grass_block','height':1}],'structure_overrides':[]}
(target/'server.properties').write_text('level-type=minecraft:flat\nlevel-seed=0\ngenerator-settings='+json.dumps(settings,separators=(',',':'))+'\nspawn-monsters=false\nspawn-animals=false\n',encoding='ascii')
print(target)
