#!/usr/bin/env python3
"""
Mock PIM server — ~900 products across 6 categories, with physical dimensions.
Port: 8090
"""
import json
import itertools
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from datetime import datetime, timezone, timedelta
import random

random.seed(42)


def iso(days_ago):
    dt = datetime.now(timezone.utc) - timedelta(days=days_ago)
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def sku(prefix, n):
    return f"{prefix}-{n:04d}"


# ─────────────────────────────────────────────────────────────────────────────
#  FURNITURE
# ─────────────────────────────────────────────────────────────────────────────

SOFA_STYLES = ["Modern", "Classic", "Scandinavian", "Industrial", "Velvet", "Corner", "Convertible", "Modular"]
SOFA_COLORS = ["Grey", "Beige", "Navy Blue", "Forest Green", "Charcoal", "Cream", "Terracotta", "Anthracite"]
SOFA_DIMS   = {
    "Modern":      {"width_cm": 220, "depth_cm": 88,  "height_cm": 82,  "seat_height_cm": 44, "weight_kg": 52},
    "Classic":     {"width_cm": 225, "depth_cm": 95,  "height_cm": 92,  "seat_height_cm": 46, "weight_kg": 60},
    "Scandinavian":{"width_cm": 215, "depth_cm": 85,  "height_cm": 78,  "seat_height_cm": 42, "weight_kg": 48},
    "Industrial":  {"width_cm": 218, "depth_cm": 90,  "height_cm": 85,  "seat_height_cm": 45, "weight_kg": 58},
    "Velvet":      {"width_cm": 222, "depth_cm": 92,  "height_cm": 88,  "seat_height_cm": 45, "weight_kg": 55},
    "Corner":      {"width_cm": 285, "depth_cm": 220, "height_cm": 85,  "seat_height_cm": 44, "weight_kg": 95},
    "Convertible": {"width_cm": 220, "depth_cm": 95,  "height_cm": 88,  "seat_height_cm": 45, "weight_kg": 65},
    "Modular":     {"width_cm": 270, "depth_cm": 95,  "height_cm": 82,  "seat_height_cm": 43, "weight_kg": 80},
}

CHAIR_TYPES = ["Armchair", "Dining Chair", "Office Chair", "Rocking Chair", "Bar Stool", "Accent Chair", "Chaise Lounge"]
CHAIR_DIMS  = {
    "Armchair":      {"width_cm": 85,  "depth_cm": 88,  "height_cm": 90,  "seat_height_cm": 44, "weight_kg": 18},
    "Dining Chair":  {"width_cm": 48,  "depth_cm": 52,  "height_cm": 90,  "seat_height_cm": 46, "weight_kg": 6},
    "Office Chair":  {"width_cm": 65,  "depth_cm": 68,  "height_cm": 112, "seat_height_cm": "46–56 adj.", "weight_kg": 14},
    "Rocking Chair": {"width_cm": 72,  "depth_cm": 95,  "height_cm": 105, "seat_height_cm": 44, "weight_kg": 12},
    "Bar Stool":     {"width_cm": 42,  "depth_cm": 42,  "height_cm": 100, "seat_height_cm": "65–75 adj.", "weight_kg": 7},
    "Accent Chair":  {"width_cm": 78,  "depth_cm": 82,  "height_cm": 87,  "seat_height_cm": 43, "weight_kg": 15},
    "Chaise Lounge": {"width_cm": 85,  "depth_cm": 165, "height_cm": 90,  "seat_height_cm": 40, "weight_kg": 22},
}

TABLE_TYPES = ["Coffee Table", "Dining Table", "Desk", "Console Table", "Side Table", "Extendable Table", "Nesting Table"]
TABLE_DIMS  = {
    "Coffee Table":     {"width_cm": 120,       "depth_cm": 60,  "height_cm": 45, "weight_kg": 18},
    "Dining Table":     {"width_cm": 180,       "depth_cm": 90,  "height_cm": 76, "weight_kg": 38},
    "Desk":             {"width_cm": 140,       "depth_cm": 65,  "height_cm": 75, "weight_kg": 22},
    "Console Table":    {"width_cm": 120,       "depth_cm": 35,  "height_cm": 82, "weight_kg": 12},
    "Side Table":       {"width_cm": 50,        "depth_cm": 50,  "height_cm": 55, "weight_kg": 8},
    "Extendable Table": {"width_cm": "160–240", "depth_cm": 90,  "height_cm": 76, "weight_kg": 45},
    "Nesting Table":    {"width_cm": 55,        "depth_cm": 55,  "height_cm": 55, "weight_kg": 10},
}

