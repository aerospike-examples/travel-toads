# Aerospike DX demo — booking site (Java SDK, AEL, Path Expressions)

A booking-site demo showing developer-experience gains from three Aerospike investments together:
the new Java SDK, the Aerospike Expression Language (AEL, GA in server 8.2), and Path Expressions
(GA in 8.1.2).

Full design — data model, index design, screen-to-query mapping, and the demo narrative — lives in
[`docs/design.md`](docs/design.md).

## Status

- [x] Design doc captured (`docs/design.md`)
- [x] Dataset generator (`datagen/`) — produces hotels + landmarks matching the documented schema
- [x] Docker Compose packaging, `./demo` wrapper (start/reset/seed/status/logs/stop), Aerospike
      config, and the five scenario definitions (`scenarios/*.json`) shared by backend and frontend
- [x] Backend (Java, aerospike-client-java-sdk `stage` branch: connect, create indexes, load, the
      demo-narrative queries) — see `backend/README.md`
- [x] Frontend (React + Vite: search/results/detail/booking, Demo/Engineering mode panels) — see
      `frontend/README.md`
- [x] **`./demo start` verified end-to-end** on this machine: real `docker build` for all three
      images, real data load (900 hotels), all four indexes reaching RW, a real booking + a real
      409 on double-booking with state left correct, and `./demo reset` restoring it in ~5.5s.
      Three integration bugs were found and fixed in the process — see "Integration fixes" below.
- [x] **Real server-side AEL, verified against the public 8.2.0.0 GA release** — `docker-compose.yml`'s
      tracked default. `POST /search` returns real HTTP 200 results via genuine server-side AEL
      execution across every query shape, with no preview build and no manual setup; found and fixed
      two real, version-independent bugs along the way (an invalid `..` operator design.md's own
      examples used, and a missing `:MAP` type annotation) — both fixes are in the tracked backend
      and `docs/design.md` now. Also found the SDK's `stage` branch had quietly bumped its AEL
      version requirement from 8.1.3 to 8.2.0 mid-project — `backend/Dockerfile` now pins a commit
      instead of tracking `stage`. See `backend/README.md`'s "AEL verified against a real 8.1.3+
      server" for the full writeup, and `local/README.md` if you need to point at a build newer or
      different than the tracked default.
- [x] **A classic Exp-tree fallback still runs underneath, as a safety net, not the primary path.**
      Every predicate has a non-AEL equivalent, verified correct and cross-checked against
      `data/stats.json` — this is what kept `/search` and `/suggest` returning real, indexed-or-full-scan
      results throughout development even when the server underneath briefly didn't support AEL (a
      moving `stage` SDK version gate, or an older image). One confirmed, genuinely partial
      server-side gap persists even on 8.2.0.0 GA: `GET /suggest`'s AEL `lowercase()` isn't
      implemented server-side yet (`trim()` works), so name search falls through to this tier
      automatically — see `backend/README.md` for the full writeup.
- [x] **Price and guests/rooms filters closed the same gap.** Both originally had a classic-`Exp`
      form only — invisible pre-8.1.3, but a real, silently-dropped-filter bug once AEL went live,
      since a query can only carry one filter condition (AEL or classic, never both). Both turned
      out to have an _exact_ AEL form as an existence-count (`min(price) >= N ⇔ no rate < N`,
      `max(occupancy) >= N ⇔ some room >= N`), verified the same way as above and confirmed live in
      the frontend ("Under $100" on Austin: 630 → 243 hotels, with the price predicate genuinely in
      the displayed query). See `backend/README.md`'s "Closing the price / guests-and-rooms gap".
- [ ] Video / blog / short-form / SE pre-canned demos

**AEL is live by default, not a limitation anymore.** The demo's queries are written in AEL
(Aerospike Expression Language) per design.md, which needs server 8.1.3+; `docker-compose.yml`'s
tracked default is now the public `aerospike/aerospike-server:8.2.0.0` GA image, so a plain clone
gets real, indexed AEL execution out of the box — no preview build, no manual version check, no
code change required. The classic Exp-tree fallback described above still runs underneath as a
safety net (and is what keeps name search working around the one confirmed server-side gap), but
it's no longer the primary path a fresh clone takes. Booking (plain CDT `operate()`) and index
creation never needed AEL and have worked since the start.

## Running it

```bash
./demo start
```

See `./demo` (no args) for the full command list (`start`/`reset`/`seed`/`status`/`logs`/`stop`).

## Repo layout

```text
docs/design.md         — the design doc (source of truth for data model, indexes, query shapes)
datagen/               — Maven project; generates data/hotels.ndjson, data/landmarks.json, data/stats.json
data/                  — generator output (gitignored — regenerate with datagen, don't commit)
aerospike/aerospike.conf — single-node server config (access-address / alternate-access-address
                           split so both the backend, over the compose network, and host-side
                           tools like Voyager, via 127.0.0.1, can reach it — see the file's header)
scenarios/*.json       — canonical scenario definitions (query shape + narration) shared by the
                          backend's API responses and the frontend's Demo/Engineering panels
scripts/{start,reset,seed,health} — implementation behind ./demo's subcommands
demo                   — the SE-facing entry point: ./demo start|reset|seed|status|logs|stop
docker-compose.yml     — wires aerospike + datagen (one-shot) + backend + frontend together
backend/               — Java service (aerospike-client-java-sdk, stage branch) — see backend/README.md
frontend/              — React booking UI — see frontend/README.md
```

## Decisions locked in so far

- **Backend language: Java SDK** (per design.md's "Verified against the SDK source" section — the
  demo's load-bearing findings, like AEL requiring server-side planning, were confirmed against
  `aerospike-client-java-sdk` branch `stage`, commit `c1f3cc0`).
