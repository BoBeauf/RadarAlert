# RadarAlert — CLAUDE.md

## Présentation du projet

RadarAlert est une application Android d'avertissement de radars routiers. Elle affiche les radars sur une carte et alerte l'utilisateur (son, voix, overlay) à l'approche d'un radar.

L'application ne charge pas les données directement depuis les sources publiques : une **pipeline distante** (GitHub Actions + Supabase) agrège les données de plusieurs sources et les met à disposition. L'app Android télécharge ensuite ces données consolidées.

---

## Architecture globale

```
Sources publiques                 Backend (GitHub Actions)        App Android
─────────────────                 ────────────────────────        ─────────────
data.gouv.fr         ──┐
securite-routiere.fr ──┤── fetch_radars.py ──▶ Supabase (PostgreSQL) ──▶ Room (SQLite)
OpenStreetMap        ──┘          (cron dimanche 01h UTC)
```

### Composants principaux

| Composant | Technologie | Rôle |
|-----------|-------------|------|
| Pipeline de données | Python 3.12 + GitHub Actions | Fetch, déduplication, upsert Supabase |
| Base distante | Supabase (PostgreSQL) | Stockage centralisé des radars |
| App Android | Kotlin + Jetpack Compose | UI, carte, alertes, location |
| Base locale | Room (SQLite) | Cache des radars sur l'appareil |

---

## Stack technique

### Android (`app/`)
- **Langage :** Kotlin
- **UI :** Jetpack Compose + Material3
- **Carte :** MapLibre 11.8.1 (tuiles vectorielles)
- **Base de données locale :** Room 2.6.1
- **Localisation :** FusedLocationProviderClient (Google Play Services)
- **Réseau :** OkHttp 4.12.0
- **Async :** Kotlin Coroutines 1.8.1
- **Paramètres :** DataStore (Preferences)
- **Build :** Gradle 8+ (Kotlin DSL), JVM 17, minSdk 26, targetSdk 35

### Pipeline de données (`scripts/`)
- **Langage :** Python 3.12
- **HTTP :** `requests` 2.32.3
- **BDD :** Supabase via son API REST

---

## Structure des dossiers

```
RadarAlert/
├── app/src/main/java/com/radaralert/
│   ├── MainActivity.kt            # Point d'entrée Android
│   ├── RadarAlertApp.kt           # Application class
│   ├── alert/                     # Alertes (son, voix, overlay)
│   │   ├── AlertManager.kt
│   │   ├── OverlayManager.kt
│   │   ├── RadarIconFactory.kt
│   │   ├── RadarOverlayManager.kt
│   │   ├── SoundAlert.kt
│   │   ├── StatusOverlayManager.kt
│   │   └── VoiceAlert.kt
│   ├── data/                      # Couche données
│   │   ├── CsvParser.kt           # Parse data.gouv.fr
│   │   ├── SrParser.kt            # Parse securite-routiere.fr
│   │   ├── OsmParser.kt           # Parse OpenStreetMap
│   │   ├── DownloadManager.kt     # Téléchargement & cache
│   │   ├── RadarDao.kt            # Requêtes Room
│   │   ├── RadarDatabase.kt       # Singleton Room
│   │   ├── RadarEntity.kt         # Entité radar
│   │   └── SettingsRepository.kt  # Persistance paramètres
│   ├── location/
│   │   ├── LocationService.kt     # Service foreground GPS
│   │   └── ProximityChecker.kt    # Détection radars proches
│   └── ui/
│       ├── navigation/NavHost.kt
│       ├── screens/
│       │   ├── MapScreen.kt       # Écran carte principal
│       │   ├── PermissionScreen.kt
│       │   └── SettingsScreen.kt
│       └── theme/Theme.kt
├── scripts/
│   ├── fetch_radars.py            # Pipeline fetch & upsert
│   └── requirements.txt
├── dashboard/
│   └── index.html                 # Dashboard d'audit (single-file, Leaflet + Supabase REST)
├── supabase/
│   └── schema.sql                 # Schéma des 3 tables raw_*
└── .github/workflows/
    └── fetch-radars.yml           # Cron GitHub Actions (dimanche 01h UTC)
```

---

## Pipeline de données

### Sources agrégées
| Table Supabase | Source | Format |
|----------------|--------|--------|
| `raw_gov` | data.gouv.fr | CSV (130k+ radars, limites de vitesse, département/commune/route) |
| `raw_sr` | securite-routiere.gouv.fr | JSON (IDs stables comme "FE193002", cross-ref possible) |
| `raw_osm` | OpenStreetMap (Overpass API) | JSON (node IDs, tags OSM) |

