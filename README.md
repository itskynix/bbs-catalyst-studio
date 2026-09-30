# BBS CS (Catalyst Studio)

**BBS CS (Catalyst Studio)** is an advanced Minecraft machinima, animation, and video production studio mod for Fabric 1.20.4, built as an upgraded fork of BBS FS.

It brings a dedicated non-linear editing (NLE) timeline directly into Minecraft, featuring a studio-grade lossless audio pipeline, intelligent camera-frustum Replay LOD optimizations, and critical rendering fixes.

---

### Key Highlights

* **Catalyst NLE Timeline:** Native composition timeline supporting Video, Audio, Image, Text, and Scene/Film layers with keyframing, trimming, and multi-track editing.
* **Lossless Audio Pipeline:** 28-bit Soxr sinc resampling, transparent 2D stereo pass-through, and zero pitch-drift timeline scrubbing.
* **Replay LOD Engine:** Distance and frustum-based actor culling to preserve performance in massive scenes (automatically disabled during offline video exports).
* **Batch Replay Tools:** Instant replay reset without reloading dashboards, along with batch selection tools (Select All, Select Same Model).
* **Render & Asset Fixes:** Full OpenGL state isolation preventing missing texture glitches with Iris/Sodium, and a comprehensive fix for armor right-side limb visibility.
* **BBS Hub:** Integrated in-game community asset browser foundation for models, rigs, and presets.

---

### Building from Source

Ensure you have Java 17+ installed, then build with Gradle:

```bash
./gradlew build
