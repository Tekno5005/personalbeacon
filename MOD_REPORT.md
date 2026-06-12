# Personal Beacon Mod — Detaylı Teknik Rapor

**Sürüm:** 1.2.0  
**Minecraft Sürümü:** 1.20.1  
**Mod Yükleyici:** Fabric  
**Java Sürümü:** 17 (çalışma zamanı) / 21 (geliştirme)  
**Grup:** `net.tekno`  
**Tarih:** 2026-06-12

---

## 1. Genel Bakış

**Personal Beacon**, Minecraft'ın standart Beacon (İşaret Taşı) bloğuna **kişisel erişim kontrolü** ekleyen bir Fabric modudur. Vanilya Minecraft'ta bir beacon'un etki alanına giren tüm oyuncular otomatik olarak buff etkilerini alır. Bu mod, beacon sahibinin yalnızca belirli oyuncuları etkiye dahil edebileceği bir **beyaz liste (whitelist)** sistemi sunar.

### Temel Problem ve Çözüm

| Durum | Vanilya Davranışı | Mod ile Davranış |
|---|---|---|
| Kısıtlanmamış beacon | Etki alanındaki HERKES buff alır | Değişmez (geriye dönük uyumlu) |
| Kısıtlanmış beacon | — | Yalnızca izin verilen oyuncular buff alır |
| Sahipsiz beacon | — | İlk kısıtlayan oyuncu otomatik sahip olur |

---

## 2. Mimari Yapı

```
personalbeacon/
├── src/main/java/net/tekno/personalbeacon/
│   ├── PersonalBeaconMod.java           ← Sunucu giriş noktası
│   ├── PersonalBeaconClient.java        ← İstemci giriş noktası
│   ├── PersonalBeaconConfig.java        ← Yapılandırma yönetimi
│   ├── BeaconAccessData.java            ← Temel veri modeli
│   ├── BeaconAccessManager.java         ← Kalıcı durum yöneticisi
│   ├── ModNetworking.java               ← Ağ paket işleyicileri
│   ├── DebugCommand.java                ← Sunucu komutları
│   ├── BeaconPosHolder.java             ← Beacon konum yardımcısı
│   ├── mixin/
│   │   ├── BeaconBlockEntityMixin.java  ← Efekt filtreleme enjeksiyonu
│   │   ├── BeaconScreenMixin.java       ← GUI buton enjeksiyonu
│   │   └── BeaconScreenHandlerAccessor.java ← Konum yakalama
│   └── client/
│       ├── BeaconAccessScreen.java      ← Erişim kontrol arayüzü
│       ├── GuiEditorScreen.java         ← Yerleşim düzenleyici
│       ├── GuiLayoutConfig.java         ← GUI yapılandırma modeli
│       └── TextureButtonWidget.java     ← Özel doku butonu
└── src/test/java/
    └── BeaconAccessDataTest.java        ← 15 JUnit 5 test senaryosu
```

---

## 3. Temel Özellikler

### 3.1 Erişim Kontrolü Sistemi

Mod, üç katmanlı bir sahiplik modeli uygular:

- **Birincil Sahip (Primary Owner):** Beacon'u ilk kısıtlayan oyuncu. Hiçbir zaman listeden çıkarılamaz.
- **Ortak Yöneticiler (Co-Owners):** Birincil sahip tarafından yönetim yetkisi verilen oyuncular.
- **İzin Verilen Oyuncular (Allowed Players):** Beacon etkilerini alacak oyuncular.

Veri `BeaconAccessData.java` içinde UUID tabanlı üç harita ile tutulur:

```java
Map<BlockPos, Set<UUID>> allowedPlayers   // Etkilenecek oyuncular
Map<BlockPos, Set<UUID>> beaconOwners     // Ortak yöneticiler
Map<BlockPos, UUID>      primaryOwners    // Birincil sahipler (1 tane)
Map<UUID, String>        playerNames      // Çevrimdışı isim önbelleği
```

### 3.2 Efekt Filtreleme (BeaconBlockEntityMixin)