- **Repo: new repo**, not a fork of `path-expressions-java-preview`'s `booking/` folder — no local
  checkout of the reference repos was available when this was started.
- **SDK version: pinned to a fixed commit on the `stage` branch**, not the branch tip (no tagged
  release matches what design.md's code was verified against — the latest tag, `v0.9.0-alpha.2`, is
  older than `stage`). Originally tracked `stage` directly; switched to a pin after `stage` silently
  bumped its AEL version requirement from 8.1.3 to 8.2.0 (see `backend/README.md`'s "AEL verified
  against a real 8.1.3+ server"). Built from source in `backend/Dockerfile`; there's no Maven Central
  artifact yet.
- **Server version: public 8.2.0.0**, the first GA release with AEL and Path Expressions built in
  (AEL itself needs 8.1.3+ at minimum). Built and tested directly against it — see the Status
  section above.
- **Frontend stack: React + Vite + TypeScript**, plain CSS, no component-library dependency.

## Integration fixes

Found and fixed while running `./demo start` for real, after the backend and frontend (built
concurrently against a shared contract) both landed:

- **`aerospike/aerospike.conf` was mounted at the wrong path.** The official server image's
  entrypoint always eval-templates `/etc/aerospike/aerospike.template.conf` ->
  `/etc/aerospike/aerospike.conf` on every start (baked into the image), and needs to write that
  target itself. Mounting our config directly at the target path made that write fail with
  "Read-only file system". Fixed by mounting it as the _template_ instead
  (`docker-compose.yml`'s `aerospike` service) — our config has no `${...}`/`$(...)` sequences, so
  the eval-template step is a harmless no-op copy.
- **Missing `cluster-name`.** This server version refuses to start without one configured; added
  `cluster-name aerospike-dx-demo` to `aerospike/aerospike.conf`'s `service {}` block.
- **`datagen/Dockerfile`'s runtime base image had no arm64 build.** `eclipse-temurin:17-jre-alpine`
  is amd64-only (confirmed via `docker manifest inspect`) — fails outright on Apple Silicon.
  Bumped to `eclipse-temurin:21-jre-alpine` (multi-arch, already what `backend/Dockerfile` uses); a
  jar compiled for release 17 runs fine on a 21 JRE.
- **Every real search returned a raw 500, not the intended `AEL_UNSUPPORTED` banner.** The
  frontend's speculative extra fields on `POST /search` (`minPrice`/`maxPrice`, later
  `adults`/`children`/`rooms` for the guests-and-rooms picker) were assumed harmless if the
  backend ignored them — but Javalin's default Jackson mapper rejects unrecognized JSON fields
  outright. Fixed by disabling `FAIL_ON_UNKNOWN_PROPERTIES` globally in `HttpApi.start()`; see
  `backend/README.md`'s "Bug found and fixed during frontend integration" for the full story.
- **Frontend/backend contract mismatches**, found by reading the real backend source against the
  frontend's documented assumptions (see `frontend/README.md`'s "Resolved during backend
  integration" section for the full list): `checkIn`/`checkOut` needed to be the YYYYMMDD integer
  the backend actually expects, not ISO date strings (fixed at the network boundary only, in
  `frontend/src/api/client.ts`); `propertyType`/`bed` filter values were corrected against the real
  generator output (`bed_and_breakfast` not `bnb`; no `single` bed, generator also produces
  `sofa_bed`/`bunk`); and `POST /bookings` now sends `demo: true/false` so its confirmation-screen
  demo panel actually populates (the backend does support that field — the frontend's earlier note
  that it didn't was based on a wrong assumption).
- **No preflight check for `docker compose` (v2) itself, and the failure mode when it's missing is
  actively misleading.** `./demo start`/`logs`/`stop`/`status`/`seed` all shell out to
  `docker compose ...`. A real user (Homebrew's plain `docker` formula, no Docker Desktop, no
  `~/.docker/cli-plugins/` directory at all) hit this: with the compose plugin absent, this Docker
  CLI version (29.x) doesn't fail with the expected `'compose' is not a docker command` — it
  misparses the _next_ argument as a root-level flag, so `docker compose up -d --build` dies with
  `unknown shorthand flag: 'd' in -d` against `docker`'s own usage banner. Reproduced locally by
  temporarily removing `~/.docker/cli-plugins/docker-compose` and confirming the identical error
  (then restoring it) — this is a genuine Docker CLI quirk, not our script or a shell alias. Fixed
  by adding a `docker compose version` preflight check (in `demo` and in
  `scripts/start`/`scripts/seed`) whose failure message gives the exact remediation for the
  Homebrew case: `brew install docker-compose` doesn't symlink the plugin into
  `~/.docker/cli-plugins/` on its own, so `docker compose` stays unresolvable even after installing
  it, until you `ln -sfn "$(brew --prefix)/opt/docker-compose/bin/docker-compose"
~/.docker/cli-plugins/docker-compose` yourself.

## Open decisions (see design.md's "Open items" for the full list)

- Property image licensing — the frontend currently ships real stock-photo crops (cut from mosaics
  dropped at the project root; see `frontend/src/lib/hotelPhoto.ts`) with no license resolved.
  Accepted as a known risk for now; revisit properly before anything public.
- Room-count range: `datagen/README.md` documents a real record-size finding — the documented
  10-40 room range pushes ~27% of hotels over the 128 KiB "sweet spot" with full amenity/booking
  detail. Needs a decision before this is treated as validated.
- Naming/quote clearance from product marketing for the preview customer this demo's data model is
  loosely based on — blocks anything this demo produces from naming them (it currently doesn't, and
  shouldn't until cleared).
