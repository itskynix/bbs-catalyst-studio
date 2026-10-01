# BBS Mod - Geliştirme Durumu (DEV_STATE)

**Proje:** Blockbuster Studio Catalyst (BBS CS) - Fabric 1.20.4 Port (`bbs-cs`)  
**Tarih:** 30 Eylül 2026  
**Son Tamamlanan Aşama:** 64 — Aktif Film Olmadığında Film Seçim / Karşılama Ekranının (Welcome Overlay) Geri Getirilmesi  
*(Aşama 57: BBS Hub Pazaryeri, Aşama 58: Video & Audio Ses Kalitesi, Aşama 59: Mod Kimliği ve Sürüm C1.0)*:

1. **FAZ 0: Crash, DoS ve Veri Kaybı Açıkları (%100):**
   - `[P0-1] DEFECT-18`: `PacketCrusher.java` parçalı paketlerde 32MB ve 4000 parça tavan sınırı ile DoS/OOM koruması.
   - `[P0-2] DEFECT-19`: `ServerNetwork.java` oyuncu başına izole `incomingCrushers` haritası ve bağlantı kopma temizliği.
   - `[P0-3] DEFECT-20`: `ServerNetwork.java` sonlu sayı denetimleri (`Double.isFinite`, `Float.isFinite`), dünya sınırları kırpması, 2.000.000 blok yapı hacim limiti ve eksik `arePanelsAllowed` kontrolleri.
   - `[P0-4] DEFECT-17`: `FolderManager.java` & `BaseManager.java` path traversal (`..` ve dizin dışı) engellemesi ve null-güvenliği.
   - `[P0-5] DEFECT-02`: `CatalystProject.load()` bozuk projeleri silmeden `.corrupt-<timestamp>` yedeği alma; `CatalystProjectManager` hasarlı proje takibi ve koruması.
   - `[P0-6] DEFECT-01`: `CatalystProjectManager.getProjectFile()` güvenli slug + 8 karakterlik deterministik SHA-256 hash ve Windows ayrılmış aygıt adı (`CON`, `PRN` vb.) koruması.

2. **FAZ 1: Animasyon & Timeline Kusurları (%100):**
   - `[P1-1] DEFECT-03 & 13`: `CatalystLayer.computeEffective(frame)` saf hesaplama; oynatma sırasında taban değer kirlenmesi ve kalıcı değer sapması çözüldü.
   - `[P1-2] DEFECT-05`: `splitChannelsAt()` ile dilimleme sınırlarında interpolasyonlu keyframe enjeksiyonu (`cutSelected()`).
   - `[P1-3] DEFECT-06`: `copyChannelsTo()` ile katman çoğaltmada 8 kanalın tamamının derin kopyalanması (`duplicateSelected()`).
   - `[P1-4] DEFECT-07`: `lastClickedChannels` ve `deleteSelectedKeyframes()` ile doğrudan seçili keyframe silme.
   - `[P1-5] DEFECT-08`: Göreli keyframe sürükleme (`dragStartTicks`), birden çok kanal keyframe'inin tek noktaya çökmesi önlendi.
   - `[P1-6] DEFECT-09`: Önizleme tuvalinde katman sürükleme bittiğinde animasyonlu katmana otomatik keyframe yazılması (`updatePropertyValue`).
   - `[P1-7] DEFECT-10`: `addKeyframeSingle()` ve Alt+Click ile X ve Y eksenlerinin birbirinden tamamen bağımsız keyframelenebilmesi.
   - `[P1-8] DEFECT-14`: `jumpToKeyframe(next)` J/K navigasyonunun seçili katman ve tıklanan kanala odaklanarak gereksiz zıplamaları önlemesi.
   - `[P1-9] DEFECT-11`: Negatif ölçekleme (`Math.copySign`), işaret korumalı viewport culling, bounding-box ve fare etkileşim dönüşümleri.
   - `[P1-10] DEFECT-12`: Metin katmanı ölçeğinin matrise uygulanması ve unscaled kutu boyutları ile çift çarpım oluşmadan orantılı render edilmesi.

3. **FAZ 2: Türkçe İşletim Sistemi (`Locale.ROOT`) Güvenceleri (%100):**
   - `[P2-1]` `MolangParser.java`: `expression.toLowerCase(Locale.ROOT)` ile MoLang parser çökmesi önlendi.
   - `[P2-2]` `EnumUtils.java`: `e.name().toLowerCase(Locale.ROOT)` ile enum anahtar bozulmaları engellendi.
   - `[P2-3]` `AudioReader.java`: `link.path.toLowerCase(Locale.ROOT)` ile büyük harfli `.AVI` / `.MP4` uzantı tanıma hatası çözüldü.
   - `[P2-4]` `CatalystLayer.java`: `s.toUpperCase(Locale.ROOT)` ile `"audio"` ve `"image"` katman türlerinin bozulması engellendi.

4. **FAZ 3: Kod Hijyeni, Bounded Buffers & Thread-Safety (%100):**
   - `[P3-1] DEFECT-21`: `AudioReader.java` FFmpeg 30 saniye işlem zaman aşımı (`destroyForcibly`) ve 256MB tavan tampon sınırı.
   - `[P3-2] DEFECT-22`: `CatalystLayer.java` arka plan worker thread'lerinde yüklenen önbelleklere `volatile` güvencesi.
   - `[P3-3] DEFECT-23`: `ServerNetwork.java` ve `FFMpegUtils.java` boş catch bloklarına `BBSMod.LOGGER` hata ve uyarı logları.
   - `[OPSIYONEL] DEFECT-16`: `BBSMod.java` `BBS_EDITING_RULE` gamerule varsayılanı `false` yapılarak çok oyunculu sunucularda izinsiz istemci erişimi engellendi.

5. **Render HUD Canlı Önizleme İzolasyonu & MP4 Tavan Siyah Bar Çözümü (`UICatalystPanel.java`):**
   - Canlı önizleme tuvaline `GL11.glEnable(GL11.GL_SCISSOR_TEST)` ve GUI ölçekleme çarpanlı fiziksel `glScissor` uygulandı; katman animasyonlarının HUD butonlarının veya ekran sınırlarının dışına taşması engellendi.
   - Pencereli modda ana framebuffer yüksekliği ile export çözünürlüğü arasındaki fark `int offsetY = Math.max(0, screenH - this.exportHeight)` ile tavana hizalandı; `readH < this.exportHeight` durumunda `MemoryUtil.memCopy` ile satırlar doldurularak üretilen videolardaki üst siyah şerit tamamen giderildi.

6. **Catalyst Ses Export Hattı İyileştirmesi (Stereo Desteği, PCM Clipping Koruması ve Codec Uyumluluğu):**
   - **Gerçek 2-Kanal 48kHz Stereo Miksaj:** `AudioRenderer.renderAudio` 32-bit kayan noktalı `mixLeft` ve `mixRight` sonsuz dinamik tavanlı tamponlarla yeniden yapılandırıldı. Stereo kaynaklar çift kanallı ayrımını korurken mono kaynaklar her iki kanala faz çakışmasız eşit dağıtıldı.
   - **Analog Tanh Soft-Clipping & Peak Normalization:** Tepe genlik taraması ve aşırı yüklenmelerde dinamik tavan koruması (`maxPeak > 1.25F`) getirildi. 16-bit PCM short sınırında 0.85 eşiğini aşan sinyaller yumuşak hiperbolik tanjant eğrisi ile satüre edilerek dijital clipping ve boğukluk önlendi.
   - **Çok Kanallı Dilimleme:** `Wave.excerpt(from, to)` metodu ile stereo kanal ve bayt hizalamaları bozulmadan zaman aralığı dilimlemesi sağlandı.
   - **FFmpeg Codec & Container Uyumluluğu:** AAC ve MP3 için yüksek kaliteli bitrate (varsayılan 320 kbps Studio Master), Opus codec'i MP4 container ile kullanıldığında `-strict -2` parametresi otomatik entegre edildi.

7. **Video Layer ve Audio Layer Ses Kalitesi Düşüklüğü / Resampling Bozulması Onarımı (Aşama 58):**
   - **Video Ses Demuxer & OpenAL Frekans Uyumu (`AudioReader.java`):** Sabit `-ar 44100` ve çift resampling döngüsü kaldırıldı. OpenAL aygıtının natif frekansı (`ALC_FREQUENCY`, varsayılan 48 kHz) otomatik tespit edilip FFmpeg'e bağlandı. 28-bit Soxr sinc resampler ve triangular dither fallback hattı kuruldu; 16 KB pipe tamponuyla underrun ve cızırtı riski sıfırlandı.
   - **Kayıpsız Bit Derinliği & 44.1 / 48 kHz Korunması (`Wave.java` & `SoundBuffer.java`):** `Wave.normalize()` metodunun 44.1 kHz ses dosyalarını gereksiz yere 48 kHz'e zorlayarak aşındırması engellendi. `SoundBuffer` yalnızca 48 kHz üstündeki (96k/192k) büyük dosyaları kontrollü indirger; standart 44.1 kHz ve 48 kHz sesler OpenAL donanım mikserine doğrudan ve kayıpsız beslenir.
   - **Band-Limited Blackman-Nuttall Windowed Sinc Resampler (`Wave.java`):** Tizleri körelten ve anti-aliasing filtresi olmayan 4-noktalı Catmull-Rom cubic spline yerine, >90dB stopband zayıflatmalı ve Nyquist sınırını aşan frekansları matematiksel olarak süzerek yok eden band-limited windowed sinc resampler yazıldı.
   - **OpenAL 2D Stereo Doğrudan Geçiş (`SoundPlayer.java`):** `configure2DStereo()` ve merkez pan modunda yanlışlıkla açık olan `AL_SOURCE_SPATIALIZE_SOFT` değeri `AL_FALSE` yapıldı. OpenAL Soft'un stereo kanalları kulak pinna filtreleriyle boğuklaştıran 3D HRTF filtrelemesi devreden çıkarıldı.
   - **Catalyst Playback Senkronizasyon & Scrubbing Düzeltmesi (`UICatalystPanel.java`):** Master clock ses oynatıcısının integer kare yuvarlama sapması yüzünden her render karesinde mikro-seek yapması önlendi. `scrubAudio` metodu sesin natif örnekleme hızına (`wave.sampleRate`) göre dinamik adımlama kazanarak 44.1 kHz seslerin timeline kaydırılırken pitch kayması düzeltildi.

8. **Mod Kimliği, Sürüm ve Metadata Güncellemesi - BBS CS C1.0 (Aşama 59):**
   - **`fabric.mod.json`:** Mod adı "BBS CS (Catalyst Studio)", açıklaması "Built for machinima creators, animators, and filmmakers. BBS CS upgrades the core studio with the Catalyst NLE timeline, lossless studio-grade audio engine, smart replay LOD culling, and major render stability fixes." olarak güncellendi.
   - **Yazarlar & Katkıcılar:** "McHorse", "Wemppy (BBS FS)" ve "Kynix" yazarlar listesine tescillendi.
   - **`gradle.properties`:** Mod sürümü `mod_version=C1.0` olarak güncellendi.
   - **Oyun İçi UI & Branding (`UILandingScreen.java`):** Giriş ekranı sürüm metni ve afiş başlığı `\u00a7lBBS CS` olarak güncellendi.
   - **ModMenu Yerelleştirmesi (`en_us.json`, `tr_tr.json`):** `modmenu.nameTranslation.bbs` ve `modmenu.descriptionTranslation.bbs` anahtarları Türkçe ve İngilizce olarak eklendi.

**Derleme sonucu:** `BUILD SUCCESSFUL` — 0 hata, `apiCheck` onaylı, temiz derleme.



## 1. Mimari Genel Bakış

BBS moduna, DaVinci Resolve ve modern prodüksiyon araçlarından esinlenen iki devrimsel sistem entegre edilmiştir:
1. **DaVinci Resolve "Deliver" Render & Export Pipeline:** Donanım hızlandırmalı (NVENC, AMF, QSV, ProRes, HQ GIF), tam ekran Cinema Monitor HUD, dinamik başlatıcı bağımsız çıktı dizini, doğrudan hedef export FBO'sundan okuma yapan 4-bayt RGBA `glReadPixels` motoru, katı bitrate kilitleme (`-minrate`, `-maxrate`, `-bufsize`, `-cbr_padding`), Render Queue iş yöneticisi, In/Out ve Custom Range aralık seçimi, canlı SMPTE monitörü ve atmosferik mesafe sisi (terrain fog) garantisi.
2. **Canlı Çok Kanallı Replay Kaydı (Live Multi-Track Replay Recording) & DaVinci Kurgu Kısayolları:** Jilet kesim (`C`), çoklu katman kesimi (Multi-Track Razor Cut), dalgalı kırpma / ripple trim (`Q` & `E`) track izolasyonu ve No-Overlap çakışma önleyici koruma, kesim noktası gezintisi (`Yukarı / Aşağı Ok`), klip susturma/mute toggle (`D`), otomatik In/Out mark clip (`X`), In/Out işaretleme (`I`, `O`, `Alt + X`), görsel cetvel bandı, cetvel tabanlı scrubbing, klipsiz alana sürükleyerek doğrudan sarı kutu çoklu seçimi (Marquee Selection) ve tek tıkla seçim kaldırma (Deselect) mimarisi.
3. **Pro Camera & Optik Kadrajlama Motoru:** Gerçek sinema lensleri (35mm eşdeğeri focal length dönüşümü), Dutch angle / bağımsız roll rotasyonu, hedef odaklı dinamik Dolly Zoom (Vertigo) algoritması ve DoF odak mesafesi kanalı.

---

## 2. Son Yapılan Kritik Düzeltmeler ve Geliştirmeler (17. Aşama)

### A. 'ProCameraClip' ve Optik Matematik Motoru (`ProCameraClip.java`)
* **35mm Sensör Eşdeğeri ve Çift Yönlü FOV Dönüşümü:**
  - Minecraft dikey FOV'u ile 35mm full frame sensör yüksekliği ($h = 24.0\text{ mm}$) arasında çift yönlü matematiksel dönüşüm kuruldu:
    * $\text{FOV} = 2 \cdot \arctan\left(\frac{h}{2 \cdot f}\right) \cdot \frac{180}{\pi}$
    * $f = \frac{h}{2 \cdot \tan\left(\frac{\text{FOV} \cdot \pi}{360}\right)}$
  - Hazır sinema lens presetleri (24mm, 35mm, 50mm, 85mm) ve serbest mm trackpad'i ile anında optik kadraj oluşturma.
* **Bağımsız Roll (Dutch Angle) Kanalı:**
  - `Position.angle.roll` üzerinden `Camera.updateView().rotateZ(...)` matrisine direkt enjekte edilen bağımsız roll keyframe kanalı.
* **Dahili Dolly Zoom (Vertigo) Modu ("Lock Subject Size"):**
  - Kamera ileri/geri hareket ederken süjenin ekrandaki boyutunu sabit tutan dinamik alan derinliği/arka plan perspektif akıtma algoritması:
    * $d_t \cdot \tan\left(\frac{\text{FOV}_t}{2}\right) = d_0 \cdot \tan\left(\frac{\text{FOV}_0}{2}\right) \implies \text{FOV}_t = 2 \cdot \arctan\left(\frac{d_0}{d_t} \cdot \tan\left(\frac{\text{FOV}_0}{2}\right)\right)$
  - Tek tıkla crosshair hedefinden veya blok/entity üzerinden süje koordinatını kilitleme (`RayTracing.rayTraceEntity`).
* **Focus Distance (Odak Mesafesi) Kanalı:**
  - `focus_distance` keyframe kanalı `Position` ve `Camera` sınıflarına (`focusDistance`) bağlandı; ilerideki Depth of Field (DoF) ve shader entegrasyonlarına tam uyumlu altyapı hazırlandı.

### B. Pro Kamera Arayüzü (`UIProCameraClip.java`)
* **Lens & Odak Uzaklığı Bölümü:** `[24mm]`, `[35mm]`, `[50mm]`, `[85mm]` hızlı preset butonları, serbest odak uzaklığı (`focalLength` mm) ve FOV trackpad'leri (biri değiştiğinde diğeri eşzamanlı güncellenir).
* **Dutch Angle / Roll Bölümü:** `[-45°]`, `[-15°]`, `[0°]`, `[+15°]`, `[+45°]` hazır açı butonları ve hassas roll trackpad'i.
* **Dolly Zoom (Vertigo) Bölümü:** "Lock Subject Size" toggle anahtarı, "Pick Subject from Crosshair" butonu ve süje mesafesi trackpad'i.
* **Focus Distance:** Blok cinsinden odak mesafesi ayarı.
* **Keyframe Düzenleyici Entegrasyonu:** Tüm kanalları (`x`, `y`, `z`, `yaw`, `pitch`, `roll`, `fov`, `distance`, `focus_distance`) grafik eğri editöründe görselleştirip düzenleme.

### C. Kayıt ve Yerelleştirme (Registration & L10n)
* `BBSMod.java`: `factoryCameraClips` fabrikasına `Link.bbs("pro_camera")` olarak `ProCameraClip.class` tescil edildi (`Icons.CAMERA`, `0xe056fd`).
* `UIClip.java`: `UIProCameraClip::new` UI fabrikasına kaydedildi.
* `TrackStyle.java`: `focus_distance` kanalı açık mavi (`0x54a0ff`) iz rengiyle tanımlandı.
* `en_us.json` & `tr_tr.json`: `bbs.ui.camera.clips.bbs:pro_camera` çevirileri tanımlandı ("Pro Camera" / "Pro Kamera").

---

