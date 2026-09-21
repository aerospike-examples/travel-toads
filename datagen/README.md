# datagen

Generates the demo dataset described in [`../docs/design.md`](../docs/design.md): synthetic hotels
across Austin, Round Rock, and San Marcos, plus the fixed landmark set used for geo searches.

## Build

Requires a JDK 17+ and Maven. If `java` isn't on `PATH` (macOS/Homebrew sometimes needs this):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || brew --prefix openjdk)
export PATH="$JAVA_HOME/bin:$PATH"
```

```bash
mvn -q package
```

## Run

```bash
java -jar target/datagen.jar --start-date=2026-09-16 --hotels=900 --seed=42 --out=../data
```

| Flag | Default | Notes |
|---|---|---|
| `--start-date` | today | ISO date. Defaults to today so a run six months from now shows current dates, not sold-out history — see design.md's "Reproducibility" section. |
| `--days` | 90 | Date window length. |
| `--hotels` | 900 | Total hotel count, split 70% Austin / 15% Round Rock / 15% San Marcos. |
| `--seed` | 42 | RNG seed — same seed + start date + hotel count reproduces the same dataset. |
| `--out` | `./data` | Output directory. |

Writes:
- `hotels.ndjson` — one JSON object per line, matching the `hotels` set schema in design.md exactly.
- `landmarks.json` — the fixed 8-landmark array (real coordinates, not generated).
- `stats.json` — per-city/per-propertyType/per-locality counts and record-size distribution, so the
  "record size sanity check" and "deliberately not indexed" claims in design.md can be checked
  against real numbers instead of asserted.

## Known finding: record size vs. room count

design.md estimates "tens of KiB" per hotel at 20 rooms and caps rooms at 10-40 specifically to
stay inside Aerospike's 1-128 KiB sweet spot. Measured against this generator's output (which
includes full amenity lists and per-night `booked` detail — reservationId, breakfast, flex — not
just the bare rate/price structure the estimate was based on):

- ~4.1 KB per room (90-day window, ~27 rate periods/room)
- 10-room hotels: ~41 KiB. 40-room hotels: ~164 KiB — over the 128 KiB line.
- At `--hotels=900 --seed=42 --start-date=2026-09-16`: avg 100.6 KiB/hotel, **241/900 (27%) hotels
  land outside the 1-128 KiB range**, all at the high end (max 173.9 KiB).

This doesn't break anything — 128 KiB is a performance guideline in the source doc, not a hard
Aerospike record limit — but it does mean the "10-40 rooms keeps us in the sweet spot" claim doesn't
hold at the top of that range with this level of per-booking detail. Worth a decision before this
goes in the blog/video: narrow the room-count range (e.g. 10-28, which keeps the max hotel under
~115 KiB), or accept large-property outliers as realistic variety and drop the "stays in 1-128 KiB"
claim to "mostly stays in range."