BED_SIZES = ["Single", "Double", "King", "Super King", "Bunk Bed", "Sofa Bed", "Ottoman Bed"]
BED_DIMS  = {
    "Single":     {"mattress_cm": "90×190",  "frame_width_cm": 100, "frame_length_cm": 205, "frame_height_cm": 45, "weight_kg": 28},
    "Double":     {"mattress_cm": "135×190", "frame_width_cm": 148, "frame_length_cm": 205, "frame_height_cm": 45, "weight_kg": 36},
    "King":       {"mattress_cm": "150×200", "frame_width_cm": 163, "frame_length_cm": 215, "frame_height_cm": 45, "weight_kg": 42},
    "Super King": {"mattress_cm": "180×200", "frame_width_cm": 193, "frame_length_cm": 215, "frame_height_cm": 45, "weight_kg": 50},
    "Bunk Bed":   {"mattress_cm": "90×190 ×2", "frame_width_cm": 100, "frame_length_cm": 210, "frame_height_cm": 165, "weight_kg": 55},
    "Sofa Bed":   {"mattress_cm": "140×190", "frame_width_cm": 225, "frame_length_cm": 95,  "frame_height_cm": 88,  "weight_kg": 62},
    "Ottoman Bed":{"mattress_cm": "150×200", "frame_width_cm": 163, "frame_length_cm": 215, "frame_height_cm": 48,  "weight_kg": 58},
}

WARDROBE_TYPES = ["Sliding Door", "Hinged Door", "Walk-in", "Corner", "Open Wardrobe", "Built-in Frame"]
WARDROBE_DIMS  = {
    "Sliding Door": {"width_cm": 200, "depth_cm": 62,  "height_cm": 220, "weight_kg": 90},
    "Hinged Door":  {"width_cm": 180, "depth_cm": 58,  "height_cm": 220, "weight_kg": 80},
    "Walk-in":      {"width_cm": 240, "depth_cm": 60,  "height_cm": 220, "weight_kg": 120},
    "Corner":       {"width_cm": 220, "depth_cm": 220, "height_cm": 220, "weight_kg": 140},
    "Open Wardrobe":{"width_cm": 160, "depth_cm": 40,  "height_cm": 200, "weight_kg": 45},
    "Built-in Frame":{"width_cm": 200,"depth_cm": 58,  "height_cm": 240, "weight_kg": 100},
}

MATERIALS = ["Oak", "Walnut", "Beech", "MDF", "Metal Frame", "Rattan", "Marble Top", "Tempered Glass"]

furniture_products = []
n = 1

for style, color in itertools.product(SOFA_STYLES, SOFA_COLORS[:5]):
    if n > 120: break
    d = SOFA_DIMS[style]
    furniture_products.append({
        "sku": sku("FRN-SFA", n),
        "name": f"{style} Sofa in {color}",
        "shortDescription": f"{style}-style 3-seater sofa in {color.lower()} fabric.",
        "description": (
            f"The {style} Sofa in {color} combines comfort and aesthetics. "
            f"Upholstered in high-quality {color.lower()} fabric with solid oak legs. "
            f"Available in 2- and 3-seater configurations. Easy assembly, 5-year frame warranty."
        ),
        "category": "Furniture / Sofas & Armchairs",
        "brand": "StreamX Home",
        "lastModified": iso(random.randint(1, 90)),
        "attributes": {
            "material": "fabric / solid oak legs", "color": color, "style": style, "seats": "3",
            "width_cm": d["width_cm"], "depth_cm": d["depth_cm"],
            "height_cm": d["height_cm"], "seat_height_cm": d["seat_height_cm"],
            "weight_kg": d["weight_kg"],
            "warranty": "5 years", "assembly": "required",
            "price": f"{random.randint(799, 2999)} GBP"
        }
    })
    n += 1

for chair_type in CHAIR_TYPES:
    for mat in MATERIALS[:8]:
        d = CHAIR_DIMS[chair_type]
        furniture_products.append({
            "sku": sku("FRN-CHR", n),
            "name": f"{chair_type} — {mat}",
            "shortDescription": f"{chair_type} crafted from {mat.lower()}, ergonomic design.",
            "description": (
                f"The {chair_type} in {mat} is designed for everyday comfort. "
                f"Solid {mat.lower()} construction ensures durability. 2-year warranty."
            ),
            "category": "Furniture / Chairs",
            "brand": "StreamX Home",
            "lastModified": iso(random.randint(1, 90)),
            "attributes": {
                "material": mat, "type": chair_type,
                "width_cm": d["width_cm"], "depth_cm": d["depth_cm"],
                "height_cm": d["height_cm"], "seat_height_cm": d["seat_height_cm"],
                "weight_kg": d["weight_kg"],
                "warranty": "2 years",
                "price": f"{random.randint(89, 499)} GBP"
            }
        })
        n += 1

for table_type in TABLE_TYPES:
    for mat in MATERIALS[:6]:
        d = TABLE_DIMS[table_type]
        furniture_products.append({
            "sku": sku("FRN-TBL", n),
            "name": f"{table_type} — {mat}",
            "shortDescription": f"{table_type} in {mat.lower()}, modern finish.",
            "description": (
                f"The {table_type} in {mat} brings style and practicality to any room. "
                f"Scratch-resistant surface, easy-clean finish. Flat-pack with full instructions."
            ),
            "category": "Furniture / Tables",
            "brand": "StreamX Home",
            "lastModified": iso(random.randint(1, 90)),
            "attributes": {
                "material": mat, "type": table_type,
                "width_cm": d["width_cm"], "depth_cm": d["depth_cm"],
                "height_cm": d["height_cm"], "weight_kg": d["weight_kg"],
                "finish": "matte",
                "price": f"{random.randint(149, 1499)} GBP"
            }
        })
        n += 1