Mod, Minecraft'ın `BeaconBlockEntity.applyPlayerEffects()` metoduna **en yüksek öncelikte** (`@At("HEAD")`) enjekte eder:

1. Beacon kısıtlanmamışsa → vanilya davranışına devam et
2. Beacon kısıtlanmışsa → vanilya işlemini **iptal et**, sadece izin verilen oyunculara efekt uygula

Vanilya menzil hesabı korunur:
- Seviye 1 → 20 blok
- Seviye 2 → 30 blok
- Seviye 3 → 40 blok
- Seviye 4 → 50 blok

### 3.3 Ağ Mimarisi (ModNetworking)

5 özel paket kanalı tanımlanmıştır:

**Sunucu → İstemci (S2C):**
| Paket | Açıklama |
|---|---|
| `S2C_SYNC_ACCESS` | Beacon durum verisi (sahip, izinliler, isim önbelleği) |
| `S2C_TEST_MODE` | Test modu geçiş durumu |

**İstemci → Sunucu (C2S):**
| Paket | Açıklama |
|---|---|
| `C2S_UPDATE_ACCESS` | Oyuncuyu izinli listesine ekle/çıkar |
| `C2S_REQUEST_SYNC` | Güncel durumu iste |
| `C2S_TOGGLE_OWNER` | Ortak yönetici ekle/çıkar |
| `C2S_UNRESTRICT` | Tüm kısıtlamaları kaldır |

Her C2S paketi sunucu tarafında **mesafe doğrulaması** yapar:
```
mesafe² ≤ maxManageDistance²
```

### 3.4 Grafik Arayüz (BeaconAccessScreen)

Vanilla beacon ekranına eklenen "Erişimi Yönet" butonuyla açılan özel bir GUI:

**Durum 1 — Kısıtlanmamış:**
- "Herkese Açık" mesajı gösterilir
- "Erişimi Kısıtla" butonu

**Durum 2 — Kısıtlanmış:**
- Sahip adı ★ ile gösterilir
- Bağlı oyuncular yeşil (●), çevrimdışı oyuncular kırmızı (●)
- Her satırda ✔ İzin Ver / ✘ Engelle düğmesi
- ★/☆ ile ortak yönetici atama
- Bekleme durumu (...) anlık geri bildirim için

**Durum 3 — Tek Oyuncu:**
- Erişim kontrolü çok oyunculu modda çalışır uyarısı

### 3.5 Veri Kalıcılığı

`BeaconAccessManager`, Minecraft'ın `PersistentState` sistemini kullanarak verileri world kayıt dosyasına NBT formatında yazar:

```
data/
└── personalbeacon.dat   ← Tüm beacon erişim verileri
```

NBT yapısı:
```
{
  "beacons": [
    { "x": 100, "y": 64, "z": -200,
      "players": ["uuid1", "uuid2"],
      "owner": "uuid1",
      "coOwners": ["uuid1", "uuid3"] }
  ],
  "playerNames": { "uuid1": "Tekno5005", ... }
}
```

### 3.6 Yapılandırma (PersonalBeaconConfig)

`config/personalbeacon.json` dosyasıyla özelleştirilebilir ayarlar:

| Ayar | Varsayılan | Açıklama |
|---|---|---|
| `maxPlayersPerBeacon` | 0 (sınırsız) | Beacon başına max oyuncu |
| `maxManageDistance` | 0 (devre dışı) | Yönetim için max mesafe (blok) |
| `allowNonOpManagement` | true | Op olmayan oyuncular kendi beacon'larını yönetebilir |
| `skipDistanceCheckInSingleplayer` | true | Tek oyunculuda mesafe kontrolünü atla |
| `testModeDefault` | false | Test modu başlangıç durumu |

---

## 4. Sunucu Komutları

`/personalbeacon` komutu OP Seviye 2 gerektirir:

