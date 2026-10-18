# Hive defense and Bulletdive impact

Both loaders use the same gameplay rules.

- A hive is a stationary encounter structure. Pushes and the shared stagger/knockdown controller cannot animate it as a fallen creature.
- Conventional damage is multiplied by 0.10 and capped at 2 authored damage units per accepted hit. A separate server clock accepts at most one conventional hit per 10 ticks. This clock is independent of vanilla `invulnerableTime`, which gun mods can reset. Existing infected health-unit conversion still applies once.
- An active Prototype's direct melee/native skills and Bulletdive retain full damage. Being infected or firing a gun while in Prototype mode does not turn a projectile into a native melee hit. Actual `/kill` and void damage remain administrative/environmental exceptions.
- Bulletdive uses the player-owned `blacklight:bulletdive` damage type. It bypasses vanilla armor, shields, enchantment protection, resistance/effect reduction, and ordinary hit cooldown. Damage/death events, creative/admin invulnerability, PvP/target eligibility and loot ownership remain intact. It never removes targets or writes their health directly.
- Main impact damage is `base × (1 + actualDrop / 30)`. The default base is 22 authored units: 30 blocks = 44; 300 blocks = 242; 60,000 blocks = 44,022. Existing infected health-unit conversion applies to infected targets. Only numeric overflow saturates at a finite float; no gameplay height cap was added. Terrain work and visual budgets remain independent.

## Compatibility limits

The automatic test reproduces TaCZ's two-part normal/AP hit pattern and resets vanilla invulnerability between hits. It verifies structural defense through the real damage pipeline, but is not a claim of playing with a particular installed TaCZ build.

TaCZ implementation reference: https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/entity/EntityKineticBullet.java

There are several Titan ports. The inspected `AquaThree/TheTitansNeo` base class explicitly caps non-Titan damage at 1,000 and checks whether players may hurt it. Generic true damage alone cannot promise an instant kill against that custom rule. No speculative reflection or health override was added without the user's actual runtime mod to test.

Source: https://github.com/AquaThree/TheTitansNeo/blob/main/src/main/java/net/byAqua3/thetitansneo/entity/titan/EntityTitan.java

## Available runtime candidates checked on 2026-10-18

- NeoForge 1.21.1: TheTitansNeo 1.0.8, an unofficial remaster: https://www.curseforge.com/minecraft/mc-mods/thetitansneo/files/8210650
- Forge 1.20.1: Potentia: Titans Mod Returns beta 0.12.2, a separate revival/reimagining, not the same implementation: https://www.curseforge.com/minecraft/mc-mods/potentia-titans-mod-returns/files/8810218
- TaCZ Forge 1.20.1: https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero
- TaCZ NeoForge 1.21.1 unofficial port: https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1

These listings establish version availability only. They do not establish binary compatibility or constitute installed-mod testing. Titan-specific integration remains unimplemented; the generic damage and TaCZ-pattern tests above are the current tested scope.
