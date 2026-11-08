# CLAUDE.md

Bu dosya Claude Code'un bu repoda çalışırken uyması gereken bağlamı ve kuralları içerir.
Her oturumun başında oku. Buradaki kurallar tartışmaya açık değildir.

---

## 1. Proje Nedir

"Curiosity of Things" adlı faceless İngilizce YouTube kanalının içerik üretim hattını
uçtan uca otomatikleştiren, üç katmanlı bir AI agent sistemi.

Kanal profili:
- Konular: dark history, çözülmemiş gizemler, psikoloji
- Sunum: yüz yok, dokümanter tarzı anlatım, karanlık ve rahatsız edici görseller
- Hedef kitle: İngilizce konuşan izleyici

Mevcut manuel pipeline (otomatikleştirilmek istenen süreç):
script yazımı → seslendirme (XTTS v2) → görsel üretimi (fal.ai Flux Schnell) → kurgu (CapCut)

---

## 2. Mimari

Dört katman:

| Katman | Teknoloji | Sorumluluk |
|---|---|---|
| Frontend | Angular v22 | Pipeline'ı tetikleme, durum izleme, çıktıları inceleme |
| Backend | Spring Boot 4.1.0 | Orkestrasyon, pipeline state yönetimi, persistence, REST API |
| Agent | Python / FastAPI | LLM çağrıları, üretim node'larının kendisi |
| Veritabanı | PostgreSQL 18.4 | Pipeline kayıtları, script'ler, node çıktıları |

Akış:
```
Angular  →  Spring Boot (REST)  →  FastAPI agent  →  LLM / harici servisler
                    ↓
              PostgreSQL
```

Spring Boot orkestratördür, agent değildir. İş mantığı ve state Spring tarafında,
üretim (generation) mantığı Python tarafında durur. Bu ayrımı bozma.

---

## 3. Pipeline Node'ları

Kaynak: `StepType.java` — kodda 12 adım var, tablo kodla eşleşiyor.

| Node | Durum | Not |
|---|---|---|
| SCRIPT_GENERATE | Çalışıyor | Gemini 2.5 Flash + deterministik generate → validate → regenerate döngüsü (max 3 deneme, `main.py`). Doğrulama geçemezse adım FAILED olur, senaryo yine de kaydedilir. |
| SCRIPT_REVIEW | Stub | İçerik/kalite incelemesi (planlı) |
| SCRIPT_SPLIT | Çalışıyor | Senaryoyu ~100 kelimelik part'lara böler; her part = 1 ses + 1 görsel. Part'lar `ScriptPart` tablosunda. Yeniden koşarsa eski part'ları siler. |
| TTS_GENERATE | Çalışıyor | XTTS v2, part başına `ses/voice_{i}.wav`. Referans ses: `agent-python/referans.wav` (SPEAKER_WAV env). Üretilmiş part'lar atlanır (yarıda kesilirse kaldığı yerden devam). |
| TTS_QC | Stub | Ses kalite kontrolü (planlı) |
| IMAGE_PROMPT | Çalışıyor | Gemini part başına İngilizce görsel prompt üretir (JSON). Stil son eki (VHS/analog estetik) üretimde otomatik eklenir. |
| IMAGE_GENERATE | Çalışıyor | fal.ai Flux Schnell, part başına `gorseller/image_{i}.jpg`. Üretilmiş part'lar atlanır. |
| IMAGE_QC | Stub | Görsel kalite kontrolü (planlı) |
| EDIT | Stub | Şimdilik manuel (CapCut) |
| THUMBNAIL | Stub | Planlı |
| METADATA | Stub | Başlık/açıklama/etiket üretimi (planlı) |
| UPLOAD | Stub | Planlı |

Stub adımlar tetiklenirse FAILED olur ve "henüz uygulanmadı" logu yazar
(yanlışlıkla DONE görünmesinler diye).

---

## 4. Değişmez Kurallar

Bu kurallar üretilen içeriğin kimliğini tanımlar. Kod bunları garanti etmek zorundadır.

### 4.1 İçerik kuralları
- **Her script çözülmemiş, akılda kalan bir soruyla biter.** Düz bir sonuç cümlesi,
  özet ya da "işte böyle" tarzı kapanış kesinlikle yasaktır. Bu bir stil tercihi değil,
  pipeline'ın hard requirement'ı.
- Ton: sürekli tırmanan huzursuzluk. Rahatlama anı verilmez, gerilim çözülmez.
- Mümkün olduğunda açı izleyicinin kendi psikolojisine çevrilir; konu izleyiciyi
  doğrudan işin içine katmalıdır.

### 4.2 Teknik kısıtlar
- **XTTS v2 cümle başına maksimum 250 karakter kabul eder.** Bunu aşan cümle
  seslendirmede patlar. Uzun birleşik cümleler üretim aşamasında değil,
  doğrulama aşamasında yakalanıp bölünmelidir.
- LLM'e "kuralları prompt içinde söylemek" yeterli değildir. Kurallar kod
  tarafından doğrulanır.

---

## 5. Açık Mimari Sorunlar (öncelik sırasıyla)

1. **Part'lar arayüzden görülemiyor/düzenlenemiyor.** Görsel prompt'ları ve
   part metinleri sadece DB'de; kullanıcı prompt'u beğenmezse elle düzeltip
   tek bir part'ın görselini/sesini yeniden ürettiremiyor (eski manuel akışta
   `regenerate = [3]` ile yapılıyordu). Sıradaki en değerli iş bu.

