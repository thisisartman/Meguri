"""Prefecture outlines for the onboarding's region step (#81): which prefecture you are in, and the highlight on the map.

    python tools/map-pack/make_prefectures.py ne_10m_admin_1_states_provinces.geojson app/src/main/assets/map/prefectures.json

Source: Natural Earth 10m admin-1 states and provinces (public domain),
https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_10m_admin_1_states_provinces.geojson
Keeps Japan's 47, thins each outline (Douglas-Peucker), drops islets smaller than MIN_AREA, rounds to 4 decimals.
Output: [{"code": "JP-15", "ja": "新潟県", "en": "Niigata", "rings": [[[lng, lat], ...], ...]}, ...]
"""
import json
import sys

TOLERANCE = 0.008  # degrees, under a kilometre: enough for a highlight and for "which prefecture am I in"
MIN_AREA = 0.0015  # square degrees, about 15 km²; Sado and the larger islands stay


def area(ring):
    return abs(sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]))) / 2


def dp(points, tol):
    """Douglas-Peucker, iterative, so long coastlines don't hit the recursion limit."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (x1, y1), (x2, y2) = points[a], points[b]
        dx, dy = x2 - x1, y2 - y1
        norm = (dx * dx + dy * dy) ** 0.5 or 1e-12
        best, idx = 0.0, None
        for i in range(a + 1, b):
            x0, y0 = points[i]
            d = abs(dy * x0 - dx * y0 + x2 * y1 - y2 * x1) / norm
            if d > best:
                best, idx = d, i
        if idx is not None and best > tol:
            keep[idx] = True
            stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(points, keep) if k]


def thin_ring(ring, tol):
    """A closed ring starts and ends on the same point, which gives Douglas-Peucker a zero-length baseline:
    split it at its middle and thin each half."""
    pts = ring[:-1] if ring[0] == ring[-1] else ring
    mid = len(pts) // 2
    out = dp(pts[: mid + 1], tol)[:-1] + dp(pts[mid:] + [pts[0]], tol)
    return out if out[0] == out[-1] else out + [out[0]]


def main(src, out):
    data = json.load(open(src, encoding="utf-8"))
    result = []
    for f in data["features"]:
        p = f["properties"]
        if p.get("adm0_a3") != "JPN":
            continue
        g = f["geometry"]
        polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        rings = []
        for poly in polys:
            outer = poly[0]  # holes don't matter for a highlight
            if area(outer) < MIN_AREA:
                continue
            thin = thin_ring(outer, TOLERANCE)
            if len(thin) >= 4:
                rings.append([[round(x, 4), round(y, 4)] for x, y in thin])
        if not rings:  # a prefecture of islets only keeps its largest
            outer = max((poly[0] for poly in polys), key=area)
            rings = [[[round(x, 4), round(y, 4)] for x, y in thin_ring(outer, TOLERANCE)]]
        result.append({"code": p["iso_3166_2"], "ja": p["name_ja"], "en": p["name"], "rings": rings})
    result.sort(key=lambda r: r["code"])
    json.dump(result, open(out, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(len(result), "prefectures,", sum(len(r) for x in result for r in x["rings"]), "points ->", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
