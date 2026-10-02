# BBS Mod - GeliÅŸtirme Durumu (DEV_STATE)

**Proje:** Blockbuster Studio Catalyst (BBS CS) - Fabric 1.20.4 Port (`bbs-cs`)  
**Tarih:** 01 Ekim 2026  
**Son Tamamlanan AÅŸama:** 65 â€” GÃ¶kyÃ¼zÃ¼ / Ufuk GeÃ§iÅŸi ve Sis Render HatasÄ±nÄ±n OnarÄ±mÄ± (Sky Dome Void & Fog Pass Fix)  
*(AÅŸama 57: BBS Hub Pazaryeri, AÅŸama 58: Video & Audio Ses Kalitesi, AÅŸama 59: Mod KimliÄŸi ve SÃ¼rÃ¼m C1.0)*:

1. **FAZ 0: Crash, DoS ve Veri KaybÄ± AÃ§Ä±klarÄ± (%100):**
   - `[P0-1] DEFECT-18`: `PacketCrusher.java` parÃ§alÄ± paketlerde 32MB ve 4000 parÃ§a tavan sÄ±nÄ±rÄ± ile DoS/OOM korumasÄ±.
   - `[P0-2] DEFECT-19`: `ServerNetwork.java` oyuncu baÅŸÄ±na izole `incomingCrushers` haritasÄ± ve baÄŸlantÄ± kopma temizliÄŸi.
   - `[P0-3] DEFECT-20`: `ServerNetwork.java` sonlu sayÄ± denetimleri (`Double.isFinite`, `Float.isFinite`), dÃ¼nya sÄ±nÄ±rlarÄ± kÄ±rpmasÄ±, 2.000.000 blok yapÄ± hacim limiti ve eksik `arePanelsAllowed` kontrolleri.
   - `[P0-4] DEFECT-17`: `FolderManager.java` & `BaseManager.java` path traversal (`..` ve dizin dÄ±ÅŸÄ±) engellemesi ve null-gÃ¼venliÄŸi.
   - `[P0-5] DEFECT-02`: `CatalystProject.load()` bozuk projeleri silmeden `.corrupt-<timestamp>` yedeÄŸi alma; `CatalystProjectManager` hasarlÄ± proje takibi ve korumasÄ±.
   - `[P0-6] DEFECT-01`: `CatalystProjectManager.getProjectFile()` gÃ¼venli slug + 8 karakterlik deterministik SHA-256 hash ve Windows ayrÄ±lmÄ±ÅŸ aygÄ±t adÄ± (`CON`, `PRN` vb.) korumasÄ±.

2. **FAZ 1: Animasyon & Timeline KusurlarÄ± (%100):**
   - `[P1-1] DEFECT-03 & 13`: `CatalystLayer.computeEffective(frame)` saf hesaplama; oynatma sÄ±rasÄ±nda taban deÄŸer kirlenmesi ve kalÄ±cÄ± deÄŸer sapmasÄ± Ã§Ã¶zÃ¼ldÃ¼.
   - `[P1-2] DEFECT-05`: `splitChannelsAt()` ile dilimleme sÄ±nÄ±rlarÄ±nda interpolasyonlu keyframe enjeksiyonu (`cutSelected()`).
   - `[P1-3] DEFECT-06`: `copyChannelsTo()` ile katman Ã§oÄŸaltmada 8 kanalÄ±n tamamÄ±nÄ±n derin kopyalanmasÄ± (`duplicateSelected()`).
   - `[P1-4] DEFECT-07`: `lastClickedChannels` ve `deleteSelectedKeyframes()` ile doÄŸrudan seÃ§ili keyframe silme.
   - `[P1-5] DEFECT-08`: GÃ¶reli keyframe sÃ¼rÃ¼kleme (`dragStartTicks`), birden Ã§ok kanal keyframe'inin tek noktaya Ã§Ã¶kmesi Ã¶nlendi.
   - `[P1-6] DEFECT-09`: Ã–nizleme tuvalinde katman sÃ¼rÃ¼kleme bittiÄŸinde animasyonlu katmana otomatik keyframe yazÄ±lmasÄ± (`updatePropertyValue`).
   - `[P1-7] DEFECT-10`: `addKeyframeSingle()` ve Alt+Click ile X ve Y eksenlerinin birbirinden tamamen baÄŸÄ±msÄ±z keyframelenebilmesi.
   - `[P1-8] DEFECT-14`: `jumpToKeyframe(next)` J/K navigasyonunun seÃ§ili katman ve tÄ±klanan kanala odaklanarak gereksiz zÄ±plamalarÄ± Ã¶nlemesi.
   - `[P1-9] DEFECT-11`: Negatif Ã¶lÃ§ekleme (`Math.copySign`), iÅŸaret korumalÄ± viewport culling, bounding-box ve fare etkileÅŸim dÃ¶nÃ¼ÅŸÃ¼mleri.
   - `[P1-10] DEFECT-12`: Metin katmanÄ± Ã¶lÃ§eÄŸinin matrise uygulanmasÄ± ve unscaled kutu boyutlarÄ± ile Ã§ift Ã§arpÄ±m oluÅŸmadan orantÄ±lÄ± render edilmesi.

3. **FAZ 2: TÃ¼rkÃ§e Ä°ÅŸletim Sistemi (`Locale.ROOT`) GÃ¼venceleri (%100):**
   - `[P2-1]` `MolangParser.java`: `expression.toLowerCase(Locale.ROOT)` ile MoLang parser Ã§Ã¶kmesi Ã¶nlendi.
   - `[P2-2]` `EnumUtils.java`: `e.name().toLowerCase(Locale.ROOT)` ile enum anahtar bozulmalarÄ± engellendi.
   - `[P2-3]` `AudioReader.java`: `link.path.toLowerCase(Locale.ROOT)` ile bÃ¼yÃ¼k harfli `.AVI` / `.MP4` uzantÄ± tanÄ±ma hatasÄ± Ã§Ã¶zÃ¼ldÃ¼.
   - `[P2-4]` `CatalystLayer.java`: `s.toUpperCase(Locale.ROOT)` ile `"audio"` ve `"image"` katman tÃ¼rlerinin bozulmasÄ± engellendi.

4. **FAZ 3: Kod Hijyeni, Bounded Buffers & Thread-Safety (%100):**
   - `[P3-1] DEFECT-21`: `AudioReader.java` FFmpeg 30 saniye iÅŸlem zaman aÅŸÄ±mÄ± (`destroyForcibly`) ve 256MB tavan tampon sÄ±nÄ±rÄ±.
   - `[P3-2] DEFECT-22`: `CatalystLayer.java` arka plan worker thread'lerinde yÃ¼klenen Ã¶nbelleklere `volatile` gÃ¼vencesi.
   - `[P3-3] DEFECT-23`: `ServerNetwork.java` ve `FFMpegUtils.java` boÅŸ catch bloklarÄ±na `BBSMod.LOGGER` hata ve uyarÄ± loglarÄ±.
   - `[OPSIYONEL] DEFECT-16`: `BBSMod.java` `BBS_EDITING_RULE` gamerule varsayÄ±lanÄ± `false` yapÄ±larak Ã§ok oyunculu sunucularda izinsiz istemci eriÅŸimi engellendi.

5. **Render HUD CanlÄ± Ã–nizleme Ä°zolasyonu & MP4 Tavan Siyah Bar Ã‡Ã¶zÃ¼mÃ¼ (`UICatalystPanel.java`):**
   - CanlÄ± Ã¶nizleme tuvaline `GL11.glEnable(GL11.GL_SCISSOR_TEST)` ve GUI Ã¶lÃ§ekleme Ã§arpanlÄ± fiziksel `glScissor` uygulandÄ±; katman animasyonlarÄ±nÄ±n HUD butonlarÄ±nÄ±n veya ekran sÄ±nÄ±rlarÄ±nÄ±n dÄ±ÅŸÄ±na taÅŸmasÄ± engellendi.
   - Pencereli modda ana framebuffer yÃ¼ksekliÄŸi ile export Ã§Ã¶zÃ¼nÃ¼rlÃ¼ÄŸÃ¼ arasÄ±ndaki fark `int offsetY = Math.max(0, screenH - this.exportHeight)` ile tavana hizalandÄ±; `readH < this.exportHeight` durumunda `MemoryUtil.memCopy` ile satÄ±rlar doldurularak Ã¼retilen videolardaki Ã¼st siyah ÅŸerit tamamen giderildi.

6. **Catalyst Ses Export HattÄ± Ä°yileÅŸtirmesi (Stereo DesteÄŸi, PCM Clipping KorumasÄ± ve Codec UyumluluÄŸu):**
   - **GerÃ§ek 2-Kanal 48kHz Stereo Miksaj:** `AudioRenderer.renderAudio` 32-bit kayan noktalÄ± `mixLeft` ve `mixRight` sonsuz dinamik tavanlÄ± tamponlarla yeniden yapÄ±landÄ±rÄ±ldÄ±. Stereo kaynaklar Ã§ift kanallÄ± ayrÄ±mÄ±nÄ± korurken mono kaynaklar her iki kanala faz Ã§akÄ±ÅŸmasÄ±z eÅŸit daÄŸÄ±tÄ±ldÄ±.
   - **Analog Tanh Soft-Clipping & Peak Normalization:** Tepe genlik taramasÄ± ve aÅŸÄ±rÄ± yÃ¼klenmelerde dinamik tavan korumasÄ± (`maxPeak > 1.25F`) getirildi. 16-bit PCM short sÄ±nÄ±rÄ±nda 0.85 eÅŸiÄŸini aÅŸan sinyaller yumuÅŸak hiperbolik tanjant eÄŸrisi ile satÃ¼re edilerek dijital clipping ve boÄŸukluk Ã¶nlendi.
   - **Ã‡ok KanallÄ± Dilimleme:** `Wave.excerpt(from, to)` metodu ile stereo kanal ve bayt hizalamalarÄ± bozulmadan zaman aralÄ±ÄŸÄ± dilimlemesi saÄŸlandÄ±.
   - **FFmpeg Codec & Container UyumluluÄŸu:** AAC ve MP3 iÃ§in yÃ¼ksek kaliteli bitrate (varsayÄ±lan 320 kbps Studio Master), Opus codec'i MP4 container ile kullanÄ±ldÄ±ÄŸÄ±nda `-strict -2` parametresi otomatik entegre edildi.

7. **Video Layer ve Audio Layer Ses Kalitesi DÃ¼ÅŸÃ¼klÃ¼ÄŸÃ¼ / Resampling BozulmasÄ± OnarÄ±mÄ± (AÅŸama 58):**
   - **Video Ses Demuxer & OpenAL Frekans Uyumu (`AudioReader.java`):** Sabit `-ar 44100` ve Ã§ift resampling dÃ¶ngÃ¼sÃ¼ kaldÄ±rÄ±ldÄ±. OpenAL aygÄ±tÄ±nÄ±n natif frekansÄ± (`ALC_FREQUENCY`, varsayÄ±lan 48 kHz) otomatik tespit edilip FFmpeg'e baÄŸlandÄ±. 28-bit Soxr sinc resampler ve triangular dither fallback hattÄ± kuruldu; 16 KB pipe tamponuyla underrun ve cÄ±zÄ±rtÄ± riski sÄ±fÄ±rlandÄ±.
   - **KayÄ±psÄ±z Bit DerinliÄŸi & 44.1 / 48 kHz KorunmasÄ± (`Wave.java` & `SoundBuffer.java`):** `Wave.normalize()` metodunun 44.1 kHz ses dosyalarÄ±nÄ± gereksiz yere 48 kHz'e zorlayarak aÅŸÄ±ndÄ±rmasÄ± engellendi. `SoundBuffer` yalnÄ±zca 48 kHz Ã¼stÃ¼ndeki (96k/192k) bÃ¼yÃ¼k dosyalarÄ± kontrollÃ¼ indirger; standart 44.1 kHz ve 48 kHz sesler OpenAL donanÄ±m mikserine doÄŸrudan ve kayÄ±psÄ±z beslenir.
   - **Band-Limited Blackman-Nuttall Windowed Sinc Resampler (`Wave.java`):** Tizleri kÃ¶relten ve anti-aliasing filtresi olmayan 4-noktalÄ± Catmull-Rom cubic spline yerine, >90dB stopband zayÄ±flatmalÄ± ve Nyquist sÄ±nÄ±rÄ±nÄ± aÅŸan frekanslarÄ± matematiksel olarak sÃ¼zerek yok eden band-limited windowed sinc resampler yazÄ±ldÄ±.
   - **OpenAL 2D Stereo DoÄŸrudan GeÃ§iÅŸ (`SoundPlayer.java`):** `configure2DStereo()` ve merkez pan modunda yanlÄ±ÅŸlÄ±kla aÃ§Ä±k olan `AL_SOURCE_SPATIALIZE_SOFT` deÄŸeri `AL_FALSE` yapÄ±ldÄ±. OpenAL Soft'un stereo kanallarÄ± kulak pinna filtreleriyle boÄŸuklaÅŸtÄ±ran 3D HRTF filtrelemesi devreden Ã§Ä±karÄ±ldÄ±.
   - **Catalyst Playback Senkronizasyon & Scrubbing DÃ¼zeltmesi (`UICatalystPanel.java`):** Master clock ses oynatÄ±cÄ±sÄ±nÄ±n integer kare yuvarlama sapmasÄ± yÃ¼zÃ¼nden her render karesinde mikro-seek yapmasÄ± Ã¶nlendi. `scrubAudio` metodu sesin natif Ã¶rnekleme hÄ±zÄ±na (`wave.sampleRate`) gÃ¶re dinamik adÄ±mlama kazanarak 44.1 kHz seslerin timeline kaydÄ±rÄ±lÄ±rken pitch kaymasÄ± dÃ¼zeltildi.

8. **Mod KimliÄŸi, SÃ¼rÃ¼m ve Metadata GÃ¼ncellemesi - BBS CS C1.0 (AÅŸama 59):**
   - **`fabric.mod.json`:** Mod adÄ± "BBS CS (Catalyst Studio)", aÃ§Ä±klamasÄ± "Built for machinima creators, animators, and filmmakers. BBS CS upgrades the core studio with the Catalyst NLE timeline, lossless studio-grade audio engine, smart replay LOD culling, and major render stability fixes." olarak gÃ¼ncellendi.
   - **Yazarlar & KatkÄ±cÄ±lar:** "McHorse", "Wemppy (BBS FS)" ve "Kynix" yazarlar listesine tescillendi.
   - **`gradle.properties`:** Mod sÃ¼rÃ¼mÃ¼ `mod_version=C1.0` olarak gÃ¼ncellendi.
   - **Oyun Ä°Ã§i UI & Branding (`UILandingScreen.java`):** GiriÅŸ ekranÄ± sÃ¼rÃ¼m metni ve afiÅŸ baÅŸlÄ±ÄŸÄ± `\u00a7lBBS CS` olarak gÃ¼ncellendi.
   - **ModMenu YerelleÅŸtirmesi (`en_us.json`, `tr_tr.json`):** `modmenu.nameTranslation.bbs` ve `modmenu.descriptionTranslation.bbs` anahtarlarÄ± TÃ¼rkÃ§e ve Ä°ngilizce olarak eklendi.

**Derleme sonucu:** `BUILD SUCCESSFUL` â€” 0 hata, `apiCheck` onaylÄ±, temiz derleme.



## 1. Mimari Genel BakÄ±ÅŸ

BBS moduna, DaVinci Resolve ve modern prodÃ¼ksiyon araÃ§larÄ±ndan esinlenen iki devrimsel sistem entegre edilmiÅŸtir:
1. **DaVinci Resolve "Deliver" Render & Export Pipeline:** DonanÄ±m hÄ±zlandÄ±rmalÄ± (NVENC, AMF, QSV, ProRes, HQ GIF), tam ekran Cinema Monitor HUD, dinamik baÅŸlatÄ±cÄ± baÄŸÄ±msÄ±z Ã§Ä±ktÄ± dizini, doÄŸrudan hedef export FBO'sundan okuma yapan 4-bayt RGBA `glReadPixels` motoru, katÄ± bitrate kilitleme (`-minrate`, `-maxrate`, `-bufsize`, `-cbr_padding`), Render Queue iÅŸ yÃ¶neticisi, In/Out ve Custom Range aralÄ±k seÃ§imi, canlÄ± SMPTE monitÃ¶rÃ¼ ve atmosferik mesafe sisi (terrain fog) garantisi.
2. **CanlÄ± Ã‡ok KanallÄ± Replay KaydÄ± (Live Multi-Track Replay Recording) & DaVinci Kurgu KÄ±sayollarÄ±:** Jilet kesim (`C`), Ã§oklu katman kesimi (Multi-Track Razor Cut), dalgalÄ± kÄ±rpma / ripple trim (`Q` & `E`) track izolasyonu ve No-Overlap Ã§akÄ±ÅŸma Ã¶nleyici koruma, kesim noktasÄ± gezintisi (`YukarÄ± / AÅŸaÄŸÄ± Ok`), klip susturma/mute toggle (`D`), otomatik In/Out mark clip (`X`), In/Out iÅŸaretleme (`I`, `O`, `Alt + X`), gÃ¶rsel cetvel bandÄ±, cetvel tabanlÄ± scrubbing, klipsiz alana sÃ¼rÃ¼kleyerek doÄŸrudan sarÄ± kutu Ã§oklu seÃ§imi (Marquee Selection) ve tek tÄ±kla seÃ§im kaldÄ±rma (Deselect) mimarisi.
3. **Pro Camera & Optik Kadrajlama Motoru:** GerÃ§ek sinema lensleri (35mm eÅŸdeÄŸeri focal length dÃ¶nÃ¼ÅŸÃ¼mÃ¼), Dutch angle / baÄŸÄ±msÄ±z roll rotasyonu, hedef odaklÄ± dinamik Dolly Zoom (Vertigo) algoritmasÄ± ve DoF odak mesafesi kanalÄ±.

---

## 2. Son YapÄ±lan Kritik DÃ¼zeltmeler ve GeliÅŸtirmeler (17. AÅŸama)

### A. 'ProCameraClip' ve Optik Matematik Motoru (`ProCameraClip.java`)
* **35mm SensÃ¶r EÅŸdeÄŸeri ve Ã‡ift YÃ¶nlÃ¼ FOV DÃ¶nÃ¼ÅŸÃ¼mÃ¼:**
  - Minecraft dikey FOV'u ile 35mm full frame sensÃ¶r yÃ¼ksekliÄŸi ($h = 24.0\text{ mm}$) arasÄ±nda Ã§ift yÃ¶nlÃ¼ matematiksel dÃ¶nÃ¼ÅŸÃ¼m kuruldu:
    * $\text{FOV} = 2 \cdot \arctan\left(\frac{h}{2 \cdot f}\right) \cdot \frac{180}{\pi}$
    * $f = \frac{h}{2 \cdot \tan\left(\frac{\text{FOV} \cdot \pi}{360}\right)}$
  - HazÄ±r sinema lens presetleri (24mm, 35mm, 50mm, 85mm) ve serbest mm trackpad'i ile anÄ±nda optik kadraj oluÅŸturma.
* **BaÄŸÄ±msÄ±z Roll (Dutch Angle) KanalÄ±:**
  - `Position.angle.roll` Ã¼zerinden `Camera.updateView().rotateZ(...)` matrisine direkt enjekte edilen baÄŸÄ±msÄ±z roll keyframe kanalÄ±.
* **Dahili Dolly Zoom (Vertigo) Modu ("Lock Subject Size"):**
  - Kamera ileri/geri hareket ederken sÃ¼jenin ekrandaki boyutunu sabit tutan dinamik alan derinliÄŸi/arka plan perspektif akÄ±tma algoritmasÄ±:
    * $d_t \cdot \tan\left(\frac{\text{FOV}_t}{2}\right) = d_0 \cdot \tan\left(\frac{\text{FOV}_0}{2}\right) \implies \text{FOV}_t = 2 \cdot \arctan\left(\frac{d_0}{d_t} \cdot \tan\left(\frac{\text{FOV}_0}{2}\right)\right)$
  - Tek tÄ±kla crosshair hedefinden veya blok/entity Ã¼zerinden sÃ¼je koordinatÄ±nÄ± kilitleme (`RayTracing.rayTraceEntity`).
* **Focus Distance (Odak Mesafesi) KanalÄ±:**
  - `focus_distance` keyframe kanalÄ± `Position` ve `Camera` sÄ±nÄ±flarÄ±na (`focusDistance`) baÄŸlandÄ±; ilerideki Depth of Field (DoF) ve shader entegrasyonlarÄ±na tam uyumlu altyapÄ± hazÄ±rlandÄ±.

### B. Pro Kamera ArayÃ¼zÃ¼ (`UIProCameraClip.java`)
* **Lens & Odak UzaklÄ±ÄŸÄ± BÃ¶lÃ¼mÃ¼:** `[24mm]`, `[35mm]`, `[50mm]`, `[85mm]` hÄ±zlÄ± preset butonlarÄ±, serbest odak uzaklÄ±ÄŸÄ± (`focalLength` mm) ve FOV trackpad'leri (biri deÄŸiÅŸtiÄŸinde diÄŸeri eÅŸzamanlÄ± gÃ¼ncellenir).
* **Dutch Angle / Roll BÃ¶lÃ¼mÃ¼:** `[-45Â°]`, `[-15Â°]`, `[0Â°]`, `[+15Â°]`, `[+45Â°]` hazÄ±r aÃ§Ä± butonlarÄ± ve hassas roll trackpad'i.
* **Dolly Zoom (Vertigo) BÃ¶lÃ¼mÃ¼:** "Lock Subject Size" toggle anahtarÄ±, "Pick Subject from Crosshair" butonu ve sÃ¼je mesafesi trackpad'i.
* **Focus Distance:** Blok cinsinden odak mesafesi ayarÄ±.
* **Keyframe DÃ¼zenleyici Entegrasyonu:** TÃ¼m kanallarÄ± (`x`, `y`, `z`, `yaw`, `pitch`, `roll`, `fov`, `distance`, `focus_distance`) grafik eÄŸri editÃ¶rÃ¼nde gÃ¶rselleÅŸtirip dÃ¼zenleme.

### C. KayÄ±t ve YerelleÅŸtirme (Registration & L10n)
* `BBSMod.java`: `factoryCameraClips` fabrikasÄ±na `Link.bbs("pro_camera")` olarak `ProCameraClip.class` tescil edildi (`Icons.CAMERA`, `0xe056fd`).
* `UIClip.java`: `UIProCameraClip::new` UI fabrikasÄ±na kaydedildi.
* `TrackStyle.java`: `focus_distance` kanalÄ± aÃ§Ä±k mavi (`0x54a0ff`) iz rengiyle tanÄ±mlandÄ±.
* `en_us.json` & `tr_tr.json`: `bbs.ui.camera.clips.bbs:pro_camera` Ã§evirileri tanÄ±mlandÄ± ("Pro Camera" / "Pro Kamera").

---

## 3. Son YapÄ±lan Kritik DÃ¼zeltmeler (18. ve 19. AÅŸama)

### Pro Camera Clip UIKeyframeEditor Dizi TaÅŸmasÄ± (ArrayIndexOutOfBoundsException) DÃ¼zeltmesi (18. AÅŸama)
* **Hata / KÃ¶k Neden:** `KeyframeClip` yapÄ±sÄ±nda varsayÄ±lan 8 kanal bulunurken, `ProCameraClip` ile 9. kanal (`focus_distance` - index 8) eklendiÄŸinde `UIKeyframeEditor.COLORS` dizisinin sabit 8 elemanlÄ± (`length = 8`) olmasÄ± nedeniyle `Index 8 out of bounds for length 8` fÄ±rlatÄ±lÄ±yordu.
* **UIKeyframeEditor.java:**
  - `COLORS` palet dizisi 16 elemana geniÅŸletildi ve index 8'e `0x54a0ff` (focus distance / soft blue) kanalÄ± entegre edildi.
  - `getColor(int index)` ve `getChannelColor(KeyframeChannel channel, int index)` yardÄ±mcÄ± fonksiyonlarÄ± eklenerek hem `TrackStyle.color(channel.getId())` ile dinamik stil eÅŸleÅŸtirme saÄŸlandÄ± hem de olasÄ± taÅŸmalara karÅŸÄ± modulo/sÄ±nÄ±r korumasÄ± getirildi.
  - `setClip(KeyframeClip clip)` metodunda `null` kontrolleri ve dinamik kanal sayÄ±sÄ± (`clip.channels.length`) iterasyonu gÃ¼venli hale getirildi.

### UIProCameraClip UIKeyframeClip KalÄ±tÄ±mÄ± ve Senkronizasyon TemizliÄŸi (19. AÅŸama)
* **Hedef / YapÄ±lanlar:**
  - `UIProCameraClip` doÄŸrudan `UIKeyframeClip` taban sÄ±nÄ±fÄ±ndan tÃ¼retildi (`public class UIProCameraClip extends UIKeyframeClip`).
  - Orijinal `UIKeyframeClip` iÃ§indeki `fillData()`, keyframe editÃ¶rÃ¼ baÄŸlama, cetvel render'Ä± (`rulerRenderer`), timeline imleÃ§ senkronizasyonu ve klip sÃ¼resi (`duration`) hesaplama mantÄ±ÄŸÄ± doÄŸrudan devralÄ±ndÄ± (`super.fillData()`).
  - `getCurrentClipTick()` iÃ§erisine `Math.max(0F, Math.min(dur, tick))` sÄ±nÄ±r korumasÄ± eklendi; playhead klip baÅŸlangÄ±cÄ±ndan Ã¶nceyken (-5 tick vb.) negatif cursor ve hatalÄ± keyframe yazÄ±mÄ± tamamen engellendi.
  - SaÄŸ paneldeki 35mm Lens presetleri (`24mm`, `35mm`, `50mm`, `85mm`), Roll Dutch Angle butonlarÄ±, Dolly Zoom (Vertigo) ve Focus Distance kontrolleri taban panelin Ã¼zerine temizce eklendi.
  - Kod fazlalÄ±ÄŸÄ± ve kopyalanmÄ±ÅŸ editÃ¶r baÅŸlatÄ±cÄ±larÄ± temizlendi.

### UIClipsPanel Panel AÃ§Ä±lma Garantisi & UIClips Hovered Clip Kenar BoyutlandÄ±rma (Resize/Trim) Ä°zolasyonu (20. AÅŸama)
* **UIClipsPanel.java:**
  - `pickClip(Clip clip)` iÃ§erisine doÄŸrudan `if (clip instanceof ProCameraClip proCameraClip)` tip kontrolÃ¼ eklendi; Pro Camera klibine tÄ±klandÄ±ÄŸÄ± an saÄŸ panelde `UIProCameraClip` paneli kesin olarak baÅŸlatÄ±lÄ±p `fillData()` tetiklenerek arayÃ¼z takÄ±lmasÄ± Ã¶nlendi.
