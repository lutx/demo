#!/usr/bin/env python3
"""
Mock AEM server for RAG demo.
Serves GraphQL endpoint (Content Fragments) and JSON model pages.
Port: 4502
"""
import json
from http.server import HTTPServer, BaseHTTPRequestHandler

CONTENT_FRAGMENTS = [
    # ── Furniture ────────────────────────────────────────────────────────────
    {
        "_path": "/content/streamx/en/articles/furniture-buying-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Furniture Buying Guide",
        "body": {"plaintext": (
            "Our furniture catalogue covers six sub-categories: Sofas & Armchairs, Chairs, Tables, "
            "Beds & Mattresses, Storage & Wardrobes, and Outdoor Furniture. "
            "All sofas are available in 2- and 3-seater configurations with a 5-year frame warranty. "
            "Solid wood options include Oak, Walnut and Beech. MDF and metal-frame alternatives are also available. "
            "Flat-pack items come with full assembly instructions and an average build time of 30–45 minutes. "
            "Delivery within the UK is free on orders over £500."
        )},
        "_modified": "2026-03-28T09:00:00Z"
    },
    {
        "_path": "/content/streamx/en/articles/sofa-styles-explained",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Sofa Styles Explained",
        "body": {"plaintext": (
            "We stock eight sofa styles: Modern, Classic, Scandinavian, Industrial, Velvet, Corner, Convertible and Modular. "
            "Scandinavian sofas feature clean lines, solid oak legs and natural fabric upholstery. "
            "Industrial sofas combine metal frames with distressed fabric or leather. "
            "Corner sofas are ideal for open-plan living rooms and seat 4–6 people. "
            "Convertible (sofa-bed) models include a pull-out mattress and storage compartment. "
            "Colour options range from Grey and Beige through to Terracotta and Forest Green."
        )},
        "_modified": "2026-03-29T09:00:00Z"
    },
    {
        "_path": "/content/streamx/en/articles/wardrobe-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Wardrobe Types and Materials",
        "body": {"plaintext": (
            "Our wardrobe range includes Sliding Door, Hinged Door, Walk-in, Corner, Open and Built-in Frame options. "
            "Sliding door wardrobes are ideal for rooms with limited floor space. "
            "Walk-in wardrobes are available as modular kits and include hanging rails, shelves and drawer units. "
            "Materials available: Oak, Walnut, Beech and MDF with various finishes (matte, gloss, wood-effect). "
            "Soft-close hinges are standard on all hinged models. 10-year structural warranty."
        )},
        "_modified": "2026-03-29T10:00:00Z"
    },

    # ── Electronics ──────────────────────────────────────────────────────────
    {
        "_path": "/content/streamx/en/articles/electronics-overview",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Electronics Catalogue Overview",
        "body": {"plaintext": (
            "The StreamX Electronics range covers three main categories: Laptops, TVs and Smartphones. "
            "Laptops are available under three brands: StreamX Pro, StreamX Air and StreamX Ultra, "
            "spanning screen sizes from 13 to 16 inches with Intel Core i5/i7/i9, AMD Ryzen 7 and Apple M3 processors. "
            "All laptops ship with a 2-year carry-in warranty. "
            "TVs range from 32 to 85 inches and include OLED, QLED, LED, Mini-LED and 8K OLED panel types. "
            "Smartphones are offered as StreamX Phone 15, Phone 15 Pro, Phone 15 Ultra, Fold 3 and Flip 5, "
            "with storage from 128 GB to 1 TB and full 5G support."
        )},
        "_modified": "2026-03-30T08:00:00Z"
    },
    {
        "_path": "/content/streamx/en/articles/laptop-comparison",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Laptop Comparison: Pro vs Air vs Ultra",
        "body": {"plaintext": (
            "StreamX Pro is our performance flagship, available with Intel Core i9 or AMD Ryzen 7, up to 32 GB RAM "
            "and 1 TB SSD. Best for developers, video editors and power users. "
            "StreamX Air is the lightweight everyday laptop: up to 14 inches, Intel Core i7, 16 GB RAM, 512 GB SSD. "
            "Battery life up to 18 hours. Ideal for professionals on the move. "
            "StreamX Ultra bridges the gap: Apple M3 or M3 Pro chip, up to 18 GB unified memory, "
            "fanless silent operation and a 2K Liquid Retina display. "
            "All models feature Thunderbolt 4 ports and a 2-year carry-in warranty."
        )},
        "_modified": "2026-03-30T08:30:00Z"
    },
    {
        "_path": "/content/streamx/en/articles/tv-buying-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "TV Buying Guide: OLED vs QLED vs LED",
        "body": {"plaintext": (
            "OLED TVs deliver perfect blacks and infinite contrast — ideal for dark rooms and cinephiles. "
            "Our OLED range starts at 55 inches. "
            "QLED TVs use quantum-dot technology for vibrant colours and high brightness — great for bright living rooms. "
            "LED and Mini-LED TVs offer the best value for everyday viewing; Mini-LED adds local dimming zones "
            "for improved HDR performance. "
            "8K OLED models are available at 75 and 85 inches for future-proof purchases. "
            "All StreamX Smart TVs run StreamX OS with built-in Netflix, Disney+, Prime Video and Apple TV+. "
            "HDMI 2.1 x3 is standard on 55-inch and above models."
        )},
        "_modified": "2026-03-30T09:00:00Z"
    },

    # ── Kitchen ──────────────────────────────────────────────────────────────
    {
        "_path": "/content/streamx/en/articles/kitchen-cookware-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Cookware Materials Guide",
        "body": {"plaintext": (
            "We offer five cookware material types: Cast Iron, Stainless Steel, Non-stick Titanium, Ceramic Coated and Carbon Steel. "
            "Cast Iron retains heat exceptionally well and is ideal for slow cooking and searing. "
            "Stainless Steel is durable, non-reactive and dishwasher-safe — the most versatile choice. "
            "Non-stick Titanium coating provides effortless food release and is PFOA-free. "
            "Ceramic Coated pans are a natural, chemical-free non-stick option. "
            "Carbon Steel is the professional chef's choice: light, responsive and naturally non-stick once seasoned. "
            "All cookware is induction, gas, electric and ceramic hob compatible. Oven-safe to 260°C."
        )},
        "_modified": "2026-03-27T10:00:00Z"
    },
    {
        "_path": "/content/streamx/en/articles/kitchen-appliances-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Kitchen Appliances Buying Guide",
        "body": {"plaintext": (
            "Our kitchen appliance range includes: Stand Mixer, Air Fryer, Espresso Machine, Blender Pro, "
            "Bread Maker, Food Processor, Toaster Oven, Induction Hob, Sous Vide Stick and Electric Kettle. "
            "The Stand Mixer features a 1200W motor, 6 speeds and a 5L bowl — suitable for bread, pasta and cakes. "
            "The Air Fryer has a 6L XXL capacity and 8 cooking presets with rapid air circulation. "
            "The Espresso Machine has a 15-bar pump, built-in grinder and steam wand. "
            "All appliances are available in Black, Silver, White and Red. 2-year warranty, CE certified."
        )},
        "_modified": "2026-03-27T11:00:00Z"
    },

    # ── Lighting ─────────────────────────────────────────────────────────────
    {
        "_path": "/content/streamx/en/articles/lighting-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Lighting Guide: Styles, Types and Finishes",
        "body": {"plaintext": (
            "Our lighting catalogue includes ten product types: Floor Lamp, Table Lamp, Pendant Light, Ceiling Light, "
            "Wall Sconce, Desk Lamp, LED Strip, Chandelier, Bedside Lamp and Outdoor Lantern. "
            "Available in six styles: Minimalist, Industrial, Art Deco, Scandinavian, Mid-Century and Contemporary. "
            "Finish options: Matte Black, Brushed Gold, Chrome, Antique Brass, White and Copper. "
            "All fixtures are compatible with E27 LED bulbs (included). IP44 rated for bathroom and outdoor use. "
            "Energy-efficient LED technology throughout the range. 2-year warranty."
        )},
        "_modified": "2026-03-26T09:00:00Z"
    },

    # ── Outdoor & Garden ─────────────────────────────────────────────────────
    {
        "_path": "/content/streamx/en/articles/outdoor-garden-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Outdoor & Garden Catalogue Guide",
        "body": {"plaintext": (
            "The StreamX Outdoor range covers Garden Furniture and Garden Tools. "
            "Garden furniture materials include Rattan, Teak, Aluminium, FSC Pine, Polywood and Recycled Plastic. "
            "Sets include: Garden Sofa Set, Sun Lounger, Dining Set, Hanging Chair, Bistro Set, Daybed and Bar Set. "
            "All outdoor furniture is UV-resistant and weatherproof; cushions and water-resistant covers included. "
            "Garden tools available: Lawn Mower, Hedge Trimmer, Leaf Blower (cordless 36V), "
            "Garden Shredder, Pressure Washer (150 bar), Rotary Tiller, Garden Vacuum and Digital Water Timer. "
            "Tools come in Standard, Pro and Cordless variants. 3-year warranty on all tools."
        )},
        "_modified": "2026-03-25T10:00:00Z"
    },

    # ── Home Decor ───────────────────────────────────────────────────────────
    {
        "_path": "/content/streamx/en/articles/home-decor-guide",
        "_metadata": {"stringMetadata": [{"name": "cq:model", "value": "article"}]},
        "title": "Home Decor Collection Overview",
        "body": {"plaintext": (
            "Our Home Decor collection features eight product families: Throw Pillows, Area Rugs, Wall Art Prints, "
            "Vases, Scented Candles, Curtains, Blankets and Mirrors. "
            "Throw Pillows are available in Linen, Velvet and Cotton, in colours including Sage Green, "
            "Dusty Pink, Ochre, Navy and Stone. "
            "Area Rugs come in Wool, Jute, Viscose and Cotton; sizes from 80×150 cm to 200×300 cm. "
            "Scented Candles are made from Soy Wax, Beeswax or Coconut Wax in fragrances such as "
            "Oakmoss, Vanilla & Amber, Sea Salt, Bergamot and Sandalwood. "
            "All items are ethically sourced and handcrafted. Gift-wrap available."
        )},
        "_modified": "2026-03-24T10:00:00Z"
    },
]

PAGES_DATA = {
    "jcr:primaryType": "cq:Page",
    "en": {"jcr:primaryType": "cq:Page"}
}

PAGE_MODELS = {}  # no platform pages — catalog only

GRAPHQL_RESPONSE = {
    "data": {
        "articleList": {
            "items": CONTENT_FRAGMENTS
        }
    }
}


class AemMockHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # quiet per-request logging; startup summary printed below

    def do_GET(self):
        if self.path == "/content/streamx.1.json":
            self.send_json(PAGES_DATA)
            return

        for path, model in PAGE_MODELS.items():
            if self.path == f"{path}.model.json":
                self.send_json(model)
                return

        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        if self.path in [
            "/content/_cq_graphql/global/endpoint.json",
            "/graphql"
        ]:
            self.send_json(GRAPHQL_RESPONSE)
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
    server = HTTPServer(("0.0.0.0", 4502), AemMockHandler)
    print(f"AEM Mock server running on http://0.0.0.0:4502  "
          f"({len(CONTENT_FRAGMENTS)} content fragments, {len(PAGE_MODELS)} pages)")
    server.serve_forever()
