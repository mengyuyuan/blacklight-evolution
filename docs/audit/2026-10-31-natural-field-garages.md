# Natural field garage closeout

Actual normal-terrain client acceptance found a natural military base but no garage. CityMilitaryStructure passed `city` as Planner.newStart, so every ordinary-world start was rejected. The planner also required existing city roads while the ordinary-world survey always returns road=false.

New structure callbacks now explicitly mark the start as new, and distinguish urban/field planning. Urban starts retain the exact existing-road survey. Suitable new field starts can include their own bounded motor-pool apron instead. All rotated exit columns are surveyed against water, occupied plots and shallow height limits, then clipped and persisted as part of the new garage piece. Old saved piece lists are not revisited; missing FieldApron defaults false. No chassis or crew are created by this change; real natural discovery, garrison recruitment, boarding and fleet dispatch still perform issuance.

Regressions cover new field selection without city roads, urban behavior, old-start rejection, all rotations, water and steep terrain. Actual client natural-base/garage/boarding/patrol movement remains an explicit acceptance gate.
