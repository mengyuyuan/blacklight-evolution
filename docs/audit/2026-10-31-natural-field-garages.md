# Natural field garage closeout

Actual normal-terrain client acceptance found a natural military base but no garage. CityMilitaryStructure passed `city` as Planner.newStart, so every ordinary-world start was rejected. The planner also required existing city roads while the ordinary-world survey always returns road=false.

New structure callbacks now explicitly mark the start as new, and distinguish urban/field planning. Urban starts retain the exact existing-road survey. Suitable new field starts can include their own bounded motor-pool apron instead. All rotated exit columns are surveyed against water, occupied plots and shallow height limits, then clipped and persisted as part of the new garage piece. Old saved piece lists are not revisited; missing FieldApron defaults false. No chassis or crew are created by this change; real natural discovery, garrison recruitment, boarding and fleet dispatch still perform issuance.

Regressions cover new field selection without city roads, urban behavior, old-start rejection, all rotations, water and steep terrain. Actual client natural-base/garage/boarding/patrol movement remains an explicit acceptance gate.

The bounded ordinary-world terrain cache is now independent for base/checkpoint selection and garage selection. Earlier rejected candidates can exhaust the 256-column field survey before the side plot is evaluated, silently rejecting every new column. Both caches remain bounded and no chunk requests were added.

## Ordinary-terrain follow-up after native .7 failure
A bounded native search across 49 placement regions found only one field base, with no suitable side garage. New field bases now permit a six-block foundation relief (city planning still uses three), with actual six-block clipped support and a persisted optional FoundationDepth. Missing depth on older pieces remains three. A field garage selects its own supported nearby datum within six blocks of the base; steep/water/occupied plots remain rejected. The field survey cache now covers the entire 208-block reference window (at most 1024 coarse columns), instead of exhausting 256 early during rejected candidate scans. No entity spawning was moved into worldgen; real garage personnel and vehicle dispatch still own issuance. Further native natural-patrol acceptance remains required before release.