for bed_size in BED_SIZES:
    for mat in MATERIALS[:5]:
        d = BED_DIMS[bed_size]
        furniture_products.append({
            "sku": sku("FRN-BED", n),
            "name": f"{bed_size} Bed Frame — {mat}",
            "shortDescription": f"{bed_size} bed frame in {mat.lower()}, minimalist headboard.",
            "description": (
                f"The {bed_size} Bed Frame in {mat} has a clean silhouette with robust construction. "
                f"Slatted base included. Mattress sold separately. 10-year structural warranty."
            ),
            "category": "Furniture / Beds & Mattresses",
            "brand": "StreamX Home",
            "lastModified": iso(random.randint(1, 90)),
            "attributes": {
                "material": mat, "size": bed_size,
                "mattress_size_cm": d["mattress_cm"],
                "frame_width_cm": d["frame_width_cm"], "frame_length_cm": d["frame_length_cm"],
                "frame_height_cm": d["frame_height_cm"], "weight_kg": d["weight_kg"],
                "slats": "included", "mattress": "not included",
                "price": f"{random.randint(299, 1799)} GBP"
            }
        })
        n += 1

for w_type in WARDROBE_TYPES:
    for mat in MATERIALS[:4]:
        d = WARDROBE_DIMS[w_type]
        furniture_products.append({
            "sku": sku("FRN-WRD", n),
            "name": f"{w_type} Wardrobe — {mat}",
            "shortDescription": f"{w_type} wardrobe in {mat.lower()}, multiple compartments.",
            "description": (
                f"The {w_type} Wardrobe in {mat} maximises storage without compromising on style. "
                f"Includes hanging rail, shelves and drawers. Soft-close hinges on all doors."
            ),
            "category": "Furniture / Storage & Wardrobes",
            "brand": "StreamX Home",
            "lastModified": iso(random.randint(1, 90)),
            "attributes": {
                "material": mat, "type": w_type,
                "width_cm": d["width_cm"], "depth_cm": d["depth_cm"],
                "height_cm": d["height_cm"], "weight_kg": d["weight_kg"],
                "doors": "soft-close",
                "price": f"{random.randint(399, 2499)} GBP"
            }
        })
        n += 1


# ─────────────────────────────────────────────────────────────────────────────
#  LIGHTING
# ─────────────────────────────────────────────────────────────────────────────

LIGHT_TYPES  = ["Floor Lamp", "Table Lamp", "Pendant Light", "Ceiling Light", "Wall Sconce",
                "Desk Lamp", "LED Strip", "Chandelier", "Bedside Lamp", "Outdoor Lantern"]
LIGHT_STYLES = ["Minimalist", "Industrial", "Art Deco", "Scandinavian", "Mid-Century", "Contemporary"]
LIGHT_COLORS = ["Matte Black", "Brushed Gold", "Chrome", "Antique Brass", "White", "Copper"]
LIGHT_DIMS   = {
    "Floor Lamp":     {"height_cm": 165, "base_diameter_cm": 28, "shade_diameter_cm": 40, "weight_kg": 4.5},
    "Table Lamp":     {"height_cm": 55,  "base_diameter_cm": 18, "shade_diameter_cm": 30, "weight_kg": 1.8},
    "Pendant Light":  {"height_cm": "adj. 30–120", "shade_diameter_cm": 35, "cable_length_cm": 180, "weight_kg": 1.2},
    "Ceiling Light":  {"height_cm": 22,  "diameter_cm": 48,      "weight_kg": 2.1},
    "Wall Sconce":    {"height_cm": 30,  "width_cm": 18,         "depth_cm": 20,  "weight_kg": 0.9},
    "Desk Lamp":      {"height_cm": 42,  "base_width_cm": 16,    "arm_reach_cm": 38, "weight_kg": 1.1},
    "LED Strip":      {"length_cm": 500, "width_cm": 1,          "thickness_cm": 0.3, "weight_kg": 0.4},
    "Chandelier":     {"height_cm": 60,  "diameter_cm": 70,      "weight_kg": 6.0},
    "Bedside Lamp":   {"height_cm": 42,  "base_diameter_cm": 14, "shade_diameter_cm": 24, "weight_kg": 1.0},
    "Outdoor Lantern":{"height_cm": 50,  "width_cm": 22,         "depth_cm": 22,  "weight_kg": 1.5},
}

lighting_products = []
nl = 1