### Comportement de `fetch_radars.py`
- **Détection de changement :** hash du contenu → `changed_at` mis à jour seulement si la donnée change
- **Déduplication :** upsert par batch de 500 enregistrements
- **Concurrence :** GOV et OSM en parallèle (ThreadPoolExecutor) ; SR en séquentiel (rate-limit IP)
- **Retry :** 3 tentatives par URL
- **Validation géographique :** coordonnées France métropole + DOM-TOM

### Spécificités SR (securite-routiere.fr)
- Fetch séquentiel avec shuffle aléatoire de la liste + jitter 1 s ± 0,5 s entre requêtes
- Les radars de type **Itinéraires** (IDs `I_xx_xxx`) sont exclus — ce sont des zones de tronçon, pas des points
- La vitesse est extraite du champ `rulesmesured[].macinename` (pattern `vitesse_vl_70`)
- Le serveur ferme la connexion après ~300 requêtes → une session TCP suffit sur un run complet

### CI/CD
- Cron GitHub Actions : chaque **dimanche à 01h00 UTC**
- Timeout du job : 90 minutes
- Secrets requis : `SUPABASE_SERVICE_ROLE_KEY`
- URL Supabase : définie dans le workflow (variable `SUPABASE_URL`)

### Dashboard d'audit (`dashboard/index.html`)
- Fichier unique (Leaflet + MarkerCluster, dark CartoDB tiles) — aucune dépendance locale
- Connecté à Supabase via l'API REST (pas de SDK) avec clé anon
- Carte : clustering par source (GOV=bleu, SR=orange, OSM=vert), chargement bbox au pan/zoom (limite 3 000/source)
- Sidebar : comptages, couverture des champs (% non-null), types, fraîcheur
- Lancer localement : `python3 -m http.server 8765 --directory dashboard` (ou via `.claude/launch.json`)
- Nécessite la clé Supabase anon dans `const SUPABASE_KEY = '...'` (ne pas committer)

---

## App Android — points clés

### Permissions requises
- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` : GPS
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` : service en arrière-plan
- `POST_NOTIFICATIONS` : alertes Android 13+
- `SYSTEM_ALERT_WINDOW` : overlay par-dessus les autres apps
- `INTERNET` : téléchargement des données
- `VIBRATE` : retour haptique

### Flux de données Android
1. `DownloadManager` télécharge les données depuis Supabase
2. Les parsers (`CsvParser`, `SrParser`, `OsmParser`) transforment les données brutes
3. `RadarDao` insère dans Room (SQLite local)
4. `LocationService` (foreground) track la position GPS
5. `ProximityChecker` interroge Room pour les radars proches
6. `AlertManager` déclenche son / voix / overlay selon la proximité

### Carte
- MapLibre pour les tuiles vectorielles (fond de carte)
- osmdroid pour les overlays flexibles (icônes radars)
- Dual approach : performances MapLibre + flexibilité osmdroid

---

## Commandes utiles

### Build Android
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Build + installe sur device connecté
./gradlew test                   # Tests unitaires
./gradlew connectedAndroidTest   # Tests instrumentés (device requis)
```

### Pipeline Python
```bash
cd scripts
pip install -r requirements.txt
SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... python fetch_radars.py
```

---

## Variables d'environnement

| Variable | Usage | Où |
|----------|-------|----|
| `SUPABASE_URL` | URL de l'instance Supabase | GitHub Actions workflow |
| `SUPABASE_SERVICE_ROLE_KEY` | Clé admin Supabase (secret) | GitHub Actions secret |

La clé Supabase côté Android (clé anon publique) est probablement dans `local.properties` ou dans le code — ne pas committer de secrets.

---

## Conventions du projet

- **Langue du code :** Kotlin (Android) / Python (pipeline)
- **Langue des commentaires :** français
- **Architecture Android :** pas de ViewModel explicite pour l'instant — Compose + coroutines directement
- **Schéma BDD :** les 3 tables `raw_*` stockent les données brutes telles qu'elles viennent des sources ; la consolidation/fusion est à faire côté app ou via une vue SQL

---

## Points d'attention

- L'API securite-routiere.fr est protégée par un rate-limit IP → fetch séquentiel + shuffle + jitter 1 s ± 0,5 s (≈66 min pour 3206 radars) ; timeout GHA à 90 min
- Le service foreground doit rester actif même en arrière-plan (batterie / Doze mode Android)
- L'overlay `SYSTEM_ALERT_WINDOW` nécessite une permission explicite de l'utilisateur (Settings > Apps)
- Les données OSM et gouvernementales peuvent se chevaucher → gestion de la déduplication importante
- `local.properties` et les keystores ne sont pas commités (voir `.gitignore`)
