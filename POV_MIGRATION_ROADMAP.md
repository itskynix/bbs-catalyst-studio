# BBS CATALYST STUDIO (1.20.4 FABRIC)
## ÖZGÜN POV (BİRİNCİ ŞAHIS) SİSTEMİ MİMARİ YOL HARİTASI VE FAZ PLANI

Bu doküman, BBS Catalyst Studio projesinden silinen üçüncü taraf telifli eklentinin yerine; BBS CS'in kendi çekirdek mimarisi, `FormRenderer`, `UIOverlayPanel` ve `DopeSheet` altyapısı üzerine inşa edilecek özgün POV kurgu sisteminin faz bazlı uygulama planıdır.

---

### GENEL MİMARİ PRENSİPLER (CLEAN-ROOM STANDARDI)
1. **Harici Kod / Decompile Yasağı:** Silinen eklentinin decompile sınıfları referans alınmaz; yalnızca kullanıcı davranış tarifleri ve görsel arayüz hedefleri doğrultusunda BBS CS çekirdeğine özgün kod yazılır.
2. **Faz Bazlı İlerleme:** Her faz bağımsız bir alt modül olarak kodlanır, `./gradlew` derlemesi (`BUILD SUCCESSFUL`) alınır ve onaylanmadan bir sonraki faza geçilmez.
3. **Modüler Entegrasyon:** POV bir "eklenti" gibi değil, Film motoru ve Replay sisteminin doğal bir uzantısı olarak konumlandırılır.

---

## 🧭 FAZ LİSTESİ

### FAZ 1: TEMEL OMURGA, EDİTÖR GİRİŞİ VE POV TIMELINE
*Hedef: Arayüz butonları, Dope Sheet ayrımı ve aktör kurgu kanalları.*

* [ ] **1.1 Sağ Üst Başlık Butonu:** `UIFilmPanel` araç çubuğuna özgün `Open POV Editor` butonunun ve kısayolunun eklenmesi (`Icons.USER` veya kamera ikonu).
* [ ] **1.2 POV Timeline Katmanı:** Aktör replay editöründe (`player/*`, `emoticons/*`) `Action clips timeline` üzerinde ayrı bir `POV Timeline` katmanının açılması.
* [ ] **1.3 Dope Sheet Kanallarının Tanımlanması:**
  * **Hotbar Dope Sheet:** Slot seçimleri, eşya yerleşim kanalları.
  * **Hands Dope Sheet:** `Right/Left Hand Visible`, `Right/Left Swing`, `Main/Offhand Equip`, `Bob Phase`, `Bob Strength`, `Render Pitch/Yaw`.
  * **Vision Dope Sheet:** Göz açısı, FOV ve kafa dönüş kanalları.
* [ ] **1.4 Gezinme Butonları:** POV Timeline içinde Dope Sheet'ten Replay paneline geri dönüş ve `POV Action clips timeline` geçiş butonlarının yerleştirilmesi.

---

### FAZ 2: 16:9 POV MODEL EDİTÖRÜ VE REPLAY KONTROLLERİ
*Hedef: Kol, el ve eşya pozlamalarının görsel olarak yapılacağı vizör ekranı.*

* [ ] **2.1 16:9 FPV Görsel Tuvali:** Kameranın birinci şahıs kadrajını simüle eden 16:9 çerçeveli önizleme alanı.
* [ ] **2.2 Kemik Ağacı ve Pozlama:** `right_arm`, `left_arm`, `right_arm_item`, `armor_*` kemiklerinin ofset (X, Y, Z), rotasyon ve ebeveynlik (parenting) kontrolleri.
* [ ] **2.3 Serbest Kamera (Free Camera) Toggle'ı:**
  * **Açık (Free):** Kamerayı fare ile serbest hareket ettirip elleri/kemikleri dışarıdan izleme modu.
  * **Kapalı (FPS Lock):** Kameranın tam birinci şahıs açısına kilitlenip oyun içi vizörü birebir yansıtması.
