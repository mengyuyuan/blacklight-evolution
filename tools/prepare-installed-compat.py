"""Stage externally downloaded test JARs; never edit or distribute their contents."""
import argparse
import hashlib
from pathlib import Path
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('--tacz', type=Path, required=True)
parser.add_argument('--potentia', type=Path, required=True)
parser.add_argument('--repository', type=Path, required=True)
args = parser.parse_args()
for artifact in ('tacz', 'potentia'):
    source = getattr(args, artifact).resolve(strict=True)
    destination = args.repository / 'blacklight' / 'qa' / artifact / '1'
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination / f'{artifact}-1.jar')
    (destination / f'{artifact}-1.pom').write_text(
        '<project><modelVersion>4.0.0</modelVersion><groupId>blacklight.qa</groupId>'
        f'<artifactId>{artifact}</artifactId><version>1</version></project>', encoding='utf-8')
    print(artifact, hashlib.sha256(source.read_bytes()).hexdigest())
