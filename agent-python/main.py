import os
import re
import json
import time
import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
from google import genai

load_dotenv()

app = FastAPI(title="Curiosity Agent")

client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))


@app.get("/")
def health():
    return {"status": "ok", "service": "curiosity-agent"}


@app.get("/ping")
def ping():
    return {"message": "Python agent buradan selam gönderiyor"}


class ScriptRequest(BaseModel):
    topic: str


SCRIPT_SYSTEM_RULES = """Sen 'Curiosity of Things' adlı bir YouTube kanalı için senaryo yazan usta bir yazarsın.
Kanal karanlık tarih, gizem ve çözülmemiş vakalar üzerine İngilizce içerik üretir.
Ton: sinematik, atmosferik, merak uyandıran; bir belgesel anlatıcısının sesi gibi.

TEMEL KURALLAR:
- Senaryoyu İNGİLİZCE yaz.
- Uzunluk: yaklaşık 1900-2300 kelime. Akıcı, sürükleyici bir anlatı.
- İlk iki cümlede izleyiciyi yakala; güçlü, atmosferik bir açılış yap.
- Yılları rakamla değil yazıyla yaz (örn. '1947' DEĞİL 'nineteen forty-seven').
- Sahne yönergesi, kamera notu, köşeli parantez YOK. Sadece seslendirilecek anlatı metni.
- Hiçbir cümle 250 karakteri geçmesin. Uzun cümleleri doğal yerlerden böl.

RİTİM VE CÜMLE YAPISI — EN ÖNEMLİ KURAL:
Cümle uzunluklarını ÇEŞİTLENDİR. Kısa, orta ve uzun cümleleri harmanla.
Aşağıdaki iki hata YAPAY ve UCUZ durur; ASLA yapma:

  HATA 1 — Art arda çok kısa, parçalı cümleler:
  KÖTÜ: "No wreckage. No bodies. No explanation. Just silence."
  İYİ: "There was no wreckage, no bodies, no explanation left behind — only a silence that refused to answer."

  HATA 2 — Aynı kelimeyle başlayan paralel cümle dizisi:
  KÖTÜ: "It did not fall quietly. It did not fall alone. It did not fall without a trace."
  İYİ: "It fell in a way no one could explain, dragging its silence down with it, leaving nothing behind to study."

Kural: Peş peşe İKİDEN fazla cümle aynı kelimeyle başlamasın.
Kural: Peş peşe üç cümlenin üçü de altı kelimeden kısa olmasın.
Virgül, uzun tire ve bağlaçlarla cümleleri birbirine bağlayarak akış yarat; makineli tüfek gibi kısa cümlelerle değil.

KAPANIŞ:
- ASLA düz bir kapanışla bitirme ('that's the story', 'thanks for watching' gibi).
- Sonu, geriye çekilip düşündüren bir SORU ya da çözülmemiş bir gizemle bitir.
- Senaryonun SON cümlesi soru işaretiyle (?) bitmek ZORUNDA.
- Bu kapanış izleyicinin aklında günlerce kalmalı.
"""

# --- Doğrulama katmanı ---
# Kurallar prompt'ta da yazıyor ama LLM'e güvenmiyoruz:
# burada deterministik olarak kontrol edilir, ihlal varsa hata geri
# beslenip senaryo yeniden ürettirilir (CLAUDE.md, Bölüm 4.2).

MAX_SENTENCE_LENGTH = 250          # XTTS v2 cümle başına limit
MIN_WORD_COUNT = 1500              # hedef 1900-2300; bunun çok altı kabul edilmez
MAX_GENERATION_ATTEMPTS = 3        # validate başarısızsa en fazla bu kadar yeniden üretim

# Kapanışta geçmesi yasak kalıplar (küçük harfe çevrilip aranır)
BANNED_CLOSING_PATTERNS = [
    "that's the story",
    "that is the story",
    "thanks for watching",
    "thank you for watching",
    "in conclusion",
    "to sum up",
    "and that's how",
    "the end.",
    "subscribe",
]


def split_sentences(script: str) -> list[str]:
    # Cümlelere böl (basit ama iş görür)
    sentences = re.split(r'(?<=[.!?])\s+', script.strip())
    return [s.strip() for s in sentences if s.strip()]


