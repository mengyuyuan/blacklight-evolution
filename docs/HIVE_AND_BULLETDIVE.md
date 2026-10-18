# Hive defense and Bulletdive impact

Both loaders use the same gameplay rules.

- A hive is a stationary encounter structure. Pushes and the shared stagger/knockdown controller cannot animate it as a fallen creature.
- Conventional damage is multiplied by 0.10 and capped at 2 authored damage units per accepted hit. A separate server clock accepts at most one conventional hit per 10 ticks. This clock is independent of vanilla `invulnerableTime`, which gun mods can reset. Existing infected health-unit conversion still applies once.
- An active Prototype's direct melee/native skills and Bulletdive retain full damage. Being infected or firing a gun while in Prototype mode does not turn a projectile into a native melee hit. Actual `/kill` and void damage remain administrative/environmental exceptions.
- Bulletdive uses the player-owned `blacklight:bulletdive` damage type. It bypasses vanilla armor, shields, enchantment protection, resistance/effect reduction, and ordinary hit cooldown. Damage/death events, creative/admin invulnerability, PvP/target eligibility and loot ownership remain intact. It never removes targets or writes their health directly.
- Main impact damage is `base × (1 + actualDrop / 30)`. The default base is 22 authored units: 30 blocks = 44; 300 blocks = 242; 60,000 blocks = 44,022. Existing infected health-unit conversion applies to infected targets. Only numeric overflow saturates at a finite float; no gameplay height cap was added. Terrain work and visual budgets remain independent.

For the configurable cross-mod incoming budget, outgoing multiplier and native true damage, see [CROSS_MOD_COMBAT.md](CROSS_MOD_COMBAT.md). The impact formula above is the base amount before the external-target multiplier.

## Installed-mod compatibility verified on 2026-10-18

The opt-in `src/compatTest` GameTests load real third-party binaries. They invoke TaCZ's installed `createDamageSources` and `tacAttackEntity` routines, including its normal/AP split and invulnerability resets. This verifies the installed server hit pipeline, not physical projectile travel or a manually fired magazine.

| Runtime | Exact external build | Verified outcome |
| --- | --- | --- |
| NeoForge 1.21.1 / 21.1.244 | [TaCZ unofficial 1.1.8-hotfix-r7](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1/files/9027337) | 30 hits, rotating 0/50/100% AP, 100 damage per shot over 120 ticks: hive loses 25 of 800 HP with cross-mod defense enabled; no knockdown. Prototype melee still deals full scaled damage. |
| NeoForge 1.21.1 / 21.1.244 | [TheTitansNeo 1.0.8](https://www.curseforge.com/minecraft/mc-mods/thetitansneo/files/8210650) | Reproduced 1,000-damage cap; optional bridge permits the 60,000-block dive (44,022 base impact before the external-target multiplier) to kill the vulnerable zombie titan through its own hurt/death pipeline. Ordinary external weapon cap and invulnerability are preserved; native Blacklight true attacks also bypass the cap. |
| Forge 1.20.1 / 47.4.10 | [TaCZ 1.1.8-hotfix2](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989) | Same installed 30-hit test passes. |
| Forge 1.20.1 / 47.4.10 | [Potentia beta 0.12.2](https://www.curseforge.com/minecraft/mc-mods/potentia-titans-mod-returns/files/8810218) | Zombie titan (21,000 HP) dies from the 60,000-block dive. Spider titan's separate cap reproduced and bridged; a height-scaled hit exceeding its remaining HP kills it without skipping its native death path. Ordinary external weapons remain capped and invulnerability remains effective; native Blacklight true attacks also bypass the cap. |

Potentia is a separate revival/reimagining, not the same implementation as TheTitansNeo. These results do not promise a kill against every titan, immune phase, or different release.

Potentia beta 0.12.2 also ships required `thetitans:*` damage IDs which do not exist in its registry. They invalidate several vanilla damage tags. The Forge bridge makes only unresolved legacy element entries from that mod's own damage-tag resources optional. Valid entries, other packs, tag references, and removal flags are preserved. Installed tests verify that Bulletdive's armor, shield and enchantment bypass tags load again.

TheTitansNeo 1.0.8 also lists two unregistered pig-zombie entity types in its undead tag. The NeoForge bridge makes only those missing element IDs from that mod's own entity-tag resources optional. Installed tests verify that vanilla undead, smite and inverted-healing tags remain available. Other packs and valid IDs are unchanged.

The bridges are optional and do not add a runtime dependency. They do not set target health, discard live targets, bypass creative/admin invulnerability, or cancel third-party death events. Third-party binaries are test inputs only, not redistributed in Blacklight packages.

### Reproducing installed tests

Use an isolated test world with the exact external builds above. For NeoForge, place TaCZ and TheTitansNeo in the test run directory's `mods` folder and run `gradlew runGameTestServer -PcompatLiveTest -PgameTestRunDirectory=<directory>`.

Forge development runtimes require remapped external binaries. Stage them using `python tools/prepare-installed-compat.py --tacz <jar> --potentia <jar> --repository <directory>`, then run `gradlew runGameTestServer -PcompatLiveTest -I tools/installed-compat.init.gradle -PcompatRepository=<directory> -PgameTestRunDirectory=<run-directory>`. Keep the original production JARs out of the Forge development run's `mods` folder. Normal builds omit these tests and dependencies.

### Exact downloaded binary hashes (SHA-256)

- TheTitansNeo: `2123ca02e4d045726d97ae8efd9e2b9935aab12fd222b5d165151371151edb05`
- NeoForge TaCZ: `fa3a09f15265a4065a566960a8deab315f6c601a5f9a542840b362d455dd5470`
- Forge TaCZ: `2cbb89f44333d2eb4e825d86d789ff119b272ff75184390b55bafd4f0e7f10c2`
- Potentia: `7a57e83e9a8535e7c9a978b0d833d2048ca6c1776dcf7407ba2705a042549f4b`
