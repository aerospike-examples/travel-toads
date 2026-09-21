import { useState } from "react";
import { useDemoMode } from "../context/DemoModeContext";
import type { DemoMode } from "../context/DemoModeContext";
import { resetDemo } from "../api/client";
import "./PresenterPanel.css";
import "./DemoPanel.css";

const MODES: { value: DemoMode; label: string; hint: string }[] = [
  { value: "off", label: "Customer", hint: "Clean booking site — no technical UI" },
  { value: "demo", label: "Demo", hint: "Shows the Aerospike query behind each screen" },
  { value: "engineering", label: "Eng", hint: "Adds code, query hints, and notes" },
];

/**
 * Unobtrusive slide-out drawer for the person running the demo, not the
 * "customer" the site is dressed up for — so it stays tucked off-screen
 * (just a small tab peeking from the right edge) until opened, rather than
 * living in the always-visible header. Holds the Customer/Demo/Engineering
 * switch, Reset Demo, and — below that — the current search query and the
 * most recent booking, so whoever's presenting can always see them
 * regardless of the mode the main site is showing right now (unlike the
 * inline DemoPanel next to the results, which hides itself in Customer
 * mode). Reads {@link DemoModeContext}'s `currentQuery` (kept live by
 * SearchResultsPage on every search) and `lastBooking` (set by
 * HotelDetailPage right after a booking confirms) — the latter exists so a
 * presenter can jump straight to Voyager afterward: there's no index to look
 * a reservation up by id alone (see BookingService), so the hotelId here is
 * what actually lets you pull the record up by key.
 */
export function PresenterPanel() {
  const { mode, setMode, currentQuery, lastBooking } = useDemoMode();
  const [open, setOpen] = useState(false);
  const [resetting, setResetting] = useState(false);

  async function handleReset() {
    const confirmed = window.confirm("Reset the demo to its starting state?");
    if (!confirmed) return;
    setResetting(true);
    try {
      await resetDemo();
    } finally {
      window.location.reload();
    }
  }

  return (
    <div className={`presenter-panel-wrap${open ? " open" : ""}`}>
      <button
        type="button"
        className="presenter-panel-tab"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-label={open ? "Close presenter controls" : "Open presenter controls"}
        title="Presenter controls"
      >
        {open ? "›" : "‹"}
      </button>

      <aside className="presenter-panel card-surface">
        <div className="presenter-panel-header">
          <h3>Presenter controls</h3>
        </div>

        <div className="presenter-panel-section">
          <span className="field-label">Detail level</span>
          <div
            className="mode-switch"
            role="radiogroup"
            aria-label="Detail level"
            title={MODES.find((m) => m.value === mode)?.hint}
          >
            {MODES.map((m) => (
              <button
                key={m.value}
                type="button"
                role="radio"
                aria-checked={mode === m.value}
                className={`mode-switch-option${mode === m.value ? " active" : ""}`}
                onClick={() => setMode(m.value)}
                title={m.hint}
              >
                {m.label}
              </button>
            ))}
          </div>

          <button
            type="button"
            className="btn btn-ghost btn-sm reset-btn"
            onClick={handleReset}
            disabled={resetting}
          >
            ↻ Reset Demo
          </button>
        </div>

        <div className="presenter-panel-section presenter-panel-query">
          <span className="field-label">Current query</span>
          {currentQuery ? (
            <>
              <div className="demo-panel-meta">
                {currentQuery.queryMechanism && (
                  <span className="demo-panel-pill">
                    <span className="demo-panel-pill-label">Mechanism</span>
                    {currentQuery.queryMechanism}
                  </span>
                )}
                <span className="demo-panel-pill">
                  <span className="demo-panel-pill-label">Index</span>
                  {currentQuery.index ?? "none (full scan)"}
                </span>
              </div>
              {currentQuery.aelTemplate && (
                <pre className="demo-panel-code">
                  <code>{currentQuery.aelTemplate}</code>
                </pre>
              )}
              {currentQuery.notes && <p className="presenter-query-notes">{currentQuery.notes}</p>}
            </>
          ) : (
            <p className="presenter-query-empty">No active search yet — pick a destination to see its query here.</p>
          )}
        </div>

        <div className="presenter-panel-section presenter-panel-query">
          <span className="field-label">Last booking</span>
          {lastBooking ? (
            <>
              <div className="demo-panel-meta">
                <span className="demo-panel-pill">
                  <span className="demo-panel-pill-label">Hotel ID</span>
                  {lastBooking.hotelId}
                </span>
                <span className="demo-panel-pill">
                  <span className="demo-panel-pill-label">Room ID</span>
                  {lastBooking.roomId}
                </span>
                <span className="demo-panel-pill">
                  <span className="demo-panel-pill-label">
                    {lastBooking.rateSegments.length === 1 ? "Rate" : "Rates"}
                  </span>
                  {lastBooking.rateSegments.map((s) => `rates[${s.index}] (${s.rateId})`).join(", ")}
                </span>
                <span className="demo-panel-pill">
                  <span className="demo-panel-pill-label">Reservation</span>
                  {lastBooking.reservationId}
                </span>
              </div>
              <p className="presenter-query-notes">
                Open this hotel record by key in Voyager, then expand straight to rooms →{" "}
                {lastBooking.roomId} → rates →{" "}
                {lastBooking.rateSegments
                  .map((s) => `list index ${s.index} (rateId "${s.rateId}")`)
                  .join(" and ")}
                {lastBooking.rateSegments.length > 1 ? " — this stay spanned two rate periods" : ""}{" "}
                → booked. "rates" is a list, not a map, so Voyager shows it by that numeric
                position, not by rateId — no need to open each entry and check; there's no index to
                search by reservation ID alone either.
              </p>
            </>
          ) : (
            <p className="presenter-query-empty">No booking confirmed yet this session.</p>
          )}
        </div>
      </aside>
    </div>
  );
}