| Komut | Açıklama |
|---|---|
| `/personalbeacon help` | Komut yardımı |
| `/personalbeacon debug` | Tüm kısıtlı beacon'ları listele |
| `/personalbeacon add <oyuncu> <x> <y> <z>` | Beacon'a oyuncu ekle |
| `/personalbeacon remove <oyuncu> <x> <y> <z>` | Beacon'dan oyuncu çıkar |
| `/personalbeacon setowner <oyuncu> <x> <y> <z>` | Beacon sahibi ata |
| `/personalbeacon clear <x> <y> <z>` | Beacon verilerini sil |
| `/personalbeacon test` | Test modunu aç/kapat |

---

## 5. Özel Özellikler

### Softlock Önleme
Tek oyunculu modda, bir oyuncu beacon ile ilk kez etkileşime girdiğinde **otomatik olarak izin listesine eklenir**. Bu sayede oyuncu kendi beacon'unun etkilerini kaybetmez.

### Beacon Kırılma Temizliği
Bir beacon kırıldığında, ilgili tüm erişim verileri otomatik olarak temizlenir (`PlayerBlockBreakEvents`).

### Geriye Dönük Uyumluluk
Kısıtlama eklenmemiş beacon'lar vanilya gibi davranır; mevcut world'ler için herhangi bir veri geçişi gerekmez.

### Özelleştirilebilir GUI Düzeni
`/personalbeaconop edit` komutuyla açılan **yerleşim düzenleyici** sayesinde, GUI'deki her element (pencereler, butonlar, metin konumları) oyun içinden yeniden konumlandırılabilir. Ayarlar `config/personalbeacon_gui_layout.json` dosyasına kaydedilir.

---

## 6. Çoklu Dil Desteği

4 dil tam olarak desteklenmektedir:

| Dil | Dosya |
|---|---|
| İngilizce | `en_us.json` |
| Türkçe | `tr_tr.json` |
| İspanyolca | `es_es.json` |
| Fransızca | `fr_fr.json` |

---

## 7. Test Altyapısı

`BeaconAccessDataTest.java` dosyasında 15 JUnit 5 test senaryosu:

- Kısıtlanmamış durum davranışı
- Oyuncu ekleme/çıkarma yaşam döngüsü
- Sahiplik yönetimi
- Oyuncu isim önbelleğe alma
- Değişmezlik garantileri
- Tam NBT döngüsü (serileştirme/geri yükleme)
- Hatalı UUID dayanıklılığı

Çalıştırmak için: `./gradlew test`

---

## 8. Teknik Öne Çıkanlar

| Alan | Detay |
|---|---|
| **Mixin Stratejisi** | `@At("HEAD")` iptal edilebilir enjeksiyon ile en yüksek öncelik |
| **Kalıcılık** | `PersistentState` + NBT (dünya kayıt sistemiyle bütünleşik) |
| **Ağ** | Fabric'in özel paket API'si, 5 kanal |
| **Thread Güvenliği** | Test modu için `volatile` alan |
| **Performans** | O(1) çevrimiçi oyuncu arama, kareli mesafe kontrolü |
| **Veri Kaybı Önleme** | NBT serileştirmede tüm konum kümelerinin birleşimi |
| **Geriye Uyumluluk** | Eski `getOwner()` depreke edilmiş ama çalışır |

---

## 9. Sürüm Geçmişi (v1.2.0 Yenilikleri)

- "Herkese Aç" / "Erişimi Kısıtla" iki durumlu akış
- Kullanıcı eylemleri için eylem çubuğu geri bildirimi
- Bekleme durumlu (\...) ortak yönetici (sahip) açma/kapama
- Bağlam açıklama satırı
- Boş liste mesajı
- Mesafe kontrolü optimizasyonu (kareli hesaplama)
- O(1) çevrimiçi oyuncu arama
- NBT serileştirmede veri kaybı önleme
- Duyarlı GUI ölçekleme
- Thread-safe test modu geçişi

---

## 10. Özet

Personal Beacon modu, Minecraft'ın beacon mekaniklerine **çok oyunculu ortamlar için tasarlanmış, güvenli ve esnek bir erişim kontrol katmanı** ekler. Sunucu operatörleri için komut tabanlı yönetim, oyuncular için GUI tabanlı self-servis yönetim ve geliştiriciler için özelleştirilebilir yerleşim sistemi ile kapsamlı bir çözüm sunmaktadır.
