# 0.3.3 · NeoForge 1.21.1

Shield Borer now has sequential upgrades in Utility skills and its own per-player switch in Settings. Learning it no longer automatically makes shield sprinting excavate terrain: the switch defaults to off, including existing saves without this preference.

| Skill | Cost | Forward reach | Break limit per player/tick |
| --- | --- | --- | --- |
| Shield Borer | 350 EP | About 1.5 blocks | 6 |
| Shield Borer Boost I | 250 EP | About 2 blocks | 9 |
| Shield Borer Boost II | 450 EP | About 2.5 blocks | 12 |

- Each upgrade requires the preceding level and Shield. Existing skills and progression bits are retained.
- Turning excavation off preserves upgrades and restores ordinary movement/wall traversal priority immediately. Re-enable it in Settings to excavate again.
- The switch persists through saves/death and synchronizes from the server. Each settings request changes only its named preference; Shield Borer and Bulletdive choices cannot overwrite one another.
- The server selects block positions and enforces learned reach. All tiers retain the shared 24-block/server-tick limit, time/load budgets, normal loot, protection events, and floor/liquid/unbreakable exclusions.
- Tutorial text shows the new setup and upgrades with remapped controls. The existing shield-running GIF is a pose demonstration, not a new excavation recording.
- Network protocol advances to standalone-6 for the new preference and request formats. Update clients and server together.
- Other weapon-specific upgrade trees are not added in this patch. Their existing form unlocks and shared Attack Extension upgrades remain.

Validation: unit regressions cover sequential purchases, persistence, tier reach, independent settings and packet codecs. Dedicated-server GameTests cover real excavation, default-off behavior, immediate stop, learned-tier enforcement and protection at the highest tier, alongside retained pickup and terrain-load tests.
