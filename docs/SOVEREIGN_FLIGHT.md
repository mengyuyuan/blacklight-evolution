# wings.13：翼面气流与前置音障薄膜

- 原有无升级冲刺为 25 格/秒，普通巡航仍为 10.4 格/秒。
- 技能页面「移动」新增「高速飞行 I / II / 超音速飞行 III」。前置为君主之翼，逐级购买，分别消耗 600 / 1200 / 2400 进化点；速度上限依次为 80 / 200 / 440 格/秒。
- 只有持续向前疾飞应用升级极速。侧移、后退、纯升降保留精细控制；松开疾跑或方向键制动。转向保留速度惯性。
- 游戏音速参考固定为 343 格/秒（1 格视作 1 米、20 tick/s），不是按温度计算的大气模拟。
- 起步重下拍、慢回收；总拍翼周期不随冲刺加快。高速逐渐后掠收翼，保持受力下压和翼尖滞后。
- 头部流光固定到实际渲染头骨位置，向后绕开脸部；跨音速产生双层气环、共享音爆声与短促视野脉冲。约 189 格/秒起渐显子弹头状薄膜，达到音速后完整显示并持续保留：圆尖位于头部前方，后缘开放并渐隐，不再形成包围身体的后部球罩；边缘提亮、中央透明。效果透明度、尾迹和数量有上限。
- 翼面迎风边薄雾、下拍短气流和左右反向旋转的翼尖尾涡，采样实际变形后的翼骨位置，随收翼、转向及拍翼变化。效果采用有限条带，没有新增气流实体或网络包。
- 速度、购买和解锁由服务端校验；使用原版扫掠碰撞，未关闭穿墙/超速校验。对至多 5 个合并到达的移动包按飞行预算校验；接收后用已验证位移修正速度，避免撞墙后服务端继续累加。
- 实际速度根据前方区块就绪范围自动调整，升级只决定理论极速；加载快就加速，加载慢就提前减速。额外保留约 8 个飞行 tick 的加载余量，区块成批到达时限速缓慢回升，减少忽快忽慢。只探测已就绪区块，并保留硬刹车距离校验；不强制生成远处地形。探索未生成地区不保证维持极速，实际速度受区块加载与服务器 TPS 限制。
- 修复高速爬升时本地玩家被当前 tick 包围盒错误剔除的问题，第三人称使用限定距离内的插值位置保留角色渲染。
- 音爆不造成方块破坏，不额外增加战斗伤害。

# Sovereign flight prototype — Forge 1.20.1

Unlock Sovereign Wings in the skill page, then hold the remappable ability key
(default Z), press TAB for the second page (Basic abilities), select Sovereign
Wings and release. Selecting it again retracts
the wings. Weapons, a defence and a sense can coexist with the wings.

Jump starts flight / ascends; sneak descends; movement keys steer relative to
the camera; sprint accelerates. Releasing movement brakes to a hover. Landing
returns to ordinary ground movement. Water, riding, spectator and creative
flight relinquish this controller. The server owns eligibility and flight
phase; vanilla movement collision remains active.

## Animation revision

The current body playback uses six adapted Rokoko mocap clips: takeoff,
hover, cruise, boost, brake and landing. The official free superhero FBX
files were downloaded and retargeted to the native player bind. Source ranges,
hashes, terms and the reproducible Blender exporter are in
`art/sovereign-wings/FLIGHT_SOURCES.md`. The previous formula-only body poses
have been replaced. Superman-style arms were adapted to trail alongside
the Blacklight body. Hover/cruise/boost retain captured limb follow-through,
with navigation owning the main body inclination. A shared wingbeat adds small
muscular follow-through; it never rotates the body by the wing joint angles.

Equip/retraction now uses a dedicated brace/open/recover gesture instead of
the armour transformation clip. It adds local rotations only and preserves
the head, shoulder joint offsets and ground leg gait.