def validate_script(script: str, check_min_length: bool = True) -> list[str]:
    """Deterministik kural kontrolleri. Boş liste dönerse senaryo geçerli.
    Hata mesajları LLM'e geri beslenir, o yüzden spesifik yazılır.
    check_min_length=False: elle girilen kısa senaryolar (test/kısa video)
    kelime sayısına takılmadan kontrol edilir."""
    errors: list[str] = []
    sentences = split_sentences(script)

    if not sentences:
        return ["Senaryo boş."]

    # 1) XTTS v2 limiti: 250 karakteri geçen cümle olamaz
    long_sentences = [s for s in sentences if len(s) > MAX_SENTENCE_LENGTH]
    for s in long_sentences[:5]:
        errors.append(
            f"{len(s)} karakterlik cümle var, limit {MAX_SENTENCE_LENGTH}. "
            f"Bu cümleyi doğal bir yerinden böl: \"{s[:80]}...\""
        )

    # 2) Kapanış çözülmemiş bir soruyla bitmek zorunda (hard requirement)
    if not sentences[-1].endswith("?"):
        errors.append(
            "Senaryonun son cümlesi soru işaretiyle bitmiyor. "
            "Kapanış, izleyicinin aklında kalacak çözülmemiş bir SORU olmalı."
        )

    # 3) Yasaklı kapanış kalıpları (son ~400 karakterde aranır)
    tail = script.strip()[-400:].lower()
    for pattern in BANNED_CLOSING_PATTERNS:
        if pattern in tail:
            errors.append(
                f"Kapanışta yasaklı kalıp geçiyor: \"{pattern}\". "
                "Düz kapanış yasak; gizemi çözmeden soruyla bitir."
            )

    # 4) Art arda üç cümle aynı kelimeyle başlayamaz
    for i in range(len(sentences) - 2):
        starts = [sent.split()[0].lower() for sent in sentences[i:i + 3] if sent.split()]
        if len(starts) == 3 and starts[0] == starts[1] == starts[2]:
            errors.append(
                f"Peş peşe üç cümle \"{starts[0]}\" kelimesiyle başlıyor. "
                "Cümle başlangıçlarını çeşitlendir."
            )
            break  # aynı türden tek hata yeter, LLM'i boğma

    # 5) Asgari uzunluk
    if check_min_length:
        word_count = len(script.split())
        if word_count < MIN_WORD_COUNT:
            errors.append(
                f"Senaryo {word_count} kelime; en az {MIN_WORD_COUNT}, hedef 1900-2300 kelime."
            )

    return errors


def analyze_script(script: str) -> dict:
    # Bilgilendirme amaçlı metrikler (doğrulamadan bağımsız)
    sentences = split_sentences(script)
    long_sentences = [s for s in sentences if len(s) > MAX_SENTENCE_LENGTH]
    return {
        "wordCount": len(script.split()),
        "sentenceCount": len(sentences),
        "longSentenceCount": len(long_sentences),
    }


def call_gemini(prompt: str) -> str:
    # Gemini bazen anlık olarak yanıt vermez; exponential backoff ile dener
    last_error = None
    for attempt in range(4):
        try:
            response = client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
            )
            if response.text:
                return response.text
            last_error = RuntimeError("Gemini boş yanıt döndürdü")
        except Exception as e:
            last_error = e
        wait = 2 ** attempt
        print(f"Gemini denemesi başarısız: {last_error}. {wait}s bekleniyor...")
        time.sleep(wait)

    raise HTTPException(
        status_code=503,
        detail=f"Gemini birkaç denemeden sonra yanıt vermedi: {str(last_error)}"
    )


@app.post("/agent/script")
def generate_script(request: ScriptRequest):
    """generate → validate → regenerate döngüsü.
    Doğrulama hataları bir sonraki denemenin prompt'una geri beslenir.
    MAX_GENERATION_ATTEMPTS denemede geçemezse son senaryo valid=False ile döner;
    kabul/ret kararı orkestratörün (Spring)."""
    topic = request.topic
    base_prompt = f"{SCRIPT_SYSTEM_RULES}\n\nKONU: {topic}\n\nŞimdi bu konu için tam senaryoyu yaz:"

    script = ""
    errors: list[str] = []

    for attempt in range(1, MAX_GENERATION_ATTEMPTS + 1):
        prompt = base_prompt
        if errors:
            feedback = "\n".join(f"- {e}" for e in errors)
            prompt += (
                "\n\nÖNCEKİ DENEMEN AŞAĞIDAKİ KURALLARI İHLAL ETTİ. "
                "BU HATALARIN HİÇBİRİNİ TEKRARLAMADAN SENARYOYU BAŞTAN YAZ:\n"
                + feedback
            )

        script = call_gemini(prompt)
        errors = validate_script(script)

        if not errors:
            return {
                "topic": topic,
                "script": script,
                "valid": True,
                "attempts": attempt,
                "errors": [],
                "analysis": analyze_script(script),
            }

        print(f"Deneme {attempt}: {len(errors)} doğrulama hatası, yeniden üretilecek.")

    return {
        "topic": topic,
        "script": script,
        "valid": False,
        "attempts": MAX_GENERATION_ATTEMPTS,
        "errors": errors,
        "analysis": analyze_script(script),
    }