## 3. Son Yapılan Kritik Düzeltmeler (18. ve 19. Aşama)

### Pro Camera Clip UIKeyframeEditor Dizi Taşması (ArrayIndexOutOfBoundsException) Düzeltmesi (18. Aşama)
* **Hata / Kök Neden:** `KeyframeClip` yapısında varsayılan 8 kanal bulunurken, `ProCameraClip` ile 9. kanal (`focus_distance` - index 8) eklendiğinde `UIKeyframeEditor.COLORS` dizisinin sabit 8 elemanlı (`length = 8`) olması nedeniyle `Index 8 out of bounds for length 8` fırlatılıyordu.
* **UIKeyframeEditor.java:**
  - `COLORS` palet dizisi 16 elemana genişletildi ve index 8'e `0x54a0ff` (focus distance / soft blue) kanalı entegre edildi.
  - `getColor(int index)` ve `getChannelColor(KeyframeChannel channel, int index)` yardımcı fonksiyonları eklenerek hem `TrackStyle.color(channel.getId())` ile dinamik stil eşleştirme sağlandı hem de olası taşmalara karşı modulo/sınır koruması getirildi.
  - `setClip(KeyframeClip clip)` metodunda `null` kontrolleri ve dinamik kanal sayısı (`clip.channels.length`) iterasyonu güvenli hale getirildi.

### UIProCameraClip UIKeyframeClip Kalıtımı ve Senkronizasyon Temizliği (19. Aşama)
* **Hedef / Yapılanlar:**
  - `UIProCameraClip` doğrudan `UIKeyframeClip` taban sınıfından türetildi (`public class UIProCameraClip extends UIKeyframeClip`).
  - Orijinal `UIKeyframeClip` içindeki `fillData()`, keyframe editörü bağlama, cetvel render'ı (`rulerRenderer`), timeline imleç senkronizasyonu ve klip süresi (`duration`) hesaplama mantığı doğrudan devralındı (`super.fillData()`).
  - `getCurrentClipTick()` içerisine `Math.max(0F, Math.min(dur, tick))` sınır koruması eklendi; playhead klip başlangıcından önceyken (-5 tick vb.) negatif cursor ve hatalı keyframe yazımı tamamen engellendi.
  - Sağ paneldeki 35mm Lens presetleri (`24mm`, `35mm`, `50mm`, `85mm`), Roll Dutch Angle butonları, Dolly Zoom (Vertigo) ve Focus Distance kontrolleri taban panelin üzerine temizce eklendi.
  - Kod fazlalığı ve kopyalanmış editör başlatıcıları temizlendi.

### UIClipsPanel Panel Açılma Garantisi & UIClips Hovered Clip Kenar Boyutlandırma (Resize/Trim) İzolasyonu (20. Aşama)
* **UIClipsPanel.java:**
  - `pickClip(Clip clip)` içerisine doğrudan `if (clip instanceof ProCameraClip proCameraClip)` tip kontrolü eklendi; Pro Camera klibine tıklandığı an sağ panelde `UIProCameraClip` paneli kesin olarak başlatılıp `fillData()` tetiklenerek arayüz takılması önlendi.
* **UIClips.java:**
  - `getClipUnder(UIContext context, int mouseX, int mouseY)` yardımcı metodu eklendi: Fare sol tuşuna basıldığında önce kenar handle'ları (sol/sağ trim sınırları), ardından görsel klip alanı taranarak farenin tam altındaki hedef klip doğrudan tespit ediliyor.
  - Kenar tutma ve boyutlandırma (`grabMode != 0`) işlemi `selectedClip` listesinden tamamen izole edildi; yalnızca farenin yakaladığı klip (`Collections.singletonList(clip)`) `grabbedClips` ve `grabbedData`'ya aktarıldı.
  - Pro Camera seçiliyken alttaki veya diğer katmanlardaki bir klip kenarından çekildiğinde Pro Camera'nın yanlışlıkla uzayıp kısalması sorunu tamamen çözüldü.

### Pro Camera Tıklanma / Hit Detection, Sağ Tık Menüsü & Constructor NPE Çözümü (21. Aşama)
* **Kök Neden Tespiti:**
  1. `UIProCameraClip` sınıfında `public final ProCameraClip proClip;` alanı `super(...)` sonrasında atanıyordu; fakat Java'da üst sınıf kurucusu (`UIClip`) doğrudan alt sınıfın override ettiği `registerUI()` metodunu çağırıyordu. Bu anda `this.proClip` henüz `null` olduğu için `proClip.lockSubjectSize` çağrısında `NullPointerException` patlıyor, `UIClipsPanel.pickClip` içindeki sessiz `try-catch` bu hatayı yutarak panelin oluşturulmasını iptal ediyordu.
  2. `UIClips.java` içindeki `this.context(...)` menü oluşturucu mekanizması sağ tıklanan klibi otomatik seçmiyordu (`hasSelected` yalnızca önceden seçilmiş bir klip varsa aktif oluyordu). Eğer klibe daha önce sol tıklanamadıysa sağ tıklandığında klibe özel menü yerine boş timeline genel menüsü ("Reorganize clips", "Record microphone...") açılıyordu.
  3. `UIClips.java` sol tık metodunda (`handleLeftClick`) daha önce seçilmiş klip ile tıklanan klip aynı referans olduğunda `delegate.pickClip` çağrısı atlanabiliyordu.
* **Uygulanan Düzeltmeler:**
  - `UIProCameraClip.java`:
    * Hatalı `final ProCameraClip proClip` alanı kaldırılarak yerine doğrudan üst sınıfın hazır `this.clip` alanını cast eden `public ProCameraClip getProClip() { return (ProCameraClip) this.clip; }` metodu eklendi. Null referans çökmesi tamamen giderildi.
  - `UIClips.java`:
    * Sağ tık context menüsü (`this.context(...)`) tetiklendiğinde `getClipUnder(context, mouseX, mouseY)` ile farenin altındaki klip tespit edildi. Eğer bir klibin üzerine sağ tıklandıysa anında `this.setSelected(clipUnderMouse)` ve `this.delegate.pickClip(clipUnderMouse)` çalıştırılarak klibe özel içerik menüsünün (Sil, Kes, Klip Dönüştür vb.) garanti olarak açılması sağlandı.
    * Sol tık kurgusu temizlendi; tıklanan klip doğrudan ve koşulsuz olarak `this.setSelected(clip)` ve `this.delegate.pickClip(clip)` ile aktif hale getirildi.
    * `addConverters` fonksiyonuna `clip`, `data` ve `data.converters` null kontrolleri eklenerek dönüştürücü menü çağrıları zırhlandı.
  - `UIClipsPanel.java`:
    * Klip paneli zaten mevcutsa ve görünürlük durumunda kapalı kaldıysa `attachPropertiesPanel` ve `setVisible(true)` ile sağ panelin ekrana yeniden bağlanması ve `fillData()` çağrısının sorunsuz çalışması garantiye alındı.

### Minecraft Pelerin (Cape) & Pelerinli Elytra Entegrasyonu (22. Aşama)
* **Veri Modeli ve Keyframe Desteği (`ModelForm.java` & `TrackCatalog.java`):**
  - `ModelForm` sınıfına `hasCape` (`ValueBoolean`, varsayılan `false`) ve `capeTexture` (`ValueLink`, varsayılan `bbs:textures/default_cape.png`) alanları eklendi.
  - Bu alanlar `Form`'un özellik havuzuna tescillendiğinden otomatik olarak NBT serileştirme/deserileştirme, geri alma (Undo/Redo) ve `TrackCatalog` üzerinden Replay zaman çizelgesinde dinamik keyframe kanalı (açma/kapama, pelerin dokusu değiştirme) desteği kazandı.
* **Akıcı Fizik Motoru & Vanilla Konumlandırma (`CapeRenderer.java`):**
  - Vanilla Minecraft `CapeFeatureRenderer` matematik modeli temel alındı:
    * Yürüme, koşma, eğilme (sneak) ve süzülme durumlarında pelerinin geriye ve yana savrulma açıları (`forwardSwing`, `sideSwing`, `pitchOffset`) hesaplandı.
    * Titreme (stutter) olmaması için tick tabanlı ayrık adımlar yerine render anında `partialTicks` (transition) ile `prevCapeX/Y/Z` ve mevcut pozisyon arasında kesintisiz lerp interpolasyonu sağlandı.
    * Pozisyon gövdenin (torso/body) arka üst merkezine Vanilla standart mesafesiyle (`translate(0, 0, 0.125F)`) bağlandı.
  - BBS `TextureManager`'da bulunan herhangi bir özel dokunun (BBS Link) Minecraft `TextureManager`'a anında bağlanabilmesi için dinamik doku tescil köprüsü kuruldu.
* **Elytra Uyumu & Hibrit Doku Sistemi (`ArmorRenderer.java` & `ModelFormRenderer.java`):**
  - Aktörde `hasCape = true` iken göğüslük slotunda Elytra (`Items.ELYTRA`) takılıysa:
    * Sırt pelerini otomatik olarak gizlendi (Vanilla davranışı).
    * Seçili olan `capeTexture`, doğrudan Elytra kanat dokusu olarak kullanıldı (`renderElytra`).
* **Kullanıcı Arayüzü (UI) & Yerelleştirme (`UIModelFormPanel.java`, `TrackStyle.java`, L10n):**
  - `UIModelFormPanel` içerisine "Cape" ("Pelerin") bölümü eklendi:
    * "Enable Cape" (Pelerin Aç/Kapa) toggle butonu.
    * "Pick Cape Texture..." doku seçici butonu (`UITexturePicker`).
  - Replay zaman çizelgesinde `has_cape` ve `cape_texture` kanalları için özel stil (renk `0xe84118` ve ikonlar) tanımlandı.
  - `en_us.json` ve `tr_tr.json` dil dosyalarına çeviriler entegre edildi.
  - Varsayılan yüksek kaliteli 64x32 pelerin dokusu (`default_cape.png`) oluşturulup `assets/bbs/textures/` dizinine eklendi.

### Pelerin (Cape) UV Standardı, Sırt Konumlandırma & Akıcı Fizik (Stutter Giderme) ve Replay Kayıt/Oynatma Hotfix (24. Aşama)
* **Kök Neden & Mimari Düzeltmeler:**
  1. **Cape Konumlandırma ve 64x32 UV Hizalaması (`CapeRenderer.java`):**
     - Standart Minecraft pelerin doku haritası (64x32) için `cuboid(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, Dilation.NONE, 1.0F, 0.5F)` geometrisi `root.createPart(64, 64)` ile tescillendi; UV böleni kusursuz (64, 32) oranına oturtuldu.
     - Pelerin gövdenin arka üst kenarına Vanilla `0.125F` pozitif Z ofsetiyle yerleştirildi. Dış desenin (`12..21 x, 1..16 y`) arkaya (kameraya), iç astarın ise sırta bakması sağlandı.
  2. **Kare Hızından Bağımsız Yaylanma Fiziği (Fluid Spring Damping - 0 Stutter):**
     - 20-tick kısıtlamasından bağımsız olarak, her render karesinde `dt` hesaplandı ve yay sönümlemeli (`MathHelper.lerp(dt * 10.0F, currentPitch, targetPitch)`) akıcı açı hesabı yapıldı.
     - Sneak durumu ani sıçramak yerine yumuşak geçişle (`currentSneak`) süzüldü; teleport ve zaman çizelgesi scrubbing atlamalarında ani hız patlamaları filtrelendi.
  3. **Replay Kayıt (Sağ Alt) ve Oynatma (Sağ Ctrl) Çakışma Önleme:**
     - `BBSModClient.java` içinde `keyPlayFilm()`: Replay kaydı aktifken (`recorder != null`, `MultiTrackReplaySession.isActive()` veya `panel.isRecording()`) Sağ Ctrl basışları yutuldu; arka planda film oynatılması engellendi.
     - `BBSModClient.java` içinde `keyRecordReplay()`: Kayıt durdurulduğunda `panel.stopPlayback()` ve `Films.stopFilm()` çağrılarak oynatma döngüsü ve runner durumu sıfırlandı.
     - `UIFilmPanel.java`: `stopPlayback()` ve `isRecording()` yardımcı metodları eklendi.
     - `Films.java`: `isPlaying(filmId)` kontrolü getirilerek film zaten oynatılıyorsa temiz bir toggle mantığıyla durdurulması sağlandı; `unfreeze(filmId)` ile donmuş editör kontrolcüleri temizlenip mükerrer `FirstPersonFilmController` oluşumu tamamen engellendi.

### Pelerin (Cape) Birinci Şahıs (FPS/Hand) İzolasyonu & UV Ön-Arka Düzeltmesi (25. Aşama)
* **Kök Neden & Mimari Düzeltmeler:**
  1. **Birinci Şahıs (FPS) El/Kol Görünümünde Pelerin Gizleme:**
     - `ModelFormRenderer.java`:
       * `renderModel()` içinde `hasCape` ve `hasEquipment` bloklarına `!this.renderingArm` koşulu eklendi. Böylece `renderFirstPersonHand()` çağrıldığında pelerin veya gövde zırhı çizim döngüsüne hiç girilmemesi sağlandı.
       * `renderCape()` metodunun en başına `if (this.renderingArm) return;` ve yerel oyuncu birinci şahıs perspektifindeyken (`mc.options.getPerspective().isFirstPerson() && mcEntity == mc.player`) pelerin çizimini tamamen engelleyen güvenlik kontrolü eklendi.
     - `CapeRenderer.java`:
       * `renderCape()` metoduna aynı şekilde birinci şahıs perspektif kontrolü eklenerek yalnızca üçüncü şahıs modunda gövde render edildiğinde pelerinin çizilmesi garanti altına alındı.
  2. **UV Ön-Arka Terslik Düzeltmesi (64x32 Dokuda Arka Sırt Deseni):**
     - `CapeRenderer.java`:
       * Standart Minecraft pelerin doku haritasında (64x32) koyu/yıldızlı dış sırt deseni (`x: 12..21, y: 1..16`) küpün NORTH (-Z) yüzeyindedir. Pelerin modeline `matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - sideSwing / 2.0F))` uygulanarak dış desenin kameraya (arkaya) bakması, açık renkli iç astarın (`x: 1..10, y: 1..16`) ise doğrudan aktörün sırtına bakması sağlandı.
       * 180° Y çevirmesi pitch dönüşünden (`totalPitch`) sonra uygulandığı için ileri koşarken pelerinin geriye doğru havaya kalkma fiziği ve Vanilla uyumlu savrulma yönü kusursuz şekilde korundu.

### Wavey Capes ("waveycapes") Aktör Simülasyon Köprüsü & Kumaş Render Düzeltmesi (27. Aşama)
* **Kök Neden:**
  - Wavey Capes 1.20.4'te pelerin çizimini yapan asıl sınıf `dev.tr7zw.waveycapes.render.CustomCapeRenderer` ve `VanillaCapeRenderer`'dır.
  - Mod `AbstractClientPlayerEntity` ve `CapeHolder` arayüzlerine bağlıdır; BBS `ActorEntity` bu sınıfları doğrudan genişletmediğinden önceki denemede çağrı `ClassCastException`/`NullPointerException` nedeniyle sessizce fallback katı küpüne düşmüştür.
* **Uygulanan Mimari Çözüm (`CapeRenderer.java`):**
  - **Doğru Sınıf ve Metot Entegrasyonu:**
    * `dev.tr7zw.waveycapes.render.CustomCapeRenderer` ve `VanillaCapeRenderer` reflection ile dinamik olarak oluşturuldu.
    * `PlayerWrapper(AbstractClientPlayerEntity)` sarmalayıcısı `mc.player` referansıyla başlatıldı.
  - **Aktör Bazlı Simülasyon Köprüsü (Actor-specific Simulation Swapping):**
    * Her BBS aktörü için `dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer` arayüzünü dinamik olarak implemente eden bir `Proxy` (`InvocationHandler`) oluşturuldu; aktörün anlık koordinatları, `prev` pozisyonları, gövde açısı (`bodyYaw`) ve eğilme (`isSneaking`) durumu Wavey Capes simülasyonuna aktarıldı.
    * Her aktöre özel `BasicSimulation` oluşturulup `WeakHashMap` içinde saklandı.
    * Çizim anında `mc.player` üzerindeki `CapeHolder`'ın simülasyonu o aktörün simülasyonuyla geçici olarak takas edildi, `simulate(proxy)` ile kumaş fizik adımı yürütüldü, `customCapeRenderer.render(...)` ile 16 parçalı dalgalanan pelerin çizildi ve `finally` bloğunda `mc.player`'ın orijinal simülasyonu geri yüklendi.
  - **Hata Tespiti & Katı Küp Engelleme:**
    * Olası istisnaları yakalamak için bir defaya mahsus `t.printStackTrace()` loglaması eklendi.
    * `isWaveyCapesLoaded()` aktif olduğunda, fallback olarak katı blok çizilmesi engellendi (`return`).

