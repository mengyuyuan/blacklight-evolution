# 贴图生成记录

方式：内置 image_gen，均为已有色图的精细化编辑，非 CLI/API fallback。

## Front edit

Target: `precision_front.png`. Use case: precise-object-edit. Asset: a production 2D UV projection texture for an existing wing model. Preserve original aspect ratio, proportional silhouette, holes, blade tips, folds, plate boundaries, vein locations, roots, spacing and neutral grey background. Enhance charcoal-black faceted chitin plate edges, restrained cool grey edge wear and fine scratches, deep crimson membrane fibers and thickness around existing tears, and thin vivid crimson cores along existing veins. Distinguish matte armor from slightly wet tissue. Keep Minecraft-style broad faceted shapes readable. No feathers, rivets, ornate patterns, added holes/veins, noisy crust, labels or fuzzy bloom.

## Back edit

Target: `precision_back.png`; refined front image used only as material style reference. Preserve back target shape/registration, existing root separation, original 838:711 aspect, margins and neutral grey backdrop. Refine charcoal chitin broad planar facets, controlled cool-grey edge details, fine scratches, sinewy crimson membrane and existing thin vivid red fissures. No new vein routes, holes, bridge between roots, new central part, feathers, camera changes, objects or text. Geometry of the style reference must not replace target geometry.

## Verification and post-generation integration

Generated images are 1364×1153 and 1362×1155; the request to aim at higher resolution did not produce 2K assets, so these are not labelled 2K/4K. Image pixels were kept intact. Dense image registration was used to derive per-vertex material UVs, followed by actual front/back/oblique renders. Original UV coordinates remain available for original emission. V2 additionally adjusts model depth by local armor/membrane/edge region; no X/Y silhouette edits.

## Side-wall atlas (V3)

Input refined front image used as material style reference only. Generate a new flat square atlas with two equal horizontal material swatches and no border. Upper half: charcoal-black chitin/bone plate cross-section, broad dark-grey blocky strata, restrained chipped edges, fine deep-red fissures. Lower half: dark crimson biological membrane cross-section, tightly packed red-black sinewy fibers and blocky tissue striations. Seamless horizontally within each half, neutral flat illumination, no wing/object silhouettes, feathers, text, labels, grey background or orange lava. Output: 1254×1254. A separate side-wall UV layer region assigns the corresponding half to armor or membrane, leaving the original front/back UV artwork intact.
