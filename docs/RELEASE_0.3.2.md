# 0.3.2 · NeoForge 1.21.1

- Entering Blacklight mode selects rear third person once. Leaving restores the previous perspective unless the player manually changed it. Uses the mode state, so remapped mode keys work. First-person weapon work is deferred; this release does not claim to fix first-person claws or whipfist.
- Thermal/infected silhouettes restore the camera and projection matrices for the AFTER_LEVEL pass. Sensory redraws no longer replace recorded hand anchors.
- Whip casts use the skeletal animation clock, including held attack poses. Their attachment blends over physical distance from the rendered hand instead of an arbitrary four source nodes; the distant authoritative contact path remains unchanged.
- Hammer ram recovery adapts the spine and shoulder rotations to keep the enlarged hammer mesh above the planted floor. Root and leg transforms are preserved; airborne poses are unchanged.
- Prototype mode permits normal item and experience pickup. Normal mining, placement and item-use restrictions remain.
- Added **Shield Borer / 盾构机**, 350 EP after unlocking Shield, in the utility skill category. Deploy a working shield and sprint forward on the ground. Releasing sprint, retracting/breaking the shield or leaving the mode stops excavation.
- Shield excavation uses server-selected nearby cells, normal loot and protection events. It excludes support surfaces, liquids and unbreakable blocks, never loads remote chunks, and is limited to six successful breaks per player / 24 per server tick within the remaining tick-time and loose-entity budgets. The skill tutorial uses live remapped bindings and the existing shield-running pose demonstration.
- Includes the 0.3.1 bulletdive loose-entity backpressure and nearby loot merging fixes.

## Validation

- Unit regressions cover perspective ownership, shield prerequisites, excavation corridor, whip attachment, and hammer recovery clearance without moving the root or legs.
- Dedicated-server GameTests exercise real loot, item/XP pickup, protection cancellation, sprint release, shield loss/breakage, bedrock, and the retained extreme-height bulletdive load controls.
- Disposable-world framebuffer captures check sensory alignment, whip attachment and hammer landing/recovery. Test fixtures are excluded from release jars.
