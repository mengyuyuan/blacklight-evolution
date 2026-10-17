# Sovereign flight animation provenance

Retrieved 2026-10-16 from Rokoko's official free-resource page:
https://www.rokoko.com/resources/rokoko-mocap-15-free-superhero-animations

The publisher identifies Sam Lazarus as the capture creator and describes
permission for animation, VFX, games and commercial projects. This is not a
claim that the resource is CC0 or that it inherits the code license.
The page's public download points to:
https://drive.google.com/drive/folders/1qhnHjvosA8V6ChhhcFTbCbt5MppfzcPS

The current `Superhero.zip` contains nine FBX files despite the page's older
title saying fifteen animations. Only `SuperHeroFlying_mixamo.fbx` and
`SuperHeroLanding_Takeoff_mixamo.fbx` supply the in-game animation data.
Raw downloads remain outside the repository in
`E:/Blacklight-Wings-20261016/references/`.

SHA256:
- archive: b911e7b200c66916d812c2e9c392df458c5cae2c093d7d3ce832749d24aaad72
- flying FBX: e9ed5f2387bc574f2a4267aaa8ca150116357561cbd2d661ece8050bea95ccfa
- landing/takeoff FBX: bb03d43a3657cb842d6e86b91675febd3587f1e31e2fc00a7587bba7f0068d41

## Conversion and edits

`retarget_flight.py` reproduces the Blender conversion. It maps the Mixamo
skeleton to twelve native Blacklight joints, calibrates the source T-pose
against the target arms-down bind, removes root travel/yaw and retains target
limb lengths. Source FBX time is imported on Blender's 24 fps clock; exported
clips are sampled at 30 fps. Exact source ranges and changed durations are
recorded in the script and exported resource.

Six clips: takeoff, landing, hover, cruise, boost, brake. Loop tails are
crossfaded. Two quaternion filter passes remove capture spikes before the
time-compressed gameplay playback; floor contact is recalculated afterwards.
Arms are adapted to trail alongside the body instead of the
source Superman fist-forward gesture; hover legs are relaxed. Ground clips
use foot-height correction. These are adapted mocap clips, not untouched
originals or wholly new capture.

Runtime transitions start from the currently visible pose when interrupted.
Steering adds a small bank and chest lean. Wing articulation remains
project-authored: deployment, launch flare, acceleration sweep and gentle
tip lag. It is not the wing motion of a purchased pack. The Fab Winged
Warrior pack was researched but not purchased or incorporated.
