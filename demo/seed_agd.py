"""
Seed script — 500 produktów AGD dla RAG Service.
Uruchom: python3 demo/seed_agd.py
"""
import json, random, urllib.request

random.seed(42)

brands = ["Bosch", "Siemens", "Electrolux", "Whirlpool", "Samsung", "LG",
          "Miele", "AEG", "Candy", "Indesit", "Gorenje", "Philips", "Braun", "Tefal"]

categories = {
    "Pralka": {
        "models": ["WAN", "WUQ", "WAT", "WNA", "WGB"],
        "attrs": lambda: {
            "pojemnosc": f"{random.choice([6,7,8,9,10,11])} kg",
            "obroty": f"{random.choice([1000,1200,1400,1600])} rpm",
            "klasa_energetyczna": random.choice(["A","A+","A++","A+++"]),
            "liczba_programow": str(random.randint(10, 22)),
        },
        "price": (699, 3999),
    },
    "Zmywarka": {
        "models": ["SMS", "SPS", "SPV", "SMV"],
        "attrs": lambda: {
            "pojemnosc": f"{random.choice([9,10,12,13,14,15])} kompletów",
            "klasa_energetyczna": random.choice(["A","A+","A++","A+++"]),
            "glosnosc": f"{random.randint(38, 52)} dB",
            "liczba_programow": str(random.randint(5, 12)),
        },
        "price": (899, 4500),
    },
    "Lodówka": {
        "models": ["KGN", "KGE", "KSW", "KIN"],
        "attrs": lambda: {
            "pojemnosc_chlodziarki": f"{random.randint(200, 400)} L",
            "pojemnosc_zamrazarki": f"{random.randint(50, 120)} L",
            "klasa_energetyczna": random.choice(["D","C","B","A"]),
            "glosnosc": f"{random.randint(35, 45)} dB",
        },
        "price": (1200, 6000),
    },
    "Piekarnik": {
        "models": ["HBA", "HBN", "OIE", "BEL"],
        "attrs": lambda: {
            "pojemnosc": f"{random.choice([57,65,71,80])} L",
            "typ": random.choice(["elektryczny","parowy","parowo-elektryczny"]),
            "liczba_funkcji": str(random.randint(6, 18)),
            "czyszczenie": random.choice(["pirolityczne","katalityczne","parowe"]),
        },
        "price": (799, 5000),
    },
    "Ekspres do kawy": {
        "models": ["EQ", "TI", "CM", "EP"],
        "attrs": lambda: {
            "typ": random.choice(["automatyczny","kolbowy","kapsułkowy"]),
            "cisnienie": f"{random.choice([15,19,20])} bar",
            "pojemnosc_zbiornika": f"{random.choice([1.2,1.5,1.8,2.4])} L",
            "wbudowany_mlynak": random.choice(["tak","nie"]),
        },
        "price": (299, 4500),
    },
    "Odkurzacz": {
        "models": ["BGL", "BGS", "VC", "FC"],
        "attrs": lambda: {
            "typ": random.choice(["workowy","bezworkowy","robot","piorący"]),
            "moc": f"{random.randint(700, 2400)} W",
            "glosnosc": f"{random.randint(68, 82)} dB",
            "filtr": random.choice(["HEPA 12","HEPA 13","HEPA 14"]),
        },
        "price": (199, 3500),
    },
    "Mikrofalówka": {
        "models": ["HMT", "FE", "MWF", "MS"],
        "attrs": lambda: {
            "pojemnosc": f"{random.choice([17,20,23,25,28,32])} L",
            "moc": f"{random.choice([700,800,900,1000])} W",
            "grill": random.choice(["tak","nie"]),
            "konwekcja": random.choice(["tak","nie"]),
        },
        "price": (249, 1200),
    },
    "Żelazko": {
        "models": ["TDA", "GC", "SI", "EasySpeed"],
        "attrs": lambda: {
            "typ": random.choice(["parowe","suche","z generatorem pary"]),
            "moc": f"{random.randint(2000, 3100)} W",
            "pojemnosc_zbiornika": f"{random.choice([0.2,0.3,0.4,1.5,1.7])} L",
            "stopka": random.choice(["ceramiczna","tytanowa","stalowa nierdzewna"]),
        },
        "price": (79, 699),
    },
}

products = []
idx = 1
per_cat = 500 // len(categories)   # 62 per category = 496; last one gets remainder

cat_items = list(categories.items())
for cat_idx, (cat_name, cat) in enumerate(cat_items):
    count = per_cat + (500 - per_cat * len(categories)) if cat_idx == len(cat_items) - 1 else per_cat
    for _ in range(count):
        brand  = random.choice(brands)
        model  = random.choice(cat["models"]) + str(random.randint(10, 99)) + random.choice(["X","B","W","S",""])
        attrs  = cat["attrs"]()
        price  = round(random.uniform(*cat["price"]), 2)
        sku    = f"AGD-{idx:04d}"
        url    = f"https://sklep.example.com/agd/{cat_name.lower().replace(' ','-').replace('ó','o').replace('ż','z').replace('ó','o')}/{sku}"

        attrs_text = ", ".join(f"{k.replace('_',' ')}: {v}" for k, v in attrs.items())
        text = (
            f"{brand} {model} — {cat_name}. SKU: {sku}. "
            f"Parametry techniczne: {attrs_text}. "
            f"Cena: {price:.2f} PLN. "
            f"Marka {brand} jest znana z wysokiej jakości i trwałości. "
            f"Produkt dostępny w magazynie, czas dostawy 2–5 dni roboczych. "
            f"Gwarancja producenta: 2 lata."
        )

        products.append({
            "url":   url,
            "title": f"{brand} {model} — {cat_name} ({sku})",
            "text":  text,
            "type":  "product",
            "metadata": {
                "category": cat_name,
                "brand": brand,
                "sku": sku,
                "price_pln": f"{price:.2f}",
                **attrs,
            },
        })
        idx += 1

print(f"Przygotowano {len(products)} produktów. Wysyłam...")

payload = json.dumps({"documents": products}, ensure_ascii=False).encode("utf-8")

req = urllib.request.Request(
    "http://localhost/api/admin/ingest/documents",
    data=payload,
    headers={"Content-Type": "application/json; charset=utf-8", "X-Admin-Key": "admin123"},
    method="POST",
)
with urllib.request.urlopen(req, timeout=300) as resp:
    result = json.loads(resp.read().decode())
    print(f"Odpowiedź API: {result}")
    print(f"Zingestowano: {result.get('documentCount')} produktów AGD.")
