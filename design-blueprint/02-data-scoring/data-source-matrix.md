# Data Source Matrix — Property/Home Finding App

**Purpose:** Developer handoff reference for data ingestion. Initial market: Nigeria. Designed to scale globally.

**Coverage rating key:** 🟢 Strong · 🟡 Partial / urban-only / dated · 🔴 Weak or unavailable

**Architecture note:** Do not call these APIs live per user request. Ingest → preprocess → cache into a geospatial DB (PostGIS), keyed to your geography, refreshed on schedule. The app reads from your DB; external sources run as a background pipeline.

---

## Tier 1 — Environment & Geography (free, global, reliable)

| Data Layer | Source | Access Method | Auth | Cost | Refresh | NG Coverage | Notes |
|---|---|---|---|---|---|---|---|
| Weather & climate (rainfall, temp, humidity, solar) | NASA POWER | REST API (JSON/CSV), OPeNDAP | None for API | Free | Daily | 🟢 | Global, gridded. Workhorse for climate trends & rainy-season profiling. |
| Flood — near real-time | NASA LANCE (MODIS MCDWD / VIIRS VCDWD) | GeoTIFF / HDF download, GIBS tile layers | Earthdata Login (download) | Free | Daily, ~250 m | 🟢 | Global. Good for live flood extent. |
| Flood — historical frequency | NASA LANCE reprocessed archive (2003–2025) via LAADS DAAC | Bulk download | Earthdata Login | Free | Static archive + ongoing | 🟢 | Essential for "how often does this spot flood" scoring. |
| Flood — hazard frequency grid | CHRR / CIESIN Global Flood Hazard Frequency & Distribution | Dataset download (raster grid) | None | Free | Static (legacy) | 🟡 | Older (1985–2003 basis) but useful as a baseline risk index. |
| Flood — radar-based extent | Copernicus Global Flood Monitoring (Sentinel-1 SAR) | API / WMS | Copernicus account | Free | Per-pass | 🟢 | Complements optical (MODIS/VIIRS) in cloudy/rainy conditions. |
| Hazard vulnerability & socioeconomic | NASA SEDAC | Dataset download, Hazards Mapper, some APIs | Earthdata Login (some) | Free | Periodic | 🟡 | Population, vulnerability layers for quality-of-life dimension. |
| Elevation / drainage / slope | SRTM / Copernicus DEM | Tile download | None / account | Free | Static | 🟢 | Derive flood-proneness from elevation where flood maps are thin. |
| Air quality | OpenAQ; Sentinel-5P (Copernicus) | REST API; tile/API | API key (OpenAQ free) | Free | Hourly–daily | 🟡 | Ground-station coverage sparse in NG; satellite fills gaps. |
| Streamflow / river flood forecast | NOAA NWPS | REST API | None | Free | Real-time | 🔴 | **US only.** Listed so it's not mistaken for global. |

---

## Tier 1 — Amenities & Proximity (free, global, reliable)

| Data Layer | Source | Access Method | Auth | Cost | Refresh | NG Coverage | Notes |
|---|---|---|---|---|---|---|---|
| POIs — schools, hospitals, markets, worship (mosque/church/temple), pharmacies, transit, parks | OpenStreetMap via Overpass API | Live tag queries (`amenity=*`, `place_of_worship` + `religion=*`) | None | Free (rate-limited) | Continuous | 🟡 | Strong in Lagos/cities, sparse rural. Show data-density/confidence in UI. |
| POIs — bulk ingest | Geofabrik `nigeria-latest.osm.pbf` (~680 MB) | File download → PostGIS (osm2pgsql) | None | Free | ~Daily | 🟡 | Preferred for ingestion at scale vs. live Overpass. |
| POIs — cleaned humanitarian export | HOTOSM Nigeria POI (Humanitarian Data Exchange) | Shapefile / vector download | None | Free | Periodic | 🟡 | Pre-filtered, handy starting dataset. |
| Geocoding & reverse geocoding | Nominatim (OSM) | REST API | None | Free (strict rate limit) | Continuous | 🟡 | Fine for low volume. |
| Geocoding — at scale | OpenCage / Google / Mapbox | REST API | API key | Paid (usage-based) | Continuous | 🟢 | Budget for this once volume grows. |
| Travel time / routing (walk/drive/transit) | OpenRouteService / OSRM; Google Distance Matrix | REST API | API key | Free tier → paid | Continuous | 🟡 | Powers "15-minute life" proximity scoring. |

---

## Tier 2 — Housing Cost (fragmented; the hard part)

| Data Layer | Source | Access Method | Auth | Cost | Refresh | NG Coverage | Notes |
|---|---|---|---|---|---|---|---|
| Per-listing prices | PropertyPro.ng, Nigeria Property Centre, Private Property | Scraping or data partnership | N/A | Eng. cost / negotiated | Listing-driven | 🟡 | No public API. Legal/ToS review needed before scraping. |
| Market intelligence | Estate Intel | B2B platform / licensed feed | Account | Paid | Periodic | 🟢 | Curated African real-estate data; cleanest paid option. |
| Area-level aggregates | NBS + CAHF Nigeria Housing Data Dashboard | Dashboard / report download | None | Free | Infrequent | 🟡 | Based on 2018 Living Standards Survey — coarse & dated. |
| Market sizing / forecasts | Statista Real Estate (Nigeria) | Report | Subscription | Paid | Annual | 🟡 | Macro only, not property-level. |

> **MVP guidance:** show neighborhood **price bands** (achievable) rather than per-property valuations (not achievable without listings data).

---

## Tier 2 — Safety / Crime (city/state-level only)

| Data Layer | Source | Access Method | Auth | Cost | Refresh | NG Coverage | Notes |
|---|---|---|---|---|---|---|---|
| Perceived safety index | Numbeo | API / scrape | API key | Free tier → paid | Periodic | 🟡 | Crowdsourced perception, city-level. |
| Conflict & violence events | ACLED | REST API | Account/key | Free (registration) | Weekly | 🟢 | Genuinely useful for North/insurgency risk; geocoded events. |
| Official crime stats | NBS / Nigeria Risk Index | Report / portal | None | Free | Lagging | 🟡 | State-level, not neighborhood. |

> **MVP guidance:** present safety as a **city/region-level** indicator with transparent sourcing — never false street-level precision.

---

## Cross-cutting cautions

- **NASA site migration:** all NASA Earth-science sites are migrating into Earthdata through end of 2026. Build against APIs, not scraped pages; expect URL churn.
- **Earthdata Login:** one free account unlocks LANCE, LAADS DAAC, SEDAC downloads — set up early.
- **OSM coverage honesty:** surface a confidence/data-density signal per area so cities and villages aren't presented with equal certainty.
- **Licensing:** verify each portal's Terms of Service before scraping; prefer partnerships/licensed feeds for anything user-facing and commercial.
