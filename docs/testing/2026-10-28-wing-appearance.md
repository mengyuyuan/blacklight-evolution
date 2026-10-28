# Wing appearance integration acceptance, 2026-10-28

Build: NeoForge 1.21.1, `0.3.5-preview.20261028-wing-switch.1`.
Installable bundled JAR SHA-256:
`6198a368310d3da5c0a2c8779e24b50260a4b7ad0ae3f1401f3de9579466aef5`.

## Automated and artifact checks

- All 795 Java unit tests passed, with no failures, errors or skips.
- Tests exercise actual packed wing geometry through deployment and folding,
  checking uniform shape preservation, a fixed mount, clearance, overlap reversal
  and effect anchors. The 157,041 accepted color triangles remain present.
- Settings save/network round trips pass for both appearances, with the default
  and existing-setting setters retaining the expected choice.
- Bundled motion verification: 256 originals matched exactly.
- Compact map verification: 17,931 maps, 7,890 XZ files matched, 598,341,209 decoded
  bytes. Package budget and standalone boundary checks passed.
- All 26 pre-existing resources with wing-related paths are byte-identical to
  HEAD and the packaged copies. New mesh/textures reproduce byte-identically with
  the portable packer and match their packaged copies.
- No private client QA classes, GameTest classes or withdrawn experimental wing
  data are included in the installable JAR.

## Real client checks

A hidden, separate Minecraft 1.21.1 / NeoForge 21.1.244 client used a copied QA
world and a private temporary test mod. It did not control desktop input or use
the player's live world. The test exercised production systems and C2S/S2C
settings updates, not a browser substitute:

1. Classic remains the default and production wing equip works.
2. Precision choice reaches the client from the integrated server, retaining the
   player's speed setting without granting unrelated unlocks.
3. Precision folding renders in both overlap orders and connects to actual wing
   flight state; switching back to classic also works while airborne.
4. A complete save/exit/relaunch reloads the precision choice on client and server.
5. The actual skill settings button switches both ways in the final JAR. The UI
   shows named appearances rather than presenting classic as a disabled ability.

Short local render-event samples, with one player and a 30 FPS frame cap, measured
precision median/P95 render-event duration at 5.75/10.88 ms and classic at
4.93/17.49 ms. These exclude frame-cap sleep and are not end-to-end FPS, GPU timing
or a multiplayer performance guarantee.

## Scope and limits

The optional binding moves two whole wings around fixed roots, with uniform
ground retraction. It avoids the rejected local stretching; a complete
multi-joint retarget of the Prototype 2 flyer is not claimed. Classic animation
and abilities remain available unchanged.

The checks cover this integration, an integrated server and one local client.
They do not establish a two-client dedicated-server soak pass or completion of
the separate broad code audit. An unresolved high-altitude dive assertion in
a separate broad server run remains outside this wing acceptance; this document
does not establish which change or test environment caused that failure.

Rebuild/provenance: `art/sovereign-wings/PRECISION_APPEARANCE.md`.

## Review evidence

- [Recorded checks and hashes](../verification/wing-appearance-20261028/acceptance.json)
- [Actual appearance settings](../verification/wing-appearance-20261028/settings.png)
- [Precision wings in flight](../verification/wing-appearance-20261028/precision-airborne.png)
- [Return to classic wings in flight](../verification/wing-appearance-20261028/classic-airborne.png)

The recorded playable build includes a separate uncommitted landing-debris fix.
This PR excludes that fix and its GameTest; the screenshots above cover unchanged
wing code and the settings UI. The broad audit ledger is also excluded. The
repository's PR build and server checks independently validate the submitted tree;
the earlier playable build hash must not be presented as this PR's release hash.
