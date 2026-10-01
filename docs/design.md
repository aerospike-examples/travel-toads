# Aerospike DX demo: booking site using Java SDK, AEL, Path Expression

> Adapted from the design doc shared 2026-08-27. This file is the source of truth for the data
> model, index design, and demo narrative until a canonical link replaces it.

## Goal and deliverables

The demo is two things, both first-class:

1. **A frontend** with the look and feel of a real booking site. Not a debug console or a table of
   JSON. A destination and date search, a filter sidebar, result cards, a property detail page with
   room and rate options, and a booking flow — recognisable to anyone who has booked a hotel online.
   If it looks like a toy, the performance and modelling claims read as toy claims.
2. **The backend code** driving the APIs behind it. This is the actual subject. Every screen in the
   frontend resolves to one of the queries designed on this page, and that code is what the video,
   blog, and SE walkthrough are about. The UI exists to give the code something real to be judged
   against.

Deliverables, all built on that one application:

- Live code in the `aerospike-examples` GitHub org.
- A video walkthrough.
- Short-form videos.
- A blog post.
- Pre-canned demos SEs can run with prospects, customers, and at meetups.

Stack: Java SDK (or Python SDK), AEL, and Path Expressions.

## Screen-to-query mapping

| Frontend surface                                                   | Backend endpoint              | What it exercises                                                                 |
| ------------------------------------------------------------------ | ----------------------------- | --------------------------------------------------------------------------------- |
| Destination + date search                                          | `POST /search`                | locality index or geo index, plus date coverage via path expressions              |
| "Within N miles of the airport / downtown"                         | `POST /search` (radius param) | AeroCircle built from a landmarks point, GEO2DSPHERE index                        |
| Neighborhood chips                                                 | `POST /search`                | `hotel-locality-idx` — a different index, same code shape                         |
| Filter sidebar: rating, price, amenities, property type, bed, view | `POST /search`                | filter-expression predicates layered on the index scan                            |
| Property name typeahead                                            | `GET /suggest`                | 8.1.3 string API in AEL, Unicode-safe                                             |
| Property detail: rooms and rate options for the chosen dates       | `GET /hotels/{id}`            | path-expression projection — only matching rooms and rate segments cross the wire |
| Book                                                               | `POST /bookings`              | atomic remove-from-`available` + append-to-`booked` with a count check            |

Worth considering: show the query alongside the result. A collapsible panel that displays the AEL
string driving the current view — updating live as filter chips are toggled — makes the "readable
query language" claim self-evident instead of asserted. It is the same move Voyager's Expression tab
already makes, and it turns every filter click into a demonstration. Low cost, and probably the
single highest-leverage frontend feature for this audience.

## Demo dataset

Generated, not scraped. Enough volume that filters and indexes visibly do work, small enough to load
quickly on a laptop.

| Dimension       | Target                                                                           | Why                                                                                                                                                                                                                      |
| --------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Cities          | One main + two adjacent — Austin, plus Round Rock (north) and San Marcos (south) | Not one city — with a single city the `locality` slug is always `austin-*` and the city+neighborhood composite adds nothing over neighborhood alone. Adjacency specifically is what makes the radius slider interesting. |
| Hotels          | 500–2,000 total                                                                  | Roughly 70% Austin, 15% each adjacent. Enough that an unfiltered result set is obviously too big and each chip visibly cuts it down.                                                                                     |
| Neighborhoods   | 6–8 Austin, 3 each adjacent                                                      | Real ones — Austin: Downtown, South Congress, East Austin, Zilker, Rainey Street, The Domain. Gives the locality index real cardinality without flattening the main city into the suburbs.                               |
| Rooms per hotel | 10–40                                                                            | Keeps records inside Aerospike's 1–128 KiB sweet spot.                                                                                                                                                                   |
| Date window     | 90 days                                                                          | Constrained deliberately. A full year of per-day availability across every room bloats records and index entries for no demo value.                                                                                      |

### Metro layout

