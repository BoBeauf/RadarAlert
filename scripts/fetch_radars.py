#!/usr/bin/env python3
"""
Fetch radar data from 3 sources and upsert into Supabase raw tables.
Sources: data.gouv.fr (gov), securite-routiere.gouv.fr (sr), OpenStreetMap (osm)

Logique :
  - Upsert de tous les radars à chaque run
  - changed_at n'est mis à jour que si le contenu a réellement changé
  - fetched_at est toujours mis à jour (= "vu pour la dernière fois")
"""

import csv
import hashlib
import os
import random
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from io import StringIO

import requests

# ── Config ────────────────────────────────────────────────────

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]

GOV_CSV_URL    = "https://www.data.gouv.fr/api/1/datasets/r/8a22b5a8-4b65-41be-891a-7c0aead4ba51"
SR_BASE_URL    = "https://radars.securite-routiere.gouv.fr"
OVERPASS_URL   = "https://overpass-api.de/api/interpreter"
OVERPASS_QUERY = '[out:json][timeout:360];node["highway"="speed_camera"](35.0,-11.0,72.0,45.0);out body;'

SR_RETRIES        = 3
SR_DELAY_BASE_S   = 1.0    # délai de base entre chaque requête detail SR
SR_DELAY_JITTER_S = 0.5    # ± jitter aléatoire (évite les patterns détectables)
BATCH_SIZE        = 500


# ── Helpers ───────────────────────────────────────────────────

def make_hash(*values) -> str:
    content = "|".join("" if v is None else str(v) for v in values)
    return hashlib.md5(content.encode()).hexdigest()

def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()

def log(source: str, msg: str):
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
    print(f"[{ts}] [{source}] {msg}", flush=True)


# ── Source GOV (data.gouv.fr) ─────────────────────────────────

def fetch_gov() -> list[dict]:
    log("GOV", "Téléchargement du CSV data.gouv.fr...")
    t0 = time.time()
    resp = requests.get(GOV_CSV_URL, timeout=60, allow_redirects=True)
    resp.raise_for_status()
    log("GOV", f"CSV reçu ({len(resp.content) // 1024} KB) — parsing...")

    radars = []
    skipped = 0
    reader = csv.reader(StringIO(resp.text))
    next(reader)  # skip header

    for fields in reader:
        if len(fields) < 15:
            skipped += 1
            continue
        try:
            lat       = float(fields[3])
            lng       = float(fields[4])
            source_id = int(fields[5])
        except (ValueError, IndexError):
            skipped += 1
            continue

        # Validation coordonnées France métropolitaine + DOM-TOM
        if not (-21.4 <= lat <= 51.2 and -62.0 <= lng <= 55.9):
            skipped += 1
            continue

        speed_car = int(fields[14].strip()) if fields[14].strip().isdigit() else None
        speed_hgv = int(fields[13].strip()) if fields[13].strip().isdigit() else None

        radar = {
            "source_id":         source_id,
            "lat":               lat,
            "lng":               lng,
            "type":              fields[9].strip(),
            "speed_car":         speed_car,
            "speed_hgv":         speed_hgv,
            "department":        fields[2].strip(),
            "city":              fields[10].strip(),
            "route":             fields[11].strip(),
            "direction":         fields[6].strip(),
            "equipment":         fields[7].strip(),
            "install_date":      fields[8].strip(),
            "section_length_km": fields[12].strip(),
        }
        radar["data_hash"] = make_hash(
            lat, lng, radar["type"], speed_car, speed_hgv,
            radar["department"], radar["city"], radar["route"],
            radar["direction"], radar["equipment"],
            radar["install_date"], radar["section_length_km"]
        )
        radars.append(radar)

    log("GOV", f"Terminé en {time.time() - t0:.1f}s — {len(radars)} radars ({skipped} ignorés)")
    return radars


# ── Source SR (securite-routiere.gouv.fr) ────────────────────

SR_TYPE_MAP = {
    "Fixes":               "Radar fixe",
    "Feux rouges":         "Radar feu rouge",
    "Vitesse Moyenne":     "Radar tronçon",
    "Discriminants":       "Radar discriminant",
    "Itinéraires":         "Radar itinéraire",
    "Passages à niveau":   "Radar passage à niveau",
    "Urbain":              "Radar urbain",
}

