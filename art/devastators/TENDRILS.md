# Authored Devastator tissue models

These meshes were modeled from new cross-sections and topology. Original game screenshots informed the tissue structure and proportions. The main model is a near-round lobed stalk with three raised red cords, a raised muscle cord, closed transverse ribs and short discrete side thorns. No original game vertices, UVs, meshes or textures are read or included. Veins and ribs have volume; colors are vertex RGB values.

The editable source is `tools/author_devastator_tendrils.py`. The OBJ/MTL files and `tendrils-authored.blend` can also be opened and edited in a 3D editor. `tendrils-editable.json` is the same explicit geometry schema consumed by the runtime resource. Editing the Blender file does not silently change the runtime JSON: export updated coordinates into that schema deliberately, or update the authoring sections and regenerate.

Run `python tools/author_devastator_tendrils.py` to regenerate geometry and validate the actual exported coordinates, normals, winding, array sizes and bounds. Add `--preview` to render the models in the locally installed Blender. The Python script and the runtime JSON are the build inputs; Blender is only required for the editable scene and previews.

| Mesh | Y extent in Minecraft model units | Triangles | Runtime quad vertices |
| --- | --- | --- | --- |
| `tendril_segment` | 0–1 | 1080 | 4320 |
| `tendril_spike` | 0–0.3 | 328 | 1312 |
| `whipfist_segment` | 0–1 | 848 | 3392 |

All meshes use `sourceScale=1` and `longitudinalAxis=1`. Their coordinates retain slight asymmetric lobes and a sculpted bend. The runtime deforms the authored vertices along its existing motion path. Sixty main segments and sixty tips emit 337,920 vertices through the fallback triangle-to-quad path, or 253,440 indexed triangle vertices through the current GPU path, before other effects.

The previous side blades occupied up to 0.126 of the entire authored axis. Whole-path deformation stretched those plates into long planks. The old model also multiplied its own strong taper and flattened cross-section by the runtime taper. The corrected stalk has 12 cross-section samples and 25 axial rings, rounded volume normals, a 0.97 Z/X aspect factor and a weak model taper; the runtime's wave, width and taper remain unchanged. Only the separate 0.3-unit tip has a strong point taper.

Side thorns now have at most 0.0024 normalized axial span. Their straight-axis extent is about 0.394 blocks at 164-block stretch, with radial projection forming the thorn. They are closed transverse solids, not axial fins. The model audit checks rounded coordinates, nondegenerate faces, normalized outward volume normals, bounds, the 1100-triangle budget and this stretch-sensitive axial span.

`tendrils-three-quarter.png`, `tendrils-side.png` and `tendril-close-up.png` show the modeled silhouette and physical raised veins. `tendril-stretched-close-up.png` applies the existing runtime curve/taper/normal mapping to a 24-block strand with a small control offset, so the long-distance silhouette is inspectable before game integration. Run `--stretched-preview` to rebuild only that modeling preview. The saved Blender scene includes the editable stretch-QA object with rendering disabled by default. These are local modeling previews, not final in-game timing, material or attachment acceptance; those need integrated game QA.
