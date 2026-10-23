# Optional YSM appearance bridge (preview)

NeoForge 1.21.1 and Forge 1.20.1 support the dedicated `alex_mercer_blacklight_bridge` model while Blacklight supplies its combat pose. Install official YSM and YSM Mapping API separately. Other YSM model IDs retain their own rendering path.

Build the opt-in model from our existing Alex Mercer YSM source:

```sh
python tools/build_ysm_bridge_model.py --help
```

The converter preserves body geometry and texture, writes to a separate destination, and adds live pose and covered-skin channels. Select the resulting pack in YSM. Armor remains the original block armor. The bridge converts final articulated matrices to local Bedrock channels and matches YSM's reflected X basis for native attachments.

Forge locates the registered YSM capability through the type provided by Mapping API; NeoForge uses the published state accessor. No implementation names or third-party model assets are embedded in this integration.

With Hold My Items 2.1 and this dedicated model selected, ordinary first-person arms use the held-item arm frame instead of applying the independent YSM full-body camera offset. HMI continues to move the arm and item together; no user configuration is rewritten. This does not enable a new first-person combat mode.

See `verification/WEBSITE_FEEDBACK_20261023_ZH.md` for the current clean build, mathematical pose regression and real client checks. Cross-machine multiplayer, every HMI animation, all equipment combinations and arbitrary other YSM models have not been fully visually accepted. Private screenshot drivers remain outside the release sources.
