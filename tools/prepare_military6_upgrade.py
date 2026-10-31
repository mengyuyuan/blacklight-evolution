"""Copy a frozen M5 QA world into a new M6 run directory; never launch Java/Gradle."""

import argparse
import hashlib
import json
import shutil
import stat
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4


CASES = {
    "neo-city": ("neoforge", "military5-upgrade-neoforge", "blacklight:city"),
    "neo-world": ("neoforge", "military5-world-upgrade-neoforge", "blacklight:world"),
    "forge-city": ("forge", "military5-upgrade-forge", "blacklight:city"),
}
MANIFEST = "military6-legacy-copy.json"
RESULT = "military6-legacy-audit.json"
AUDITS = {"city": "city-military-audit.json", "homes": "military-homes-audit.json"}
REQUIRED = ("world", "config", "defaultconfigs", "server.properties", "eula.txt", *AUDITS.values())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def plain(path):
    info = path.lstat()
    require(not path.is_symlink() and not getattr(info, "st_file_attributes", 0) & 0x400,
            f"Symlink/junction/reparse point is not a frozen copy input: {path}")
    require(stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode), f"Not a regular file/directory: {path}")


def inventory(root):
    plain(root)
    names = set(REQUIRED)
    names.update(p.name for p in root.iterdir()
                 if ("audit" in p.name.lower() and p.suffix.lower() == ".json")
                 or p.name.startswith("MILITARY4-BASELINE"))
    files, directories = {}, []

    def visit(path):
        plain(path)
        relative = path.relative_to(root).as_posix()
        if path.is_dir():
            directories.append(relative)
            for child in sorted(path.iterdir()):
                visit(child)
        else:
            files[relative] = {"size": path.stat().st_size, "sha256": sha256(path)}

    for name in sorted(names):
        visit(root / name)
    require(all((root / name).is_dir() for name in ("world", "config", "defaultconfigs")), "Missing required directory")
    return {"directories": sorted(directories), "files": files}


def locations(repo, case, run_directory):
    loader, source_name, _ = CASES[case]
    require(repo.name == f"{loader}-military6", f"{case} must run from the matching M6 worktree")
    qa = repo / ".superpowers"
    plain(qa)
    target = Path(run_directory)
    if not target.is_absolute():
        target = repo / target
    # Check the lexical parent before resolving, so an existing linked target is also rejected.
    require(target.parent.resolve() == qa.resolve() and target.name.startswith("military6-legacy-"),
            "Destination must be a direct .superpowers/military6-legacy-* child of this M6 worktree")
    if target.exists() or target.is_symlink():
        plain(target)
    source = repo.parent / f"{loader}-military5" / ".superpowers" / source_name
    for ancestor in (source.parent.parent, source.parent, source):
        plain(ancestor)
    return source.resolve(), target.absolute()


def prepare(repo, case, run_directory):
    source, target = locations(repo, case, run_directory)
    require(not target.exists(), f"Refusing to overwrite an existing destination: {target}")
    before = inventory(source)
    for name in AUDITS.values():
        audit = read_json(source / name)
        require(audit["status"] == "PASS" and audit["dimension"] == CASES[case][2],
                f"Expected the frozen M5 restart fixture, not a fresh/incomplete world: {name}")
    properties = (source / "server.properties").read_text(encoding="iso-8859-1")
    require("level-name=world" in properties.splitlines(), "Only the known level-name=world fixtures are supported")
    require((source / "world" / "level.dat").is_file(), "Missing real world/level.dat")
    target.mkdir()  # No merge, overwrite, cleanup or writes to the M5 source.
    for relative in sorted(before["directories"], key=lambda p: (p.count("/"), p)):
        (target / relative).mkdir()
    for relative in before["files"]:
        with (source / relative).open("rb") as incoming, (target / relative).open("xb") as outgoing:
            shutil.copyfileobj(incoming, outgoing, length=1024 * 1024)
    require(inventory(target) == before, "Copy hash/file/directory mismatch; preserve this incomplete destination")
    require(inventory(source) == before, "M5 source changed during copying; preserve this incomplete destination")
    manifest = {
        "schema": 1, "status": "COPY_VERIFIED", "copyId": str(uuid4()), "case": case,
        "source": str(source), "destination": str(target), "dimension": CASES[case][2],
        "preparedUtc": datetime.now(timezone.utc).isoformat(), "snapshot": before,
        "sourceUnchangedAfterCopy": True, "copyHashesMatch": True, "gameplayPassClaimed": False,
    }
    with (target / MANIFEST).open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(manifest, stream, indent=2, ensure_ascii=True)
        stream.write("\n")
    return manifest