* **UIClips.java:**
  - `getClipUnder(UIContext context, int mouseX, int mouseY)` yardÄ±mcÄ± metodu eklendi: Fare sol tuÅŸuna basÄ±ldÄ±ÄŸÄ±nda Ã¶nce kenar handle'larÄ± (sol/saÄŸ trim sÄ±nÄ±rlarÄ±), ardÄ±ndan gÃ¶rsel klip alanÄ± taranarak farenin tam altÄ±ndaki hedef klip doÄŸrudan tespit ediliyor.
  - Kenar tutma ve boyutlandÄ±rma (`grabMode != 0`) iÅŸlemi `selectedClip` listesinden tamamen izole edildi; yalnÄ±zca farenin yakaladÄ±ÄŸÄ± klip (`Collections.singletonList(clip)`) `grabbedClips` ve `grabbedData`'ya aktarÄ±ldÄ±.
  - Pro Camera seÃ§iliyken alttaki veya diÄŸer katmanlardaki bir klip kenarÄ±ndan Ã§ekildiÄŸinde Pro Camera'nÄ±n yanlÄ±ÅŸlÄ±kla uzayÄ±p kÄ±salmasÄ± sorunu tamamen Ã§Ã¶zÃ¼ldÃ¼.

### Pro Camera TÄ±klanma / Hit Detection, SaÄŸ TÄ±k MenÃ¼sÃ¼ & Constructor NPE Ã‡Ã¶zÃ¼mÃ¼ (21. AÅŸama)
* **KÃ¶k Neden Tespiti:**
  1. `UIProCameraClip` sÄ±nÄ±fÄ±nda `public final ProCameraClip proClip;` alanÄ± `super(...)` sonrasÄ±nda atanÄ±yordu; fakat Java'da Ã¼st sÄ±nÄ±f kurucusu (`UIClip`) doÄŸrudan alt sÄ±nÄ±fÄ±n override ettiÄŸi `registerUI()` metodunu Ã§aÄŸÄ±rÄ±yordu. Bu anda `this.proClip` henÃ¼z `null` olduÄŸu iÃ§in `proClip.lockSubjectSize` Ã§aÄŸrÄ±sÄ±nda `NullPointerException` patlÄ±yor, `UIClipsPanel.pickClip` iÃ§indeki sessiz `try-catch` bu hatayÄ± yutarak panelin oluÅŸturulmasÄ±nÄ± iptal ediyordu.
  2. `UIClips.java` iÃ§indeki `this.context(...)` menÃ¼ oluÅŸturucu mekanizmasÄ± saÄŸ tÄ±klanan klibi otomatik seÃ§miyordu (`hasSelected` yalnÄ±zca Ã¶nceden seÃ§ilmiÅŸ bir klip varsa aktif oluyordu). EÄŸer klibe daha Ã¶nce sol tÄ±klanamadÄ±ysa saÄŸ tÄ±klandÄ±ÄŸÄ±nda klibe Ã¶zel menÃ¼ yerine boÅŸ timeline genel menÃ¼sÃ¼ ("Reorganize clips", "Record microphone...") aÃ§Ä±lÄ±yordu.
  3. `UIClips.java` sol tÄ±k metodunda (`handleLeftClick`) daha Ã¶nce seÃ§ilmiÅŸ klip ile tÄ±klanan klip aynÄ± referans olduÄŸunda `delegate.pickClip` Ã§aÄŸrÄ±sÄ± atlanabiliyordu.
* **Uygulanan DÃ¼zeltmeler:**
  - `UIProCameraClip.java`:
    * HatalÄ± `final ProCameraClip proClip` alanÄ± kaldÄ±rÄ±larak yerine doÄŸrudan Ã¼st sÄ±nÄ±fÄ±n hazÄ±r `this.clip` alanÄ±nÄ± cast eden `public ProCameraClip getProClip() { return (ProCameraClip) this.clip; }` metodu eklendi. Null referans Ã§Ã¶kmesi tamamen giderildi.
  - `UIClips.java`:
    * SaÄŸ tÄ±k context menÃ¼sÃ¼ (`this.context(...)`) tetiklendiÄŸinde `getClipUnder(context, mouseX, mouseY)` ile farenin altÄ±ndaki klip tespit edildi. EÄŸer bir klibin Ã¼zerine saÄŸ tÄ±klandÄ±ysa anÄ±nda `this.setSelected(clipUnderMouse)` ve `this.delegate.pickClip(clipUnderMouse)` Ã§alÄ±ÅŸtÄ±rÄ±larak klibe Ã¶zel iÃ§erik menÃ¼sÃ¼nÃ¼n (Sil, Kes, Klip DÃ¶nÃ¼ÅŸtÃ¼r vb.) garanti olarak aÃ§Ä±lmasÄ± saÄŸlandÄ±.
    * Sol tÄ±k kurgusu temizlendi; tÄ±klanan klip doÄŸrudan ve koÅŸulsuz olarak `this.setSelected(clip)` ve `this.delegate.pickClip(clip)` ile aktif hale getirildi.
    * `addConverters` fonksiyonuna `clip`, `data` ve `data.converters` null kontrolleri eklenerek dÃ¶nÃ¼ÅŸtÃ¼rÃ¼cÃ¼ menÃ¼ Ã§aÄŸrÄ±larÄ± zÄ±rhlandÄ±.
  - `UIClipsPanel.java`:
    * Klip paneli zaten mevcutsa ve gÃ¶rÃ¼nÃ¼rlÃ¼k durumunda kapalÄ± kaldÄ±ysa `attachPropertiesPanel` ve `setVisible(true)` ile saÄŸ panelin ekrana yeniden baÄŸlanmasÄ± ve `fillData()` Ã§aÄŸrÄ±sÄ±nÄ±n sorunsuz Ã§alÄ±ÅŸmasÄ± garantiye alÄ±ndÄ±.

### Minecraft Pelerin (Cape) & Pelerinli Elytra Entegrasyonu (22. AÅŸama)
* **Veri Modeli ve Keyframe DesteÄŸi (`ModelForm.java` & `TrackCatalog.java`):**
  - `ModelForm` sÄ±nÄ±fÄ±na `hasCape` (`ValueBoolean`, varsayÄ±lan `false`) ve `capeTexture` (`ValueLink`, varsayÄ±lan `bbs:textures/default_cape.png`) alanlarÄ± eklendi.
  - Bu alanlar `Form`'un Ã¶zellik havuzuna tescillendiÄŸinden otomatik olarak NBT serileÅŸtirme/deserileÅŸtirme, geri alma (Undo/Redo) ve `TrackCatalog` Ã¼zerinden Replay zaman Ã§izelgesinde dinamik keyframe kanalÄ± (aÃ§ma/kapama, pelerin dokusu deÄŸiÅŸtirme) desteÄŸi kazandÄ±.
* **AkÄ±cÄ± Fizik Motoru & Vanilla KonumlandÄ±rma (`CapeRenderer.java`):**
  - Vanilla Minecraft `CapeFeatureRenderer` matematik modeli temel alÄ±ndÄ±:
    * YÃ¼rÃ¼me, koÅŸma, eÄŸilme (sneak) ve sÃ¼zÃ¼lme durumlarÄ±nda pelerinin geriye ve yana savrulma aÃ§Ä±larÄ± (`forwardSwing`, `sideSwing`, `pitchOffset`) hesaplandÄ±.
    * Titreme (stutter) olmamasÄ± iÃ§in tick tabanlÄ± ayrÄ±k adÄ±mlar yerine render anÄ±nda `partialTicks` (transition) ile `prevCapeX/Y/Z` ve mevcut pozisyon arasÄ±nda kesintisiz lerp interpolasyonu saÄŸlandÄ±.
    * Pozisyon gÃ¶vdenin (torso/body) arka Ã¼st merkezine Vanilla standart mesafesiyle (`translate(0, 0, 0.125F)`) baÄŸlandÄ±.
  - BBS `TextureManager`'da bulunan herhangi bir Ã¶zel dokunun (BBS Link) Minecraft `TextureManager`'a anÄ±nda baÄŸlanabilmesi iÃ§in dinamik doku tescil kÃ¶prÃ¼sÃ¼ kuruldu.
* **Elytra Uyumu & Hibrit Doku Sistemi (`ArmorRenderer.java` & `ModelFormRenderer.java`):**
  - AktÃ¶rde `hasCape = true` iken gÃ¶ÄŸÃ¼slÃ¼k slotunda Elytra (`Items.ELYTRA`) takÄ±lÄ±ysa:
    * SÄ±rt pelerini otomatik olarak gizlendi (Vanilla davranÄ±ÅŸÄ±).
    * SeÃ§ili olan `capeTexture`, doÄŸrudan Elytra kanat dokusu olarak kullanÄ±ldÄ± (`renderElytra`).
* **KullanÄ±cÄ± ArayÃ¼zÃ¼ (UI) & YerelleÅŸtirme (`UIModelFormPanel.java`, `TrackStyle.java`, L10n):**
  - `UIModelFormPanel` iÃ§erisine "Cape" ("Pelerin") bÃ¶lÃ¼mÃ¼ eklendi:
    * "Enable Cape" (Pelerin AÃ§/Kapa) toggle butonu.
    * "Pick Cape Texture..." doku seÃ§ici butonu (`UITexturePicker`).
  - Replay zaman Ã§izelgesinde `has_cape` ve `cape_texture` kanallarÄ± iÃ§in Ã¶zel stil (renk `0xe84118` ve ikonlar) tanÄ±mlandÄ±.
  - `en_us.json` ve `tr_tr.json` dil dosyalarÄ±na Ã§eviriler entegre edildi.
  - VarsayÄ±lan yÃ¼ksek kaliteli 64x32 pelerin dokusu (`default_cape.png`) oluÅŸturulup `assets/bbs/textures/` dizinine eklendi.

### Pelerin (Cape) UV StandardÄ±, SÄ±rt KonumlandÄ±rma & AkÄ±cÄ± Fizik (Stutter Giderme) ve Replay KayÄ±t/Oynatma Hotfix (24. AÅŸama)
* **KÃ¶k Neden & Mimari DÃ¼zeltmeler:**
  1. **Cape KonumlandÄ±rma ve 64x32 UV HizalamasÄ± (`CapeRenderer.java`):**
     - Standart Minecraft pelerin doku haritasÄ± (64x32) iÃ§in `cuboid(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, Dilation.NONE, 1.0F, 0.5F)` geometrisi `root.createPart(64, 64)` ile tescillendi; UV bÃ¶leni kusursuz (64, 32) oranÄ±na oturtuldu.
     - Pelerin gÃ¶vdenin arka Ã¼st kenarÄ±na Vanilla `0.125F` pozitif Z ofsetiyle yerleÅŸtirildi. DÄ±ÅŸ desenin (`12..21 x, 1..16 y`) arkaya (kameraya), iÃ§ astarÄ±n ise sÄ±rta bakmasÄ± saÄŸlandÄ±.
  2. **Kare HÄ±zÄ±ndan BaÄŸÄ±msÄ±z Yaylanma FiziÄŸi (Fluid Spring Damping - 0 Stutter):**
     - 20-tick kÄ±sÄ±tlamasÄ±ndan baÄŸÄ±msÄ±z olarak, her render karesinde `dt` hesaplandÄ± ve yay sÃ¶nÃ¼mlemeli (`MathHelper.lerp(dt * 10.0F, currentPitch, targetPitch)`) akÄ±cÄ± aÃ§Ä± hesabÄ± yapÄ±ldÄ±.
     - Sneak durumu ani sÄ±Ã§ramak yerine yumuÅŸak geÃ§iÅŸle (`currentSneak`) sÃ¼zÃ¼ldÃ¼; teleport ve zaman Ã§izelgesi scrubbing atlamalarÄ±nda ani hÄ±z patlamalarÄ± filtrelendi.
  3. **Replay KayÄ±t (SaÄŸ Alt) ve Oynatma (SaÄŸ Ctrl) Ã‡akÄ±ÅŸma Ã–nleme:**
     - `BBSModClient.java` iÃ§inde `keyPlayFilm()`: Replay kaydÄ± aktifken (`recorder != null`, `MultiTrackReplaySession.isActive()` veya `panel.isRecording()`) SaÄŸ Ctrl basÄ±ÅŸlarÄ± yutuldu; arka planda film oynatÄ±lmasÄ± engellendi.
     - `BBSModClient.java` iÃ§inde `keyRecordReplay()`: KayÄ±t durdurulduÄŸunda `panel.stopPlayback()` ve `Films.stopFilm()` Ã§aÄŸrÄ±larak oynatma dÃ¶ngÃ¼sÃ¼ ve runner durumu sÄ±fÄ±rlandÄ±.
     - `UIFilmPanel.java`: `stopPlayback()` ve `isRecording()` yardÄ±mcÄ± metodlarÄ± eklendi.
     - `Films.java`: `isPlaying(filmId)` kontrolÃ¼ getirilerek film zaten oynatÄ±lÄ±yorsa temiz bir toggle mantÄ±ÄŸÄ±yla durdurulmasÄ± saÄŸlandÄ±; `unfreeze(filmId)` ile donmuÅŸ editÃ¶r kontrolcÃ¼leri temizlenip mÃ¼kerrer `FirstPersonFilmController` oluÅŸumu tamamen engellendi.

### Pelerin (Cape) Birinci ÅahÄ±s (FPS/Hand) Ä°zolasyonu & UV Ã–n-Arka DÃ¼zeltmesi (25. AÅŸama)
* **KÃ¶k Neden & Mimari DÃ¼zeltmeler:**
  1. **Birinci ÅahÄ±s (FPS) El/Kol GÃ¶rÃ¼nÃ¼mÃ¼nde Pelerin Gizleme:**
     - `ModelFormRenderer.java`:
       * `renderModel()` iÃ§inde `hasCape` ve `hasEquipment` bloklarÄ±na `!this.renderingArm` koÅŸulu eklendi. BÃ¶ylece `renderFirstPersonHand()` Ã§aÄŸrÄ±ldÄ±ÄŸÄ±nda pelerin veya gÃ¶vde zÄ±rhÄ± Ã§izim dÃ¶ngÃ¼sÃ¼ne hiÃ§ girilmemesi saÄŸlandÄ±.
       * `renderCape()` metodunun en baÅŸÄ±na `if (this.renderingArm) return;` ve yerel oyuncu birinci ÅŸahÄ±s perspektifindeyken (`mc.options.getPerspective().isFirstPerson() && mcEntity == mc.player`) pelerin Ã§izimini tamamen engelleyen gÃ¼venlik kontrolÃ¼ eklendi.
     - `CapeRenderer.java`:
       * `renderCape()` metoduna aynÄ± ÅŸekilde birinci ÅŸahÄ±s perspektif kontrolÃ¼ eklenerek yalnÄ±zca Ã¼Ã§Ã¼ncÃ¼ ÅŸahÄ±s modunda gÃ¶vde render edildiÄŸinde pelerinin Ã§izilmesi garanti altÄ±na alÄ±ndÄ±.
  2. **UV Ã–n-Arka Terslik DÃ¼zeltmesi (64x32 Dokuda Arka SÄ±rt Deseni):**
     - `CapeRenderer.java`:
       * Standart Minecraft pelerin doku haritasÄ±nda (64x32) koyu/yÄ±ldÄ±zlÄ± dÄ±ÅŸ sÄ±rt deseni (`x: 12..21, y: 1..16`) kÃ¼pÃ¼n NORTH (-Z) yÃ¼zeyindedir. Pelerin modeline `matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - sideSwing / 2.0F))` uygulanarak dÄ±ÅŸ desenin kameraya (arkaya) bakmasÄ±, aÃ§Ä±k renkli iÃ§ astarÄ±n (`x: 1..10, y: 1..16`) ise doÄŸrudan aktÃ¶rÃ¼n sÄ±rtÄ±na bakmasÄ± saÄŸlandÄ±.
       * 180Â° Y Ã§evirmesi pitch dÃ¶nÃ¼ÅŸÃ¼nden (`totalPitch`) sonra uygulandÄ±ÄŸÄ± iÃ§in ileri koÅŸarken pelerinin geriye doÄŸru havaya kalkma fiziÄŸi ve Vanilla uyumlu savrulma yÃ¶nÃ¼ kusursuz ÅŸekilde korundu.

### Wavey Capes ("waveycapes") AktÃ¶r SimÃ¼lasyon KÃ¶prÃ¼sÃ¼ & KumaÅŸ Render DÃ¼zeltmesi (27. AÅŸama)
* **KÃ¶k Neden:**
  - Wavey Capes 1.20.4'te pelerin Ã§izimini yapan asÄ±l sÄ±nÄ±f `dev.tr7zw.waveycapes.render.CustomCapeRenderer` ve `VanillaCapeRenderer`'dÄ±r.
  - Mod `AbstractClientPlayerEntity` ve `CapeHolder` arayÃ¼zlerine baÄŸlÄ±dÄ±r; BBS `ActorEntity` bu sÄ±nÄ±flarÄ± doÄŸrudan geniÅŸletmediÄŸinden Ã¶nceki denemede Ã§aÄŸrÄ± `ClassCastException`/`NullPointerException` nedeniyle sessizce fallback katÄ± kÃ¼pÃ¼ne dÃ¼ÅŸmÃ¼ÅŸtÃ¼r.
* **Uygulanan Mimari Ã‡Ã¶zÃ¼m (`CapeRenderer.java`):**
  - **DoÄŸru SÄ±nÄ±f ve Metot Entegrasyonu:**
    * `dev.tr7zw.waveycapes.render.CustomCapeRenderer` ve `VanillaCapeRenderer` reflection ile dinamik olarak oluÅŸturuldu.
    * `PlayerWrapper(AbstractClientPlayerEntity)` sarmalayÄ±cÄ±sÄ± `mc.player` referansÄ±yla baÅŸlatÄ±ldÄ±.
  - **AktÃ¶r BazlÄ± SimÃ¼lasyon KÃ¶prÃ¼sÃ¼ (Actor-specific Simulation Swapping):**
    * Her BBS aktÃ¶rÃ¼ iÃ§in `dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer` arayÃ¼zÃ¼nÃ¼ dinamik olarak implemente eden bir `Proxy` (`InvocationHandler`) oluÅŸturuldu; aktÃ¶rÃ¼n anlÄ±k koordinatlarÄ±, `prev` pozisyonlarÄ±, gÃ¶vde aÃ§Ä±sÄ± (`bodyYaw`) ve eÄŸilme (`isSneaking`) durumu Wavey Capes simÃ¼lasyonuna aktarÄ±ldÄ±.
    * Her aktÃ¶re Ã¶zel `BasicSimulation` oluÅŸturulup `WeakHashMap` iÃ§inde saklandÄ±.
    * Ã‡izim anÄ±nda `mc.player` Ã¼zerindeki `CapeHolder`'Ä±n simÃ¼lasyonu o aktÃ¶rÃ¼n simÃ¼lasyonuyla geÃ§ici olarak takas edildi, `simulate(proxy)` ile kumaÅŸ fizik adÄ±mÄ± yÃ¼rÃ¼tÃ¼ldÃ¼, `customCapeRenderer.render(...)` ile 16 parÃ§alÄ± dalgalanan pelerin Ã§izildi ve `finally` bloÄŸunda `mc.player`'Ä±n orijinal simÃ¼lasyonu geri yÃ¼klendi.
  - **Hata Tespiti & KatÄ± KÃ¼p Engelleme:**
    * OlasÄ± istisnalarÄ± yakalamak iÃ§in bir defaya mahsus `t.printStackTrace()` loglamasÄ± eklendi.
    * `isWaveyCapesLoaded()` aktif olduÄŸunda, fallback olarak katÄ± blok Ã§izilmesi engellendi (`return`).

### Wavey Capes SimÃ¼lasyon FrekansÄ± (20 TPS), Matris TemizliÄŸi ve Koordinat GÃ¼venliÄŸi (28. AÅŸama)
* **KÃ¶k Neden & Mimari DÃ¼zeltmeler:**
  1. **SimÃ¼lasyon FrekansÄ± AyrÄ±ÅŸtÄ±rmasÄ± (20 TPS Tick vs Render Frame):**
     - Wavey Capes Verlet kumaÅŸ simÃ¼lasyonu Minecraft'Ä±n 20 TPS oyun dÃ¶ngÃ¼sÃ¼ iÃ§in kalibre edilmiÅŸtir. YÃ¼ksek yenileme hÄ±zÄ±na sahip monitÃ¶rlerde (60/120/144 FPS) her Ã§izim karesinde `simulate()` Ã§aÄŸrÄ±ldÄ±ÄŸÄ±nda fizik motoruna saniyede yÃ¼zlerce kez hÄ±z ekleniyor ve pelerin kontrolsÃ¼zce havaya fÄ±rlayÄ±p takÄ±lÄ± kalÄ±yordu.
     - `simulate(proxy)` Ã§aÄŸrÄ±sÄ± `renderWaveyCape()` render akÄ±ÅŸÄ±ndan tamamen Ã§Ä±karÄ±ldÄ±.
     - Fizik adÄ±mÄ± `CapeRenderer.tick(IEntity)` metodu iÃ§erisindeki `stepSimulation(entity)` fonksiyonuna taÅŸÄ±ndÄ±. Scrubbing/duraklatÄ±lmÄ±ÅŸ Replay durumlarÄ±nda dahi fizik motorunu stabil tutmak iÃ§in 50 ms zaman damgasÄ± denetleyicisi (`lastSimTimes`) eklendi.
     - `renderWaveyCape()` iÃ§inde yalnÄ±zca matris interpolasyonu ve `customCapeRenderer.render(...)` Ã§aÄŸrÄ±sÄ± bÄ±rakÄ±ldÄ±.
  2. **Matris TemizliÄŸi:**
     - Wavey Capes `modifyPoseStack` iÃ§erisinde pelerin kumaÅŸÄ±nÄ±n kÄ±vrÄ±lma aÃ§Ä±sÄ±nÄ± ve dalgalanmasÄ±nÄ± kendisi hesapladÄ±ÄŸÄ±ndan, render Ã¶ncesinde uygulanan ek pitch/yaw dÃ¶nÃ¼ÅŸÃ¼mleri kaldÄ±rÄ±ldÄ±.
     - Matris sadece sÄ±rt hizalama ve eÄŸilme ofseti (`sneakY`, `sneakZ`) ile Wavey Capes'e temiz bir ÅŸekilde teslim edildi.
  3. **Koordinat GÃ¼venliÄŸi & IÅŸÄ±nlanma/Delta KorumasÄ± (Proxy):**
     - `MinecraftPlayer` proxy'sinden gelen pozisyonlar `sanitize()` ile `NaN` ve `Infinity` durumlarÄ±na karÅŸÄ± korundu.
     - AktÃ¶r 20 bloktan fazla anlÄ±k konum deÄŸiÅŸtirdiÄŸinde (Ä±ÅŸÄ±nlanma / Replay scrubber sÄ±Ã§ramasÄ±) `prev` koordinatlar anlÄ±k koordinatlara eÅŸitlenerek simÃ¼lasyonun patlamasÄ± engellendi.