| City          | Approx. centre [lon, lat] | From downtown Austin | Role in the data                                                        |
| ------------- | ------------------------- | -------------------- | ----------------------------------------------------------------------- |
| Austin (main) | [-97.7431, 30.2672]       | —                    | Urban mix: hotels, hostels, apartments. Carries the SXSW sold-out week. |
| Round Rock    | [-97.6789, 30.5083]       | ~18 mi N             | Suburban business travel: chain hotels, motels. Thinner amenity lists.  |
| San Marcos    | [-97.9414, 29.8833]       | ~29 mi SW            | University + outlet-mall destination: budget hotels, B&Bs, resorts.     |

All three sit on the I-35 corridor, bracketing Austin north and south, and all three are served by
the same airport — which is realistic and useful, because "near AUS" legitimately spans city
boundaries.

Why adjacency matters: it proves geo and locality are not redundant filters. Widening the radius
from downtown Austin pulls in whole cities one at a time — something no neighborhood chip can
express: 5 mi (8047 m) is central Austin only; 10 mi (16093 m) is most of Austin; 25 mi (40234 m)
adds Round Rock; 35 mi (56327 m) adds San Marcos. Dragging one slider and watching adjacent cities
enter the results is a better argument for the geo index than any assertion about it.

### Real city, fictional properties

Use real geography with invented hotel names. Real coordinates, neighborhoods, and landmarks are
what make distance queries meaningful and let a map render correctly — a fictional metro makes every
distance arbitrary and kills the geo story. But the names, prices, and availability must be
invented. Landmarks are per city: Austin gets downtown, AUS airport, the convention center, a
stadium; Round Rock and San Marcos each get two or three of their own. AUS is shared by all three.

This is not only an aesthetic choice: this repo and its video go public, and publishing fabricated
prices and availability against real named hotels would read as real inventory data. Fictional
properties at real coordinates avoids that entirely.

Neighborhood names for the adjacent cities should be sanity-checked against reality before seeding —
the Austin list above is solid, the suburbs less so.

### Record size sanity check

The day-list model is the main driver of record size. At 90 days with alternating weekday/weekend
rate periods, each room carries roughly 26 rate objects. At 20 rooms that is about 520 rate objects
per hotel, landing in the tens of KiB — comfortably inside the 1–128 KiB sweet spot. At 50+ rooms
with a full-year window it would leave that range, which is exactly why both are capped above.

### Reproducibility

The generator should take a start date, defaulting to today, so an SE running the demo six months
from now still sees current dates rather than a screen of sold-out history. The worked examples in
this doc use a pinned window (2026-09-16 to 2026-09-20) so the documentation stays stable; recorded
video will show whatever window was live at recording time. Worth deciding whether the video pins a
date for consistency with the blog.

**Open:** property images. A booking site needs them, and licensing matters for a public repo —
permissively licensed stock, generated placeholders, or CSS-only cards. Affects repo size as well as
legal review.

## Data model

### Set: hotels

One record per property. `rooms` is a map keyed by room ID; each room carries immutable physical
attributes plus a list of date-bounded rate periods.

```json
{
	"hotelId": "HTL-4021",
	"name": "Lakeside Grand",
	"city": "Austin",
	"neighborhood": "Downtown",
	"locality": "austin-downtown",
	"propertyType": "hotel",
	"location": { "type": "Point", "coordinates": [-97.745, 30.264] },
	"stars": 4,
	"rating": 86,
	"reviewCount": 1247,
	"amenities": [
		"breakfast_included",
		"free_cancellation",
		"pool",
		"fitness",
		"restaurant",
		"room_service",
		"airport_shuttle"
	],
	"rooms": {
		"room-101": {
			"bed": "king",
			"sqm": 34,
			"view": "pool",
			"maxOccupancy": 2,
			"nonSmoking": true,
			"amenities": [
				"air_conditioning",
				"private_bathroom",
				"balcony",
				"tv",
				"coffee_maker",
				"refrigerator",
				"bath"
			],
			"rates": [
				{
					"rateId": "WKD",
					"from": 20260916,
					"to": 20260917,
					"price": 144,
					"available": [20260916, 20260917],
					"booked": []
				},
				{
					"rateId": "WKND",
					"from": 20260918,
					"to": 20260919,
					"price": 165,
					"available": [20260919],
					"booked": [
						{
							"day": 20260918,
							"reservationId": "RES-88213",
							"breakfast": true,
							"flex": false
						}
					]
				}
			]
		},
		"room-402": {
			"bed": "twin",
			"sqm": 28,
			"view": "street",
			"maxOccupancy": 2,
			"nonSmoking": true,
			"amenities": ["air_conditioning", "private_bathroom", "tv"],
			"rates": [
				{
					"rateId": "WKD",
					"from": 20260916,
					"to": 20260917,
					"price": 118,
					"available": [20260916, 20260917],
					"booked": []
				},
				{
					"rateId": "WKND",
					"from": 20260918,
					"to": 20260919,
					"price": 152,
					"available": [],
					"booked": []
				}
			]
		}
	}
}
```

