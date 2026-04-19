#!/usr/bin/env python3
"""
Consolide raw_gov, raw_sr, raw_osm en une table `radars` sans doublons.

Algorithme (index spatial par grille, sans PostGIS) :
  1. Chargement des 3 tables brutes depuis Supabase
  2. Index de grille 0,01° × 0,01° (≈ 1,1 km) sur GOV et OSM
  3. Phase SR  : pour chaque radar SR, trouve le GOV et l'OSM le plus proche (≤ 100 m)
  4. Phase GOV : pour les GOV non appariés, cherche l'OSM le plus proche
  5. Phase OSM : les OSM restants deviennent des clusters solo
  6. Pour chaque cluster, règles de priorité par champ → radar consolidé
  7. Upsert par batch dans la table `radars` avec change-detection

Règles de priorité par champ :
  lat/lng, type, speed_car/hgv  →  SR > GOV > OSM
  department                    →  GOV > SR  (OSM n/a)
  city                          →  GOV > OSM (SR n/a)
  route, direction              →  GOV > SR > OSM
  equipment, install_date,
  section_length_km             →  SR > GOV  (OSM n/a)
"""

import hashlib
import os
import time
from collections import defaultdict
from datetime import datetime, timezone

import requests

# ── Config ────────────────────────────────────────────────────

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]

PROXIMITY_LAT = 0.0009   # ≈ 100 m en latitude
PROXIMITY_LNG = 0.0013   # ≈ 100 m en longitude à 46°N
GRID_STEP     = 0.01     # taille cellule de grille (≈ 1,1 km)
BATCH_SIZE    = 500


# ── Helpers ───────────────────────────────────────────────────

def log(tag: str, msg: str):
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"[{ts}] [{tag}] {msg}", flush=True)

def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()

def make_hash(*values) -> str:
    content = "|".join("" if v is None else str(v) for v in values)
    return hashlib.md5(content.encode()).hexdigest()

def first_non_empty(*values):
    """Retourne la première valeur non-None et non-vide."""
    for v in values:
        if v is not None and v != "":
            return v
    return None


# ── Client Supabase REST ──────────────────────────────────────

class SupabaseRest:
    def __init__(self, url: str, key: str):
        self.base = f"{url}/rest/v1"
        self.session = requests.Session()
        self.session.headers.update({
            "apikey":        key,
            "Authorization": f"Bearer {key}",
            "Content-Type":  "application/json",
        })

    def select_all(self, table: str, columns: str) -> list[dict]:
        """Charge toute une table par pagination de 1 000."""
        rows = []
        offset = 0
        while True:
            resp = self.session.get(
                f"{self.base}/{table}",
                params={"select": columns, "limit": 1000, "offset": offset},
                timeout=60,
            )
            resp.raise_for_status()
            batch = resp.json()
            rows.extend(batch)
            if len(batch) < 1000:
                break
            offset += 1000
        return rows

    def select_existing(self, table: str, id_col: str) -> dict:
        """Retourne {id → {hash, changed_at}} pour le change-detection."""
        rows = self.select_all(table, f"{id_col},data_hash,changed_at")
        return {
            r[id_col]: {"hash": r["data_hash"], "changed_at": r["changed_at"]}
            for r in rows
        }

    def upsert(self, table: str, rows: list[dict]) -> None:
        resp = self.session.post(
            f"{self.base}/{table}",
            json=rows,
            headers={"Prefer": "resolution=merge-duplicates,return=minimal"},
            timeout=60,
        )
        resp.raise_for_status()


# ── Index spatial ─────────────────────────────────────────────

def grid_key(lat: float, lng: float) -> tuple[int, int]:
    return (int(lat / GRID_STEP), int(lng / GRID_STEP))

def build_grid(radars: list[dict]) -> dict:
    grid = defaultdict(list)
    for r in radars:
        grid[grid_key(r["lat"], r["lng"])].append(r)
    return grid

def nearby(grid: dict, lat: float, lng: float) -> list[dict]:
    """Retourne les radars dans les 9 cellules autour de (lat, lng)."""
    ci, cj = grid_key(lat, lng)
    result = []
    for di in (-1, 0, 1):
        for dj in (-1, 0, 1):
            result.extend(grid.get((ci + di, cj + dj), []))
    return result

def within_100m(lat1, lng1, lat2, lng2) -> bool:
    return abs(lat1 - lat2) <= PROXIMITY_LAT and abs(lng1 - lng2) <= PROXIMITY_LNG