Takeoff has an eight-tick planted windup. Landing plays a short contact
compression and recovery. Interrupted transitions blend from the visible
pose rather than resetting to idle. Forward velocity, lateral travel and
yaw rate add a smoothed directional blend. Backward movement does not lean
into the forward-flight clip. Camera position is not driven by the capture.

Wings sample the authored Fly action from GreyFrogGames Flying Bat v1.0,
licensed CC BY 4.0. Source arm and finger curves are extracted at 121 points,
retargeted to the concept wing's unusual up-curved arches and long blades,
and mirrored. The main stroke spans about 56 degrees in neutral hover;
secondary arm and finger curves preserve the source's separate timing.
The fingers spread outboard during flight so a broad downstroke does not
sweep the inward-pointing concept blades through the body. Source attribution,
license and reproducible extraction are in `THIRD_PARTY_ASSETS.md` and
`art/sovereign-wings/extract_bat_wingbeat.py`.
The roughly 0.8–1.3 Hz cadence is integrated continuously; speed or direction
changes do not restart the cycle. Forward flight sweeps the stroke aft,
backward flight and braking reverse its plane, ascent increases amplitude,
and descent relaxes it. Lateral travel and turning vary the two wings' sweep
and stroke amplitudes. Colour and emissive passes share the same sampled pose.
The stroke axis is counter-transformed through the current chest rotation,
so horizontal body flight still produces a downward stroke in the upright
entity frame instead of silently turning it into a fore/aft swing.
Grounded wings fold without a flight beat. This is visual articulation; it
does not introduce oscillating velocity or camera shake into flight physics.

The Fab Winged Warrior listing was located as a possible external animation
source, but its animations have not been downloaded, licensed or integrated.
This build must not be described as using that pack. The current reconstruction
is `model-v15`: 669 meshes / 22,979 triangles, two 256 px wing atlases and 35 wing
joints. Continuous faceted spars and membranes replace the cuboid assembly.
Four unequal blades, traced apertures, overlapping plates and branching seams
follow the supplied sheet. Depth and unseen surfaces are inferred.

The complete player torso, shoulders and sleeves remain present. Thin fixed
scapular tissue grows from the back into the wing roots and follows the chest
matrix. Only the outer rig articulates. No shoulder caps replace the player's
skin, and no wingbeat moves the shoulder attachment independently of the body.
Organic hooks are anchored to the actual retraced bone stations, so they cannot
float at coordinates inherited from older model candidates.

Flight capture neck yaw/roll is removed for the cuboid head; forward/up pitch
is retained with a rear neck-edge fulcrum so the chin/hat does not sweep
through the shoulders. This retarget affects only the six flight body clips.

Both folded poses are authored explicitly: left wing outside or right wing
outside. The server chooses once when flight ends, preserves that choice
through idle / phase changes and sends it to tracking players. Rendering does
not draw random numbers. The fold blends over 24 ticks with staggered joints.

Rebuild with `build_reference_mesh_v15.py detail`, then run
`render_mesh_wings.py` in Blender with `-- --detail --v15`, and
`validate_mesh_export.py -- --v15` for the weighted GLB round-trip.
Copy the resulting `model-v15/sovereign_wings.asset.json` to
`src/main/resources/assets/blacklight/wing_models/sovereign_wings.json`.
Older cuboid/body-surface authoring scripts are historical, not the runtime source.
The body clips remain adapted Rokoko capture; the wingbeat uses the credited bat animation.

## Equip and movement transitions

Equipping no longer automatically launches on a transient ground-contact gap.
The shared locomotion controller keeps ticking with wings equipped, so ground
walk/run poses and player facing continue to update. Flight-facing does not use
a stale ground locomotion heading. Steering redirects existing boost momentum
more quickly while preserving straight-line acceleration and maximum speed.

The server synchronizes the wing equip/retract timestamp. Tissue grows from
the embedded roots through proximal arches to distal blades over 32 ticks;
a short unfurl settles into the folded pose. Retraction takes 22 ticks.
The dedicated local brace/open/recover gesture preserves the ground leg gait.
Moving surfaces use continuous weights instead of assigning adjacent plates
to unrelated rigid bones.

