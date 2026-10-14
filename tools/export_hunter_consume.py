"""Export Blacklight motion data with separate initial-source and project-adaptation metadata.

Reads decoded source inputs and adapts them to Minecraft. This script does not
claim the resulting motion was wholly authored from scratch, and does not upload files.
"""

from motion_provenance import motion_notice, motion_provenance
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("receiver", type=Path)
parser.add_argument("motion_directory", type=Path)
args = parser.parse_args()
raw = args.receiver.read_bytes()
source = json.loads(raw)
if source["name"] != "alex_brawl_consume_2_rcv" or source["frame_rate"] != 30:
    raise ValueError("Expected the active Brawler consume-2 receiver at 30 fps")
channels = [c for g in source["groups"] if g["name"] == "Jaw"
            for c in g["channels"] if c["semantic"] == "SCL"]
if len(channels) != 1:
    raise ValueError("Expected exactly one Jaw scale channel")
channel = channels[0]
if len(channel["times"]) != len(channel["values"]):
    raise ValueError("Mismatched scale keys")
actor = json.loads((args.motion_directory / "consume_large_actor.json").read_text())
payload = dict(schema=1, source=source["name"], sourceSha256=hashlib.sha256(raw).hexdigest(),
               actorClipSeconds=max(t for track in actor["animation"] for t in track["time"]),
               jawScale=[[t, *v] for t, v in zip(channel["times"], channel["values"])],
               privateNotice=motion_notice('Original-derived animation channel. Private local use only.'), provenance=motion_provenance())
output = args.motion_directory / "hunter-consume.data"
output.write_text(json.dumps(payload, separators=(",", ":"), allow_nan=False), encoding="utf8")
print(f"Exported {len(payload['jawScale'])} Jaw scale keys to {output}")