for l_type, l_style, l_color in itertools.product(LIGHT_TYPES, LIGHT_STYLES[:4], LIGHT_COLORS[:4]):
    if nl > 180: break
    d = LIGHT_DIMS[l_type]
    attrs = {"type": l_type, "style": l_style, "finish": l_color,
             "bulb": "E27 LED 8W included", "ip_rating": "IP44",
             "price": f"{random.randint(29, 599)} GBP"}
    attrs.update({k: v for k, v in d.items()})
    lighting_products.append({
        "sku": sku("LGT", nl),
        "name": f"{l_style} {l_type} — {l_color}",
        "shortDescription": f"{l_style} {l_type.lower()} in {l_color.lower()} finish, energy-efficient.",
        "description": (
            f"The {l_style} {l_type} in {l_color} adds a statement to any interior. "
            f"Compatible with LED bulbs (E27, included). IP44 rated where applicable."
        ),
        "category": "Lighting",
        "brand": "StreamX Lights",
        "lastModified": iso(random.randint(1, 60)),
        "attributes": attrs
    })
    nl += 1


# ─────────────────────────────────────────────────────────────────────────────
#  ELECTRONICS
# ─────────────────────────────────────────────────────────────────────────────

LAPTOP_BRANDS = ["StreamX Pro", "StreamX Air", "StreamX Ultra"]
LAPTOP_SPECS  = [
    ("13-inch", "Intel Core i5", "8GB",  "256GB SSD", {"width_cm": 30.4, "depth_cm": 21.2, "thickness_cm": 1.6, "weight_kg": 1.3}),
    ("14-inch", "Intel Core i7", "16GB", "512GB SSD", {"width_cm": 31.2, "depth_cm": 22.0, "thickness_cm": 1.7, "weight_kg": 1.5}),
    ("15-inch", "Intel Core i9", "32GB", "1TB SSD",   {"width_cm": 34.9, "depth_cm": 23.9, "thickness_cm": 1.8, "weight_kg": 1.8}),
    ("16-inch", "AMD Ryzen 7",   "16GB", "512GB SSD", {"width_cm": 35.6, "depth_cm": 24.8, "thickness_cm": 1.9, "weight_kg": 2.0}),
    ("13-inch", "Apple M3",      "8GB",  "256GB SSD", {"width_cm": 29.9, "depth_cm": 20.8, "thickness_cm": 1.1, "weight_kg": 1.2}),
    ("14-inch", "Apple M3 Pro",  "18GB", "512GB SSD", {"width_cm": 31.2, "depth_cm": 22.1, "thickness_cm": 1.5, "weight_kg": 1.6}),
]

TV_SIZES = [32, 40, 43, 50, 55, 65, 75, 85]
TV_TYPES = ["OLED", "QLED", "LED", "Mini-LED", "8K OLED"]
# TV dims: screen diagonal → (width_cm, height_cm with stand, depth_with_stand_cm, weight_kg)
TV_DIMS = {
    32: (72,  56, 19, 5.2),  40: (89,  61, 23, 7.0),
    43: (96,  65, 24, 8.5),  50: (112, 73, 26, 11.0),
    55: (123, 80, 28, 14.5), 65: (145, 93, 31, 19.0),
    75: (167, 106,34, 26.0), 85: (189, 119,36, 35.0),
}

PHONE_MODELS  = ["StreamX Phone 15", "StreamX Phone 15 Pro", "StreamX Phone 15 Ultra",
                 "StreamX Phone 14", "StreamX Phone 14 Pro",
                 "StreamX Fold 3", "StreamX Flip 5",
                 "StreamX Phone SE", "StreamX Phone 15 Plus"]
PHONE_STORAGE = ["128GB", "256GB", "512GB", "1TB"]
PHONE_COLORS  = ["Midnight Black", "Arctic White", "Pacific Blue", "Desert Gold", "Space Grey"]
PHONE_DIMS    = {
    "StreamX Phone 15":      {"height_mm": 147, "width_mm": 71,  "thickness_mm": 7.8,  "weight_g": 171},
    "StreamX Phone 15 Pro":  {"height_mm": 153, "width_mm": 73,  "thickness_mm": 8.0,  "weight_g": 187},
    "StreamX Phone 15 Ultra":{"height_mm": 163, "width_mm": 77,  "thickness_mm": 8.5,  "weight_g": 218},
    "StreamX Phone 14":      {"height_mm": 146, "width_mm": 70,  "thickness_mm": 7.8,  "weight_g": 172},
    "StreamX Phone 14 Pro":  {"height_mm": 148, "width_mm": 71,  "thickness_mm": 7.9,  "weight_g": 183},
    "StreamX Fold 3":        {"height_mm": 158, "width_mm": 128, "thickness_mm": 14.2, "weight_g": 271, "note": "folded: 158×67×14.2 mm"},
    "StreamX Flip 5":        {"height_mm": 165, "width_mm": 71,  "thickness_mm": 7.2,  "weight_g": 187, "note": "folded: 84×71×15.9 mm"},
    "StreamX Phone SE":      {"height_mm": 138, "width_mm": 67,  "thickness_mm": 7.3,  "weight_g": 144},
    "StreamX Phone 15 Plus": {"height_mm": 160, "width_mm": 77,  "thickness_mm": 8.0,  "weight_g": 201},
}