# =====================================================================
# SCRIPT_REVIEW — senaryoyu içerik/kalite açısından incele
# İki katman: deterministik kural kontrolleri (kelime sayısı hariç)
# + Gemini'nin editör gözüyle ton/ritim/kapanış değerlendirmesi.
# =====================================================================

class ReviewRequest(BaseModel):
    topic: str
    script: str


REVIEW_PROMPT = """Sen 'Curiosity of Things' adlı karanlık belgesel YouTube kanalının acımasız ama adil baş editörüsün.
Aşağıdaki İngilizce senaryoyu incele. Kanalın kimliği şu:
- Ton: sürekli tırmanan huzursuzluk; rahatlama anı yok, gerilim çözülmez.
- Açı mümkünse izleyicinin kendi psikolojisine döner; izleyici işin içine çekilir.
- Kapanış: çözülmemiş, akılda günlerce kalacak bir soru. Düz özet/kapanış kabul edilemez.

ŞUNLARI DENETLE:
1. Ton tutarlı mı, gerilim gerçekten tırmanıyor mu, ortada sarkan/sıkıcı bölüm var mı?
2. Yapay zeka ürünü olduğu belli olan kalıplar var mı: aynı yapıda peş peşe cümleler,
   makineli tüfek gibi kısa cümle dizileri, klişe belgesel dili ("little did they know" vb.)?
3. Kapanış sorusu gerçekten güçlü mü, yoksa zorlama/basmakalıp mı?
4. Açılış ilk iki cümlede yakalıyor mu?

SADECE şu formatta bir JSON nesnesi döndür, başka hiçbir şey yazma:
{"approved": true/false, "verdict": "tek cümlelik genel değerlendirme (Türkçe)", "issues": ["sorun 1 (Türkçe)", "sorun 2", ...]}

approved=false ise issues boş olamaz. Küçük üslup önerileri approved=true iken de issues'a yazılabilir.

KONU: {topic}

SENARYO:
{script}"""


@app.post("/agent/review")
def review_script(request: ReviewRequest):
    # 1) Deterministik kontroller — kelime sayısı hariç (elle girilen
    #    kısa test senaryoları da incelenebilsin)
    hard_errors = validate_script(request.script, check_min_length=False)

    # 2) Gemini editör incelemesi
    prompt = REVIEW_PROMPT.replace("{topic}", request.topic).replace("{script}", request.script)

    last_error = "bilinmiyor"
    for _ in range(3):
        raw = call_gemini(prompt)
        try:
            cleaned = re.sub(r"^```(json)?|```$", "", raw.strip(), flags=re.MULTILINE).strip()
            data = json.loads(cleaned)
            if isinstance(data, dict) and "approved" in data:
                gemini_approved = bool(data.get("approved"))
                issues = [str(i) for i in data.get("issues", [])]
                verdict = str(data.get("verdict", ""))
                return {
                    # Kural ihlali varsa Gemini onaylasa bile geçemez
                    "approved": gemini_approved and not hard_errors,
                    "verdict": verdict,
                    "issues": issues,
                    "hardErrors": hard_errors,
                }
            last_error = "JSON'da 'approved' alanı yok"
        except (json.JSONDecodeError, TypeError) as e:
            last_error = f"JSON parse hatası: {e}"
        print(f"Review yanıtı geçersiz döndü ({last_error}), yeniden denenecek.")

    raise HTTPException(
        status_code=502,
        detail=f"Gemini geçerli bir inceleme döndürmedi: {last_error}"
    )


# =====================================================================
# SCRIPT_SPLIT — senaryoyu part'lara böl
# Bir part = bir ses dosyası (voice_i.wav) + bir görsel (image_i)
# =====================================================================

