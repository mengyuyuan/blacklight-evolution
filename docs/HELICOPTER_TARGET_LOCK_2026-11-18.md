# Helicopter target lock correction

The empty-handed helicopter rider never passed `ExplicitTarget.enabled`: that
gate covered prototype mode, hand-held firearms and ground vehicles only. This
blocked the Tab input handler and the server's target-lock request. The aircraft
control sender also ignored the selected target and always used the camera ray.

Both loaders now allow a living rider in a non-crashing helicopter through the
existing gate. `HelicopterControls` uses `HelicopterManualAim` to choose the visible
selected skin point and convert it to world yaw/pitch from the chin gun. Lost or
covered targets fall back to the free crosshair. The server's ordinary seat
authority, mount slew/limits, ammunition and physical shot collision remain in
force; a target lock cannot guarantee a hit outside the weapon's traverse.

Helicopter occupants canonicalize to their airframe only for explicit targeting.
The aircraft is one lock candidate rather than a hull plus six competing crew
candidates. Grabbing/hijacking keep their existing living-body paths. Own craft
and its occupants are excluded, positive-health CRASH aircraft release locks,
and occupant allegiance/PvP protection still gates the canonical airframe.

Ground prototype and rifle targeting already supported the aircraft hull. The
existing structural skin supplies its closest visible point, so the aim marker
is neither a fixed tail anchor nor a selected cockpit passenger. Network lock
acknowledgement, HUD, outline and chase-camera paths already use generic Entity
identities; no packet layout change is needed.

Lock range is unchanged: client render distance times 16 and server view distance
times 16, each clamped to 32–256 blocks. The server independently validates its
range. This is separate from original weapon reach (30 mm: 175) and AI firing
envelopes (60/100), none of which were altered by this change. These lock rules
are the mod's player interface, not a claim about original Prototype controls.

`HelicopterTargetLockGameTest` adds four actual-world regressions in the
`helicopterTest` source set:

- Empty-handed driver: acquire a ground target, run the exact shared client aim
  conversion, feed the real seat lease/input and receive one finite cannon hit.
- Empty-handed copilot: the same chain with a separate real connected driver.
- Ground prototype/rifle: canonical aircraft candidate, nearest structural skin,
  and a real off-axis rifle use damaging the aircraft with one-round debit.
- Own occupants, protected player crew, complete wall and positive-health CRASH:
  rejection/release plus crosshair fallback for a stale blocked lock.

These tests do not synthesize a physical Tab key, render an outline, or inspect
the cockpit HUD. Client keyboard/render acceptance still requires a client
check. Compilation and runtime results are recorded by the parent integration
run; this edit did not run Gradle or open a player save.

## Parent integration results

Final isolated runs passed on both NeoForge 1.21.1 and Forge 1.20.1: 145 unit tests (zero failures/errors) and all 26 registered required GameTests. Logs: `D:/CodexCache/work/helicopter-final-verified-neo-20261118.log` and `D:/CodexCache/work/helicopter-final-verified-forge-20261118.log`. No release JAR was installed and no client was launched during this verification.

The driver and copilot both used the real shared lock-to-gun conversion, accepted seat input and one finite cannon round: actual target health 2500 to 2400, source operator and aircraft both matched, target alive and registered, shots=1, remaining rounds=0. Ground prototype/rifle targeting, own/protected occupants, cover and positive-health CRASH rejection also passed. Physical Tab dispatch, HUD/outline rendering and the player's movement feel remain client acceptance items.

The NPC sortie used the same aircraft across two public missions, preserved depleted player stores, saved/reloaded shot counters, observed all seven second-sortie flight phases, and reached 9 Hellfires with real attributed damage. NeoForge recorded 174 gun shots and Forge 220; the stochastic source maneuver choice can change counts. Friendly-aircraft removal allowed a real missile hit after 23 ticks (NeoForge) and 22 ticks (Forge), with hull loss 10. Neither source weapon timers nor projectile range was relaxed.

Earlier integration failures were retained in `helicopter-final-neo-20261118.log` and `helicopter-final-forge-20261118.log`. Lock fixtures incorrectly compared post-entry infected health against the pre-entry 500 instead of the actual scaled 2500; they now compare actual pre-shot health, retaining all damage ownership, alive/registration and one-round assertions. Symmetric nose surfaces produce two different points at exactly the same nearest distance (283.66182270198084 squared); the assertion now verifies nearest distance and real forward skin attachment, then still requires the rifle hit. Failed lock fixtures now clean up their actual players. The two long NPC target fixtures now explicitly require persistence so unrelated distant players cannot naturally despawn the stationary test opponent; they fail immediately if that target is removed. These are fixture corrections, not weakened production safety or fire rules.