2. **Adım sırası zorlanmıyor.** Kullanıcı herhangi bir adımı herhangi bir
   sırada koşabiliyor; ön koşul kontrolleri var (part yoksa FAILED) ama
   "önceki adım DONE değilse koşma" kuralı yok.

3. **Senkron çalışma.** `runStep` HTTP yanıtı dönene kadar bloklar. TTS ve
   görsel üretimi uzun sürer; part başına ilerleme DB'ye yazılıyor ama arayüz
   canlı ilerleme göstermiyor. Job kuyruğu + polling/WebSocket gerekecek
   (pom.xml'de websocket starter'ı hazır ama kullanılmıyor).

4. **Stub adımlar:** SCRIPT_REVIEW, TTS_QC, IMAGE_QC, EDIT, THUMBNAIL,
   METADATA, UPLOAD.

> Çözüldü (2026-07-23): Eski madde 1 ve 2 — `generate → validate → regenerate`
> döngüsü `main.py`'de kuruldu. Deterministik kontroller: 250 karakter cümle
> limiti, soru işaretiyle biten kapanış, yasaklı kapanış kalıpları, tekrarlı
> cümle başlangıçları, asgari kelime sayısı. Hatalar bir sonraki denemenin
> prompt'una geri besleniyor, max 3 deneme. Geçemezse Spring adımı FAILED
> işaretliyor.

---

## 6. Benimle Nasıl Çalışacaksın

- **Türkçe konuş.** Sade dil kullan, önce analoji sonra teknik terim.
- **Kod yorumları Türkçe olsun.**
- **Tek seferde tek adım.** Büyük değişikliği parçalara böl, her parçadan sonra dur.
- **Önce planı söyle, onayımı al, sonra kod yaz.** Sormadan çok dosyaya dokunma.
- **Emin değilsen uydurma, sor.** Var olmayan dosya/fonksiyon/endpoint uydurmak yasak.
- Kısa cevap ver. Gereksiz özet ve jargon istemiyorum.
- Bir şeyin kötü fikir olduğunu düşünüyorsan söyle, onaylamak zorunda değilsin.

---

## 7. Proje Yapısı

```
agent-projesi/
├── agent-frontend/          Angular 22 (standalone components, signals)
│   └── src/app/
│       ├── app.routes.ts    Rotalar: / (dashboard), /videos, /new,
│       │                    /projects/:id, /steps/:stepId, /settings
│       ├── services/project.ts        Tek HTTP servisi (ProjectService)
│       ├── dashboard/ home/ video-create/ project-detail/
│       ├── step-detail/     Adım ekranı: senaryo editörü + "Gemini ile üret"
│       └── settings/
├── agent-backend/           Spring Boot 4.1.0, Java 25, Maven
│   └── src/main/java/com/curiosity/agent_backend/
│       ├── controller/      VideoProjectController (/api/projects),
│       │                    AgentController (/api/agent/ping)
│       ├── service/         PipelineService (adım koşturma, overallStatus),
│       │                    VideoProjectService (CRUD),
│       │                    AgentClient (FastAPI'ye RestClient)
│       ├── domain/          VideoProject, PipelineStep, StepType (12 adım),
│       │                    StepStatus (PENDING/RUNNING/DONE/FAILED/SKIPPED)
│       ├── repository/      JPA repository'ler
│       ├── dto/             Request/response record'ları
│       └── config/WebConfig.java      CORS (localhost:4200)
└── agent-python/            FastAPI agent
    └── main.py              Tek dosya: /agent/script endpoint'i,
                             generate → validate → regenerate döngüsü
```

Önemli akış: `step-detail.ts` → `POST /api/projects/steps/{id}/run` →
`PipelineService.runStep` → `AgentClient` → `POST localhost:8000/agent/script` →
Gemini → validate → sonuç `video_project.raw_script`'e yazılır.

Şema yönetimi: migration yok, `ddl-auto=update` (Hibernate tabloları kendisi günceller).

---

## 8. Komutlar

Üç servis ayrı ayrı, üç ayrı terminalde çalışır. PostgreSQL'in ayakta olması gerekir.

Backend (`agent-backend/` içinde):
```
.\mvnw.cmd spring-boot:run      # çalıştır (port 8080)
.\mvnw.cmd compile              # sadece derle
.\mvnw.cmd test                 # test (şu an sadece boş context testi var)
```

Python agent (`agent-python/` içinde):
```
venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload       # port 8000
```

Frontend (`agent-frontend/` içinde):
```
npm install
npm start                       # ng serve, port 4200
npm test                        # vitest
npm run build
```

Migration aracı yok (bkz. Bölüm 7, ddl-auto=update).

---

## 9. Ortam Değişkenleri / Sırlar

API anahtarları (Gemini, fal.ai vb.) asla koda gömülmez, repoya commit edilmez.
`.env` veya application config üzerinden okunur. `.gitignore` kontrol edilmeli.

Mevcut değişkenler (sadece isimler):

| Nerede | Değişken | Ne için |
|---|---|---|
| `agent-python/.env` (gitignore'da) | `GEMINI_API_KEY` | Gemini API |
| | `FAL_KEY` | fal.ai (görsel üretimi) |
| | `SPEAKER_WAV` | XTTS referans ses dosyası yolu (varsayılan: `referans.wav`) |
| `agent-backend/secrets.properties` (gitignore'da) | `spring.datasource.url` | PostgreSQL bağlantısı |
| | `spring.datasource.username` | |
| | `spring.datasource.password` | |
| `application.properties` (repo'da, sır değil) | `agent.base-url` | FastAPI agent adresi (http://localhost:8000) |
| | `content.base-dir` | Ses/görsel çıktılarının kök klasörü; proje başına `folderName` alt klasörü açılır |