PART_TARGET_WORDS = 100  # part başına hedef kelime sayısı

# Elle bölme ayracı: tek başına bir satırda üç veya daha fazla tire (---)
PART_SEPARATOR = re.compile(r"^\s*-{3,}\s*$", re.MULTILINE)


class SplitRequest(BaseModel):
    script: str


def auto_split(script: str) -> list[str]:
    # Cümle cümle biriktir, ~PART_TARGET_WORDS kelimede bir part kapat
    sentences = split_sentences(script)
    parts: list[str] = []
    current: list[str] = []
    word_count = 0

    for sentence in sentences:
        current.append(sentence)
        word_count += len(sentence.split())
        if word_count >= PART_TARGET_WORDS:
            parts.append(" ".join(current))
            current, word_count = [], 0

    if current:
        tail = " ".join(current)
        # Kuyruk çok kısaysa ayrı bir part yapma, son parta yapıştır
        if parts and len(tail.split()) < PART_TARGET_WORDS // 2:
            parts[-1] += " " + tail
        else:
            parts.append(tail)

    return parts


@app.post("/agent/split")
def split_script(request: SplitRequest):
    script = request.script.strip()
    if not script:
        raise HTTPException(status_code=400, detail="Senaryo boş, bölünecek bir şey yok.")

    if PART_SEPARATOR.search(script):
        # Kullanıcı part'ları --- ile elle ayırmış: aynen kullan,
        # kelime hesabına hiç girme (bir blok = bir part)
        parts = [p.strip() for p in PART_SEPARATOR.split(script) if p.strip()]
        mode = "manual"
    else:
        parts = auto_split(script)
        mode = "auto"

    if not parts:
        raise HTTPException(status_code=400, detail="Bölme sonucunda hiç part çıkmadı.")

    # Güvenlik: 250 karakteri aşan cümle TTS'te patlar; hangi modda olursa
    # olsun burada yakala ki hata TTS adımında değil bölme anında görülsün
    problems: list[str] = []
    for i, part in enumerate(parts, start=1):
        for s in split_sentences(part):
            if len(s) > MAX_SENTENCE_LENGTH:
                problems.append(
                    f"Part {i}: {len(s)} karakterlik cümle var, limit {MAX_SENTENCE_LENGTH}. "
                    f"Cümle: \"{s[:80]}...\""
                )
    if problems:
        raise HTTPException(
            status_code=400,
            detail="Bölme durduruldu; seslendirmede patlayacak cümleler var:\n"
                   + "\n".join(problems)
        )

    return {"parts": parts, "partCount": len(parts), "mode": mode}


# =====================================================================
# IMAGE_PROMPT — her part için Gemini'den görsel prompt'u iste
# =====================================================================

class ImagePromptRequest(BaseModel):
    topic: str
    parts: list[str]


@app.post("/agent/image-prompts")
def generate_image_prompts(request: ImagePromptRequest):
    numbered = "\n\n".join(
        f"PART {i}:\n{p}" for i, p in enumerate(request.parts, start=1)
    )

    prompt = f"""Sen 'Curiosity of Things' adlı karanlık belgesel kanalının görsel yönetmenisin.
Aşağıda '{request.topic}' konulu videonun seslendirme part'ları var.
Her part için, o part seslendirilirken ekranda duracak TEK görselin
İngilizce text-to-image prompt'unu yaz.

KURALLAR:
- Prompt İngilizce ve tek cümlelik yoğun bir sahne tarifi olsun.
- Stil ekleme (film grain, VHS, vignette vb. YAZMA) — stil son eki üretim sırasında otomatik ekleniyor.
- İnsan yüzü gösterme: figürler arkası dönük, bulanık ya da anonim olsun.
- Metin, logo, watermark isteme.
- Part sayısı kadar prompt döndür, sıra part sırasıyla aynı olsun.

SADECE şu formatta bir JSON dizisi döndür, başka hiçbir şey yazma:
["prompt 1", "prompt 2", ...]

{numbered}"""

    last_error = "bilinmiyor"
    for _ in range(3):
        raw = call_gemini(prompt)
        try:
            # Gemini bazen ```json ... ``` bloğuna sarar, temizle
            cleaned = re.sub(r"^```(json)?|```$", "", raw.strip(), flags=re.MULTILINE).strip()
            prompts = json.loads(cleaned)
            if isinstance(prompts, list) and len(prompts) == len(request.parts):
                return {"prompts": [str(p) for p in prompts]}
            last_error = f"beklenen {len(request.parts)} prompt, gelen {len(prompts) if isinstance(prompts, list) else 'dizi değil'}"
        except (json.JSONDecodeError, TypeError) as e:
            last_error = f"JSON parse hatası: {e}"
        print(f"Görsel prompt üretimi geçersiz döndü ({last_error}), yeniden denenecek.")

    raise HTTPException(
        status_code=502,
        detail=f"Gemini geçerli prompt listesi döndürmedi: {last_error}"
    )


