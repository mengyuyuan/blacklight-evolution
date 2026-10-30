"""Isolated copy/result-gate fixtures. Retain evidence; never touch the real M5 sources."""

import copy
import json
import unittest
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4

import prepare_military6_upgrade as upgrade


class LegacyUpgradeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = Path(__file__).resolve().parents[1] / ".superpowers" / f"military6-legacy-fixtures-{uuid4().hex}"
        cls.evidence.mkdir()

    def fixture(self, case="neo-city", baseline=True):
        base = self.evidence / uuid4().hex
        loader, source_name, dimension = upgrade.CASES[case]
        repo = base / f"{loader}-military6"
        (repo / ".superpowers").mkdir(parents=True)
        source = base / f"{loader}-military5" / ".superpowers" / source_name
        for name in ("world/region", "world/entities", "world/dimensions/blacklight/city/data", "config", "defaultconfigs/empty"):
            (source / name).mkdir(parents=True, exist_ok=True)
        for name, data in {
            "world/level.dat": b"fixture-level-only-not-a-real-world",
            "world/region/r.0.0.mca": bytes(range(256)) * 1024,
            "world/entities/r.0.0.mca": b"fixture-entities",
            "config/blacklight.toml": b"enabled=true\n",
            "server.properties": b"level-name=world\r\nserver-ip=127.0.0.1\r\nserver-port=0\r\n",
            "eula.txt": b"eula=true\n",
            "extra-audit.json": b'{"historical":true}\n',
        }.items():
            (source / name).write_bytes(data)
        for name in upgrade.AUDITS.values():
            (source / name).write_text(json.dumps({"status": "PASS", "dimension": dimension}), encoding="utf-8")
        if baseline:
            (source / "MILITARY4-BASELINE.json").write_bytes(b'{"immutable":"fixture"}\n')
        return repo, source, ".superpowers/military6-legacy-fixture"

    def prepared(self):
        repo, source, directory = self.fixture()
        manifest = upgrade.prepare(repo, "neo-city", directory)
        return repo, source, directory, Path(manifest["destination"]), manifest

    def result_fixture(self, target, manifest):
        before = {key: {"status": "PASS", "dimension": manifest["dimension"], "updatedUtc": "2026-10-26T00:00:00Z",
                        "sha256": manifest["snapshot"]["files"][name]["sha256"]}
                  for key, name in upgrade.AUDITS.items()}
        before["homes"].update(firstProcessId="m5-first", restartProcessId="m5-second")
        processes = []
        for index, phase in enumerate(("first", "second"), start=1):
            after = copy.deepcopy(before)
            for key in after:
                after[key].update(updatedUtc=f"2026-10-27T{index:02}:01:00Z")
            after["homes"]["restartProcessId"] = f"m6-{phase}"
            for key, filename in upgrade.AUDITS.items():
                raw = {field: value for field, value in after[key].items() if field != "sha256"}
                (target / filename).write_text(json.dumps(raw), encoding="utf-8")
                after[key]["sha256"] = upgrade.sha256(target / filename)
            processes.append({
                "phase": phase, "pid": 100 + index, "processToken": f"token-{phase}", "status": "PASS",
                "stoppingEvent": True, "serverTicks": 120, "copyManifestSha256": upgrade.sha256(target / upgrade.MANIFEST),
                "modVersion": "0.3.4-preview.20261026-military.6", "startedUtc": f"2026-10-27T{index:02}:00:00Z",
                "completedUtc": f"2026-10-27T{index:02}:02:00Z", "oldAuditsBefore": copy.deepcopy(before), "oldAuditsAfter": after,
                "loadedDimensions": [{"dimension": dimension, "vehicles": 0, "garages": 0, "fleets": 0}
                                     for dimension in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "blacklight:city", "blacklight:world")],
            })
            before = copy.deepcopy(after)
        return {"schema": 1, "copyId": manifest["copyId"], "status": "PASS", "processes": processes}

    def write_result(self, target, result):
        (target / upgrade.RESULT).write_text(json.dumps(result), encoding="utf-8")

    def test_complete_hashed_copy_for_all_three_cases(self):
        for case in upgrade.CASES:
            with self.subTest(case=case):
                repo, source, directory = self.fixture(case)
                before = upgrade.inventory(source)
                manifest = upgrade.prepare(repo, case, directory)
                target, loaded = upgrade.load_manifest(repo, case, directory)
                self.assertEqual(upgrade.inventory(target), before)
                self.assertEqual(upgrade.inventory(source), before)
                self.assertEqual(loaded, manifest)
                self.assertTrue((target / "defaultconfigs/empty").is_dir())
                self.assertIn("MILITARY4-BASELINE.json", manifest["snapshot"]["files"])
                self.assertIn("extra-audit.json", manifest["snapshot"]["files"])
                self.assertFalse((target / upgrade.RESULT).exists())
                self.assertFalse(manifest["gameplayPassClaimed"])

    def test_existing_directory_is_never_merged_or_overwritten(self):
        repo, source, directory, target, _ = self.prepared()
        original = upgrade.sha256(target / upgrade.MANIFEST)
        with self.assertRaisesRegex(ValueError, "overwrite"):
            upgrade.prepare(repo, "neo-city", directory)
        self.assertEqual(upgrade.sha256(target / upgrade.MANIFEST), original)
        empty = repo / ".superpowers/military6-legacy-existing-empty"
        empty.mkdir()
        with self.assertRaisesRegex(ValueError, "overwrite"):
            upgrade.prepare(repo, "neo-city", str(empty))

    def test_missing_real_world_is_rejected_before_creating_target(self):
        repo, source, directory = self.fixture()
        (source / "world/level.dat").rename(source / "world/level.dat.fixture-missing")
        with self.assertRaisesRegex(ValueError, "level.dat"):
            upgrade.prepare(repo, "neo-city", directory)
        self.assertFalse((repo / directory).exists())

    def test_optional_m4_baseline_is_optional(self):
        repo, source, directory = self.fixture(baseline=False)
        manifest = upgrade.prepare(repo, "neo-city", directory)
        self.assertNotIn("MILITARY4-BASELINE.json", manifest["snapshot"]["files"])

    def test_destination_cannot_escape_m6_qa_or_select_the_wrong_loader(self):
        repo, source, directory = self.fixture()
        for case, target in (("forge-city", directory), ("neo-city", str(source)), ("neo-city", "run"),
                             ("neo-city", ".superpowers/old-world")):
            with self.subTest(case=case, target=target), self.assertRaises(ValueError):
                upgrade.prepare(repo, case, target)

    def test_source_rehash_detects_content_additions_and_empty_directory_changes(self):
        for name, directory in (("config/changed.toml", False), ("defaultconfigs/new-empty", True)):
            repo, source, run, target, manifest = self.prepared()
            if directory:
                (source / name).mkdir()
            else:
                (source / name).write_bytes(b"changed fixture")
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "source no longer matches"):
                upgrade.load_manifest(repo, "neo-city", run)

    def test_copy_failure_preserves_partial_directory_without_verified_manifest(self):
        repo, source, directory = self.fixture()
        original_copy = upgrade.shutil.copyfileobj
        changed = False

        def mutate_fixture(incoming, outgoing, length):
            nonlocal changed
            original_copy(incoming, outgoing, length)
            if not changed:
                changed = True
                (source / "world/level.dat").write_bytes(b"fixture changed during copy")

        with patch.object(upgrade.shutil, "copyfileobj", side_effect=mutate_fixture):
            with self.assertRaisesRegex(ValueError, "mismatch|changed during"):
                upgrade.prepare(repo, "neo-city", directory)
        self.assertTrue((repo / directory).is_dir())
        self.assertFalse((repo / directory / upgrade.MANIFEST).exists())

    def test_m5_pass_alone_is_not_m6_process_evidence(self):
        repo, source, directory, target, manifest = self.prepared()
        with self.assertRaises(FileNotFoundError):
            upgrade.check_results(target, manifest)
        result = self.result_fixture(target, manifest)
        result["processes"][0]["oldAuditsAfter"] = copy.deepcopy(result["processes"][0]["oldAuditsBefore"])
        self.write_result(target, result)
        with self.assertRaisesRegex(ValueError, "inherited PASS"):
            upgrade.check_results(target, manifest)

    def test_two_fresh_process_result_contract(self):
        repo, source, directory, target, manifest = self.prepared()
        self.write_result(target, self.result_fixture(target, manifest))
        upgrade.check_results(target, manifest)

    def test_gate_binds_final_original_audits_to_second_process_record(self):
        repo, source, directory, target, manifest = self.prepared()
        self.write_result(target, self.result_fixture(target, manifest))
        (target / upgrade.AUDITS["homes"]).write_bytes(b'{"status":"PASS","stale":true}')
        with self.assertRaisesRegex(ValueError, "Final homes JSON"):
            upgrade.check_results(target, manifest)

    def test_gate_rejects_pid_reuse_stale_pass_late_spawns_and_incomplete_runs(self):
        repo, source, directory, target, manifest = self.prepared()
        mutations = {
            "one-process": lambda r: r["processes"].pop(),
            "same-pid": lambda r: r["processes"][1].update(pid=101),
            "failed-first": lambda r: r["processes"][0].update(status="FAIL"),
            "no-stop-hook": lambda r: r["processes"][1].update(stoppingEvent=False),
            "startup-only": lambda r: r["processes"][0].update(serverTicks=1),
            "stale-pass": lambda r: r["processes"][0]["oldAuditsAfter"]["city"].update(updatedUtc="2026-10-26T00:00:00Z"),
            "old-home-token": lambda r: r["processes"][0]["oldAuditsAfter"]["homes"].update(restartProcessId="m5-second"),
            "wrong-source-chain": lambda r: r["processes"][1]["oldAuditsBefore"]["city"].update(sha256="0" * 64),
            "wrong-version": lambda r: r["processes"][1].update(modVersion="military.5"),
            "missing-dimension": lambda r: r["processes"][0].update(loadedDimensions=[]),
        }
        for key in ("vehicles", "garages", "fleets"):
            mutations[f"late-{key}-in-other-dimension"] = lambda r, key=key: r["processes"][1]["loadedDimensions"][1].update({key: 1})
        for name, mutate in mutations.items():
            result = self.result_fixture(target, manifest)
            mutate(result)
            self.write_result(target, result)
            with self.subTest(name=name), self.assertRaises(ValueError):
                upgrade.check_results(target, manifest)

    def test_both_installed_preparation_scripts_match(self):
        repo = Path(__file__).resolve().parents[1]
        self.assertEqual((repo / "tools/prepare_military6_upgrade.py").read_bytes(),
                         (repo.parent / "forge-military6/tools/prepare_military6_upgrade.py").read_bytes())


if __name__ == "__main__":
    unittest.main(verbosity=2)