def closest(candidates: list, lat: float, lng: float):
    if not candidates:
        return None
    return min(candidates, key=lambda r: (r["lat"] - lat) ** 2 + (r["lng"] - lng) ** 2)


# ── Construction d'un radar consolidé ────────────────────────

def build_consolidated(sr=None, gov=None, osm=None) -> dict:
    sources = [s for s, v in [("sr", sr), ("gov", gov), ("osm", osm)] if v]

    if sr:
        canonical_id = f"sr:{sr['source_id']}"
    elif gov:
        canonical_id = f"gov:{gov['source_id']}"
    else:
        canonical_id = f"osm:{osm['source_id']}"

    anchor = sr or gov or osm
    lat = anchor["lat"]
    lng = anchor["lng"]

    # Chaque champ : `dict and dict.get(key)` → None si dict absent, valeur sinon
    type_     = first_non_empty(sr and sr.get("type"), gov and gov.get("type"), osm and osm.get("type"))
    speed_car = first_non_empty(sr and sr.get("speed_car"), gov and gov.get("speed_car"), osm and osm.get("speed_limit"))
    speed_hgv = first_non_empty(sr and sr.get("speed_hgv"), gov and gov.get("speed_hgv"), osm and osm.get("speed_limit_hgv"))
    department      = first_non_empty(gov and gov.get("department"), sr and sr.get("department"))
    city            = first_non_empty(gov and gov.get("city"), osm and osm.get("city"))
    route           = first_non_empty(gov and gov.get("route"), sr and sr.get("route"), osm and osm.get("route"))
    direction       = first_non_empty(gov and gov.get("direction"), sr and sr.get("direction"), osm and osm.get("direction"))
    equipment       = first_non_empty(sr and sr.get("equipment"), gov and gov.get("equipment"))
    install_date    = first_non_empty(sr and sr.get("install_date"), gov and gov.get("install_date"))
    section_length_km = first_non_empty(sr and sr.get("section_length_km"), gov and gov.get("section_length_km"))

    # GOV et SR sont toujours France ; OSM hérite de sa colonne country
    if sr or gov:
        country = "FR"
    else:
        country = osm.get("country") if osm else None

    data_hash = make_hash(
        lat, lng, type_, speed_car, speed_hgv,
        department, city, route, direction,
        equipment, install_date, section_length_km,
        country, ",".join(sorted(sources)),
    )

    return {
        "canonical_id":       canonical_id,
        "sources":            sources,
        "gov_id":             gov["source_id"] if gov else None,
        "sr_id":              sr["source_id"]  if sr  else None,
        "osm_id":             osm["source_id"] if osm else None,
        "lat":                lat,
        "lng":                lng,
        "type":               type_,
        "speed_car":          speed_car,
        "speed_hgv":          speed_hgv,
        "department":         department,
        "city":               city,
        "route":              route,
        "direction":          direction,
        "equipment":          equipment,
        "install_date":       install_date,
        "section_length_km":  section_length_km,
        "country":            country,
        "source_count":       len(sources),
        "data_hash":          data_hash,
    }


# ── Consolidation ─────────────────────────────────────────────