* [ ] **2.4 Replay Paneli POV Özellikleri:** Replay sol paneline `POV Body parts` listesi, `Hardcore Look` ve `Camera Shake` toggle kontrollerinin entegre edilmesi.

---

### FAZ 3: GELİŞMİŞ "BAKIŞ AÇISI" KAMERA KLİBİ (`bbs:actor_eye`)
*Hedef: Kameranın kurguda aktöre kilitlenmesi ve ekrana bindirilecek katmanların yönetimi.*

* [ ] **3.1 Replay Source Seçimi:** Sahnede yer alan aktörlerden birini baz alacak dinamik aktör seçici.
* [ ] **3.2 POV Output Görünürlük Bayrakları:**
  * Eller (Hands)
  * Arayüz (HUD)
  * Nişangah (Crosshair)
  * POV Aksiyon Klipleri
  * Ekran Efektleri (Screen Effects)
  * İmleç (Cursor)
  * Kamera Sarsıntısı (Camera Shake)
* [ ] **3.3 Kamera Takip Modları:**
  * `Head Look` (Kafa kemiği matrisine doğrudan kilitlenme)
  * `Hardcore Look` (Gövdeye bağlı kısıtlı kafa dönüş açısı)
  * `Block Outline` (Hedeflenen bloğun sınır çizgilerini gösterme)
  * `Perspective Switch` (Birinci şahıs / Üçüncü şahıs otonom kamera geçiş butonu)

---

### FAZ 4: POV ACTION CLIPS TIMELINE (ZENGİN ARAYÜZ KATMANLARI)
*Hedef: Kurgu esnasında ekrana bindirilen ve anahtar karelerle canlandırılabilen klip türleri.*

* [ ] **4.1 Arayüz Klibi (GUI Clip):** Sandık, fırın, örs, envanter vb. vanilla ekranlarının seçimi, opaklık ve eşya yerleşim Dope Sheet animasyonu.
* [ ] **4.2 Menü Klibi (Menu Clip):** Oyun Menüsü (ESC), Ölüm Ekranı ("Öldün!") ve Uyuma Ekranı ("Yataktan Ayrıl") şablonları ve animasyon kanalları.
* [ ] **4.3 Kamera Sarsıntısı (Camera Shake):** Hasar alma (`Hurt Time`), ölüm anı ve sarsıntı şiddeti anahtar kare kanalları.
* [ ] **4.4 Partikül Klibi (Particle Effect):** Minecraft partikül kütüphanesinden seçim ve zaman çizgisi tetikleyicisi.
* [ ] **4.5 Boss Barları (Boss Bars):** Boss adı, can yüzdesi, renk ve bar stili için Dope Sheet animasyonu.
* [ ] **4.6 Durum Efektleri (Status Effects):** Faydalı, zararlı ve nötr efektlerin sağ tık menüsünden seçilip HUD'da listelenmesi ve animasyonu.
* [ ] **4.7 Başarımlar / Bildirimler (Toasts):** Hazır başarım şablonları, özel başlık/açıklama, özel eşya ve doku desteği.
* [ ] **4.8 Sohbet Klibi (Chat Clip):** Yazı yazma animasyonu, imleç konumu, komut tamamlama önerisi ve kaydırma kanalları.
* [ ] **4.9 Ekran Efektleri (Screen Effects):** Vignette, Totem patlaması (`Totem of Undying`), bulantı (`Nausea`) ve dondurucu soğuk efektlerinin Dope Sheet kontrolleri.

---

## 🛠 DERLEME VE DOĞRULAMA PROSEDÜRÜ
Her faz bittiğinde aşağıdaki doğrulama adımları işletilmelidir:
1. `./gradlew.bat --no-daemon -x apiCheck -x runAddonApiCheck -x test -x check :1.20.4:build` komutu koşturulur.
2. `BUILD SUCCESSFUL` teyit edilir.
3. Oluşan JAR `versions/bbs-C1.3-1.20.4.jar` yoluna kopyalanır.
4. `DEV_STATE.md` ve `İŞLEMLER.txt` güncellenir.