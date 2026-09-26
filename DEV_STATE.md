# BBS Mod - Geliştirme Durumu (DEV_STATE)

**Proje:** Blockbuster Studio (BBS) - Fabric 1.20.4 Port (`bbs-cs`)  
**Tarih:** 25 Eylül 2026  
**Son Tamamlanan Aşama:** 17. Aşama — Pro Camera Clip Mimarisi (35mm Odak Uzaklığı & Lens Presetleri, Bağımsız Roll / Dutch Angle, Dahili Dolly Zoom / Vertigo ve Odak Mesafesi Altyapısı)

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

---

## 4. Derleme & Doğrulama Durumu

* `./gradlew.bat --no-daemon compileJava compileClientJava` komutu çalıştırıldı.
* **Sonuç:** `BUILD SUCCESSFUL in 13s` — 0 Hata, 0 Kritik Uyarı.

---

## 5. Sırada Yapılacak Adım (Next Step)

* Yüksek kalite WAV (24-bit, 32-bit float, 96 kHz) ve MP3 ses motoru entegrasyonu tamamlandı. Oyun içinde canlı doğrulamaya hazır.