electronics_products = []
ne = 1

for brand in LAPTOP_BRANDS:
    for size, cpu, ram, storage, dims in LAPTOP_SPECS:
        electronics_products.append({
            "sku": sku("ELC-LAP", ne),
            "name": f"{brand} {size} — {cpu}",
            "shortDescription": f"{size} laptop, {cpu}, {ram} RAM, {storage}.",
            "description": (
                f"The {brand} {size} laptop delivers outstanding performance. "
                f"Powered by {cpu} with {ram} RAM and {storage}. "
                f"All-day battery (up to 18 hours), 2K IPS display, Thunderbolt 4 ports. 2-year warranty."
            ),
            "category": "Electronics / Laptops",
            "brand": brand,
            "lastModified": iso(random.randint(1, 30)),
            "attributes": {
                "screen": size, "processor": cpu, "ram": ram, "storage": storage,
                "width_cm": dims["width_cm"], "depth_cm": dims["depth_cm"],
                "thickness_cm": dims["thickness_cm"], "weight_kg": dims["weight_kg"],
                "battery": "up to 18 hours", "warranty": "2 years",
                "price": f"{random.randint(699, 3499)} GBP"
            }
        })
        ne += 1

for tv_size, tv_type in itertools.product(TV_SIZES, TV_TYPES):
    if ne > 150: break
    w, h, dep, wt = TV_DIMS[tv_size]
    electronics_products.append({
        "sku": sku("ELC-TV", ne),
        "name": f"StreamX {tv_size}\" {tv_type} Smart TV",
        "shortDescription": f"{tv_size}-inch {tv_type} Smart TV, 4K HDR.",
        "description": (
            f"The StreamX {tv_size}\" {tv_type} Smart TV offers cinema-quality picture with 4K HDR. "
            f"Built-in Netflix, Disney+, Prime Video and Apple TV+. Dolby Atmos, HDMI 2.1 x3."
        ),
        "category": "Electronics / TVs",
        "brand": "StreamX Electronics",
        "lastModified": iso(random.randint(1, 60)),
        "attributes": {
            "screen_size_inches": tv_size, "panel": tv_type,
            "width_cm": w, "height_with_stand_cm": h,
            "depth_with_stand_cm": dep, "weight_kg": wt,
            "resolution": "4K UHD" if "8K" not in tv_type else "8K",
            "hdmi": "3x HDMI 2.1", "smart_os": "StreamX OS",
            "price": f"{random.randint(299, 4999)} GBP"
        }
    })
    ne += 1

for model, storage, color in itertools.product(PHONE_MODELS, PHONE_STORAGE[:3], PHONE_COLORS[:4]):
    if ne > 220: break
    d = PHONE_DIMS[model]
    attrs = {
        "storage": storage, "color": color,
        "display": "6.1\" Super AMOLED", "camera": "50MP + 12MP + 10MP",
        "battery": "4500mAh 45W fast charge", "water_resistance": "IP68",
        "price": f"{random.randint(499, 1599)} GBP"
    }
    attrs.update(d)
    electronics_products.append({
        "sku": sku("ELC-PHN", ne),
        "name": f"{model} {storage} — {color}",
        "shortDescription": f"{model}, {storage}, {color}, 5G.",
        "description": (
            f"The {model} in {color} ({storage}) features a 6.1-inch Super AMOLED display, "
            f"triple camera (50MP + 12MP + 10MP), 5G, 4500mAh battery with 45W fast charging. IP68."
        ),
        "category": "Electronics / Smartphones",
        "brand": "StreamX Mobile",
        "lastModified": iso(random.randint(1, 30)),
        "attributes": attrs
    })
    ne += 1


# ─────────────────────────────────────────────────────────────────────────────
#  KITCHEN
# ─────────────────────────────────────────────────────────────────────────────

