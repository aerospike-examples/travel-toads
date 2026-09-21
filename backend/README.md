# Booking backend

Java backend for the Aerospike DX demo booking-site appliance, built on Aerospike's **preview**
Java SDK (`com.aerospike:aerospike-client-sdk`, built from source from
[`aerospike-client-java-sdk`](https://github.com/aerospike/aerospike-client-java-sdk) — pinned to a
fixed commit on the `stage` branch, not the branch tip; see "AEL verified against a real 8.1.3+
server" below for why pinning replaced tracking `stage` directly. Not the classic `aerospike-client`
on Maven Central). See `../docs/design.md` for the data model, index design, and demo narrative this
implements; this file covers how to build/run it and where the real implementation had to deviate
from that design doc.

## Build & run

### Standalone (no Docker)

Requires JDK 21+ (this backend and the SDK it depends on both build with `--release 21`) and a
reachable Aerospike server.

```bash
# 1. Build and install the preview SDK's "client" module into your local ~/.m2. Pinned to a
#    specific commit, not the "stage" branch tip - see "AEL verified against a real 8.1.3+ server"
#    below for why: stage is a moving branch that broke this build once already (bumped the AEL
#    version gate from 8.1.3 to 8.2.0 with no warning). Match backend/Dockerfile's SDK_REF if you
#    change this.
git clone --branch stage --depth 50 https://github.com/aerospike/aerospike-client-java-sdk /tmp/sdk
git -C /tmp/sdk checkout 6222bee
mvn -q -pl client -am install -DskipTests -f /tmp/sdk/pom.xml

# 2. Build this backend:
cd backend
mvn -q package -DskipTests

# 3. Run it (defaults match docker-compose.yml; override for a standalone Aerospike):
AEROSPIKE_HOST=localhost AEROSPIKE_PORT=3000 AEROSPIKE_NAMESPACE=test \
DATA_DIR=../data SCENARIOS_DIR=../scenarios SERVER_PORT=8081 \
  java -jar target/booking-backend.jar
```

`AEROSPIKE_NAMESPACE` defaults to `demo` (matching `aerospike/aerospike.conf`'s namespace, used by
docker-compose). If you're pointing at a plain `docker run aerospike/aerospike-server` container
with no custom config, its default namespace is `test` — set `AEROSPIKE_NAMESPACE=test` as above.

Environment variables (all optional, defaults shown):

| Variable | Default | Notes |
|---|---|---|
| `AEROSPIKE_HOST` | `aerospike` | compose service name; use `localhost` standalone |
| `AEROSPIKE_PORT` | `3000` | |
| `AEROSPIKE_NAMESPACE` | `demo` | see above |
| `DATA_DIR` | `/data` | must contain `hotels.ndjson` and `landmarks.json` |
| `SCENARIOS_DIR` | auto-detected (`/app/scenarios`, then `../scenarios`, then `scenarios`) | see "Deviations" below |
| `SERVER_PORT` | `8081` | |

### Docker

```bash
docker build ./backend -t booking-backend
```

The Dockerfile is three stages: `sdk-builder` clones the SDK branch and `mvn install`s just its
`client` module; `build` compiles this backend against that installed artifact; the final stage is
a slim `eclipse-temurin:21-jre-alpine` image with `wget` installed (needed by docker-compose's
healthcheck) running the shaded jar. `ARG SDK_REF=stage` lets you pin a commit SHA
(`--build-arg SDK_REF=<sha>`) if the `stage` branch moves out from under this build — the backend
was built and tested against commit `ef7a1ea` (2026-08-28, "CLIENT-5278 Update AEL tests for
latest server").

Normal usage is `./demo start` from the repo root (see the root `README.md`), which builds and
runs this alongside `aerospike`, `datagen`, and `frontend` via `docker-compose.yml`.

### Tests

There is no `mvn test` suite in this module — see "What wasn't done" below. Everything described
under "Verified end-to-end" was checked by actually running the built jar/image against a real
Aerospike server, not by unit tests.

## Deviations from the brief, and why

**1. `docker-compose.yml` gained one bind mount on the `backend` service (`../scenarios ->
/app/scenarios:ro`), plus one new env var (`SCENARIOS_DIR`).** The brief requires reusing
`scenarios/*.json` verbatim at runtime rather than hardcoding that copy into Java. Those files live
at the repo root, but `docker-compose.yml`'s `build: ./backend` line scopes the backend's Docker
*build context* to `backend/` only, so the Dockerfile has no way to `COPY` a repo-root directory at
image-build time. Restructuring the build context (`context: .` + `dockerfile: backend/Dockerfile`)
would have been a bigger, riskier change to a file owned by someone else's concurrent work; a
single read-only bind mount is the smallest correction that makes the documented behavior
possible, and it keeps `scenarios/*.json` as the single live source (no copying/staleness risk).
If `SCENARIOS_DIR` doesn't resolve to a real directory (e.g. someone drops this mount), the backend
doesn't crash — `ScenarioRegistry` logs a warning once and every `demo:` field in API responses
just comes back `null`.

**2. Booking is two sequential atomic single-record operations, not one combined `operate()`.**
`scenarios/booking.json`'s illustrative code shows one `.execute()` call doing both the
remove-from-`available` and the append-to-`booked`. But a single `operate()` runs every operation
in the call regardless of what an earlier operation in the *same* call returned — there's no
in-call conditional. Combining them the way the illustration shows would mean either always
appending (which defeats design.md's own point about asserting the removed count first) or
appending unconditionally and compensating afterwards. Instead, `BookingService` does: (a) an
atomic remove-by-value-list from `available`, returning the values actually removed; (b) if that
count doesn't match the requested nights, it puts back whatever *was* removed (a corrective
`listAppendItems`) and returns `409 ROOM_NOT_AVAILABLE` without ever touching `booked`; (c) only on
a full match does it append the reservation to `booked`, in a second atomic call. Each step is
still a genuine atomic single-record CDT operation; what's given up is a single round trip, and
there is a narrow window between steps (a) and (c) where the removed nights are gone from
`available` but not yet reflected in `booked`. Acceptable for a demo; a production system would
want a stored procedure or a transaction here instead.

**3. The GEO-typed `location` bin is written as a second explicit call, not inside the mapped
insert.** This is exactly the gotcha docs/design.md calls out by name: a GeoJSON value pushed
through the generic `RecordMapper.toMap()` → map path becomes a MAP bin, not a GEO2DSPHERE bin, and
`hotel-loc-idx` then silently never covers it. This SDK branch's own geo test
(`QueryGeoTest.java`) confirms the only way to write a GEO-typed bin is the explicit
`.bin(name).setToGeoJson(json)` operation. I checked whether `OperationObjectBuilder`/
`ObjectBuilder` (the builder behind `session.insert(dataSet).object(hotel).using(mapper)`) has any
hook to splice in an extra explicit bin operation alongside the mapped object — it doesn't (no
`.bin(...)` method anywhere on that builder chain). So `HotelLoader.writeHotel()` does the mapped
insert (which deliberately omits `location`, see `HotelMapper`'s Javadoc) and then immediately
issues `session.upsert(key).bin("location").setToGeoJson(...).execute()` on the same key. I did not
just assume this is correct: after loading, I queried `hotel-loc-idx` (via the workaround `Exp`
form for the reasons in point 4 below, and separately with AEL once I'd confirmed it via a local
probe) and got real, non-zero geo results, so the index genuinely covers the bin — not a silent
zero-row failure.

**4. `hotel-minprice-idx` uses the `Exp`-tree `createIndex` overload from design.md, even though
this SDK branch now also has an AEL `createIndex(DataSet, String, IndexType,
IndexCollectionType, String ael)` overload** (design.md's own "Open items" section anticipated this
might land later — it has). I didn't switch to it, on purpose: the AEL overload still needs
server-side AEL parsing (8.1.3+), and the brief is explicit that index creation should actually
work today against 8.1.2.4. Using the `Exp` form (verbatim from design.md's `CdtExp.selectByPath` /
`ListExp.getByRank` snippet — the class and method names in this branch matched design.md exactly,
no adjustment needed) means all four indexes really do reach `RW` on the server this demo currently
ships with. Worth revisiting once the AEL overload path is exercised against 8.1.3+.

**5. `GET /health`'s `recordCount` is a real primary-index scan, not the set-info "objects"
stat.** I initially used `session.info().set("hotels").get().getObjects()`. `Session.truncate()`'s
own Javadoc says the call "may return before the truncation is complete... new records can be
written immediately because they'll have a newer last-update time" — and I hit exactly that in
testing: calling `POST /admin/reset` against 900 loaded hotels, then reloading, briefly reported
`recordCount: 1800` (the old, not-yet-reaped records plus the freshly reloaded ones) even though
every actual read/query already correctly saw only the new 900. Since the brief is explicit that
`/health` accuracy is load-bearing for the whole `./demo start`/`./demo reset` UX, `hotelRecordCount()`
now does a plain unindexed scan (no AEL, so it works today) and counts what's actually visible,
which sidesteps the stat's lag entirely. Confirmed by testing `/admin/reset` repeatedly against a
real container and polling `/health` immediately after — it now reports the correct count right
away every time.

**6. `GET /hotels/{id}`'s room/rate projection is done in Java after a plain key read, not as a
server-side AEL path-expression projection.** Every other AEL-dependent endpoint in this backend
has a documented `503 AEL_UNSUPPORTED` fallback — but the API contract does *not* list a 503 case
for this endpoint, only 404. Since AEL genuinely does not work at all against the 8.1.2.4 server
this demo ships with today, the only way to make this endpoint actually function (not just exist)
is to do the "only matching rooms/rate segments cross the wire" filtering in the application layer
instead of in an AEL where-clause/projection: read the full record by key (no AEL, no index, always
works), then filter each room's `rates` list down to periods overlapping `[checkIn, checkOut]` in
`HotelRecordUtil.projectRooms()`. The output shape is identical either way. A true server-side
projection via `Exp`-tree CDT reads (which wouldn't need 8.1.3) is possible and would be a better
long-term answer, but wasn't worth the added complexity given the time budget here.

**7. `scenarios/path-expressions.json` is reused for both the `/search` bed+date filter and the
`GET /hotels/{id}` demo block.** There is no dedicated scenario file for "property detail" — the
five provided files cover `search-near-airport`/`increase-radius`, `click-neighborhood`,
`filter-rooms`, `search-name`, and `book`. Design.md's own step 4 narrative describes the coarse
room/date filter and the finer per-room coverage count as the same "path expressions" demo beat, so
`HotelDetailService` reuses `path-expressions.json`'s copy for its `demo` block too, substituting
only `checkIn`/`checkOut` (there's no `bed` filter on this endpoint, so that placeholder is left as
the scenario's own literal text). This is a judgment call given the ambiguity, not something the
brief spelled out explicitly.

**8. `/search`'s choice of demo scenario when no location filter is given.** The API contract's
`landmarkId`/`locality` fields are both optional, but if neither is present there's no
geo/locality index predicate to attach a scenario to. In that case (and only in that case) the
response's `demo` is `null` even when `demo: true` was requested and even though the query still
runs (as a plain filtered scan) — there's no dishonest scenario to attach to it. If `bed` is given
without a location filter, `path-expressions` is used as the scenario instead, since that's still
an accurate description of the query actually run.

**9. AEL parameters are bound via `PreparedAel`/`?N` placeholders, not the plain `.where(String,
Object...)` overload** design.md's "Parameterized AEL is real" note points at. Reading this
branch's actual current source (`AbstractFilterableBuilder.createWhereClauseProcessor`, commit
`ef7a1ea`): the plain string overload substitutes parameters via `String.format(ael, params)` — raw
text substitution, no AEL-literal quoting or escaping — which is exactly the string-concatenation
risk the brief says to avoid. The safe positional-placeholder binding that does quote/escape values
into valid AEL literals (`AelPlaceholderBinder`) turned out to live behind the separate
`PreparedAel` type in this commit, reached via `.where(PreparedAel, Object...)`, not the plain
string overload — evidently this moved since design.md's own commit (`c1f3cc0`). Every AEL string
this backend builds (`SearchService`, `SuggestService`) uses `?0`, `?1`, ... placeholders and goes
through `AerospikeService.buildAelQuery()`, which wraps it in `PreparedAel.prepare(...)` before
calling `.where(...)` — so request values (locality names, search text, landmark names, etc.) are
always bound as literals, never spliced into the query text as raw strings.

**10. CORS is hand-rolled (`ctx.header(...)` in a `before` filter + a catch-all `OPTIONS`
handler), not Javalin's bundled CORS plugin.** Functionally identical for this demo's needs
(`Access-Control-Allow-Origin: *`), and avoids depending on the exact `bundledPlugins.enableCors(...)`
API shape of the pinned Javalin version.

## Getting real results without 8.1.3

`/search` and `/suggest` originally 503'd with `AEL_UNSUPPORTED` on every call against this
demo's 8.1.2.4 server — correct, but not a working demo. This section covers the classic-Exp
fallback that makes them return real, correct results today, with no AEL involved at all, and
exactly what was and wasn't achievable that way (verified against a real server throughout, not
assumed — every count below was cross-checked against `data/stats.json`).

**Why this is possible at all:** AEL — the text parser and its index planner — needs 8.1.3+, but
the classic `Exp`/CDT expression-filter API (`QueryBuilder#where(Exp)`) is a long-standing
Aerospike feature, unrelated to AEL, that works against any server. This SDK branch's own geo test
(`QueryGeoTest.java`) proves it: it runs the identical geo predicate this way instead of via AEL.
The cost, stated directly in that method's own javadoc ("If this method is used, no secondary
index can be used"), is that it always runs as a full scan with a filter, not an index probe —
fine for 900 records.

**What works, per predicate (`ClassicFilters.java`), each confirmed with a real, non-degenerate
count:**

| Predicate | Classic-Exp construction | Verified count | Cross-check |
|---|---|---|---|
| Geo (landmark + radius) | `Exp.geoCompare(Exp.geoBin(...), Exp.geo(circleJson))` | 97 (airport, 10mi, rating≥70) | plausible subset |
| Locality | `Exp.eq(Exp.stringBin("locality"), ...)` | 112 (austin-downtown) | exact match |
| City (whole-city destination) | `Exp.eq(Exp.stringBin("city"), ...)` — unindexed, same tier as propertyType/bed/amenities, not an index-selecting predicate like geo/locality | 630 (Austin), 135 (Round Rock), 135 (San Marcos) | exact match all three |
| Rating | `Exp.ge(Exp.intBin("rating"), ...)` | 64 (≥80) | plausible |
| Property type | `Exp.eq(Exp.stringBin("propertyType"), ...)` | 39 (resort), 407 (hotel) | exact match both |
| Amenities | `ListExp.getByValue(COUNT, ..., Exp.listBin("amenities"))` — plain top-level list, no nesting | 344 (pool) | plausible |
| Bed type | `CdtExp.selectByPath` (scalar leaf) → `ListExp.getByValue` | 864/900 (king) | plausible (5 non-dorm bed types) |

**What doesn't work, and why — a real, isolated finding, not a guess:**

- **Date-range availability** (`$.rooms..rates..available.[?(@ >= X and @ <= Y)].count() > 0`) has
  no working classic-Exp form. `CdtExp.selectByPath` reliably flattens a nested *scalar* leaf
  (proven twice: bed-type above, and `minPriceExpression()`), but does **not** flatten a nested
  *list*-typed leaf (each rate's "available" list) the same way. Diagnosed by isolating each
  layer against the real server: `ListExp.size()` on the selected structure correctly reported
  "non-empty" for all 900 records, but every value/range-match attempt against the same structure
  (`getByValue`, `getByValueRange`, with and without an intermediate select, even with a
  deliberately absurd all-encompassing date range) came back zero — a silent structural mismatch
  (almost certainly a list-of-lists where a flat list of day-integers was expected), not an error,
  not a range/off-by-one bug. This is exactly the kind of query AEL's path expressions exist to
  make simple; replicating it by hand in raw CDT expressions didn't work cleanly in this SDK
  preview. Fixed as an honest application-layer post-filter instead —
  `HotelRecordUtil#hasDateCoverage`, applied to whatever the server's filter already narrowed
  down. Every *other* predicate in the same query still runs server-side; only this one doesn't.
- **Name search (`/suggest`)** — `StringExp.regexCompare` (case-insensitive contains) is **also**
  gated to 8.1.3, independently of AEL: confirmed via a one-time capability probe
  (`AerospikeService#supportsClassicStringOps`, a constant expression with no data dependency —
  does `"Hello"` match `/.*ell.*/i`?), not a per-request guess. The server accepts the classic-Exp
  regex filter without error and silently matches nothing — even a hotel literally named "Sunset
  Backpackers" queried for "sunset". design.md's own SDK-source notes had already flagged this:
  `contains`/`lower`/`regexCompare` are grouped together as "the 8.1.3 [string ops] surface...
  client-side wiring is ahead of the server release" — these are new server opcodes in their own
  right, not just new AEL grammar. Falls back one level further than every other endpoint: a full
  unfiltered scan, filtered in Java (`SuggestService#scanAndFilterInApp`). Still correct results
  (verified: searching "sunset" returns all 10 real Sunset-prefixed hotel names), just honestly
  not server-side until 8.1.3 — the demo panel says so explicitly rather than implying otherwise.

**One more real bug found along the way:** when no index-selecting or scalar predicate applies at
all (a plain search with only the mandatory date check, which — per above — isn't a classic-Exp
clause), `expClauses` is empty, and `Exp.and()` called with zero clauses was confirmed to
degenerate into a filter matching *nothing* rather than *everything*/*no filter* — total silently
dropped from 900 to 0. `SearchService` special-cases this: an empty clause list runs a plain
unfiltered query instead of `Exp.and()` on nothing.

**When 8.1.3 is available:** none of this needs to change. `supportsAel()` flips to `true`
automatically (derived from the connected cluster's real version), every endpoint above already
prefers the AEL path first, and the classic-Exp/app-layer fallback code simply stops being
reached. The one thing worth re-testing at that point: whether `CdtExp.selectByPath`'s list-leaf
flattening and `StringExp`'s opcodes behave differently on a real 8.1.3+ server, since both
findings above were isolated against 8.1.2.4 specifically.

## Bug found and fixed during frontend integration

**Every POST /search carrying a field this backend's DTOs don't declare returned a raw 500,
not the intended behavior.** The frontend (built concurrently, see `frontend/README.md`
"Deviations") sends speculative `minPrice`/`maxPrice` and `adults`/`children`/`rooms` fields on
`POST /search`, on the documented assumption that "unknown fields are harmless if the backend
ignores them." That assumption was wrong for this backend: Javalin's default Jackson
`ObjectMapper` has `FAIL_ON_UNKNOWN_PROPERTIES` enabled, so any such field threw an uncaught
`UnrecognizedPropertyException`, caught only by the generic `Exception` handler, which returned
`500 {"error":"INTERNAL_ERROR","message":"Unexpected server error."}` — surfaced in the UI as a
bare "unexpected server error" instead of the intended `AEL_UNSUPPORTED` banner. This affected
**every real search from the actual frontend**, not an edge case — the earlier manual `curl`
verification in this file didn't catch it because those test payloads never included the extra
fields the real UI always sends.

Fixed in `HttpApi.start()` by configuring Javalin's `JavalinJackson` with
`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` disabled, globally, rather than annotating
every current and future DTO individually. Confirmed by re-sending the exact request shape the
frontend sends (including `minPrice`/`maxPrice`/`adults`/`children`/`rooms`) and getting the
correct `503 AEL_UNSUPPORTED` back instead of `500`, then re-running the full endpoint regression
suite below to confirm nothing else changed behavior.

## AEL and server version 8.1.2 vs 8.1.3

The Aerospike image this demo ships with today (`aerospike/aerospike-server:8.1.2.4`) does not
support AEL. `AerospikeService.supportsAel()` reads the SDK's own `Cluster.supportsAel()` (a
client-side flag derived from the cluster's minimum server version, no round trip needed), and
every endpoint whose query needs a `.where(String ael, ...)` clause (`POST /search`,
`GET /suggest`) checks it *before* building the query at all, via `AerospikeService.requireAel()`.
When it's false, the endpoint returns:

```json
{"error":"AEL_UNSUPPORTED","message":"This query needs Aerospike server 8.1.3+ for AEL; this cluster is running 8.1.2.4. See docs/design.md.","serverVersion":"8.1.2.4","requiredVersion":"8.1.3"}
```
with HTTP 503. This is logged once at first use (not on every request).

I also confirmed, by actually running a `.where(...)` query against a real
`aerospike/aerospike-server:8.1.2.4` container with the version gate bypassed, exactly what happens
if that gate weren't there — this is the real, load-bearing part for whoever swaps in an 8.1.3+
server later:

```
com.aerospike.client.sdk.AerospikeException$BinOpInvalidException: Error 26,0,0,0,0,0: Aerospike Expression Language (AEL) requires server version 8.1.3+. Server version is 8.1.2.4
    at com.aerospike.client.sdk.AerospikeException.toException(...)
    at com.aerospike.client.sdk.AelMaterializer.expressionFromString(AelMaterializer.java:35)
    at com.aerospike.client.sdk.query.WhereClauseProcessor$WhereStringImpl.toFilterExp(...)
    ...
    at com.aerospike.client.sdk.query.QueryBuilder.execute(QueryBuilder.java:670)
```

Result code **26** (`ResultCode.OP_NOT_APPLICABLE`). Notably this is thrown **client-side**, before
anything goes on the wire — the SDK already knows the cluster's minimum version from cluster tend
and refuses to even try. `AerospikeService.executeAelQuery()` also catches this defensively (in
case a future SDK version narrows exactly what it gates on `supportsAel()`) and re-throws it as the
same `AelUnsupportedException` → 503 response, so nothing changes for callers either way.

**When the Aerospike image is upgraded to 8.1.3+:** nothing in this backend needs to change.
`Cluster.supportsAel()` will start returning `true` automatically (it's derived from the connected
cluster's real version), `requireAel()` becomes a no-op, and `POST /search`/`GET /suggest` will
start actually executing their AEL queries and returning real results instead of 503. Worth then
re-testing the `geoCompare`/`geoJson` AEL path specifically — design.md flags that even this SDK's
own test suite has a `// TODO: Test AEL variant... when it becomes available` next to a
classic-`Exp` geo test, meaning geo-via-AEL specifically hasn't been proven yet anywhere. If it
throws for reasons unrelated to server version, `SearchService`'s geo clause is the one to swap
back to the `Exp.geoCompare(Exp.geoBin(...), Exp.geo(...))` form.

## AEL verified against a real 8.1.3+ server

Everything in the section above documents behavior against the public
`aerospike/aerospike-server:8.1.2.4` image, where AEL genuinely isn't supported. This backend has
also been run against a real server that *does* support AEL server-side — not just with the version
gate bypassed — to confirm the whole stack end to end, not only the fallback path. If you have
access to a server build like that, `local/server.rpm` (see `local/README.md`) is how you point this
repo at it locally; `./demo start` picks it up automatically, and nothing about that setup is
tracked in this repo's history.

Two independent, real findings came out of that testing:

**1. The SDK's `stage` branch moved the AEL version gate from 8.1.3 to 8.2.0** (commit `6222bee`,
"CLIENT-5451 Change server version reference from 8.1.3 to 8.2.0", on the public
`aerospike-client-java-sdk` repo). `stage` is a moving branch by design, and this backend's
Dockerfile cloned it fresh on every build with no pin — so it silently inherited a breaking change
with zero code changes on our side: `Connected to Aerospike at aerospike:3000 (... AEL
supported=false)` against a server that otherwise genuinely supports it. Confirmed by cloning the SDK
and diffing `Version.java` across that commit. **Fixed** by pinning `backend/Dockerfile`'s `SDK_REF`
to a specific commit instead of `stage` — exactly what that `ARG`'s own comment already said to do
"if upstream changes break this build." (`git clone --branch <ref>` only accepts branch/tag names,
not a raw commit SHA, so the Dockerfile clones `stage` with `--depth 50` and checks out `SDK_REF`
from that instead — comfortably past how far a pin is likely to drift before someone updates it.)

**2. Two of design.md's own AEL path-expression examples are invalid syntax**, discovered by
actually running them and reading the server's own parser errors, not by re-reading the docs harder:

- **There is no `..` recursive-descent operator in AEL.** `$.rooms..rates..available...` — used in
  both `SearchService`'s date-coverage clause and design.md's own narrative examples — makes a real
  server reject it outright: `'..' is reserved -- use ':' for ranges (e.g. [1:3])`. The SDK's own
  `docs/ael/*.md` never uses `..` for anything but that `[1:3]` range form: every multi-level example
  in `path-expressions.md` (e.g. `$.store.stationery.*.quantity.*[?(...)]`) chains an explicit `.*`
  per level instead. This was a documentation error, not a server-version gap — it would fail against
  any real AEL-capable server.
- **A bare CDT root bin needs an explicit `:MAP`/`:LIST` type annotation before it can be wildcarded**
  — `$.rooms.*...` alone fails with `unresolved bin type, use $.bin:LIST or $.bin:MAP`; needs
  `$.rooms:MAP.*...`. Only the root needs it — nested steps (`.rates`, `.available`) resolve fine
  once the root is typed. Note this is a *different* spelling than the documented mechanism for type
  ambiguity in `docs/ael/type-inference.md` (`.get(type: MAP)`) — confirmed the `:MAP` shorthand
  directly against the server's own error message rather than assuming the documented form applies
  here too; a bare CDT membership test like `?x in $.amenities` needed no annotation at all, so this
  seems specific to typing a root that gets iterated with `.*`.
- Separately (and correctly *not* "fixed" — see below): **a filter predicate `[?(...)]` must
  directly follow a `*`.** `available.[?(...)]` (a bare field name, then a filter) is a syntax error
  at exactly that position; it needs its own wildcard first: `available.*[?(...)]`.

Corrected `SearchService`'s two AEL-generating lines to
`$.rooms:MAP.*.rates.*.available.*[?(@ >= ?N and @ <= ?M)].count() > 0` and
`$.rooms:MAP.*[?(@.bed == ?N)].count() > 0` — verified via a standalone probe (a throwaway Java
program using this same SDK commit, bypassing the backend/Docker rebuild cycle for faster iteration)
before touching the real source, then re-verified through the actual running backend:
`POST /search` returns real HTTP 200 results for a date-only query, a locality+bed query, and a
geo-radius query — all three previously-distinct code paths design.md's screen-to-query table
describes. **These two fixes are real bugs independent of server version** — kept in the tracked
backend regardless of what server image `docker-compose.yml` points at, since `..` was never valid
AEL to begin with.

**Confirmed, not a bug — a genuinely partial server-side rollout:** `GET /suggest`'s
`$.name.lowercase()...` gets `unknown function 'lowercase'` on every AEL-capable server tested so
far. Isolated with the same probe: `.trim()` works server-side, but `.length()`, `.lowercase()`, and
`.uppercase()` don't yet. This matches this file's own earlier prediction (string ops gate on
CLIENT-4354/CLIENT-4420, "code complete, under review" per design.md) — the client-side `StringExp`
surface is ahead of what's implemented server-side. `/suggest` now falls through to a working
alternative instead of failing outright (see "Fixing hotel-name search's fall-through" below).

**Also confirmed on later testing**: `docker compose up -d --build` recreates a service whose image
changed, but leaves an already-running service whose own config *didn't* change untouched. Swapping
the aerospike image alone this way left `backend` on a stale connection, still reporting the
*previous* server's version and throwing `INTERNAL_ERROR` on every query, until explicitly
force-recreated (`docker compose up -d --force-recreate --no-deps backend`). `./demo start` always
force-recreates everything together, so this only bites if you run raw `docker compose` commands
yourself against an already-running stack. The complete `/search` filter matrix (locality, bed,
price, guests/rooms, geo, and all combined) was re-verified across multiple server builds along the
way — identical result counts every time, confirming the fixes above hold generally rather than
against one specific build.

### Closing the price / guests-and-rooms gap

`SearchService` originally gave price bracket and guests/rooms filters a classic-`Exp` form only —
"no AEL grammar was ever designed for a min-price rank aggregate or occupancy" (see the git history
on this file). That was invisible while this demo only ever ran against 8.1.2.4, but the moment AEL
went live for real (above), it became a genuine, silent bug: `QueryBuilder.where(...)`'s own Javadoc
is explicit that a query carries exactly one filter condition — AEL and classic-`Exp` can't be
layered together — so the AEL branch was applying zero of these two filters. The presenter panel
was already surfacing this honestly (a "have no AEL form... aren't reflected" note), but the actual
search results were wrong, not just under-explained.

Rather than reach for the rank-select syntax (`[#0]`) design.md's own min-price *index* uses, both
turned out to have an exact form as an existence-count — no approximation needed:

- `min(price) >= N  ⇔  no rate has price < N` and `min(price) < N  ⇔  some rate has price < N` — so
  price bracket is `$.rooms:MAP.*.rates.*[?(@.price < ?N)].count() == 0` (at-least) or `> 0` (below),
  filtering the rate object itself, the same shape as the proven bed filter.
- `hasRoomForOccupancy` checks the *largest* room's capacity (rank -1) — but `max(x) >= N ⇔ at least
  one x >= N`, so `$.rooms:MAP.*[?(@.maxOccupancy >= ?N)].count() > 0` is exact, not approximate.
- `roomCountAtLeast` (total room count) turned out simplest of all: `$.rooms:MAP.size() >= N` gave
  "Parameter error" against the real server (`size()` doesn't seem wired up for a bare `:MAP` root
  yet), but counting the wildcard with no filter predicate at all —
  `$.rooms:MAP.*.count() >= N` — worked on the first attempt.

All four verified with the same fast standalone-probe technique as the fixes above (bypassing the
Docker rebuild cycle), then re-verified through the real running backend and, for price
specifically, through the actual frontend: clicking "Under $100" against Austin (630 hotels) now
narrows to 243, with `$.rooms:MAP.*.rates.*[?(@.price < 100)].count() > 0` genuinely present in the
presenter panel's query text — not a note explaining why it's missing. The now-obsolete gap-warning
(`warnAelGapIfNeeded` and the matching presenter-panel note) were removed rather than left dormant.

## Voyager's expression editor doesn't support AEL path expressions (2026-09-16)

Pasting this backend's real, working, presenter-panel AEL queries into Aerospike Voyager (v0.2.6,
the latest available — [release notes](https://aerospike.com/docs/database/tools/voyager/release-notes)
confirm no version has specifically announced path-expression support, though 0.2.2's notes claim
"the full Aerospike Expression Language (AEL) surface, including CDT operations") fails there,
independent of anything in this repo. Isolated with the same bisection method used against the real
server earlier in this file:

- A bare scalar comparison (`$.rating >= 80`) works fine in Voyager's expression box.
- Adding a wildcard (`$.rooms.*.count() >= 1`, no `:MAP` even) fails: `no viable alternative at
  input '$.rooms.*'`. The `:MAP`/`:LIST` annotation this backend needs (see "AEL verified against a
  real 8.1.3+ server" above) was never reached — the wildcard itself isn't parseable.
- Voyager's own error text lists `Supported operators: =, !=, >, <, >=, <=` with no `==` — but
  don't take an error message's own claimed scope at face value over an actual test: `$.locality ==
  'austin-downtown'` (real `==`, straight from a presenter-panel query, just trimmed of everything
  after it) works fine in Voyager too. So the operator list in that error text is incomplete, not
  authoritative — the real boundary confirmed so far is flat `$.binName op value` scalar comparisons
  (equality included) work; wildcards, path navigation, and filter predicates (`[?(...)]`) don't.

**Not a bug here, and not fixable here** — same "client tooling behind a brand-new server feature"
shape as the SDK's `stage` branch gate (above), except this time in a separate Aerospike product
this repo doesn't control. Practical implications for anyone giving this demo:

- Step 7 ("Close the loop in Voyager," design.md) opens a record by key to confirm a booking landed
  — no AEL filtering involved, so that part of the demo is unaffected.
- Don't expect to paste a presenter-panel query into Voyager whole and have it run — every real
  `/search` call includes the mandatory date-coverage clause (`$.rooms:MAP.*.rates.*.available.*
  [?(...)].count() > 0`, checkIn/checkOut being the only two required fields on the endpoint), so
  literally no combination of filters produces a wildcard-free string. There is no search to run
  from the site that sidesteps this — it's structural, not a matter of which filters are picked.
- The workable version of this demo beat: copy the real AEL from the presenter panel, then trim it
  by hand down to just the leading flat clause before pasting into Voyager — confirmed working for
  locality/city/propertyType (`==`) and rating (`>=`). Honest framing for a live demo: "here's the
  real generated query, and here's the piece of it Voyager can already run today" — not a claim
  that the whole thing pastes in as-is.
- Worth periodically checking Voyager's release notes for path-expression support landing, the same
  way the SDK pin above will need bumping again someday.

## Fixing a self-contradictory error message (2026-09-16)

Found live, by someone actually using the demo, not by re-reading code: hitting the hotel-name
typeahead produced this from `/suggest`:

```json
{"error":"AEL_UNSUPPORTED","message":"This query needs Aerospike server 8.2.0+ for AEL; this cluster is running 8.2.0.0.","serverVersion":"8.2.0.0","requiredVersion":"8.2.0"}
```

Read literally, that says the requirement is already met (`8.2.0.0` satisfies `8.2.0+`) and still
fails — which is exactly what it was doing, and exactly why it read as broken. The root cause:
`AelUnsupportedException` was thrown from two genuinely different places with an identical message
(see `requireAel()` vs `executeAelQuery()`'s catch block in `AerospikeService.java`) —

1. **The version gate genuinely fails** (`requireAel()`): the cluster's minimum version really is
   below what's needed. A version bump actually fixes this one.
2. **The version gate passes, but the server rejects this specific query anyway**
   (`executeAelQuery()`'s defensive catch): `/suggest`'s `lowercase()` is the confirmed example —
   happens on every AEL-capable server tested so far, regardless of version, because it's a missing
   function, not a version floor. A version bump does *not* fix this one.

Both were reported with the same "needs version X+" wording, which is only true for case 1.
**Fixed** by adding `AelUnsupportedException.versionGateFailed` (`true` for case 1, `false` for
case 2), threading it through the HTTP body (`versionGateFailed` field, alongside a message that's
now accurate for whichever case actually happened), and having
`frontend/src/components/AelUnsupportedBanner.tsx` only show the "Cluster version: X · Requires:
Y+" comparison line when it's actually case 1 — that line is exactly what read as self-contradictory
for case 2, since the versions genuinely did match.

Verified via curl (`/suggest?q=lake` now returns the accurate case-2 message and
`"versionGateFailed":false`) and live in the browser — with one unrelated speed bump along the way:
the browser tab initially kept rendering the *old* banner text even after the rebuild, because it
had a cached `index.html` pinned to the previous JS bundle hash; a cache-busted reload
(`?_cb=`) confirmed the fix was correct all along and the stale render was purely a browser-cache
artifact, not a code issue.

## Fixing hotel-name search's fall-through (2026-09-16)

Explaining the error message above led to a bigger find: hotel-name search (`/suggest`) wasn't
degraded, it was **completely broken** — every request 503'd, zero results, ever — even though a
working fallback sat a few lines away in the same file the whole time.

`SuggestService` was written with three tiers (AEL → classic Exp regex → app-layer scan), each
meant to catch what the one before it couldn't. But the branching only *chose* a tier once, up
front, based on `aerospike.supportsAel()` — it never caught a rejection *from within* the chosen
tier and moved to the next one. Once this cluster's version genuinely satisfied `supportsAel()`,
every request took the AEL branch, AEL's `lowercase()` failed (the confirmed, real gap from
"AEL verified against a real 8.1.3+ server" above), and the exception propagated straight to a 503
— the two working tiers below it never ran.

**Fixed** by catching `AelUnsupportedException` around just the AEL attempt and falling through to
the next tier instead of letting it escape, then re-checking `results == null` before each
subsequent tier rather than the original single `if`/`else if`/`else`. This also surfaced a genuine
bonus finding: `AerospikeService#supportsClassicStringOps()` — the probe for tier 2 — returns
**true** on a newer AEL-capable server. design.md and this file's own earlier notes only ever
confirmed it `false` against 8.1.2.4 (the classic regex filter silently matching nothing); on 8.2.0 it actually
works, so hotel-name search now lands on tier 2 (a real server-side classic Exp filter), not tier 3
(the app-layer scan) — confirmed by the demo panel's own `queryMechanism` field, not assumed.

Verified live: typing "lake" now returns ten real hotel names (Lakeside Court, Suites, Motel,
Resort, Plaza, House, Backpackers, ...) in the actual typeahead dropdown, where it previously showed
only the error banner.

## What wasn't done

- No automated test suite (JUnit) — verification was end-to-end manual testing against real
  Aerospike containers, documented below. Given the time budget, this was prioritized over unit
  tests for a demo backend whose correctness-critical logic (index creation, the booking
  concurrency check, the AEL gate) was each specifically exercised for real.
- `hotel-minprice-idx` is created and verified healthy, but no `/search` request parameter actually
  filters or sorts by price — the API contract's `SearchRequest` shape doesn't define one. The
  index still exists and is reported by `/health`.
- Rate limiting, auth, and TLS are all absent, matching the rest of this appliance (single-node,
  no auth, LAN/laptop demo use only — see `aerospike/aerospike.conf`'s own comments).

## Verified end-to-end

All of the following were actually run, not just written:

```bash
# Build the preview SDK's client module and install it locally
git clone --branch stage --depth 1 https://github.com/aerospike/aerospike-client-java-sdk /tmp/sdk
mvn -q -pl client -am install -DskipTests -f /tmp/sdk/pom.xml

# Compile + package this backend
cd backend && mvn -q package -DskipTests

# Build the Docker image (from repo root, as specified)
cd .. && docker build ./backend -t booking-backend

# Stand up a real, unmodified 8.1.2.4 server and run the built image against it on a private
# docker network (aliased "aerospike" so AEROSPIKE_HOST=aerospike resolves exactly like it does
# under docker-compose), mounting the real data/ and scenarios/ directories read-only:
docker network create demo-test-net
docker run -d --name as-test --network demo-test-net --network-alias aerospike \
  -p 3010:3000 aerospike/aerospike-server:8.1.2.4
docker run -d --name backend-test --network demo-test-net \
  -e AEROSPIKE_HOST=aerospike -e AEROSPIKE_PORT=3000 -e AEROSPIKE_NAMESPACE=test \
  -e DATA_DIR=/data -e SCENARIOS_DIR=/app/scenarios \
  -v "$(pwd)/data:/data:ro" -v "$(pwd)/scenarios:/app/scenarios:ro" \
  -p 8092:8081 booking-backend
```

Then, against that running container:

- `GET /health` → `{"status":"ok","recordCount":900,"indexes":[...all four "RW"...]}` — confirmed
  data load (900 records from the real `data/hotels.ndjson`) and all four secondary indexes
  reaching `RW`, both on first boot (empty set → load) and on a restart against an already-loaded
  set (skips reload, just re-verifies indexes).
- `docker exec backend-test wget -qO- http://localhost:8081/health` — the exact command
  `docker-compose.yml`'s healthcheck runs — succeeded (confirms `wget` is actually present in the
  final Alpine image).
- `GET /landmarks` → all 8 landmarks from the real `data/landmarks.json`.
- `GET /hotels/{id}` → full property detail with rooms/rates correctly filtered to the requested
  date window; `GET /hotels/NOPE` → `404`.
- `POST /bookings` for an available room/rate/date → `201` with a `reservationId`, and the booked
  day moved from `available` to `booked` on read-back. Immediately repeating the identical booking
  request → `409 ROOM_NOT_AVAILABLE`, with `available`/`booked` left correctly unchanged (not
  double-decremented) — the core concurrency trap design.md calls out.
- `POST /bookings` against an unknown hotel/room → `404`.
- `POST /search` and `GET /suggest` → `503 AEL_UNSUPPORTED` with the documented JSON body, since
  the test server is 8.1.2.4 (see previous section for the underlying exception this reproduces).
- `POST /admin/reset` → truncates and reloads in a few seconds, restoring the booking made above
  (verified by reading the room back afterward), and `/health`'s `recordCount` reads correctly
  right away (see deviation 5).
- `POST /admin/seed` → same reload path, `200 {"status":"ok"}`.

Test containers/networks were torn down after (`docker rm -f`, `docker network rm`) — nothing from
this verification was left running.
