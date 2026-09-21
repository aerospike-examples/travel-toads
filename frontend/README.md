# Aerostay — frontend

A booking-site frontend (React + Vite + TypeScript, plain CSS) that doubles as a live
demonstration of the Aerospike queries running behind each screen. See
`../docs/design.md` for the why; this README covers how to run and verify this
`frontend/` directory on its own, plus everything that had to be guessed or
adapted because the backend was being built concurrently against the same spec.

The brand name "Aerostay" is this frontend's own invention — a generic, fictional
booking site name, unrelated to any real company. No confidential naming from
`docs/design.md` appears anywhere in this directory.

## Running standalone

```bash
npm ci
npm run dev          # http://localhost:5173, proxies /api -> http://localhost:8081
```

`vite.config.ts`'s dev proxy forwards `/api/*` to `http://localhost:8081` and
**strips the `/api` prefix** before forwarding (see "The `/api` prefix" below).
Point it at a different backend by editing the `target` in `vite.config.ts`, or
run `docker compose up backend aerospike datagen` from the repo root and let
this dev server talk to the backend published on `localhost:8081`.

Build and preview the production bundle:

```bash
npm run build        # tsc -b && vite build -> dist/
npm run preview
```

Build and run the standalone Docker image (same as docker-compose's `frontend`
service, but reachable directly):

```bash
docker build -t aerostay-frontend ./frontend
docker run --rm -p 8080:80 --network <compose-network> aerostay-frontend
```

It must run on the same Docker network as a container reachable at hostname
`backend` on port `8081` — that's what `nginx.conf` proxies `/api/` to,
matching docker-compose's service name. Standalone (no compose network), the
container serves the static app fine but every `/api/*` call will fail until
nginx can resolve `backend`.

## How every screen maps to the backend contract

- Search/landing + results: one page (`src/pages/SearchResultsPage.tsx`) —
  search bar + neighborhood chips + filter sidebar + result grid all update
  live against `POST /search` as the user changes anything, per design.md's
  "collapsible panel...updating live as filter chips are toggled" framing.
- Property detail: `src/pages/HotelDetailPage.tsx`, backed by
  `GET /hotels/{id}`.
- Booking: a modal (`src/components/BookingModal.tsx`) over the detail page,
  `POST /bookings`, landing on `src/pages/BookingConfirmationPage.tsx`.
- The Off/Demo/Engineering mode switch and the "↻ Reset Demo" button live in
  `src/components/Header.tsx`, wired through `src/context/DemoModeContext.tsx`
  (persisted to `localStorage`). The collapsible query panel is
  `src/components/DemoPanel.tsx` — one component, rendered near the search
  results, the room list, and the booking confirmation; Engineering mode
  expands the same panel rather than swapping to a different UI.
- The `AEL_UNSUPPORTED` (503) state is `src/components/AelUnsupportedBanner.tsx`,
  handled uniformly by `src/api/client.ts`'s `ApiResult` wrapper for
  `/search`, `/suggest`, and `/hotels/{id}`.

## How I tested this without a live backend

