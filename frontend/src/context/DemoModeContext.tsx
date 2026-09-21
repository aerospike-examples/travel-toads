import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { DemoBlock, RateSegment } from "../api/types";

export type DemoMode = "off" | "demo" | "engineering";

export interface LastBooking {
  hotelId: string;
  /** The exact room this reservation is nested under — a hotel can have dozens of rooms, so
   * without this the presenter still has to expand and scan every one of them in Voyager to find
   * which `rooms.{roomId}` holds the booked entry. With it, that's a single direct expand. */
  roomId: string;
  /** Every rate segment the reservation actually landed on (see BookingResponse#rateSegments) —
   * almost always one, occasionally two for a stay spanning a rate-period boundary. `rates` is a
   * CDT *list*, not a map, so each segment's numeric `index` (not just its `rateId`) is what
   * actually tells you which entry to expand in Voyager, which displays lists by position. */
  rateSegments: RateSegment[];
  reservationId: string;
}

const STORAGE_KEY = "traveltoads.demoMode";

interface DemoModeContextValue {
  mode: DemoMode;
  setMode: (mode: DemoMode) => void;
  /** true whenever mode is "demo" or "engineering" — pass as the `demo` flag on API calls. */
  demoFlag: boolean;
  showDemoPanel: boolean;
  showEngineering: boolean;
  /**
   * The most recent search's demo block, independent of `mode` — the Presenter Panel shows this
   * (below Reset Demo) so whoever's running the demo can see the live query even in Customer mode,
   * where the inline DemoPanel next to the results is deliberately hidden. Pages that run a query
   * (currently just SearchResultsPage) call `setCurrentQuery` with whatever the backend returned,
   * on every request — including null on an empty/no-destination state or a failed request, so the
   * presenter panel doesn't keep showing a stale query.
   */
  currentQuery: DemoBlock | null;
  setCurrentQuery: (demo: DemoBlock | null) => void;
  /**
   * The most recently confirmed booking's hotelId + roomId + reservationId, independent of `mode`
   * — same always-visible treatment as `currentQuery`. There's no secondary index or query path to
   * look a reservation up by id alone (it's nested inside its hotel record's rooms/rates/booked
   * list, see BookingService); the intended way to inspect one in Voyager is a primary-key lookup
   * on the hotel record, then expanding straight to `rooms.{roomId}` — the roomId is what turns
   * that into a direct expand instead of scanning every room on a hotel that might have dozens.
   * HotelDetailPage's `onBooked` calls `setLastBooking` right after a successful booking; null
   * until the first one this session.
   */
  lastBooking: LastBooking | null;
  setLastBooking: (booking: LastBooking | null) => void;
}

const DemoModeContext = createContext<DemoModeContextValue | null>(null);

function readInitialMode(): DemoMode {
  if (typeof window === "undefined") return "off";
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === "off" || stored === "demo" || stored === "engineering") {
    return stored;
  }
  return "off";
}

export function DemoModeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<DemoMode>(readInitialMode);
  const [currentQuery, setCurrentQuery] = useState<DemoBlock | null>(null);
  const [lastBooking, setLastBooking] = useState<LastBooking | null>(null);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // localStorage unavailable (private mode, etc.) — mode just won't persist.
    }
  }, [mode]);

  const value = useMemo<DemoModeContextValue>(
    () => ({
      mode,
      setMode,
      demoFlag: mode !== "off",
      showDemoPanel: mode === "demo" || mode === "engineering",
      showEngineering: mode === "engineering",
      currentQuery,
      setCurrentQuery,
      lastBooking,
      setLastBooking,
    }),
    [mode, currentQuery, lastBooking],
  );

  return (
    <DemoModeContext.Provider value={value}>{children}</DemoModeContext.Provider>
  );
}

export function useDemoMode(): DemoModeContextValue {
  const ctx = useContext(DemoModeContext);
  if (!ctx) {
    throw new Error("useDemoMode must be used within a DemoModeProvider");
  }
  return ctx;
}
