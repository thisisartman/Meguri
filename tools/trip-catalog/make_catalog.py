"""Build the trip catalogue CSV: JR Joetsu, Yahiko, Echigo and Shin'etsu Line stations (OpenStreetMap) plus the Niigata manhole cards."""
import csv, json, sys

stations = json.load(open(sys.argv[1], encoding="utf-8")) | json.load(open(sys.argv[2], encoding="utf-8"))
out = sys.argv[3]
ORDER = ["高崎", "高崎問屋町", "井野", "新前橋", "群馬総社", "八木原", "渋川", "敷島", "津久田", "岩本", "沼田", "後閑",
         "上牧", "水上", "湯檜曽", "土合", "土樽", "越後中里", "岩原スキー場前", "越後湯沢", "石打", "大沢",
         "上越国際スキー場前", "塩沢", "六日町", "五日町", "浦佐", "八色", "小出", "越後堀之内", "北堀之内",
         "越後川口", "小千谷", "越後滝谷", "宮内"]
GUNMA = set(ORDER[:16])
LINES = [
    ("JR_JOETSU", "上越線", "Joetsu Line", ORDER),
    ("JR_YAHIKO", "弥彦線", "Yahiko Line", ["弥彦", "矢作", "吉田", "西燕", "燕", "燕三条", "北三条", "東三条"]),
    ("JR_ECHIGO", "越後線", "Echigo Line",
     ["柏崎", "東柏崎", "西中通", "荒浜", "刈羽", "西山", "礼拝", "石地", "小木ノ城", "出雲崎", "妙法寺", "小島谷", "桐原", "寺泊", "分水",
      "粟生津", "南吉田", "吉田", "北吉田", "岩室", "巻", "越後曽根", "越後赤塚", "内野西が丘", "内野", "新潟大学前", "寺尾", "小針", "青山",
      "関屋", "白山", "新潟"]),
    ("JR_SHINETSU", "信越本線", "Shin'etsu Line",
     ["宮内", "長岡", "北長岡", "押切", "見附", "帯織", "東光寺", "三条", "東三条", "保内", "加茂", "羽生田", "田上", "矢代田", "古津", "新津",
      "さつき野", "荻川", "亀田", "越後石山", "新潟"]),
]

cols = ["type", "catalog_key", "code", "name_ja", "name_romaji", "series", "release_date", "prefecture", "municipality",
        "place_name", "address", "place_phone", "lat", "lng", "hours_text", "closed_days", "notes", "source_url",
        "line_id", "line_name_ja", "line_name_romaji", "line_seq"]
rows = []
for line_id, line_ja, line_ro, names in LINES:
    for seq, n in enumerate(names, 1):
        s = stations[n]
        rows.append({
            "type": "EKI_STAMP", "name_ja": n + "駅", "name_romaji": f"{s['en']} Station",
            "prefecture": "群馬県" if n in GUNMA else "新潟県", "place_name": n + "駅",
            "lat": f"{s['lat']:.6f}", "lng": f"{s['lng']:.6f}",
            # Shown on the item page: written for the person using the app; the credit is required by the ODbL
            "notes": "Paper stamp not confirmed at this station. Map data © OpenStreetMap contributors.",
            "source_url": "https://www.openstreetmap.org",
            "line_id": line_id, "line_name_ja": line_ja, "line_name_romaji": line_ro, "line_seq": seq,
        })

def dms(d, m, s):
    return round(d + m / 60 + s / 3600, 6)