### Design decisions worth keeping straight

| Decision                                                          | Rationale                                                                                                                                                                                                                             |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dates are YYYYMMDD integers                                       | Readable on screen and integer-comparable. Removes the earlier ISO-string + unix-timestamp pair entirely.                                                                                                                             |
| `available` is a list of day integers; `booked` is a list of maps | Booking removes a day from `available` and appends to `booked`. Coverage checking becomes a count, not interval-overlap plus gap detection.                                                                                           |
| `from`/`to` stay as immutable period bounds                       | The rate period is policy and doesn't change; `available` is inventory and shrinks. Same ARI (Availability/Rates/Inventory) split real channel-manager feeds use.                                                                     |
| `rating` is 0–100 integer                                         | Displayed as divide-by-ten (86 → 8.6). Sidesteps the float-indexability question entirely. Guard: next to `stars: 4` this reads like a percentage — document it in the mapper and README so nobody "fixes" it.                        |
| Physical attributes on the room, time-varying on the rate         | `bed`, `sqm`, `view`, `maxOccupancy`, `nonSmoking`, room `amenities` never change. Price and availability do.                                                                                                                         |
| Amenities are codes, not display strings                          | `wifi`, not "Free WiFi". A global travel brand runs in dozens of languages — the frontend localizes. Cheap now, painful to retrofit.                                                                                                  |
| `breakfast_included` / `free_cancellation` are hotel amenities    | Coarse property-level search chips, matching how OTA filters actually behave. `wifi` and `parking` dropped — a chip matching every record is a dead chip on camera.                                                                   |
| `locality` is a mapper-computed slug                              | `city` + `neighborhood` normalized to `austin-downtown`. Aerospike has no composite indexes; this is the workaround, and storing it keeps the index a plain bin index. Requires more than one city in the seed data to be meaningful. |
| Prices are whole dollars                                          | Readability over correctness for the demo. Cents-as-integers is the production answer.                                                                                                                                                |

### Set: landmarks

Small reference data. These are the origin of proximity searches, never the target, so they need no
geo index — read by key and cacheable at app startup.

```json
{ "landmarkId": "aus-downtown", "city": "Austin", "name": "Downtown Austin",
  "type": "city_center",
  "location": { "type": "Point", "coordinates": [-97.7431, 30.2672] } }

{ "landmarkId": "aus-airport", "city": "Austin", "name": "Austin-Bergstrom Intl",
  "type": "airport",
  "location": { "type": "Point", "coordinates": [-97.6664, 30.1975] } }
```

`type` gives the UI its grouping: `city_center`, `airport`, `convention_center`, `stadium`,
`transit_hub`. Austin, Round Rock, and San Marcos each get their own; AUS is shared.

GeoJSON gotchas that fail quietly: coordinates are `[longitude, latitude]` — the reverse of how
everyone says it aloud. The bin must be GEO-typed, not a nested map: if the RecordMapper serializes
`location` as a map it becomes a MAP bin, the geo index silently doesn't cover it, and queries return
nothing with no error. Worth an explicit test in the repo. AeroCircle radius is in meters, so the
UI's mile chips need one conversion table (1 mi = 1609, 3 mi = 4828, 5 mi = 8047, 10 mi = 16093,
25 mi = 40234, 35 mi = 56327).

## Secondary indexes

Four indexes. Three are plain bin indexes; only minimum price needs an expression.

| Index                | ktype       | itype   | Source                              | Role                            |
| -------------------- | ----------- | ------- | ----------------------------------- | ------------------------------- |
| `hotel-loc-idx`      | geo2dsphere | default | `location` bin                      | distance-from-landmark searches |
| `hotel-locality-idx` | string      | default | `locality` bin                      | neighborhood browse             |
| `hotel-rating-idx`   | numeric     | default | `rating` bin                        | quality chip (8.0+ → `>= 80`)   |
| `hotel-minprice-idx` | numeric     | default | expression, rank-0 over rate prices | price chip                      |