The real backend was being built in parallel, so I wrote a throwaway Node
`http` mock (no framework) implementing every documented endpoint and status
code against fixture data (48 generated hotels across Austin / Round Rock /
San Marcos, matching design.md's data model), and drove the app through it
with Playwright (`chromium.launch()` + a scripted click-through, screenshots
saved and inspected). It is **not** part of this repo — it lived in my scratch
directory — since the brief said "hardcoded fixture JSON matching the exact
shapes" was sufficient and didn't ask for it to be committed. If it's useful
to keep around, it's small enough to recreate from the endpoint list above;
notable fixture behaviors I built in specifically to exercise every state:

- A specific landmark (`aus-stadium` in my fixture) always returned 503
  `AEL_UNSUPPORTED`, to check the banner.
- A specific `propertyType`+`minRating` combination always returned zero
  results, to check the empty state.
- One hotel ID always returned 503 on `GET /hotels/{id}`.
- A Playwright `page.route()` intercept forced one `POST /bookings` call to
  return 409, to check the "someone else just booked that night" flow
  end-to-end (guest fills the form, submits, sees the inline conflict banner,
  room list refetches).

I confirmed all of the following actually render correctly (not just "should
build"): the default landing/results grid, Demo mode's query panel, Engineering
mode's expanded panel, a neighborhood-chip search (locality index instead of
geo index, same panel shape — the design doc's "index-selection beat"), the
AEL_UNSUPPORTED banner, a zero-results state, the property detail page's room
list with per-room availability, the booking modal, a successful booking's
confirmation screen (including its own demo panel for the `ael: null` /
`relevantCode`-only booking scenario), a 409 booking conflict, Customer mode
showing zero technical UI, and the name-search typeahead dropdown.

Verification commands actually run, in order:

```bash
npm run build                          # tsc -b && vite build — must succeed
npm run lint                           # oxlint — only benign fast-refresh /
                                        # set-state-in-effect warnings remain
docker build ./frontend                # from repo root — must succeed
# then the mock-backend + Playwright click-through described above, plus:
docker network create aerostay-net
docker run -d --name backend --network aerostay-net <mock-backend-image>
docker run -d --network aerostay-net -p 8099:80 aerostay-frontend
curl http://localhost:8099/api/landmarks     # proxy reaches the backend
curl http://localhost:8099/hotels/HTL-1000   # SPA fallback serves index.html
docker restart backend && curl http://localhost:8099/api/landmarks
                                        # confirms nginx re-resolves `backend`
                                        # after it restarts with a new IP
```

## Deviations from the brief, and why

**No price field in the documented `POST /search` body, but the brief asks for
a price filter sidebar.** The exact JSON contract in the brief lists
`landmarkId, radiusMiles, locality, checkIn, checkOut, minRating, propertyType,
bed, amenities, page, pageSize, demo` — no price field — while the prose says
the filter sidebar's price control should become "an additional POST /search
body field." I implemented the price bracket UI (`Any / Under $100 / $100–200
/ $200–300 / $300+`) and: (1) send it speculatively as extra `minPrice`/
`maxPrice` fields on the request (harmless if the real backend ignores unknown
fields), and (2) apply the same bracket as a client-side filter over whatever
page of results comes back, so the control does something even if the backend
never looks at those fields. **If the real backend adds a price field with
different names, update `SearchRequest` in `src/api/types.ts` and the two spots
in `src/pages/SearchResultsPage.tsx` that read `PRICE_BRACKETS`.**

**The `/api` prefix.** The brief says the frontend calls same-origin `/api/*`,
proxied to the backend. docker-compose separately publishes the backend
directly on host port 8081 "for direct API/curl debugging" — which only makes
sense as its own bare paths (`/landmarks`, not `/api/landmarks`) if the backend
doesn't natively serve an `/api` prefix. I therefore assumed nginx's
`proxy_pass http://backend:8081/;` (trailing slash) strips `/api` on the way
to the backend, and mirrored that in the Vite dev proxy with an explicit
`rewrite`. **If the real backend actually expects the `/api` prefix itself**,
delete the `rewrite` in `vite.config.ts` and change `nginx.conf`'s `location
/api/` block to stop stripping the prefix (both are commented at the exact
line to change).

**`locality` slug reconstruction.** Neighborhood chips need to send a
`locality` string (design.md's example: `"austin-downtown"`), but no endpoint
returns that slug directly — `/search` results and `/landmarks` only expose
`city`/`neighborhood` separately. `src/lib/format.ts`'s `toLocalitySlug()`
guesses `slugify(city) + "-" + slugify(neighborhood)`. This matches the one
documented example exactly, but design.md doesn't specify multi-word
normalization rules (e.g. whether "The Domain" drops "the"). If the real
backend normalizes differently, neighborhood-chip search will silently return
zero results for the affected neighborhoods — fix in one place
(`toLocalitySlug`).

**`checkIn`/`checkOut` sent as ISO date strings (`"2026-09-16"`), not the
internal `YYYYMMDD` integers** that `docs/design.md` uses for the stored
`rates[].from/to/available` day values. The brief's endpoint signatures don't
state a wire type for these; I used ISO strings as the more conventional REST
choice for a request parameter (query string and JSON body), while still
treating the `from`/`to`/`available` values _returned inside_ `rooms` as the
day-integer format design.md specifies (see `isoToYmdInt`/`formatYmdInt` in
`src/lib/format.ts`). If the backend instead expects `checkIn`/`checkOut` as
integers too, swap the two call sites in `src/pages/SearchResultsPage.tsx` and
`src/pages/HotelDetailPage.tsx`/`src/components/BookingModal.tsx` to send
`isoToYmdInt(...)`.

**Guessed enum values.** `propertyType` (`hotel, hostel, motel, apartment,
resort, bnb`) and `bed` (`king, queen, double, twin, single`) aren't enumerated
in the brief; these are inferred from design.md's sample record and its
"seed data must vary" section (six property types). Wrong or missing values
here just mean a filter chip that never matches anything — swap the lists in
`src/lib/constants.ts`.

**`POST /bookings` has no `demo` field in its documented request body**
(unlike `/search`, `/suggest`, `/hotels/{id}`, which all take an explicit
`demo: boolean`). So the frontend has no way to _ask_ for a demo block on a
booking — `BookingRequest` in `src/api/types.ts` matches the documented shape
exactly (no `demo` field sent). The confirmation page's demo panel just
renders whatever `demo` value the response happens to contain, gated on the
local UI mode; if the real backend only attaches a booking demo block when
some other signal says "demo mode," the panel will only show when that
condition holds. No frontend change needed either way — this is a note for
whoever owns the backend more than a deviation on our side.

**Neighborhood chip list.** Seeded with design.md's confirmed Austin list
(Downtown, South Congress, East Austin, Zilker, Rainey Street, The Domain) and
then extended live with whatever `city`/`neighborhood` pairs show up in actual
`/search` results (so Round Rock/San Marcos neighborhoods appear once real
data exists) — design.md explicitly flags the suburb neighborhood names as
"not sanity-checked," so nothing for those two cities is hardcoded.

**nginx `proxy_pass` with a variable.** To keep nginx from caching the
`backend` container's IP for its whole process lifetime (a real Docker
gotcha — if `backend` restarts with a new IP, a static `proxy_pass
http://backend:8081/;` would keep proxying to the dead address until the
frontend container itself restarts), `nginx.conf` uses a `resolver
127.0.0.11` plus a `$backend_upstream` variable so nginx re-resolves the
hostname per-request. Using a variable in `proxy_pass` disables nginx's usual
"replace the matched location prefix" behavior, though — I hit this while
verifying the build (the backend was receiving bare `GET /` instead of
`GET /landmarks`) and fixed it with an explicit `rewrite ^/api/(.*)$ /$1
break;` ahead of the `proxy_pass`. Verified by restarting the mock `backend`
container mid-session and confirming `/api/*` calls kept working.

**docker-compose.yml.** No changes were needed — the existing `frontend`
service (`build: ./frontend`, port `8080:80`, `depends_on: backend:
condition: service_healthy`) matches what's actually built here exactly.

## Resolved during backend integration

Checked against the real backend once both landed:

- **`checkIn`/`checkOut` wire format**: confirmed the backend expects the YYYYMMDD integer
  (`long checkIn` in `SearchRequest.java`/`BookingRequest.java`, `Long.parseLong(...)` on the
  `GET /hotels/{id}` query params) — ISO strings would have failed every request. Fixed at the
  network boundary only: `api/client.ts`'s `search()`/`getHotel()`/`createBooking()` now convert
  with `isoToYmdInt()` right before sending; every internal call site still uses ISO strings
  (unchanged) since that's what date inputs/formatting/`nightsBetween`/`coverageCount` want.
- **`propertyType`/`bed` enums**: corrected against the actual generator
  (`datagen/.../HotelGenerator.java`) — `bed_and_breakfast` not `bnb`, and bed types are
  `king/queen/twin/double/sofa_bed/bunk`, not `single` (which the generator never produces).
- **`locality` slug guess**: confirmed correct as-is against real generated data
  (`data/stats.json`'s `hotelsPerLocality` keys, e.g. `austin-the-domain`,
  `round-rock-la-frontera`) — no change needed.
- **`/api` prefix stripping**: confirmed correct — the backend serves bare paths.
- **`BookingRequest`'s `demo` field**: the backend's `BookingRequest.java` does have one (default
  `false`) — this README's earlier claim that it didn't was checked against the wrong assumption.
  Added `demo: boolean` to the frontend's `BookingRequest` type and wired `BookingModal` to send
  the current `useDemoMode().demoFlag`, so the confirmation screen's demo panel now actually
  populates in Demo/Engineering mode.
- **Price field and guests/rooms — now real, server-side.** Both were originally sent speculatively
  with no backend-side effect (documented in "Deviations" below at the time). The backend later
  added real classic-Exp filters for both (see `backend/README.md`'s "Getting real results without
  8.1.3"), verified against ground truth computed directly from `data/hotels.ndjson`. The
  client-side price-bracket filter this page used to apply on top of the results is gone —
  redundant now that the server does it for real, and risked silently disagreeing with the
  server's count if the two bracket boundaries ever drifted apart.

## What's intentionally not here

- **No property photos** — `docs/design.md` calls this an open item (licensing
  / repo size). Result cards use CSS-only gradients + subtle patterns,
  color- and pattern-coded by `propertyType`, instead of broken `<img>` tags
  or gray boxes.
- **No component library** — plain CSS per-component (colocated `.css` files),
  design tokens in `src/index.css`.
- **No test framework wired up** — verification was manual (build + lint +
  Docker build + a scripted Playwright click-through against a fixture
  backend, described above), per the brief's "before you consider this done"
  checklist. If this repo wants automated tests going forward, Vitest +
  React Testing Library would be the natural fit for this stack; nothing here
  precludes adding it later.
