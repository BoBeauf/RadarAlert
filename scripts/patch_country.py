#!/usr/bin/env python3
"""
Patch one-shot : calcule la colonne `country` pour les enregistrements
existants de raw_osm et radars, sans re-fetcher les sources.
À supprimer après exécution.
"""

import os, time
import requests
from datetime import datetime, timezone

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
BATCH_SIZE   = 500

# ── Même polygone que fetch_radars.py ─────────────────────────
_FRANCE_METRO = [
    (51.09,  2.55), (50.87,  1.58), (50.25,  1.63), (49.68, -1.62),
    (48.66, -1.97), (48.72, -2.98), (48.45, -4.77), (47.88, -4.35),
    (47.52, -2.75), (47.30, -2.52), (46.68, -1.97), (46.20, -1.55),
    (45.67, -1.22), (45.27, -1.07), (44.66, -1.22), (44.04, -1.24),
    (43.56, -1.50), (43.37, -1.77), (43.27, -0.57), (43.12,  0.16),
    (42.79,  0.72), (42.68,  1.17), (42.50,  1.93), (42.55,  2.45),
    (42.43,  3.16), (43.02,  3.06), (43.30,  3.53), (43.44,  4.55),
    (43.37,  5.04), (43.08,  5.83), (43.23,  6.68), (43.49,  7.07),
    (43.73,  7.42), (44.10,  7.67), (44.72,  6.87), (45.18,  6.75),
    (45.91,  6.88), (46.38,  6.92), (46.52,  6.18), (47.04,  6.02),
    (47.49,  7.08), (47.59,  7.58), (48.00,  7.59), (48.48,  7.77),
    (48.97,  7.96), (49.17,  6.93), (49.55,  6.34), (49.86,  5.82),
    (50.13,  5.12), (50.36,  4.89), (50.52,  4.16), (50.68,  3.37),
    (50.79,  2.84), (51.09,  2.55),
]

def _pip(lat, lng, poly):
    inside = False
    j = len(poly) - 1
    for i, (lat_i, lng_i) in enumerate(poly):
        lat_j, lng_j = poly[j]
        if (lat_i > lat) != (lat_j > lat):
            x_int = lng_i + (lat - lat_i) * (lng_j - lng_i) / (lat_j - lat_i)
            if lng < x_int:
                inside = not inside
        j = i
    return inside

def in_france_metro(lat, lng):
    if _pip(lat, lng, _FRANCE_METRO):
        return True
    return 41.33 <= lat <= 43.03 and 8.54 <= lng <= 9.57

# ── Client Supabase minimal ───────────────────────────────────
def log(msg):
    print(f"[{datetime.now(timezone.utc).strftime('%H:%M:%S')}] {msg}", flush=True)

sess = requests.Session()
sess.headers.update({
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
})

def select_all(table, cols):
    rows, offset = [], 0
    while True:
        r = sess.get(f"{SUPABASE_URL}/rest/v1/{table}",
                     params={"select": cols, "limit": 1000, "offset": offset}, timeout=60)
        r.raise_for_status()
        batch = r.json()
        rows.extend(batch)
        if len(batch) < 1000:
            break
        offset += 1000
    return rows

def patch_country(table, id_col, fr_ids, other_ids):
    """
    PATCH country='FR' sur les lignes FR, country=null sur les autres.
    Utilise le filtre `id=in.(...)` pour ne toucher que la colonne country.
    """
    def do_patch(ids, value, label):
        total = (len(ids) - 1) // BATCH_SIZE + 1
        for i in range(0, len(ids), BATCH_SIZE):
            batch = ids[i:i + BATCH_SIZE]
            ids_str = ",".join(str(x) for x in batch)
            r = sess.patch(
                f"{SUPABASE_URL}/rest/v1/{table}",
                params={id_col: f"in.({ids_str})"},
                json={"country": value},
                headers={"Prefer": "return=minimal"},
                timeout=60,
            )
            if not r.ok:
                print(f"  ERREUR {r.status_code}: {r.text[:300]}")
            r.raise_for_status()
            bn = i // BATCH_SIZE + 1
            if bn % 10 == 0 or bn == total:
                log(f"  {table} [{label}] batch {bn}/{total}")

    if fr_ids:
        do_patch(fr_ids, "FR", "FR")
    if other_ids:
        do_patch(other_ids, None, "null")

# ── raw_osm ───────────────────────────────────────────────────
log("Chargement raw_osm...")
t0 = time.time()
osm_rows = select_all("raw_osm", "source_id,lat,lng")
log(f"  {len(osm_rows)} radars OSM chargés en {time.time()-t0:.1f}s")

log("PIP France métropolitaine sur raw_osm...")
t0 = time.time()
fr_count = 0
osm_patch = []
for r in osm_rows:
    c = "FR" if in_france_metro(r["lat"], r["lng"]) else None
    osm_patch.append({"source_id": r["source_id"], "country": c})
    if c:
        fr_count += 1
log(f"  {fr_count} FR / {len(osm_rows) - fr_count} hors France — en {time.time()-t0:.1f}s")

log("PATCH raw_osm...")
t0 = time.time()
osm_fr    = [r["source_id"] for r in osm_patch if r["country"] == "FR"]
osm_other = [r["source_id"] for r in osm_patch if r["country"] is None]
patch_country("raw_osm", "source_id", osm_fr, osm_other)
log(f"  raw_osm OK en {time.time()-t0:.1f}s")

# ── radars ────────────────────────────────────────────────────
log("Chargement radars...")
t0 = time.time()
radar_rows = select_all("radars", "canonical_id,sources,lat,lng")
log(f"  {len(radar_rows)} radars FUSION chargés en {time.time()-t0:.1f}s")

log("Calcul country sur radars...")
t0 = time.time()
radar_patch = []
for r in radar_rows:
    sources = r.get("sources") or []
    if "sr" in sources or "gov" in sources:
        c = "FR"
    else:
        c = "FR" if in_france_metro(r["lat"], r["lng"]) else None
    radar_patch.append({"canonical_id": r["canonical_id"], "country": c})

log("PATCH radars...")
radar_fr    = [r["canonical_id"] for r in radar_patch if r["country"] == "FR"]
radar_other = [r["canonical_id"] for r in radar_patch if r["country"] is None]
patch_country("radars", "canonical_id", radar_fr, radar_other)
log(f"  radars OK en {time.time()-t0:.1f}s")

log("Patch terminé.")