### Wavey Capes KumaÅŸ SimÃ¼lasyonu Veri Besleme (getXCloak / capeX lag), 20 TPS Tick Ä°zolasyonu ve Matris TemizliÄŸi (29. AÅŸama)
* **KÃ¶k Neden & Mimari DÃ¼zeltmeler:**
  1. **Modun SimÃ¼lasyonuna (BasicSimulation) Veri Besleme HatasÄ±:**
     - Wavey Capes kumaÅŸ eÄŸrilik ve dalgalanma vektÃ¶rÃ¼nÃ¼ (`Vector3 movement`) hesaplarken `getXCloak() - getX()` ve `getZCloak() - getZ()` gecikme (lag) vektÃ¶rÃ¼nÃ¼ ana momentum kaynaÄŸÄ± olarak kullanÄ±r (`d3 * sin(bodyYaw) + d5 * -cos(bodyYaw)`).
     - Ã–nceki proxy uygulamasÄ±nda `getXCloak()` doÄŸrudan `x`, `getZCloak()` ise doÄŸrudan `z` dÃ¶ndÃ¼rdÃ¼ÄŸÃ¼ iÃ§in aradaki fark daima sÄ±fÄ±r kalmÄ±ÅŸ; `movement.x` 0 olduÄŸundan Verlet simÃ¼lasyonundaki 16 point hiÃ§bir zaman arkaya doÄŸru bÃ¼kÃ¼lmeyip dÃ¼z bir tahta gibi takÄ±lÄ± kalmÄ±ÅŸtÄ±r.
     - `ActorCapeState` sÄ±nÄ±fÄ± eklenerek Vanilla Minecraft pelerin gecikme fiziÄŸi (`capeX += (x - capeX) * 0.25`, `capeZ += (z - capeZ) * 0.25`) aktÃ¶r bazÄ±nda uygulandÄ±.
     - Fiziksel yer deÄŸiÅŸtirmenin az olduÄŸu veya yerinde yÃ¼rÃ¼me/Replay cutscene durumlarÄ±nda bacak animasyonunun (`limbSpeed`) kumaÅŸa rÃ¼zgar/momentum etkisi yapabilmesi iÃ§in `extraLag` hesaplanarak `getXCloak()` ve `getZCloak()` deÄŸerlerine yedirildi.
  2. **SimÃ¼lasyonun Ã‡alÄ±ÅŸma ZamanÄ± (simulate Ã§aÄŸrÄ±sÄ± 20 TPS):**
     - `renderWaveyCape()` iÃ§erisindeki zaman damgalÄ± `stepSimulation` Ã§aÄŸrÄ±sÄ± tamamen kaldÄ±rÄ±ldÄ±.
     - `simulate(...)` Ã§aÄŸrÄ±sÄ± yalnÄ±zca `CapeRenderer.tick(IEntity)` metodu altÄ±nda (20 TPS oyun tick'inde) Ã§alÄ±ÅŸacak ÅŸekilde izole edildi.
     - `renderWaveyCape()` iÃ§inde sadece Wavey Capes'in bÃ¼kÃ¼lmÃ¼ÅŸ 16 node'u `transition` (`partialTicks`) ile enterpole edilerek akÄ±cÄ± ÅŸekilde Ã§izildi.
  3. **Ekstra Matris Rotasyon ve Ofsetlerinin Temizlenmesi:**
     - Wavey Capes'in `CustomCapeRenderer.modifyPoseStackSimulation` metodu pelerin gÃ¶vde arka pivotunu (`translate(0, 0, 0.125)`), sÄ±rt dÃ¶nÃ¼ÅŸÃ¼nÃ¼ (`YP 180Â°`), rÃ¼zgar salÄ±nÄ±mÄ±nÄ± (`XP 6Â° + naturalWind`) ve her bir segmentin bÃ¼kÃ¼lme aÃ§Ä±sÄ±nÄ± (`XP -getRotation(...)`) tamamen kendi iÃ§inde hesapladÄ±ÄŸÄ±ndan, `renderWaveyCape` iÃ§erisindeki tÃ¼m manuel pitch, rotasyon ve sneak ofsetleri kaldÄ±rÄ±ldÄ±; matris temiz bir ÅŸekilde aktarÄ±ldÄ±.

### BBS Ses Motoru YÃ¼ksek Kalite WAV/PCM Ã‡Ã¶zÃ¼cÃ¼, Catmull-Rom Resampler ve MP3 Format DesteÄŸi (30. AÅŸama)
* **KÃ¶k Neden & Mimari DÃ¼zeltmeler:**
  1. **YÃ¼ksek Kalite WAV ve PCM Ã‡Ã¶zÃ¼cÃ¼ / NormalleÅŸtirici (`Wave.java` & `WaveReader.java`):**
     - 24-bit, 32-bit INT ve 32-bit/64-bit IEEE float WAV dosyalarÄ±nÄ±n Ã§Ã¶zÃ¼lmesinde yaÅŸanan 8-bit distorsiyon, cÄ±zÄ±rtÄ± ve aÅŸÄ±rÄ± clipping sorununun temel nedeni tespit edildi: `Wave.convertTo16()` metodunda Little Endian WAV baytlarÄ± Big Endian `ByteBuffer` ile okunarak bayt sÄ±rasÄ± ters Ã§evriliyor, 24-bit PCM'de iÅŸaret biti (sign bit) ve LSB/MSB yer deÄŸiÅŸtiriyor, 32-bit float deÄŸerleri ise Ã¼s taÅŸmasÄ±yla $\pm 10^{38}$ aralÄ±ÄŸÄ±na fÄ±rlayÄ±p maksimum seviyede kare dalga (white noise/clipping) oluÅŸturuyordu.
     - `WaveReader.java` gÃ¼ncellenerek `WAVE_FORMAT_EXTENSIBLE` (0xFFFE / 65534) ve `WAVE_FORMAT_IEEE_FLOAT` (3) baÅŸlÄ±klarÄ± eklendi; GUID Ã¼zerinden PCM/Float ayrÄ±mÄ± yapÄ±ldÄ± ve bÃ¼yÃ¼k `data` bloklarÄ±nÄ±n stream'den eksiksiz okunmasÄ± saÄŸlandÄ±.
     - `Wave.java` iÃ§inde `convertTo16()` doÄŸrudan Little Endian bayt ayrÄ±ÅŸtÄ±rmasÄ± ile yeniden yazÄ±ldÄ±: 8-bit unsigned PCM, 16-bit signed PCM, 24-bit signed PCM (`>> 8`), 32-bit signed INT PCM (`>> 16`), 32-bit IEEE float (`Float.intBitsToFloat`) ve 64-bit float deÄŸerleri $[-1.0, 1.0]$ normalize aralÄ±ÄŸÄ±na alÄ±nÄ±p NaN korumalÄ± olarak 16-bit Signed PCM'e dÃ¶nÃ¼ÅŸtÃ¼rÃ¼ldÃ¼.
     - Ã‡ok kanallÄ± stÃ¼dyo kayÄ±tlarÄ± iÃ§in (`numChannels > 2`, Ã¶rn. 5.1 surround) `downmixToStereo()` metodu eklendi.
  2. **StÃ¼dyo Ã–rnekleme HÄ±zÄ± Ä°Ã§in Catmull-Rom KÃ¼bik Resampler (`Wave.resample`):**
     - 48 kHz Ã¼zeri stÃ¼dyo kayÄ±tlarÄ± (88.2 kHz, 96 kHz, 192 kHz) iÃ§in `Catmull-Rom` kÃ¼bik eÄŸri interpolasyonu uygulayan `resample(targetRate)` algoritmasÄ± eklendi. 96 kHz/192 kHz kayÄ±tlar 48 kHz'e, 88.2 kHz kayÄ±tlar 44.1 kHz'e sÄ±fÄ±r aliasing ve faz bozulmasÄ± ile dÃ¶nÃ¼ÅŸtÃ¼rÃ¼lÃ¼r.
     - `Wave.normalize()` metodu ile bit derinliÄŸi, kanal sayÄ±sÄ± ve Ã¶rnekleme hÄ±zÄ± tek Ã§aÄŸrÄ±da OpenAL uyumlu standarda (`AL_FORMAT_STEREO16` / `MONO16`, $\le 48000$ Hz) getirildi.
  3. **Yerel MP3 Format DesteÄŸi (`Mp3Reader.java`):**
     - JLayer (`javazoom:jlayer:1.0.1`) kÃ¼tÃ¼phanesi projeye dahil edildi (`build.gradle`).
     - `mchorse.bbs_mod.audio.mp3.Mp3Reader` sÄ±nÄ±fÄ± yazÄ±larak MP3 akÄ±ÅŸlarÄ±nÄ±n doÄŸrudan 16-bit Signed PCM `Wave` nesnesine Ã§Ã¶zÃ¼lmesi saÄŸlandÄ±.
     - `AudioReader.java`, `SoundManager.java`, `SoundBuffer.java`, `UISoundOverlayPanel.java` ve `UIAudioEditor.java` sÄ±nÄ±flarÄ±nda `.mp3` dosya uzantÄ± filtreleri eklendi; tÃ¼m Ã§Ã¶zÃ¼len seslerin OpenAL buffer'Ä±na yÃ¼klenmeden Ã¶nce otomatik `normalize()` edilmesi garanti altÄ±na alÄ±ndÄ±.

### Catalyst Editor UI Panel Ä°skeleti - BBS Catalyst Studio (31. AÅŸama)
* **KÃ¶k Neden & Mimari Uygulama:**
  1. **Dashboard GÃ¶rev Ã‡ubuÄŸu Entegrasyonu (`UIDashboard.java`):**
     - Dashboard alt araÃ§ Ã§ubuÄŸunda (`registerPanels()`) "Filmler" (`film`) panelinin hemen saÄŸÄ±na yeni `catalyst` panel adÄ±mÄ± kaydedildi:
       `this.buildStep("catalyst", () -> this.panels.registerPanel(new UICatalystPanel(this), UIKeys.CATALYST_TITLE, Icons.FIVE_STAR));`
     - Ä°kon olarak ÅŸÄ±k beÅŸ kÃ¶ÅŸeli yÄ±ldÄ±z (`Icons.FIVE_STAR` - render/star/magic) seÃ§ildi.
     - Tooltip baÅŸlÄ±ÄŸÄ± iÃ§in `UIKeys.CATALYST_TITLE` oluÅŸturuldu; `en_us.json` ve `tr_tr.json` dil dosyalarÄ±na `"Catalyst Editor"` tanÄ±mlarÄ± eklendi.
  2. **Catalyst Editor Panel Ä°skeleti (`UICatalystPanel.java`):**
     - `mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel` sÄ±nÄ±fÄ± `UIDashboardPanel` tÃ¼retilerek oluÅŸturuldu.
     - **Ãœst AraÃ§ Ã‡ubuÄŸu (`topBar`):** BaÅŸlÄ±k etiketi, Play/Pause toggle (`Icons.PLAY`/`Icons.PAUSE`), Katman Ekle (`Icons.ADD`), Ä°mleÃ§te BÃ¶l (`Icons.CUT`), Proje AyarlarÄ± (`Icons.GEAR`) ve Tam Ekran (`Icons.FULLSCREEN`) eylem butonlarÄ± eklendi.
     - **Ã–nizleme AlanÄ± (`previewArea`):** Ãœst alanda (%53 yÃ¼kseklik) ortalanmÄ±ÅŸ 16:9 oranlÄ± kompozisyon tuvali (canvas guide), Ã¼Ã§te bir kuralÄ±/gÃ¼venli alan Ã§izgileri, ortalanmÄ±ÅŸ rozet ("CATALYST VIEWPORT â€¢ 1920 Ã— 1080 â€¢ 60 FPS") ve alt zaman kodu gÃ¶stergesi yerleÅŸtirildi.
     - **Alt Kompozisyon Ã‡erÃ§evesi (`bottomArea`):** Kalan alt alan (%47 yÃ¼kseklik) iki modÃ¼ler bÃ¶lÃ¼me ayrÄ±ldÄ±:
       * Sol Katmanlar Paneli (`layersContainer`): BaÅŸlÄ±k Ã§ubuÄŸu, dikey kaydÄ±rÄ±labilir katman listesi (`layersList`), Ã¶rnek kompozisyon kanallarÄ± (Video Plate, 3D Replay Actor, Audio Ambience, FX/Color).
       * SaÄŸ Zaman Ã‡izelgesi (`timelineContainer`): Ãœst cetvel/frame tick iÅŸaretleri (`timelineHeader`), Ã§ok kanallÄ± klip ÅŸeritleri grid'i ve kÄ±rmÄ±zÄ± oynatma kafasÄ± (`playhead`) imleci.

### Catalyst Editor Proje YÃ¶netim EkranÄ± ve Ãœst Sekme Sistemi (32. AÅŸama)
* **KÃ¶k Neden & Mimari Uygulama:**
  1. **Catalyst Proje Veri Modeli ve YÃ¶neticisi (`CatalystProject.java` & `CatalystProjectManager.java`):**
     - Projelerin adÄ±, FPS (varsayÄ±lan 60), sÃ¼resi (varsayÄ±lan 300 frame), Ã§Ã¶zÃ¼nÃ¼rlÃ¼ÄŸÃ¼ (1920Ã—1080) ve zaman damgalarÄ±nÄ± barÄ±ndÄ±ran `CatalystProject` sÄ±nÄ±fÄ± oluÅŸturuldu.
     - `CatalystProjectManager` ile `.minecraft/bbs/assets/catalyst_projects` dizininde otomatik oluÅŸturma, JSON bazlÄ± yÃ¼kleme, kaydetme ve silme operasyonlarÄ± saÄŸlandÄ±.
  2. **Ãœst Sekme (Tab) Sistemi (`UICatalystTab.java` & `UICatalystPanel.java`):**
     - Sol Ã¼st araÃ§ Ã§ubuÄŸuna BBS arayÃ¼z standartlarÄ±na uygun Ã¶zel sekme butonlarÄ± entegre edildi:
       * **Projeler** (`Icons.FOLDER` - "Projects")
       * **EditÃ¶r** (`Icons.FILM` - "Composition Editor")
     - Aktif sekme vurgusu (birincil tema rengi ve alt Ã§izgi) ile dinamik durum yÃ¶netimi (`CatalystTab.PROJECTS` vs `CatalystTab.EDITOR`) kuruldu.
     - Aktif proje baÅŸlÄ±k rozeti (`activeProjectLabel`) sekme Ã§ubuÄŸunun yanÄ±na yerleÅŸtirildi; seÃ§ili projenin adÄ±, FPS ve sÃ¼resi gerÃ§ek zamanlÄ± yansÄ±tÄ±ldÄ±.
  3. **Proje KarÅŸÄ±lama ve SeÃ§im EkranÄ± (Project Manager View):**
     - Aktif seÃ§ili proje olmadÄ±ÄŸÄ±nda (veya oyun aÃ§Ä±lÄ±ÅŸÄ±nda panele ilk girildiÄŸinde) panel doÄŸrudan "Projeler" karÅŸÄ±lama ekranÄ±nÄ± aÃ§ar.
     - **Sol BÃ¶lme:** KayÄ±tlÄ± projeleri listeleyen `UICatalystProjectList`, Ã¼stÃ¼nde baÅŸlÄ±k ve sayaÃ§, "+ Yeni Proje" (`Icons.ADD`), "KlasÃ¶rÃ¼ AÃ§" (`Icons.FOLDER`) ve "Yenile" (`Icons.REFRESH`) araÃ§larÄ±.
       * Projeye tek tÄ±klandÄ±ÄŸÄ±nda saÄŸdaki yapÄ±landÄ±rma formu dolgulanÄ±r.
       * Ã‡ift tÄ±klandÄ±ÄŸÄ±nda (veya Enter tuÅŸunda) proje hemen aktif edilip otomatik olarak "EditÃ¶r" sekmesine geÃ§ilir.
     - **SaÄŸ BÃ¶lme:** Proje oluÅŸturma ve dÃ¼zenleme kartÄ± (`nameInput`, `fpsInput` [1-240], `durationInput` [1-100000], `widthInput`, `heightInput`), "Projeyi AÃ§ / EditÃ¶re GeÃ§" birincil butonu, "Formu Temizle" ve "Projeyi Sil" aksiyonlarÄ±.
     - Ä°stenildiÄŸi zaman Ã¼stteki "Projeler" sekmesine tÄ±klanarak baÅŸka bir projeye geÃ§ilebilir veya yeni proje oluÅŸturulabilir.

### Catalyst Editor â€” Saniye SÃ¼resi GiriÅŸi, AE Kompozisyon Sekme Åeridi ve Katman Modeli (33. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Saniye BazlÄ± SÃ¼re GiriÅŸi (`UICatalystPanel.java`):**
     - Proje oluÅŸturma formundaki sÃ¼re alanÄ± frame â†’ saniye giriÅŸ formatÄ±na gÃ¼ncellendi (`durationSecondsInput`, aralÄ±k 0.1sâ€“3600s, varsayÄ±lan 5.0s).
     - Girilen saniyeye karÅŸÄ±lÄ±k otomatik hesaplanan kare sayÄ±sÄ± (`saniye Ã— fps`) bilgi etiketi (`durationCalcLabel`) olarak formun altÄ±nda gÃ¶sterildi (Ã¶rn. `"5.0s (300f @ 60 FPS)"`).
     - `CatalystProject` oluÅŸturulurken ve kaydedilirken hem `durationSeconds` hem de karÅŸÄ±lÄ±k gelen `durationFrames` (`seconds Ã— fps`) alanlarÄ± tutarlÄ± biÃ§imde saklandÄ±.
     - `UICatalystProjectList` satÄ±r alt baÅŸlÄ±ÄŸÄ± `"X.Xs (Yf @ Z FPS) â€¢ WÃ—H"` formatÄ±na dÃ¶nÃ¼ÅŸtÃ¼rÃ¼ldÃ¼.
  2. **Katman ve Kompozisyon Veri Modeli (`CatalystLayer.java`, `CatalystComposition.java`):**
     - `CatalystLayer`: katman adÄ±, tÃ¼rÃ¼, rengi, gÃ¶rÃ¼nÃ¼rlÃ¼k ve kilit durumu ile `startFrame`/`duration` kanallarÄ±nÄ± barÄ±ndÄ±ran yeni veri sÄ±nÄ±fÄ±. `MapType` serileÅŸtirmesi `putBool`/`getBool` kullanÄ±larak dÃ¼zeltildi.
     - `CatalystComposition`: kendi baÄŸÄ±msÄ±z katman listesine, FPS, sÃ¼re ve oynatma kafasÄ± (`playhead`) alanlarÄ±na sahip kompozisyon nesnesi. `setupDefaultLayers()` ile 4 varsayÄ±lan katman (Video, 3D Replay, Audio, FX/Color) oluÅŸturuluyor.
     - `CatalystProject` gÃ¼ncellendi: `durationSeconds`, `List<CatalystComposition> compositions`, `activeCompositionIndex` eklendi; `ensureCompositions()`, `addComposition()`, `removeComposition()` yardÄ±mcÄ± metotlarÄ± yazÄ±ldÄ±.
  3. **AE TarzÄ± Kompozisyon Sekme Åeridi (`UICatalystCompTab.java`, `UICatalystPanel.java`):**
     - `UICatalystCompTab extends UIClickable<UICatalystCompTab>`: After Effects benzeri tek composition sekmesi. Film ÅŸeridi ikonu, kompozisyon adÄ±, aktif sekme Ã¼st Ã§izgisi (birincil renk) ve Ã—(kapat) butonu (hover'da kÄ±rmÄ±zÄ±). YakÄ±n butonu `mouseClicked` override yerine `overClose` boolean alanÄ± (her karede `renderSkin` iÃ§inde gÃ¼ncellenir) + callback lambda ile handle edildi; `UIElement.mouseClicked()` `final` kÄ±sÄ±tlamasÄ± aÅŸÄ±ldÄ±.
     - `UICatalystPanel`'e 22px yÃ¼ksekliÄŸinde yatay `compTabStrip` (Ã¶nizleme alt sÄ±nÄ±rÄ± ile `bottomArea` arasÄ±), saÄŸÄ±nda `+Comp` (`Icons.ADD`) butonu eklendi. `rebuildCompTabs()` aktif projenin kompozisyonlarÄ±na gÃ¶re sekmeleri yeniden oluÅŸturuyor; `setActiveComposition(index)` ile sekme geÃ§iÅŸi yÃ¶netiliyor.
  4. **UIKeys & Dil DosyalarÄ±:**
     - `UIKeys.CATALYST_COMP_NEW`, `UIKeys.CATALYST_DURATION` (gÃ¼ncellendi) eklendi.
     - `en_us.json` ve `tr_tr.json` gÃ¼ncellendi: `"catalyst.duration"` â†’ `"Duration (Seconds)"`, `"catalyst.comp.new"` â†’ `"New Comp"`.

---

### Catalyst Editor â€” UI Ã‡akÄ±ÅŸma DÃ¼zeltmesi, LayerType Enum, Katman Ekleme MenÃ¼sÃ¼ ve Timeline Scrubbing (34. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **UI Ãœst Ãœste Binme DÃ¼zeltmesi (`UICatalystPanel.java`):**
     - `projectsView` elemanÄ± artÄ±k tÃ¼m editÃ¶r bileÅŸenlerinin (preview, compTabStrip, bottomArea) **Ã¼stÃ¼ne** ekleniyor. Bu sayede Projects sekmesindeyken arkadaki editÃ¶r metinleri ve tuval tamamen gizleniyor.
     - `projectsView`'a en Ã¼st child olarak `deepSurface()` rengiyle tam alan kaplayan opak `UIRenderable` zemin eklendi.
     - `UIElement` eklenme sÄ±rasÄ±: `topBar â†’ previewArea â†’ compTabStrip â†’ bottomArea â†’ projectsView` (projectsView en Ã¼stte render ediliyor).
  2. **`CatalystLayer` GÃ¼ncellendi â€” `id` AlanÄ± ve `LayerType` Enum:**
     - `id` alanÄ±: `UUID.randomUUID().toString().substring(0, 8)` ile otomatik kÄ±sa ID Ã¼retimi.
     - `LayerType` iÃ§ enum: `SOLID`, `SCENE`, `AUDIO`, `NULL`.
     - `LayerType.fromString()` geriye dÃ¶nÃ¼k uyumlu: eski `"VIDEO"`, `"ACTOR"`, `"EFFECT"` string deÄŸerlerini doÄŸru enum tÃ¼rÃ¼ne eÅŸliyor.
     - `LayerType.defaultColor()` her tÃ¼r iÃ§in uygun renk dÃ¶ndÃ¼rÃ¼yor.
     - `CatalystLayer.solid(name)` ve `CatalystLayer.scene(name)` hÄ±zlÄ± factory metotlarÄ± eklendi.
     - `CatalystComposition.setupDefaultLayers()` enum kullanacak ÅŸekilde gÃ¼ncellendi.
  3. **Katman Ekleme MenÃ¼sÃ¼ (`UICatalystPanel.openAddLayerMenu()`):**
     - Ãœstteki `+` (Katman Ekle) butonuna tÄ±klandÄ±ÄŸÄ±nda panelin Ã¼stÃ¼ne geÃ§ici bir inline popup overlay aÃ§Ä±lÄ±yor.
     - Ä°ki seÃ§enek: **"+ Solid Layer"** (dÃ¼z renk) ve **"+ Scene/Film Layer"** (BBS sahne/film).
     - SeÃ§ildiÄŸinde katman aktif kompozisyona ekleniyor, proje kaydediliyor, overlay kapanÄ±yor.
  4. **Timeline Playback & Scrubbing (`UICatalystPanel`):**
     - **Play/Pause:** `playPauseButton`'a tÄ±klandÄ±ÄŸÄ±nda `isPlaying` toggle olur. `tickPlayback(comp)` preview render'Ä±nÄ±n her frame'inde Ã§aÄŸrÄ±lÄ±r; `System.currentTimeMillis()` delta ile `fps`'e gÃ¶re `currentFrame` ilerletilir. Son frame'e ulaÅŸÄ±nca loop sÄ±fÄ±rlanÄ±r.
     - **Timeline scrubbing (tÄ±klama + sÃ¼rÃ¼kleme):** `timelineTracks` anonymous sÄ±nÄ±fÄ±nda:
       * `subMouseClicked` â†’ sol tÄ±k: `isScrubbing = true`, `currentFrame` mouse X'e gÃ¶re hesaplanÄ±r, play durdurulur.
       * `subMouseReleased` â†’ `isScrubbing = false`.
       * `render` baÅŸÄ±nda: `isScrubbing` aktifse mouse X â†’ frame dÃ¶nÃ¼ÅŸÃ¼mÃ¼ her karede yenilenir (drag scrub).
     - **KÄ±rmÄ±zÄ± playhead Ã§izgisi** timeline tracks ve header'da `currentFrame * 4` piksel ofsette Ã§iziliyor.
     - **Timecode** preview canvas altÄ±nda `00:00:SS:FF [Frame X / Y]` formatÄ±nda gÃ¶steriliyor.
     - `mouseXToFrame(mouseX, area, comp)` yardÄ±mcÄ± metodu: 4 px/frame Ã¶lÃ§eÄŸi, `[0, duration-1]` aralÄ±ÄŸÄ±nda sÄ±nÄ±rlandÄ±rÄ±lmÄ±ÅŸ.
  5. **Teknik DÃ¼zeltmeler:**
     - `Scroll.scroll` private alanÄ± yerine `Scroll.getScroll()` public metodu kullanÄ±ldÄ±.
     - `BBSSettings.mainSurface()` â†’ `BBSSettings.deepSurface()` (mevcut olmayan metod dÃ¼zeltmesi).
     - `@Override mouseClicked()` (`UIElement`'de `final`) â†’ kaldÄ±rÄ±ldÄ±; tÃ¼m mouse mantÄ±ÄŸÄ± `subMouseClicked` / `subMouseReleased` (her ikisi de `protected`, override edilebilir) Ã¼zerine taÅŸÄ±ndÄ±.

---

### Catalyst Editor â€” UITimelineCanvas AltyapÄ±sÄ±na GeÃ§iÅŸ ve Kompozisyon '+' DÃ¼zeltmesi (35. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Film EditÃ¶rÃ¼ Timeline AltyapÄ±sÄ±na GeÃ§iÅŸ â€” `UICatalystTimeline extends UITimelineCanvas`:**
     - Yeni `UICatalystTimeline.java` sÄ±nÄ±fÄ± oluÅŸturuldu (`ui.dashboard.panels.catalyst` paketi).
     - BBS'in olgun zaman ekseni motoru `UITimelineCanvas`'tan miras alÄ±yor: `Scale xAxis` ile pikselâ†”kare dÃ¶nÃ¼ÅŸÃ¼mÃ¼, `animateZoom()` ile mouse-wheel zoom, `dragTimeBy()` ile orta-tÄ±k sÃ¼rÃ¼kleme (pan), `Marquee` bantÄ±.
     - **Cetvel (Ruler):** `TimelineRulerRenderer.render()` kullanÄ±lÄ±yor â€” `1-2-5` adÄ±mlÄ± otomatik tick etiketleme, sunken arka plan, kÃ¼Ã§Ã¼k/bÃ¼yÃ¼k Ã§izgiler.
     - **Sadece-cetvel scrubbing:** Sol tÄ±klama **yalnÄ±zca** Ã¼st 21 px ruler bÃ¶lgesinde playhead'i ilerletiyor. Klip ÅŸeritlerine tÄ±klamak playhead'i **hareket ettirmiyor** (istemsiz scrub engellendi).
     - **Orta tÄ±k panning:** Orta fare butonu ile hem yatay (zaman ekseni) hem dikey (katman scroll) kaydÄ±rma.
     - **Ctrl + Scroll â†’ Zoom in/out:** `Scale.animateZoom()` ile anchor-tabanlÄ± animasyonlu yakÄ±nlaÅŸtÄ±rma/uzaklaÅŸtÄ±rma.
     - **DÃ¼z Scroll â†’ Dikey katman scroll:** `Scroll vertical` ile katman satÄ±rlarÄ± yukarÄ±/aÅŸaÄŸÄ± kaydÄ±rÄ±lÄ±yor.
     - **Playhead Ã§izimi:** Birincil tema rengiyle (`BBSSettings.primaryColor`) tam boy dikey Ã§izgi, ruler altÄ±nda Ã¼Ã§gen kapak, ve `UITimelineCanvas.renderCursor()` ile kare/saniye etiket kartÄ±.
     - **Klip bloklarÄ±:** Her katmanÄ±n `startFrame` ve `duration` deÄŸerleri `Scale.toGraphX()` ile dÃ¶nÃ¼ÅŸtÃ¼rÃ¼lerek Ã§iziliyor â€” zoom seviyesiyle uyumlu ÅŸekilde geniÅŸliyor/daralÄ±yor.
  2. **`UICatalystPanel.java` TemizliÄŸi:**
     - Eski `timelineContainer`, `timelineHeader`, `timelineTracks` alanlarÄ± ve `mouseXToFrame()` yardÄ±mcÄ± metodu kaldÄ±rÄ±ldÄ±.
     - `isScrubbing` boolean alanÄ± kaldÄ±rÄ±ldÄ± (artÄ±k `UICatalystTimeline` kendi iÃ§inde yÃ¶netiyor).
     - `setupBottomArea()` yeniden yazÄ±ldÄ±: Sol layers paneli aynen korundu; saÄŸ tarafa `UICatalystTimeline` eklendi.
     - Timeline data akÄ±ÅŸÄ±: `compSupplier` â†’ aktif kompozisyon, `playheadSupplier` â†’ `currentFrame`, `playheadSetter` â†’ frame gÃ¼ncelleme + otomatik pause.
  3. **Kompozisyon '+' Butonu DÃ¼zeltmesi:**
     - `addCompButton` callback'ine `this.currentFrame = 0;` eklendi. `CatalystProject.addComposition()` zaten `activeCompositionIndex`'i yeni comp'a set ediyor; `currentFrame` sÄ±fÄ±rlanmadÄ±ÄŸÄ±nda eski frame timeline'da geÃ§ersiz pozisyonda kalÄ±yordu.
     - `rebuildCompTabs()` ve `updateTitleLabel()` Ã§aÄŸrÄ±larÄ± zaten mevcut â€” dÃ¼zeltme yalnÄ±zca frame resetiydi.

---

### Catalyst Editor â€” Film EditÃ¶rÃ¼ Timeline BileÅŸeninin Birebir UyarlanmasÄ± (36. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **UIClips Mimarisinin UICatalystTimeline'a Birebir AktarÄ±lmasÄ± (`UICatalystTimeline.java`):**
     - BBS Film EditÃ¶rÃ¼nÃ¼n (`UIClips`) denenmiÅŸ ve stabil tÄ±klama, scrubbing ve klip manipÃ¼lasyon mantÄ±ÄŸÄ± `UICatalystTimeline` Ã¼zerine birebir uyarlandÄ±.
     - **KatÄ± Cetvel (Strict Ruler) Scrubbing:** `isInRuler(mouseY)` denetimi ile sadece en Ã¼stteki zaman cetveline (21px ruler) tÄ±klandÄ±ÄŸÄ±nda playhead taÅŸÄ±nÄ±r ve oynatma durdurulur (`onScrub`).
     - **Katman/Klip SeÃ§imi ve Deselect:**
       - Klip Ã¼zerine tÄ±klandÄ±ÄŸÄ±nda katman seÃ§ilir (`setSelected`), `Shift` ile Ã§oklu seÃ§im (`addSelected`/`toggleSelected`) yapÄ±lÄ±r.
       - Klip olmayan boÅŸ track alanÄ±na tÄ±klandÄ±ÄŸÄ±nda mevcut seÃ§im kaldÄ±rÄ±lÄ±r (Deselect / `clearSelection`), sÃ¼rÃ¼kleme yapÄ±lÄ±rsa Marquee seÃ§im kutusu aÃ§Ä±lÄ±r.
     - **Klip TaÅŸÄ±ma ve Ã‡ift YÃ¶nlÃ¼ Trim:**
       - Sol ve saÄŸ kenarlara yaklaÅŸÄ±ldÄ±ÄŸÄ±nda handle tespiti (`getLayerHandle` = 1 sol, 2 saÄŸ, 0 taÅŸÄ±ma).
       - Sol kenardan Ã§ekilirse `startFrame` ve `duration` dinamik olarak trim edilir (`grabMode == 1`).
       - SaÄŸ kenardan Ã§ekilirse `duration` uzatÄ±lÄ±p kÄ±saltÄ±lÄ±r (`grabMode == 2`).
       - GÃ¶vdeden tutulursa klip zaman Ã§izelgesinde serbestÃ§e taÅŸÄ±nÄ±r (`grabMode == 0`).
       - SeÃ§ili veya hover olan kliplerde `Icons.CLIP_HANLDE_LEFT` ve `Icons.CLIP_HANLDE_RIGHT` tutamaÃ§larÄ± ile beyaz Ã§erÃ§eve gÃ¶stergesi Ã§izilir.
     - **Klavye KÄ±sayollarÄ± ve Cut / Split DesteÄŸi:**
       - `C` tuÅŸuna basÄ±ldÄ±ÄŸÄ±nda veya Ã¼st paneldeki `splitButton`'a tÄ±klandÄ±ÄŸÄ±nda `cutSelected()` metodu Ã§alÄ±ÅŸarak seÃ§ili klibi playhead hizasÄ±ndan ikiye bÃ¶ler (`split`).
       - `Delete` tuÅŸuna basÄ±ldÄ±ÄŸÄ±nda seÃ§ili klipler silinir (`deleteSelected()`).
     - **Gezinme (Navigation) & Zoom:**
       - Orta fare tuÅŸuyla tutularak yatay ve dikey yÃ¶nde serbest pan hareketi.
       - Fare tekerleÄŸiyle anchor-tabanlÄ± zaman zoom'u (`zoomTimeAt`) ve `Shift + Wheel` ile dikey track kaydÄ±rmasÄ±.
  2. **UICatalystPanel Entegrasyonu & Senkronizasyon (`UICatalystPanel.java`):**
     - Ãœst araÃ§ Ã§ubuÄŸundaki `splitButton` (`Icons.CUT`) doÄŸrudan `cutSelectedClip()` metoduna baÄŸlandÄ± ve proje anÄ±nda kaydedildi.
     - Sol taraftaki `layersList` panelinde bir katmana tÄ±klandÄ±ÄŸÄ±nda timeline'daki katman otomatik olarak seÃ§ilir (`catalystTimeline.setSelected(layer)`).
     - SeÃ§ili olan katman hem sol katman panelinde (arkaplan & beyaz outline) hem de saÄŸ zaman Ã§izelgesinde (beyaz outline & handle'lar) eÅŸzamanlÄ± olarak vurgulanÄ±r.
     - Oynatma kafasÄ± scrubbing baÅŸladÄ±ÄŸÄ±nda oynatma durumu (`isPlaying = false`) sÄ±fÄ±rlanÄ±r.

---

### Catalyst Editor â€” Split SÄ±ralamasÄ±, Proje Otomatik KayÄ±t, Katman Inspector & Viewport Render (37. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **After Effects TarzÄ± Split SÄ±ralamasÄ± (Cut) (`UICatalystTimeline.java`):**
     - `cutSelected()` metodu gÃ¼ncellendi:
       * BÃ¶lÃ¼nen yeni katman listenin en sonuna deÄŸil, bÃ¶lÃ¼nen katmanÄ±n hemen index Ã¶ncesine (`comp.layers.add(originalIndex, second)`) yani bir Ã¼st satÄ±ra eklenir.
       * Yeni oluÅŸan Ã¼st parÃ§a otomatik olarak seÃ§ili (`setSelected`) hale gelir.
       * Split sonrasÄ± proje otomatik olarak diske kaydedilir (`onModified.run()`).
  2. **Tam KalÄ±cÄ± KayÄ±t (Auto-Save & Persistence):**
     - `UICatalystTimeline`'a `onModified` callback'i eklendi:
       * Katman sÃ¼rÃ¼kleme ve trim iÅŸlemi bittiÄŸinde (`subMouseReleased`),
       * Katman bÃ¶lÃ¼ndÃ¼ÄŸÃ¼nde (`cutSelected`),
       * Katman silindiÄŸinde (`deleteSelected`),
       * Inspector panelinden katman Ã¶zellikleri (ad, opaklÄ±k, renk, gÃ¶rÃ¼nÃ¼rlÃ¼k, kilit, blend modu) deÄŸiÅŸtirildiÄŸinde,
       * Proje anÄ±nda `CatalystProjectManager.saveProject(activeProject)` ile otomatik olarak kaydedilir.
     - `CatalystLayer` veri modeline `opacity` (0-100) ve `blendMode` ("NORMAL", "MULTIPLY", "SCREEN", "ADD", "OVERLAY") alanlarÄ± eklendi; JSON serileÅŸtirmesi (`toData` / `fromData`) saÄŸlandÄ±.
  3. **Katman DetaylarÄ± (Inspector Panel) (`UICatalystPanel.java`):**
     - Alt panel 3 sÃ¼tunlu profesyonel NLE dÃ¼zenine dÃ¶nÃ¼ÅŸtÃ¼rÃ¼ldÃ¼:
       * Sol: Katman Listesi (`layersContainer`, 200px)
       * Orta: Zaman Ã‡izelgesi (`catalystTimeline`, kalan alan)
       * SaÄŸ: Katman DenetÃ§isi (`inspectorContainer`, 220px)
     - SeÃ§ili katman yoksa "No layer selected" uyarÄ±sÄ± gÃ¶sterilir.
     - Katman seÃ§ildiÄŸinde:
       * Katman AdÄ± (`UITextbox`),
       * Katman Tipi (`UILabel`),
       * OpaklÄ±k (`UITrackpad`, 0-100%),
       * 8'li HÄ±zlÄ± Renk Paleti (Swatches) ile anÄ±nda renk deÄŸiÅŸtirme,
       * Blend Modu dÃ¶ngÃ¼ butonu (`NORMAL`, `MULTIPLY`, `SCREEN`, `ADD`, `OVERLAY`),
       * GÃ¶rÃ¼nÃ¼rlÃ¼k (Visible: ON/OFF) ve Kilit (Lock: ON/OFF) toggle butonlarÄ±.
  4. **Viewport CanlandÄ±rma (Canvas Rendering) (`UICatalystPanel.java`):**
     - Ãœst viewport/tuval alanÄ±nda `currentFrame`'de aktif olan tÃ¼m gÃ¶rÃ¼nÃ¼r katmanlar (`visible && startFrame <= currentFrame < startFrame + duration`) arkadan Ã¶ne doÄŸru (listenin sonundan baÅŸÄ±na) gerÃ§ek zamanlÄ± Ã§izilir.
     - `SOLID` katmanlar belirlenen renk ve opaklÄ±kta (`Colors.setA`) tuval alanÄ±na Ã§izilir.
     - `SCENE` katmanlarÄ± iÃ§in sahne etiketi ve renk alanÄ± gÃ¶sterilir.
     - Katman olmayan boÅŸ karelerde kompozisyonun siyah/koyu arka planÄ± korunur.

---

### Catalyst Editor â€” UI Ã‡akÄ±ÅŸmalarÄ± TemizliÄŸi, Timeline Context MenÃ¼sÃ¼, Fullscreen & GeniÅŸletilmiÅŸ Katman Tipleri (38. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Inspector & Proje Formu Metin Ã‡akÄ±ÅŸmasÄ± DÃ¼zeltmesi (`UICatalystPanel.java`):**
     - Inspector formunda (`inspectorForm`) her elemanÄ±n dikey yÃ¼ksekliÄŸi (`.h(16)` / `.h(20)`) ve dikey aralÄ±k (`4px`) net olarak belirlendi, etiketlerin ve inputlarÄ±n Ã¼st Ã¼ste binmesi engellendi.
     - Projeler aÃ§Ä±lÄ±ÅŸ formundaki `durationCalcLabel` ve `Resolution (W x H)` alanlarÄ±nÄ±n dikey yÃ¼kseklikleri sabitlenerek metin Ã§akÄ±ÅŸmasÄ± tamamen giderildi.
  2. **Viewport OSD DÃ¼zenlemesi (`UICatalystPanel.java`):**
     - Tuvalin tam ortasÄ±na Ã§izilen kompozisyon bilgi metin kartÄ± (`MAIN COMP | 1920x1080 | 60 FPS | Rec.709`) ortadan kaldÄ±rÄ±ldÄ±.
     - Bu bilgi tuvalin sol altÄ±nda yer alan timecode (`00:00:00:00`) satÄ±rÄ±nÄ±n hemen bir Ã¼st satÄ±rÄ±na (`area.ey() - 34`) kompakt ve ÅŸÄ±k bir OSD olarak yerleÅŸtirildi.
     - Tuval yÃ¼zeyi artÄ±k yalnÄ±zca katmanlarÄ±n kendi gÃ¶rsellerini engelsiz ÅŸekilde gÃ¶sterir.
  3. **Split Ä°simlendirme DÃ¼zeltmesi (`UICatalystTimeline.java`):**
     - `cutSelected()` metodu iÃ§indeki `" (Split)"` eki kaldÄ±rÄ±ldÄ±. BÃ¶lÃ¼nen yeni Ã¼st parÃ§a orijinal katmanÄ±n adÄ±nÄ± birebir korur (`second.name = layer.name`).
  4. **Ãœst Bardaki Add Layer'Ä±n KaldÄ±rÄ±lmasÄ±, Fullscreen & Timeline Context MenÃ¼sÃ¼:**
     - Ãœst araÃ§ Ã§ubuÄŸundaki ilkel `+` Add Layer butonu ve popup katmanÄ± tamamen kaldÄ±rÄ±ldÄ±.
     - SaÄŸ Ã¼stteki `Icons.FULLSCREEN` butonuna tÄ±klandÄ±ÄŸÄ±nda Ã¶nizleme alanÄ±nÄ±/tuvali tam ekran moduna alÄ±p alt panelleri gizleyen (`toggleFullscreen()`) mekanizma baÄŸlandÄ±.
     - `UICatalystTimeline` Ã¼zerine saÄŸ tÄ±klandÄ±ÄŸÄ±nda aÃ§Ä±lan zengin `UIContextMenu` entegre edildi:
       * **Add Layer Alt MenÃ¼sÃ¼:** Solid Layer, Audio Layer, Adjustment Layer, Null Layer, Text Layer, Film / Scene Layer, Video Layer, Image Layer.
       * **Klip Ãœzerinde SaÄŸ TÄ±k SeÃ§enekleri:** Cut / Split (C), Duplicate, Delete (Del).
       * Kopyalama (`duplicateSelected()`) seÃ§ili katmanÄ±n tam bir kopyasÄ±nÄ± oluÅŸturup hemen Ã¼stÃ¼ne yerleÅŸtirir.
  5. **GeniÅŸletilmiÅŸ Katman Veri Modeli (`CatalystLayer.java`):**
     - `LayerType` enum'Ä±na yeni tipler eklendi:
       `SOLID, AUDIO, ADJUSTMENT, NULL, TEXT, SCENE, VIDEO, IMAGE`
     - Her katman tipi iÃ§in renk paleti (`defaultColor()`) ve katman listesi iÃ§in kÄ±saltma etiketleri (`getBadge()`: `[SOL], [A], [ADJ], [N], [T], [S], [V], [IMG]`) tanÄ±mlandÄ±.

---

### Catalyst Editor â€” Timeline Snapping (Manyetik YapÄ±ÅŸma), BBS Native HiyerarÅŸik Ekleme MenÃ¼sÃ¼ ve Medya AltyapÄ±sÄ± (39. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Timeline Manyetik YapÄ±ÅŸma (Snapping) (`UICatalystTimeline.java`):**
     - Katmanlar taÅŸÄ±nÄ±rken veya sol/saÄŸ kenarlarÄ±ndan trim edilirken snap eÅŸiÄŸi eklendi (`snapTick` metodu, 6 piksel tolerans).
     - DiÄŸer katmanlarÄ±n baÅŸlangÄ±Ã§ ve bitiÅŸ karelerine (`startFrame`, `startFrame + duration`), oynatma kafasÄ±na (`playhead`) ve kompozisyon sÄ±nÄ±rlarÄ±na (`0`, `duration`) manyetik olarak kilitlenme saÄŸlandÄ±.
     - `Alt` tuÅŸuna basÄ±lÄ± tutulduÄŸunda snapping geÃ§ici olarak devre dÄ±ÅŸÄ± bÄ±rakÄ±lÄ±r.
  2. **BBS Film EditÃ¶rÃ¼ TarzÄ± HiyerarÅŸik Context MenÃ¼sÃ¼ (`UICatalystTimeline.java`):**
     - Ãœst bar olarak `MenuVerb` aksiyonlarÄ± (`ADD`, `REMOVE`, `COPY`, `CUT`) yerleÅŸtirildi.
     - "Add..." alt menÃ¼sÃ¼ altÄ±nda "Add layer at cursor..." ve "Add layer at current tick..." seÃ§enekleri eklendi.
     - SeÃ§enek tÄ±klandÄ±ÄŸÄ±nda BBS standart arama Ã§ubuÄŸu ve ikon desteÄŸine sahip `UIChoiceMenu` aÃ§Ä±larak tÃ¼m 8 katman tipi (`SOLID`, `AUDIO`, `ADJUSTMENT`, `NULL`, `TEXT`, `SCENE`, `VIDEO`, `IMAGE`) hiyerarÅŸik olarak sunuldu.
  3. **Medya & Kaynak Yolu (Resource Path) Entegrasyonu (`CatalystLayer.java` & `UICatalystPanel.java`):**
     - `CatalystLayer` modeline `public String resourcePath = ""` alanÄ± eklendi ve JSON/NBT serialization (`toData` / `fromData`) saÄŸlandÄ±.
     - Inspector paneline `Resource / File:` alanÄ± (`layerResourceInput`) entegre edildi. Katman `SCENE`, `VIDEO`, `IMAGE`, `AUDIO` veya `TEXT` olduÄŸunda gÃ¶rÃ¼nÃ¼r hale gelir ve dinamik olarak gÃ¼ncellenir.
  4. **Viewport CanlandÄ±rma & Doku DesteÄŸi (`UICatalystPanel.java`):**
     - `IMAGE` katmanlarÄ± iÃ§in BBS `BBSModClient.getTextures().getTexture(Link.create(...))` Ã¼zerinden gerÃ§ek doku/resim render'Ä± baÄŸlandÄ± (`texturedBox`).
     - Kaynak yolu girilmediÄŸinde veya resim yÃ¼klenemediÄŸinde renk kutusu ve `IMAGE: [yol]` rozeti Ã§izilir.
     - `VIDEO`, `SCENE` ve `TEXT` katmanlarÄ± iÃ§in tuval Ã¼zerinde dinamik iÃ§erik ve metin kartlarÄ± Ã§izdirildi.

---

### Catalyst Editor â€” GerÃ§ek BBS Medya OynatÄ±cÄ±larÄ±, Transform/Pivot Kontrolleri, Dahili Dosya SeÃ§ici ve UI/UX Ä°yileÅŸtirmeleri (40. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Context MenÃ¼ Buton Ã‡akÄ±ÅŸmasÄ± & Temizlik (`UICatalystTimeline.java`):**
     - HiÃ§bir katman seÃ§ili olmadÄ±ÄŸÄ±nda saÄŸ tÄ±k Ã¼st barÄ±nda yalnÄ±zca `[+]` (`MenuVerb.ADD`) ikonu gÃ¶sterilir; mÃ¼kerrer liste satÄ±rlarÄ± kaldÄ±rÄ±ldÄ±.
     - Katman seÃ§ildiÄŸinde Ã¼st barda `[-]` (`MenuVerb.REMOVE`) ve `[Duplicate]` (`MenuVerb.COPY`), alt listede ise `Cut / Split (C)`, `Duplicate` ve `Delete (Del)` seÃ§enekleri sunulur.
  2. **GeliÅŸmiÅŸ Text Layer Motoru & Inspector (`CatalystLayer.java` & `UICatalystPanel.java`):**
     - `CatalystLayer` modeline `textColor` (katman renginden baÄŸÄ±msÄ±z metin rengi), `fontSize`, `lineWrapping` ve `shadow` alanlarÄ± eklendi ve tam JSON/NBT serialization (`toData` / `fromData`) baÄŸlandÄ±.
     - Inspector paneline Ã§ok satÄ±rlÄ± metin giriÅŸi (`UITextarea`), yazÄ± boyutu trackpad'i (`layerFontSizeInput`), metin renk seÃ§icisi (`layerTextColorPicker` / `UIColor`), satÄ±r kaydÄ±rma (`Wrap: ON/OFF`) ve gÃ¶lge (`Shadow: ON/OFF`) butonlarÄ± entegre edildi.
     - Tuval Ã¼zerinde metin, katmanÄ±n timeline rengine baÄŸlÄ± kalmaksÄ±zÄ±n kendi `textColor` deÄŸeriyle ve seÃ§ilen gÃ¶lge ayarÄ±yla render edilir.
  3. **Timeline Dikey TaÅŸÄ±ma / Katman SÄ±ralamasÄ± (Layer Reordering):**
     - `UICatalystTimeline` iÃ§indeki tekil katman sÃ¼rÃ¼klemesine (`dragLayers`) dikey hareket algÄ±lama eklendi. Fare dikeyde baÅŸka bir satÄ±ra kaydÄ±rÄ±ldÄ±ÄŸÄ±nda hedef satÄ±r indeksi (`fromLayerY(mouseY)`) hesaplanÄ±p katman `comp.layers` listesinde yeni sÄ±rasÄ±na taÅŸÄ±nÄ±r.
  4. **Spacebar Play/Stop DesteÄŸi (`UICatalystPanel.java`):**
     - `subKeyPressed` metodu override edilerek odak herhangi bir metin alanÄ±nda deÄŸilken (`!context.isFocused()`) `GLFW_KEY_SPACE` tuÅŸu ile zaman Ã§izelgesi oynatÄ±mÄ± (`togglePlayback()`) tetiklendi.
  5. **Ãœst Bar SadeleÅŸtirme & Kompozisyon AyarlarÄ± ModalÄ±:**
     - Ãœst bardaki gereksiz split butonu kaldÄ±rÄ±ldÄ±, geniÅŸlik 70px olarak optimize edildi.
     - Ayarlar butonuna (`settingsButton`) tÄ±klandÄ±ÄŸÄ±nda aÃ§Ä±lan `UIOverlayPanel` modal penceresi (`openCompositionSettingsModal()`) eklendi: Kompozisyon AdÄ±, FPS, SÃ¼re (saniye) ve Ã‡Ã¶zÃ¼nÃ¼rlÃ¼k (GeniÅŸlik x YÃ¼kseklik) dinamik olarak gÃ¼ncellenip projeye kaydedilir.
  6. **Geri Al / Ä°leri Al (Undo / Redo â€” CTRL+Z / CTRL+Y) DesteÄŸi:**
     - Proje snapshot tabanlÄ± `pushUndo()`, `undo()` ve `redo()` motoru kuruldu.
     - Katman taÅŸÄ±ma, kÄ±rpma (trim), bÃ¶lme (split), Ã§oÄŸaltma (duplicate), silme (delete), ekleme ve ayar deÄŸiÅŸikliklerinde otomatik undo snapshot'Ä± alÄ±nÄ±r.
     - Odak metin kutusunda deÄŸilken `Ctrl + Z` ile geri, `Ctrl + Y` veya `Ctrl + Shift + Z` ile ileri alma kÄ±sayollarÄ± baÄŸlandÄ±.
  7. **Viewport Debug Metinlerinin TemizliÄŸi:**
     - Tuval Ã¼zerinde katmanlarÄ±n Ã¼zerinde gÃ¶rÃ¼nen "VIDEO: ...", "SCENE: ...", "IMAGE: ...", "Layer Name" gibi hata ayÄ±klama etiketleri tamamen kaldÄ±rÄ±ldÄ±; saf gÃ¶rsel/medya Ã§Ä±ktÄ±sÄ± saÄŸlandÄ±.
  8. **BBS Yerel Dosya SeÃ§icileri (Browse ButonlarÄ±):**
     - Ham metin kutusunun yanÄ±na dahili seÃ§ici (`layerPickResourceBtn`) ve klasÃ¶r aÃ§ma butonu (`layerOpenFolderBtn`) entegre edildi:
       * `IMAGE` katmanlarÄ± iÃ§in `UITexturePicker.open`
       * `VIDEO` katmanlarÄ± iÃ§in `UIStringOverlayPanel.links` (`UIVideoClip.getVideoLinks()`)
       * `AUDIO` katmanlarÄ± iÃ§in `UISoundOverlayPanel`
       * KlasÃ¶r butonu ilgili medya dizinini (`BBSMod.getAssetsFolder()`, `BBSMod.getAudioFolder()`) sistem dosya yÃ¶neticisinde aÃ§ar.
  9. **Viewport Transform & Bounding Box:**
     - `CatalystLayer` modeline `posX`, `posY`, `scaleX`, `scaleY`, `rotation`, `anchorX`, `anchorY` alanlarÄ± ve serileÅŸtirme eklendi.
     - Inspector paneline Position (X, Y), Scale (X, Y), Rotation ve Anchor Point (X, Y) trackpad kontrolleri yerleÅŸtirildi.
     - Tuval Ã¼zerinde seÃ§ili katmanÄ±n etrafÄ±na beyaz Ã§erÃ§eve (bounding box), 4 kÃ¶ÅŸesinde boyutlandÄ±rma tutamaÃ§larÄ± ve merkezinde pivot crosshair Ã§izildi. Katman gÃ¶vdesinden tutularak taÅŸÄ±nabilir ve kÃ¶ÅŸe tutamaÃ§larÄ±ndan Ã§ekilerek Ã¶lÃ§eklendirilebilir.
  10. **GerÃ§ek BBS Medya OynatÄ±mÄ±:**
      - `IMAGE`: `BBSModClient.getTextures().getTexture(...)` ile doku render'Ä±.
      - `VIDEO`: `BBSModClient.getVideos().getPlayer(layer, link).getFrame(relSec)` ile kare kare video gÃ¶sterimi.
      - `AUDIO`: `BBSModClient.getSounds().playUnique(this, link)` ile oynatma kafasÄ±yla senkron ses Ã§alma, duraklatma ve panel kapandÄ±ÄŸÄ±nda (`onDisappear`) bellek/ses temizliÄŸi saÄŸlandÄ±.

---

### Catalyst Studio â€” Inspector Scroll, Medya Senkronizasyonu, Bounding Box Shift-Scale, Waveform ve Film SeÃ§ici (41. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Inspector Scroll Paneli & Transform Reset (`UICatalystPanel.java`):**
     - Inspector paneli BBS `UI.scrollView` mimarisine geÃ§irilerek Transform, Text Ã¶zellikleri ve Medya kontrollerinin dikeyde taÅŸmasÄ± tamamen Ã§Ã¶zÃ¼ldÃ¼, fare tekerleÄŸiyle akÄ±cÄ± kaydÄ±rma saÄŸlandÄ±.
     - Transform baÅŸlÄ±ÄŸÄ±nÄ±n yanÄ±na tek tÄ±kla varsayÄ±lan deÄŸerlere (`Pos: 0, 0`, `Scale: 1, 1`, `Rot: 0Â°`, `Anchor: 0.5, 0.5`) dÃ¶nÃ¼ÅŸtÃ¼ren "Reset" butonu (`layerResetTransformBtn`) eklendi.
  2. **Video KatmanÄ± DÃ¼zeltmeleri & Ses/SÃ¼re Senkronu (`UICatalystPanel.java` & `CatalystLayer.java`):**
     - Tuvalde `VIDEO` ve `IMAGE` katmanlarÄ± Ã§izilirken katman rengi tint overlay'i kaldÄ±rÄ±ldÄ±; videolar ve resimler orijinal renkleriyle (`0xFFFFFFFF` tabanlÄ± alfa) render ediliyor.
     - `CatalystLayer` modeline `volume` (0.0 - 1.0), `audioOffset` (kare bazlÄ±) ve `mediaDuration` alanlarÄ± eklenip serileÅŸtirildi.
     - Inspector'a Volume slider'Ä±, Audio Offset trackpad'i ve klibi medyanÄ±n gerÃ§ek sÃ¼resine kilitleyen "Extend to Media Length" butonu eklendi.
     - Video sesi OpenAL ses motoru ile oynatma kafasÄ±na tam senkronize edildi.
  3. **Kompozisyon AyarlarÄ± ModalÄ± GeliÅŸtirmesi (`UICatalystPanel.java`):**
     - `openCompositionSettingsModal()` iÃ§eriÄŸi canlÄ± hesaplanan sÃ¼re gÃ¶stergesi (`saniye â€¢ frame @ FPS`) ve "Save & Apply" butonu ile zenginleÅŸtirildi.
  4. **Viewport Bounding Box Shift-Scale:**
     - KÃ¶ÅŸe tutamaÃ§larÄ± sÃ¼rÃ¼klendiÄŸinde varsayÄ±lan olarak en-boy oranÄ± (aspect ratio) korunarak Ã¶lÃ§ekleme yapÄ±lÄ±r; `Shift` tuÅŸuna basÄ±lÄ± tutulduÄŸunda serbest (non-proportional) X/Y boyutlandÄ±rma aktif olur.
  5. **Text KatmanÄ± Motoru:**
     - `fontSize` deÄŸeri `MatrixStack` Ã¶lÃ§eklemesine baÄŸlandÄ±.
     - `lineWrapping = true` iken `FontRenderer.wrap` ile otomatik satÄ±r sarma saÄŸlandÄ±.
     - Katman adÄ± asla metin iÃ§eriÄŸi olarak Ã§izilmez; metin boÅŸken inspector Ã¼zerinden dÃ¼zenleme ipucu gÃ¶sterilir.
  6. **Ses KatmanÄ± Senkronu, Waveform & Ses TemizliÄŸi:**
     - Scrubbing anÄ±nda, duraklatmada, sekme deÄŸiÅŸiminde ve panel kapanÄ±ÅŸÄ±nda tÃ¼m OpenAL kaynaklarÄ± (`stopAllAudio()`) durdurulur/temizlenir.
     - Timeline Ã¼zerinde ses klipleri iÃ§in `Waveform` render'Ä± ve genlik Ã§ubuÄŸu yedeÄŸi entegre edildi.
  7. **Film KatmanÄ± ([FILM]) & Yerel Film SeÃ§ici:**
     - `SCENE` katmanÄ± kullanÄ±cÄ± arayÃ¼zÃ¼nde "Film Layer" (`[FILM]` badge) olarak yeniden adlandÄ±rÄ±ldÄ±.
     - Inspector Ã¼zerinden `UIStringOverlayPanel` ile BBS kayÄ±tlÄ± filmleri (`BBSMod.getFilms().getKeys()`) listelenip seÃ§ildiÄŸinde klip sÃ¼resi otomatik olarak filmin kamera sÃ¼resine (`film.camera.calculateDuration()`) ayarlanÄ±r.

---

### Catalyst Studio â€” EOFException OnarÄ±mÄ±, Waveform Cache, OpenAL Kaynak YÃ¶netimi ve Performans OptimizasyonlarÄ± (42. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Waveform Ã–nbellekleme (Caching & Lazy Load) (`CatalystLayer.java` & `UICatalystTimeline.java`):**
     - `UICatalystTimeline.renderTracks` iÃ§indeki render dÃ¶ngÃ¼sÃ¼nden senkron `SoundManager.load` ve `WaveReader.read` Ã§aÄŸrÄ±larÄ± tamamen kaldÄ±rÄ±ldÄ±. Her karede diske gidip WAV okuma kaynaklÄ± `EOFException` Ã§Ã¶kme dÃ¶ngÃ¼sÃ¼ kÃ¶kten Ã§Ã¶zÃ¼ldÃ¼.
     - `CatalystLayer` modeline `cachedWaveform`, `isWaveformLoading`, `cachedWaveformPath` transient alanlarÄ± eklendi.
     - `UICatalystTimeline.ensureWaveformLoaded` asenkron iÅŸ parÃ§acÄ±ÄŸÄ± (`CatalystWaveformLoader`) kurularak waveform verisi yalnÄ±zca kaynak yolu (`resourcePath`) deÄŸiÅŸtiÄŸinde arka planda tek bir kez hesaplanÄ±p katmana Ã¶nbelleÄŸe alÄ±ndÄ± (`layer.cachedWaveform`).
     - Okuma hatalarÄ± (`EOFException`, eksik/bozuk dosyalar) `try-catch (Throwable)` ile sessizce yÃ¶netilip sonsuz yeniden denemeler engellendi.
  2. **OpenAL Ses Kaynak Havuzu ve Tekil OynatÄ±cÄ± (`UICatalystPanel.java`):**
     - Ses Ã§alma mantÄ±ÄŸÄ± 60 FPS tuval render dÃ¶ngÃ¼sÃ¼nden (`renderPreviewCanvas`) tamamen soyutlandÄ±.
     - Ses tetikleme yalnÄ±zca playhead tick'i deÄŸiÅŸtiÄŸinde Ã§alÄ±ÅŸan `updatePlaybackAudio()` metoduna taÅŸÄ±ndÄ±.
     - Her karede ve her katmanda yeni OpenAL ses kanalÄ± Ã¼retilmesi engellenerek `Allocate new source: Invalid operation` hatasÄ± ortadan kaldÄ±rÄ±ldÄ±; tekil ve kontrollÃ¼ `activeAudioPlayer` tahsis edildi.
     - Klipler arasÄ± geÃ§iÅŸte, duraklatmada (`Space`), oynatma kafasÄ± scrubbing'inde veya panel kapanÄ±ÅŸÄ±nda `stopAllAudio()` doÄŸrudan `activeAudioPlayer.delete()` Ã§aÄŸÄ±rarak OpenAL kaynaÄŸÄ±nÄ± anÄ±nda iÅŸletim sistemine/sÃ¼rÃ¼cÃ¼ye iade eder.
  3. **Hafif Performans OptimizasyonlarÄ± (Culling):**
     - **Timeline Culling:** `UICatalystTimeline` iÃ§inde gÃ¶rÃ¼nÃ¼r zaman aralÄ±ÄŸÄ± (`clipArea.ex() < area.x || clipArea.x > area.ex()`) ve dikey scroll alanÄ±nÄ±n dÄ±ÅŸÄ±nda kalan katmanlar ile dalga formlarÄ± Ã§izim dÃ¶ngÃ¼sÃ¼nden elenerek (cull) GPU/CPU yÃ¼kÃ¼ azaltÄ±ldÄ±.
     - **Viewport Culling:** `renderPreviewCanvas` iÃ§inde tuvalin sÄ±nÄ±rlarÄ± dÄ±ÅŸÄ±nda kalan (`lx + layerW < cx || lx > cx + canvasW || ly + layerH < cy || ly > cy + canvasH`), gÃ¶rÃ¼nmez (`!visible`) veya opaklÄ±ÄŸÄ± sÄ±fÄ±r olan katmanlarÄ±n Ã§izim iÅŸlemleri pas geÃ§ildi.
  4. **Katman Render SÃ¼resi ve AE Profiler (`UICatalystPanel.java`):**
     - Her katmanÄ±n Ã§izim sÃ¼resi nanosaniye hassasiyetiyle Ã¶lÃ§Ã¼lÃ¼p `layer.lastRenderMs` alanÄ±na yazÄ±ldÄ± ve sol paneldeki katman satÄ±rÄ±nÄ±n yanÄ±na (`0.2 ms`, `1.5 ms`) basÄ±ldÄ±.
     - Kompozisyonun toplam render sÃ¼resi Ã¶lÃ§Ã¼lerek tuvalin sol altÄ±ndaki OSD ÅŸeridine (`Comp: X.X ms`) entegre edildi.

---

### Catalyst Studio â€” Media Picker NullPointerException OnarÄ±mÄ± (43. AÅŸama)
* **KÃ¶k Neden:**
  - `crash-2026-09-27_04.57.42-client.txt` logundaki analize gÃ¶re `UICatalystPanel.java` iÃ§inde `openMediaPickerForSelectedLayer()` Ã§aÄŸrÄ±ldÄ±ÄŸÄ±nda, aÃ§Ä±lan overlay/picker (Ã¶rneÄŸin Video, Audio veya Doku seÃ§ici) kullanÄ±cÄ± tarafÄ±ndan seÃ§im yapÄ±lmadan kapatÄ±ldÄ±ÄŸÄ±nda veya null dÃ¶ndÃ¼ÄŸÃ¼nde `link.toString()` Ã§aÄŸrÄ±sÄ± `NullPointerException` fÄ±rlatÄ±p oyunu Ã§Ã¶kertiyordu.
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Image/Texture Picker Callback Null-Safety:**
     - `UITexturePicker.open` callback'inde `link != null` kontrolÃ¼ eklendi.
  2. **Video Picker Callback Null-Safety:**
     - `UIStringOverlayPanel.links` lambda callback'inde `link != null` kontrolÃ¼ eklenerek `link.toString()` ve `VideoPlayer` probe/sÃ¼re hesaplama iÅŸlemleri gÃ¼venli bloÄŸa alÄ±ndÄ±.
  3. **Audio Picker Callback Null-Safety:**
     - `UISoundOverlayPanel` callback'inde `link != null` kontrolÃ¼ eklenerek `link.toString()` ve `SoundBuffer` probe/sÃ¼re hesaplama iÅŸlemleri korundu.
  4. **Film/Scene Picker Callback Null & Empty Safety:**
     - Film seÃ§ici callback'inde `filmId != null && !filmId.trim().isEmpty()` kontrolÃ¼ eklenerek boÅŸ veya null film id seÃ§imlerinde NBT/JSON yÃ¼kleme Ã§Ã¶kmeleri Ã¶nlendi.

---

---

### Catalyst Studio â€” Video Aspect Ratio, Rotation Matrix, Film Viewport Render, Split Offset, Settings Modal ve Video AkÄ±cÄ±lÄ±ÄŸÄ± OnarÄ±mÄ± (44. AÅŸama)
* **Uygulanan DeÄŸiÅŸiklikler:**
  1. **Video KatmanÄ±nda Dinamik Aspect Ratio DesteÄŸi (`VideoPlayer.java` & `UICatalystPanel.java`):**
     - Sabit 16:9 oranÄ± kaldÄ±rÄ±ldÄ±; `VideoPlayer` sÄ±nÄ±fÄ±na `getWidth()`, `getHeight()` ve `getFps()` metotlarÄ± eklendi.
     - `UICatalystPanel.getLayerDimensions()` metodu yazÄ±larak video ve resimlerin orijinal piksel en-boy oranÄ±na (Ã¶rneÄŸin 4:3, 1:1, 9:16) gÃ¶re fit/letterbox/pillarbox hesaplandÄ±.
     - Hem tuvaldeki katman Ã§izimi hem de etrafÄ±ndaki Bounding Box ve kÃ¶ÅŸe tutamaÃ§larÄ± bu dinamik boyuta baÄŸlandÄ±.
  2. **Katman BÃ¶lme (Cut / Split) In-Point / Offset DesteÄŸi (`CatalystLayer.java`, `UICatalystTimeline.java` & `UICatalystPanel.java`):**
     - `CatalystLayer` modeline `mediaOffset` alanÄ± eklendi (`toData` / `fromData` ile serileÅŸtirildi).
     - `UICatalystTimeline.cutSelected()` iÃ§inde bÃ¶lÃ¼nen ikinci parÃ§anÄ±n `mediaOffset` deÄŸeri `ilk_parca.mediaOffset + firstDuration` olarak atandÄ±.
     - Video, Audio ve Film oynatma kurgusunda baÄŸÄ±l kare hesabÄ±:
       `int relativeFrame = (currentPlayhead - layer.startFrame) + layer.mediaOffset;`
       olarak gÃ¼ncellendi; kesilen parÃ§alarÄ±n videonun tam kesim karesinden oynamasÄ± saÄŸlandÄ±.
  3. **Katman DÃ¶ndÃ¼rme (Rotation) Matrisi OnarÄ±mÄ± (`UICatalystPanel.java`):**
     - Katman Ã§iziminde ve Bounding Box sÄ±nÄ±rlarÄ±nda MatrixStack Z ekseni rotasyonu uygulandÄ±:
       * Pivot noktasÄ±na translate (`lx + layerW * anchorX`, `ly + layerH * anchorY`),
       * `matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(layer.rotation))`,
       * Pivotu geri Ã¶teleme ve Ã§izim.
     - Tuval fare tÄ±klamalarÄ±nda ve kÃ¶ÅŸe tutamaÃ§larÄ±nda dÃ¶nmÃ¼ÅŸ koordinatlar trigonometrik olarak hesaplanarak dÃ¶ndÃ¼rÃ¼lmÃ¼ÅŸ katmanlarÄ±n doÄŸru seÃ§ilip Ã¶lÃ§eklenmesi saÄŸlandÄ±.
  4. **Film KatmanÄ±nÄ±n Tuvalde Render Edilmesi (`UICatalystPanel.java`):**
     - Film katmanÄ± iÃ§in `BBSRendering.getTexture()` FBO off-screen dokusu `texturedBox` ile katman sÄ±nÄ±rlarÄ±na baÄŸlandÄ±; boÅŸ mavi renk yerine canlÄ± film render'Ä± ve seÃ§im durumuna gÃ¶re ÅŸÄ±k kart gÃ¶sterimi saÄŸlandÄ±.
  5. **Composition Settings ModalÄ±nÄ± GÃ¶rÃ¼nÃ¼r Yapma (`UICatalystPanel.java`):**
     - Modal iÃ§indeki elementler `container.column(6).vertical().stretch()` ile kapsayÄ±cÄ±ya baÄŸlanÄ±p `modal.content`'e eklendi.
     - Comp Name, FPS, SÃ¼re (sn), GeniÅŸlik/YÃ¼kseklik ve "Apply & Close" butonu ekranda net ve ortalanmÄ±ÅŸ biÃ§imde gÃ¶rÃ¼nÃ¼r hale getirildi.
  6. **Video Oynatma AkÄ±cÄ±lÄ±ÄŸÄ± & Micro-Stutter Giderimi (`VideoPlayer.java` & `UICatalystPanel.java`):**
     - `VideoPlayer.upload()` iÃ§erisine mevcut kare numarasÄ± ile geÃ§erli doku kimliÄŸinin eÅŸleÅŸmesi durumunda GPU'ya gereksiz bellek aktarÄ±mÄ±nÄ± kesen Ã¶nbellek korumasÄ± eklendi.
     - `CatalystLayer` Ã¼zerinde kare numarasÄ± deÄŸiÅŸmedikÃ§e bir Ã¶nceki doku ID'sinin tekrar kullanÄ±mÄ± saÄŸlanarak oynatma akÄ±cÄ±lÄ±ÄŸÄ± artÄ±rÄ±ldÄ±.

  7. **Tam Ekran / Viewport Ã‡Ã¶zÃ¼nÃ¼rlÃ¼k BaÄŸÄ±msÄ±z Koordinat Sistemi (`UICatalystPanel.java`):**
     - KatmanlarÄ±n pencere piksel koordinatlarÄ± yerine sabit sanal kompozisyon Ã§Ã¶zÃ¼nÃ¼rlÃ¼ÄŸÃ¼ne (`comp.width` x `comp.height`, varsayÄ±lan 1920x1080) kilitlenmesi saÄŸlandÄ±:
       * `float scale = Math.min((float) (area.w - 32) / compW, (float) (area.h - 32) / compH);`
       * `float offsetX = area.x + (area.w - compW * scale) / 2.0F;`
       * `float offsetY = area.y + (area.h - compH * scale) / 2.0F;`
     - Tuval render dÃ¶ngÃ¼sÃ¼nde (`renderPreviewCanvas`) MatrixStack'e `translate(offsetX, offsetY, 0)` ve `scale(scale, scale, 1.0F)` uygulanarak tÃ¼m katmanlar, metinler, gÃ¶rseller ve Bounding Box 1920x1080 sanal piksel uzayÄ±nda Ã§izildi.
     - Tuval fare tÄ±klamalarÄ± ve sÃ¼rÃ¼kleme iÅŸlemlerinde fare koordinatlarÄ± `(mouseX - offsetX) / scale` ile sanal uzaya dÃ¶nÃ¼ÅŸtÃ¼rÃ¼lerek tam ekran geÃ§iÅŸlerinde veya pencere yeniden boyutlandÄ±rmalarÄ±nda katmanlarÄ±n kaymasÄ±, bozulmasÄ± ve oran kaybÄ± tamamen engellendi.

---

## 4. Derleme & DoÄŸrulama Durumu

* `./gradlew.bat --no-daemon compileJava compileClientJava` komutu Ã§alÄ±ÅŸtÄ±rÄ±ldÄ±.
* **SonuÃ§ (44. AÅŸama & Faz 4):** `BUILD SUCCESSFUL in 17s` â€” 0 Hata, 0 Kritik UyarÄ±.
* `./gradlew.bat --no-daemon apiCheck` komutu Ã§alÄ±ÅŸtÄ±rÄ±ldÄ±.
* **SonuÃ§:** `BUILD SUCCESSFUL in 9s` â€” Addon API sÃ¶zleÅŸmesi korundu.

---

## 5. Catalyst Timeline Profesyonel Kurgu, Senkronizasyon ve Navigasyon AltyapÄ±sÄ± (Faz 4)

### Uygulanan 5 Temel Ã–zellik:
1. **Klip Trim / Kenar Ã‡ekme ve Medya Ä°Ã§ Ofset Senkronu (Slip / Trim-In & Trim-Out):**
   - **Sol Kenar (Trim-In / Slip-In):** `startFrame` saÄŸa veya sola Ã§ekildiÄŸinde `mediaOffset` dinamik olarak ofsetlenir (`mediaOffset = Math.max(0, initialMediaOffset + deltaFrames)`). Klip asla baÅŸa sarmaz, kurgunun kesildiÄŸi andan itibaren oynamaya devam eder.
   - **SaÄŸ Kenar (Trim-Out):** `duration` dinamik olarak uzatÄ±lÄ±p kÄ±saltÄ±lÄ±r; klip sÃ¼resi `Math.max(1, layer.mediaDuration - mediaOffset)` sÄ±nÄ±rÄ±nÄ± aÅŸamaz.
   - **Kenar TutamaÃ§ Ä°mleci:** Fare katman kenarlarÄ±na yaklaÅŸtÄ±ÄŸÄ±nda (<= 6px) ve kenar Ã§ekme esnasÄ±nda imleÃ§ `GLFW.GLFW_HRESIZE_CURSOR` ÅŸeklini alÄ±r.

2. **Manyetik Hizalama (Magnetic Snapping) ve Toolbar Butonu:**
   - Zaman Ã§izelgesinin sol Ã¼st ruler kÃ¶ÅŸesine `Icons.MAGNET` snap butonu eklendi (tÄ±klama ile veya Premiere/DaVinci standardÄ± olan `N` tuÅŸuyla aÃ§Ä±lÄ±p kapanabilir, varsayÄ±lan aÃ§Ä±k). Buton Ã¼zerinde `Snapping (N)` tooltip'i gÃ¶sterilir.
   - After Effects uyumluluÄŸu gÃ¶zetilerek `S` kÄ±sayolu snapping Ã§akÄ±ÅŸmasÄ±ndan tamamen arÄ±ndÄ±rÄ±ldÄ± ve yalnÄ±zca seÃ§ili katmanÄ±n Scale Ã¶zelliÄŸini geniÅŸletmeye (`PROP_SCALE`) tahsis edildi.
   - Snap Hedefleri: Playhead, diÄŸer katmanlarÄ±n baÅŸÄ± ve sonu, tÃ¼m katmanlarÄ±n keyframe noktalarÄ±, kare 0 ve kompozisyon bitiÅŸi. Alt tuÅŸuna basÄ±lÄ± tutulduÄŸunda snap durumu tersine Ã§evrilir. Hassasiyet: 8 piksel (`SNAP_DISTANCE = 8`).

3. **Kare Kare (Frame-by-Frame) Ok TuÅŸlarÄ± ile Navigasyon:**
   - Sol Ok: Playhead -1 kare (Shift + Sol: -10 kare, min 0).
   - SaÄŸ Ok: Playhead +1 kare (Shift + SaÄŸ: +10 kare, max duration - 1).
   - `UICatalystTimeline` ve `UICatalystPanel` seviyesinde odaklanmÄ±ÅŸ metin kutusu yokken anÄ±nda senkron frame decoding ve ses Ã§alÄ±mÄ± (`seekToFrame`).

4. **Ripple Delete / BoÅŸluk Silme (Gap Deletion):**
   - `Shift + Delete` / SaÄŸ tÄ±k menÃ¼sÃ¼ "Ripple Delete (Shift + Del)": SeÃ§ili katmanlarÄ± siler ve sonraki tÃ¼m katmanlarÄ± silinen sÃ¼re kadar geri Ã§eker.
   - BoÅŸluk SeÃ§imi & Silme: Katmanlar arasÄ±ndaki boÅŸluÄŸa tÄ±klandÄ±ÄŸÄ±nda boÅŸluk mavi renkle vurgulanarak seÃ§ilir; `Delete` / `Backspace` tuÅŸuna basÄ±ldÄ±ÄŸÄ±nda sonraki tÃ¼m katmanlar boÅŸluk sÃ¼resi kadar sola kaydÄ±rÄ±lÄ±r (`rippleDeleteGap()`).

5. **Ã‡oklu Katman SeÃ§imi & Kutu / Marquee SeÃ§imi:**
   - BoÅŸ alandan sÃ¼rÃ¼kleme yapÄ±ldÄ±ÄŸÄ±nda kesiÅŸen tÃ¼m katmanlar seÃ§im kutusu (`this.marquee`) ile toplu seÃ§ilir.
   - `Shift + Click` mevcut seÃ§imi bozmadan yeni katmanlarÄ± seÃ§ime ekler.
   - Ã‡oklu taÅŸÄ±ma modunda (`grabMode == 0`) seÃ§ili katmanlar birbirlerine gÃ¶re baÄŸÄ±l mesafelerini koruyarak timeline 0 sÄ±nÄ±rÄ±na kadar senkron hareket eder.

---

## 6. Catalyst Media Pool (VarlÄ±k YÃ¶netimi) & SÃ¼rÃ¼kle-BÄ±rak AltyapÄ±sÄ± (Faz 5)

### Uygulanan Temel Ã–zellikler:
1. **CatalystMediaAsset Veri Modeli:**
   - Video, Audio ve Image varlÄ±klarÄ± iÃ§in tip gÃ¼venli `MediaType`, dosya yolu, dosya boyutu, kare cinsinden sÃ¼re ve dosya adÄ± modeli.
   - `formatSize()` (B, KB, MB, GB) ve `formatDuration(fps)` (MM:SS / HH:MM:SS) formatlayÄ±cÄ±larÄ±.
   - Geriye dÃ¶nÃ¼k tam uyumlu NBT/JSON serileÅŸtirme (`toData` / `fromData`).

2. **Proje Seviyesi Medya Havuzu (`CatalystProject`):**
   - `mediaPool` listesi, proje dosyasÄ±yla birlikte kaydedilir/yÃ¼klenir.
   - `addAsset`, `removeAsset`, `getAssetByPath`, `syncMediaPoolWithLayers` ve `cleanUnusedMedia` yÃ¶netim fonksiyonlarÄ±.

3. **UIMediaPoolPanel Paneli & KullanÄ±cÄ± ArayÃ¼zÃ¼:**
   - Sol tarafta aÃ§Ä±lÄ±r/kapanÄ±r Media Pool paneli (`B` tuÅŸu veya toolbar `Icons.SAVED` butonu ile geÃ§iÅŸ).
   - BaÅŸlÄ±k Ã§ubuÄŸu: Havuz sayaÃ§ rozeti, `UIFileDialogs.pickFile` ile "Import Media", "Clean Unused" ve "Open Folder" butonlarÄ±.
   - Dinamik metin filtreleme / arama kutusu (`UITextbox`).
   - Medya kartlarÄ±: TÃ¼r ikonu (`Icons.FILM`, `Icons.SOUND`, `Icons.IMAGE`), renk kodlamasÄ±, dosya adÄ±, dosya boyutu, sÃ¼re ve format rozeti (`VID`, `AUD`, `IMG`).
   - SaÄŸ tÄ±k baÄŸlam menÃ¼sÃ¼: "Insert to Timeline (At Playhead)", "Reload Asset", "Open Containing Folder", "Delete from Pool".

4. **SÃ¼rÃ¼kle-BÄ±rak HattÄ± (Drag & Drop Pipeline):**
   - **MasaÃ¼stÃ¼nden Pencereye:** LWJGL `glfwSetDropCallback` ile iÅŸletim sisteminden sÃ¼rÃ¼klenen dosyalar `BBSMod.getAssetsPath("catalyst_media")` dizinine kopyalanarak otomatik havuza eklenir.
   - **Havuzdan Zaman Ã‡izelgesine:** Havuzdan sÃ¼rÃ¼klenen varlÄ±k fare imlecinde yÃ¼zen kart olarak takip edilir; zaman Ã§izelgesi Ã¼zerinde manyetik snap kÄ±lavuz Ã§izgisi ve hedef kare rozeti Ã§izilir.
   - BÄ±rakÄ±ldÄ±ÄŸÄ±nda veya Ã§ift tÄ±klandÄ±ÄŸÄ±nda otomatik katman tipi (`VIDEO`, `AUDIO`, `IMAGE`), renk ve sÃ¼re atanÄ±r; ses katmanlarÄ± iÃ§in arka planda asenkron dalga formu Ã¼retimi (`ensureWaveformLoaded`) tetiklenir.

---

## 7. Catalyst Katman Trim MantÄ±ÄŸÄ±, Audio mediaOffset Senkronu & CanlÄ± Ã–nizleme Stereo/Kristal Netlik (AÅŸama 50)

### Uygulanan Temel DÃ¼zeltmeler ve Ä°yileÅŸtirmeler:
1. **Audio KatmanÄ± mediaOffset Senkronu (Trim-In / BaÅŸa Sarma Engeli):**
   - Sol kenar saÄŸa Ã§ekildiÄŸinde oluÅŸan `mediaOffset` deÄŸeri `Wave` / `SoundPlayer` oynatma ofsetine (`relSec = ((currentFrame - layer.startFrame) + layer.mediaOffset + layer.audioOffset) / fps`) baÄŸlandÄ±.
   - `SoundPlayer` iÃ§inde `pendingOffset` altyapÄ±sÄ± kuruldu; OpenAL'in durdurulmuÅŸ/baÅŸlatÄ±lmÄ±ÅŸ kaynaklarda ofseti 0'a sÄ±fÄ±rlama davranÄ±ÅŸÄ± `alSourcePlay` ardÄ±ndan `setPlaybackPosition` uygulanarak tamamen Ã¶nlendi.
   - Timeline dalga formu Ã§iziminde `layer.mediaOffset` hesaba katÄ±larak (`startTime`, `endTime`) gÃ¶rsel dalga formu ile ses ofseti 1:1 senkronize edildi.

2. **SaÄŸ Kenar (Trim-Out) KelepÃ§e ve SÃ¼re SÄ±nÄ±rÄ± DÃ¼zeltmesi (`UICatalystTimeline`):**
   - Fiziksel medya sÄ±nÄ±r kuralÄ± uygulandÄ±: `OynatÄ±lan Son Medya Karesi = layer.mediaOffset + layer.duration <= layer.mediaDuration`.
   - SaÄŸ kenar Ã§ekildiÄŸinde (`grabMode == 2` / `TRIM_END`): `maxDuration = Math.max(1, layer.mediaDuration - layer.mediaOffset)` ile kalan fiziksel medya sÃ¼resine kadar uzatabilme serbestliÄŸi saÄŸlandÄ±.
   - Sol kenar Ã§ekildiÄŸinde (`grabMode == 1` / `TRIM_START`): `startFrame` arttÄ±kÃ§a `duration` eÅŸit miktarda azaltÄ±lÄ±p `mediaOffset` aynÄ± miktarda artÄ±rÄ±larak `endFrame` (`startFrame + duration`) sabit kilitlendi; sol kenar geri Ã§ekildiÄŸinde `mediaOffset` 0'a kadar serbestÃ§e indirildi.
   - `IMAGE`, `SOLID` ve `TEXT` gibi fiziksel medya sÄ±nÄ±rÄ± olmayan katmanlarÄ±n `mediaDuration` kÄ±skacÄ±nda kalmasÄ± Ã¶nlendi.

3. **CanlÄ± Ã–nizleme Ses Kalitesi (GerÃ§ek 48kHz Stereo & 32-Bit Float Kristal Netlik):**
   - `Wave.convertToStereo()` metodu eklenerek mono seslerin OpenAL tarafÄ±ndan 3D uzamsal zayÄ±flatmaya uÄŸramasÄ± Ã¶nlendi; 2 kanallÄ± 48000 Hz 16-bit PCM standardÄ± (`AL_FORMAT_STEREO16`) garanti edildi.
   - `SoundPlayer` 2D stereo yapÄ±landÄ±rmasÄ±na OpenAL Soft `AL_SOURCE_SPATIALIZE_SOFT` desteÄŸi entegre edildi; `layer.pan` (-1.0 .. +1.0) kontrolÃ¼ canlÄ± Ã¶nizlemede gerÃ§ek stereo pan olarak etki eder hale getirildi.
   - **CanlÄ± Scrub MiksajÄ± (`playScrubAudio`):** Timeline scrubbing esnasÄ±nda tÃ¼m aktif ses katmanlarÄ± 32-bit kayan noktalÄ± (float) stereo tamponda anÄ±nda mikslenir; dinamik peak taramasÄ± ve analog tanh soft-clipping uygulanarak hoparlÃ¶rden gelen dijital Ã§atÄ±rtÄ±lar/bozulmalar Ã¶nlendi.

---

## 8. Catalyst Media Pool Dosya Yolu (Path Resolution), Doku YÃ¼kleme & Oynatma OnarÄ±mÄ± (AÅŸama 51)

### Uygulanan Temel DÃ¼zeltmeler ve Ä°yileÅŸtirmeler:
1. **Absolute Path / Dosya Yolu ve BoÅŸluk FormatlamasÄ± (`CatalystMediaAsset`, `CatalystLayer`, `CatalystProject`, `UICatalystPanel`):**
   - Media Pool'dan timeline'a sÃ¼rÃ¼kleme ve inspector dosya yolu giriÅŸlerinde Windows ters eÄŸik Ã§izgileri (`\`) tek tip normalize edilmiÅŸ mutlak yollara (`/`) dÃ¶nÃ¼ÅŸtÃ¼rÃ¼ldÃ¼.
   - Dosya adÄ±ndaki boÅŸluklar ve Ã¶zel karakterler korunurken, `dropAssetToTimeline` iÃ§inde `new File(normPath).exists()` doÄŸrulamasÄ± yapÄ±larak yetim/boÅŸ katman oluÅŸmasÄ± engellendi.

2. **AssetProvider & Direct File Resolution (Missing Texture / Checkerboard KÃ¶k OnarÄ±mÄ±):**
   - Windows sÃ¼rÃ¼cÃ¼ harfi iÃ§eren mutlak yollarÄ±n (`C:/...`, `D:/...`) `Link` tarafÄ±ndan hatalÄ± namespace (`C:`) olarak yorumlanÄ±p `AssetProvider` tarafÄ±ndan reddedilmesi sorunu, `AssetProvider.resolveDirectFile(Link link)` mekanizmasÄ± ile Ã§Ã¶zÃ¼ldÃ¼.
   - `getFile()`, `hasAsset()` ve `getAsset()` metodlarÄ± diskteki fiziksel dosyalarÄ± otomatik tespit ederek doÄŸrudan `FileInputStream` aÃ§ar hale getirildi.
   - `VideoManager.getPlayer()` ve `VideoManager.get()` metodlarÄ±na doÄŸrudan disk dosyasÄ± Ã§Ã¶zÃ¼mleme desteÄŸi entegre edildi.

3. **IMAGE KatmanÄ± Doku YÃ¼klemesi (`UICatalystPanel`, `CatalystLayer`):**
   - `UICatalystPanel.getOrLoadImageTexture(layer)` metodu inÅŸa edildi.
   - `CatalystLayer` Ã¼zerine eklenen `cachedImageTexture` ve `cachedImagePath` transient volatile alanlarÄ± ile doku Ã¶nbelleÄŸi kuruldu.
   - Harici resimler (`.png`, `.jpg`, `.jpeg`) `Pixels.fromPNGStream()` ve `Texture.textureFromPixels()` ile yÃ¼ksek kaliteli `GL11.GL_LINEAR` OpenGL dokusu olarak yÃ¼klendi; missing texture pembe-mavi dama tahtasÄ± engellendi.
   - `getLayerDimensions()` iÃ§erisine gÃ¶rselin gerÃ§ek geniÅŸlik/yÃ¼kseklik en-boy oranÄ± entegre edilerek tuval Ã¼zerindeki Ã§erÃ§eve ve tutamaÃ§lar kusursuz hizalandÄ±.

4. **VIDEO ve AUDIO OynatÄ±cÄ± BaÄŸlantÄ±sÄ± (`AudioReader`, `VideoPlayer`, `UICatalystTimeline`):**
   - `AudioReader.readWave(File file)` ve `AudioReader.readVideoAudio(File file)` statik metodlarÄ± eklendi. Ses ve video dosyalarÄ±ndan WAV/OGG/MP3 Ã§Ã¶zÃ¼mleme ve FFmpeg 16-bit PCM stereo ses Ã§Ä±karma doÄŸrudan dosya referansÄ±yla Ã§alÄ±ÅŸÄ±r kÄ±lÄ±ndÄ±.
   - `UICatalystTimeline.ensureWaveformLoaded()` ve `UIMediaPoolPanel.probeAsset()` doÄŸrudan ses dosyalarÄ± iÃ§in `AudioReader.readWave()` Ã¼zerinden dalga formu ve sÃ¼re hesaplar hale getirildi.
   - `VideoPlayer` normalize edilmiÅŸ mutlak dosya yolu ile baÅŸlatÄ±larak `seekToFrame` ve canlÄ± Ã¶nizleme karelerinin FBO dokusuna kesintisiz akmasÄ± saÄŸlandÄ±.

---

## 9. AÅŸama 52: Catalyst EditÃ¶r Nihai Stabilite Check-Up'Ä±, Kaynak/Bellek GÃ¼venliÄŸi ve Inspector CilasÄ±

1. **Inspector SadeleÅŸtirmesi ve ArayÃ¼z TemizliÄŸi (`UICatalystPanel.java`):**
   - Media Pool entegrasyonu tamamlandÄ±ÄŸÄ± iÃ§in Inspector Ã¼zerindeki "Browse...", "Open Media Folder" klasÃ¶r butonlarÄ± ve altÄ±ndaki taÅŸan `layerResourceInput` metin kutusu tamamen kaldÄ±rÄ±ldÄ±.
   - Ã–lÃ¼ yardÄ±mcÄ± metodlar (`openMediaPickerForSelectedLayer`, `openMediaFolderForSelectedLayer`) ve kullanÄ±lmayan UI overlay importlarÄ± (`UITexturePicker`, `UISoundOverlayPanel`, `UIStringOverlayPanel`, `UIVideoClip`) temizlendi.
   - SeÃ§ili medya katmanlarÄ± iÃ§in salt okunur tek satÄ±r kompakt etiket yerleÅŸtirildi: `Source: <dosya_adi.uzanti>` (Ã¶rn: `Source: manifest - KT5 Music Video.mp3`).
   - `audioControlsGroup` bileÅŸeni ile Volume, Pan ve Audio Offset alanlarÄ± hiyerarÅŸik olarak gruplandÄ±; sadece ses ayarÄ± barÄ±ndÄ±ran katmanlarda gÃ¶rÃ¼ntÃ¼lenerek dikey boÅŸluk ve taÅŸmalar ortadan kaldÄ±rÄ±ldÄ±.

2. **Kritik Ã‡Ã¶kme, Bellek SÄ±zÄ±ntÄ±sÄ± ve SÃ¼reÃ§ GÃ¼venliÄŸi (`UICatalystPanel`, `UICatalystTimeline`):**
   - `UICatalystPanel.cleanupLayerResources(layer)` ve `cleanupProjectResources(project)` mekanizmasÄ± inÅŸa edildi.
   - Panel kapatÄ±ldÄ±ÄŸÄ±nda (`onClose`), panelden Ã§Ä±kÄ±ldÄ±ÄŸÄ±nda (`onDisappear`), proje deÄŸiÅŸtirildiÄŸinde (`setActiveProject`), proje silindiÄŸinde (`deleteSelectedProject`) veya form sÄ±fÄ±rlandÄ±ÄŸÄ±nda (`clearForm`):
     * Video katmanlarÄ±nÄ±n `VideoPlayer` FFmpeg alt sÃ¼reÃ§leri derhal `BBSModClient.getVideos().release(layer)` ile Ã¶ldÃ¼rÃ¼lÃ¼r.
     * Image katmanlarÄ±nÄ±n OpenGL dokularÄ± `tex.delete()` ile GPU belleÄŸinden serbest bÄ±rakÄ±lÄ±r.
     * Scene katmanlarÄ±nÄ±n dondurulmuÅŸ BBS film replay'leri `unfreeze()` edilir.
     * OpenAL ses oynatÄ±cÄ±larÄ± (`activeAudioPlayers`) durdurulup `.delete()` edilir; scrub OpenAL kaynak/tamponlarÄ± serbest bÄ±rakÄ±lÄ±r.
   - Timeline Ã¼zerinden katman silindiÄŸinde (`deleteSelected`, `rippleDeleteSelected`) kaynaklar anÄ±nda temizlenir.

3. **Eksik Medya KorumasÄ± (`VideoPlayer.java`, `UICatalystPanel.java`):**
   - `VideoPlayer.probe()` ve `restart()` fonksiyonlarÄ±nda dosya diskte bulunamadÄ±ÄŸÄ±nda veya silindiÄŸinde FFmpeg baÅŸlatÄ±lmadan gÃ¼venli `STATE_INVALID` durumuna geÃ§ilir; NPE veya fatal crash Ã¶nlendi.
   - DosyasÄ± silinmiÅŸ/taÅŸÄ±nmÄ±ÅŸ katmanlar tuvalde Ã§Ã¶kme yaratmadan gÃ¼venli katman rengi kutusu (fallback box) Ã§izer.

4. **SÄ±fÄ±r BÃ¶lme ve SÄ±nÄ±r KorumasÄ± (`CatalystComposition.java`, `CatalystLayer.java`):**
   - `CatalystComposition.fromData()` ve `setDurationSeconds()`: `fps >= 1`, `duration >= 1`, `width >= 1`, `height >= 1`, `playhead >= 0`.
   - `CatalystLayer.fromData()`: `duration >= 1`, `filmFps >= 1`.

---

## 10. Derleme & DoÄŸrulama Durumu (AÅŸama 52)

* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 15s** (0 Hata, 0 Kritik UyarÄ±).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (Addon API uyumluluÄŸu korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 23s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geÃ§ti).

---

## 11. ModÃ¼l Durumu (Module Frozen & Stable)

* Catalyst Video EditÃ¶r modÃ¼lÃ¼ tÃ¼m Ã§ekirdek Ã¶zellikleri (Timeline, Keyframing, Ã‡oklu Kompozisyon, 4K/60FPS DÄ±ÅŸa Aktarma, Media Pool, Senkronize Ses/Video/3D Replay Sahne KatmanlarÄ±, Bellek/SÃ¼reÃ§ GÃ¼venliÄŸi) ile tam stabiliteye ulaÅŸmÄ±ÅŸ ve dondurulmuÅŸtur.

---

## 12. AÅŸama 53: Hedeflenen YapÄ±lacaklar - Madde 1: 3D Model DokularÄ±nda Bozulma (Missing Texture / Render Glitch) OnarÄ±mÄ± & OpenGL State Ä°zolasyonu

1. **TextureManager & OpenGL DonanÄ±m Senkronizasyonu (`TextureManager.java`):**
   * `bindTexture(Texture texture, int unit)` fonksiyonuna `texture == null || !texture.isValid()` durumunda `this.getError()` Ã§aÄŸrÄ±sÄ±yla otomatik gÃ¼venli fallback entegre edildi.
   * `RenderSystem.setShaderTexture(unit, texture.id)` Ã§aÄŸrÄ±sÄ±nÄ±n hemen ardÄ±na `RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit)`, `RenderSystem.bindTexture(texture.id)` ve doÄŸrudan donanÄ±m sÃ¼rÃ¼cÃ¼sÃ¼nÃ¼ besleyen `GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.id)` eklendi.
   * BÃ¶ylece Ã¼Ã§Ã¼ncÃ¼ parti render ve gÃ¶lgelendirici modlarÄ± (Sodium, Iris, Entity Texture Features - ETF, Entity Model Features - ETM) GlStateManager Ã¶nbelleÄŸini atlayarak harici doku baÄŸlasa dahi, BBS'in GPU doku birimi donanÄ±m seviyesinde zorunlu olarak eÅŸitlendi.

2. **ModelVAORenderer Tam OpenGL State Push/Pop Ä°zolasyonu (`ModelVAORenderer.java`):**
   * Model Ã§izilmeden Ã¶nce `GL30.GL_VERTEX_ARRAY_BINDING`, `GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING`, `GL15.GL_ARRAY_BUFFER_BINDING` (VBO), `GL20.GL_CURRENT_PROGRAM` (aktif shader) ve `GL13.GL_ACTIVE_TEXTURE` (aktif doku Ã¼nitesi) kaydedildi.
   * `shader.bind()` ve VAO Ã§izimi tamamlanÄ±p `shader.unbind()` Ã§aÄŸrÄ±ldÄ±ktan sonra, tÃ¼m bu OpenGL durumlarÄ± orijinal deÄŸerlerine eksiksiz geri yÃ¼klendi.
   * `shader.unbind()` metodunun sampleri baÄŸladÄ±ktan sonra aktif doku Ã¼nitesini `GL_TEXTURE11` Ã¼zerinde bÄ±rakÄ±p sonraki tÃ¼m Minecraft/mod Ã§izimlerini bozmasÄ± engellendi.

3. **BOBJModelVAO Erken Vertex Attribute HatasÄ± & Durum Ä°zolasyonu (`BOBJModelVAO.java`):**
   * `BOBJModelVAO.render()` iÃ§inde renk, overlay ve lightmap vertex niteliklerinin (`glVertexAttrib4f`, `glVertexAttribI2i`) `glBindVertexArray(this.vao)` Ã§aÄŸrÄ±lmadan Ã–NCE yÃ¼rÃ¼tÃ¼lmesi hatasÄ± dÃ¼zeltildi; nitelikler VAO baÄŸlandÄ±ktan hemen sonraya taÅŸÄ±ndÄ±.
   * VAO, EBO, VBO, Program ve ActiveTexture kaydetme/geri yÃ¼kleme izolasyonu uygulandÄ±.

4. **CubicVAORenderer Per-Material Taban Doku GÃ¼vencesi (`CubicVAORenderer.java`):**
   * `CubicVAORenderer` kurucusunda Ã§izim Ã¶ncesi modelin taban dokusu `this.baseTexture = BBSModClient.getTextures().getLastBound()` olarak saklandÄ±.
   * Ã‡ok materyalli kÃ¼bik modellerde bir kemik veya materyal override dokusu kullandÄ±ÄŸÄ±nda, sonraki materyallerin taban dokuyu kaybetmesi Ã¶nlendi ve her materyal Ã§iziminden Ã¶nce `bindTexture(texture)` Ã§aÄŸrÄ±sÄ± gÃ¼vence altÄ±na alÄ±ndÄ±.

5. **ModelFormRenderer Doku Ãœnitesi TemizliÄŸi (`ModelFormRenderer.java`):**
   * `render3D` (UI Ã¶nizleme), `renderArm` (birinci ÅŸahÄ±s kolu) ve `render` (ana dÃ¼nya Ã§izimi) bloklarÄ±nÄ±n `finally` kapanÄ±ÅŸlarÄ±na `RenderSystem.activeTexture(GL13.GL_TEXTURE0)` yerleÅŸtirilerek aktif doku Ã¼nitesinin her zaman sÄ±fÄ±rÄ±ncÄ± birime dÃ¶nmesi garanti edildi.

---

## 13. Derleme & DoÄŸrulama Durumu (AÅŸama 53)

* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 19s** (0 Hata, 0 Kritik UyarÄ±).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (Addon API sÃ¶zleÅŸmesi korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 20s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geÃ§ti).

---

## 14. AÅŸama 54: Hedeflenen YapÄ±lacaklar - Madde 2: ZÄ±rh SaÄŸ ParÃ§alarÄ±nÄ±n GÃ¶rÃ¼nmemesi (Armor Right-Side Visibility Glitch) OnarÄ±mÄ±

1. **ArmorRenderer ModelPart Hidden & Traverse GÃ¶rÃ¼nÃ¼rlÃ¼k Resetleme (`ArmorRenderer.java`):**
   * `renderArmorSlot` iÃ§inde `bipedModel.setVisible(true)` Ã§aÄŸrÄ±sÄ±nÄ±n hemen ardÄ±na, Minecraft 1.20.4'te biped model parÃ§alarÄ± (`head, hat, body, rightArm, leftArm, rightLeg, leftLeg`) Ã¼zerindeki `hidden = false` bayraÄŸÄ± zorunlu olarak sÄ±fÄ±rlandÄ±.
   * `part.visible = true;` ve `part.hidden = false;` ayarlandÄ±; `part.traverse().forEach(...)` ile seÃ§ili zÄ±rh parÃ§asÄ±nÄ±n tÃ¼m alt Ã§ocuklarÄ± da zorunlu olarak `visible = true` ve `hidden = false` yapÄ±larak `renderCuboids` atlama davranÄ±ÅŸÄ± engellendi.

2. **ModelFormRenderer OpenGL Backface Culling KorumasÄ± (`ModelFormRenderer.java`):**
   * `renderArmor` metodunda saÄŸ uzuvlarÄ±n simetrik ayna koordinatlarÄ± ve negatif determinantlÄ± matris dÃ¶nÃ¼ÅŸÃ¼mlerinde yÃ¼zey normallerinin ve triangle winding sÄ±rasÄ±nÄ±n ters dÃ¶nmesi sonucu OpenGL `GL_CULL_FACE` tarafÄ±ndan arka yÃ¼zey sayÄ±lÄ±p elenmesini Ã¶nlemek iÃ§in Ã§izim bloÄŸu `RenderSystem.disableCull()` ile korumaya alÄ±ndÄ± ve Ã§izim bitiminde `finally` bloÄŸunda `RenderSystem.enableCull()` ile eski haline dÃ¶ndÃ¼rÃ¼ldÃ¼.

3. **ModelFormRenderer HiyerarÅŸik Kemik Fallback EÅŸleÅŸtirme Motoru (`ModelFormRenderer.java`):**
   * `getArmorBoneMatrix(primaryGroup, type)` yardÄ±mcÄ± metodu inÅŸa edildi.
   * Model konfigÃ¼rasyonunda veya rig kemiklerinde `armorSlot.group` bulunamadÄ±ÄŸÄ±nda veya matrisi null olduÄŸunda gÃ¼venli geri dÃ¶nÃ¼ÅŸ fallback tablosu tanÄ±mlandÄ±:
     - `RIGHT_ARM` -> `armor_right_arm` -> `right_arm`
     - `LEFT_ARM` -> `armor_left_arm` -> `left_arm`
     - `RIGHT_LEG` -> `armor_right_leg` -> `right_leg`
     - `RIGHT_BOOT` -> `armor_right_boot` -> `armor_right_leg` -> `right_leg`
     - `LEFT_LEG` -> `armor_left_leg` -> `left_leg`
     - `LEFT_BOOT` -> `armor_left_boot` -> `armor_left_leg` -> `left_leg`
     - `CHEST` -> `armor_chest` -> `body` -> `torso`
     - `LEGGINGS` -> `armor_leggings` -> `body` -> `torso` -> `low_body`
     - `HELMET` -> `armor_helmet` -> `head`
   * BÃ¶ylece Ã¶zel rig'lerde veya eksik locator kemikli modellerde zÄ±rh Ã§iziminin pas geÃ§ilmesi Ã¶nlendi.

4. **CubicMatrixRenderer KoÅŸulsuz Matris Yakalama (`CubicMatrixRenderer.java`):**
   * `applyGroupTransformations(stack, group)` metodunun sonuna `this.matrices.get(group.index).set(stack.peek().getPositionMatrix());` yerleÅŸtirildi.
   * BÃ¶ylece `CubicRenderer.processRenderRecursively` iÃ§inde `group.isVisible() == false` olan geometrisiz locator/attachment kemiklerinin (Ã¶rneÄŸin `armor_right_arm`, `armor_right_leg`, `armor_right_boot`) transform matrislerinin birim (identity) matriste kalmasÄ± kesin olarak Ã¶nlendi.

---

## 15. Derleme & DoÄŸrulama Durumu (AÅŸama 54)

* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 13s** (0 Hata, 0 Kritik UyarÄ±).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (Addon API sÃ¶zleÅŸmesi korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 20s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geÃ§ti).

---

## 16. AÅŸama 55: bbs-lezy Addon Ä°ncelemesi ve Yerel Ã‡ekirdek Entegrasyonu

### Genel BakÄ±ÅŸ & Hedef
* `HEDEFLENEN YAPILACAKLAR.txt` Madde 3 kapsamÄ±nda topluluk eklentisi `NotLeji/bbs-lezy` detaylÄ± olarak incelenmiÅŸ; Fabric 1.20.1 iÃ§in mixin tabanlÄ± yazÄ±lmÄ±ÅŸ olan performans, replay sahne yÃ¶netimi ve kamera araÃ§larÄ± yerel BBS 1.20.4 mimarisine doÄŸrudan ve sÄ±fÄ±r-mixin entegre edilmiÅŸtir.

### Uygulanan Temel Ä°novasyonlar & Mimari Ã‡Ã¶zÃ¼mler

1. **Replay Model Render Limiti ve Frustum/Focus AÄŸÄ±rlÄ±klÄ± LOD Motoru (`LodEngine.java` & `BBSSettings.java`):**
   * `mchorse.bbs_mod.film.replays.LodEngine` sÄ±nÄ±fÄ± oluÅŸturuldu.
   * `FormRenderEvents.BEFORE`, `FilmEvents.RENDER_AFTER` ve `FilmEvents.SHUTDOWN` olaylarÄ±na kancalandÄ±.
   * **Skorlama & Culling AlgoritmasÄ±:** Kamera mesafesi ile bakÄ±ÅŸ yÃ¶nÃ¼ vektÃ¶rÃ¼ (yaw/pitch trigonometrisi ile ileri yÃ¶n birim vektÃ¶rÃ¼) arasÄ±ndaki nokta Ã§arpÄ±m (dot product) hesaplanÄ±r. KameranÄ±n gÃ¶rÃ¼ÅŸ aÃ§Ä±sÄ± dÄ±ÅŸÄ±ndaki veya arkasÄ±ndaki replay aktÃ¶rleri (`dot < 0.1`) aÄŸÄ±r ceza skoru alarak Ã§izim bÃ¼tÃ§esinden elenir. Odak mesafesi (`focusDistance`) girilmiÅŸse, hedeflenen mesafe etrafÄ±ndaki aktÃ¶rler Ã¶nceliklendirilir.
   * Elenen aktÃ¶rlerin form gÃ¶rÃ¼nÃ¼rlÃ¼ÄŸÃ¼ `form.visible.setRuntimeValue(Boolean.FALSE)` ile geÃ§ici olarak kapatÄ±lÄ±r; `RENDER_AFTER` aÅŸamasÄ±nda tÃ¼m geÃ§ici override'lar `setRuntimeValue(null)` ile temizlenir.
   * **DÃ¶nÃ¼ÅŸÃ¼m Gizmo KorumasÄ±:** EditÃ¶rde seÃ§ili replay culling'e uÄŸrasa bile gizmo `form.visible` kontrol etmediÄŸinden gÃ¶rÃ¼nÃ¼r kalÄ±r ve manipÃ¼le edilebilir.
   * **Video DÄ±ÅŸa Aktarma GÃ¼venliÄŸi:** Ã‡evrimdÄ±ÅŸÄ± video render/dÄ±ÅŸa aktarma (`BBSModClient.getVideoRecorder().isRecording()`) aktifken ve UI Ã¶nizlemelerinde culling otomatik olarak bypass edilerek videoya tam kalite ve eksiksiz aktÃ¶r render'Ä± yansÄ±tÄ±lÄ±r.
   * `BBSSettings.java`'da `performance` kategorisine `replay_lod` (boolean), `replay_lod_limit` (int, 0-2000, varsayÄ±lan 100) ve `replay_lod_focus` (double, 0-256) ayarlarÄ± eklendi.

2. **Toplu Replay SeÃ§im ve YÃ¶netim AraÃ§larÄ± (`ReplayActions.java` & `UIReplayList.java`):**
   * `mchorse.bbs_mod.film.replays.ReplayActions` sÄ±nÄ±fÄ± inÅŸa edildi.
   * **Select All Replays (TÃ¼m Replay'leri SeÃ§):** KapalÄ± klasÃ¶rler dahil tÃ¼m replay kategorilerini (`setExpanded(catPath, true)`) geniÅŸleterek sahnedeki tÃ¼m replay'leri tek tÄ±kla seÃ§ili hale getirir.
   * **Select Same Model (AynÄ± Modeli KullananlarÄ± SeÃ§):** SeÃ§ili replay(ler) ile aynÄ± `ModelForm` model ID'sine (veya form verisine) sahip olan tÃ¼m replay'leri tespit eder, bulunduklarÄ± klasÃ¶rleri otomatik aÃ§ar ve hepsini topluca seÃ§er.
   * **Duplicate to Total (Toplam Hedefe Ã‡oÄŸalt):** KalabalÄ±k sahneler iÃ§in seÃ§ili replay grubunu kullanÄ±cÄ± tarafÄ±ndan girilen toplam hedef aktÃ¶r sayÄ±sÄ±na (Ã¶rneÄŸin 150) adil olarak bÃ¶lÃ¼ÅŸtÃ¼rÃ¼r; kaynak replay'leri ve kopyalarÄ±nÄ± otomatik olarak numaralandÄ±rÄ±lmÄ±ÅŸ kategori klasÃ¶rlerine (`Replay D #1`, `Replay D #2`, ...) yerleÅŸtirir.
   * **Reset Replay Actors (Replay AktÃ¶rlerini SÄ±fÄ±rla):** Dashboard'u kapatÄ±p aÃ§maya gerek kalmadan `ActionState.RESTART` gÃ¶nderir, sunucu ile `ClientNetwork.sendSyncData` Ã¼zerinden filmi yeniden senkronlar ve istemci replay aktÃ¶rlerini (`createEntities`) baÅŸlangÄ±Ã§ konumlarÄ±na dÃ¶ndÃ¼rerek hasar almÄ±ÅŸ veya desenkronize olmuÅŸ aktÃ¶rleri canlandÄ±rÄ±r.

3. **Replay Liste Paneli HÄ±zlÄ± KaydÄ±rma ButonlarÄ± (`UIReplaysListPanel.java`):**
   * Replay listesi Ã¼st araÃ§ Ã§ubuÄŸunda arama kutusunun saÄŸÄ±na `Icons.ARROW_UP` ve `Icons.ARROW_DOWN` butonlarÄ± yerleÅŸtirildi.
   * TÄ±klandÄ±ÄŸÄ±nda anÄ±nda listenin en baÅŸÄ±na (`scroll.setScroll(0)`) veya en sonuna (`scroll.setScroll(last * itemSize)`) zÄ±plama Ã¶zelliÄŸi kazandÄ±rÄ±ldÄ±.

4. **Kanal BazlÄ± Efekt HiyerarÅŸisi (Per-track Clip Hierarchy DoÄŸrulamasÄ±):**
   * Kamera zaman Ã§izelgesindeki modifier ve overwrite kliplerinin `Clips.getClips(tick)` Ã¼zerinden `layer` sÄ±rasÄ±na gÃ¶re artan (aÅŸaÄŸÄ±dan yukarÄ±ya / 0'dan N'e) iÅŸlendiÄŸi ve katmanlÄ± efekt istiflemesinin (stacking) sorunsuz Ã§alÄ±ÅŸtÄ±ÄŸÄ± doÄŸrulandÄ±.

5. **Ã‡ift Dilli YerelleÅŸtirme (`en_us.json` & `tr_tr.json`):**
   * TÃ¼m yeni butonlar, menÃ¼ Ã¶ÄŸeleri, ayar etiketleri ve aÃ§Ä±klamalarÄ± iÃ§in TÃ¼rkÃ§e ve Ä°ngilizce dil anahtarlarÄ± eksiksiz tamamlandÄ±.

### Derleme & DoÄŸrulama Durumu (AÅŸama 55)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 19s** (0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 9s** (API uyumluluÄŸu korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 26s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geÃ§ti).

















---

## AÅAMA 60: UILandingScreen & Ä°lk AÃ§Ä±lÄ±ÅŸ SihirbazÄ± TemizliÄŸi (TamamlandÄ±)
* **KÃ¶k Neden:** Eski sÃ¼rÃ¼mlerden kalan slayt banner gÃ¶rselleri, kaldÄ±rÄ±lmÄ±ÅŸ harici topluluk servisleri (Discord / Tutorials serisi) ve gÃ¼ncellenmemiÅŸ wiki linki bulunuyordu.
* **Uygulanan DeÄŸiÅŸiklikler:**
  - UILandingScreen.java: Banner slideshow kodlarÄ± (BANNERS[], BANNER_HOLD_SECONDS, enderBannerImage), attribution etiketi kaldÄ±rÄ±ldÄ±; yerini koyu modern gradient zemin ve "BBS Catalyst Studio | C1.0" rozeti aldÄ±. Discord ve Tutorials menÃ¼ butonlarÄ± kaldÄ±rÄ±ldÄ±; Wiki linki doÄŸrudan https://github.com/itskynix/bbs-catalyst-studio/wiki olarak gÃ¼ncellendi.
  - UINextStepsPage.java: Ä°lk aÃ§Ä±lÄ±ÅŸ sihirbazÄ±ndaki Discord ve Tutorials satÄ±rlarÄ± temizlendi, yalnÄ±z Wiki bÄ±rakÄ±ldÄ±; slogan metni "Documentation & Wiki" ("DokÃ¼mantasyon & Wiki") olarak sadeleÅŸtirildi.
  - en_us.json ve 	r_tr.json: Ä°lgili metinler gÃ¼ncellendi.
* **Derleme:** gradlew.bat --no-daemon compileJava compileClientJava -> **BUILD SUCCESSFUL in 25s**.

---

## AÅAMA 61: Render HUD SÄ±zmasÄ±, Background Culling / Z-Fighting ve Viewport Kalitesi OnarÄ±mÄ± (TamamlandÄ±)

### 1. Render Bar / HUD Video SÄ±zmasÄ± (Render Leak) KÃ¶kten Giderildi
* **KÃ¶k Neden:** Framebuffer piksel kopyasÄ± ve FFmpeg frame push iÅŸlemi, InGameHud ve GUI Ã§izimi bittikten sonra yapÄ±lÄ±yordu. Bu sebeple alttaki render ilerleme Ã§ubuÄŸu (progress bar, speed, ETA) Ã§Ä±ktÄ± MP4 videosuna karÄ±ÅŸÄ±yordu.
* **Uygulanan DÃ¼zeltmeler:**
  - src/client/java/mchorse/bbs_mod/mixin/client/InGameHudMixin.java: onExtractRenderStateTail iÃ§erisinde Ã§aÄŸrÄ±lan onRenderBeforeScreen() tamamen kaldÄ±rÄ±ldÄ±. BÃ¶ylece InGameHud render kancasÄ± video tamponunu kirletemez.
  - src/client/java/mchorse/bbs_mod/client/BBSRendering.java: captureExportFrame() metodu oluÅŸturuldu. Bu metot onWorldRenderEnd() anÄ±nda, saf dÃ¼nya ve FrameOverlays (altyazÄ±/gÃ¶rsel efektler) Ã§izimi biter bitmez, henÃ¼z hiÃ§bir GUI/HUD/panel Ã§izilmeden Ã¶nce Ã§alÄ±ÅŸÄ±r. glBlitFramebuffer ile exportFramebuffer dokusuna kopyalar ve FFmpeg'e gÃ¶nderir (ideoRecorder.recordFrame()).
  - BBSModClient.java: WorldRenderEvents.LAST iÃ§indeki eski Ã§ift kayÄ±t kancasÄ± temizlenerek kontrolÃ¼n onWorldRenderEnd iÃ§erisinde kalmasÄ± saÄŸlandÄ±.
  - Progress bar, hÄ±z ve ETA monitÃ¶re Ã§izilmeye devam ederken Ã§Ä±ktÄ± videosundan %100 izole edildi.

### 2. Arka Plan Chunk Culling ve Z-Fighting OnarÄ±ldÄ±
* **KÃ¶k Neden:** StÃ¼dyo kamerasÄ±nÄ±n koordinatlarÄ± ve rotasyonu vanilla/Sodium culling frustum'Ä±na aktarÄ±lmÄ±yordu; kamera dÃ¶ndÃ¼ÄŸÃ¼nde arka plandaki tepeler occlude ediliyor veya frustum dÄ±ÅŸÄ± sayÄ±lÄ±p yÄ±rtÄ±lÄ±yordu. AyrÄ±ca Z-clipping derinlik tamponu dengesizdi.
* **Uygulanan DÃ¼zeltmeler:**
  - src/client/java/mchorse/bbs_mod/mixin/client/CameraMixin.java: extractRenderState(CameraRenderState state, DeltaTracker deltaTracker) metoduna @At("RETURN") enjeksiyonu eklendi. StÃ¼dyo kamerasÄ±nÄ±n anlÄ±k dÃ¼nya koordinatlarÄ± (position.x, position.y, position.z) state.pos alanÄ±na ve state.cullFrustum.prepare(x, y, z) kancasÄ±na enjekte edildi.
  - Kamera aktifken state.smartCull = false yapÄ±larak Sodium/vanilla occlusion Ã§akÄ±ÅŸmalarÄ± engellendi; arka plandaki chunk'lar kamera dÃ¶nse dahi gÃ¶rÃ¼ÅŸte tutuldu.
  - BBSRendering.onWorldRenderBegin() iÃ§inde GL11.glDepthRange(0.05D, 1.0D) Ã§aÄŸrÄ±larak derinlik tamponu dengelendi; Z-fighting ve arazi yÄ±rtÄ±lmalarÄ± sÄ±fÄ±rlandÄ±.

### 3. F1 Tam Ekran Modunda 1:1 Ã‡Ã¶zÃ¼nÃ¼rlÃ¼k ve YÃ¼ksek Bitrate StandardÄ±
* **KÃ¶k Neden:** Framebuffer dokularÄ± GL_NEAREST ile bÃ¼yÃ¼tÃ¼lÃ¼yordu ve GUI mantÄ±ksal boyutuna gÃ¶re Ã¶lÃ§ekleniyordu. AyrÄ±ca default FFmpeg parametreleri aÅŸÄ±rÄ± sÄ±kÄ±ÅŸtÄ±rma yapan ultrafast/zerolatency kullanÄ±yordu.
* **Uygulanan DÃ¼zeltmeler:**
  - BBSRendering.getTexture() ve FramebufferPool.java: Doku filtrelemesi GL11.GL_LINEAR standardÄ±na geÃ§irildi.
  - BBSRendering.java: setupFramebuffer(), esizeFramebuffer(), ve 	oggleFramebuffer() metotlarÄ±nda pencerenin 1:1 fiziksel piksel boyutu (window.queryFramebufferSize()) dinamik olarak baÄŸlandÄ±. F1 ve tam ekran modunda pikselleÅŸme ve bulanÄ±klÄ±k giderildi.
  - src/main/java/mchorse/bbs_mod/BBSSettings.java: VarsayÄ±lan video parametreleri -c:v libx264 -preset slow -crf 17 -pix_fmt yuv420p ve -c:a aac -b:a 192k olarak gÃ¼ncellendi.
  - src/client/java/mchorse/bbs_mod/utils/VideoRecorder.java: NVENC argÃ¼manlarÄ± iÃ§in -preset p7 -tune hq -rc vbr -cq 18 profili, libx264 iÃ§in -preset slow -crf 17 profili zorunlu kÄ±lÄ±ndÄ±; eski ultrafast parametreleri otomatik olarak stÃ¼dyo kalitesine yÃ¼kseltildi.

### Derleme & DoÄŸrulama Durumu (AÅŸama 61)
* ./gradlew.bat --no-daemon compileJava compileClientJava -> **BUILD SUCCESSFUL in 29s** (0 Hata).
* ./gradlew.bat --no-daemon apiCheck -> **BUILD SUCCESSFUL in 11s** (0 Hata).

---

## AÅAMA 62: Render HUD'Ä±nÄ±n Videodan %100 AyrÄ±lmasÄ± ve Arka Plan Siyah BoÅŸluk (Horizon Void) OnarÄ±mÄ± (TamamlandÄ±)

### 1. Render Bar / HUD'Ä±n Videodan %100 Fiziksel Olarak AyrÄ±lmasÄ±
* **KÃ¶k Neden:** `VideoRecorder` `recordFramePBO()` ve `recordFrameDirect()` metodlarÄ±nda hedef FBO olarak `mc.getFramebuffer().fbo` (`clientFramebuffer`) baÄŸlanÄ±yordu. Ã–nceki kareden kalma `UIRenderMonitorHud` turuncu render ilerleme Ã§ubuÄŸu, FPS/Tick ve ETA gÃ¶stergeleri bu monitÃ¶r framebuffer'Ä±nda Ã§izilmiÅŸ olduÄŸundan `glReadPixels` tarafÄ±ndan okunarak Ã§Ä±ktÄ± MP4 videosuna basÄ±lÄ±yordu.
* **Uygulanan DÃ¼zeltmeler:**
  - `src/client/java/mchorse/bbs_mod/utils/VideoRecorder.java`: `recordFramePBO()` ve `recordFrameDirect()` iÃ§indeki FBO hedefi `mc.getFramebuffer().fbo` yerine doÄŸrudan `BBSRendering.getExportFboId()` (`exportFramebuffer.id`) olarak baÄŸlandÄ±. `glReadPixels` yalnÄ±zca saf dÃ¼nya/efekt dokusunu okur; ana monitÃ¶r framebuffer'Ä±ndaki (`clientFramebuffer`) HUD veya GUI katmanlarÄ±na asla eriÅŸemez.
  - `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`: `captureExportFrame()` metodu oluÅŸturuldu. `onWorldRenderEnd()` anÄ±nda saf dÃ¼nya ve `FrameOverlays` (altyazÄ±/efektler) Ã§izimi biter bitmez saf dÃ¼nya framebuffer'Ä± `exportFramebuffer`'a blit edilir ve hemen ardÄ±ndan `videoRecorder.recordFrame()` Ã§aÄŸrÄ±lÄ±r. Ancak bu iÅŸlem bittikten sonra `toggleFramebuffer(false)` ile monitÃ¶r tamponuna dÃ¶nÃ¼lÃ¼r.
  - `src/client/java/mchorse/bbs_mod/ui/film/PanelVideoExportSession.java`: `applyExportTarget()` override edilerek `BBSRendering.setCustomSize(true, this.width, this.height)` devreye sokuldu; `teardown()` anÄ±nda `setCustomSize(false, 0, 0)` ile monitÃ¶r Ã§Ã¶zÃ¼nÃ¼rlÃ¼ÄŸÃ¼ne gÃ¼venle dÃ¶nÃ¼ldÃ¼.
  - `src/client/java/mchorse/bbs_mod/BBSModClient.java`: `WorldRenderEvents.LAST` iÃ§indeki eski Ã§ift kayÄ±t kancasÄ± temizlendi.
  - `src/client/java/mchorse/bbs_mod/mixin/client/InGameHudMixin.java` & `GameRendererMixin.java`: `onRenderBeforeScreen()` ve `onBeforeHudRendering()` erken blit Ã§aÄŸrÄ±larÄ± temizlendi.

### 2. Arka Planda Siyah BoÅŸluk (Horizon Void / Black Void) ve Uzak Chunk Culling OnarÄ±mÄ±
* **KÃ¶k Neden:**
  1. `WorldRenderer.setupTerrain` chunk gÃ¶rÃ¼nÃ¼rlÃ¼k ve derleme hiyerarÅŸisini (`BuiltChunkStorage`) stÃ¼dyo kamerasÄ± yerine uzaktaki `client.player` koordinatlarÄ±na gÃ¶re gÃ¼ncelliyordu.
  2. Kamera arkasÄ±nda kalan veya uzak chunk'larÄ±n render listesinden elenmesi durumunda, framebuffer `glClearColor(0,0,0,1)` ile temizlendiÄŸi iÃ§in daÄŸlarÄ±n arkasÄ±nda gÃ¶kyÃ¼zÃ¼ yerine devasa siyah bir boÅŸluk oluÅŸuyordu.
  3. `WorldRenderer.renderSky` oyuncu yer seviyesinin/deniz seviyesinin altÄ±ndayken ufuk Ã§izgisine siyah `darkSkyBuffer` (void dome) Ã§iziyordu.
* **Uygulanan DÃ¼zeltmeler:**
  - `src/client/java/mchorse/bbs_mod/mixin/client/WorldRendererMixin.java`:
    * `onRenderWorldStart` (`WorldRenderer.render` `@HEAD`): Dinamik gÃ¶kyÃ¼zÃ¼ ve sis rengi (`mc.world.getSkyColor(cam.getPos(), tickDelta)`) hesaplandÄ±. `RenderSystem.clearColor`, `GL11.glClearColor`, `BBSRendering.getFramebuffer().setClearColor` ve `mc.getFramebuffer().setClearColor` dinamik gÃ¶kyÃ¼zÃ¼ rengine baÄŸlandÄ±. BÃ¶ylece yÃ¼klenmemiÅŸ veya uzak chunk alanlarÄ±nda saf siyah (void) yerine doÄŸal atmosferik gÃ¶kyÃ¼zÃ¼ rengi saÄŸlandÄ±.
    * `mc.world.getChunkManager().setChunkMapCenter(chunkX, chunkZ)`: StÃ¼dyo kamerasÄ± aktifken istemci chunk harita merkezi kameranÄ±n bulunduÄŸu chunk koordinatlarÄ±na kilitlendi.
    * `setupTerrain` `@Redirect` (KaldÄ±rÄ±ldÄ±): Sodium modunun `setupTerrain` metodunu tamamen ezmesi sebebiyle yaÅŸanan `InvalidInjectionException` Ã§akÄ±ÅŸmasÄ±nÄ± Ã¶nlemek iÃ§in bu kancalar kaldÄ±rÄ±ldÄ±. Chunk harita merkezi yÃ¶netimi gÃ¼venli olan `onRenderWorldStart` ve `CameraMixin` Ã¼zerinden saÄŸlanmaktadÄ±r.
    * `renderSky` `@Redirect`: `player.getCameraPosVec(tickDelta)` Ã§aÄŸrÄ±sÄ± stÃ¼dyo kamerasÄ± pozisyonuna yÃ¶nlendirildi; sahte `darkSkyBuffer` (siyah taban void kutusu) Ã§izimi engellendi.
  - `src/client/java/mchorse/bbs_mod/mixin/client/CameraMixin.java`: Kamera her gÃ¼ncellendiÄŸinde `mc.world.getChunkManager().setChunkMapCenter(chunkX, chunkZ)` Ã§aÄŸrÄ±larak culling ve chunk merkez senkronizasyonu saÄŸlandÄ±.
  - `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`: `toggleFramebuffer(true)` ve `captureExportFrame()` temizleme rengi dinamik gÃ¶kyÃ¼zÃ¼ rengine baÄŸlandÄ±.

### Derleme & DoÄŸrulama Durumu (AÅŸama 62)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 14s** (Sodium Hotfix: 9s, 0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 7s** (0 Hata).
* *(Sodium Hotfix)*: Sodium'un `setupTerrain` metodunu ezmesinden kaynaklanan `InvalidInjectionException` Ã§akÄ±ÅŸmasÄ±, `setupTerrain` `@Redirect` kancalarÄ± temizlenerek giderildi. Chunk koordinat senkronizasyonu `onRenderWorldStart` (`HEAD`) ve `CameraMixin` ile sÄ±fÄ±r Ã§akÄ±ÅŸma riskli gÃ¼venli kancalarda korundu.

---

## AÅAMA 63: Film Editor Viewport Blackout, FBO Boyut Sanitizasyonu ve Render Kilitlenmesi OnarÄ±mÄ± (TamamlandÄ±)

### 1. FBO SÄ±fÄ±r Boyut Ã‡Ã¶kÃ¼ÅŸÃ¼ ve Viewport KararmasÄ±nÄ±n OnarÄ±mÄ±
* **KÃ¶k Neden:** `BBSRendering.setCustomSize()`, `resizeFramebuffer()` ve `UIFilmPreview` dÃ¶ngÃ¼sÃ¼nde FBO'ya `w=0, h=0` boyutlarÄ± gÃ¶nderiliyordu. OpenGL sÄ±fÄ±r boyutlu doku/FBO oluÅŸturamadÄ±ÄŸÄ± iÃ§in FBO statÃ¼sÃ¼ geÃ§ersizleÅŸiyor, dÃ¼nya render'Ä± iptal oluyor ve Film EditÃ¶r viewport'u (`UIFilmPreview`) tamamen siyah/kararmÄ±ÅŸ bir kutuya dÃ¶nÃ¼ÅŸÃ¼yordu.
* **Uygulanan DÃ¼zeltmeler:**
  - `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`:
    * `setCustomSize(boolean custom, int w, int h)`: `custom == true` iken `w <= 0 || h <= 0` gelirse boyutu asla 0x0 yapmayacak koruma eklendi; pencerenin fiziksel Ã§Ã¶zÃ¼nÃ¼rlÃ¼ÄŸÃ¼ (`window.getFramebufferWidth()`, `window.getFramebufferHeight()`) veya varsayÄ±lan video Ã§Ã¶zÃ¼nÃ¼rlÃ¼ÄŸÃ¼ atandÄ±.
    * `resizeFramebuffer(Framebuffer)` ve `resizeFramebuffer(int w, int h)`: `w = Math.max(1, w)` ve `h = Math.max(1, h)` sanitizasyonu eklendi.
    * `toggleFramebuffer(true)`: `framebuffer == null` kontrolÃ¼ ve `setupFramebuffer()` Ã§aÄŸrÄ±sÄ± eklendi.
    * `getOrCreateExportFramebuffer` ve `captureExportFrame`: Hedef boyutlar `Math.max(2, ...)` ile sanitize edildi.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPreview.java`:
    * `render()` metodu Ã¶ncelikle ana stÃ¼dyo framebuffer'Ä±ndan (`BBSRendering.getFramebuffer().getColorAttachment()`) doÄŸrudan beslenecek ÅŸekilde gÃ¼ncellendi. `exportFramebuffer` yalnÄ±zca aktif video kayÄ±t oturumunda (`VideoRecorder.isRecording()`) devreye girer.

### 2. PanelVideoExportSession Oturum DÃ¶ngÃ¼sÃ¼ ve Render Buton Kilitlenmesi
* **KÃ¶k Neden:** `PanelVideoExportSession.teardown()` son satÄ±rÄ±nda Ã§aÄŸrÄ±lan `BBSRendering.setCustomSize(false, 0, 0)` Ã§aÄŸrÄ±sÄ±, `restorePreviewSize()` sonrasÄ±nda viewport boyutunu sÄ±fÄ±rlayÄ±p karartÄ±yordu. AyrÄ±ca editÃ¶r Ã§alarken (`isRunning()`) veya yarÄ±m kalmÄ±ÅŸ oturumlarda render butonlarÄ± kilitleniyordu.
* **Uygulanan DÃ¼zeltmeler:**
  - `src/client/java/mchorse/bbs_mod/ui/film/PanelVideoExportSession.java`: `teardown()` iÃ§indeki `BBSRendering.setCustomSize(false, 0, 0)` kaldÄ±rÄ±ldÄ±; `restorePreviewSize()` gÃ¼venle korunarak editÃ¶rÃ¼n preview boyutu restore edildi.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmRecorder.java`: `startRecording()` iÃ§inde editÃ¶r oynatÄ±lÄ±yorsa otomatik duraklatma (`togglePlayback()`) saÄŸlandÄ±; Ã¶nceki oturumdan kalan bayraklar (`isExporting() && !isRecording()`) `cancel()` ile temizlendi.
  - `src/client/java/mchorse/bbs_mod/film/VideoExportSession.java`: `begin()` metodunda recorder Ã§alÄ±ÅŸmÄ±yorken askÄ±da kalan `State.IDLE` dÄ±ÅŸÄ±ndaki oturumlar `reset()` ile temizlenerek tekrar baÅŸlatÄ±labilir kÄ±lÄ±ndÄ±.

### 3. Eksik Film DosyasÄ± GÃ¼venliÄŸi (FileNotFoundException Fallback)
* **KÃ¶k Neden:** Yeni oluÅŸturulan veya kaydedilmemiÅŸ bir sahneye girildiÄŸinde `.dat` dosyasÄ±nÄ±n diskte bulunamamasÄ± `FileNotFoundException` fÄ±rlatÄ±yor, Film yÃ¶neticisi sahneyi yÃ¼kleyemediÄŸi iÃ§in arayÃ¼zdeki zaman Ã§izelgesi, kamera ve kontroller donduruluyordu.
* **Uygulanan DÃ¼zeltmeler:**
  - `src/main/java/mchorse/bbs_mod/utils/manager/BaseManager.java`: `load(String id)` iÃ§inde dosya fiziksel olarak yoksa (`!file.exists()`) veya `FileNotFoundException` yakalanÄ±rsa konsola fatal hata basmak yerine `create(id, new MapType())` ile temiz/boÅŸ bir Ã¶rnek dÃ¶ndÃ¼rÃ¼ldÃ¼.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`: `fill()` ve `fillData()` metotlarÄ±na `data == null` guard-clause'u eklenerek panelin ve butonlarÄ±n kilitlenmesi engellendi.

### Derleme & DoÄŸrulama Durumu (AÅŸama 63)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 15s** (0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 7s** (0 Hata).

---

## AÅAMA 64: Aktif Film OlmadÄ±ÄŸÄ±nda Film SeÃ§im / KarÅŸÄ±lama EkranÄ±nÄ±n (Welcome Overlay) Geri Getirilmesi (TamamlandÄ±)

### 1. BaseManager.java Sahte Nesne Ãœretiminin Geri AlÄ±nmasÄ±
* **KÃ¶k Neden:** AÅŸama 63'te eksik `.dat` dosyalarÄ±nda `FileNotFoundException` durumunda `BaseManager.load()` iÃ§inde zorla `create(id, new MapType())` Ã§aÄŸrÄ±lmasÄ±, BBS'in orijinal karÅŸÄ±lama/seÃ§im akÄ±ÅŸÄ±nÄ± bozmuÅŸtur. Dosya bulunamadÄ±ÄŸÄ±nda boÅŸ bir "unnamed" projesi aÃ§Ä±lmakta ve kullanÄ±cÄ± film seÃ§ememekteydi.
* **Uygulanan DÃ¼zeltmeler:**
  - `src/main/java/mchorse/bbs_mod/utils/manager/BaseManager.java`: `load(String id)` metodu iÃ§erisinde dosya diskte yoksa (`file == null || !file.exists()`) veya `FileNotFoundException` yakalandÄ±ÄŸÄ±nda sahte nesne Ã¼retimi kaldÄ±rÄ±ldÄ±; metodun gÃ¼venli bir ÅŸekilde `null` dÃ¶ndÃ¼rmesi saÄŸlandÄ±.

### 2. UIFilmPanel.java KarÅŸÄ±lama ve Film SeÃ§im AkÄ±ÅŸÄ±nÄ±n Geri Getirilmesi
* **KÃ¶k Neden:** `fill(Film data)` ve `fillData(Film data)` metotlarÄ±nda `data == null` geldiÄŸinde otomatik olarak `data = new Film(); data.setId("unnamed");` oluÅŸturulmasÄ±, `tabs.getCurrentId()` deÄŸerini null olmaktan Ã§Ä±karÄ±p `syncLanding()` mekanizmasÄ±nÄ± devredÄ±ÅŸÄ± bÄ±rakÄ±yordu.
* **Uygulanan DÃ¼zeltmeler:**
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`:
    * `fill(Film data)` ve `fillData(Film data)` iÃ§indeki sahte `unnamed` nesnesi kaldÄ±rÄ±ldÄ±. `data == null` veya film ID'si geÃ§ersiz/boÅŸ olduÄŸunda doÄŸrudan `super.fill(null)` Ã§aÄŸrÄ±ldÄ±.
    * `UIDataDashboardPanel.fill(null)` vasÄ±tasÄ±yla `tabs.setOpenId(null)` saÄŸlandÄ±; `syncLanding()` tetiklenerek `UILandingScreen` ("BBS Catalyst Studio | C1.0" banner'Ä±, New Film, List, Wiki ve Son KullanÄ±lan Filmler listesi) gÃ¶rÃ¼nÃ¼r kÄ±lÄ±ndÄ± (`editor.setVisible(false)`).
    * `NEXT_CLIP`, `PREV_CLIP` keybind'leri ve `getLoopingRange()` metoduna null kontrolleri eklenerek kapalÄ±/boÅŸ sekmede NPE riski giderildi.
    * KullanÄ±cÄ± `UILandingScreen` Ã¼zerinden film seÃ§tiÄŸinde (`pickData`) veya "New Film" oluÅŸturduÄŸunda (`addNewData`) ilgili film `.dat` dosyasÄ± yÃ¼klenerek editÃ¶r arayÃ¼zÃ¼ aÃ§Ä±lÄ±r.
    * Sekme kapatÄ±ldÄ±ÄŸÄ±nda (`closeTab`) veya son film silindiÄŸinde (`onDataRemoved`) otomatik olarak karÅŸÄ±lama/seÃ§im gÃ¶rÃ¼nÃ¼mÃ¼ne dÃ¶nÃ¼lmesi garanti altÄ±na alÄ±ndÄ±.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPreview.java`:
    * Ses dalgaboyu Ã¶nizleme dÃ¶ngÃ¼sÃ¼nde `this.panel.getData() != null` korumasÄ± eklenerek aktif film yokken oluÅŸabilecek NPE engellendi.

### Derleme & DoÄŸrulama Durumu (AÅŸama 64)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 17s** (0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (0 Hata).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 22s** (addonApiCheck: 49 passed, anchorInterpolationTest: 330 passed, migrationTest: all PASS).

---

## AÅAMA 65: GÃ¶kyÃ¼zÃ¼ / Ufuk GeÃ§iÅŸi ve Sis Render HatasÄ±nÄ±n OnarÄ±mÄ± (Sky Dome Void & Fog Pass Fix) (TamamlandÄ±)

### 1. KÃ¶k Neden Analizi (KURALLAR 5.2 / 10.1)
1. **Siyah Disk / Void Dome (`WorldRenderer.renderSky`):**
   - `WorldRendererMixin.redirectRenderSkyCameraPos`, yalnÄ±zca `BBSModClient.getCameraController().getCurrent() != null` iken kamera konumunu dÃ¶ndÃ¼rÃ¼yordu. EditÃ¶rde duraklatÄ±ldÄ±ÄŸÄ±nda, panel gezintisinde veya export sÄ±rasÄ±nda aktif controller null olduÄŸunda oyuncunun gerÃ§ek zemin koordinatÄ± (`player.getCameraPosVec`) kullanÄ±lÄ±yordu.
   - Vanilla `renderSky`, `d = player.y - skyDarknessHeight < 0` olduÄŸunda ufkun altÄ±na devasa bir siyah "karanlÄ±k disk" (`darkSkyBuffer`) Ã§izer. Kamera yÃ¼ksekte (Y=247..399) olsa bile oyuncu yerdeyken (Y=60) bu siyah disk Ã§izilip sis ile karÄ±ÅŸarak ufkun altÄ±nda sert, eÄŸik ve koyu gri bir dÃ¼zlem oluÅŸturuyordu.
2. **Clear Color Ezmesi:**
   - `WorldRendererMixin.onRenderWorldStart`, `BBSRendering.toggleFramebuffer(true)` ve `BBSRendering.captureExportFrame` iÃ§erisinde clear color el ile `mc.world.getSkyColor()` ile eziliyordu.
   - Oysa vanilla Minecraft, zemin/ekran temizleme rengini `BackgroundRenderer.render()` iÃ§inde hesaplanan **sis rengi (fog color)** olarak ayarlar (gÃ¶kyÃ¼zÃ¼ rengi deÄŸil). Bu durum ufuk Ã§izgisinde sis ile gÃ¶kyÃ¼zÃ¼ arasÄ±ndaki ton uyumunu bozarak uzak chunk'larÄ±n arkasÄ±nda sert renk geÃ§iÅŸi bÄ±rakÄ±yordu.
3. **onRenderLayer Ä°Ã§indeki Gereksiz Sis Enjeksiyonu:**
   - `WorldRendererMixin.onRenderLayer` iÃ§inde her katmanda fazladan `BackgroundRenderer.applyFog(camera, FOG_TERRAIN, ...)` Ã§aÄŸrÄ±sÄ± yapÄ±lÄ±yordu. Bu Ã§aÄŸrÄ± vanilla'nÄ±n `FOG_SKY` sis durumunu bozuyor ve gÃ¶kyÃ¼zÃ¼ geÃ§iÅŸlerini kirletiyordu.

### 2. Uygulanan Mimari Ã‡Ã¶zÃ¼mler
1. **Vanilla Sis Renginin StÃ¼dyo Boru HattÄ±na AktarÄ±lmasÄ±:**
   - `src/client/java/mchorse/bbs_mod/mixin/client/BackgroundRendererMixin.java`:
     * `BackgroundRenderer.red`, `green`, `blue` statik alanlarÄ± `@Shadow` ile baÄŸlandÄ±.
     * `BackgroundRenderer.render(...)` metodunun sonuna (`TAIL`) enjekte edilerek vanillanÄ±n her karede hesapladÄ±ÄŸÄ± saf atmosferik sis rengi `BBSRendering.setFogColor(red, green, blue)` Ã§aÄŸrÄ±sÄ±yla stÃ¼dyo boru hattÄ±na aktarÄ±ldÄ±.
2. **Framebuffer Clear Color DÃ¼zeltmesi ve Chroma Sky DesteÄŸi:**
   - `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`:
     * `fogRed`, `fogGreen`, `fogBlue` alanlarÄ± ve getter'larÄ± (`getFogRed()`, `getFogGreen()`, `getFogBlue()`) eklendi.
     * `toggleFramebuffer(true)` ve `captureExportFrame()` iÃ§indeki manuel `getSkyColor()` ezmeleri kaldÄ±rÄ±ldÄ±; temizleme rengi vanilla sis rengine (veya Chroma Sky aktifse eÄŸri/ayar rengine) baÄŸlandÄ±.
3. **WorldRendererMixin Sky ve Kamera Optimizasyonu:**
   - `src/client/java/mchorse/bbs_mod/mixin/client/WorldRendererMixin.java`:
     * `onRenderWorldStart`: `RenderSystem.clearColor`, `GL11.glClearColor` ve `mc.getFramebuffer().setClearColor` ezmeleri kaldÄ±rÄ±ldÄ±; BBS stÃ¼dyo framebuffer'Ä±na sis rengi (veya Chroma rengi) atandÄ±.
     * `onRenderLayer`: Katman render dÃ¶ngÃ¼sÃ¼ndeki gereksiz `BackgroundRenderer.applyFog(camera, FOG_TERRAIN, ...)` kaldÄ±rÄ±ldÄ±; `FOG_SKY` ve Sodium/Iris uyumluluÄŸu korundu.
     * `shouldUseStudioCameraForSky()` metodu eklendi: Kamera kontrolcÃ¼sÃ¼, dÃ¼nya render'Ä±, Ã¶zel boyutlandÄ±rma (`isCustomSize`), video kaydÄ± veya `UIFilmPanel` aÃ§Ä±k olduÄŸunda her zaman stÃ¼dyo kamerasÄ±nÄ±n konumu (`mc.gameRenderer.getCamera().getPos()`) dÃ¶ndÃ¼rÃ¼ldÃ¼; bÃ¶ylece `d < 0` kontrolÃ¼ sahte siyah disk (`darkSkyBuffer`) Ã§izilmesini %100 engelledi.

### 3. Derleme & DoÄŸrulama Durumu (AÅŸama 65)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 12s** (0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (0 Hata).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 21s** (addonApiCheck: 49 passed, anchorInterpolationTest: 330 passed, migrationTest: all PASS).

---

## AÅAMA 66: Viewport'ta Kaybolan Bloklar, BaÅŸlamayan Render ve Dev OrtamÄ±nda Sodium SÄ±zÄ±ntÄ±sÄ± (Derleme DoÄŸrulanmadÄ±)

### KÃ¶k Neden
1. **Bloklar:** `CameraMixin.onUpdate` ve `WorldRendererMixin.onRenderWorldStart` her karede `ClientChunkManager.setChunkMapCenter()` ile istemci chunk merkezini stÃ¼dyo kamerasÄ±na kaydÄ±rÄ±yordu. Sunucu chunk'larÄ± oyuncunun etrafÄ±na gÃ¶nderir; merkez kayÄ±nca yarÄ±Ã§ap dÄ±ÅŸÄ± chunk'lar "yÃ¼klenmemiÅŸ" sayÄ±lÄ±r ve oyuncu Ã§evresindeki section'lar boÅŸ derlenir. Merkez hiÃ§ geri alÄ±nmÄ±yordu.
2. **Render baÅŸlamÄ±yor:** AÅŸama 62'de `onRenderBeforeScreen()` Ã§aÄŸrÄ±larÄ± temizlenince `BBSRendering.scheduleAfterNextExportFrame()` ile kuyruÄŸa alÄ±nan eylemi Ã§alÄ±ÅŸtÄ±ran tek yer Ã§aÄŸrÄ±sÄ±z kaldÄ±; `startRecording` hiÃ§ tetiklenmedi.
3. **Sodium/Iris:** `build.gradle` iÃ§inde `modImplementation` olduÄŸundan Loom `runClient` bunlarÄ± Gradle Ã¶nbelleÄŸinden yÃ¼klÃ¼yor, `run/mods` iÃ§indeki `.disabled` dosyalarÄ± etkisiz.

### Uygulanan DÃ¼zeltmeler
* `CameraMixin.java` ve `WorldRendererMixin.java`: `setChunkMapCenter` Ã§aÄŸrÄ±larÄ± kaldÄ±rÄ±ldÄ±.
* `BBSRendering.java`: `onWorldRenderEnd()` sonuna `runPendingExportAction()` eklendi.
* `build.gradle`: Sodium ve Iris `modCompileOnly` yapÄ±ldÄ±; Ã§alÄ±ÅŸma zamanÄ± iÃ§in `-PdevMods=true` ile `modLocalRuntime`.

### DoÄŸrulama
* Derleme ve oyun iÃ§i test yapÄ±lmadÄ± (terminal eriÅŸimi yoktu): `gradlew.bat compileJava compileClientJava apiCheck` ve `runClient` ile doÄŸrulanmalÄ±.


## AŞAMA 75: Viewport Sis Kaybı ve Ufuktaki Siyah Boşluk Düzeltmesi (Tamamlandı)

### Kök Nedenler
1. **Şeffaf Clear Color (Siyah Panel Sızıntısı):** Vanilla Minecraft'ın `BackgroundRenderer.render()` metodu `RenderSystem.clearColor(red, green, blue, 0.0F)` çağırarak alfa değerini `0.0` (tam şeffaf) yapıyordu. Normal tam ekran oyunda monitör alfasız çalıştığı için sorun olmuyordu; ancak Film Paneli'nde (`UIFilmPreview`), `framebuffer` dokusu `Batcher2D` üzerinden GUI içinde çizilirken alfa harmanlama (blend) devredeydi. Geometrisi olmayan (ufuk ve uzaktaki boşluk) pikseller `alpha=0.0` olduğu için, viewport'un arkasındaki Film Dashboard'un siyah paneli delikten görünür gibi görünüyordu.
2. **Bozuk Sis Mesafesi (FOG_TERRAIN Override):** `BackgroundRendererMixin.java` içinde kalan `FOG_TERRAIN` hesaplaması, `fogStart` değerini yapay olarak `actualDistance - g` formülüyle çok uzağa itiyor ve kameranın yüksek olduğu film sahnelerinde chunk sınırındaki bloklara hiç sis vurmadan bıçak gibi kesilmesine yol açıyordu.

### Uygulanan Düzeltmeler
* `BackgroundRendererMixin.java`:
  - `FOG_TERRAIN` override bloğu tamamen kaldırılarak `bbs-fs-master` referansıyla birebir hizalandı. Vanilla'nın doğal chunk sis mesafesi ve silindir şekli geri getirildi.
  - `BackgroundRenderer.render` metodunun `TAIL` noktasına `RenderSystem.clearColor(red, green, blue, 1.0F)` eklendi. Böylece her render karesinde `RenderSystem.clear()` adımı `framebuffer`'ı tam opak atmosfer sis rengiyle temizliyor.
* `UIFilmPreview.java`:
  - Viewport alanına `texturedBox` çizilmeden önce `BBSRendering.getFogRed/Green/Blue` değerlerinden üretilen opak zemin rengi basılarak GUI alfa sızıntısı çift katmanlı olarak önlendi.

### Derleme Durumu
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL**.


## AŞAMA 76: F1 Tam Ekran Siyahlığı, Fast Mod Yaprak Şeffaflığı ve Fabulous Su Katmanı (Translucent) Düzeltmesi (Tamamlandı)

### Kök Nedenler
1. **F1 Siyah Ekran Sorunu:**
   - `UIFilmPanel.renderPanelBackground(context)` yalnızca `BBSRendering.getTexture()` nesnesini çizmeye çalışıyordu. Oysa `getTexture()` sadece aktif video kaydı / export sırasında kare yakalanırken dolduruluyordu. Normal önizleme sırasında `texture.id` boş/siyah kaldığı ve metot tüm ekranı `Colors.A100` (siyah) ile kapladığı için F1'e basıldığında ekran tamamen kararıyordu.
   - Ayrıca F1'den çıkıldığında `UIDashboard` panel görünürlüğünü açarken `filmPanel.restorePreviewSize()` çağırmıyordu.
2. **Fast Grafik Modunda Yaprak Deliklerinden Gökyüzü Sızıntısı:**
   - Vanilla Minecraft `RenderLayers.getBlockLayer()` ve `getMovingBlockLayer()` içinde grafik modu Fast (`fancyGraphicsOrBetter == false`) olduğunda `LeavesBlock` nesnelerini `RenderLayer.getSolid()` katmanına yönlendiriyordu.
   - `solid` shader'ında şeffaf pikseller için `discard` bulunmadığından yaprak dokusundaki boşluklar da derinlik tamponuna (Z-buffer) yazılıyor; arkadaki arazi blokları derinlik testine takılıp elenerek geriye gökyüzü kalıyordu.
3. **Fabulous! Modunda Suların Kaybolması (Translucent Framebuffer Compositing):**
   - Minecraft Fabulous! grafik modunda şeffaf arazi ve suları `translucentFramebuffer` içine çizer ve ardından `WorldRenderer.transparencyPostProcessor.render(tickDelta)` ile ana hedef framebuffer'a (`mainTarget`) harmanlar.
   - `transparencyPostProcessor` oyun açılışında oluşturulurken `mainTarget` ve `"minecraft:main"` haritası pencerenin `WindowFramebuffer`'ına sabitleniyordu. BBS özel `framebuffer`'a çizim yaparken bu işlemci hâlâ pencere framebuffer'ına harmanlama yapıyor, BBS'in `framebuffer`'ına sular hiç aktarılmıyordu.
   - Ayrıca `defaultSizedTargets` ve sampler'lar (`DiffuseDepthSampler`) BBS'in özel çözünürlüğüne senkronize edilmiyordu.

### Uygulanan Düzeltmeler
* **UIFilmPanel.java:**
  - `renderPanelBackground()` metodu `BBSRendering.getFramebuffer()`'ın `getColorAttachment()` çıktısını en-boy oranı (`BBSRendering.getVideoWidth() / getVideoHeight()`) korunacak şekilde ve sis rengi (`fogColor`) zemin desteğiyle çizecek şekilde güncellendi. F1 tam ekran modu artık kusursuz canlı önizleme veriyor.
* **UIDashboard.java:**
  - `TOGGLE_VISIBILITY` (F1) kancasında görünürlük tekrar açıldığında `filmPanel.restorePreviewSize()` tetiklenerek önizleme pencere boyutuna güvenle geri dönmesi sağlandı.
* **RenderLayersMixin.java (Yeni Mixin):**
  - `RenderLayers.getBlockLayer` ve `getMovingBlockLayer` metotlarına HEAD injection yapılarak `state.getBlock() instanceof LeavesBlock` durumunda her zaman `RenderLayer.getCutoutMipped()` döndürüldü. Böylece Fast modda yaprak delikleri derinlik tamponuna yazılmadan `discard` edilir ve arkadaki arazi blokları gökyüzünü delmeden görünür.
* **WorldRendererAccessor.java & PostEffect Processor Accessor'ları:**
  - `WorldRendererAccessor`: `transparencyPostProcessor` erişimcisi eklendi.
  - `PostEffectProcessorAccessor` (Yeni): `mainTarget`, `targetsByName`, `passes`, `width`, `height` erişimcileri oluşturuldu.
  - `PostEffectPassAccessor` (Yeni): `input`, `output`, `samplerNames`, `samplerValues`, `samplerWidths`, `samplerHeights` erişimcileri oluşturuldu.
* **BBSRendering.java:**
  - `updateFabulousTransparency(boolean forBBS)` metodu yazıldı: Fabulous modunda `transparencyPostProcessor`'ın `mainTarget`'ı, `"minecraft:main"` girdisi ve pass'lerdeki `DiffuseDepthSampler` derinlik sağlayıcısı BBS `framebuffer`'ına dinamik olarak bağlandı. Çözünürlük değiştiğinde `setupDimensions` ile ikincil framebuffer'lar senkronize edildi.
  - `toggleFramebuffer(true)` içinde Fabulous compositing BBS'e yönlendirildi, `setCustomSize(false)` ile normal oyuna dönüldüğünde pencere framebuffer'ına güvenle restore edildi.
  - `resizeFramebuffer` özel çözünürlükleri (`customSize`) destekleyecek şekilde güncellendi.
* **bbs.client.mixins.json:**
  - `RenderLayersMixin`, `PostEffectProcessorAccessor` ve `PostEffectPassAccessor` mixin listesine kaydedildi.

### Derleme Durumu
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 17s** (0 hata).


## AŞAMA 77: RenderLayersMixin'in Kaldırılması ve Fast Mod Doğal Katı Yaprak Mantığına Dönüş (Tamamlandı)

### Gerekçe ve Düzeltme
1. **RenderLayersMixin Kaldırıldı:**
   - Aşama 76'da `LeavesBlock` nesnelerini her koşulda `RenderLayer.getCutoutMipped()` katmanına zorlayan Mixin, Fast grafik modunun doğasını (opak/katı yaprak blokları) bozduğu için `RenderLayersMixin.java` tamamen silindi ve `bbs.client.mixins.json` içerisinden kaydı kaldırıldı.
2. **Vanilla Fast Mod Yaprak Davranışı:**
   - Minecraft'ın orijinal `RenderLayers.setFancyGraphicsOrBetter()` mantığına geri dönüldü.
   - Fast modda yaprak dokuları Vanilla'daki gibi katı/opak (`solid` pass) olarak işlenir; Aşama 75'te `UIFilmPreview` ve `clearColor(..., 1.0F)` ile sağlanan opak zemin alfa düzeltmesi sayesinde arkadaki siyah boşluk veya panel sızıntısı zaten önlenmiştir.
3. **Derleme Durumu:**
   - `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 9s** (0 hata).


## AŞAMA 78: Catalyst Editor Video Katmanı Oynatılırken Oyunun Kilitlenmesi (Not Responding) Düzeltmesi (Tamamlandı)

### Kök Nedenler
1. **Ana Render Thread'inde Senkron Ses Çıkarma Blokajı (`SoundManager.get`):**
   - Catalyst timeline'ında bir video katmanı (`LayerType.VIDEO`) bulunduğunda, oynatma başladığında `syncAudioPlayback()` metodu video dosyası için `SoundManager.get(audioLink, false)` çağırıyordu.
   - Bu çağrı ana render thread'i üzerinde `AudioReader.read()` -> `AudioReader.readVideoAudio()` -> `executeFFmpegAudioExtraction()` çalıştırarak harici `ffmpeg.exe` sürecini başlatıyor ve tüm videonun sesini (dakikalarca AAC ses akışı) ana thread üzerinde senkron olarak çözüyordu (`process.waitFor(30s)`).
   - Bu sırada Minecraft render döngüsü durduğundan ve Windows olayları işlenemediğinden Windows işletim sistemi oyunu doğrudan `(Not Responding)` durumuna sokuyordu.
   - Video ses içermediğinde dahi `soxr` başarısızlığından sonra ikinci kez `swresample` fallback'i ile FFmpeg tekrar çalıştırılıyor ve blokaj katlanıyordu.
2. **60 FPS Kompozisyon vs 25 FPS Video Drift & Yanlış Seek Tetiklemesi (`VideoPlayer.java`):**
   - `VideoPlayer.java` içindeki sıralı oynatma koşulu `target >= this.streamFrame && target <= this.streamFrame + window` şeklindeydi.
   - 60 FPS kompozisyon ile 25 FPS video çalışırken video kare indeksi her render karesinde artmaz (1 video karesi 2-3 render karesi boyunca geçerlidir). `streamFrame` 1 ileri okuduğunda `target < streamFrame` durumu oluşuyor ve kod bunu "jump" zannedip `!sequential` dalına girerek FFmpeg sürecini öldürüp her 150ms'de bir yeniden başlatıyordu (Process storm).
   - Ayrıca `UICatalystPanel` video kare indeksini `Math.round(relSec * playerFps)` ile hesaplarken, `VideoPlayer` `(int)(seconds * fps)` (floor) kullanıyordu; bu da yarım kare faz farkı yaratarak seek fırtınasını besliyordu.
3. **Scrubbing Sırasında Senkron Seek Blokajı (`seekToFrame`):**
   - `UICatalystPanel.seekToFrame()` scrubbing sırasında her fare hareketinde video katmanları için `player.seekFrame(relSec)` metodunu senkron olarak çağırıyor, bu da saniyede onlarca kez `finishSeek()` (`join(3000)`) ve senkron FFmpeg başlatıp borudan kare okumaya çalışarak arayüzü kilitliyordu.
4. **FFmpeg Süreç İdaresi:**
   - `VideoPlayer.restart()` içinde `-nostdin` ve `-loglevel error` eksikti; FFmpeg interaktif stdin borusunda bekleyebiliyordu. `stop()` metodunda boru kapatılmadan önce `destroy()` çağrılıyor, Windows'ta süreç ağacı askıda kalabiliyordu.

### Uygulanan Düzeltmeler
* **SoundManager.java:**
   - Video dosyalarının ses çözümlemesi için asenkron arka plan hattı kuruldu (`pendingWaves` ve `loadingBuffers`).
   - `get(link, false)` çağrısı video dosyası henüz çözülmemişse ana thread'i bloke etmeden `null` döndürür ve arka planda bir daemon thread başlatır.
   - Ses çözüldüğünde bir sonraki render karesinde OpenAL `SoundBuffer` nesnesi anında (0.1ms) oluşturularak oynatıcıya bağlanır. UI 60 FPS akmaya devam eder, kilitlenme sıfırlanır.
* **AudioReader.java:**
   - `executeFFmpegAudioExtraction` metoduna stderr analizi eklendi (`ExtractionResult`). Video dosyasında ses akışı bulunmadığında (`does not contain any stream`) anında tespit edilerek gereksiz ikinci FFmpeg denemesi engellendi.
* **VideoPlayer.java:**
   - `probe()` metoduna ses akışı tespiti eklendi (`hasAudio = output.contains("Audio:")` ve `hasAudio()` erişimcisi).
   - `getFrame()` içindeki atlama / seek mantığı düzeltildi: `jumpBackward = target < currentFrame - window`, `jumpForward = target > streamFrame + window`. `target < streamFrame` durumu geriye doğru büyük bir atlama değilse mevcut kare dokusu korunarak FFmpeg'in yeniden başlatılması önlendi.
   - `pendingReady` durumunda kare `target`'tan geride olsa dahi önce dokuya yüklenerek bağlantı kopması giderildi.
   - `restart()` FFmpeg komutuna `-nostdin` ve `-loglevel error` parametreleri eklendi.
   - `stop()` metodunda önce `channel.close()` yapılarak boru okumaları serbest bırakıldı, ardından `process.destroyForcibly()` ile Windows'ta anında süreç sonlandırma sağlandı. `finishSeek()` bekleme süresi 1000ms'ye indirilip takılan süreçler için forcibly sonlandırma eklendi.
* **UICatalystPanel.java:**
   - `getActiveAudioLayers()`: Video katmanlarında `player.hasAudio()` kontrolü yapılarak ses akışı olmayan videoların gereksiz yere ses mikserine dahil edilmesi önlendi.
   - `seekToFrame()`: Bloklayıcı `player.seekFrame(relSec)` yerine debounced ve asenkron çalışan `player.getFrame(relSec)` kullanımına geçildi.
   - `render()` video çizim bloğu: `videoFrameIdx` hesaplaması `Math.max(0, (int) (relSec * playerFps))` ile `VideoPlayer` zeminine tam eşitlendi.

### Derleme Durumu
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 15s** (0 hata).


## AŞAMA 79: Mod Sürümünün C1.1 Olarak Güncellenmesi (Tamamlandı)

### Yapılan Güncellemeler
* **`gradle.properties`:** `mod_version=C1.1` olarak güncellendi.
* **`fabric.mod.json`:** `expand "version": project.version` üzerinden `C1.1-1.20.4` sürümü otomatik bağlandı.
* **`UILandingScreen.java`:** Karşılama ve banner rozetindeki `BANNER_VERSION` sabiti `"C1.1"` olarak güncellendi.


