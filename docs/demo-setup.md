# TravelToads demo — presenter setup guide

Everything to do **before** anyone's watching. If you're presenting this and you're not the person
who's been building it, this is your whole runbook — nothing else to read first.

## What you need

- **Docker Desktop**, installed and running. (Colima works too — see `README.md`'s note on
  alternatives — but Docker Desktop is the tested path.)
- **This repo, cloned.** Aerospike 8.2 is GA, so the tracked `docker-compose.yml` default already
  points at a public server image with AEL built in — no private build, no zip, nothing to request
  from anyone. (If you specifically need to test against a different build, see `local/README.md`
  — most presenters will never need this.)
- About 5 minutes before your slot.

## One-time setup

```bash
git clone https://github.com/aerospike-examples/travel-toads
cd travel-toads
./demo start
```

That's the whole thing. `./demo start` builds every image (a couple of minutes the first time,
seconds after that) and brings up the database, backend, and frontend together. You'll see a
`Demo ready!` banner with the URL to open when it's done.

If it fails partway through with a Docker registry timeout (`DeadlineExceeded` or similar), that's
a transient network hiccup, not a real problem — just run `./demo start` again.

If it fails with something about `docker compose` not being found or unrecognized flags, see
`README.md`'s Docker Compose troubleshooting section — this happens specifically on Homebrew-only
Docker installs (no Docker Desktop) and has a two-command fix.

## Quick confidence check: confirm AEL is really live

Should say `true` out of the box now that Aerospike 8.2 GA is the tracked default — worth a
10-second check before you're in front of anyone, not because it's expected to fail:

```bash
docker compose logs backend | grep "Connected to Aerospike"
```

You want to see `AEL supported=true`. If it says `false` — e.g. you've pointed `local/` at an older
build — the demo still runs (it falls back to an equivalent non-AEL query path automatically) but
you won't get the "watch the real AEL query" beat; see `backend/README.md` if this happens and you
weren't expecting it.

Then open **<http://localhost:8080>**, run any search, open the presenter panel (the small `<` tab on
the right edge of the results page), and confirm you see real AEL text and an index name like
`hotel-loc-idx` or `hotel-locality-idx` — not a "results unavailable" banner.

## Right before you go on

1. **Reset to a clean slate** — removes any bookings from your own rehearsal runs, so you're not
   explaining someone else's test data:
   ```bash
   ./demo reset
   ```
2. **Open <http://localhost:8080>** in the browser you'll actually present from.
3. **Decide up front whether you're doing the Voyager beat.** If yes: open Voyager, connect to the
   demo cluster, and make sure **"Use services alternate" is enabled** on that connection — without
   it, Voyager can't reach the server from the host at all (see `aerospike/aerospike.conf`'s own
   header comment on this). See `docs/demo-script.md`'s Voyager section for exactly what to expect
   there and how to run it — it needs a small, deliberate adjustment, not a straight copy-paste.
4. Have `docs/demo-script.md` open on a second screen or printed — it's written to be followed
   live, starting from exactly this point (site open, freshly reset).

## Known, expected limitations — so nothing on stage surprises you

- **Hotel-name search** (typing a name in the destination box) works and returns real results, but
  runs via a fallback path today, not the newest AEL string functions — those aren't implemented on
  this server build yet. If you're in Eng detail mode and someone notices the query mechanism says
  "Classic Exp filter" instead of AEL for that one search, that's expected and explained in
  `backend/README.md`. Don't be thrown by it.
- **Voyager can't run this demo's more interesting queries yet.** Its own query editor doesn't
  support the wildcard/Path-Expression syntax this whole demo leans on — only flat comparisons like
  `$.rating >= 80`. This is a real, current limitation of that tool, not something broken here. The
  demo script's Voyager section has the exact, tested way to handle this gracefully if it comes up.

## Command reference

| Command         | What it does                                                                         |
| --------------- | ------------------------------------------------------------------------------------ |
| `./demo start`  | Build and start everything. Safe to re-run any time.                                 |
| `./demo reset`  | Undo bookings, restore the seeded dataset — a few seconds, no rebuild.               |
| `./demo seed`   | Regenerate the dataset from scratch (only needed if you changed generator settings). |
| `./demo status` | Container and backend health at a glance.                                            |
| `./demo logs`   | Tail every service's logs (`./demo logs backend` for just one).                      |
| `./demo stop`   | Shut everything down. Data persists for next time.                                   |

## If something breaks during setup and you're stuck

1. `./demo logs backend` — most issues show up here first.
2. `docker compose ps` — confirm all three containers say `healthy`/`Up`.
3. If you changed `local/server.rpm` on an already-running stack rather than starting fresh, run
   `./demo start` again (not just `docker compose up`) — it force-recreates everything specifically
   to avoid a known stale-connection issue when swapping server builds.
4. Worst case: `./demo stop`, then `./demo start` again from a clean state.