# Read from Manhole Card Navi / Manhole Card Map on 2026-11-02 (personal use). 15-202-A001's position is the manhole
# printed on the card, next to its hand-out place.
cards = [
    # Hand-out places from the Niigata prefecture and city pages read 2026-11-03; positions are the buildings from OpenStreetMap.
    ("15-000-A001", "新潟県（流域下水道）", "Niigata-ken (Ryuiki Gesuido)", "06", "2017/12/09", "新潟県", None, 37.951155, 139.124041,
     "新潟県下水道公社（新潟浄化センター管理棟 2階事務室）", "新潟市東区下山3丁目680番地", "025-271-1151",
     "平日 9:00～17:00／土日祝・年末年始 10:00～15:00（土日祝・年末年始は予約が必要）"),
    ("15-100-A001", "新潟市", "Niigata-shi", "01", "2016/04/01", "新潟県", "新潟市", 37.922414, 139.045026,
     "新潟古町まちみなと情報館", "新潟市中央区古町通七番町995番地7", "025-378-4101", "10:00～17:00（水曜、年末年始を除く。営業日は施設に確認）"),
    ("15-100-B001", "新潟市", "Niigata-shi", "06", "2017/12/09", "新潟県", "新潟市", 37.801767, 139.141835,
     "新津鉄道資料館", "新潟市秋葉区新津東町2-5-6", "0250-24-5700", "9:30～17:00（火曜（祝日なら翌日）、年末年始を除く）"),
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
    })

# Cards the owner already has that are not trip goals: no position or hand-out place, so they stay off Nearby.
# Codes matched by the coordinates printed on the owner's cards (video, 2026-11-03) against Eki kara Manhole's list.
owned = [
    ("15-202-F001", "長岡市", "Nagaoka-shi", "22", "新潟県", "長岡市"),
    ("15-205-A001", "柏崎市", "Kashiwazaki-shi", "09", "新潟県", "柏崎市"),
    ("15-205-B001", "柏崎市", "Kashiwazaki-shi", "13", "新潟県", "柏崎市"),
    ("15-224-A001", "佐渡市", "Sado-shi", "21", "新潟県", "佐渡市"),
    ("15-224-B001", "佐渡市", "Sado-shi", "22", "新潟県", "佐渡市"),
    ("15-224-D001", "佐渡市", "Sado-shi", "24", "新潟県", "佐渡市"),
    ("15-224-F001", "佐渡市", "Sado-shi", "26", "新潟県", "佐渡市"),
    ("15-224-G001", "佐渡市", "Sado-shi", "27", "新潟県", "佐渡市"),
    ("15-224-H001", "佐渡市", "Sado-shi", "28", "新潟県", "佐渡市"),
    ("13-100-C001", "東京23区", "Tokyo 23-ku", "05", "東京都", None),
    ("13-100-D001", "東京23区", "Tokyo 23-ku", "05", "東京都", None),
    ("13-100-L101", "東京23区", "Tokyo 23-ku", "24", "東京都", None),
    ("13-100-N001", "東京23区", "Tokyo 23-ku", "14", "東京都", None),
    ("13-100-O001", "新宿区", "Shinjuku-ku", "15", "東京都", "新宿区"),
    ("13-100-X001", "葛飾区", "Katsushika-ku", "15", "東京都", "葛飾区"),
    ("13-100-Y001", "江戸川区", "Edogawa-ku", "15", "東京都", "江戸川区"),
    ("13-219-A001", "狛江市", "Komae-shi", "06", "東京都", "狛江市"),
    ("13-219-B001", "狛江市", "Komae-shi", "15", "東京都", "狛江市"),
]
for code, ja, ro, wave, pref, muni in owned:
    rows.append({
        "type": "MANHOLE_CARD", "code": code, "name_ja": f"{ja} {code[-4:]}", "name_romaji": f"{ro} {code[-4:]}",
        "series": wave, "prefecture": pref, "municipality": muni,
    })
rows.append({
    "type": "GOSHUIN", "name_ja": "豪徳寺", "name_romaji": "Gotokuji", "prefecture": "東京都", "municipality": "世田谷区",
})

with open(out, "w", encoding="utf-8", newline="") as f:
    w = csv.DictWriter(f, fieldnames=cols)
    w.writeheader()
    for r in rows:
        w.writerow({k: ("" if r.get(k) is None else r.get(k)) for k in cols})
print(len(rows), "rows ->", out)