## Acceleration and staged wake

`WingFlightPresentation` derives flow, thrust and braking from actual movement.
Hard acceleration deepens the downstroke, sweeps it aft, increases cadence and
adds delayed distal follow-through. Body/limbs react to acceleration and braking.
These values interpolate from tick snapshots and decay smoothly when released.

World-space wakes start at four posed wing endpoints. Initial movement has fine
slipstreams; acceleration adds a short air pulse; sustained fast flight has a
red core and bounded speed-line/lens effect. Takeoff and landing use ground air
pulses with dust only near solid ground. Trail life shortens at high speed to
avoid long rays crossing the third-person camera. Effects have actor/history/
pulse caps and require no added entities, terrain writes or particle packets.
The FOV change respects Minecraft's FOV Effects setting. See
`art/sovereign-wings/FLIGHT_FINISH_PLAN.md` for the choreography and limits.

## Previous verification history (superseded; wings.11 acceptance below)

2026-10-17 `wings.10` keeps the v11 geometry and corrects its runtime binding:
fixed scapular caps, stabilized looping torso and dedicated wing transformation.
The rendered-vertex regression samples both folded orders, partial deployment
and wingbeat phases: shoulder caps stay fixed while the wings articulate.
Dense head clearance and recovery-side tests pass with the revised wrist fold.
The isolated `Wing Shoulder QA` integrated-client run exercised wings alone:
equip, walking, takeoff, hover, boost/turn, reverse/sideways movement, descent,
landing and retraction. Front/back crops were inspected for shoulder coverage.
Log: `E:/Blacklight-Wings-20261016/qa/client-shoulder-after.log`;
poses: `client-shoulder-after.csv`; frames: `frames-shoulder-after/` in the same
QA folder. 758 frames were saved; 111 screenshot requests were dropped by the
bounded writer queue. This is visual coverage, not a frame-rate benchmark.
One 2.255 s startup/terrain setup lag warning occurred; no runtime ERROR,
movement-validation failure or crash was logged. Dedicated multiplayer and
user visual acceptance of this revision remain unverified.

2026-10-16 `wings.9` / `model-v11`: 383 tests passed, zero failures/errors/skips;
normal offline `test build`, standalone boundary and bundled-resource checks
passed. The original skin and replacement share the same surface/weights,
and source-side shoulder labels are now mapped to the actual native arm on
that side (the previous labels drove the opposite arm). Coverage tests check
retraction, retained centre skin, hands and legs. Head clearance is sampled
through all six flight clips with both folded orders and steering extremes.
Fresh GLB import agrees at open/middle/closed bounds within 0.00001 model unit.
The JAR contains the matching model, wing/body textures and excludes the QA
fixture. Front/back/side previews are actual Blender geometry renders with a
gray body proxy and the replacement skin material, not Minecraft screenshots.
This revision has not been rerun in a Minecraft client; a separate game is
currently running. The in-game tutorial GIF is still an older capture and
must be refreshed after client visual acceptance. Dedicated multiplayer
acceptance is also outstanding.

Superseded by the requested body replacement: `wings.8` / `model-v10` passed
381 tests and an isolated integrated-client flight run (860 frames, one writer
queue drop). The user rejected its fragmented shoulder mantle, so that run
must not be presented as visual acceptance of `model-v11`.

Superseded after shoulder reference feedback: 2026-10-16 `wings.7`: model/GLB checks passed for both folded orders, including
neutral-head clearance and closed-ground clearance. The exported GLB was
reimported and compared at open / middle / closed frames. The randomized
fold-order tests passed. All 377 Java tests passed (zero failures, errors or
skips); offline build, standalone boundary and bundled-resource checks passed.
The JAR contains matching final cuboids/textures and excludes the QA harness.
The current Forge play session was left untouched; no v7 client or dedicated
multiplayer acceptance has been performed. The tutorial GIF still shows the
previous v6 game capture and must be replaced after the next client QA run.

