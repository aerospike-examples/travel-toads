# TravelToads demo — script

Starts here: **http://localhost:8080 is already open, the demo's been freshly reset**
(`./demo reset`). If that's not true yet, see `docs/demo-setup.md` first.

Timings are rough guides for a ~10-minute walkthrough, not a strict script — the "say" lines are
starting points, not something to read verbatim. Every beat below has actually been run and
verified this way; nothing here is aspirational.

---

## 1. Set the scene (~30s)

You're looking at the homepage — search bar, hero banner, "TravelToads."

**Say:** "This is a real booking-site UI — search, filters, results, a booking flow. Nothing about
the front end is the point. What matters is what's answering every one of these screens underneath,
and I can show you that live as we go."

---

## 2. Search a neighborhood — the locality index (~1 min)

1. Click the destination box, type `Austin`, press **Enter** or select **"Austin — All
   neighborhoods"**. You'll get a large, unfiltered-looking result count (630 in this dataset).
2. Click the **"Downtown"** neighborhood chip. The count narrows sharply (132 in this dataset).
3. Open the **presenter panel** — the small `<` tab on the right edge of the page.
4. Point at the query text and the **INDEX** label (`hotel-locality-idx`).

**Say:** "That's not a mocked number — that's a real query running right now, and it just told you
which secondary index the database picked to answer it. Nothing here is pre-recorded."

---

## 3. Prove it's not one index — geo radius search (~1–2 min)

1. Clear the destination box.
2. Use the **"Within [ ] miles of [Airport ▾]"** control — type `5`, press Tab. It auto-fills the
   destination to the airport landmark and searches immediately.
3. Note the result count (a tight cluster in central Austin).
4. Change the number to `25`, then `35`. Watch the count grow each time.
5. Open the presenter panel again — same fluent code shape as step 2, but the **INDEX** now says
   `hotel-loc-idx` — a completely different index.

**Say:** "Same code path, same shape of query — the server's planner chose a different index purely
because the predicate changed, geo instead of a string match. At 25 and 35 miles you're pulling in
Round Rock and San Marcos — this isn't one city with padding, it's three real metro areas, and
widening the radius is what proves the geo index is doing real work, not just filtering a list."

---

## 4. Filter sidebar — watch the query grow (~1–2 min)

With a search active (Downtown Austin works well):

1. Click a **guest rating** chip (e.g. "8.0+").
2. Click a **price** chip (e.g. "Under $100").
3. Pick a **bed type**.
4. Adjust **guests and rooms** upward (e.g. 4 adults).

After each click, glance at the presenter panel — the AEL string visibly grows with an extra clause,
and the result count updates to match.

**Say:** "Every one of those chips is a real predicate getting added to the same query, live. Price
and guest count in particular used to be UI-only — they're now both genuinely evaluated by the
database, not just decorative filters that quietly did nothing." *(Only mention the "used to be"
part to a technical/internal audience — it's a real fix, not customer-facing framing.)*

---

## 5. Hotel name search (~30s)

1. Click the destination box, type part of a hotel name — `lake` works well in this dataset.
2. Real suggestions appear (Lakeside Court, Lakeside Suites, etc.).

**Say:** "Name search, case-insensitive, live." If you're in **Eng** detail mode and someone notices
this one's `queryMechanism` says "Classic Exp filter" instead of AEL: that's expected — this
particular string function isn't implemented on this server build yet, so it gracefully falls back
to an equivalent that still runs server-side. Worth knowing, not worth dwelling on unless asked.

---

## 6. Property detail — path expressions (~1 min)

1. Click into any hotel card from a results page.
2. Point at the room list — each room shows its own rates for the dates you searched.

**Say:** "This is Path Expressions doing the retrieval — reaching directly into the nested rooms and
rates inside one record, rather than pulling the whole document back and filtering it in application
code."

---

## 7. Book a room (~1 min)

1. Pick an available room, go through the booking flow to confirmation.

**Say:** "That's a single atomic operation on one record — removing the booked night from
availability and appending the reservation — so there's no window where two people could book the
same room on the same night."

---

## 8. Close the loop in Voyager (~1–2 min, optional but strong)

Requires Voyager open, connected, **with "Use services alternate" enabled** (see
`docs/demo-setup.md`).

1. Copy the `hotelId` from the booking confirmation (or the URL of the hotel detail page).
2. In Voyager, look up that record by key.
3. Show the nested `available`/`booked` update from step 7 sitting right there in the raw record.

**Say:** "Same data, real tool, no separate 'admin view' — what you just watched happen in the app
is sitting in the actual record."

---

## 9. (Optional, technical audiences) Real AEL running in a second tool

This needs a small, deliberate adjustment — say so rather than pretending it's a plain paste:

1. Go back to a search from step 2 or 4, copy its AEL string from the presenter panel.
2. **By hand, delete everything from `and $.rooms:MAP...` onward** — keep only the leading clause,
   e.g. `$.locality == 'austin-downtown'`.
3. Paste that trimmed piece into Voyager's expression editor and run it. It matches.

**Say:** "Voyager's own query editor runs real AEL — here's the exact fragment of what you just saw
running, executing there too. The wildcard syntax that reaches inside nested rooms and rates is
brand new, and Voyager's tooling hasn't caught up to it yet — the flat filters already work fine
today, same as the server and the SDK, which have supported all of this from day one." This is a
credible, honest answer if asked directly — not a workaround to hide.

---

## 10. Wrap-up (~30s)

1. Flip **Presenter Controls** to **Eng** mode for a moment.
2. Recap: "Three things made everything you just saw possible — a fluent Java SDK with no
   boilerplate connection/policy setup, AEL giving you a readable query string instead of nested
   method calls, and Path Expressions letting that query reach directly into nested data instead of
   pulling whole records back to filter client-side."

---

## Resetting for the next run

```bash
./demo reset
```

A few seconds, no rebuild — clears the booking you just made and restores the seeded dataset
exactly as it started.
