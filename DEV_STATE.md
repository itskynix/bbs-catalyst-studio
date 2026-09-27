# BBS Mod - Geliştirme Durumu (DEV_STATE)

**Proje:** Blockbuster Studio (BBS) - Fabric 1.20.4 Port (`bbs-cs`)  
**Tarih:** 25 Eylül 2026  
**Son Tamamlanan Aşama:** 47. Aşama — Alt Ses Katmanı Oynatma Önceliği, 2K/4K Video Frame Optimizasyonu ve Stereo OpenAL Desteği: getActiveAudioLayer iki aşamalı öncelik taraması (AUDIO katmanları her zaman öncelikli, VIDEO katmanları volume > 0 şartıyla), VideoPlayer dinamik setMaxSize ve renderWidth/renderHeight ölçekleme (FFmpeg -vf scale ile 4K'da 33MB'tan ~8MB'a düşüş), 16ms render bütçeli frame skipping/lag koruması ve OpenAL 2D stereo (AL_SOURCE_RELATIVE + AL_ROLLOFF_FACTOR=0 + setPosition(0,0,0)).

---

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
* **Sonuç (44. Aşama):** `BUILD SUCCESSFUL in 10s` — 0 Hata, 0 Kritik Uyarı.

---

## 5. Sırada Yapılacak Adım (Next Step)

* Catalyst Studio 44. Aşama (Dinamik Video Aspect Ratio, Z Rotasyon Matrisi, Canlı Film Viewport Render'ı, Split In-Point Media Offset'i, Görünür Settings Modalı, Akıcı GPU Video Dokusu ve Çözünürlük Bağımsız Sanal Koordinat Sistemi) başarıyla tamamlandı. Sıradaki aşamalarda katman efekt zincirleri (Effects / Filters), maskeleme (Masks) ve keyframe tabanlı canlandırma (Transform Keyframing) geliştirilebilir.












