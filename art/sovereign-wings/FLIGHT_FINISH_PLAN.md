# Sovereign flight finish — 2026-10-17

## User target

Finish the reference-based model and the complete flight presentation today:
beautiful coherent surfaces, forceful acceleration/dashes, continuous plausible
motion and distinct initial-speed / acceleration / sustained-speed effects.

## Choreography

| Phase | Body and wings | Effects |
| --- | --- | --- |
| Equip | Back tissue grows first; bones unfurl from embedded roots | Existing biomass transformation |
| Ground | Normal gait, no flight override; one of two randomized folds | No flying wake |
| Takeoff | 8-tick planted coil, release, stronger downstroke; blend to airborne | Brief ground air pulse and eight dust puffs only near solid ground |
| Hover | Bent knees, capture-backed torso/limb movement, shared wingbeat response | Quiet, no speed streaks |
| Cruise | Gradual forward inclination; normal wing cycle | Fine pale/red world-space tip trails |
| Acceleration | Velocity-derived thrust increases downstroke amplitude and frequency; aft sweep, progressive tip delay; body/legs follow through | One short launch wake on hard acceleration; progressive lens and speed-line response |
| Fast sustained flight | Thrust settles, body stays streamlined; smooth turning/banking | Longer/brighter red-core tip trails, one threshold wake; bounded +5-degree lens response |
| Brake | Deceleration spreads wings, lifts body and bends legs; settles to hover | Wake fades with actual speed rather than disappearing on key release |
| Descend / land | Existing descent control and landing capture; transition to gait | Small landing air/dust pulse, wake expires |
| Retract | Fixed roots remain body-bound until wing retraction completes | No new airborne effects |

The original bat joint curves and body captures remain the motion source.
Movement uses the existing server/prediction controller with bounded acceleration,
braking and redirection. Visual wingbeats do not change collision, camera position,
entity velocity or the body's shoulder attachment. This is physically motivated
animation, not a fluid-dynamics simulation.

## Bounds and interoperability

- World-space trails sample the actual posed mesh endpoints, including body bank.
- At most 32 nearby tracked actors, 24 samples per actor and 48 transient pulses.
- Tick-owned presentation; repeated render passes cannot advance clocks.
- No effect entities, terrain modifications or additional per-frame network packets.
- Observer effects derive from synchronized flight phase and observed movement.
- FOV follows Minecraft's FOV Effects setting and avoids stacking ground-sprint FOV.
- Pauses/reconnects/teleports do not count as physical acceleration.

## Acceptance

1. Reference geometry, two fold variants, attachment and head/ground checks.
2. Velocity-ramp, braking, loop/transition and duplicate-frame regression tests.
3. Plain and armored isolated clients: equip, walk/run, takeoff, hover, cruise,
   boost with turning, reverse/side movement, descend, land, retract and wheel.
4. Inspect the captured frames and recorded body/flow values; build and inspect
   the installable JAR separately to exclude the opt-in test fixture.
5. Deliver current in-game and orthographic previews, editable sources and JAR.
