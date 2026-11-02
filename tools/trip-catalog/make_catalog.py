"""Build the trip catalogue CSV: JR Joetsu Line stations (OpenStreetMap) plus the Niigata manhole cards read so far."""
import csv, json, sys

stations = json.load(open(sys.argv[1], encoding="utf-8"))
out = sys.argv[2]
ORDER = ["高崎", "高崎問屋町", "井野", "新前橋", "群馬総社", "八木原", "渋川", "敷島", "津久田", "岩本", "沼田", "後閑",
         "上牧", "水上", "湯檜曽", "土合", "土樽", "越後中里", "岩原スキー場前", "越後湯沢", "石打", "大沢",
         "上越国際スキー場前", "塩沢", "六日町", "五日町", "浦佐", "八色", "小出", "越後堀之内", "北堀之内",
         "越後川口", "小千谷", "越後滝谷", "宮内"]
GUNMA = set(ORDER[:16])

cols = ["type", "catalog_key", "code", "name_ja", "name_romaji", "series", "release_date", "prefecture", "municipality",
        "place_name", "address", "place_phone", "lat", "lng", "hours_text", "closed_days", "notes", "source_url",
        "line_id", "line_name_ja", "line_name_romaji", "line_seq"]
rows = []
for seq, n in enumerate(ORDER, 1):
    s = stations[n]
    rows.append({
        "type": "EKI_STAMP", "name_ja": n + "駅", "name_romaji": f"{s['en']} Station",
        "prefecture": "群馬県" if n in GUNMA else "新潟県", "place_name": n + "駅",
        "lat": f"{s['lat']:.6f}", "lng": f"{s['lng']:.6f}",
        "notes": "Station position from OpenStreetMap (ODbL). Whether this station has a paper stamp is not checked.",
        "source_url": "https://www.openstreetmap.org",
        "line_id": "JR_JOETSU", "line_name_ja": "上越線", "line_name_romaji": "Joetsu Line", "line_seq": seq,
    })

def dms(d, m, s):
    return round(d + m / 60 + s / 3600, 6)

# Read from Manhole Card Navi / Manhole Card Map on 2026-11-02 (personal use). Positions are the manhole
# printed on the card, not yet the hand-out place, except where the place is known to be next to it.
cards = [
    ("15-000-A001", "新潟県（流域下水道）", "Niigata-ken (Ryuiki Gesuido)", "06", "2017/12/09", "新潟県", None, dms(37, 56, 2.8), dms(139, 7, 40.9), None, None, None, None),
    ("15-100-A001", "新潟市", "Niigata-shi", "01", "2016/04/01", "新潟県", "新潟市", dms(37, 54, 49.2), dms(139, 3, 42.8), None, None, None, None),
    ("15-100-B001", "新潟市", "Niigata-shi", "06", "2017/12/09", "新潟県", "新潟市", dms(37, 47, 57.0), dms(139, 7, 18.7), None, None, None, None),
    ("15-202-A001", "長岡市", "Nagaoka-shi", "07", "2018/04/28", "新潟県", "長岡市", dms(37, 26, 50.1), dms(138, 51, 4.0),
     "ながおか市民協働センター", "長岡市大手通1-4-10 シティホールプラザアオーレ長岡 西棟3階", "0258-39-2020", "8:30～21:00（12/29～1/3を除く）"),
    # B, C, G and Tsubame E: hand-out places from the cities' pages read 2026-11-03; positions are the
    # hand-out buildings from OpenStreetMap.
    ("15-202-B001", "長岡市", "Nagaoka-shi", "13", "2020/12/17", "新潟県", "長岡市", 37.455291, 138.804645,
     "道の駅ながおか花火館 長岡花火ミュージアム", "長岡市喜多町707", "0258-86-7766", "10:00～17:30（水曜、12/31～1/2を除く）"),
    ("15-202-C001", "長岡市", "Nagaoka-shi", "14", None, "新潟県", "長岡市", 37.446261, 138.851310,
     "ながおか市民協働センター", "長岡市大手通1-4-10 シティホールプラザアオーレ長岡 西棟3階", "0258-39-2020", "8:30～21:00（12/29～1/3を除く）"),
    ("15-202-G001", "長岡市", "Nagaoka-shi", "29", "2026/07/31", "新潟県", "長岡市", 37.448388, 138.848878,
     "米百俵プレイス ミライエ長岡 3階 インフォメーションカウンター", "長岡市大手通2-3-10", "0258-86-6008",
     "平日・土 9:00～21:00／日祝 9:00～18:00（第2木曜、月末、12/29～1/3は休館）"),
    ("15-213-E001", "燕市", "Tsubame-shi", "29", "2026/07/31", "新潟県", "燕市", 37.673156, 138.882245,
     "燕市役所 2階 下水道課 20番窓口（土日祝は夜間休日受付）", "燕市吉田西太田1934", "0256-77-8291", "9:00～16:30（12/29～1/3は休止）"),
]
for code, ja, ro, wave, date, pref, muni, lat, lng, place, addr, phone, hours in cards:
    rows.append({
        "type": "MANHOLE_CARD", "code": code, "name_ja": f"{ja} {code[-4:]}", "name_romaji": f"{ro} {code[-4:]}",
        "series": wave, "release_date": date, "prefecture": pref, "municipality": muni,
        "place_name": place, "address": addr, "place_phone": phone, "lat": lat, "lng": lng, "hours_text": hours,
        "notes": None if place else "Position is the manhole on the card; hand-out place not checked yet.",
    })

with open(out, "w", encoding="utf-8", newline="") as f:
    w = csv.DictWriter(f, fieldnames=cols)
    w.writeheader()
    for r in rows:
        w.writerow({k: ("" if r.get(k) is None else r.get(k)) for k in cols})
print(len(rows), "rows ->", out)