def stable_sr_id(raw_id: str) -> int:
    try:
        return int(raw_id) + 100_000_000_000
    except ValueError:
        digits = "".join(c for c in raw_id if c.isdigit())
        if digits:
            return int(digits) + 200_000_000_000
        return abs(hash(raw_id)) + 100_000_000_000

def fetch_sr_detail(session: requests.Session, raw_id: str):
    url = f"{SR_BASE_URL}/radars/{raw_id}"
    for attempt in range(1, SR_RETRIES + 1):
        try:
            resp = session.get(url, timeout=30)
            if resp.status_code == 429 or resp.status_code >= 500:
                wait = min(2 ** attempt, 30)
                log("SR", f"  {raw_id} → HTTP {resp.status_code}, attente {wait}s (tentative {attempt}/{SR_RETRIES})")
                time.sleep(wait)
                continue
            if not resp.ok:
                return None
            return resp.json()
        except Exception as e:
            if attempt < SR_RETRIES:
                time.sleep(2 * attempt)
            else:
                log("SR", f"  {raw_id} → abandon après {SR_RETRIES} tentatives : {e}")
    return None

def fetch_sr() -> list[dict]:
    log("SR", "Connexion à securite-routiere.gouv.fr...")
    t0 = time.time()
    session = requests.Session()
    session.headers.update({
        "Accept":     "application/json",
        "User-Agent": "Mozilla/5.0 (compatible; RadarAlert/1.0)",
    })

    # /radars/all : retry car le serveur est fragile
    basic_list = None
    for attempt in range(1, SR_RETRIES + 1):
        try:
            resp = session.get(f"{SR_BASE_URL}/radars/all", timeout=30)
            resp.raise_for_status()
            basic_list = resp.json()
            break
        except Exception as e:
            log("SR", f"  /radars/all tentative {attempt}/{SR_RETRIES} échouée : {e}")
            if attempt < SR_RETRIES:
                time.sleep(2 * attempt)
    if basic_list is None:
        raise RuntimeError("Impossible de récupérer /radars/all après plusieurs tentatives")

    # Les Itinéraires (I_xx_xxx) sont des zones, pas des radars ponctuels — on les exclut
    basic_list = [r for r in basic_list if r.get("typeLabel") != "Itinéraires"]

    # Ordre aléatoire : évite les patterns détectables côté serveur
    random.shuffle(basic_list)

    total = len(basic_list)
    log("SR", f"{total} radars (Itinéraires exclus, ordre aléatoire) — fetch séquentiel avec jitter {SR_DELAY_BASE_S}s ± {SR_DELAY_JITTER_S}s...")

    radars = []
    failed = with_speed = 0
    t_sr = time.time()

    for i, basic in enumerate(basic_list):
        raw_id = basic.get("id", "")
        lat    = basic.get("lat")
        lng    = basic.get("lng")
        if not raw_id or lat is None or lng is None:
            continue

        radar_type = SR_TYPE_MAP.get(basic.get("typeLabel", ""), basic.get("typeLabel", ""))
        detail     = fetch_sr_detail(session, raw_id)

        speed_car = speed_hgv = None
        department = route = direction = equipment = install_date = section_km = ""

        if detail:
            for rule in (detail.get("rulesmesured") or []):
                mname = rule.get("macinename", "")
                if mname.startswith("vitesse_vl_"):
                    try: speed_car = int(mname[len("vitesse_vl_"):])
                    except ValueError: pass
                elif mname.startswith("vitesse_pl_"):
                    try: speed_hgv = int(mname[len("vitesse_pl_"):])
                    except ValueError: pass
            troncon      = detail.get("radartronconkm", "")
            section_km   = troncon.replace(",", ".").strip() if isinstance(troncon, str) else ""
            department   = detail.get("department", "")
            route        = detail.get("radarroad", "")
            direction    = detail.get("radardirection", "")
            equipment    = detail.get("radarequipment", "")
            install_date = detail.get("radarinstalldate", "")
        else:
            failed += 1

        if speed_car is not None:
            with_speed += 1

        radars.append({
            "source_id":         raw_id,
            "stable_id":         stable_sr_id(raw_id),
            "lat":               lat,
            "lng":               lng,
            "type":              radar_type,
            "speed_car":         speed_car,
            "speed_hgv":         speed_hgv,
            "department":        department,
            "route":             route,
            "direction":         direction,
            "equipment":         equipment,
            "install_date":      install_date,
            "section_length_km": section_km,
            "data_hash": make_hash(
                lat, lng, radar_type, speed_car, speed_hgv,
                department, route, direction, equipment, install_date, section_km
            ),
        })

        done = i + 1
        if done % 100 == 0:
            elapsed = time.time() - t_sr
            eta = (total - done) * elapsed / done
            pct = done * 100 // total
            log("SR", f"  {done}/{total} ({pct}%) — detail_ko={failed} avec_vitesse={with_speed} — ETA {eta:.0f}s")

        # Délai avec jitter pour rester sous le radar du rate-limiter
        time.sleep(max(0.2, SR_DELAY_BASE_S + random.uniform(-SR_DELAY_JITTER_S, SR_DELAY_JITTER_S)))

    log("SR", f"Terminé en {time.time() - t0:.1f}s — {len(radars)} radars — detail_ko={failed} avec_vitesse={with_speed}")
    return radars


