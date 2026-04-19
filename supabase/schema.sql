-- ============================================================
-- RadarAlert – schéma des tables raw (données brutes par source)
-- À exécuter dans l'éditeur SQL de Supabase (une seule fois)
-- ============================================================

-- ── raw_gov : données brutes data.gouv.fr ────────────────────
CREATE TABLE IF NOT EXISTS raw_gov (
    source_id         BIGINT PRIMARY KEY,
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    type              TEXT,
    speed_car         INT,
    speed_hgv         INT,
    department        TEXT,
    city              TEXT,
    route             TEXT,
    direction         TEXT,
    equipment         TEXT,
    install_date      TEXT,
    section_length_km TEXT,
    data_hash         TEXT NOT NULL,
    fetched_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── raw_sr : données brutes securite-routiere.gouv.fr ────────
CREATE TABLE IF NOT EXISTS raw_sr (
    source_id         TEXT PRIMARY KEY,   -- peut être alphanumérique ex: "FE193002"
    stable_id         BIGINT,             -- ID numérique stable pour cross-référence
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    type              TEXT,
    speed_car         INT,
    speed_hgv         INT,
    department        TEXT,
    route             TEXT,
    direction         TEXT,
    equipment         TEXT,
    install_date      TEXT,
    section_length_km TEXT,
    data_hash         TEXT NOT NULL,
    fetched_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── raw_osm : données brutes OpenStreetMap ───────────────────
CREATE TABLE IF NOT EXISTS raw_osm (
    source_id       BIGINT PRIMARY KEY,   -- OSM node ID
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    type            TEXT,
    speed_limit     INT,
    speed_limit_hgv INT,
    city            TEXT,
    route           TEXT,
    direction       TEXT,
    data_hash       TEXT NOT NULL,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── radars : table consolidée (fusion des 3 sources) ────────
-- Règles de priorité par champ :
--   lat/lng, type, speed_*  : SR > GOV > OSM
--   department              : GOV > SR
--   city                    : GOV > OSM  (SR n/a)
--   route, direction        : GOV > SR > OSM
--   equipment, install_date,
--   section_length_km       : SR > GOV   (OSM n/a)
CREATE TABLE IF NOT EXISTS radars (
    canonical_id      TEXT PRIMARY KEY,   -- 'sr:FE193002' | 'gov:123456' | 'osm:789012'
    sources           TEXT[] NOT NULL,    -- sous-ensemble de ['gov','sr','osm']
    gov_id            BIGINT,
    sr_id             TEXT,
    osm_id            BIGINT,
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    type              TEXT,
    speed_car         INT,
    speed_hgv         INT,
    department        TEXT,
    city              TEXT,
    route             TEXT,
    direction         TEXT,
    equipment         TEXT,
    install_date      TEXT,
    section_length_km TEXT,
    source_count      SMALLINT NOT NULL DEFAULT 1,
    data_hash         TEXT NOT NULL,
    fetched_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS radars_lat_lng  ON radars (lat, lng);
CREATE INDEX IF NOT EXISTS radars_gov_id   ON radars (gov_id);
CREATE INDEX IF NOT EXISTS radars_sr_id    ON radars (sr_id);

-- ── Colonne country (ajout après-coup si tables déjà créées) ─
ALTER TABLE raw_osm ADD COLUMN IF NOT EXISTS country TEXT;
ALTER TABLE radars  ADD COLUMN IF NOT EXISTS country TEXT;
CREATE INDEX IF NOT EXISTS raw_osm_country ON raw_osm (country);
CREATE INDEX IF NOT EXISTS radars_country  ON radars  (country);
