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
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from io import StringIO

import requests
from supabase import create_client

# ── Config ────────────────────────────────────────────────────

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]

GOV_CSV_URL   = "https://www.data.gouv.fr/api/1/datasets/r/8a22b5a8-4b65-41be-891a-7c0aead4ba51"
SR_BASE_URL   = "https://radars.securite-routiere.gouv.fr"
OVERPASS_URL  = "https://overpass-api.de/api/interpreter"
OVERPASS_QUERY = '[out:json][timeout:360];node["highway"="speed_camera"](35.0,-11.0,72.0,45.0);out body;'

SR_MAX_CONCURRENT = 5
SR_RETRIES        = 3
BATCH_SIZE        = 500


# ── Helpers ───────────────────────────────────────────────────

def make_hash(*values) -> str:
    content = "|".join("" if v is None else str(v) for v in values)
    return hashlib.md5(content.encode()).hexdigest()

def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


# ── Source GOV (data.gouv.fr) ─────────────────────────────────

def fetch_gov() -> list[dict]:
    print("Fetching data.gouv.fr CSV...")
    resp = requests.get(GOV_CSV_URL, timeout=60, allow_redirects=True)
    resp.raise_for_status()

    radars = []
    reader = csv.reader(StringIO(resp.text))
    next(reader)  # skip header

    for fields in reader:
        if len(fields) < 15:
            continue
        try:
            lat       = float(fields[3])
            lng       = float(fields[4])
            source_id = int(fields[5])
        except (ValueError, IndexError):
            continue

        # Validation coordonnées France métropolitaine + DOM-TOM
        if not (-21.4 <= lat <= 51.2 and -62.0 <= lng <= 55.9):
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

    print(f"  → {len(radars)} radars (gov)")
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

def fetch_sr_detail(session: requests.Session, raw_id: str) -> dict | None:
    url = f"{SR_BASE_URL}/radars/{raw_id}"
    for attempt in range(1, SR_RETRIES + 1):
        try:
            resp = session.get(url, timeout=40, headers={"Connection": "close"})
            if not resp.ok:
                return None
            return resp.json()
        except Exception:
            if attempt < SR_RETRIES:
                time.sleep(0.5 * attempt)
    return None

def fetch_sr() -> list[dict]:
    print("Fetching securite-routiere.gouv.fr...")
    session = requests.Session()
    session.headers.update({"Accept": "application/json"})

    resp = session.get(f"{SR_BASE_URL}/radars/all", timeout=30)
    resp.raise_for_status()
    basic_list = resp.json()
    print(f"  → {len(basic_list)} radars dans la liste de base")

    def process_one(basic: dict) -> dict | None:
        raw_id = basic.get("id", "")
        lat    = basic.get("lat")
        lng    = basic.get("lng")
        if not raw_id or lat is None or lng is None:
            return None

        radar_type = SR_TYPE_MAP.get(basic.get("typeLabel", ""), basic.get("typeLabel", ""))
        detail     = fetch_sr_detail(session, raw_id)

        speed_car = speed_hgv = None
        department = route = direction = equipment = install_date = section_km = ""

        if detail:
            for rule in detail.get("rulesmesured", []):
                mname = rule.get("macinename", "")
                if mname.startswith("vitesse_vl_"):
                    try: speed_car = int(mname[len("vitesse_vl_"):])
                    except ValueError: pass
                elif mname.startswith("vitesse_pl_"):
                    try: speed_hgv = int(mname[len("vitesse_pl_"):])
                    except ValueError: pass

            troncon    = detail.get("radartronconkm", "")
            section_km = troncon.replace(",", ".").strip() if isinstance(troncon, str) else ""
            department = detail.get("department", "")
            route      = detail.get("radarroad", "")
            direction  = detail.get("radardirection", "")
            equipment  = detail.get("radarequipment", "")
            install_date = detail.get("radarinstalldate", "")

        radar = {
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
        }
        radar["data_hash"] = make_hash(
            lat, lng, radar_type, speed_car, speed_hgv,
            department, route, direction, equipment, install_date, section_km
        )
        return radar

    radars = []
    with ThreadPoolExecutor(max_workers=SR_MAX_CONCURRENT) as executor:
        futures = {executor.submit(process_one, b): b for b in basic_list}
        done = 0
        for future in as_completed(futures):
            result = future.result()
            if result:
                radars.append(result)
            done += 1
            if done % 1000 == 0:
                print(f"  SR progress: {done}/{len(basic_list)}")

    print(f"  → {len(radars)} radars (sr)")
    return radars


# ── Source OSM (OpenStreetMap / Overpass) ─────────────────────

def fetch_osm() -> list[dict]:
    print("Fetching OpenStreetMap via Overpass...")
    resp = requests.post(OVERPASS_URL, data={"data": OVERPASS_QUERY}, timeout=420)
    resp.raise_for_status()

    radars = []
    for node in resp.json().get("elements", []):
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

        name  = tags.get("name", "")
        city  = name if name else tags.get("addr:city", "")
        route = tags.get("ref", "")
        direction = tags.get("direction", "")

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
        }
        radar["data_hash"] = make_hash(lat, lng, radar_type, speed, speed_hgv, city, route, direction)
        radars.append(radar)

    print(f"  → {len(radars)} radars (osm)")
    return radars


# ── Upsert Supabase ───────────────────────────────────────────

def upsert_to_supabase(supabase, table: str, new_data: list[dict], id_field: str):
    """
    Upsert tous les radars.
    - fetched_at : toujours mis à jour
    - changed_at : mis à jour seulement si le hash a changé
    """
    print(f"\nSync {table} ({len(new_data)} radars)...")
    now = now_iso()

    # 1. Récupérer les hashes et changed_at existants (pagination)
    existing: dict[str, dict] = {}
    offset = 0
    while True:
        result = (
            supabase.table(table)
            .select(f"{id_field},data_hash,changed_at")
            .range(offset, offset + 999)
            .execute()
        )
        for row in result.data:
            existing[row[id_field]] = {"hash": row["data_hash"], "changed_at": row["changed_at"]}
        if len(result.data) < 1000:
            break
        offset += 1000

    print(f"  {len(existing)} enregistrements existants en base")

    # 2. Construire le payload : conserver changed_at si le contenu n'a pas changé
    rows = []
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
        rows.append({**row, "fetched_at": now, "changed_at": changed_at})

    print(f"  nouveaux: {new_count} | modifiés: {changed_count} | inchangés: {unchanged_count}")

    # 3. Upsert par batches
    for i in range(0, len(rows), BATCH_SIZE):
        supabase.table(table).upsert(rows[i:i + BATCH_SIZE]).execute()
        print(f"  batch {i // BATCH_SIZE + 1}/{(len(rows) - 1) // BATCH_SIZE + 1} ✓")

    print(f"  → {table} sync terminé")


# ── Main ──────────────────────────────────────────────────────

def main():
    supabase = create_client(SUPABASE_URL, SUPABASE_KEY)

    gov_data = fetch_gov()
    sr_data  = fetch_sr()
    osm_data = fetch_osm()

    upsert_to_supabase(supabase, "raw_gov", gov_data, "source_id")
    upsert_to_supabase(supabase, "raw_sr",  sr_data,  "source_id")
    upsert_to_supabase(supabase, "raw_osm", osm_data, "source_id")

    print("\nTout est synchronisé ✓")


if __name__ == "__main__":
    main()
