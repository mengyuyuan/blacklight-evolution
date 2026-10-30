"""Bind independent client JUnit evidence to the source and asset bytes it consumed."""
import argparse
from datetime import datetime, timezone
from hashlib import sha256
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


def aggregate(files):
    return sha256("".join(f"{value}  {name}\n" for name, value in sorted(files.items())).encode()).hexdigest()


def snapshot(root, output, loader):
    sources = {}
    for line in (output / "javac.args").read_text(encoding="utf-8-sig" if loader == "neo" else "mbcs").splitlines():
        path = Path(line.strip('"'))
        if path.suffix == ".java" and path.is_file():
            name = path.relative_to(root).as_posix() if path.is_relative_to(root) else "shared-tools/" + path.name
            sources[name] = digest(path)
    assets = {}
    for name in (
        "art/military-vehicles/vehicle-authoring.json",
        "src/main/resources/assets/blacklight/military/vehicle-models.json",
        "src/main/resources/data/blacklight/military/vehicle-mounts.json",
        "src/main/resources/data/blacklight/military/vehicles.json",
        "src/main/resources/assets/blacklight/models/mercer_v4.json",
        "src/main/resources/assets/blacklight/animation/player_bind.json",
        "src/main/resources/assets/blacklight/textures/entity/mercer_v4.png",
        "src/main/resources/assets/blacklight/textures/entity/military_vehicles_military.png",
        "src/main/resources/assets/blacklight/textures/entity/military_vehicles_blackwatch.png",
    ):
        assets[name] = digest(root / name)
    authoring = assets["art/military-vehicles/vehicle-authoring.json"]
    for name in ("src/main/resources/assets/blacklight/military/vehicle-models.json", "src/main/resources/data/blacklight/military/vehicle-mounts.json"):
        assert json.loads((root / name).read_text(encoding="utf-8"))["sourceSha256"] == authoring, name + " is not from current authoring"
    return {"loader": loader, "compiledSourceSha256": aggregate(sources), "compiledSources": sources,
            "assetContractSha256": aggregate(assets), "assets": assets, "physicalAuthoringSha256": authoring}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--loader", choices=("neo", "forge"), required=True)
    parser.add_argument("--stage", choices=("before", "after"), required=True)
    args = parser.parse_args()
    root, output = args.root.resolve(), args.output.resolve()
    assert output.is_dir() and output.is_relative_to(root / "build/military6-client-revision")
    current = snapshot(root, output, args.loader)
    if args.stage == "before":
        (output / "source-contract-before.json").write_text(json.dumps(current, indent=2), encoding="utf-8")
        return
    before = json.loads((output / "source-contract-before.json").read_text(encoding="utf-8"))
    current["unchangedDuringVerification"] = current == before
    current["verifiedAt"] = datetime.now(timezone.utc).isoformat()
    result = ET.parse(output / "result.xml").getroot().attrib
    current["junit"] = {key: int(result[key]) for key in ("tests", "failures", "skipped")}
    vertices = json.loads((output / "actual-player-vertices.json").read_text(encoding="utf-8"))
    summary = {}
    for row in vertices["headClearanceCases"]:
        value = summary.setdefault(row["model"], {"cases": 0, "minimumClearance": float("inf")})
        value["cases"] += 1
        value["minimumClearance"] = min(value["minimumClearance"], row["clearance"])
    current["actualHeadClearance"] = summary
    current["actualGLExecuted"] = False
    (output / "source-contract.json").write_text(json.dumps(current, indent=2), encoding="utf-8")
    assert current["unchangedDuringVerification"], "Source/assets changed during compilation/tests; receipt is not accepted"
    assert current["junit"] == {"tests": 19, "failures": 0, "skipped": 0}, current["junit"]
    print(json.dumps({key: current[key] for key in ("loader", "compiledSourceSha256", "physicalAuthoringSha256", "assetContractSha256", "junit", "unchangedDuringVerification", "actualHeadClearance")}))


if __name__ == "__main__":
    main()