COOKWARE = ["Frying Pan", "Saucepan", "Casserole Dish", "Wok", "Grill Pan", "Stockpot", "Sauté Pan"]
COOKWARE_MATERIALS = ["Cast Iron", "Stainless Steel", "Non-stick Titanium", "Ceramic Coated", "Carbon Steel"]
COOKWARE_SIZE_DATA = {
    "20cm": {"diameter_cm": 20, "depth_cm": 5,  "weight_kg": 1.1},
    "24cm": {"diameter_cm": 24, "depth_cm": 6,  "weight_kg": 1.5},
    "28cm": {"diameter_cm": 28, "depth_cm": 7,  "weight_kg": 1.9},
    "32cm": {"diameter_cm": 32, "depth_cm": 8,  "weight_kg": 2.4},
}
APPLIANCE_DIMS = {
    "Stand Mixer":      {"width_cm": 38, "depth_cm": 22, "height_cm": 36, "weight_kg": 6.4, "power_w": 1200},
    "Air Fryer":        {"width_cm": 32, "depth_cm": 33, "height_cm": 34, "weight_kg": 5.2, "power_w": 1800},
    "Espresso Machine": {"width_cm": 35, "depth_cm": 30, "height_cm": 38, "weight_kg": 9.0, "power_w": 1450},
    "Blender Pro":      {"width_cm": 17, "depth_cm": 17, "height_cm": 48, "weight_kg": 2.8, "power_w": 1500},
    "Bread Maker":      {"width_cm": 28, "depth_cm": 38, "height_cm": 32, "weight_kg": 6.0, "power_w": 650},
    "Food Processor":   {"width_cm": 22, "depth_cm": 22, "height_cm": 38, "weight_kg": 4.5, "power_w": 800},
    "Toaster Oven":     {"width_cm": 42, "depth_cm": 33, "height_cm": 28, "weight_kg": 5.0, "power_w": 1400},
    "Induction Hob":    {"width_cm": 28, "depth_cm": 36, "height_cm": 6,  "weight_kg": 1.8, "power_w": 2000},
    "Sous Vide Stick":  {"width_cm": 7,  "depth_cm": 7,  "height_cm": 38, "weight_kg": 0.9, "power_w": 1200},
    "Electric Kettle":  {"width_cm": 20, "depth_cm": 15, "height_cm": 24, "weight_kg": 1.1, "power_w": 2200},
}
APPLIANCES = [
    ("Stand Mixer",      "1200W motor, 6-speed, 5L bowl, includes dough hook and whisk", 249, 599),
    ("Air Fryer",        "6L XXL capacity, 8 presets, rapid air circulation", 79, 199),
    ("Espresso Machine", "15-bar pump, built-in grinder, steam wand, 1.8L tank", 199, 899),
    ("Blender Pro",      "1500W, 2L jug, self-cleaning, smoothie/soup/ice modes", 59, 249),
    ("Bread Maker",      "12 programmes, 900g loaf, delay timer, gluten-free", 89, 179),
    ("Food Processor",   "800W, 3L bowl, 8 attachments, BPA-free", 99, 299),
    ("Toaster Oven",     "25L, convection fan, 5 cooking functions", 69, 199),
    ("Induction Hob",    "2000W, 10 power levels, child lock, auto-switch off", 49, 149),
    ("Sous Vide Stick",  "1200W, 0.5–20L, ±0.1°C, Bluetooth app", 59, 129),
    ("Electric Kettle",  "1.7L, 2200W, 6 temperature presets, keep-warm 30 min", 29, 79),
]

kitchen_products = []
nk = 1

for item, mat, size in itertools.product(COOKWARE, COOKWARE_MATERIALS, list(COOKWARE_SIZE_DATA.keys())):
    if nk > 120: break
    d = COOKWARE_SIZE_DATA[size]
    kitchen_products.append({
        "sku": sku("KCH-CWR", nk),
        "name": f"{mat} {item} {size}",
        "shortDescription": f"{size} {item.lower()} in {mat.lower()}, induction compatible.",
        "description": (
            f"The {mat} {item} ({size}) is built for serious home cooks. "
            f"Induction, gas and electric compatible. Oven-safe to 260°C."
        ),
        "category": "Kitchen / Cookware",
        "brand": "StreamX Kitchen",
        "lastModified": iso(random.randint(1, 120)),
        "attributes": {
            "material": mat, "size": size,
            "diameter_cm": d["diameter_cm"], "depth_cm": d["depth_cm"],
            "weight_kg": d["weight_kg"],
            "compatible": "induction, gas, electric, ceramic",
            "oven_safe": "260°C",
            "price": f"{random.randint(19, 149)} GBP"
        }
    })
    nk += 1

for name, desc, price_lo, price_hi in APPLIANCES:
    for color in ["Black", "Silver", "White", "Red"]:
        d = APPLIANCE_DIMS[name]
        kitchen_products.append({
            "sku": sku("KCH-APP", nk),
            "name": f"{name} — {color}",
            "shortDescription": f"{name} in {color.lower()}, {desc.split(',')[0].lower()}.",
            "description": f"The {name} in {color}: {desc}. 2-year warranty, CE certified.",
            "category": "Kitchen / Appliances",
            "brand": "StreamX Kitchen",
            "lastModified": iso(random.randint(1, 60)),
            "attributes": {
                "color": color,
                "width_cm": d["width_cm"], "depth_cm": d["depth_cm"],
                "height_cm": d["height_cm"], "weight_kg": d["weight_kg"],
                "power_w": d["power_w"],
                "warranty": "2 years", "certification": "CE, RoHS",
                "price": f"{random.randint(price_lo, price_hi)} GBP"
            }
        })
        nk += 1


# ─────────────────────────────────────────────────────────────────────────────
#  OUTDOOR & GARDEN
# ─────────────────────────────────────────────────────────────────────────────

