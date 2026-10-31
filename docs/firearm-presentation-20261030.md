# Firearm presentation and reload controls

Right-click fires, including while crouched. R reloads the held firearm by
default; it is registered as a normal remappable Blacklight key. The main hand
has priority when both hands contain firearms. An empty weapon, or a reload
request with no reserve, names the actual compatible ammunition item, the
reserve count and the currently mapped reload key in the action bar. Loaded
weapons retain their existing finite-ammunition rules, including in creative.

The C2S request contains only a hand index. The server resolves the real held
item and validates life state, consumption control, cooldown, inventory and
reload deadline. Invalid hand values and repeated requests cannot mint rounds
or shorten loading. Old saved firearms keep their magazine data.

The shared non-looping reload timeline lowers the weapon, extracts the
magazine, fetches and inserts its replacement, works the action and returns
to aim. First-person hands use the existing player skin and authored magazine
coordinates. NPCs use the same timeline. Three staged server sound cues follow
the persisted deadline; reopening a view does not restart a reload.

Server hit receipts drive bounded, cosmetic client muzzle flashes, tracers,
casings and surface impacts. Rockets and shells have distinct 3D geometry and
local trails. Cannon fire includes barrel recoil and staged impact effects.
Projectile physics, actual hit checks, occlusion, terrain permissions and
finite vehicle ammunition remain server-owned. No client supplies damage or
arbitrary effect counts. The protocol is standalone-44-military-gunplay;
client and server must update together.

Client preferences live in config/blacklight-gunplay-client.toml: cameraKick,
tracers, casings and effectLimit. Camera feedback affects rendering only.
The default effect limit is 96, with an absolute cap of 128 and a per-tick
receipt cap. New audio totals about 130 KiB, with CC0 provenance in
docs/assets/firearm-audio-sources.json and licenses/firearm-audio-CC0.txt.

## Validation

The focused regression run passed 67 tests. It covers the shared reload/recoil timeline, view
state, hand transforms, Blackwatch rig and vehicle definitions/models.
The private GameTest passed and exercises the actual R request handler, exact reserve
debit, repeated and malformed requests, reload sound stages, early-fire
rejection, crouched firing, wall occlusion and an empty reserve.

The opt-in gunplayTest client fixture passed. It uses a fresh run-gunplay-* directory,
creates its own flat save and records real framebuffer images. It presses
the registered R binding through Minecraft's input queue and receives server
feedback through the real local network path. It never opens an existing
save or sends desktop mouse/keyboard input. Its classes are excluded from
release JARs. The final evidence and package checksum are retained outside
the repository in E:/Blacklight-Military-Purge-20261030.

The real-client images include the ammo-specific empty action-bar hint,
first-person reload stages, the front view of a reloading soldier, rifle
tracers/casings and a tank's cannon contact. Vehicle health remains truck 100,
APC 240 and tank 420. Zero health still uses the existing passenger-ejection
and removal flow; persistent wrecks and a burning destruction sequence are
outside this change.

This is a targeted gunplay validation. The previous full PR audit still has
four known campaign/city-test failures; it is not a claim that full CI passes.