### Wavey Capes Simülasyon Frekansı (20 TPS), Matris Temizliği ve Koordinat Güvenliği (28. Aşama)
* **Kök Neden & Mimari Düzeltmeler:**
  1. **Simülasyon Frekansı Ayrıştırması (20 TPS Tick vs Render Frame):**
     - Wavey Capes Verlet kumaş simülasyonu Minecraft'ın 20 TPS oyun döngüsü için kalibre edilmiştir. Yüksek yenileme hızına sahip monitörlerde (60/120/144 FPS) her çizim karesinde `simulate()` çağrıldığında fizik motoruna saniyede yüzlerce kez hız ekleniyor ve pelerin kontrolsüzce havaya fırlayıp takılı kalıyordu.
     - `simulate(proxy)` çağrısı `renderWaveyCape()` render akışından tamamen çıkarıldı.
     - Fizik adımı `CapeRenderer.tick(IEntity)` metodu içerisindeki `stepSimulation(entity)` fonksiyonuna taşındı. Scrubbing/duraklatılmış Replay durumlarında dahi fizik motorunu stabil tutmak için 50 ms zaman damgası denetleyicisi (`lastSimTimes`) eklendi.
     - `renderWaveyCape()` içinde yalnızca matris interpolasyonu ve `customCapeRenderer.render(...)` çağrısı bırakıldı.
  2. **Matris Temizliği:**
     - Wavey Capes `modifyPoseStack` içerisinde pelerin kumaşının kıvrılma açısını ve dalgalanmasını kendisi hesapladığından, render öncesinde uygulanan ek pitch/yaw dönüşümleri kaldırıldı.
     - Matris sadece sırt hizalama ve eğilme ofseti (`sneakY`, `sneakZ`) ile Wavey Capes'e temiz bir şekilde teslim edildi.
  3. **Koordinat Güvenliği & Işınlanma/Delta Koruması (Proxy):**
     - `MinecraftPlayer` proxy'sinden gelen pozisyonlar `sanitize()` ile `NaN` ve `Infinity` durumlarına karşı korundu.
     - Aktör 20 bloktan fazla anlık konum değiştirdiğinde (ışınlanma / Replay scrubber sıçraması) `prev` koordinatlar anlık koordinatlara eşitlenerek simülasyonun patlaması engellendi.