# ── Polygone France métropolitaine + Corse ───────────────────
# ~55 points, précision suffisante pour taguer les radars OSM.
# Sources des coordonnées : frontières officielles simplifiées.

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

def _pip(lat: float, lng: float, poly: list) -> bool:
    """Ray casting point-in-polygon."""
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

def in_france_metro(lat: float, lng: float) -> bool:
    """Retourne True si le point est en France métropolitaine (Corse incluse)."""
    if _pip(lat, lng, _FRANCE_METRO):
        return True
    # Corse : bbox simple suffisante (rien d'autre dans ce rectangle)
    return 41.33 <= lat <= 43.03 and 8.54 <= lng <= 9.57


# ── Source OSM (OpenStreetMap / Overpass) ─────────────────────

def fetch_osm() -> list[dict]:
    log("OSM", "Requête Overpass (Europe entière, peut prendre 2-3 min)...")
    t0 = time.time()
    resp = requests.post(OVERPASS_URL, data={"data": OVERPASS_QUERY}, timeout=420)
    resp.raise_for_status()

    elements = resp.json().get("elements", [])
    log("OSM", f"Réponse reçue ({len(resp.content) // 1024} KB) — {len(elements)} nœuds — parsing...")

    radars = []
    for node in elements:
        lat    = node.get("lat")
        lng    = node.get("lon")
        osm_id = node.get("id")
        if lat is None or lng is None or osm_id is None:
            continue

        tags        = node.get("tags", {})
        enforcement = tags.get("enforcement", "")

        if "traffic_signals" in enforcement or "red_light" in enforcement:
            radar_type = "Radar feu rouge"
        elif "average_speed" in enforcement or "average_speed" in tags:
            radar_type = "Radar tronçon"
        else:
            radar_type = "Radar fixe"

        speed_str = tags.get("maxspeed", "")
        speed     = int(speed_str) if speed_str.isdigit() else None
        hgv_str   = tags.get("maxspeed:hgv", "")
        speed_hgv = int(hgv_str) if hgv_str.isdigit() else None

        name      = tags.get("name", "")
        city      = name if name else tags.get("addr:city", "")
        route     = tags.get("ref", "")
        direction = tags.get("direction", "")

        country = "FR" if in_france_metro(lat, lng) else None

        radar = {
            "source_id":       osm_id,
            "lat":             lat,
            "lng":             lng,
            "type":            radar_type,
            "speed_limit":     speed,
            "speed_limit_hgv": speed_hgv,
            "city":            city,
            "route":           route,
            "direction":       direction,
            "country":         country,
        }
        radar["data_hash"] = make_hash(lat, lng, radar_type, speed, speed_hgv, city, route, direction, country)
        radars.append(radar)

    log("OSM", f"Terminé en {time.time() - t0:.1f}s — {len(radars)} radars")
    return radars


# ── Client Supabase REST (sans SDK) ──────────────────────────