def consolidate(sr_data: list, gov_data: list, osm_data: list) -> list[dict]:
    log("MERGE", f"Début — SR:{len(sr_data)}  GOV:{len(gov_data)}  OSM:{len(osm_data)}")
    t0 = time.time()

    gov_grid = build_grid(gov_data)
    osm_grid = build_grid(osm_data)

    gov_matched: set = set()
    osm_matched: set = set()
    clusters:    list = []

    # ── Phase 1 : SR comme ancre ──────────────────
    sr_with_gov = sr_with_osm = 0
    for sr in sr_data:
        lat, lng = sr["lat"], sr["lng"]

        gov_near = [g for g in nearby(gov_grid, lat, lng) if within_100m(lat, lng, g["lat"], g["lng"])]
        best_gov = closest(gov_near, lat, lng)
        if best_gov:
            for g in gov_near:
                gov_matched.add(g["source_id"])
            sr_with_gov += 1

        osm_near = [o for o in nearby(osm_grid, lat, lng) if within_100m(lat, lng, o["lat"], o["lng"])]
        best_osm = closest(osm_near, lat, lng)
        if best_osm:
            for o in osm_near:
                osm_matched.add(o["source_id"])
            sr_with_osm += 1

        clusters.append(build_consolidated(sr=sr, gov=best_gov, osm=best_osm))

    log("MERGE", f"  Phase 1 (SR) : {len(sr_data)} clusters — {sr_with_gov} avec GOV, {sr_with_osm} avec OSM")

    # ── Phase 2 : GOV non appariés + OSM ─────────
    gov_unmatched = [g for g in gov_data if g["source_id"] not in gov_matched]
    gov_with_osm = 0
    for gov in gov_unmatched:
        lat, lng = gov["lat"], gov["lng"]

        osm_near = [
            o for o in nearby(osm_grid, lat, lng)
            if o["source_id"] not in osm_matched and within_100m(lat, lng, o["lat"], o["lng"])
        ]
        best_osm = closest(osm_near, lat, lng)
        if best_osm:
            for o in osm_near:
                osm_matched.add(o["source_id"])
            gov_with_osm += 1

        clusters.append(build_consolidated(gov=gov, osm=best_osm))

    log("MERGE", f"  Phase 2 (GOV non appariés) : {len(gov_unmatched)} clusters — {gov_with_osm} avec OSM")

    # ── Phase 3 : OSM solo ────────────────────────
    osm_unmatched = [o for o in osm_data if o["source_id"] not in osm_matched]
    for osm in osm_unmatched:
        clusters.append(build_consolidated(osm=osm))

    log("MERGE", f"  Phase 3 (OSM solo) : {len(osm_unmatched)} clusters")

    counts = {}
    for c in clusters:
        n = c["source_count"]
        counts[n] = counts.get(n, 0) + 1
    log("MERGE", (
        f"Consolidation OK en {time.time() - t0:.1f}s — "
        f"{len(clusters)} radars  "
        f"solo:{counts.get(1,0)}  duo:{counts.get(2,0)}  trio:{counts.get(3,0)}"
    ))
    return clusters


# ── Upsert Supabase ───────────────────────────────────────────

def upsert_radars(db: SupabaseRest, clusters: list[dict]):
    log("DB", f"Sync table 'radars' — {len(clusters)} radars à traiter")
    t0  = time.time()
    now = now_iso()

    existing = db.select_existing("radars", "canonical_id")
    log("DB", f"  {len(existing)} radars déjà en base")

    to_upsert = []
    new_c = changed_c = unchanged_c = 0
    for cluster in clusters:
        cid  = cluster["canonical_id"]
        prev = existing.get(cid)
        if prev is None:
            changed_at = now
            new_c += 1
        elif prev["hash"] != cluster["data_hash"]:
            changed_at = now
            changed_c += 1
        else:
            changed_at = prev["changed_at"]
            unchanged_c += 1
        to_upsert.append({**cluster, "fetched_at": now, "changed_at": changed_at})

    log("DB", f"  nouveaux:{new_c}  modifiés:{changed_c}  inchangés:{unchanged_c}")

    total_batches = (len(to_upsert) - 1) // BATCH_SIZE + 1
    for i in range(0, len(to_upsert), BATCH_SIZE):
        db.upsert("radars", to_upsert[i:i + BATCH_SIZE])
        batch_num = i // BATCH_SIZE + 1
        if batch_num % 20 == 0 or batch_num == total_batches:
            log("DB", f"  batch {batch_num}/{total_batches}")

    log("DB", f"  radars sync OK en {time.time() - t0:.1f}s")


# ── Main ──────────────────────────────────────────────────────

def main():
    t_start = time.time()
    log("MAIN", "Démarrage consolidation RadarAlert")
    db = SupabaseRest(SUPABASE_URL, SUPABASE_KEY)

    gov_cols = "source_id,lat,lng,type,speed_car,speed_hgv,department,city,route,direction,equipment,install_date,section_length_km"
    sr_cols  = "source_id,lat,lng,type,speed_car,speed_hgv,department,route,direction,equipment,install_date,section_length_km"
    osm_cols = "source_id,lat,lng,type,speed_limit,speed_limit_hgv,city,route,direction,country"

    t0 = time.time()
    gov_data = db.select_all("raw_gov", gov_cols)
    log("LOAD", f"GOV : {len(gov_data)} radars chargés en {time.time() - t0:.1f}s")

    t0 = time.time()
    sr_data = db.select_all("raw_sr", sr_cols)
    log("LOAD", f"SR  : {len(sr_data)} radars chargés en {time.time() - t0:.1f}s")

    t0 = time.time()
    osm_data = db.select_all("raw_osm", osm_cols)
    log("LOAD", f"OSM : {len(osm_data)} radars chargés en {time.time() - t0:.1f}s")

    clusters = consolidate(sr_data, gov_data, osm_data)
    upsert_radars(db, clusters)

    log("MAIN", f"Pipeline terminé en {time.time() - t_start:.1f}s")


if __name__ == "__main__":
    main()