### Wavey Capes Kumaş Simülasyonu Veri Besleme (getXCloak / capeX lag), 20 TPS Tick İzolasyonu ve Matris Temizliği (29. Aşama)
* **Kök Neden & Mimari Düzeltmeler:**
  1. **Modun Simülasyonuna (BasicSimulation) Veri Besleme Hatası:**
     - Wavey Capes kumaş eğrilik ve dalgalanma vektörünü (`Vector3 movement`) hesaplarken `getXCloak() - getX()` ve `getZCloak() - getZ()` gecikme (lag) vektörünü ana momentum kaynağı olarak kullanır (`d3 * sin(bodyYaw) + d5 * -cos(bodyYaw)`).
     - Önceki proxy uygulamasında `getXCloak()` doğrudan `x`, `getZCloak()` ise doğrudan `z` döndürdüğü için aradaki fark daima sıfır kalmış; `movement.x` 0 olduğundan Verlet simülasyonundaki 16 point hiçbir zaman arkaya doğru bükülmeyip düz bir tahta gibi takılı kalmıştır.
     - `ActorCapeState` sınıfı eklenerek Vanilla Minecraft pelerin gecikme fiziği (`capeX += (x - capeX) * 0.25`, `capeZ += (z - capeZ) * 0.25`) aktör bazında uygulandı.
     - Fiziksel yer değiştirmenin az olduğu veya yerinde yürüme/Replay cutscene durumlarında bacak animasyonunun (`limbSpeed`) kumaşa rüzgar/momentum etkisi yapabilmesi için `extraLag` hesaplanarak `getXCloak()` ve `getZCloak()` değerlerine yedirildi.
  2. **Simülasyonun Çalışma Zamanı (simulate çağrısı 20 TPS):**
     - `renderWaveyCape()` içerisindeki zaman damgalı `stepSimulation` çağrısı tamamen kaldırıldı.
     - `simulate(...)` çağrısı yalnızca `CapeRenderer.tick(IEntity)` metodu altında (20 TPS oyun tick'inde) çalışacak şekilde izole edildi.
     - `renderWaveyCape()` içinde sadece Wavey Capes'in bükülmüş 16 node'u `transition` (`partialTicks`) ile enterpole edilerek akıcı şekilde çizildi.
  3. **Ekstra Matris Rotasyon ve Ofsetlerinin Temizlenmesi:**
     - Wavey Capes'in `CustomCapeRenderer.modifyPoseStackSimulation` metodu pelerin gövde arka pivotunu (`translate(0, 0, 0.125)`), sırt dönüşünü (`YP 180°`), rüzgar salınımını (`XP 6° + naturalWind`) ve her bir segmentin bükülme açısını (`XP -getRotation(...)`) tamamen kendi içinde hesapladığından, `renderWaveyCape` içerisindeki tüm manuel pitch, rotasyon ve sneak ofsetleri kaldırıldı; matris temiz bir şekilde aktarıldı.

### BBS Ses Motoru Yüksek Kalite WAV/PCM Çözücü, Catmull-Rom Resampler ve MP3 Format Desteği (30. Aşama)
* **Kök Neden & Mimari Düzeltmeler:**
  1. **Yüksek Kalite WAV ve PCM Çözücü / Normalleştirici (`Wave.java` & `WaveReader.java`):**
     - 24-bit, 32-bit INT ve 32-bit/64-bit IEEE float WAV dosyalarının çözülmesinde yaşanan 8-bit distorsiyon, cızırtı ve aşırı clipping sorununun temel nedeni tespit edildi: `Wave.convertTo16()` metodunda Little Endian WAV baytları Big Endian `ByteBuffer` ile okunarak bayt sırası ters çevriliyor, 24-bit PCM'de işaret biti (sign bit) ve LSB/MSB yer değiştiriyor, 32-bit float değerleri ise üs taşmasıyla $\pm 10^{38}$ aralığına fırlayıp maksimum seviyede kare dalga (white noise/clipping) oluşturuyordu.
     - `WaveReader.java` güncellenerek `WAVE_FORMAT_EXTENSIBLE` (0xFFFE / 65534) ve `WAVE_FORMAT_IEEE_FLOAT` (3) başlıkları eklendi; GUID üzerinden PCM/Float ayrımı yapıldı ve büyük `data` bloklarının stream'den eksiksiz okunması sağlandı.
     - `Wave.java` içinde `convertTo16()` doğrudan Little Endian bayt ayrıştırması ile yeniden yazıldı: 8-bit unsigned PCM, 16-bit signed PCM, 24-bit signed PCM (`>> 8`), 32-bit signed INT PCM (`>> 16`), 32-bit IEEE float (`Float.intBitsToFloat`) ve 64-bit float değerleri $[-1.0, 1.0]$ normalize aralığına alınıp NaN korumalı olarak 16-bit Signed PCM'e dönüştürüldü.
     - Çok kanallı stüdyo kayıtları için (`numChannels > 2`, örn. 5.1 surround) `downmixToStereo()` metodu eklendi.
  2. **Stüdyo Örnekleme Hızı İçin Catmull-Rom Kübik Resampler (`Wave.resample`):**
     - 48 kHz üzeri stüdyo kayıtları (88.2 kHz, 96 kHz, 192 kHz) için `Catmull-Rom` kübik eğri interpolasyonu uygulayan `resample(targetRate)` algoritması eklendi. 96 kHz/192 kHz kayıtlar 48 kHz'e, 88.2 kHz kayıtlar 44.1 kHz'e sıfır aliasing ve faz bozulması ile dönüştürülür.
     - `Wave.normalize()` metodu ile bit derinliği, kanal sayısı ve örnekleme hızı tek çağrıda OpenAL uyumlu standarda (`AL_FORMAT_STEREO16` / `MONO16`, $\le 48000$ Hz) getirildi.
  3. **Yerel MP3 Format Desteği (`Mp3Reader.java`):**
     - JLayer (`javazoom:jlayer:1.0.1`) kütüphanesi projeye dahil edildi (`build.gradle`).
     - `mchorse.bbs_mod.audio.mp3.Mp3Reader` sınıfı yazılarak MP3 akışlarının doğrudan 16-bit Signed PCM `Wave` nesnesine çözülmesi sağlandı.
     - `AudioReader.java`, `SoundManager.java`, `SoundBuffer.java`, `UISoundOverlayPanel.java` ve `UIAudioEditor.java` sınıflarında `.mp3` dosya uzantı filtreleri eklendi; tüm çözülen seslerin OpenAL buffer'ına yüklenmeden önce otomatik `normalize()` edilmesi garanti altına alındı.

### Catalyst Editor UI Panel İskeleti - BBS Catalyst Studio (31. Aşama)
* **Kök Neden & Mimari Uygulama:**
  1. **Dashboard Görev Çubuğu Entegrasyonu (`UIDashboard.java`):**
     - Dashboard alt araç çubuğunda (`registerPanels()`) "Filmler" (`film`) panelinin hemen sağına yeni `catalyst` panel adımı kaydedildi:
       `this.buildStep("catalyst", () -> this.panels.registerPanel(new UICatalystPanel(this), UIKeys.CATALYST_TITLE, Icons.FIVE_STAR));`
     - İkon olarak şık beş köşeli yıldız (`Icons.FIVE_STAR` - render/star/magic) seçildi.
     - Tooltip başlığı için `UIKeys.CATALYST_TITLE` oluşturuldu; `en_us.json` ve `tr_tr.json` dil dosyalarına `"Catalyst Editor"` tanımları eklendi.
  2. **Catalyst Editor Panel İskeleti (`UICatalystPanel.java`):**
     - `mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel` sınıfı `UIDashboardPanel` türetilerek oluşturuldu.
     - **Üst Araç Çubuğu (`topBar`):** Başlık etiketi, Play/Pause toggle (`Icons.PLAY`/`Icons.PAUSE`), Katman Ekle (`Icons.ADD`), İmleçte Böl (`Icons.CUT`), Proje Ayarları (`Icons.GEAR`) ve Tam Ekran (`Icons.FULLSCREEN`) eylem butonları eklendi.
     - **Önizleme Alanı (`previewArea`):** Üst alanda (%53 yükseklik) ortalanmış 16:9 oranlı kompozisyon tuvali (canvas guide), üçte bir kuralı/güvenli alan çizgileri, ortalanmış rozet ("CATALYST VIEWPORT • 1920 × 1080 • 60 FPS") ve alt zaman kodu göstergesi yerleştirildi.
     - **Alt Kompozisyon Çerçevesi (`bottomArea`):** Kalan alt alan (%47 yükseklik) iki modüler bölüme ayrıldı:
       * Sol Katmanlar Paneli (`layersContainer`): Başlık çubuğu, dikey kaydırılabilir katman listesi (`layersList`), örnek kompozisyon kanalları (Video Plate, 3D Replay Actor, Audio Ambience, FX/Color).
       * Sağ Zaman Çizelgesi (`timelineContainer`): Üst cetvel/frame tick işaretleri (`timelineHeader`), çok kanallı klip şeritleri grid'i ve kırmızı oynatma kafası (`playhead`) imleci.

### Catalyst Editor Proje Yönetim Ekranı ve Üst Sekme Sistemi (32. Aşama)
* **Kök Neden & Mimari Uygulama:**
  1. **Catalyst Proje Veri Modeli ve Yöneticisi (`CatalystProject.java` & `CatalystProjectManager.java`):**
     - Projelerin adı, FPS (varsayılan 60), süresi (varsayılan 300 frame), çözünürlüğü (1920×1080) ve zaman damgalarını barındıran `CatalystProject` sınıfı oluşturuldu.
     - `CatalystProjectManager` ile `.minecraft/bbs/assets/catalyst_projects` dizininde otomatik oluşturma, JSON bazlı yükleme, kaydetme ve silme operasyonları sağlandı.
  2. **Üst Sekme (Tab) Sistemi (`UICatalystTab.java` & `UICatalystPanel.java`):**
     - Sol üst araç çubuğuna BBS arayüz standartlarına uygun özel sekme butonları entegre edildi:
       * **Projeler** (`Icons.FOLDER` - "Projects")
       * **Editör** (`Icons.FILM` - "Composition Editor")
     - Aktif sekme vurgusu (birincil tema rengi ve alt çizgi) ile dinamik durum yönetimi (`CatalystTab.PROJECTS` vs `CatalystTab.EDITOR`) kuruldu.
     - Aktif proje başlık rozeti (`activeProjectLabel`) sekme çubuğunun yanına yerleştirildi; seçili projenin adı, FPS ve süresi gerçek zamanlı yansıtıldı.
  3. **Proje Karşılama ve Seçim Ekranı (Project Manager View):**
     - Aktif seçili proje olmadığında (veya oyun açılışında panele ilk girildiğinde) panel doğrudan "Projeler" karşılama ekranını açar.
     - **Sol Bölme:** Kayıtlı projeleri listeleyen `UICatalystProjectList`, üstünde başlık ve sayaç, "+ Yeni Proje" (`Icons.ADD`), "Klasörü Aç" (`Icons.FOLDER`) ve "Yenile" (`Icons.REFRESH`) araçları.
       * Projeye tek tıklandığında sağdaki yapılandırma formu dolgulanır.
       * Çift tıklandığında (veya Enter tuşunda) proje hemen aktif edilip otomatik olarak "Editör" sekmesine geçilir.
     - **Sağ Bölme:** Proje oluşturma ve düzenleme kartı (`nameInput`, `fpsInput` [1-240], `durationInput` [1-100000], `widthInput`, `heightInput`), "Projeyi Aç / Editöre Geç" birincil butonu, "Formu Temizle" ve "Projeyi Sil" aksiyonları.
     - İstenildiği zaman üstteki "Projeler" sekmesine tıklanarak başka bir projeye geçilebilir veya yeni proje oluşturulabilir.

### Catalyst Editor — Saniye Süresi Girişi, AE Kompozisyon Sekme Şeridi ve Katman Modeli (33. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Saniye Bazlı Süre Girişi (`UICatalystPanel.java`):**
     - Proje oluşturma formundaki süre alanı frame → saniye giriş formatına güncellendi (`durationSecondsInput`, aralık 0.1s–3600s, varsayılan 5.0s).
     - Girilen saniyeye karşılık otomatik hesaplanan kare sayısı (`saniye × fps`) bilgi etiketi (`durationCalcLabel`) olarak formun altında gösterildi (örn. `"5.0s (300f @ 60 FPS)"`).
     - `CatalystProject` oluşturulurken ve kaydedilirken hem `durationSeconds` hem de karşılık gelen `durationFrames` (`seconds × fps`) alanları tutarlı biçimde saklandı.
     - `UICatalystProjectList` satır alt başlığı `"X.Xs (Yf @ Z FPS) • W×H"` formatına dönüştürüldü.
  2. **Katman ve Kompozisyon Veri Modeli (`CatalystLayer.java`, `CatalystComposition.java`):**
     - `CatalystLayer`: katman adı, türü, rengi, görünürlük ve kilit durumu ile `startFrame`/`duration` kanallarını barındıran yeni veri sınıfı. `MapType` serileştirmesi `putBool`/`getBool` kullanılarak düzeltildi.
     - `CatalystComposition`: kendi bağımsız katman listesine, FPS, süre ve oynatma kafası (`playhead`) alanlarına sahip kompozisyon nesnesi. `setupDefaultLayers()` ile 4 varsayılan katman (Video, 3D Replay, Audio, FX/Color) oluşturuluyor.
     - `CatalystProject` güncellendi: `durationSeconds`, `List<CatalystComposition> compositions`, `activeCompositionIndex` eklendi; `ensureCompositions()`, `addComposition()`, `removeComposition()` yardımcı metotları yazıldı.
  3. **AE Tarzı Kompozisyon Sekme Şeridi (`UICatalystCompTab.java`, `UICatalystPanel.java`):**
     - `UICatalystCompTab extends UIClickable<UICatalystCompTab>`: After Effects benzeri tek composition sekmesi. Film şeridi ikonu, kompozisyon adı, aktif sekme üst çizgisi (birincil renk) ve ×(kapat) butonu (hover'da kırmızı). Yakın butonu `mouseClicked` override yerine `overClose` boolean alanı (her karede `renderSkin` içinde güncellenir) + callback lambda ile handle edildi; `UIElement.mouseClicked()` `final` kısıtlaması aşıldı.
     - `UICatalystPanel`'e 22px yüksekliğinde yatay `compTabStrip` (önizleme alt sınırı ile `bottomArea` arası), sağında `+Comp` (`Icons.ADD`) butonu eklendi. `rebuildCompTabs()` aktif projenin kompozisyonlarına göre sekmeleri yeniden oluşturuyor; `setActiveComposition(index)` ile sekme geçişi yönetiliyor.
  4. **UIKeys & Dil Dosyaları:**
     - `UIKeys.CATALYST_COMP_NEW`, `UIKeys.CATALYST_DURATION` (güncellendi) eklendi.
     - `en_us.json` ve `tr_tr.json` güncellendi: `"catalyst.duration"` → `"Duration (Seconds)"`, `"catalyst.comp.new"` → `"New Comp"`.

---

### Catalyst Editor — UI Çakışma Düzeltmesi, LayerType Enum, Katman Ekleme Menüsü ve Timeline Scrubbing (34. Aşama)
* **Uygulanan Değişiklikler:**
  1. **UI Üst Üste Binme Düzeltmesi (`UICatalystPanel.java`):**
     - `projectsView` elemanı artık tüm editör bileşenlerinin (preview, compTabStrip, bottomArea) **üstüne** ekleniyor. Bu sayede Projects sekmesindeyken arkadaki editör metinleri ve tuval tamamen gizleniyor.
     - `projectsView`'a en üst child olarak `deepSurface()` rengiyle tam alan kaplayan opak `UIRenderable` zemin eklendi.
     - `UIElement` eklenme sırası: `topBar → previewArea → compTabStrip → bottomArea → projectsView` (projectsView en üstte render ediliyor).
  2. **`CatalystLayer` Güncellendi — `id` Alanı ve `LayerType` Enum:**
     - `id` alanı: `UUID.randomUUID().toString().substring(0, 8)` ile otomatik kısa ID üretimi.
     - `LayerType` iç enum: `SOLID`, `SCENE`, `AUDIO`, `NULL`.
     - `LayerType.fromString()` geriye dönük uyumlu: eski `"VIDEO"`, `"ACTOR"`, `"EFFECT"` string değerlerini doğru enum türüne eşliyor.
     - `LayerType.defaultColor()` her tür için uygun renk döndürüyor.
     - `CatalystLayer.solid(name)` ve `CatalystLayer.scene(name)` hızlı factory metotları eklendi.
     - `CatalystComposition.setupDefaultLayers()` enum kullanacak şekilde güncellendi.
  3. **Katman Ekleme Menüsü (`UICatalystPanel.openAddLayerMenu()`):**
     - Üstteki `+` (Katman Ekle) butonuna tıklandığında panelin üstüne geçici bir inline popup overlay açılıyor.
     - İki seçenek: **"+ Solid Layer"** (düz renk) ve **"+ Scene/Film Layer"** (BBS sahne/film).
     - Seçildiğinde katman aktif kompozisyona ekleniyor, proje kaydediliyor, overlay kapanıyor.
  4. **Timeline Playback & Scrubbing (`UICatalystPanel`):**
     - **Play/Pause:** `playPauseButton`'a tıklandığında `isPlaying` toggle olur. `tickPlayback(comp)` preview render'ının her frame'inde çağrılır; `System.currentTimeMillis()` delta ile `fps`'e göre `currentFrame` ilerletilir. Son frame'e ulaşınca loop sıfırlanır.
     - **Timeline scrubbing (tıklama + sürükleme):** `timelineTracks` anonymous sınıfında:
       * `subMouseClicked` → sol tık: `isScrubbing = true`, `currentFrame` mouse X'e göre hesaplanır, play durdurulur.
       * `subMouseReleased` → `isScrubbing = false`.
       * `render` başında: `isScrubbing` aktifse mouse X → frame dönüşümü her karede yenilenir (drag scrub).
     - **Kırmızı playhead çizgisi** timeline tracks ve header'da `currentFrame * 4` piksel ofsette çiziliyor.
     - **Timecode** preview canvas altında `00:00:SS:FF [Frame X / Y]` formatında gösteriliyor.
     - `mouseXToFrame(mouseX, area, comp)` yardımcı metodu: 4 px/frame ölçeği, `[0, duration-1]` aralığında sınırlandırılmış.
  5. **Teknik Düzeltmeler:**
     - `Scroll.scroll` private alanı yerine `Scroll.getScroll()` public metodu kullanıldı.
     - `BBSSettings.mainSurface()` → `BBSSettings.deepSurface()` (mevcut olmayan metod düzeltmesi).
     - `@Override mouseClicked()` (`UIElement`'de `final`) → kaldırıldı; tüm mouse mantığı `subMouseClicked` / `subMouseReleased` (her ikisi de `protected`, override edilebilir) üzerine taşındı.

---

### Catalyst Editor — UITimelineCanvas Altyapısına Geçiş ve Kompozisyon '+' Düzeltmesi (35. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Film Editörü Timeline Altyapısına Geçiş — `UICatalystTimeline extends UITimelineCanvas`:**
     - Yeni `UICatalystTimeline.java` sınıfı oluşturuldu (`ui.dashboard.panels.catalyst` paketi).
     - BBS'in olgun zaman ekseni motoru `UITimelineCanvas`'tan miras alıyor: `Scale xAxis` ile piksel↔kare dönüşümü, `animateZoom()` ile mouse-wheel zoom, `dragTimeBy()` ile orta-tık sürükleme (pan), `Marquee` bantı.
     - **Cetvel (Ruler):** `TimelineRulerRenderer.render()` kullanılıyor — `1-2-5` adımlı otomatik tick etiketleme, sunken arka plan, küçük/büyük çizgiler.
     - **Sadece-cetvel scrubbing:** Sol tıklama **yalnızca** üst 21 px ruler bölgesinde playhead'i ilerletiyor. Klip şeritlerine tıklamak playhead'i **hareket ettirmiyor** (istemsiz scrub engellendi).
     - **Orta tık panning:** Orta fare butonu ile hem yatay (zaman ekseni) hem dikey (katman scroll) kaydırma.
     - **Ctrl + Scroll → Zoom in/out:** `Scale.animateZoom()` ile anchor-tabanlı animasyonlu yakınlaştırma/uzaklaştırma.
     - **Düz Scroll → Dikey katman scroll:** `Scroll vertical` ile katman satırları yukarı/aşağı kaydırılıyor.
     - **Playhead çizimi:** Birincil tema rengiyle (`BBSSettings.primaryColor`) tam boy dikey çizgi, ruler altında üçgen kapak, ve `UITimelineCanvas.renderCursor()` ile kare/saniye etiket kartı.
     - **Klip blokları:** Her katmanın `startFrame` ve `duration` değerleri `Scale.toGraphX()` ile dönüştürülerek çiziliyor — zoom seviyesiyle uyumlu şekilde genişliyor/daralıyor.
  2. **`UICatalystPanel.java` Temizliği:**
     - Eski `timelineContainer`, `timelineHeader`, `timelineTracks` alanları ve `mouseXToFrame()` yardımcı metodu kaldırıldı.
     - `isScrubbing` boolean alanı kaldırıldı (artık `UICatalystTimeline` kendi içinde yönetiyor).
     - `setupBottomArea()` yeniden yazıldı: Sol layers paneli aynen korundu; sağ tarafa `UICatalystTimeline` eklendi.
     - Timeline data akışı: `compSupplier` → aktif kompozisyon, `playheadSupplier` → `currentFrame`, `playheadSetter` → frame güncelleme + otomatik pause.
  3. **Kompozisyon '+' Butonu Düzeltmesi:**
     - `addCompButton` callback'ine `this.currentFrame = 0;` eklendi. `CatalystProject.addComposition()` zaten `activeCompositionIndex`'i yeni comp'a set ediyor; `currentFrame` sıfırlanmadığında eski frame timeline'da geçersiz pozisyonda kalıyordu.
     - `rebuildCompTabs()` ve `updateTitleLabel()` çağrıları zaten mevcut — düzeltme yalnızca frame resetiydi.

---

### Catalyst Editor — Film Editörü Timeline Bileşeninin Birebir Uyarlanması (36. Aşama)
* **Uygulanan Değişiklikler:**
  1. **UIClips Mimarisinin UICatalystTimeline'a Birebir Aktarılması (`UICatalystTimeline.java`):**
     - BBS Film Editörünün (`UIClips`) denenmiş ve stabil tıklama, scrubbing ve klip manipülasyon mantığı `UICatalystTimeline` üzerine birebir uyarlandı.
     - **Katı Cetvel (Strict Ruler) Scrubbing:** `isInRuler(mouseY)` denetimi ile sadece en üstteki zaman cetveline (21px ruler) tıklandığında playhead taşınır ve oynatma durdurulur (`onScrub`).
     - **Katman/Klip Seçimi ve Deselect:**
       - Klip üzerine tıklandığında katman seçilir (`setSelected`), `Shift` ile çoklu seçim (`addSelected`/`toggleSelected`) yapılır.
       - Klip olmayan boş track alanına tıklandığında mevcut seçim kaldırılır (Deselect / `clearSelection`), sürükleme yapılırsa Marquee seçim kutusu açılır.
     - **Klip Taşıma ve Çift Yönlü Trim:**
       - Sol ve sağ kenarlara yaklaşıldığında handle tespiti (`getLayerHandle` = 1 sol, 2 sağ, 0 taşıma).
       - Sol kenardan çekilirse `startFrame` ve `duration` dinamik olarak trim edilir (`grabMode == 1`).
       - Sağ kenardan çekilirse `duration` uzatılıp kısaltılır (`grabMode == 2`).
       - Gövdeden tutulursa klip zaman çizelgesinde serbestçe taşınır (`grabMode == 0`).
       - Seçili veya hover olan kliplerde `Icons.CLIP_HANLDE_LEFT` ve `Icons.CLIP_HANLDE_RIGHT` tutamaçları ile beyaz çerçeve göstergesi çizilir.
     - **Klavye Kısayolları ve Cut / Split Desteği:**
       - `C` tuşuna basıldığında veya üst paneldeki `splitButton`'a tıklandığında `cutSelected()` metodu çalışarak seçili klibi playhead hizasından ikiye böler (`split`).
       - `Delete` tuşuna basıldığında seçili klipler silinir (`deleteSelected()`).
     - **Gezinme (Navigation) & Zoom:**
       - Orta fare tuşuyla tutularak yatay ve dikey yönde serbest pan hareketi.
       - Fare tekerleğiyle anchor-tabanlı zaman zoom'u (`zoomTimeAt`) ve `Shift + Wheel` ile dikey track kaydırması.
  2. **UICatalystPanel Entegrasyonu & Senkronizasyon (`UICatalystPanel.java`):**
     - Üst araç çubuğundaki `splitButton` (`Icons.CUT`) doğrudan `cutSelectedClip()` metoduna bağlandı ve proje anında kaydedildi.
     - Sol taraftaki `layersList` panelinde bir katmana tıklandığında timeline'daki katman otomatik olarak seçilir (`catalystTimeline.setSelected(layer)`).
     - Seçili olan katman hem sol katman panelinde (arkaplan & beyaz outline) hem de sağ zaman çizelgesinde (beyaz outline & handle'lar) eşzamanlı olarak vurgulanır.
     - Oynatma kafası scrubbing başladığında oynatma durumu (`isPlaying = false`) sıfırlanır.

---

### Catalyst Editor — Split Sıralaması, Proje Otomatik Kayıt, Katman Inspector & Viewport Render (37. Aşama)
* **Uygulanan Değişiklikler:**
  1. **After Effects Tarzı Split Sıralaması (Cut) (`UICatalystTimeline.java`):**
     - `cutSelected()` metodu güncellendi:
       * Bölünen yeni katman listenin en sonuna değil, bölünen katmanın hemen index öncesine (`comp.layers.add(originalIndex, second)`) yani bir üst satıra eklenir.
       * Yeni oluşan üst parça otomatik olarak seçili (`setSelected`) hale gelir.
       * Split sonrası proje otomatik olarak diske kaydedilir (`onModified.run()`).
  2. **Tam Kalıcı Kayıt (Auto-Save & Persistence):**
     - `UICatalystTimeline`'a `onModified` callback'i eklendi:
       * Katman sürükleme ve trim işlemi bittiğinde (`subMouseReleased`),
       * Katman bölündüğünde (`cutSelected`),
       * Katman silindiğinde (`deleteSelected`),
       * Inspector panelinden katman özellikleri (ad, opaklık, renk, görünürlük, kilit, blend modu) değiştirildiğinde,
       * Proje anında `CatalystProjectManager.saveProject(activeProject)` ile otomatik olarak kaydedilir.
     - `CatalystLayer` veri modeline `opacity` (0-100) ve `blendMode` ("NORMAL", "MULTIPLY", "SCREEN", "ADD", "OVERLAY") alanları eklendi; JSON serileştirmesi (`toData` / `fromData`) sağlandı.
  3. **Katman Detayları (Inspector Panel) (`UICatalystPanel.java`):**
     - Alt panel 3 sütunlu profesyonel NLE düzenine dönüştürüldü:
       * Sol: Katman Listesi (`layersContainer`, 200px)
       * Orta: Zaman Çizelgesi (`catalystTimeline`, kalan alan)
       * Sağ: Katman Denetçisi (`inspectorContainer`, 220px)
     - Seçili katman yoksa "No layer selected" uyarısı gösterilir.
     - Katman seçildiğinde:
       * Katman Adı (`UITextbox`),
       * Katman Tipi (`UILabel`),
       * Opaklık (`UITrackpad`, 0-100%),
       * 8'li Hızlı Renk Paleti (Swatches) ile anında renk değiştirme,
       * Blend Modu döngü butonu (`NORMAL`, `MULTIPLY`, `SCREEN`, `ADD`, `OVERLAY`),
       * Görünürlük (Visible: ON/OFF) ve Kilit (Lock: ON/OFF) toggle butonları.
  4. **Viewport Canlandırma (Canvas Rendering) (`UICatalystPanel.java`):**
     - Üst viewport/tuval alanında `currentFrame`'de aktif olan tüm görünür katmanlar (`visible && startFrame <= currentFrame < startFrame + duration`) arkadan öne doğru (listenin sonundan başına) gerçek zamanlı çizilir.
     - `SOLID` katmanlar belirlenen renk ve opaklıkta (`Colors.setA`) tuval alanına çizilir.
     - `SCENE` katmanları için sahne etiketi ve renk alanı gösterilir.
     - Katman olmayan boş karelerde kompozisyonun siyah/koyu arka planı korunur.

---

### Catalyst Editor — UI Çakışmaları Temizliği, Timeline Context Menüsü, Fullscreen & Genişletilmiş Katman Tipleri (38. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Inspector & Proje Formu Metin Çakışması Düzeltmesi (`UICatalystPanel.java`):**
     - Inspector formunda (`inspectorForm`) her elemanın dikey yüksekliği (`.h(16)` / `.h(20)`) ve dikey aralık (`4px`) net olarak belirlendi, etiketlerin ve inputların üst üste binmesi engellendi.
     - Projeler açılış formundaki `durationCalcLabel` ve `Resolution (W x H)` alanlarının dikey yükseklikleri sabitlenerek metin çakışması tamamen giderildi.
  2. **Viewport OSD Düzenlemesi (`UICatalystPanel.java`):**
     - Tuvalin tam ortasına çizilen kompozisyon bilgi metin kartı (`MAIN COMP | 1920x1080 | 60 FPS | Rec.709`) ortadan kaldırıldı.
     - Bu bilgi tuvalin sol altında yer alan timecode (`00:00:00:00`) satırının hemen bir üst satırına (`area.ey() - 34`) kompakt ve şık bir OSD olarak yerleştirildi.
     - Tuval yüzeyi artık yalnızca katmanların kendi görsellerini engelsiz şekilde gösterir.
  3. **Split İsimlendirme Düzeltmesi (`UICatalystTimeline.java`):**
     - `cutSelected()` metodu içindeki `" (Split)"` eki kaldırıldı. Bölünen yeni üst parça orijinal katmanın adını birebir korur (`second.name = layer.name`).
  4. **Üst Bardaki Add Layer'ın Kaldırılması, Fullscreen & Timeline Context Menüsü:**
     - Üst araç çubuğundaki ilkel `+` Add Layer butonu ve popup katmanı tamamen kaldırıldı.
     - Sağ üstteki `Icons.FULLSCREEN` butonuna tıklandığında önizleme alanını/tuvali tam ekran moduna alıp alt panelleri gizleyen (`toggleFullscreen()`) mekanizma bağlandı.
     - `UICatalystTimeline` üzerine sağ tıklandığında açılan zengin `UIContextMenu` entegre edildi:
       * **Add Layer Alt Menüsü:** Solid Layer, Audio Layer, Adjustment Layer, Null Layer, Text Layer, Film / Scene Layer, Video Layer, Image Layer.
       * **Klip Üzerinde Sağ Tık Seçenekleri:** Cut / Split (C), Duplicate, Delete (Del).
       * Kopyalama (`duplicateSelected()`) seçili katmanın tam bir kopyasını oluşturup hemen üstüne yerleştirir.
  5. **Genişletilmiş Katman Veri Modeli (`CatalystLayer.java`):**
     - `LayerType` enum'ına yeni tipler eklendi:
       `SOLID, AUDIO, ADJUSTMENT, NULL, TEXT, SCENE, VIDEO, IMAGE`
     - Her katman tipi için renk paleti (`defaultColor()`) ve katman listesi için kısaltma etiketleri (`getBadge()`: `[SOL], [A], [ADJ], [N], [T], [S], [V], [IMG]`) tanımlandı.

---

### Catalyst Editor — Timeline Snapping (Manyetik Yapışma), BBS Native Hiyerarşik Ekleme Menüsü ve Medya Altyapısı (39. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Timeline Manyetik Yapışma (Snapping) (`UICatalystTimeline.java`):**
     - Katmanlar taşınırken veya sol/sağ kenarlarından trim edilirken snap eşiği eklendi (`snapTick` metodu, 6 piksel tolerans).
     - Diğer katmanların başlangıç ve bitiş karelerine (`startFrame`, `startFrame + duration`), oynatma kafasına (`playhead`) ve kompozisyon sınırlarına (`0`, `duration`) manyetik olarak kilitlenme sağlandı.
     - `Alt` tuşuna basılı tutulduğunda snapping geçici olarak devre dışı bırakılır.
  2. **BBS Film Editörü Tarzı Hiyerarşik Context Menüsü (`UICatalystTimeline.java`):**
     - Üst bar olarak `MenuVerb` aksiyonları (`ADD`, `REMOVE`, `COPY`, `CUT`) yerleştirildi.
     - "Add..." alt menüsü altında "Add layer at cursor..." ve "Add layer at current tick..." seçenekleri eklendi.
     - Seçenek tıklandığında BBS standart arama çubuğu ve ikon desteğine sahip `UIChoiceMenu` açılarak tüm 8 katman tipi (`SOLID`, `AUDIO`, `ADJUSTMENT`, `NULL`, `TEXT`, `SCENE`, `VIDEO`, `IMAGE`) hiyerarşik olarak sunuldu.
  3. **Medya & Kaynak Yolu (Resource Path) Entegrasyonu (`CatalystLayer.java` & `UICatalystPanel.java`):**
     - `CatalystLayer` modeline `public String resourcePath = ""` alanı eklendi ve JSON/NBT serialization (`toData` / `fromData`) sağlandı.
     - Inspector paneline `Resource / File:` alanı (`layerResourceInput`) entegre edildi. Katman `SCENE`, `VIDEO`, `IMAGE`, `AUDIO` veya `TEXT` olduğunda görünür hale gelir ve dinamik olarak güncellenir.
  4. **Viewport Canlandırma & Doku Desteği (`UICatalystPanel.java`):**
     - `IMAGE` katmanları için BBS `BBSModClient.getTextures().getTexture(Link.create(...))` üzerinden gerçek doku/resim render'ı bağlandı (`texturedBox`).
     - Kaynak yolu girilmediğinde veya resim yüklenemediğinde renk kutusu ve `IMAGE: [yol]` rozeti çizilir.
     - `VIDEO`, `SCENE` ve `TEXT` katmanları için tuval üzerinde dinamik içerik ve metin kartları çizdirildi.

---

### Catalyst Editor — Gerçek BBS Medya Oynatıcıları, Transform/Pivot Kontrolleri, Dahili Dosya Seçici ve UI/UX İyileştirmeleri (40. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Context Menü Buton Çakışması & Temizlik (`UICatalystTimeline.java`):**
     - Hiçbir katman seçili olmadığında sağ tık üst barında yalnızca `[+]` (`MenuVerb.ADD`) ikonu gösterilir; mükerrer liste satırları kaldırıldı.
     - Katman seçildiğinde üst barda `[-]` (`MenuVerb.REMOVE`) ve `[Duplicate]` (`MenuVerb.COPY`), alt listede ise `Cut / Split (C)`, `Duplicate` ve `Delete (Del)` seçenekleri sunulur.
  2. **Gelişmiş Text Layer Motoru & Inspector (`CatalystLayer.java` & `UICatalystPanel.java`):**
     - `CatalystLayer` modeline `textColor` (katman renginden bağımsız metin rengi), `fontSize`, `lineWrapping` ve `shadow` alanları eklendi ve tam JSON/NBT serialization (`toData` / `fromData`) bağlandı.
     - Inspector paneline çok satırlı metin girişi (`UITextarea`), yazı boyutu trackpad'i (`layerFontSizeInput`), metin renk seçicisi (`layerTextColorPicker` / `UIColor`), satır kaydırma (`Wrap: ON/OFF`) ve gölge (`Shadow: ON/OFF`) butonları entegre edildi.
     - Tuval üzerinde metin, katmanın timeline rengine bağlı kalmaksızın kendi `textColor` değeriyle ve seçilen gölge ayarıyla render edilir.
  3. **Timeline Dikey Taşıma / Katman Sıralaması (Layer Reordering):**
     - `UICatalystTimeline` içindeki tekil katman sürüklemesine (`dragLayers`) dikey hareket algılama eklendi. Fare dikeyde başka bir satıra kaydırıldığında hedef satır indeksi (`fromLayerY(mouseY)`) hesaplanıp katman `comp.layers` listesinde yeni sırasına taşınır.
  4. **Spacebar Play/Stop Desteği (`UICatalystPanel.java`):**
     - `subKeyPressed` metodu override edilerek odak herhangi bir metin alanında değilken (`!context.isFocused()`) `GLFW_KEY_SPACE` tuşu ile zaman çizelgesi oynatımı (`togglePlayback()`) tetiklendi.
  5. **Üst Bar Sadeleştirme & Kompozisyon Ayarları Modalı:**
     - Üst bardaki gereksiz split butonu kaldırıldı, genişlik 70px olarak optimize edildi.
     - Ayarlar butonuna (`settingsButton`) tıklandığında açılan `UIOverlayPanel` modal penceresi (`openCompositionSettingsModal()`) eklendi: Kompozisyon Adı, FPS, Süre (saniye) ve Çözünürlük (Genişlik x Yükseklik) dinamik olarak güncellenip projeye kaydedilir.
  6. **Geri Al / İleri Al (Undo / Redo — CTRL+Z / CTRL+Y) Desteği:**
     - Proje snapshot tabanlı `pushUndo()`, `undo()` ve `redo()` motoru kuruldu.
     - Katman taşıma, kırpma (trim), bölme (split), çoğaltma (duplicate), silme (delete), ekleme ve ayar değişikliklerinde otomatik undo snapshot'ı alınır.
     - Odak metin kutusunda değilken `Ctrl + Z` ile geri, `Ctrl + Y` veya `Ctrl + Shift + Z` ile ileri alma kısayolları bağlandı.
  7. **Viewport Debug Metinlerinin Temizliği:**
     - Tuval üzerinde katmanların üzerinde görünen "VIDEO: ...", "SCENE: ...", "IMAGE: ...", "Layer Name" gibi hata ayıklama etiketleri tamamen kaldırıldı; saf görsel/medya çıktısı sağlandı.
  8. **BBS Yerel Dosya Seçicileri (Browse Butonları):**
     - Ham metin kutusunun yanına dahili seçici (`layerPickResourceBtn`) ve klasör açma butonu (`layerOpenFolderBtn`) entegre edildi:
       * `IMAGE` katmanları için `UITexturePicker.open`
       * `VIDEO` katmanları için `UIStringOverlayPanel.links` (`UIVideoClip.getVideoLinks()`)
       * `AUDIO` katmanları için `UISoundOverlayPanel`
       * Klasör butonu ilgili medya dizinini (`BBSMod.getAssetsFolder()`, `BBSMod.getAudioFolder()`) sistem dosya yöneticisinde açar.
  9. **Viewport Transform & Bounding Box:**
     - `CatalystLayer` modeline `posX`, `posY`, `scaleX`, `scaleY`, `rotation`, `anchorX`, `anchorY` alanları ve serileştirme eklendi.
     - Inspector paneline Position (X, Y), Scale (X, Y), Rotation ve Anchor Point (X, Y) trackpad kontrolleri yerleştirildi.
     - Tuval üzerinde seçili katmanın etrafına beyaz çerçeve (bounding box), 4 köşesinde boyutlandırma tutamaçları ve merkezinde pivot crosshair çizildi. Katman gövdesinden tutularak taşınabilir ve köşe tutamaçlarından çekilerek ölçeklendirilebilir.
  10. **Gerçek BBS Medya Oynatımı:**
      - `IMAGE`: `BBSModClient.getTextures().getTexture(...)` ile doku render'ı.
      - `VIDEO`: `BBSModClient.getVideos().getPlayer(layer, link).getFrame(relSec)` ile kare kare video gösterimi.
      - `AUDIO`: `BBSModClient.getSounds().playUnique(this, link)` ile oynatma kafasıyla senkron ses çalma, duraklatma ve panel kapandığında (`onDisappear`) bellek/ses temizliği sağlandı.

---

### Catalyst Studio — Inspector Scroll, Medya Senkronizasyonu, Bounding Box Shift-Scale, Waveform ve Film Seçici (41. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Inspector Scroll Paneli & Transform Reset (`UICatalystPanel.java`):**
     - Inspector paneli BBS `UI.scrollView` mimarisine geçirilerek Transform, Text özellikleri ve Medya kontrollerinin dikeyde taşması tamamen çözüldü, fare tekerleğiyle akıcı kaydırma sağlandı.
     - Transform başlığının yanına tek tıkla varsayılan değerlere (`Pos: 0, 0`, `Scale: 1, 1`, `Rot: 0°`, `Anchor: 0.5, 0.5`) dönüştüren "Reset" butonu (`layerResetTransformBtn`) eklendi.
  2. **Video Katmanı Düzeltmeleri & Ses/Süre Senkronu (`UICatalystPanel.java` & `CatalystLayer.java`):**
     - Tuvalde `VIDEO` ve `IMAGE` katmanları çizilirken katman rengi tint overlay'i kaldırıldı; videolar ve resimler orijinal renkleriyle (`0xFFFFFFFF` tabanlı alfa) render ediliyor.
     - `CatalystLayer` modeline `volume` (0.0 - 1.0), `audioOffset` (kare bazlı) ve `mediaDuration` alanları eklenip serileştirildi.
     - Inspector'a Volume slider'ı, Audio Offset trackpad'i ve klibi medyanın gerçek süresine kilitleyen "Extend to Media Length" butonu eklendi.
     - Video sesi OpenAL ses motoru ile oynatma kafasına tam senkronize edildi.
  3. **Kompozisyon Ayarları Modalı Geliştirmesi (`UICatalystPanel.java`):**
     - `openCompositionSettingsModal()` içeriği canlı hesaplanan süre göstergesi (`saniye • frame @ FPS`) ve "Save & Apply" butonu ile zenginleştirildi.
  4. **Viewport Bounding Box Shift-Scale:**
     - Köşe tutamaçları sürüklendiğinde varsayılan olarak en-boy oranı (aspect ratio) korunarak ölçekleme yapılır; `Shift` tuşuna basılı tutulduğunda serbest (non-proportional) X/Y boyutlandırma aktif olur.
  5. **Text Katmanı Motoru:**
     - `fontSize` değeri `MatrixStack` ölçeklemesine bağlandı.
     - `lineWrapping = true` iken `FontRenderer.wrap` ile otomatik satır sarma sağlandı.
     - Katman adı asla metin içeriği olarak çizilmez; metin boşken inspector üzerinden düzenleme ipucu gösterilir.
  6. **Ses Katmanı Senkronu, Waveform & Ses Temizliği:**
     - Scrubbing anında, duraklatmada, sekme değişiminde ve panel kapanışında tüm OpenAL kaynakları (`stopAllAudio()`) durdurulur/temizlenir.
     - Timeline üzerinde ses klipleri için `Waveform` render'ı ve genlik çubuğu yedeği entegre edildi.
  7. **Film Katmanı ([FILM]) & Yerel Film Seçici:**
     - `SCENE` katmanı kullanıcı arayüzünde "Film Layer" (`[FILM]` badge) olarak yeniden adlandırıldı.
     - Inspector üzerinden `UIStringOverlayPanel` ile BBS kayıtlı filmleri (`BBSMod.getFilms().getKeys()`) listelenip seçildiğinde klip süresi otomatik olarak filmin kamera süresine (`film.camera.calculateDuration()`) ayarlanır.

---

### Catalyst Studio — EOFException Onarımı, Waveform Cache, OpenAL Kaynak Yönetimi ve Performans Optimizasyonları (42. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Waveform Önbellekleme (Caching & Lazy Load) (`CatalystLayer.java` & `UICatalystTimeline.java`):**
     - `UICatalystTimeline.renderTracks` içindeki render döngüsünden senkron `SoundManager.load` ve `WaveReader.read` çağrıları tamamen kaldırıldı. Her karede diske gidip WAV okuma kaynaklı `EOFException` çökme döngüsü kökten çözüldü.
     - `CatalystLayer` modeline `cachedWaveform`, `isWaveformLoading`, `cachedWaveformPath` transient alanları eklendi.
     - `UICatalystTimeline.ensureWaveformLoaded` asenkron iş parçacığı (`CatalystWaveformLoader`) kurularak waveform verisi yalnızca kaynak yolu (`resourcePath`) değiştiğinde arka planda tek bir kez hesaplanıp katmana önbelleğe alındı (`layer.cachedWaveform`).
     - Okuma hataları (`EOFException`, eksik/bozuk dosyalar) `try-catch (Throwable)` ile sessizce yönetilip sonsuz yeniden denemeler engellendi.
  2. **OpenAL Ses Kaynak Havuzu ve Tekil Oynatıcı (`UICatalystPanel.java`):**
     - Ses çalma mantığı 60 FPS tuval render döngüsünden (`renderPreviewCanvas`) tamamen soyutlandı.
     - Ses tetikleme yalnızca playhead tick'i değiştiğinde çalışan `updatePlaybackAudio()` metoduna taşındı.
     - Her karede ve her katmanda yeni OpenAL ses kanalı üretilmesi engellenerek `Allocate new source: Invalid operation` hatası ortadan kaldırıldı; tekil ve kontrollü `activeAudioPlayer` tahsis edildi.
     - Klipler arası geçişte, duraklatmada (`Space`), oynatma kafası scrubbing'inde veya panel kapanışında `stopAllAudio()` doğrudan `activeAudioPlayer.delete()` çağırarak OpenAL kaynağını anında işletim sistemine/sürücüye iade eder.
  3. **Hafif Performans Optimizasyonları (Culling):**
     - **Timeline Culling:** `UICatalystTimeline` içinde görünür zaman aralığı (`clipArea.ex() < area.x || clipArea.x > area.ex()`) ve dikey scroll alanının dışında kalan katmanlar ile dalga formları çizim döngüsünden elenerek (cull) GPU/CPU yükü azaltıldı.
     - **Viewport Culling:** `renderPreviewCanvas` içinde tuvalin sınırları dışında kalan (`lx + layerW < cx || lx > cx + canvasW || ly + layerH < cy || ly > cy + canvasH`), görünmez (`!visible`) veya opaklığı sıfır olan katmanların çizim işlemleri pas geçildi.
  4. **Katman Render Süresi ve AE Profiler (`UICatalystPanel.java`):**
     - Her katmanın çizim süresi nanosaniye hassasiyetiyle ölçülüp `layer.lastRenderMs` alanına yazıldı ve sol paneldeki katman satırının yanına (`0.2 ms`, `1.5 ms`) basıldı.
     - Kompozisyonun toplam render süresi ölçülerek tuvalin sol altındaki OSD şeridine (`Comp: X.X ms`) entegre edildi.

---

### Catalyst Studio — Media Picker NullPointerException Onarımı (43. Aşama)
* **Kök Neden:**
  - `crash-2026-09-27_04.57.42-client.txt` logundaki analize göre `UICatalystPanel.java` içinde `openMediaPickerForSelectedLayer()` çağrıldığında, açılan overlay/picker (örneğin Video, Audio veya Doku seçici) kullanıcı tarafından seçim yapılmadan kapatıldığında veya null döndüğünde `link.toString()` çağrısı `NullPointerException` fırlatıp oyunu çökertiyordu.
* **Uygulanan Değişiklikler:**
  1. **Image/Texture Picker Callback Null-Safety:**
     - `UITexturePicker.open` callback'inde `link != null` kontrolü eklendi.
  2. **Video Picker Callback Null-Safety:**
     - `UIStringOverlayPanel.links` lambda callback'inde `link != null` kontrolü eklenerek `link.toString()` ve `VideoPlayer` probe/süre hesaplama işlemleri güvenli bloğa alındı.
  3. **Audio Picker Callback Null-Safety:**
     - `UISoundOverlayPanel` callback'inde `link != null` kontrolü eklenerek `link.toString()` ve `SoundBuffer` probe/süre hesaplama işlemleri korundu.
  4. **Film/Scene Picker Callback Null & Empty Safety:**
     - Film seçici callback'inde `filmId != null && !filmId.trim().isEmpty()` kontrolü eklenerek boş veya null film id seçimlerinde NBT/JSON yükleme çökmeleri önlendi.

---

---

### Catalyst Studio — Video Aspect Ratio, Rotation Matrix, Film Viewport Render, Split Offset, Settings Modal ve Video Akıcılığı Onarımı (44. Aşama)
* **Uygulanan Değişiklikler:**
  1. **Video Katmanında Dinamik Aspect Ratio Desteği (`VideoPlayer.java` & `UICatalystPanel.java`):**
     - Sabit 16:9 oranı kaldırıldı; `VideoPlayer` sınıfına `getWidth()`, `getHeight()` ve `getFps()` metotları eklendi.
     - `UICatalystPanel.getLayerDimensions()` metodu yazılarak video ve resimlerin orijinal piksel en-boy oranına (örneğin 4:3, 1:1, 9:16) göre fit/letterbox/pillarbox hesaplandı.
     - Hem tuvaldeki katman çizimi hem de etrafındaki Bounding Box ve köşe tutamaçları bu dinamik boyuta bağlandı.
  2. **Katman Bölme (Cut / Split) In-Point / Offset Desteği (`CatalystLayer.java`, `UICatalystTimeline.java` & `UICatalystPanel.java`):**
     - `CatalystLayer` modeline `mediaOffset` alanı eklendi (`toData` / `fromData` ile serileştirildi).
     - `UICatalystTimeline.cutSelected()` içinde bölünen ikinci parçanın `mediaOffset` değeri `ilk_parca.mediaOffset + firstDuration` olarak atandı.
     - Video, Audio ve Film oynatma kurgusunda bağıl kare hesabı:
       `int relativeFrame = (currentPlayhead - layer.startFrame) + layer.mediaOffset;`
       olarak güncellendi; kesilen parçaların videonun tam kesim karesinden oynaması sağlandı.
  3. **Katman Döndürme (Rotation) Matrisi Onarımı (`UICatalystPanel.java`):**
     - Katman çiziminde ve Bounding Box sınırlarında MatrixStack Z ekseni rotasyonu uygulandı:
       * Pivot noktasına translate (`lx + layerW * anchorX`, `ly + layerH * anchorY`),
       * `matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(layer.rotation))`,
       * Pivotu geri öteleme ve çizim.
     - Tuval fare tıklamalarında ve köşe tutamaçlarında dönmüş koordinatlar trigonometrik olarak hesaplanarak döndürülmüş katmanların doğru seçilip ölçeklenmesi sağlandı.
  4. **Film Katmanının Tuvalde Render Edilmesi (`UICatalystPanel.java`):**
     - Film katmanı için `BBSRendering.getTexture()` FBO off-screen dokusu `texturedBox` ile katman sınırlarına bağlandı; boş mavi renk yerine canlı film render'ı ve seçim durumuna göre şık kart gösterimi sağlandı.
  5. **Composition Settings Modalını Görünür Yapma (`UICatalystPanel.java`):**
     - Modal içindeki elementler `container.column(6).vertical().stretch()` ile kapsayıcıya bağlanıp `modal.content`'e eklendi.
     - Comp Name, FPS, Süre (sn), Genişlik/Yükseklik ve "Apply & Close" butonu ekranda net ve ortalanmış biçimde görünür hale getirildi.
  6. **Video Oynatma Akıcılığı & Micro-Stutter Giderimi (`VideoPlayer.java` & `UICatalystPanel.java`):**
     - `VideoPlayer.upload()` içerisine mevcut kare numarası ile geçerli doku kimliğinin eşleşmesi durumunda GPU'ya gereksiz bellek aktarımını kesen önbellek koruması eklendi.
     - `CatalystLayer` üzerinde kare numarası değişmedikçe bir önceki doku ID'sinin tekrar kullanımı sağlanarak oynatma akıcılığı artırıldı.

  7. **Tam Ekran / Viewport Çözünürlük Bağımsız Koordinat Sistemi (`UICatalystPanel.java`):**
     - Katmanların pencere piksel koordinatları yerine sabit sanal kompozisyon çözünürlüğüne (`comp.width` x `comp.height`, varsayılan 1920x1080) kilitlenmesi sağlandı:
       * `float scale = Math.min((float) (area.w - 32) / compW, (float) (area.h - 32) / compH);`
       * `float offsetX = area.x + (area.w - compW * scale) / 2.0F;`
       * `float offsetY = area.y + (area.h - compH * scale) / 2.0F;`
     - Tuval render döngüsünde (`renderPreviewCanvas`) MatrixStack'e `translate(offsetX, offsetY, 0)` ve `scale(scale, scale, 1.0F)` uygulanarak tüm katmanlar, metinler, görseller ve Bounding Box 1920x1080 sanal piksel uzayında çizildi.
     - Tuval fare tıklamaları ve sürükleme işlemlerinde fare koordinatları `(mouseX - offsetX) / scale` ile sanal uzaya dönüştürülerek tam ekran geçişlerinde veya pencere yeniden boyutlandırmalarında katmanların kayması, bozulması ve oran kaybı tamamen engellendi.

---

## 4. Derleme & Doğrulama Durumu

* `./gradlew.bat --no-daemon compileJava compileClientJava` komutu çalıştırıldı.
* **Sonuç (44. Aşama & Faz 4):** `BUILD SUCCESSFUL in 17s` — 0 Hata, 0 Kritik Uyarı.
* `./gradlew.bat --no-daemon apiCheck` komutu çalıştırıldı.
* **Sonuç:** `BUILD SUCCESSFUL in 9s` — Addon API sözleşmesi korundu.

---

## 5. Catalyst Timeline Profesyonel Kurgu, Senkronizasyon ve Navigasyon Altyapısı (Faz 4)

### Uygulanan 5 Temel Özellik:
1. **Klip Trim / Kenar Çekme ve Medya İç Ofset Senkronu (Slip / Trim-In & Trim-Out):**
   - **Sol Kenar (Trim-In / Slip-In):** `startFrame` sağa veya sola çekildiğinde `mediaOffset` dinamik olarak ofsetlenir (`mediaOffset = Math.max(0, initialMediaOffset + deltaFrames)`). Klip asla başa sarmaz, kurgunun kesildiği andan itibaren oynamaya devam eder.
   - **Sağ Kenar (Trim-Out):** `duration` dinamik olarak uzatılıp kısaltılır; klip süresi `Math.max(1, layer.mediaDuration - mediaOffset)` sınırını aşamaz.
   - **Kenar Tutamaç İmleci:** Fare katman kenarlarına yaklaştığında (<= 6px) ve kenar çekme esnasında imleç `GLFW.GLFW_HRESIZE_CURSOR` şeklini alır.

2. **Manyetik Hizalama (Magnetic Snapping) ve Toolbar Butonu:**
   - Zaman çizelgesinin sol üst ruler köşesine `Icons.MAGNET` snap butonu eklendi (tıklama ile veya Premiere/DaVinci standardı olan `N` tuşuyla açılıp kapanabilir, varsayılan açık). Buton üzerinde `Snapping (N)` tooltip'i gösterilir.
   - After Effects uyumluluğu gözetilerek `S` kısayolu snapping çakışmasından tamamen arındırıldı ve yalnızca seçili katmanın Scale özelliğini genişletmeye (`PROP_SCALE`) tahsis edildi.
   - Snap Hedefleri: Playhead, diğer katmanların başı ve sonu, tüm katmanların keyframe noktaları, kare 0 ve kompozisyon bitişi. Alt tuşuna basılı tutulduğunda snap durumu tersine çevrilir. Hassasiyet: 8 piksel (`SNAP_DISTANCE = 8`).

3. **Kare Kare (Frame-by-Frame) Ok Tuşları ile Navigasyon:**
   - Sol Ok: Playhead -1 kare (Shift + Sol: -10 kare, min 0).
   - Sağ Ok: Playhead +1 kare (Shift + Sağ: +10 kare, max duration - 1).
   - `UICatalystTimeline` ve `UICatalystPanel` seviyesinde odaklanmış metin kutusu yokken anında senkron frame decoding ve ses çalımı (`seekToFrame`).

4. **Ripple Delete / Boşluk Silme (Gap Deletion):**
   - `Shift + Delete` / Sağ tık menüsü "Ripple Delete (Shift + Del)": Seçili katmanları siler ve sonraki tüm katmanları silinen süre kadar geri çeker.
   - Boşluk Seçimi & Silme: Katmanlar arasındaki boşluğa tıklandığında boşluk mavi renkle vurgulanarak seçilir; `Delete` / `Backspace` tuşuna basıldığında sonraki tüm katmanlar boşluk süresi kadar sola kaydırılır (`rippleDeleteGap()`).

5. **Çoklu Katman Seçimi & Kutu / Marquee Seçimi:**
   - Boş alandan sürükleme yapıldığında kesişen tüm katmanlar seçim kutusu (`this.marquee`) ile toplu seçilir.
   - `Shift + Click` mevcut seçimi bozmadan yeni katmanları seçime ekler.
   - Çoklu taşıma modunda (`grabMode == 0`) seçili katmanlar birbirlerine göre bağıl mesafelerini koruyarak timeline 0 sınırına kadar senkron hareket eder.

---

## 6. Catalyst Media Pool (Varlık Yönetimi) & Sürükle-Bırak Altyapısı (Faz 5)

### Uygulanan Temel Özellikler:
1. **CatalystMediaAsset Veri Modeli:**
   - Video, Audio ve Image varlıkları için tip güvenli `MediaType`, dosya yolu, dosya boyutu, kare cinsinden süre ve dosya adı modeli.
   - `formatSize()` (B, KB, MB, GB) ve `formatDuration(fps)` (MM:SS / HH:MM:SS) formatlayıcıları.
   - Geriye dönük tam uyumlu NBT/JSON serileştirme (`toData` / `fromData`).

2. **Proje Seviyesi Medya Havuzu (`CatalystProject`):**
   - `mediaPool` listesi, proje dosyasıyla birlikte kaydedilir/yüklenir.
   - `addAsset`, `removeAsset`, `getAssetByPath`, `syncMediaPoolWithLayers` ve `cleanUnusedMedia` yönetim fonksiyonları.

3. **UIMediaPoolPanel Paneli & Kullanıcı Arayüzü:**
   - Sol tarafta açılır/kapanır Media Pool paneli (`B` tuşu veya toolbar `Icons.SAVED` butonu ile geçiş).
   - Başlık çubuğu: Havuz sayaç rozeti, `UIFileDialogs.pickFile` ile "Import Media", "Clean Unused" ve "Open Folder" butonları.
   - Dinamik metin filtreleme / arama kutusu (`UITextbox`).
   - Medya kartları: Tür ikonu (`Icons.FILM`, `Icons.SOUND`, `Icons.IMAGE`), renk kodlaması, dosya adı, dosya boyutu, süre ve format rozeti (`VID`, `AUD`, `IMG`).
   - Sağ tık bağlam menüsü: "Insert to Timeline (At Playhead)", "Reload Asset", "Open Containing Folder", "Delete from Pool".

4. **Sürükle-Bırak Hattı (Drag & Drop Pipeline):**
   - **Masaüstünden Pencereye:** LWJGL `glfwSetDropCallback` ile işletim sisteminden sürüklenen dosyalar `BBSMod.getAssetsPath("catalyst_media")` dizinine kopyalanarak otomatik havuza eklenir.
   - **Havuzdan Zaman Çizelgesine:** Havuzdan sürüklenen varlık fare imlecinde yüzen kart olarak takip edilir; zaman çizelgesi üzerinde manyetik snap kılavuz çizgisi ve hedef kare rozeti çizilir.
   - Bırakıldığında veya çift tıklandığında otomatik katman tipi (`VIDEO`, `AUDIO`, `IMAGE`), renk ve süre atanır; ses katmanları için arka planda asenkron dalga formu üretimi (`ensureWaveformLoaded`) tetiklenir.

---

## 7. Catalyst Katman Trim Mantığı, Audio mediaOffset Senkronu & Canlı Önizleme Stereo/Kristal Netlik (Aşama 50)

### Uygulanan Temel Düzeltmeler ve İyileştirmeler:
1. **Audio Katmanı mediaOffset Senkronu (Trim-In / Başa Sarma Engeli):**
   - Sol kenar sağa çekildiğinde oluşan `mediaOffset` değeri `Wave` / `SoundPlayer` oynatma ofsetine (`relSec = ((currentFrame - layer.startFrame) + layer.mediaOffset + layer.audioOffset) / fps`) bağlandı.
   - `SoundPlayer` içinde `pendingOffset` altyapısı kuruldu; OpenAL'in durdurulmuş/başlatılmış kaynaklarda ofseti 0'a sıfırlama davranışı `alSourcePlay` ardından `setPlaybackPosition` uygulanarak tamamen önlendi.
   - Timeline dalga formu çiziminde `layer.mediaOffset` hesaba katılarak (`startTime`, `endTime`) görsel dalga formu ile ses ofseti 1:1 senkronize edildi.

2. **Sağ Kenar (Trim-Out) Kelepçe ve Süre Sınırı Düzeltmesi (`UICatalystTimeline`):**
   - Fiziksel medya sınır kuralı uygulandı: `Oynatılan Son Medya Karesi = layer.mediaOffset + layer.duration <= layer.mediaDuration`.
   - Sağ kenar çekildiğinde (`grabMode == 2` / `TRIM_END`): `maxDuration = Math.max(1, layer.mediaDuration - layer.mediaOffset)` ile kalan fiziksel medya süresine kadar uzatabilme serbestliği sağlandı.
   - Sol kenar çekildiğinde (`grabMode == 1` / `TRIM_START`): `startFrame` arttıkça `duration` eşit miktarda azaltılıp `mediaOffset` aynı miktarda artırılarak `endFrame` (`startFrame + duration`) sabit kilitlendi; sol kenar geri çekildiğinde `mediaOffset` 0'a kadar serbestçe indirildi.
   - `IMAGE`, `SOLID` ve `TEXT` gibi fiziksel medya sınırı olmayan katmanların `mediaDuration` kıskacında kalması önlendi.

3. **Canlı Önizleme Ses Kalitesi (Gerçek 48kHz Stereo & 32-Bit Float Kristal Netlik):**
   - `Wave.convertToStereo()` metodu eklenerek mono seslerin OpenAL tarafından 3D uzamsal zayıflatmaya uğraması önlendi; 2 kanallı 48000 Hz 16-bit PCM standardı (`AL_FORMAT_STEREO16`) garanti edildi.
   - `SoundPlayer` 2D stereo yapılandırmasına OpenAL Soft `AL_SOURCE_SPATIALIZE_SOFT` desteği entegre edildi; `layer.pan` (-1.0 .. +1.0) kontrolü canlı önizlemede gerçek stereo pan olarak etki eder hale getirildi.
   - **Canlı Scrub Miksajı (`playScrubAudio`):** Timeline scrubbing esnasında tüm aktif ses katmanları 32-bit kayan noktalı (float) stereo tamponda anında mikslenir; dinamik peak taraması ve analog tanh soft-clipping uygulanarak hoparlörden gelen dijital çatırtılar/bozulmalar önlendi.

---

## 8. Catalyst Media Pool Dosya Yolu (Path Resolution), Doku Yükleme & Oynatma Onarımı (Aşama 51)

### Uygulanan Temel Düzeltmeler ve İyileştirmeler:
1. **Absolute Path / Dosya Yolu ve Boşluk Formatlaması (`CatalystMediaAsset`, `CatalystLayer`, `CatalystProject`, `UICatalystPanel`):**
   - Media Pool'dan timeline'a sürükleme ve inspector dosya yolu girişlerinde Windows ters eğik çizgileri (`\`) tek tip normalize edilmiş mutlak yollara (`/`) dönüştürüldü.
   - Dosya adındaki boşluklar ve özel karakterler korunurken, `dropAssetToTimeline` içinde `new File(normPath).exists()` doğrulaması yapılarak yetim/boş katman oluşması engellendi.

2. **AssetProvider & Direct File Resolution (Missing Texture / Checkerboard Kök Onarımı):**
   - Windows sürücü harfi içeren mutlak yolların (`C:/...`, `D:/...`) `Link` tarafından hatalı namespace (`C:`) olarak yorumlanıp `AssetProvider` tarafından reddedilmesi sorunu, `AssetProvider.resolveDirectFile(Link link)` mekanizması ile çözüldü.
   - `getFile()`, `hasAsset()` ve `getAsset()` metodları diskteki fiziksel dosyaları otomatik tespit ederek doğrudan `FileInputStream` açar hale getirildi.
   - `VideoManager.getPlayer()` ve `VideoManager.get()` metodlarına doğrudan disk dosyası çözümleme desteği entegre edildi.

3. **IMAGE Katmanı Doku Yüklemesi (`UICatalystPanel`, `CatalystLayer`):**
   - `UICatalystPanel.getOrLoadImageTexture(layer)` metodu inşa edildi.
   - `CatalystLayer` üzerine eklenen `cachedImageTexture` ve `cachedImagePath` transient volatile alanları ile doku önbelleği kuruldu.
   - Harici resimler (`.png`, `.jpg`, `.jpeg`) `Pixels.fromPNGStream()` ve `Texture.textureFromPixels()` ile yüksek kaliteli `GL11.GL_LINEAR` OpenGL dokusu olarak yüklendi; missing texture pembe-mavi dama tahtası engellendi.
   - `getLayerDimensions()` içerisine görselin gerçek genişlik/yükseklik en-boy oranı entegre edilerek tuval üzerindeki çerçeve ve tutamaçlar kusursuz hizalandı.

4. **VIDEO ve AUDIO Oynatıcı Bağlantısı (`AudioReader`, `VideoPlayer`, `UICatalystTimeline`):**
   - `AudioReader.readWave(File file)` ve `AudioReader.readVideoAudio(File file)` statik metodları eklendi. Ses ve video dosyalarından WAV/OGG/MP3 çözümleme ve FFmpeg 16-bit PCM stereo ses çıkarma doğrudan dosya referansıyla çalışır kılındı.
   - `UICatalystTimeline.ensureWaveformLoaded()` ve `UIMediaPoolPanel.probeAsset()` doğrudan ses dosyaları için `AudioReader.readWave()` üzerinden dalga formu ve süre hesaplar hale getirildi.
   - `VideoPlayer` normalize edilmiş mutlak dosya yolu ile başlatılarak `seekToFrame` ve canlı önizleme karelerinin FBO dokusuna kesintisiz akması sağlandı.

---

## 9. Aşama 52: Catalyst Editör Nihai Stabilite Check-Up'ı, Kaynak/Bellek Güvenliği ve Inspector Cilası

1. **Inspector Sadeleştirmesi ve Arayüz Temizliği (`UICatalystPanel.java`):**
   - Media Pool entegrasyonu tamamlandığı için Inspector üzerindeki "Browse...", "Open Media Folder" klasör butonları ve altındaki taşan `layerResourceInput` metin kutusu tamamen kaldırıldı.
   - Ölü yardımcı metodlar (`openMediaPickerForSelectedLayer`, `openMediaFolderForSelectedLayer`) ve kullanılmayan UI overlay importları (`UITexturePicker`, `UISoundOverlayPanel`, `UIStringOverlayPanel`, `UIVideoClip`) temizlendi.
   - Seçili medya katmanları için salt okunur tek satır kompakt etiket yerleştirildi: `Source: <dosya_adi.uzanti>` (örn: `Source: manifest - KT5 Music Video.mp3`).
   - `audioControlsGroup` bileşeni ile Volume, Pan ve Audio Offset alanları hiyerarşik olarak gruplandı; sadece ses ayarı barındıran katmanlarda görüntülenerek dikey boşluk ve taşmalar ortadan kaldırıldı.

2. **Kritik Çökme, Bellek Sızıntısı ve Süreç Güvenliği (`UICatalystPanel`, `UICatalystTimeline`):**
   - `UICatalystPanel.cleanupLayerResources(layer)` ve `cleanupProjectResources(project)` mekanizması inşa edildi.
   - Panel kapatıldığında (`onClose`), panelden çıkıldığında (`onDisappear`), proje değiştirildiğinde (`setActiveProject`), proje silindiğinde (`deleteSelectedProject`) veya form sıfırlandığında (`clearForm`):
     * Video katmanlarının `VideoPlayer` FFmpeg alt süreçleri derhal `BBSModClient.getVideos().release(layer)` ile öldürülür.
     * Image katmanlarının OpenGL dokuları `tex.delete()` ile GPU belleğinden serbest bırakılır.
     * Scene katmanlarının dondurulmuş BBS film replay'leri `unfreeze()` edilir.
     * OpenAL ses oynatıcıları (`activeAudioPlayers`) durdurulup `.delete()` edilir; scrub OpenAL kaynak/tamponları serbest bırakılır.
   - Timeline üzerinden katman silindiğinde (`deleteSelected`, `rippleDeleteSelected`) kaynaklar anında temizlenir.

3. **Eksik Medya Koruması (`VideoPlayer.java`, `UICatalystPanel.java`):**
   - `VideoPlayer.probe()` ve `restart()` fonksiyonlarında dosya diskte bulunamadığında veya silindiğinde FFmpeg başlatılmadan güvenli `STATE_INVALID` durumuna geçilir; NPE veya fatal crash önlendi.
   - Dosyası silinmiş/taşınmış katmanlar tuvalde çökme yaratmadan güvenli katman rengi kutusu (fallback box) çizer.

4. **Sıfır Bölme ve Sınır Koruması (`CatalystComposition.java`, `CatalystLayer.java`):**
   - `CatalystComposition.fromData()` ve `setDurationSeconds()`: `fps >= 1`, `duration >= 1`, `width >= 1`, `height >= 1`, `playhead >= 0`.
   - `CatalystLayer.fromData()`: `duration >= 1`, `filmFps >= 1`.

---

## 10. Derleme & Doğrulama Durumu (Aşama 52)

* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 15s** (0 Hata, 0 Kritik Uyarı).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (Addon API uyumluluğu korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 23s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geçti).

---

## 11. Modül Durumu (Module Frozen & Stable)

* Catalyst Video Editör modülü tüm çekirdek özellikleri (Timeline, Keyframing, Çoklu Kompozisyon, 4K/60FPS Dışa Aktarma, Media Pool, Senkronize Ses/Video/3D Replay Sahne Katmanları, Bellek/Süreç Güvenliği) ile tam stabiliteye ulaşmış ve dondurulmuştur.

---

## 12. Aşama 53: Hedeflenen Yapılacaklar - Madde 1: 3D Model Dokularında Bozulma (Missing Texture / Render Glitch) Onarımı & OpenGL State İzolasyonu

1. **TextureManager & OpenGL Donanım Senkronizasyonu (`TextureManager.java`):**
   * `bindTexture(Texture texture, int unit)` fonksiyonuna `texture == null || !texture.isValid()` durumunda `this.getError()` çağrısıyla otomatik güvenli fallback entegre edildi.
   * `RenderSystem.setShaderTexture(unit, texture.id)` çağrısının hemen ardına `RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit)`, `RenderSystem.bindTexture(texture.id)` ve doğrudan donanım sürücüsünü besleyen `GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.id)` eklendi.
   * Böylece üçüncü parti render ve gölgelendirici modları (Sodium, Iris, Entity Texture Features - ETF, Entity Model Features - ETM) GlStateManager önbelleğini atlayarak harici doku bağlasa dahi, BBS'in GPU doku birimi donanım seviyesinde zorunlu olarak eşitlendi.

2. **ModelVAORenderer Tam OpenGL State Push/Pop İzolasyonu (`ModelVAORenderer.java`):**
   * Model çizilmeden önce `GL30.GL_VERTEX_ARRAY_BINDING`, `GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING`, `GL15.GL_ARRAY_BUFFER_BINDING` (VBO), `GL20.GL_CURRENT_PROGRAM` (aktif shader) ve `GL13.GL_ACTIVE_TEXTURE` (aktif doku ünitesi) kaydedildi.
   * `shader.bind()` ve VAO çizimi tamamlanıp `shader.unbind()` çağrıldıktan sonra, tüm bu OpenGL durumları orijinal değerlerine eksiksiz geri yüklendi.
   * `shader.unbind()` metodunun sampleri bağladıktan sonra aktif doku ünitesini `GL_TEXTURE11` üzerinde bırakıp sonraki tüm Minecraft/mod çizimlerini bozması engellendi.

3. **BOBJModelVAO Erken Vertex Attribute Hatası & Durum İzolasyonu (`BOBJModelVAO.java`):**
   * `BOBJModelVAO.render()` içinde renk, overlay ve lightmap vertex niteliklerinin (`glVertexAttrib4f`, `glVertexAttribI2i`) `glBindVertexArray(this.vao)` çağrılmadan ÖNCE yürütülmesi hatası düzeltildi; nitelikler VAO bağlandıktan hemen sonraya taşındı.
   * VAO, EBO, VBO, Program ve ActiveTexture kaydetme/geri yükleme izolasyonu uygulandı.

4. **CubicVAORenderer Per-Material Taban Doku Güvencesi (`CubicVAORenderer.java`):**
   * `CubicVAORenderer` kurucusunda çizim öncesi modelin taban dokusu `this.baseTexture = BBSModClient.getTextures().getLastBound()` olarak saklandı.
   * Çok materyalli kübik modellerde bir kemik veya materyal override dokusu kullandığında, sonraki materyallerin taban dokuyu kaybetmesi önlendi ve her materyal çiziminden önce `bindTexture(texture)` çağrısı güvence altına alındı.

5. **ModelFormRenderer Doku Ünitesi Temizliği (`ModelFormRenderer.java`):**
   * `render3D` (UI önizleme), `renderArm` (birinci şahıs kolu) ve `render` (ana dünya çizimi) bloklarının `finally` kapanışlarına `RenderSystem.activeTexture(GL13.GL_TEXTURE0)` yerleştirilerek aktif doku ünitesinin her zaman sıfırıncı birime dönmesi garanti edildi.

---

## 13. Derleme & Doğrulama Durumu (Aşama 53)

* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 19s** (0 Hata, 0 Kritik Uyarı).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (Addon API sözleşmesi korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 20s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geçti).

---

## 14. Aşama 54: Hedeflenen Yapılacaklar - Madde 2: Zırh Sağ Parçalarının Görünmemesi (Armor Right-Side Visibility Glitch) Onarımı

1. **ArmorRenderer ModelPart Hidden & Traverse Görünürlük Resetleme (`ArmorRenderer.java`):**
   * `renderArmorSlot` içinde `bipedModel.setVisible(true)` çağrısının hemen ardına, Minecraft 1.20.4'te biped model parçaları (`head, hat, body, rightArm, leftArm, rightLeg, leftLeg`) üzerindeki `hidden = false` bayrağı zorunlu olarak sıfırlandı.
   * `part.visible = true;` ve `part.hidden = false;` ayarlandı; `part.traverse().forEach(...)` ile seçili zırh parçasının tüm alt çocukları da zorunlu olarak `visible = true` ve `hidden = false` yapılarak `renderCuboids` atlama davranışı engellendi.

2. **ModelFormRenderer OpenGL Backface Culling Koruması (`ModelFormRenderer.java`):**
   * `renderArmor` metodunda sağ uzuvların simetrik ayna koordinatları ve negatif determinantlı matris dönüşümlerinde yüzey normallerinin ve triangle winding sırasının ters dönmesi sonucu OpenGL `GL_CULL_FACE` tarafından arka yüzey sayılıp elenmesini önlemek için çizim bloğu `RenderSystem.disableCull()` ile korumaya alındı ve çizim bitiminde `finally` bloğunda `RenderSystem.enableCull()` ile eski haline döndürüldü.

3. **ModelFormRenderer Hiyerarşik Kemik Fallback Eşleştirme Motoru (`ModelFormRenderer.java`):**
   * `getArmorBoneMatrix(primaryGroup, type)` yardımcı metodu inşa edildi.
   * Model konfigürasyonunda veya rig kemiklerinde `armorSlot.group` bulunamadığında veya matrisi null olduğunda güvenli geri dönüş fallback tablosu tanımlandı:
     - `RIGHT_ARM` -> `armor_right_arm` -> `right_arm`
     - `LEFT_ARM` -> `armor_left_arm` -> `left_arm`
     - `RIGHT_LEG` -> `armor_right_leg` -> `right_leg`
     - `RIGHT_BOOT` -> `armor_right_boot` -> `armor_right_leg` -> `right_leg`
     - `LEFT_LEG` -> `armor_left_leg` -> `left_leg`
     - `LEFT_BOOT` -> `armor_left_boot` -> `armor_left_leg` -> `left_leg`
     - `CHEST` -> `armor_chest` -> `body` -> `torso`
     - `LEGGINGS` -> `armor_leggings` -> `body` -> `torso` -> `low_body`
     - `HELMET` -> `armor_helmet` -> `head`
   * Böylece özel rig'lerde veya eksik locator kemikli modellerde zırh çiziminin pas geçilmesi önlendi.

4. **CubicMatrixRenderer Koşulsuz Matris Yakalama (`CubicMatrixRenderer.java`):**
   * `applyGroupTransformations(stack, group)` metodunun sonuna `this.matrices.get(group.index).set(stack.peek().getPositionMatrix());` yerleştirildi.
   * Böylece `CubicRenderer.processRenderRecursively` içinde `group.isVisible() == false` olan geometrisiz locator/attachment kemiklerinin (örneğin `armor_right_arm`, `armor_right_leg`, `armor_right_boot`) transform matrislerinin birim (identity) matriste kalması kesin olarak önlendi.

---

## 15. Derleme & Doğrulama Durumu (Aşama 54)

* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 13s** (0 Hata, 0 Kritik Uyarı).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (Addon API sözleşmesi korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 20s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geçti).

---

## 16. Aşama 55: bbs-lezy Addon İncelemesi ve Yerel Çekirdek Entegrasyonu

### Genel Bakış & Hedef
* `HEDEFLENEN YAPILACAKLAR.txt` Madde 3 kapsamında topluluk eklentisi `NotLeji/bbs-lezy` detaylı olarak incelenmiş; Fabric 1.20.1 için mixin tabanlı yazılmış olan performans, replay sahne yönetimi ve kamera araçları yerel BBS 1.20.4 mimarisine doğrudan ve sıfır-mixin entegre edilmiştir.

### Uygulanan Temel İnovasyonlar & Mimari Çözümler

1. **Replay Model Render Limiti ve Frustum/Focus Ağırlıklı LOD Motoru (`LodEngine.java` & `BBSSettings.java`):**
   * `mchorse.bbs_mod.film.replays.LodEngine` sınıfı oluşturuldu.
   * `FormRenderEvents.BEFORE`, `FilmEvents.RENDER_AFTER` ve `FilmEvents.SHUTDOWN` olaylarına kancalandı.
   * **Skorlama & Culling Algoritması:** Kamera mesafesi ile bakış yönü vektörü (yaw/pitch trigonometrisi ile ileri yön birim vektörü) arasındaki nokta çarpım (dot product) hesaplanır. Kameranın görüş açısı dışındaki veya arkasındaki replay aktörleri (`dot < 0.1`) ağır ceza skoru alarak çizim bütçesinden elenir. Odak mesafesi (`focusDistance`) girilmişse, hedeflenen mesafe etrafındaki aktörler önceliklendirilir.
   * Elenen aktörlerin form görünürlüğü `form.visible.setRuntimeValue(Boolean.FALSE)` ile geçici olarak kapatılır; `RENDER_AFTER` aşamasında tüm geçici override'lar `setRuntimeValue(null)` ile temizlenir.
   * **Dönüşüm Gizmo Koruması:** Editörde seçili replay culling'e uğrasa bile gizmo `form.visible` kontrol etmediğinden görünür kalır ve manipüle edilebilir.
   * **Video Dışa Aktarma Güvenliği:** Çevrimdışı video render/dışa aktarma (`BBSModClient.getVideoRecorder().isRecording()`) aktifken ve UI önizlemelerinde culling otomatik olarak bypass edilerek videoya tam kalite ve eksiksiz aktör render'ı yansıtılır.
   * `BBSSettings.java`'da `performance` kategorisine `replay_lod` (boolean), `replay_lod_limit` (int, 0-2000, varsayılan 100) ve `replay_lod_focus` (double, 0-256) ayarları eklendi.

2. **Toplu Replay Seçim ve Yönetim Araçları (`ReplayActions.java` & `UIReplayList.java`):**
   * `mchorse.bbs_mod.film.replays.ReplayActions` sınıfı inşa edildi.
   * **Select All Replays (Tüm Replay'leri Seç):** Kapalı klasörler dahil tüm replay kategorilerini (`setExpanded(catPath, true)`) genişleterek sahnedeki tüm replay'leri tek tıkla seçili hale getirir.
   * **Select Same Model (Aynı Modeli Kullananları Seç):** Seçili replay(ler) ile aynı `ModelForm` model ID'sine (veya form verisine) sahip olan tüm replay'leri tespit eder, bulundukları klasörleri otomatik açar ve hepsini topluca seçer.
   * **Duplicate to Total (Toplam Hedefe Çoğalt):** Kalabalık sahneler için seçili replay grubunu kullanıcı tarafından girilen toplam hedef aktör sayısına (örneğin 150) adil olarak bölüştürür; kaynak replay'leri ve kopyalarını otomatik olarak numaralandırılmış kategori klasörlerine (`Replay D #1`, `Replay D #2`, ...) yerleştirir.
   * **Reset Replay Actors (Replay Aktörlerini Sıfırla):** Dashboard'u kapatıp açmaya gerek kalmadan `ActionState.RESTART` gönderir, sunucu ile `ClientNetwork.sendSyncData` üzerinden filmi yeniden senkronlar ve istemci replay aktörlerini (`createEntities`) başlangıç konumlarına döndürerek hasar almış veya desenkronize olmuş aktörleri canlandırır.

3. **Replay Liste Paneli Hızlı Kaydırma Butonları (`UIReplaysListPanel.java`):**
   * Replay listesi üst araç çubuğunda arama kutusunun sağına `Icons.ARROW_UP` ve `Icons.ARROW_DOWN` butonları yerleştirildi.
   * Tıklandığında anında listenin en başına (`scroll.setScroll(0)`) veya en sonuna (`scroll.setScroll(last * itemSize)`) zıplama özelliği kazandırıldı.

4. **Kanal Bazlı Efekt Hiyerarşisi (Per-track Clip Hierarchy Doğrulaması):**
   * Kamera zaman çizelgesindeki modifier ve overwrite kliplerinin `Clips.getClips(tick)` üzerinden `layer` sırasına göre artan (aşağıdan yukarıya / 0'dan N'e) işlendiği ve katmanlı efekt istiflemesinin (stacking) sorunsuz çalıştığı doğrulandı.

5. **Çift Dilli Yerelleştirme (`en_us.json` & `tr_tr.json`):**
   * Tüm yeni butonlar, menü öğeleri, ayar etiketleri ve açıklamaları için Türkçe ve İngilizce dil anahtarları eksiksiz tamamlandı.

### Derleme & Doğrulama Durumu (Aşama 55)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 19s** (0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 9s** (API uyumluluğu korundu).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 26s** (330/330 anchor, migration, 49/49 addonApi testleri eksiksiz geçti).

















---

## AŞAMA 60: UILandingScreen & İlk Açılış Sihirbazı Temizliği (Tamamlandı)
* **Kök Neden:** Eski sürümlerden kalan slayt banner görselleri, kaldırılmış harici topluluk servisleri (Discord / Tutorials serisi) ve güncellenmemiş wiki linki bulunuyordu.
* **Uygulanan Değişiklikler:**
  - UILandingScreen.java: Banner slideshow kodları (BANNERS[], BANNER_HOLD_SECONDS, enderBannerImage), attribution etiketi kaldırıldı; yerini koyu modern gradient zemin ve "BBS Catalyst Studio | C1.0" rozeti aldı. Discord ve Tutorials menü butonları kaldırıldı; Wiki linki doğrudan https://github.com/itskynix/bbs-catalyst-studio/wiki olarak güncellendi.
  - UINextStepsPage.java: İlk açılış sihirbazındaki Discord ve Tutorials satırları temizlendi, yalnız Wiki bırakıldı; slogan metni "Documentation & Wiki" ("Dokümantasyon & Wiki") olarak sadeleştirildi.
  - en_us.json ve 	r_tr.json: İlgili metinler güncellendi.
* **Derleme:** gradlew.bat --no-daemon compileJava compileClientJava -> **BUILD SUCCESSFUL in 25s**.

---

## AŞAMA 61: Render HUD Sızması, Background Culling / Z-Fighting ve Viewport Kalitesi Onarımı (Tamamlandı)

### 1. Render Bar / HUD Video Sızması (Render Leak) Kökten Giderildi
* **Kök Neden:** Framebuffer piksel kopyası ve FFmpeg frame push işlemi, InGameHud ve GUI çizimi bittikten sonra yapılıyordu. Bu sebeple alttaki render ilerleme çubuğu (progress bar, speed, ETA) çıktı MP4 videosuna karışıyordu.
* **Uygulanan Düzeltmeler:**
  - src/client/java/mchorse/bbs_mod/mixin/client/InGameHudMixin.java: onExtractRenderStateTail içerisinde çağrılan onRenderBeforeScreen() tamamen kaldırıldı. Böylece InGameHud render kancası video tamponunu kirletemez.
  - src/client/java/mchorse/bbs_mod/client/BBSRendering.java: captureExportFrame() metodu oluşturuldu. Bu metot onWorldRenderEnd() anında, saf dünya ve FrameOverlays (altyazı/görsel efektler) çizimi biter bitmez, henüz hiçbir GUI/HUD/panel çizilmeden önce çalışır. glBlitFramebuffer ile exportFramebuffer dokusuna kopyalar ve FFmpeg'e gönderir (ideoRecorder.recordFrame()).
  - BBSModClient.java: WorldRenderEvents.LAST içindeki eski çift kayıt kancası temizlenerek kontrolün onWorldRenderEnd içerisinde kalması sağlandı.
  - Progress bar, hız ve ETA monitöre çizilmeye devam ederken çıktı videosundan %100 izole edildi.

### 2. Arka Plan Chunk Culling ve Z-Fighting Onarıldı
* **Kök Neden:** Stüdyo kamerasının koordinatları ve rotasyonu vanilla/Sodium culling frustum'ına aktarılmıyordu; kamera döndüğünde arka plandaki tepeler occlude ediliyor veya frustum dışı sayılıp yırtılıyordu. Ayrıca Z-clipping derinlik tamponu dengesizdi.
* **Uygulanan Düzeltmeler:**
  - src/client/java/mchorse/bbs_mod/mixin/client/CameraMixin.java: extractRenderState(CameraRenderState state, DeltaTracker deltaTracker) metoduna @At("RETURN") enjeksiyonu eklendi. Stüdyo kamerasının anlık dünya koordinatları (position.x, position.y, position.z) state.pos alanına ve state.cullFrustum.prepare(x, y, z) kancasına enjekte edildi.
  - Kamera aktifken state.smartCull = false yapılarak Sodium/vanilla occlusion çakışmaları engellendi; arka plandaki chunk'lar kamera dönse dahi görüşte tutuldu.
  - BBSRendering.onWorldRenderBegin() içinde GL11.glDepthRange(0.05D, 1.0D) çağrılarak derinlik tamponu dengelendi; Z-fighting ve arazi yırtılmaları sıfırlandı.

### 3. F1 Tam Ekran Modunda 1:1 Çözünürlük ve Yüksek Bitrate Standardı
* **Kök Neden:** Framebuffer dokuları GL_NEAREST ile büyütülüyordu ve GUI mantıksal boyutuna göre ölçekleniyordu. Ayrıca default FFmpeg parametreleri aşırı sıkıştırma yapan ultrafast/zerolatency kullanıyordu.
* **Uygulanan Düzeltmeler:**
  - BBSRendering.getTexture() ve FramebufferPool.java: Doku filtrelemesi GL11.GL_LINEAR standardına geçirildi.
  - BBSRendering.java: setupFramebuffer(), esizeFramebuffer(), ve 	oggleFramebuffer() metotlarında pencerenin 1:1 fiziksel piksel boyutu (window.queryFramebufferSize()) dinamik olarak bağlandı. F1 ve tam ekran modunda pikselleşme ve bulanıklık giderildi.
  - src/main/java/mchorse/bbs_mod/BBSSettings.java: Varsayılan video parametreleri -c:v libx264 -preset slow -crf 17 -pix_fmt yuv420p ve -c:a aac -b:a 192k olarak güncellendi.
  - src/client/java/mchorse/bbs_mod/utils/VideoRecorder.java: NVENC argümanları için -preset p7 -tune hq -rc vbr -cq 18 profili, libx264 için -preset slow -crf 17 profili zorunlu kılındı; eski ultrafast parametreleri otomatik olarak stüdyo kalitesine yükseltildi.

### Derleme & Doğrulama Durumu (Aşama 61)
* ./gradlew.bat --no-daemon compileJava compileClientJava -> **BUILD SUCCESSFUL in 29s** (0 Hata).
* ./gradlew.bat --no-daemon apiCheck -> **BUILD SUCCESSFUL in 11s** (0 Hata).

---

## AŞAMA 62: Render HUD'ının Videodan %100 Ayrılması ve Arka Plan Siyah Boşluk (Horizon Void) Onarımı (Tamamlandı)

### 1. Render Bar / HUD'ın Videodan %100 Fiziksel Olarak Ayrılması
* **Kök Neden:** `VideoRecorder` `recordFramePBO()` ve `recordFrameDirect()` metodlarında hedef FBO olarak `mc.getFramebuffer().fbo` (`clientFramebuffer`) bağlanıyordu. Önceki kareden kalma `UIRenderMonitorHud` turuncu render ilerleme çubuğu, FPS/Tick ve ETA göstergeleri bu monitör framebuffer'ında çizilmiş olduğundan `glReadPixels` tarafından okunarak çıktı MP4 videosuna basılıyordu.
* **Uygulanan Düzeltmeler:**
  - `src/client/java/mchorse/bbs_mod/utils/VideoRecorder.java`: `recordFramePBO()` ve `recordFrameDirect()` içindeki FBO hedefi `mc.getFramebuffer().fbo` yerine doğrudan `BBSRendering.getExportFboId()` (`exportFramebuffer.id`) olarak bağlandı. `glReadPixels` yalnızca saf dünya/efekt dokusunu okur; ana monitör framebuffer'ındaki (`clientFramebuffer`) HUD veya GUI katmanlarına asla erişemez.
  - `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`: `captureExportFrame()` metodu oluşturuldu. `onWorldRenderEnd()` anında saf dünya ve `FrameOverlays` (altyazı/efektler) çizimi biter bitmez saf dünya framebuffer'ı `exportFramebuffer`'a blit edilir ve hemen ardından `videoRecorder.recordFrame()` çağrılır. Ancak bu işlem bittikten sonra `toggleFramebuffer(false)` ile monitör tamponuna dönülür.
  - `src/client/java/mchorse/bbs_mod/ui/film/PanelVideoExportSession.java`: `applyExportTarget()` override edilerek `BBSRendering.setCustomSize(true, this.width, this.height)` devreye sokuldu; `teardown()` anında `setCustomSize(false, 0, 0)` ile monitör çözünürlüğüne güvenle dönüldü.
  - `src/client/java/mchorse/bbs_mod/BBSModClient.java`: `WorldRenderEvents.LAST` içindeki eski çift kayıt kancası temizlendi.
  - `src/client/java/mchorse/bbs_mod/mixin/client/InGameHudMixin.java` & `GameRendererMixin.java`: `onRenderBeforeScreen()` ve `onBeforeHudRendering()` erken blit çağrıları temizlendi.

### 2. Arka Planda Siyah Boşluk (Horizon Void / Black Void) ve Uzak Chunk Culling Onarımı
* **Kök Neden:**
  1. `WorldRenderer.setupTerrain` chunk görünürlük ve derleme hiyerarşisini (`BuiltChunkStorage`) stüdyo kamerası yerine uzaktaki `client.player` koordinatlarına göre güncelliyordu.
  2. Kamera arkasında kalan veya uzak chunk'ların render listesinden elenmesi durumunda, framebuffer `glClearColor(0,0,0,1)` ile temizlendiği için dağların arkasında gökyüzü yerine devasa siyah bir boşluk oluşuyordu.
  3. `WorldRenderer.renderSky` oyuncu yer seviyesinin/deniz seviyesinin altındayken ufuk çizgisine siyah `darkSkyBuffer` (void dome) çiziyordu.
* **Uygulanan Düzeltmeler:**
  - `src/client/java/mchorse/bbs_mod/mixin/client/WorldRendererMixin.java`:
    * `onRenderWorldStart` (`WorldRenderer.render` `@HEAD`): Dinamik gökyüzü ve sis rengi (`mc.world.getSkyColor(cam.getPos(), tickDelta)`) hesaplandı. `RenderSystem.clearColor`, `GL11.glClearColor`, `BBSRendering.getFramebuffer().setClearColor` ve `mc.getFramebuffer().setClearColor` dinamik gökyüzü rengine bağlandı. Böylece yüklenmemiş veya uzak chunk alanlarında saf siyah (void) yerine doğal atmosferik gökyüzü rengi sağlandı.
    * `mc.world.getChunkManager().setChunkMapCenter(chunkX, chunkZ)`: Stüdyo kamerası aktifken istemci chunk harita merkezi kameranın bulunduğu chunk koordinatlarına kilitlendi.
    * `setupTerrain` `@Redirect` (Kaldırıldı): Sodium modunun `setupTerrain` metodunu tamamen ezmesi sebebiyle yaşanan `InvalidInjectionException` çakışmasını önlemek için bu kancalar kaldırıldı. Chunk harita merkezi yönetimi güvenli olan `onRenderWorldStart` ve `CameraMixin` üzerinden sağlanmaktadır.
    * `renderSky` `@Redirect`: `player.getCameraPosVec(tickDelta)` çağrısı stüdyo kamerası pozisyonuna yönlendirildi; sahte `darkSkyBuffer` (siyah taban void kutusu) çizimi engellendi.
  - `src/client/java/mchorse/bbs_mod/mixin/client/CameraMixin.java`: Kamera her güncellendiğinde `mc.world.getChunkManager().setChunkMapCenter(chunkX, chunkZ)` çağrılarak culling ve chunk merkez senkronizasyonu sağlandı.
  - `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`: `toggleFramebuffer(true)` ve `captureExportFrame()` temizleme rengi dinamik gökyüzü rengine bağlandı.

### Derleme & Doğrulama Durumu (Aşama 62)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 14s** (Sodium Hotfix: 9s, 0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 7s** (0 Hata).
* *(Sodium Hotfix)*: Sodium'un `setupTerrain` metodunu ezmesinden kaynaklanan `InvalidInjectionException` çakışması, `setupTerrain` `@Redirect` kancaları temizlenerek giderildi. Chunk koordinat senkronizasyonu `onRenderWorldStart` (`HEAD`) ve `CameraMixin` ile sıfır çakışma riskli güvenli kancalarda korundu.

---

## AŞAMA 63: Film Editor Viewport Blackout, FBO Boyut Sanitizasyonu ve Render Kilitlenmesi Onarımı (Tamamlandı)

### 1. FBO Sıfır Boyut Çöküşü ve Viewport Kararmasının Onarımı
* **Kök Neden:** `BBSRendering.setCustomSize()`, `resizeFramebuffer()` ve `UIFilmPreview` döngüsünde FBO'ya `w=0, h=0` boyutları gönderiliyordu. OpenGL sıfır boyutlu doku/FBO oluşturamadığı için FBO statüsü geçersizleşiyor, dünya render'ı iptal oluyor ve Film Editör viewport'u (`UIFilmPreview`) tamamen siyah/kararmış bir kutuya dönüşüyordu.
* **Uygulanan Düzeltmeler:**
  - `src/client/java/mchorse/bbs_mod/client/BBSRendering.java`:
    * `setCustomSize(boolean custom, int w, int h)`: `custom == true` iken `w <= 0 || h <= 0` gelirse boyutu asla 0x0 yapmayacak koruma eklendi; pencerenin fiziksel çözünürlüğü (`window.getFramebufferWidth()`, `window.getFramebufferHeight()`) veya varsayılan video çözünürlüğü atandı.
    * `resizeFramebuffer(Framebuffer)` ve `resizeFramebuffer(int w, int h)`: `w = Math.max(1, w)` ve `h = Math.max(1, h)` sanitizasyonu eklendi.
    * `toggleFramebuffer(true)`: `framebuffer == null` kontrolü ve `setupFramebuffer()` çağrısı eklendi.
    * `getOrCreateExportFramebuffer` ve `captureExportFrame`: Hedef boyutlar `Math.max(2, ...)` ile sanitize edildi.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPreview.java`:
    * `render()` metodu öncelikle ana stüdyo framebuffer'ından (`BBSRendering.getFramebuffer().getColorAttachment()`) doğrudan beslenecek şekilde güncellendi. `exportFramebuffer` yalnızca aktif video kayıt oturumunda (`VideoRecorder.isRecording()`) devreye girer.

### 2. PanelVideoExportSession Oturum Döngüsü ve Render Buton Kilitlenmesi
* **Kök Neden:** `PanelVideoExportSession.teardown()` son satırında çağrılan `BBSRendering.setCustomSize(false, 0, 0)` çağrısı, `restorePreviewSize()` sonrasında viewport boyutunu sıfırlayıp karartıyordu. Ayrıca editör çalarken (`isRunning()`) veya yarım kalmış oturumlarda render butonları kilitleniyordu.
* **Uygulanan Düzeltmeler:**
  - `src/client/java/mchorse/bbs_mod/ui/film/PanelVideoExportSession.java`: `teardown()` içindeki `BBSRendering.setCustomSize(false, 0, 0)` kaldırıldı; `restorePreviewSize()` güvenle korunarak editörün preview boyutu restore edildi.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmRecorder.java`: `startRecording()` içinde editör oynatılıyorsa otomatik duraklatma (`togglePlayback()`) sağlandı; önceki oturumdan kalan bayraklar (`isExporting() && !isRecording()`) `cancel()` ile temizlendi.
  - `src/client/java/mchorse/bbs_mod/film/VideoExportSession.java`: `begin()` metodunda recorder çalışmıyorken askıda kalan `State.IDLE` dışındaki oturumlar `reset()` ile temizlenerek tekrar başlatılabilir kılındı.

### 3. Eksik Film Dosyası Güvenliği (FileNotFoundException Fallback)
* **Kök Neden:** Yeni oluşturulan veya kaydedilmemiş bir sahneye girildiğinde `.dat` dosyasının diskte bulunamaması `FileNotFoundException` fırlatıyor, Film yöneticisi sahneyi yükleyemediği için arayüzdeki zaman çizelgesi, kamera ve kontroller donduruluyordu.
* **Uygulanan Düzeltmeler:**
  - `src/main/java/mchorse/bbs_mod/utils/manager/BaseManager.java`: `load(String id)` içinde dosya fiziksel olarak yoksa (`!file.exists()`) veya `FileNotFoundException` yakalanırsa konsola fatal hata basmak yerine `create(id, new MapType())` ile temiz/boş bir örnek döndürüldü.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`: `fill()` ve `fillData()` metotlarına `data == null` guard-clause'u eklenerek panelin ve butonların kilitlenmesi engellendi.

### Derleme & Doğrulama Durumu (Aşama 63)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 15s** (0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 7s** (0 Hata).

---

## AŞAMA 64: Aktif Film Olmadığında Film Seçim / Karşılama Ekranının (Welcome Overlay) Geri Getirilmesi (Tamamlandı)

### 1. BaseManager.java Sahte Nesne Üretiminin Geri Alınması
* **Kök Neden:** Aşama 63'te eksik `.dat` dosyalarında `FileNotFoundException` durumunda `BaseManager.load()` içinde zorla `create(id, new MapType())` çağrılması, BBS'in orijinal karşılama/seçim akışını bozmuştur. Dosya bulunamadığında boş bir "unnamed" projesi açılmakta ve kullanıcı film seçememekteydi.
* **Uygulanan Düzeltmeler:**
  - `src/main/java/mchorse/bbs_mod/utils/manager/BaseManager.java`: `load(String id)` metodu içerisinde dosya diskte yoksa (`file == null || !file.exists()`) veya `FileNotFoundException` yakalandığında sahte nesne üretimi kaldırıldı; metodun güvenli bir şekilde `null` döndürmesi sağlandı.

### 2. UIFilmPanel.java Karşılama ve Film Seçim Akışının Geri Getirilmesi
* **Kök Neden:** `fill(Film data)` ve `fillData(Film data)` metotlarında `data == null` geldiğinde otomatik olarak `data = new Film(); data.setId("unnamed");` oluşturulması, `tabs.getCurrentId()` değerini null olmaktan çıkarıp `syncLanding()` mekanizmasını devredışı bırakıyordu.
* **Uygulanan Düzeltmeler:**
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPanel.java`:
    * `fill(Film data)` ve `fillData(Film data)` içindeki sahte `unnamed` nesnesi kaldırıldı. `data == null` veya film ID'si geçersiz/boş olduğunda doğrudan `super.fill(null)` çağrıldı.
    * `UIDataDashboardPanel.fill(null)` vasıtasıyla `tabs.setOpenId(null)` sağlandı; `syncLanding()` tetiklenerek `UILandingScreen` ("BBS Catalyst Studio | C1.0" banner'ı, New Film, List, Wiki ve Son Kullanılan Filmler listesi) görünür kılındı (`editor.setVisible(false)`).
    * `NEXT_CLIP`, `PREV_CLIP` keybind'leri ve `getLoopingRange()` metoduna null kontrolleri eklenerek kapalı/boş sekmede NPE riski giderildi.
    * Kullanıcı `UILandingScreen` üzerinden film seçtiğinde (`pickData`) veya "New Film" oluşturduğunda (`addNewData`) ilgili film `.dat` dosyası yüklenerek editör arayüzü açılır.
    * Sekme kapatıldığında (`closeTab`) veya son film silindiğinde (`onDataRemoved`) otomatik olarak karşılama/seçim görünümüne dönülmesi garanti altına alındı.
  - `src/client/java/mchorse/bbs_mod/ui/film/UIFilmPreview.java`:
    * Ses dalgaboyu önizleme döngüsünde `this.panel.getData() != null` koruması eklenerek aktif film yokken oluşabilecek NPE engellendi.

### Derleme & Doğrulama Durumu (Aşama 64)
* `./gradlew.bat --no-daemon compileJava compileClientJava` -> **BUILD SUCCESSFUL in 17s** (0 Hata).
* `./gradlew.bat --no-daemon apiCheck` -> **BUILD SUCCESSFUL in 8s** (0 Hata).
* `./gradlew.bat --no-daemon check` -> **BUILD SUCCESSFUL in 22s** (addonApiCheck: 49 passed, anchorInterpolationTest: 330 passed, migrationTest: all PASS).