class SupabaseRest:
    """
    Client REST minimal pour Supabase — compatible avec tous les formats de clé
    (JWT service_role eyJ... ou Secret key sbsec_...).
    """
    def __init__(self, url: str, key: str):
        self.base = f"{url}/rest/v1"
        self.session = requests.Session()
        self.session.headers.update({
            "apikey":        key,
            "Authorization": f"Bearer {key}",
            "Content-Type":  "application/json",
        })

    def select(self, table: str, columns: str, limit: int = 1000, offset: int = 0) -> list[dict]:
        resp = self.session.get(
            f"{self.base}/{table}",
            params={"select": columns, "limit": limit, "offset": offset},
            timeout=30,
        )
        resp.raise_for_status()
        return resp.json()

    def upsert(self, table: str, rows: list[dict]) -> None:
        resp = self.session.post(
            f"{self.base}/{table}",
            json=rows,
            headers={"Prefer": "resolution=merge-duplicates,return=minimal"},
            timeout=60,
        )
        resp.raise_for_status()


# ── Upsert Supabase ───────────────────────────────────────────

def upsert_to_supabase(db: SupabaseRest, table: str, new_data: list[dict], id_field: str):
    """
    Upsert tous les radars.
    - fetched_at : toujours mis à jour
    - changed_at : mis à jour seulement si le hash a changé
    """
    log("DB", f"Sync {table} — {len(new_data)} radars à traiter")
    t0  = time.time()
    now = now_iso()

    # 1. Récupérer les hashes et changed_at existants (pagination)
    existing: dict[str, dict] = {}
    offset = 0
    while True:
        rows = db.select(table, f"{id_field},data_hash,changed_at", limit=1000, offset=offset)
        for row in rows:
            existing[row[id_field]] = {"hash": row["data_hash"], "changed_at": row["changed_at"]}
        if len(rows) < 1000:
            break
        offset += 1000

    log("DB", f"  {len(existing)} enregistrements existants en base")

    # 2. Construire le payload : conserver changed_at si le contenu n'a pas changé
    to_upsert = []
    new_count = changed_count = unchanged_count = 0
    for row in new_data:
        sid  = row[id_field]
        prev = existing.get(sid)
        if prev is None:
            changed_at = now
            new_count += 1
        elif prev["hash"] != row["data_hash"]:
            changed_at = now
            changed_count += 1
        else:
            changed_at = prev["changed_at"]
            unchanged_count += 1
        to_upsert.append({**row, "fetched_at": now, "changed_at": changed_at})

    log("DB", f"  nouveaux: {new_count} | modifiés: {changed_count} | inchangés: {unchanged_count}")

    # 3. Upsert par batches
    total_batches = (len(to_upsert) - 1) // BATCH_SIZE + 1
    for i in range(0, len(to_upsert), BATCH_SIZE):
        db.upsert(table, to_upsert[i:i + BATCH_SIZE])
        batch_num = i // BATCH_SIZE + 1
        if batch_num % 10 == 0 or batch_num == total_batches:
            log("DB", f"  {table} : batch {batch_num}/{total_batches}")

    log("DB", f"  {table} sync OK en {time.time() - t0:.1f}s")


# ── Main ──────────────────────────────────────────────────────

def main():
    t_start = time.time()
    log("MAIN", "Démarrage du pipeline RadarAlert")
    db = SupabaseRest(SUPABASE_URL, SUPABASE_KEY)

    # Fetch des 3 sources en parallèle (SR et OSM sont les plus lents)
    log("MAIN", "Fetch des 3 sources en parallèle...")
    with ThreadPoolExecutor(max_workers=3) as executor:
        future_gov = executor.submit(fetch_gov)
        future_sr  = executor.submit(fetch_sr)
        future_osm = executor.submit(fetch_osm)
        gov_data = future_gov.result()
        sr_data  = future_sr.result()
        osm_data = future_osm.result()

    total = len(gov_data) + len(sr_data) + len(osm_data)
    log("MAIN", f"Fetch terminé — {total} radars au total (gov:{len(gov_data)} sr:{len(sr_data)} osm:{len(osm_data)})")

    upsert_to_supabase(db, "raw_gov", gov_data, "source_id")
    upsert_to_supabase(db, "raw_sr",  sr_data,  "source_id")
    upsert_to_supabase(db, "raw_osm", osm_data, "source_id")

    log("MAIN", f"Pipeline terminé en {time.time() - t_start:.1f}s")


if __name__ == "__main__":
    main()
