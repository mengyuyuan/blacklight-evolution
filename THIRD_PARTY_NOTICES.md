# Third-party notices

The Prototype velocity-corrected radial speed effect is inspired by and adapted
from the design of **AnimeSpeedLines**, Copyright (c) 2024 2Retr0, distributed
under the MIT License: https://github.com/2Retr0/AnimeSpeedLines

This fork ports Blacklight's native `PostChain` implementation to Minecraft
1.20.1 Forge, with a distinct shader, radial blur, chromatic separation,
Blacklight tinting, and project-specific activation/synchronization logic.

The complete upstream license below was retrieved from the project's official
GitHub repository on 2026-10-01. Source:
https://github.com/2Retr0/AnimeSpeedLines/blob/89b86ddbe4bc69db8954d39083c9059e9bbbb90c/LICENSE
(license blob SHA-1: `1511dceb22980b24c7b6f2840b7ffc931c0b2bea`).

```text
MIT License

Copyright (c) 2024 2Retr0

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Required runtime dependencies (installed separately)

GeckoLib 4.8.4 for Minecraft 1.20.1 Forge is the only required additional
mod. Its installed JAR declares MIT and includes `GECKOLIB_LICENSE`.
Minecraft and Forge remain required platform components. GeckoLib is not
shaded into Blacklight's JAR. Local convenience packages may include its
unchanged separate JAR and complete license.

## Bundled MixinExtras runtime

The Forge port embeds the unchanged MixinExtras Forge 0.4.1 library through
Forge Jar-in-Jar. It supplies the custom Mixin injectors that NeoForge included
in the original platform. MixinExtras is distributed under the MIT License;
its upstream project and license are at https://github.com/LlamaLad7/MixinExtras.
The embedded library retains its own license files.

## Independent animation and player geometry

The independent runtime does not load Epic Fight, Epic ParCool, or Weapons of
Miracles code, skeletons, meshes, or animations. The player bind hierarchy at
`assets/blacklight/animation/player_bind.json` is generated from mathematical
block proportions by `tools/author_player_bind.py`; the generator does not
read third-party skeleton files. BoneTransform, BlacklightPose,
BlacklightSkeleton, MatrixMotionClip, the CPU skin renderer, and the action
clock are project-authored code covered by the project's license.

The included runtime profile inventory is recorded in `THIRD_PARTY_ASSETS.md`.
Historical RC59 packages and archived Epic Fight adapters remain historical
development inputs, not code dependencies of this runtime.

## Optional ParCool compatibility

ParCool 3.4.3.3 is an optional installed mod and compile-only compatibility API
(LGPL-3.0). Blacklight's native traversal does not require it. Its JAR and assets
are not embedded. The former Epic ParCool animation lookup has been removed.

## Shenzhen Futian map database

Map data © OpenStreetMap contributors (ODbL 1.0), generated with Arnis 3.3.0
(Apache-2.0). The derived database under `data/blacklight/city/futian/` remains
under ODbL 1.0. See `docs/FUTIAN_DATA_SOURCES.md` for attribution, source links,
generation parameters and accuracy limits. In-game attribution is available
through `/blacklightcity credits`.
