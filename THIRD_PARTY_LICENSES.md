# Third-Party Licenses

BBS CS (Catalyst Studio) contains, is based on, or is built to work with the projects below.

---

## BBS (Blockbuster Studio) — McHorse

The original mod this project is a fork of. Licence: **MIT**.

```
MIT License

Copyright (c) 2025 McHorse

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

## BBS FS (Fabric Studio) — Wemppy

The Fabric port and foundation of BBS this project continues from. Its copyright line is kept in
`LICENSE.md` (Copyright (c) 2026 Wemppy (BBS FS)), under the same MIT terms as that file.

## IRLights & irl-core — qualett

Dynamic shader lighting architecture, GPU cluster buffer pipeline, shadow engine, and BBS add-on bridge (`org.qualet.irl.*` and `qualet.irlite.*`). Licence: **MIT**.

```
MIT License

Copyright (c) 2026 qualet

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

The sources of `irl-core` and `bbs-irlights-addon` are integrated directly into BBS CS under the MIT License above.

## BBS Refreshed Addon — quaIett

The interface blur (`InterfaceBlur`) is adapted from it; its MIT notice is in
`src/main/resources/assets/bbs/licenses/bbs-refreshed-addon.txt`.

## Jolt Physics and jolt-jni

The physics studio runs on Jolt Physics (Jorrit Rouwe, MIT License) through the jolt-jni bindings (Stephen Gold, MIT License), shipped as jar-in-jar libraries with their native builds.

## BBS Physics Engine (BBS FS Port & BBS Physics Addon) — Wemppy

The physics engine of BBS CS is the BBS Physics Engine add-on by Wemppy, integrated whole into
BBS CS under `mchorse.bbs_mod.physics` (common half in `src/main`, client half in `src/client`) and
adapted to BBS CS's own API: the Jolt world and its native library loading, the recorded and cached
film scene, rigid bodies, ragdolls with their joints and muscles, bone splitting and tearing, death
impacts, chains, cloth, balloons, structure destruction, the Collision and Physics tabs of the form
editor, the impulse, tear and death clips, the physics tracks of the dope sheet, baking to keyframes
and the film physics settings. Its textures are kept under `assets/bbs_physics`. BBS CS adds on top
of it: the physics and gravity gun tools and items, the crosshair menus of the clips' fields, and
the fallback that gives an unmarked rigid body its own size (a block its own 1×1×1 outline).
Licence: **MIT**.

```
MIT License

Copyright (c) 2026 Wemppy

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
