# Attack handoff source review (2026-10-14)

Inspected local decoded `fig-r22/prototype_claws-owners.json` and the historical
`prototype-retarget-r25/claw/build_claws.py`. No raw source tracks are copied here.

The old exporter retains windup/release/recovery frames but omits the overlapping
`alex_claw_amb_fight` animation in `transition_to_idle`:

| Move | Owner | Recovery starts (exported seconds) | Idle begins within recovery | Idle blend-in |
| --- | --- | --- | --- | --- |
| claw_auto1 | 19820 | 1/30 + 0.6 | 0.2 | 0.1 |
| claw_auto2 | 24016 | 1/30 + 0.5 | 8/30 | 7/30 |
| claw_auto3 | 28472 | 0.05 + 0.6 | 8/30 | 7/30 |

Restore this overlap using the existing retargeted claw idle pose. This is a
reviewed local adaptation, not a claim that the complete original animation
partition evaluator has been reproduced. Other weapons retain existing timing.
No hit windows, combo input windows, charge thresholds or server damage change.

The earlier handoff cached its previous endpoint but still sampled live state on
every render. A network change could thus show the next pose before its handoff
was committed, then return to the old endpoint at the next tick. Capture a single
pose interval at tick end (including utility layers), keeping 60 Hz source
subsamples. Renderers only sample that interval. The body facing and attached
layers now share a tick-owned shortest-angle transition instead of switching
instantly between weapon yaw and vanilla body yaw.

Regression checks cover a network mutation between render passes, immutable
source subframes, decoded recovery timing using actual bundled clips, and
attack-to-idle facing across the +/-180 degree boundary. In-game confirmation
is still required; passing these checks alone does not prove all reported
stuttering is eliminated.