Superseded on reference-model feedback: 2026-10-16 `wings.6`: 375 tests passed, zero failures/errors. The original
concept silhouette is restored by an upright shoulder fan, downward bias on
the power stroke and primarily rearward wrist recovery. The hinges return
to shoulder-blade height; the central saddle is removed. Paired branching
contact tissue is weighted between chest and moving wing roots. Deployment
starts independently at each embedded skin contact, with tissue spreading
before the spar extends. See `art/sovereign-wings/ORGANIC_ROOT.md`.
The isolated client run includes visible deployment, takeoff, hover,
forward/boost, turning, reverse/lateral flight, braking, descent, landing and
retraction. The actual-game review is
`E:/Blacklight-Wings-20261016/qa/sovereign-organic-root-review.mp4`;
log: `E:/Blacklight-Wings-20261016/qa-wings6.log`.
The reference remains the visual target; passing tests does not establish
perfect visual reproduction. Dedicated multiplayer acceptance is pending.


Superseded on user visual feedback (low attachment and accessory-like saddle): 2026-10-16 `wings.5`: 374 tests passed with zero failures/errors. The new
regression samples six actual flight clips against independent wing cycles
and both steering extremes, including the weighted membrane vertices, plus
standing folded-wing head/ground clearance. The final isolated client run
completed takeoff/ascent, hover, forward/boost, turning, reverse/lateral flight,
braking, descent, landing and retraction, while varying camera pitch. It
captured 610 review frames with zero writer-queue drops and logged no runtime
errors or movement-validation kicks. Front/back review confirms the lowered,
widened back attachment and raised grounded tips in the tested poses. This
is sampled/visual verification, not a guarantee for every possible pose/mod.
Review: `E:/Blacklight-Wings-20261016/qa/sovereign-head-clearance-final-review.mp4`.
Log: `E:/Blacklight-Wings-20261016/qa-wings5-final.log`. Tutorial updated to
this capture. Multiplayer acceptance remains outstanding.


Superseded after user reported flight/head intersection: 2026-10-16 `wings.4`: 373 tests passed, zero failures/errors/skips. The final
isolated integrated-client run completed takeoff/ascent, hover, forward/boost,
turning, backward/lateral travel, braking, descent, landing and retraction.
No runtime errors or movement-validation kicks were logged. The run captured
604 frames; 25 screenshot requests were dropped by the bounded writer queue.
These counts do not measure game frame rate. The 28.5-second review is
`E:/Blacklight-Wings-20261016/qa/sovereign-bat-rig-final-review.mp4`.
Front/back contact-sheet inspection checked the corrected rearward wrist fold;
the earlier forward-folding candidate was rejected. Dense recovery-phase FK
tests check that hovering fingertips remain behind the actor and on their own
side of its centre. Rig tests also check hierarchy, common wrist attachment,
membrane weights and geometry budget. The in-game tutorial now uses this run.
The final client log is `E:/Blacklight-Wings-20261016/qa-wings4-final.log`.


Superseded on user visual feedback: 2026-10-16 `wings.3` had 371 tests passed,
zero failures. Two isolated integrated-
client runs checked the directional beat; the second includes the lowered
forward-flight stroke plane. It exercised takeoff/ascent, hover, forward,
boost, turning, backward, both lateral directions, braking, descent, landing
and retraction. No runtime errors or movement-validation kicks were logged.
The final run captured 639 frames with zero writer-queue drops. The 28.5-second
review is `qa/sovereign-directional-wingbeats.mp4` under the workspace output
root. Its 30 fps encoding does not imply native 30/60 fps acquisition.
Additional pure tests check downward displacement on both wings with upright,
45-degree and horizontal body poses, mirrored steering, recovery folding,
loop seams, direction changes, duplicate render calls and grounded folding.
The tutorial capture now shows the directional wingbeats.

2026-10-16 `wings.2`: 365 tests passed, zero failures. Two disposable-world
integrated-client runs completed. The final run exercised takeoff, hover,
cruise, boost, turning, braking, descent, landing and retraction, with no
runtime errors or movement-validation kicks in the client/server log.
471 review frames were captured with zero writer-queue drops; this is not
a claim of 60 fps capture or a multiplayer performance benchmark. The JAR
was checked against the new motion/tutorial resources and excludes the QA
fixture. Full recording and validation metadata are under
`E:/Blacklight-Wings-20261016/qa/`.