def load_manifest(repo, case, run_directory):
    source, target = locations(repo, case, run_directory)
    manifest = read_json(target / MANIFEST)
    require(manifest["schema"] == 1 and manifest["status"] == "COPY_VERIFIED" and manifest["case"] == case,
            "Invalid copy manifest")
    require(Path(manifest["source"]) == source and Path(manifest["destination"]) == target,
            "Manifest belongs to another source/destination")
    require(manifest["dimension"] == CASES[case][2] and manifest["sourceUnchangedAfterCopy"] is True
            and manifest["copyHashesMatch"] is True, "Copy verification was not completed")
    require(inventory(source) == manifest["snapshot"], "Frozen M5 source no longer matches the before-copy inventory")
    return target, manifest


def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def check_results(target, manifest):
    result = read_json(target / RESULT)
    require(result["schema"] == 1 and result["copyId"] == manifest["copyId"] and result["status"] == "PASS",
            "Independent M6 result has not passed both processes")
    processes = result["processes"]
    require(len(processes) == 2, "Exactly two M6 processes are required")
    require(len({p["pid"] for p in processes}) == 2 and all(type(p["pid"]) is int and p["pid"] > 0 for p in processes),
            "The two M6 processes must have different OS PIDs")
    require(len({p["processToken"] for p in processes}) == 2, "Repeated process token")
    manifest_sha = sha256(target / MANIFEST)
    previous = None
    for phase, process in zip(("first", "second"), processes):
        require(process["phase"] == phase and process["status"] == "PASS" and process["stoppingEvent"] is True,
                f"{phase}: missing successful stopping-time inspection")
        require(process["copyManifestSha256"] == manifest_sha and "military.6" in process["modVersion"],
                f"{phase}: wrong M6 version/copy manifest")
        require(process["serverTicks"] >= 60, f"{phase}: startup-only observation")
        started, stopped = instant(process["startedUtc"]), instant(process["completedUtc"])
        require(started < stopped, f"{phase}: invalid process time window")
        dimensions = process["loadedDimensions"]
        ids = [d["dimension"] for d in dimensions]
        require(len(ids) == len(set(ids)) and {manifest["dimension"], "minecraft:overworld"} <= set(ids),
                f"{phase}: missing/duplicate loaded dimensions")
        require(all(d[key] == 0 for d in dimensions for key in ("vehicles", "garages", "fleets")),
                f"{phase}: M6 automatically added a vehicle, garage or fleet")
        for key, filename in AUDITS.items():
            before, after = process["oldAuditsBefore"][key], process["oldAuditsAfter"][key]
            expected = (manifest["snapshot"]["files"][filename]["sha256"] if previous is None
                        else previous["oldAuditsAfter"][key]["sha256"])
            require(before["sha256"] == expected and before["sha256"] != after["sha256"],
                    f"{phase}/{key}: inherited PASS is not current-process proof")
            require(after["status"] == "PASS" and after["dimension"] == manifest["dimension"]
                    and started <= instant(after["updatedUtc"]) <= stopped,
                    f"{phase}/{key}: audit did not finish in this process")
        home_before, home_after = process["oldAuditsBefore"]["homes"], process["oldAuditsAfter"]["homes"]
        require(home_after["restartProcessId"] not in (home_before["restartProcessId"], home_after["firstProcessId"]),
                f"{phase}: homes audit did not record a fresh process")
        if previous is not None:
            require(process["modVersion"] == previous["modVersion"] and instant(previous["completedUtc"]) < started,
                    "M6 processes overlapped or changed mod version")
        previous = process
    for key, filename in AUDITS.items():
        require(sha256(target / filename) == previous["oldAuditsAfter"][key]["sha256"],
                f"Final {key} JSON differs from the second M6 process record")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=CASES, required=True)
    parser.add_argument("--run-directory", required=True)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check-source", action="store_true", help="Read-only: rehash the frozen M5 source")
    mode.add_argument("--check-copy", action="store_true", help="Read-only: compare source and copy BEFORE running M6")
    mode.add_argument("--check-results", action="store_true", help="Read-only: require two fresh M6 process results and unchanged M5 source")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    try:
        if args.check_source or args.check_copy or args.check_results:
            target, manifest = load_manifest(repo, args.case, args.run_directory)
            if args.check_copy:
                require(inventory(target) == manifest["snapshot"], "Copy differs from the initial M5 snapshot")
            if args.check_results:
                check_results(target, manifest)
            action = "RESULTS" if args.check_results else "COPY" if args.check_copy else "SOURCE_UNCHANGED"
            print(f"M6_LEGACY_{action} PASS case={args.case} directory={target}")
        else:
            manifest = prepare(repo, args.case, args.run_directory)
            files = manifest["snapshot"]["files"]
            print(f"M6_LEGACY_COPY PASS case={args.case} files={len(files)} bytes={sum(f['size'] for f in files.values())} "
                  f"directory={manifest['destination']} gameplay_pass_claimed=false")
    except (OSError, ValueError, KeyError, TypeError) as failure:
        parser.exit(1, f"M6_LEGACY FAIL: {failure}\n")


if __name__ == "__main__":
    main()
