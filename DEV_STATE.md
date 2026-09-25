# BBS Mod - Geliştirme Durumu (DEV_STATE)

**Proje:** Blockbuster Studio (BBS) - Fabric 1.20.4 Port (`bbs-cs`)  
**Tarih:** 25 Eylül 2026  
**Son Tamamlanan Aşama:** 16. Aşama — In/Out Odak Tuzağı Çözümü ('I', 'O', 'Alt+X'), Viewport Altın Oran & Sinematik Maskeleme Barları, ve Gelişmiş Shake Clip Motoru (Sine, Cosine, Perlin, Math)

---

## 1. Mimari Genel Bakış

BBS moduna, DaVinci Resolve ve modern prodüksiyon araçlarından esinlenen iki devrimsel sistem entegre edilmiştir:
1. **DaVinci Resolve "Deliver" Render & Export Pipeline:** Donanım hızlandırmalı (NVENC, AMF, QSV, ProRes, HQ GIF), tam ekran Cinema Monitor HUD, dinamik başlatıcı bağımsız çıktı dizini, doğrudan hedef export FBO'sundan okuma yapan 4-bayt RGBA `glReadPixels` motoru, katı bitrate kilitleme (`-minrate`, `-maxrate`, `-bufsize`, `-cbr_padding`), Render Queue iş yöneticisi, In/Out ve Custom Range aralık seçimi, canlı SMPTE monitörü ve atmosferik mesafe sisi (terrain fog) garantisi.
2. **Canlı Çok Kanallı Replay Kaydı (Live Multi-Track Replay Recording) & DaVinci Kurgu Kısayolları:** Jilet kesim (`C`), çoklu katman kesimi (Multi-Track Razor Cut), dalgalı kırpma / ripple trim (`Q` & `E`) track izolasyonu ve No-Overlap çakışma önleyici koruma, kesim noktası gezintisi (`Yukarı / Aşağı Ok`), klip susturma/mute toggle (`D`), otomatik In/Out mark clip (`X`), In/Out işaretleme (`I`, `O`, `Alt + X`), görsel cetvel bandı, cetvel tabanlı scrubbing, klipsiz alana sürükleyerek doğrudan sarı kutu çoklu seçimi (Marquee Selection) ve tek tıkla seçim kaldırma (Deselect) mimarisi.

---

## 2. Son Yapılan Kritik Düzeltmeler ve Geliştirmeler (16. Aşama)

### A. 'I', 'O' (In/Out) ve 'Alt+X' (Clear In/Out) Kesin Tuş Düzeltmesi (Focus Trap Çözümü)
* **Kök Neden & Çözüm:**
  - Olaylar daha önce sadece `UIClips.subKeyPressed` içine gömülü kaldığından, odak önizleme veya panel üzerinde olduğunda tuşlar yakalanamıyordu.
  - `UIFilmPanel.java`: En üst seviye olay yönlendiricisi olan `handleKeyPressed(UIContext context)` ve `subKeyPressed(UIContext context)` metodlarına evrensel In/Out dinleyicisi eklendi (`handleTimelineInOutKeys`):
    * Herhangi bir metin kutusu odakta değilse (`!context.isFocused()`):
      - `I` tuşu: Oynatma kafasının o anki tick konumunu In noktası olarak kaydeder (`clips.setInPoint(cursorTick)`).
      - `O` tuşu: Oynatma kafasının o anki tick konumunu Out noktası olarak kaydeder (`clips.setOutPoint(cursorTick)`).
      - `Alt + X` tuşu: Seçili In ve Out aralığını tamamen temizler (`clips.clearInOut()`).
    * Olay başarıyla tüketilerek hiçbir alt/üst panele kaçırılmadan timeline ile senkronize edilir.

### B. Viewport Ayarları: Altın Oran (Golden Ratio) ve Sinematik Maskeleme Barları (Cinematic Aspect Ratio Bars)
* **Altın Oran Kılavuz Çizgileri (Golden Ratio / Phi Grid):**
  - `BBSSettings.java`: `editorGoldenRatio` ayarı eklendi.
  - `UIFilmPreview.java`: Altın oran matematiği ($1 : 0.618 : 1$ orantısı, $x + w \times 0.382$ ve $x + w \times 0.618$) kullanılarak rehber çizgileri mevcut `Guides color` rengiyle viewport üzerine çizilir.
* **Sinematik En-Boy Oranı Maskeleme Barları (Cinematic Aspect Ratio Bars):**
  - `BBSSettings.java`: `editorCinematicAspectRatio` ayarı açılır menü (modes) olarak tanımlandı: `Off`, `2.39:1`, `1.85:1`, `4:3`.
  - `UIFilmPreview.java`: Seçilen orana göre viewport alanını hesaplar ve görüntünün üst/alt (letterbox) veya sağ/sol (pillarbox) kenarlarına yarı saydam siyah (`0xd0000000`) sinematik maskeleme barlarını çizer. Kurgucu monitörde doğrudan sinematik kadrajı görür.

### C. Gelişmiş Shake Clip (Sallantı Klibi) Motoru
* **4 Farklı Sallantı Modu (`ShakeClip.java`):**
  1. `Sine`: Geleneksel sinüs dalgalı salınım (geriye dönük tam uyumluluk).
  2. `Cosine`: Faz kaymalı kosinüs dalgalı salınım.
  3. `Perlin Noise`: Organik, elde taşınan (handheld) kamera hissi veren 3 oktavlı fbm (fractal brownian motion) pürüzsüz gürültü algoritması.
  4. `Math Expression`: Kullanıcının dinamik formül yazabilmesi (örn: `sin(t * 15) * 0.5 + noise(t * 5)`).
* **Ek Parametreler ve Kontroller:**
  - `Frequency` (Sallantı hızı çarpanı)
  - `Amplitude` (Sallantı gücü)
  - `Rotational Weight` (Dönme ağırlığı: Pitch, Yaw, Roll çarpanı)
  - `Positional Weight` (Konum ağırlığı: X, Y, Z çarpanı)
  - `NoiseFunction.java`: Matematik motoruna (`MathBuilder`) `noise(x, [channel])` fonksiyonu tescil edildi.
* **Kullanıcı Arayüzü (`UIShakeClip.java`):**
  - Mod seçim düğmesi (`UICirculate`: Sine, Cosine, Perlin Noise, Math Expression).
  - Frekans, genlik, dönme ve konum ağırlıkları için trackpad girişleri.
  - Math Expression modu seçildiğinde dinamik olarak açılan `UITextboxHelp` formül kutusu.

---

## 3. Derleme & Doğrulama Durumu

* `./gradlew.bat --no-daemon compileJava compileClientJava` komutu çalıştırıldı.
* **Sonuç:** `BUILD SUCCESSFUL in 16s` — 0 Hata, 0 Kritik Uyarı.

---

## 4. Sırada Yapılacak Adım (Next Step)

* **Next Step:** Oyun İçi Canlı Doğrulama
  1. `.\gradlew.bat --no-daemon runClient` ile oyunun başlatılması.
  2. Timeline üzerinde hiçbir kutu odakta değilken `I`, `O` ve `Alt + X` kısayollarının her noktada anında tepki verdiğinin doğrulanması.
  3. `Settings -> Viewport` altından Altın Oran kılavuzunun ve `2.39:1` / `1.85:1` sinematik barlarının test edilmesi.
  4. Kamera kurgusunda `Shake` klibi eklenip `Perlin Noise` ve `Math Expression` modlarının kamera üzerindeki organik etkisinin doğrulanması.