GARDEN_FURNITURE = ["Garden Sofa Set", "Sun Lounger", "Garden Dining Set", "Hanging Chair",
                    "Garden Bench", "Bistro Set", "Daybed", "Garden Bar Set"]
GARDEN_MATERIALS = ["Rattan", "Teak", "Aluminium", "FSC Pine", "Polywood", "Recycled Plastic"]
GARDEN_FURN_DIMS = {
    "Garden Sofa Set":  {"width_cm": 210, "depth_cm": 80,  "height_cm": 72,  "seat_height_cm": 36, "weight_kg": 38},
    "Sun Lounger":      {"width_cm": 195, "depth_cm": 65,  "height_cm": 35,  "seat_height_cm": 35, "weight_kg": 12},
    "Garden Dining Set":{"width_cm": 180, "depth_cm": 90,  "height_cm": 74,  "seat_height_cm": 46, "weight_kg": 55},
    "Hanging Chair":    {"width_cm": 110, "depth_cm": 110, "height_cm": 175, "seat_height_cm": 90, "weight_kg": 20},
    "Garden Bench":     {"width_cm": 150, "depth_cm": 60,  "height_cm": 80,  "seat_height_cm": 44, "weight_kg": 18},
    "Bistro Set":       {"width_cm": 60,  "depth_cm": 60,  "height_cm": 72,  "seat_height_cm": 44, "weight_kg": 10},
    "Daybed":           {"width_cm": 200, "depth_cm": 100, "height_cm": 80,  "seat_height_cm": 40, "weight_kg": 45},
    "Garden Bar Set":   {"width_cm": 120, "depth_cm": 60,  "height_cm": 105, "seat_height_cm": 76, "weight_kg": 30},
}
GARDEN_TOOLS = [
    ("Lawn Mower",     "Self-propelled, 46cm cut, grass box included", 199, 699,
     {"width_cm": 52, "depth_cm": 85, "height_cm": 104, "weight_kg": 28}),
    ("Hedge Trimmer",  "550W, 45cm blade, anti-vibration handle", 59, 149,
     {"length_cm": 65, "width_cm": 20, "height_cm": 22, "weight_kg": 2.9}),
    ("Leaf Blower",    "Cordless 36V, 2Ah battery included, 280 km/h", 49, 129,
     {"length_cm": 55, "width_cm": 14, "height_cm": 22, "weight_kg": 2.3}),
    ("Garden Shredder","2500W, 45mm capacity, quick-stop safety", 99, 299,
     {"width_cm": 44, "depth_cm": 34, "height_cm": 80, "weight_kg": 18}),
    ("Pressure Washer","2000W, 150 bar, 8m hose, patio cleaner included", 79, 299,
     {"width_cm": 34, "depth_cm": 30, "height_cm": 90, "weight_kg": 12}),
    ("Rotary Tiller",  "900W, 3 tilling widths, foldable handle", 129, 399,
     {"width_cm": 38, "depth_cm": 58, "height_cm": 105, "weight_kg": 14}),
    ("Garden Vacuum",  "3-in-1 blow/vacuum/mulch, 3000W, 40L bag", 59, 149,
     {"length_cm": 110, "width_cm": 26, "height_cm": 36, "weight_kg": 5.5}),
    ("Water Timer",    "Digital, 6 programmes, freeze protection", 15, 49,
     {"width_cm": 8, "depth_cm": 6, "height_cm": 12, "weight_kg": 0.2}),
]

outdoor_products = []
no = 1

for item, mat in itertools.product(GARDEN_FURNITURE, GARDEN_MATERIALS):
    if no > 100: break
    d = GARDEN_FURN_DIMS[item]
    outdoor_products.append({
        "sku": sku("OUT-FRN", no),
        "name": f"{mat} {item}",
        "shortDescription": f"{item} in {mat.lower()}, weather-resistant.",
        "description": (
            f"The {mat} {item} is built to withstand British weather. "
            f"UV-resistant and weatherproof. Cushions included (water-resistant covers)."
        ),
        "category": "Outdoor & Garden / Furniture",
        "brand": "StreamX Outdoor",
        "lastModified": iso(random.randint(1, 180)),
        "attributes": {
            "material": mat, "weather_resistant": "yes",
            "width_cm": d["width_cm"], "depth_cm": d["depth_cm"],
            "height_cm": d["height_cm"], "seat_height_cm": d["seat_height_cm"],
            "weight_kg": d["weight_kg"],
            "cushions": "included", "assembly": "~30 min",
            "price": f"{random.randint(149, 2499)} GBP"
        }
    })
    no += 1

for name, desc, lo, hi, dims in GARDEN_TOOLS:
    for variant in ["Standard", "Pro", "Cordless"]:
        attrs = {"variant": variant, "warranty": "3 years",
                 "price": f"{random.randint(lo, hi)} GBP"}
        attrs.update(dims)
        outdoor_products.append({
            "sku": sku("OUT-TLS", no),
            "name": f"{name} {variant}",
            "shortDescription": f"{name} {variant.lower()}, {desc.split(',')[0].lower()}.",
            "description": f"The {name} {variant}: {desc}. 3-year warranty, safety certified.",
            "category": "Outdoor & Garden / Tools",
            "brand": "StreamX Garden",
            "lastModified": iso(random.randint(1, 90)),
            "attributes": attrs
        })
        no += 1


