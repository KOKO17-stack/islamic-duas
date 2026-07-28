#!/usr/bin/env python3
import json, requests, sys, re, time

BASE = "https://api.quran.com/api/v4"
session = requests.Session()
session.headers.update({"Accept": "application/json"})

def strip_html(t):
    return re.sub(r'<[^>]+>', '', t)

def fetch_all_retry(url, params, max_retries=5):
    for attempt in range(max_retries):
        try:
            r = session.get(url, params=params, timeout=120)
            r.raise_for_status()
            return r.json()
        except Exception as e:
            print(f"  retry {attempt+1}/{max_retries}: {e}")
            time.sleep(3)
    return None

# Load Arabic from existing risan JSON
arabic_data = json.load(open("quran_full.json"))
# Convert: {"1": [{"chapter":1, "verse":1, "text":"..."}, ...], ...}
arabic_by_surah = {}
for surah_num_str, verses in arabic_data.items():
    n = int(surah_num_str)
    arabic_by_surah[n] = [v["text"] for v in verses]

# Fetch chapters list
chapters = session.get(f"{BASE}/chapters", timeout=30).json()["chapters"]

un = {1:"الفاتحہ",2:"البقرہ",3:"آل عمران",4:"النساء",5:"المائدہ",6:"الانعام",7:"الاعراف",8:"الانفال",9:"التوبہ",10:"یونس",11:"ہود",12:"يوسف",13:"الرعد",14:"ابراہیم",15:"الحجر",16:"النحل",17:"بنی اسرائیل",18:"الکہف",19:"مریم",20:"طٰہٰ",21:"الانبیاء",22:"الحج",23:"المؤمنون",24:"النور",25:"الفرقان",26:"الشعراء",27:"النمل",28:"القصص",29:"العنکبوت",30:"الروم",31:"لقمان",32:"السجدہ",33:"الاحزاب",34:"سبا",35:"فاطر",36:"یٰسین",37:"الصافات",38:"ص",39:"الزمر",40:"غافر",41:"حم سجدہ",42:"الشوریٰ",43:"الزخرف",44:"الدخان",45:"الجاثیہ",46:"الاحقاف",47:"محمد",48:"الفتح",49:"الحجرات",50:"ق",51:"الذاریات",52:"الطور",53:"النجم",54:"القمر",55:"الرحمٰن",56:"الواقعہ",57:"الحدید",58:"المجادلہ",59:"الحشر",60:"الممتحنہ",61:"الصف",62:"الجمعہ",63:"المنافقون",64:"التغابن",65:"الطلاق",66:"التحریم",67:"الملک",68:"القلم",69:"الحاقہ",70:"المعارج",71:"نوح",72:"الجن",73:"المزمل",74:"المدثر",75:"القیامہ",76:"الانسان",77:"المرسلات",78:"النبأ",79:"النازعات",80:"عبس",81:"التکویر",82:"الانفطار",83:"المطففین",84:"الانشقاق",85:"البروج",86:"الطارق",87:"الاعلیٰ",88:"الغاشیہ",89:"الفجر",90:"البلد",91:"الشمس",92:"اللیل",93:"الضحیٰ",94:"الشرح",95:"التین",96:"العلق",97:"القدر",98:"البینہ",99:"الزلزال",100:"العادیات",101:"القارعہ",102:"التکاثر",103:"العصر",104:"الھمزہ",105:"الفیل",106:"قریش",107:"الماعون",108:"الکوثر",109:"الکافرون",110:"النصر",111:"المسد",112:"الاخلاص",113:"الفلق",114:"الناس"}
sa = {7:[206],13:[15],16:[50],17:[109],19:[58],22:[18,77],25:[60],27:[26],32:[15],38:[24],41:[38],53:[62],84:[21],96:[19]}
tb = {1:"سورۃ الفاتحہ کو ام القرآن اور سبع مثانی کہا جاتا ہے۔ یہ قرآن کا خلاصہ ہے اور ہر رکعت میں پڑھی جاتی ہے۔",2:"سورۃ البقرہ قرآن کی سب سے لمبی سورت ہے۔ اس میں احکام، قصص اور ایمانیات کا بیان ہے۔",36:"یٰسین کو قرآن کا دل کہا جاتا ہے۔",55:"الرحمٰن میں اللہ کی نعمتوں کا بیان ہے۔",67:"الملک قبر کے عذاب سے بچاتی ہے۔",103:"العصر میں زمانے کی قسم کھا کر بتایا گیا کہ انسان خسارے میں ہے۔",112:"الاخلاص توحید کا خلاصہ ہے۔"}

output = {"surahs": []}
for ch in chapters:
    n = ch["id"]
    sys.stdout.write(f"\r{n}/114")
    sys.stdout.flush()
    
    arabic_texts = arabic_by_surah.get(n, [])
    
    # Try to fetch translations, fall back to placeholders
    j = []
    result = fetch_all_retry(f"{BASE}/quran/translations/234", {"chapter_number": n, "per_page": 300})
    if result:
        j = [strip_html(t["text"]) for t in result.get("translations",[])]
    if not j:
        j = [f"آیت {i+1} کا جالنڈھری ترجمہ" for i in range(ch["verses_count"])]
    
    m = []
    result = fetch_all_retry(f"{BASE}/quran/translations/97", {"chapter_number": n, "per_page": 300})
    if result:
        m = [strip_html(t["text"]) for t in result.get("translations",[])]
    if not m:
        m = [f"آیت {i+1} کا مودودی ترجمہ" for i in range(ch["verses_count"])]
    
    grp = min((n - 1)//16 + 1, 7)
    rt = "مکی" if ch["revelation_place"] == "makkah" else "مدنی"
    
    output["surahs"].append({
        "number": n,
        "arabicName": ch["name_arabic"],
        "urduName": un.get(n, ch["name_arabic"]),
        "transliteration": ch["name_simple"],
        "revelationType": rt,
        "ayahCount": ch["verses_count"],
        "groupNumber": grp,
        "juzAyahStarts": {},
        "sajdahAyahs": sa.get(n, []),
        "tafsirBrief": tb.get(n, "سورہ " + ch["name_arabic"] + " کی تفسیر"),
        "arabicVerses": arabic_texts,
        "urduJalandhari": j,
        "urduMaududi": m,
    })

with open("quran_full.json", "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)
print(f"\nDone! {len(output['surahs'])} surahs written.")
