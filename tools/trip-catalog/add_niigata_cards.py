"""Adds every Niigata manhole card to the bundled catalogue (#65, Niigata first, then Saitama and Tokyo).

    python tools/trip-catalog/add_niigata_cards.py app/src/main/assets/catalogue/trip-niigata.csv

The asset CSV is the source since phase 5 (dam rows were added to it directly), so this edits it in place
rather than regenerating it. Rows are keyed by card code: a code already in the file keeps its hand-checked
place unless it has no position yet (the owner's cards read from their video), in which case the place is filled.

Source: Eki kara Manhole's Niigata list (https://ekikaramanhole.whitebeach.org/ext/manholecard/search.cgi?m=15,
48 cards) and its hand-out map feed, read 2026-11-07; snapshot in sources/ekikaramanhole-niigata-2026-11-07.json.
Positions are the hand-out places from that feed (its "ここに行く" link), not the manholes printed on the cards.
Hours are condensed from the feed text; source_url is the city's stock-check page the feed links to.
"""
import csv
import sys

TOWN = {
    "長岡市": "Nagaoka-shi", "三条市": "Sanjo-shi", "柏崎市": "Kashiwazaki-shi", "新発田市": "Shibata-shi",
    "小千谷市": "Ojiya-shi", "加茂市": "Kamo-shi", "十日町市": "Tokamachi-shi", "見附市": "Mitsuke-shi",
    "村上市": "Murakami-shi", "燕市": "Tsubame-shi", "糸魚川市": "Itoigawa-shi", "阿賀野市": "Agano-shi",
    "佐渡市": "Sado-shi", "胎内市": "Tainai-shi", "出雲崎町": "Izumozaki-machi",
}
# Designs the cities publish (see make_catalog.py DESIGNS for how Nagaoka's were tied to codes).
DESIGNS = {
    "15-202-D001": ("かわぐっち", "Kawagutchi"),
    "15-202-E001": ("あぶらげんしん", "Abura Genshin"),
    "15-202-F001": ("長岡の四季", "Four seasons"),
}