### Creating them — SDK

```java
// Bin indexes — no expression involved
session.createIndex(hotels, "hotel-loc-idx", "location",
    IndexType.GEO2DSPHERE, IndexCollectionType.DEFAULT).waitTillComplete();

session.createIndex(hotels, "hotel-locality-idx", "locality",
    IndexType.STRING, IndexCollectionType.DEFAULT).waitTillComplete();

session.createIndex(hotels, "hotel-rating-idx", "rating",
    IndexType.INTEGER, IndexCollectionType.DEFAULT).waitTillComplete();

// Expression index — minimum nightly price across every rate in the record
Exp prices = CdtExp.selectByPath(Exp.Type.LIST, SelectFlags.VALUE,
    Exp.mapBin("rooms"),
    CTX.allChildren(),
    CTX.mapKey(Value.get("rates")),
    CTX.allChildren(),
    CTX.mapKey(Value.get("price")));

Expression minPrice = Exp.build(
    ListExp.getByRank(ListReturnType.VALUE, Exp.Type.INT, Exp.val(0), prices));

session.createIndex(hotels, "hotel-minprice-idx",
    IndexType.INTEGER, IndexCollectionType.DEFAULT, minPrice).waitTillComplete();
```

The min-price index is the one place the demo uses `Exp` rather than AEL — `createIndex` has no AEL
overload. Everything else, including every query, is AEL.

A third overload creates a set index (8.1.2+) with no bin or type:
`session.createIndex(dataSet, "idx_set_presence").waitTillComplete();` — not needed here, but worth
knowing it exists.

Ops-side equivalent (for the runbook, not the demo):

```bash
asadm -e "enable; manage sindex create geo2dsphere hotel-loc-idx      ns demo set hotels bin location"
asadm -e "enable; manage sindex create string      hotel-locality-idx ns demo set hotels bin locality"
asadm -e "enable; manage sindex create numeric     hotel-rating-idx   ns demo set hotels bin rating"
asadm -e "enable; manage sindex create numeric     hotel-minprice-idx ns demo set hotels exp_base64 <B64>"
```

Both paths converge on the same `sindex-create` info command, so they are interchangeable — the SDK
just doesn't require a shell.

### Verifying them — SDK introspection

```java
List<Sindex> indexes = session.info().secondaryIndexes();

for (Sindex idx : indexes) {
    System.out.printf("%-20s %-12s %-4s %-14s entries/value=%d%n",
        idx.getIndexName(),
        idx.getType(),          // IndexType
        idx.getState(),         // WO while building, RW when queryable
        idx.getBin() != null ? idx.getBin() : "(expression)",
        idx.getEntriesPerBval());
}
```

`Sindex` also exposes `getNamespace()`, `getSet()`, `getIndexType()`, `getContext()`, and `getExp()`.

Two demo beats this unlocks:

- `IndexState` is `WO` then `RW`. An index is write-only while it populates, and queries against it
  fail until it flips to `RW`. Showing that transition explains an error message every new Aerospike
  user eventually hits, and it justifies `waitTillComplete()` rather than making it look ceremonial.
- `getEntriesPerBval()` makes selectivity measurable. The "deliberately not indexed" argument below
  is usually asserted; this reports entries per bin value directly. `hotel-locality-idx` will show a
  low number, and a hypothetical `propertyType` index would show a huge one — the same claim,
  demonstrated instead of stated.

Deliberately not filtered to available rates for min-price — that would make the min-price index
churn on every booking. Price changes only on repricing, so this stays stable. It's a coarse
pre-filter with no false negatives; path expressions resolve the user's actual date window
downstream.

### Deliberately not indexed

Worth its own slide — most demos never say this part out loud.

