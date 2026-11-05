# Credits

BBS CS (Catalyst Studio) stands on the work of the people below.

| Who | What they made | Licence |
| --- | --- | --- |
| **McHorse** | **BBS (Blockbuster Studio)**, the original creator. Everything this studio is built on. | MIT |
| **Wemppy** | **BBS FS (Fabric Studio)**, the port and the foundation of the Fabric version this project continues from. | See `LICENSE.md` |
| **qualett** | **IRLight / irl-core**, the dynamic shader lighting architecture, shadow engine, and GPU cluster buffer pipeline. | MIT |
| **Kynix** | **BBS CS (Catalyst Studio)**, the fork and its developer. | MIT (see `LICENSE.md`) |

## Translators and contributors

They are listed in `fabric.mod.json` (the `contributors` entry), which is what the mod loader shows
in its mod list. The same names and roles belong here when that list changes.

## Where the licence texts are

`LICENSE.md` holds the licence of this project and the copyright lines of McHorse, Wemppy and Kynix.
`THIRD_PARTY_LICENSES.md` holds the notices of everything taken from other projects.

### Special Thanks & Credits
- **Glaxium:** For the original BBS POV add-on concept, inspiration, and permission regarding the POV system.

## IRLight & irl-core Monolithic Integration

The IRLights dynamic lighting engine and `irl-core` math/shader pipeline (`org.qualet.irl.*` and `qualet.irlite.*`) are integrated directly as Java sources into BBS CS.
All ported files and integrated systems are:

```
Copyright (c) 2026 qualet
Licensed under the MIT License.
```
