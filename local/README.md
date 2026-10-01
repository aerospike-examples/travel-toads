# local/ — pointing the demo at a different server build

`docker-compose.yml`'s default (`aerospike/aerospike-server:8.2.0.0`) is the first public GA release
with AEL and Path Expressions built in, so **this mechanism is no longer needed to make AEL work** —
a plain clone already gets real AEL out of the box. It's still useful if you need to test against a
newer or internal build ahead of whatever's in `docker-compose.yml` (a preview point release, a fix
for a specific bug, etc.) without touching that tracked default.

## Setup

1. Download the RPM matching your machine's architecture from the internal artifact repo
   (check with `uname -m`: `arm64` → the `aarch64` RPM, `x86_64` → the `x86_64` RPM).
2. Place it here as `local/server.rpm` (rename it — the filename doesn't matter beyond that).
3. `./demo start`.

That's it — no other files to create or edit. `scripts/start` detects `local/server.rpm` on its
own and wires up everything needed (see its own comment on this, and `Dockerfile` in this
directory) the first time you run it. Delete `local/server.rpm` (or the auto-generated
`../docker-compose.override.yml`) to fall back to the public image, with nothing else to undo.

Upgrading to a newer internal build later is the same step 2, repeated: overwrite
`local/server.rpm` and run `./demo start` again. **One gotcha worth knowing** if the stack is
already running when you do this: swapping the aerospike image alone doesn't recreate an
already-running `backend` container (compose only recreates services whose own config changed), so
it's left on a stale connection reporting the previous server's version until you also run
`docker compose up -d --force-recreate --no-deps backend`. A fresh `./demo start` (nothing running
yet) doesn't hit this — every service starts together.

## Why this directory looks the way it does

`Dockerfile` (this directory) and this README are ordinary tracked files — there is nothing
confidential in "build an image from whatever RPM shows up at a fixed path." The only things that
are gitignored are `local/*.rpm` (the actual unreleased binary) and `../docker-compose.override.yml`
(auto-generated, and the mechanism by which this is opt-in — its absence is what keeps a plain
clone's default experience on the public image, untouched). Never commit either, even though the
`.gitignore` already blocks it — that's what keeps an unreleased server build from ever reaching a
public repo through this path.

The image built here runs `asd` directly against `/etc/aerospike/aerospike.conf` — no vendor
entrypoint/templating step like the public `aerospike/aerospike-server` image needs (see
`../docker-compose.yml`'s comment on that image's mount), and no `asinfo` (it ships separately, in
`aerospike-tools`, which isn't part of the server RPM) — hence the plain-TCP healthcheck instead of
`asinfo -v status`.

## Verified

Tested this way against multiple real AEL-capable server builds, each confirmed clean (`rpm -i`
needs `shadow-utils` for the RPM's preinstall scriptlet to create the `aerospike` user — not a
declared dependency, just an unstated assumption — but nothing else beyond that), starting cleanly
against the real `aerospike.conf` and forming a single-node cluster every time.

The real findings from that testing — two version-independent AEL syntax bugs (no `..` operator
exists in AEL; a bare CDT root bin needs an explicit `:MAP` annotation), the SDK's `stage` branch
moving its own version gate, the price/guests-and-rooms fix, and the force-recreate gotcha mentioned
above — are documented once, in `../backend/README.md`'s "AEL verified against a real 8.1.3+
server", rather than duplicated here. Re-verified against a later server build too, with identical
results across the complete `/search` filter matrix (locality, bed, price, guests/rooms, geo, and
all combined) — the fixes hold generally, not just against whichever specific build they were first
found on. `GET /suggest`'s `lowercase()`/`uppercase()`/`length()` still aren't implemented
server-side as of the 8.2.0.0 GA release (`trim()` works) — confirmed still falls through cleanly to
a working classic-Exp tier instead of failing outright; see backend/README.md for how that's handled.