| Candidate                                            | Why not                                                                                                                                                                                                                                                                                                            |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Available days (list of day integers)                | The obvious index, and the wrong one. Day values repeat across rooms, so a date range query returns the record once per matching (room, rate, day) — up to 80× for a 4-night search on a 20-room hotel. The SI docs confirm range queries on collection indexes require application-side dedup. Bad DX; not shown. |
| Bed types via path extraction                        | Same failure mode — a hotel with ten king rooms writes `king` ten times. If needed later, use a denormalized distinct `bedTypes` rollup instead.                                                                                                                                                                   |
| Amenities (list, string)                             | Would be safe — a hotel lists `pool` once, so equality hits one entry per record. Dropped to keep the index set tight.                                                                                                                                                                                             |
| `propertyType`                                       | Six values across the dataset. Terrible selectivity; the index costs more than it saves. Filter expression. (`entriesPerBval` proves it.)                                                                                                                                                                          |
| `reviewCount`, `stars`, `maxOccupancy`, `nonSmoking` | Cheap filter-expression predicates on an already-narrowed set.                                                                                                                                                                                                                                                     |
| `landmarks.location`                                 | Search origin, never search target.                                                                                                                                                                                                                                                                                |

The duplicate rule, stated cleanly: duplicates come from repeated values within the indexed
collection. That single sentence explains every row above.

One index predicate per query. Everything else becomes a filter expression. The demo must show
different queries choosing different indexes — never one query using several.

## Demo narrative

### 1. Connect, create indexes, load — Java SDK

No policy-object juggling. The `RecordMapper` earns its keep here: it computes the `locality` slug
and emits `location` as a GEO-typed bin — real work, not bin shuffling. Then list the indexes and
show them reach `RW` before any query runs.

```java
Cluster cluster = new ClusterDefinition("localhost", 3000).connect();
Session session = cluster.createSession(Behavior.DEFAULT);
DataSet hotels = DataSet.of("demo", "hotels");

// …createIndex calls from above…

session.insert(hotels)
    .object(hotel)
    .using(hotelMapper)
    .execute();
```

### 2. Search near a landmark — geo index + AEL

Read the landmark point by key, build an AeroCircle, and let the server's planner route it to the
geo index:

```java
String circle = "{\"type\":\"AeroCircle\",\"coordinates\":[[-97.6664,30.1975],4828]}";

RecordStream rs = session.query(hotels)
    .where("geoCompare($.location, geoJson('" + circle + "')) "
         + "and $.rating >= 80 "
         + "and $.propertyType == 'hotel'")
    .execute();
```

`geoJson()` and `geoCompare()` are confirmed AEL grammar (`docs/ael/hll-and-geo.md`), AeroCircle
included. The circle is constructed per request and never stored. Widening the radius is the moment
adjacent cities enter the result set.

### 3. Browse a neighborhood — a different index, same code shape

This is the index-selection beat: identical fluent chain, identical AEL shape, different index
chosen by the server. `QueryHint` (`forIndex`, `requireIndex`, `hardHint`) plus the planner's
explain flags let the demo show which index was picked rather than assert it.

```java
session.query(hotels)
    .where("$.locality == 'austin-downtown' and $.rating >= 80")
    .execute();
```

### 4. Find rooms and rate segments — path expressions in AEL

Geo, scalar filters, and nested traversal in one AEL string. Note the coarse/fine split: a flat
`count()` sums across every room, so it cannot express "one room covers all four nights" — that's
the record-level pre-filter, with exact per-room coverage resolved in the projection. Same
coarse-then-fine pattern used throughout this design.