- Pure tests cover acceleration, braking, diagonal speed, invalid inputs,
  ascent/descent, body inclination and hover loop continuity.
- Opt-in `-PwingTest` includes a disposable-world client fixture. It sends
  movement payloads through the integrated server, captures hover / cruise /
  boost / descent / landing / retraction, and exits the test client.
- The fixture requires both its system property and the exact isolated QA
  directory. Its classes are excluded from installable JARs.
- The tutorial GIF is actual game capture, not a conceptual animation.

Integrated-client testing does not constitute dedicated-server multiplayer
acceptance. This remains a development build pending visual feedback and
multiplayer validation; no release tag is created by this change.


## Independent attachment and live body response (wings.11 candidate)

User feedback on wings.10 identified rigid standing posture in flight and a lifted head/neck in ordinary skin. The wing body-surface replacement has been removed: player shoulders, sleeves and torso are rendered normally. Model-v12 removes the six shoulder caps and uses fixed back tissue, with independent outer wing joints. Fixed tissue follows the same chest matrix as the body, without wingbeat skin weights. The inner back tissue was lowered and routed outboard to clear the actual neck pivot, instead of raising the head to avoid intersections.

Looping captures retain torso/chest movement and 35% of captured root variation around the navigation posture. A tick-owned wingbeat snapshot is shared by the wing renderer and small body/arm/leg responses; repeated render passes cannot advance a separate flap clock. Takeoff and landing keep their captured gestures and blend this response in/out. The head pivot offset is limited to the real 1.5-pixel rear neck edge.

The initial 2,466-cuboid candidate was superseded by model-v15 after the user rejected its fragmented silhouette. The active model traces the supplied front-view bone paths, blade endpoints, membrane edges and openings. It uses continuous faceted surfaces, overlapping plates and braided roots: 669 meshes, 22,979 triangles and 35 bones. Depth and hidden surfaces are reconstruction assumptions. It is not claimed to be an exact reconstruction of an unavailable source mesh.

Both resting fold variants curl the two longer middle fingers back by 32 degrees to keep the retraced tips above the ground. Attachment tissue stays chest-bound; its vertices do not inherit the wingbeat. Microscopic faces from membrane aperture clipping are removed during authoring, while runtime geometry validation remains strict.

Editable sources are in `art/sovereign-wings/model-v15/`: weighted Blender/GLB, a free-mesh Blockbench file, native weighted JSON and the pixel atlases. The Blockbench file retains geometry and hierarchy; weighted deformation and fold actions are authoritative in Blender/GLB and the native JSON. The GLB round-trip checks both folds at open, midpoint and closed poses against the native geometry. GitHub research and licensing decisions remain in `art/sovereign-wings/GITHUB_ANIMATION_RESEARCH.md`; no newly researched third-party code or assets were copied.

Final integrated-client checks exercised all six synchronized flight phases in
both plain and verified armor forms, including a real contact landing onto the
acceptance platform. Both ended grounded, then retracted the wings. Camera-front
boost inspection found the old-coordinate decorative hooks; these were anchored
to the retraced bone and checked again. High-speed trails were shortened after
the first visual pass. The final frame inventories are isolated by file times
and match each run's recorded capture count; future runs use unique folder IDs.

Evidence under `E:/Blacklight-Wings-20261016/qa/`: `wings11-client-acceptance.json`,
`wings11-final-contact.jpg`, `client-wings11-final-plain.log` and
`client-wings11-final-armor.log`. The two runs captured 447 / 434 frames, with
31 / 49 screenshot queue drops. This is sampled visual QA, not a claim of
constant game FPS. No movement-validation kick or mod rendering failure was
logged; the plain client did log an unrelated Mojang public-key network timeout.
Dedicated multiplayer acceptance has not been performed in this revision.