TOKAMACHI = "http://www.city.tokamachi.lg.jp/soshiki/ke/jogesuidokyoku/04/event/index.html"
# code, town, series, lat, lng, place, address, phone, hours, notes, source_url
CARDS = [
    ("15-202-D001", "長岡市", "18", 37.260973, 138.867894, "道の駅越後川口", "長岡市川口中山84-2", "0258-89-4550",
     "4～11月 9:00～18:00／12～3月 9:00～17:00（火曜は休み）", None, None),
    ("15-202-E001", "長岡市", "19", 37.459600, 139.007266, "道の駅R290とちお", "長岡市栃尾宮沢1764", "0258-53-2030",
     "9:00～17:00（1・2月の平日 10:00～16:00、1/2・1/3 9:00～15:00、12/31～1/1は休み）", None, None),
    ("15-202-F001", "長岡市", "22", 37.448291, 138.848780, "米百俵プレイス ミライエ長岡 3階 インフォメーションカウンター",
     "長岡市大手通2-3-10", "0258-86-6008", "平日・土 9:00～21:00／日祝 9:00～18:00（第2木曜、月末、12/29～1/3は休館）", None, None),
    ("15-204-A001", "三条市", "05", 37.632824, 138.953476, "三条鍛冶道場", "三条市元町11-53", "0256-34-8080",
     "9:00～17:00（月曜（祝日なら翌日）、12/29～1/3を除く）",
     "鍛冶道場の休館日は三条下水処理センター（三条市塚野目2323、8:30～17:15）または中央公民館（三条市元町13-1、0256-32-4811、9:00～22:00）で配布",
     "https://www.city.sanjo.niigata.jp/"),
    ("15-205-A001", "柏崎市", "09", 37.367357, 138.546492, "かしわざき市民活動センターまちから", "柏崎市西本町3-2-8", "0257-22-2003",
     "9:30～21:00（火曜（祝日なら翌日）、12/29～1/3を除く）", None, None),
    ("15-205-B001", "柏崎市", "13", 37.373143, 138.552350, "新潟県立柏崎アクアパーク", "柏崎市学校町6-73", "0257-22-5555",
     "平日・土 10:00～20:00／日 10:00～18:00（火曜（祝日ならその後の平日）、12/31、1/1を除く）", None, None),
    ("15-206-A001", "新発田市", "08", 37.947639, 139.326847, "まちなか観光案内所", "新発田市中央町3-2-22 松緑館ビル1階", "0254-26-6789",
     "8:30～17:00（12/29～1/3を除く）", None, None),
    ("15-206-B001", "新発田市", "12", 37.945811, 139.329344, "寺町たまり駅", "新発田市諏訪町2-3-28", "0254-22-1888",
     "9:00～16:30（12/29～1/3を除く）", None, None),
    ("15-206-C001", "新発田市", "16", 37.880764, 139.307521, "月岡温泉旅館協同組合", "新発田市月岡温泉546-1", "0254-32-2975",
     "10:00～12:00、13:00～17:00（12/30～1/3を除く）", None, None),
    ("15-206-D001", "新発田市", "28", None, None, None, None, None, None,
     "配布場所は未確認（2026-11-07）。新発田市下水道課 0254-23-7284", None),
    ("15-208-A001", "小千谷市", "03", 37.311111, 138.795278, "小千谷市錦鯉の里", "小千谷市城内1-8-22", "0258-83-2233",
     "9:00～17:00（年末年始を除く）", None, None),
    ("15-208-B001", "小千谷市", "16", 37.310834, 138.796339, "小千谷市総合産業会館 サンプラザ", "小千谷市城内1-8-25", "0258-83-4800",
     "9:00～17:30（12～2月は17:00まで、年末年始を除く）", None, None),
    ("15-209-A001", "加茂市", "18", 37.600494, 139.110576, "加茂七谷温泉 美人の湯", "加茂市大字宮寄上13-1", "0256-41-4122",
     "10:00～21:00（第2・第4水曜は配布なし）", "営業日は美人の湯のホームページで確認", "https://www.city.kamo.niigata.jp/docs/60615.html"),
    ("15-210-A001", "十日町市", "07", 37.132612, 138.613829, "松代・松之山温泉観光案内所", "十日町市松代3816-1（道の駅まつだいふるさと会館内）",
     "025-597-3442", "9:00～17:00（年末年始を除く）", None, TOKAMACHI),
    ("15-210-B001", "十日町市", "08", 37.066387, 138.707043, "宮中島温泉ミオンなかさと", "十日町市宮中己4197", "025-763-4811",
     "10～3月 10:00～22:00（木曜はゆくら妻有 日帰り温泉で配布、8月を除く）",
     "4～9月は清津渓谷トンネル エントランスPeriscope（十日町市小出2119、025-763-4800、8:30～16:00）で配布", TOKAMACHI),
    ("15-210-C001", "十日町市", "13", 37.141477, 138.755929, "十日町市博物館", "十日町市西本町1-448-9", "025-757-5531",
     "9:00～17:00（月曜（祝日なら翌日）、年末年始を除く）", None, TOKAMACHI),
    ("15-211-A001", "見附市", "17", 37.531763, 138.914891, "ネーブルみつけ みらい市場", "見附市学校町1-16-15", "0258-62-7877",
     "9:00～17:00（年末年始、休館日を除く）", None, None),
    ("15-212-A001", "村上市", "04", 38.225614, 139.481105, "村上市郷土資料館 おしゃぎり会館", "村上市三之町7-9", "0254-52-1347",
     "9:00～16:30（年末年始を除く）", None, None),
    ("15-212-B001", "村上市", "05", 38.375252, 139.458546, "道の駅笹川流れ（夕日会館）", "村上市桑川891-1", "0254-79-2017",
     "9:00～17:30（毎月最終水曜（11～3月は毎週水曜）、12/30～1/1を除く）", None, None),
    ("15-212-C001", "村上市", "06", 38.125274, 139.444040, "荒川地区公民館", "村上市羽ヶ榎104-25", "0254-62-3050",
     "9:00～17:00（年末年始を除く）", None, None),
    ("15-212-D001", "村上市", "08", 38.282807, 139.522737, "道の駅朝日 朝日みどりの里物産会館", "村上市猿沢1215", "0254-72-1551",
     "9:00～18:00（毎月最終月曜、12/31～1/1、臨時休館日を除く）", None, None),
    ("15-212-E001", "村上市", "11", 38.172902, 139.460981, "道の駅神林（穂波の里）道路情報ターミナル", "村上市九日市809", "0254-66-6326",
     "8:30～16:15（年末年始を除く）", None, None),
    ("15-213-A001", "燕市", "06", 37.630846, 138.843335, "燕市分水良寛史料館", "燕市上諏訪9-9", "0256-97-2428",
     "9:00～16:30（月曜（祝日なら翌日）、年末年始を除く）", None, None),
    ("15-213-B001", "燕市", "10", 37.652754, 138.919169, "燕市産業史料館", "燕市大曲4330-1", "0256-63-7666",
     "9:00～16:30（月曜（祝日なら翌日）、年末年始を除く）", None, None),
    ("15-213-C001", "燕市", "16", 37.651871, 138.869263, "燕市長善館史料館", "燕市粟生津97", "0256-93-5400",
     "9:00～16:30（月曜（祝日なら翌日）、12/29～1/3を除く）", None, None),
    ("15-213-D001", "燕市", "17", 37.662854, 138.825183, "道の駅 国上（ふれあい交流センター）", "燕市国上5866-1", "0256-98-0770",
     "10:00～17:00（臨時休業あり）", None, None),
    ("15-216-A001", "糸魚川市", "10", 37.044711, 137.859224, "キターレ", "糸魚川市大町2-2-19", "025-552-1540",
     "9:00～22:00（12/29～1/3を除く）", "電話は糸魚川市ガス水道局", None),
    ("15-223-A001", "阿賀野市", "08", 37.802832, 139.289545, "五頭山麓うららの森情報発信館", "阿賀野市村杉3946-163", "0250-61-3511",
     "9:00～17:00（火曜（祝日なら翌日）、年末年始を除く）", None, None),
    ("15-224-A001", "佐渡市", "21", 38.041694, 138.256001, "佐渡金銀山ガイダンス施設 きらりうむ佐渡", "佐渡市相川三町目浜町18", "0259-74-2215",
     "8:30～17:00（年末年始を除く）", None, None),
    ("15-224-B001", "佐渡市", "22", 38.018261, 138.368383, "佐渡市役所本庁舎1階 総合案内窓口", "佐渡市千種232", "0259-67-7857",
     "8:30～17:00", "土日祝は本庁舎1階の宿日直室で配布。電話は上下水道課", None),
    ("15-224-C001", "佐渡市", "23", 37.815185, 138.279749, "南佐渡観光案内所", "佐渡市小木町1935-26", "0259-86-3200",
     "8:30～17:00（年末年始を除く）", None, None),
    ("15-224-D001", "佐渡市", "24", 37.990165, 138.396073, "佐渡市畑野市民センター", "佐渡市畑野甲533", "0259-66-3111", "8:30～17:00", None, None),
    ("15-224-E001", "佐渡市", "25", 37.865743, 138.410642, "佐渡市赤泊市民センター", "佐渡市赤泊2458", "0259-87-3111", "8:30～17:00", None, None),
    ("15-224-F001", "佐渡市", "26", 38.016843, 138.426945, "佐渡市新穂市民センター", "佐渡市新穂瓜生屋501", "0259-22-3111", "8:30～17:00", None, None),
    ("15-224-G001", "佐渡市", "27", 37.998325, 138.321812, "佐渡中央会館", "佐渡市河原田本町394", "0259-57-2711",
     "8:30～17:00（年末年始を除く）", None, None),
    ("15-224-H001", "佐渡市", "28", 38.080505, 138.434920, "両津市民センター", "佐渡市両津湊198", "0259-27-2111", "8:30～17:00", None, None),
    ("15-224-I001", "佐渡市", "29", 37.998325, 138.321912, "佐和田市民センター", "佐渡市河原田本町394", "0259-57-2111", "8:30～17:00",
     "佐渡中央会館（G001）と同じ建物", None),
    ("15-227-A001", "胎内市", "10", 38.063408, 139.453000, "胎内市観光協会", "胎内市下赤谷387-1", "0254-47-2723", "9:00～18:00", None, None),
    ("15-227-B001", "胎内市", "21", 38.022505, 139.499809, "ロイヤル胎内パークホテル", "胎内市夏井1191-3", "0254-48-2211", "9:00～18:00", None, None),
    ("15-405-A001", "出雲崎町", "16", 37.538824, 138.678703, "道の駅 越後出雲崎天領の里", "三島郡出雲崎町大字尼瀬6-57", "0258-78-4000",
     "10:00～16:00（水曜、年末年始を除く。祝日・GW・夏休みは水曜も配布、4～10月の休みは第1水曜のみ）",
     "道の駅の休館日（平日の水曜）は出雲崎町役場 建設課（出雲崎町大字川西140、0258-78-2296、9:00～17:00）で配布", None),
]