# ─────────────────────────────────────────────────────────────────────────────
#  HOME DECOR
# ─────────────────────────────────────────────────────────────────────────────

DECOR_ITEMS = [
    ("Throw Pillow",   ["Linen","Velvet","Cotton"],
     ["Sage Green","Dusty Pink","Ochre","Navy","Stone"],
     {"width_cm": 45, "height_cm": 45, "depth_cm": 12, "weight_kg": 0.4}),
    ("Area Rug",       ["Wool","Jute","Viscose","Cotton"],
     ["Multi-colour","Monochrome","Terracotta","Blue","Ivory"],
     {"width_cm": 200, "length_cm": 290, "thickness_cm": 1.2, "weight_kg": 6.0}),
    ("Wall Art Print", ["Canvas","Framed","Poster"],
     ["Abstract","Botanical","Geometric","Landscape","Minimalist"],
     {"width_cm": 60, "height_cm": 80, "depth_cm": 3, "weight_kg": 1.2}),
    ("Vase",           ["Ceramic","Glass","Brass","Terracotta"],
     ["White","Sage","Black","Nude","Cobalt Blue"],
     {"diameter_cm": 15, "height_cm": 32, "weight_kg": 0.8}),
    ("Scented Candle", ["Soy Wax","Beeswax","Coconut Wax"],
     ["Oakmoss","Vanilla & Amber","Sea Salt","Bergamot","Sandalwood"],
     {"diameter_cm": 9, "height_cm": 10, "burn_hours": 45, "weight_g": 200}),
    ("Curtains",       ["Linen","Velvet","Blackout","Sheer"],
     ["Ivory","Grey","Navy","Blush","Forest Green"],
     {"width_cm": 140, "drop_cm": 260, "weight_kg": 1.5}),
    ("Blanket",        ["Merino Wool","Faux Fur","Knitted Cotton"],
     ["Oatmeal","Slate Blue","Blush","Charcoal"],
     {"width_cm": 130, "length_cm": 170, "weight_kg": 0.9}),
    ("Mirror",         ["Round","Arch","Rectangular","Sunburst"],
     ["Gold Frame","Black Frame","Natural Wood","Frameless"],
     {"width_cm": 70, "height_cm": 100, "depth_cm": 4, "weight_kg": 5.5}),
]

decor_products = []
nd = 1

for item_name, materials, variants, dims in DECOR_ITEMS:
    for mat, var in itertools.product(materials, variants):
        if nd > 180: break
        attrs = {"material": mat, "colour": var, "handcrafted": "yes", "gift_wrap": "available",
                 "price": f"{random.randint(9, 299)} GBP"}
        attrs.update(dims)
        decor_products.append({
            "sku": sku("DCR", nd),
            "name": f"{item_name} — {mat}, {var}",
            "shortDescription": f"{mat} {item_name.lower()} in {var.lower()}, handcrafted.",
            "description": (
                f"The {mat} {item_name} in {var} adds warmth and character to any room. "
                f"Ethically sourced, handcrafted by artisans. Gift-wrap available."
            ),
            "category": "Home Decor",
            "brand": "StreamX Living",
            "lastModified": iso(random.randint(1, 120)),
            "attributes": attrs
        })
        nd += 1


# ─────────────────────────────────────────────────────────────────────────────
#  COMBINE
# ─────────────────────────────────────────────────────────────────────────────

ALL_PRODUCTS = (
    furniture_products +
    lighting_products  +
    electronics_products +
    kitchen_products   +
    outdoor_products   +
    decor_products
)

print(f"[PIM Mock] Total products generated: {len(ALL_PRODUCTS)}")

PAGE_SIZE = 100


class PimMockHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_GET(self):
        parsed = urlparse(self.path)
        qs     = parse_qs(parsed.query)
        page   = int(qs.get("page", ["0"])[0])
        size   = int(qs.get("size", [str(PAGE_SIZE)])[0])

        if parsed.path in ["/api/v1/products", "/products"]:
            start    = page * size
            end      = start + size
            chunk    = ALL_PRODUCTS[start:end]
            has_more = end < len(ALL_PRODUCTS)
            self.send_json({"products": chunk, "hasMore": has_more, "total": len(ALL_PRODUCTS)})
            return

        for p in ALL_PRODUCTS:
            if parsed.path == f"/products/{p['sku']}":
                self.send_json(p)
                return

        self.send_response(404)
        self.end_headers()

    def send_json(self, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8090), PimMockHandler)
    print(f"PIM Mock server running on http://0.0.0.0:8090  ({len(ALL_PRODUCTS)} products)")
    server.serve_forever()
