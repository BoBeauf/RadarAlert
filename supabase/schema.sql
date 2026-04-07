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