EKM = "https://ekikaramanhole.whitebeach.org/ext/manholecard/search.cgi?m=15"


def names(code, town):
    ro = TOWN[town]
    d = DESIGNS.get(code)
    if d is None:
        return f"{town} {code[-4:]}", f"{ro} {code[-4:]}"
    return f"{town} {d[0]}", f"{ro.removesuffix('-shi')} · {d[1]}"


def main(path):
    with open(path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        cols, rows = reader.fieldnames, list(reader)
    by_code = {r["code"]: r for r in rows if r["type"] == "MANHOLE_CARD" and r["code"]}
    added = filled = kept = 0
    last_card = max(i for i, r in enumerate(rows) if r["type"] == "MANHOLE_CARD")
    new_rows = []
    for code, town, series, lat, lng, place, addr, phone, hours, notes, src in CARDS:
        ja, ro = names(code, town)
        fields = {
            "place_name": place, "address": addr, "place_phone": phone, "lat": lat, "lng": lng,
            "hours_text": hours, "notes": notes, "source_url": src or EKM,
        }
        fields = {k: ("" if v is None else str(v)) for k, v in fields.items()}
        row = by_code.get(code)
        if row is not None:
            if row["lat"]:
                kept += 1
                continue
            row.update(fields)
            filled += 1
            continue
        row = {c: "" for c in cols}
        row.update(type="MANHOLE_CARD", code=code, name_ja=ja, name_romaji=ro, series=series,
                   prefecture="新潟県", municipality=town, **fields)
        new_rows.append(row)
        added += 1
    rows[last_card + 1:last_card + 1] = new_rows
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    print(f"added {added}, filled {filled}, kept {kept} -> {path}")


if __name__ == "__main__":
    main(sys.argv[1])
