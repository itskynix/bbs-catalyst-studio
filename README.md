### Key Highlights

#### 🎬 Catalyst NLE Timeline & Composition Studio *(Experimental)*
* **Multi-Track Timeline:** Dedicated non-linear timeline supporting Video, Audio, Image, Text, Scene/Film, Solid, Null, and Adjustment tracks. *(Experimental)*
* **Modern Editing Workflow:** Magnetic snapping, multi-track razor cutting, ripple gap deletion, and box/marquee multi-selection. *(Experimental)*
* **Frame-Accurate Trimming:** Bi-directional trimming with dynamic offset synchronization to prevent unwanted frame resets. *(Experimental)*
* **Virtual Canvas & Bounding Box:** Resolution-independent 1080p preview canvas with freeform scaling, anchor-point pivot rotation, and fullscreen monitor mode. *(Experimental)*
* **Integrated Media Pool:** Drag-and-drop asset management straight from your desktop with automatic asynchronous waveform pre-caching. *(Experimental)*

#### 🎧 Studio-Grade Audio Engine
* **Lossless Resampling:** 28-bit Soxr sinc filtering ensuring transparent 44.1 kHz / 48 kHz direct audio playback without high-frequency loss.
* **Pristine 2D Stereo:** Bypassed OpenAL 3D spatialization filters on stereo sources with built-in analog soft-clipping protection.
* **Smooth Scrubbing:** Native sample-rate tracking eliminating pitch and playback speed drift during timeline scrubbing.
* **Extended Format Support:** Native decoding for 24-bit/32-bit float WAV and direct MP3 stream playback.

#### ⚡ Replay Performance & Scene Management (bbs-lezy Integration)
* **Live Multi-Track Replay Recording:** Real-time multi-actor recording session management with seamless arrow-key navigation between replays while actively recording.
* **Replay LOD Engine:** Dynamic actor culling based on camera distance and view-frustum angle to maintain frame rates in heavy scenes.
* **Export Quality Safeguard:** Dynamic culling automatically disengages during video exports to guarantee full rendering quality.
* **Batch Replay Management:** Instant single-click replay desync reset, plus recursive batch selection and crowd multiplication tools.

#### 🛡️ Critical Rendering & Glitch Fixes
* **3D Model Texture Protection:** Complete OpenGL state isolation resolving missing/glitched textures when using Sodium, Iris, ETF, and ETM.
* **Armor Right-Side Limb Glitch:** Fixed missing armor rendering on the right arm, right leg, and right boot for player and actor models.
* **Video Export Fixes:** Eliminated black letterboxing and windowed canvas clipping artifacts during video renders.

#### 🎥 Cinematic Camera & Visual Tools
* **Pro Camera:** 35mm lens simulation, focal length controls, independent camera roll (Dutch angle), and dynamic Dolly Zoom (Vertigo) mode.
* **Enhanced Cape Physics:** Fluid spring-damped cloth motion, first-person hand isolation, and Wavey Capes multi-point simulation bridge.

#### 🔒 System & Security
* **Locale Invariance:** Complete immunity to MoLang and file extension parsing crashes on Turkish and non-English Windows locales.
* **Network & Stability:** Large-packet memory threshold protections against DoS attacks, along with automatic corrupt project backups.
* **BBS Hub Foundation:** In-game marketplace framework for browsing and hot-reloading community models, presets, and rigs. *(Experimental)*