> **Corrected 2026-09-11** (see `backend/README.md`'s "AEL verified against a real 8.1.3+ server"):
> the `..` below was never valid AEL — there is no recursive-descent operator; the server rejects it
> outright, and the SDK's own `docs/ael/*.md` uses `..` for nothing but the `[1:3]` range form. Every
> multi-level example in that reference chains an explicit `.*` per level instead, which is what the
> two AEL strings below now do. `$.rooms` also needed an explicit `:MAP` type annotation once run
> for real — a bare CDT root can't otherwise be wildcarded (`unresolved bin type, use $.bin:LIST or
$.bin:MAP`); nested steps resolve fine once the root is typed.

```java
session.query(hotels)
    .where("geoCompare($.location, geoJson('" + circle + "')) "
         + "and $.rooms:MAP.*[?(@.bed == 'king')].count() > 0 "
         + "and $.rooms:MAP.*.rates.*.available.*[?(@ >= 20260916 and @ <= 20260919)].count() > 0")
    .execute();
```

Per-room coverage is itself a count, which is the simplification the day-list model bought us:

```text
$.rooms.room-101.rates.*.available.*[?(@ >= 20260916 and @ <= 20260919)].count() == 4
```

### 5. Hotel name search — 8.1.3 string API in AEL

```java
session.query(hotels)
    .where("$.name.lowercase().indexOf(needle: 'lakeside') >= 0")
    .execute();
```

Worth seeding a few non-ASCII property names: the String Ops PRD guarantees Unicode canonical
equivalence on `find`/`contains`/`replace`, so precomposed and decomposed forms match. For a global
travel brand that lands harder than another `==` filter.

### 6. Book a room — atomic, with a real concurrency check

A booking removes the requested days from `available` and appends entries to `booked`. Both touch
one record, so it is a single atomic `operate()` — no transaction needed.

The trap worth showing: remove-by-value is a silent no-op if the day is already gone, so a second
booker "succeeds" without getting the room. The CDT remove returns a count — assert it equals the
number of nights requested before appending to `booked`, and fail the booking otherwise. Same
check-and-set shape as the retail tutorial's cart.

### 7. Close the loop in Voyager

Open the booked hotel record and confirm the nested update landed — reinforcing that exploration and
production share one surface, not just at the query step.

> Key-based lookup only — don't expect to paste this page's AEL strings into Voyager's expression
> editor and have them run. Confirmed 2026-09-16 (see `backend/README.md`'s "Voyager's expression
> editor doesn't support AEL path expressions"): Voyager v0.2.6's parser rejects even a bare `.*`
> wildcard, so none of the path-expression queries above work there yet — flat scalar comparisons
> like step 3's `$.locality == 'austin-downtown' and $.rating >= 80` do (`==` included, confirmed
> despite Voyager's own error text not listing it). One catch running the real app rather than
> typing this page's examples by hand: every live `/search` call adds the mandatory date-coverage
> clause (checkIn/checkOut are the only required fields), which is itself a wildcard — so there's no
> search on the actual site that produces a paste-whole-and-run string; copying then trimming a real
> query down to its flat portion is the honest version of this demo beat (see backend/README.md).
> The presenter panel is the "watch the real query" surface for this demo until Voyager's AEL
> support catches up.

## Verified against the SDK source

Checked against `aerospike-client-java-sdk`, branch `stage` (commit `c1f3cc0`). These are
load-bearing for the code above.

| Finding                                                                     | Evidence                                                                                                                                                                                                                                                                                                                                                                                                              |
| --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| AEL is required for index-based query planning, and planning is server-side | `IndexProbePlanner.plan()` calls `QueryWhereWire.requireAel(ael)` and ships the AEL to the server via `IndexProbeCommand`. This is why every query in the demo is AEL — it isn't stylistic.                                                                                                                                                                                                                           |
| AEL requires server 8.1.3+                                                  | `Cluster.supportsAel()` returns `versionGE813`.                                                                                                                                                                                                                                                                                                                                                                       |
| Path expressions work in AEL where-clauses                                  | AEL parsing moved server-side (CLIENT-5163), and the canonical reference specifies wildcards, loop variables, and read terminals (§4.3, §7, §12, §22.5).                                                                                                                                                                                                                                                              |
| But AEL fragments can't be spliced into the Java CDT path builder           | `onEachChild(String ael)` and `modifyBy(String ael)` throw; the `Exp` overloads work. Narrow — only affects projection filters and modify bodies, not where-clauses.                                                                                                                                                                                                                                                  |
| The SDK creates and lists indexes directly                                  | `Session.createIndex` in three forms (bin, expression, set) returning `IndexTask`; `session.info().secondaryIndexes()` returns `List<Sindex>` with `IndexState` (`WO`/`RW`) and `entriesPerBval`. No CLI needed for demo setup.                                                                                                                                                                                       |
| `createIndex` has no AEL overload                                           | Every signature in `Session.java` takes a bin name or an `Expression`. This is why `hotel-minprice-idx` needs `Exp`.                                                                                                                                                                                                                                                                                                  |
| Compiling AEL to base64 does not help                                       | `AelMaterializer.expressionFromString(...)` returns `Expression.fromServerCompiledFilter()`, which packs `[128, "<ael text>"]` — a deferred-parse envelope scoped to the FILTER_EXP wire field (43), not a compiled expression tree. Routing it through asadm doesn't change that: `Session.buildCreateIndexInfoCommand` emits the same `sindex-create:...;exp=<base64>` info command that `manage sindex` writes to. |
| `Filter.geoWithinRadius` builds the AeroCircle internally                   | Confirmed in `query/Filter.java`. Also present: `geoWithinRadiusByIndex`, `equalByIndex`, `rangeByIndex`, `containsByIndex`.                                                                                                                                                                                                                                                                                          |
| `StringExp` already ships the 8.1.3 surface                                 | `lower`, `upper`, `replaceAll`, `trim`, `concat`, `regexCompare`, `contains`, `startsWith` — client-side wiring is ahead of the server release. Note the convention: bin/source is the last argument for expressions.                                                                                                                                                                                                 |
| Parameterized AEL is real                                                   | `.where(String ael, Object... params)` binds through `AelPlaceholderBinder`. The demo should use it rather than concatenating values into query text.                                                                                                                                                                                                                                                                 |

## Open items

### Product / timing

- This demo's data model is loosely inspired by a real preview customer's use case, not named here
  or anywhere in this repo. Naming/quote clearance from product marketing is required before any
  asset built from this demo names that customer publicly — it currently doesn't, and shouldn't
  until cleared.
- **Resolved by direct testing against the 8.2.0.0 GA release:** the client-side `StringExp` surface
  shipped ahead of the server — `trim()` works server-side, but `lowercase()`/`uppercase()`/`length()`
  still don't (confirmed via `backend/README.md`'s probe, re-confirmed against GA). Locality slug
  normalization and case-insensitive name search fall through to the classic-Exp tier for now, not
  blocked outright — see "Fixing hotel-name search's fall-through" in `backend/README.md`.
- AEL itself is no longer a preview-only capability — server 8.2 (which includes 8.1.3+) is
  publicly GA, and `docker-compose.yml`'s tracked default now points at it directly.
- Presenter and target date.

### Engineering questions to file

- AEL index authoring. Request `createIndex(DataSet, String, IndexType, IndexCollectionType, String ael)`
  with server-side compilation at index-create time — the server already parses AEL on every query.
  Until then, one index in the demo drops to `Exp` for a reason no viewer will understand. Now that
  8.2 GA is reachable, this is actually testable: does `sindex-create` accept opcode 128 and parse
  the embedded AEL? Not yet run — still genuinely open, not just waiting on server availability.
- Confirm the `.where(String, Object...)` placeholder token so query text is never string-concatenated.
- Verify a GEO-typed `location` bin through the `RecordMapper` — a MAP bin fails silently against the
  geo index.
- Confirm whether equality queries on collection indexes collapse duplicate (value, record) pairs, in
  case the amenity or bed-type index is revisited.

### Build decisions

- Repo: fork/extend `path-expressions-java-preview`'s `booking/` folder, or a new repo scoped to this
  demo. — **Decided 2026-08-27: starting a new repo (this one), not forking, since no local checkout
  of the reference repos was available.**
- Backend: Java SDK vs Python SDK for the primary implementation. — **Decided 2026-08-27: Java SDK.**
- Frontend stack — undecided. Must be capable of the booking-site look and feel described above, not
  just a functional shell.
- Property images: licensing and repo-size decision (permissive stock vs generated placeholders vs
  CSS-only cards).
- Whether the recorded video pins a fixed date window for consistency with the blog, or runs against
  today-relative generated data.
- Sanity-check neighborhood names for Round Rock and San Marcos before seeding; the Austin list is
  solid.
- Seed data must vary. Filters only look real if they discriminate. Property type is the lever:
  hostels with shared bathrooms, apartments with kitchenettes, motels with thin amenity lists,
  resorts with the full set. Also seed a sold-out event week (SXSW) so date filtering visibly cuts
  the result set.

## Reference documentation index

See the original design doc for the full annotated bibliography (SDK source files, internal
Confluence pages, published docs, and release notes). Not reproduced here since most of it is either
internal-only or easily found from the public docs referenced inline above.