# =====================================================================
# TTS_GENERATE — XTTS v2 ile part başına seslendirme
# Model ilk istekte bir kez yüklenir (yükleme dakikalar sürebilir).
# =====================================================================

SPEAKER_WAV = os.getenv("SPEAKER_WAV", "referans.wav")

_tts_model = None


def get_tts_model():
    global _tts_model
    if _tts_model is None:
        # Ağır importlar bilerek burada: TTS kurulu olmasa da agent ayağa kalkabilsin
        import torch
        from TTS.api import TTS as CoquiTTS
        device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"XTTS v2 yükleniyor, cihaz: {device}")
        _tts_model = CoquiTTS("tts_models/multilingual/multi-dataset/xtts_v2").to(device)
    return _tts_model


class TtsRequest(BaseModel):
    text: str
    outputDir: str
    index: int


@app.post("/agent/tts")
def generate_tts(request: TtsRequest):
    if not os.path.exists(SPEAKER_WAV):
        raise HTTPException(
            status_code=500,
            detail=f"Referans ses dosyası bulunamadı: {SPEAKER_WAV}. "
                   "agent-python klasörüne koy ya da .env'de SPEAKER_WAV ile yolunu ver."
        )

    os.makedirs(request.outputDir, exist_ok=True)
    file_path = os.path.join(request.outputDir, f"voice_{request.index}.wav")

    tts = get_tts_model()
    tts.tts_to_file(
        text=request.text,
        speaker_wav=SPEAKER_WAV,
        language="en",
        file_path=file_path,
    )
    return {"file": file_path}


# =====================================================================
# IMAGE_GENERATE — fal.ai Flux Schnell ile part başına görsel
# =====================================================================

FAL_KEY = os.getenv("FAL_KEY")

# Kanalın sabit görsel stili; part prompt'unun sonuna eklenir
STYLE_SUFFIX = ", cinematic eerie liminal atmosphere, degraded analog memory aesthetic, faded nineteen-nineties VHS and home video look, subtle chromatic aberration and scanline artifacts, muted nostalgic palette of dusty beige, faded red and cold television blue, soft haze and floating dust motes, quiet empty spaces, uncanny feeling that something is subtly wrong, faces obscured turned away or out of focus, anonymous figures, dreamlike unreliable memory mood, heavy film grain, strong vignetting, shallow depth of field, ultra detailed, sharp focus, no text, no logos, no watermarks, no gore"


class ImageRequest(BaseModel):
    prompt: str
    outputDir: str
    index: int


@app.post("/agent/image")
def generate_image(request: ImageRequest):
    if not FAL_KEY:
        raise HTTPException(status_code=500, detail="FAL_KEY tanımlı değil (agent-python/.env).")

    os.makedirs(request.outputDir, exist_ok=True)

    response = requests.post(
        "https://fal.run/fal-ai/flux/schnell",
        headers={
            "Authorization": f"Key {FAL_KEY}",
            "Content-Type": "application/json",
        },
        json={
            "prompt": request.prompt + STYLE_SUFFIX,
            "image_size": "landscape_16_9",
            "num_inference_steps": 4,
            "num_images": 1,
        },
        timeout=120,
    )
    response.raise_for_status()
    data = response.json()
    image_info = data["images"][0]
    image_url = image_info["url"]

    # Uzantıyı content type'tan seç (flux genelde jpeg döndürür)
    content_type = image_info.get("content_type", "image/jpeg")
    ext = "png" if "png" in content_type else "jpg"
    file_path = os.path.join(request.outputDir, f"image_{request.index}.{ext}")

    img_data = requests.get(image_url, timeout=120).content
    with open(file_path, "wb") as f:
        f.write(img_data)

    return {"file": file_path}
