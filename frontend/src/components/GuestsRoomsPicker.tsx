import { useEffect, useRef, useState } from "react";
import "./GuestsRoomsPicker.css";

export interface GuestsRooms {
  adults: number;
  children: number;
  rooms: number;
}

export const DEFAULT_GUESTS_ROOMS: GuestsRooms = { adults: 2, children: 0, rooms: 1 };

const BOUNDS: Record<keyof GuestsRooms, { min: number; max: number }> = {
  adults: { min: 1, max: 8 },
  children: { min: 0, max: 6 },
  rooms: { min: 1, max: 8 },
};

interface GuestsRoomsPickerProps {
  value: GuestsRooms;
  onChange: (value: GuestsRooms) => void;
}

/**
 * Replaces the old hotel-name search slot in the search bar. There's no guest-count field in
 * the *documented* POST /search contract, but the backend does now act on `adults`/`children`/
 * `rooms`: it filters to hotels with at least `rooms` rooms total, and at least one room big
 * enough for an even split of the party across them (see backend's ClassicFilters#roomCountAtLeast
 * / #hasRoomForOccupancy). That's a deliberate simplification, not true bin-packing across rooms —
 * see that method's own Javadoc for exactly what it does and doesn't guarantee.
 */
export function GuestsRoomsPicker({ value, onChange }: GuestsRoomsPickerProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  function step(key: keyof GuestsRooms, delta: number) {
    const bounds = BOUNDS[key];
    const next = Math.min(bounds.max, Math.max(bounds.min, value[key] + delta));
    onChange({ ...value, [key]: next });
  }

  return (
    <div className="guests-picker" ref={containerRef}>
      <label className="field-label" htmlFor="guests-picker-button">
        Guests and rooms
      </label>
      <button
        id="guests-picker-button"
        type="button"
        className="text-input guests-picker-button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        {summarize(value)}
      </button>

      {open && (
        <div className="guests-picker-popover">
          <Stepper
            label="Adults"
            value={value.adults}
            bounds={BOUNDS.adults}
            onDecrement={() => step("adults", -1)}
            onIncrement={() => step("adults", 1)}
          />
          <Stepper
            label="Children"
            value={value.children}
            bounds={BOUNDS.children}
            onDecrement={() => step("children", -1)}
            onIncrement={() => step("children", 1)}
          />
          <Stepper
            label="Rooms"
            value={value.rooms}
            bounds={BOUNDS.rooms}
            onDecrement={() => step("rooms", -1)}
            onIncrement={() => step("rooms", 1)}
          />
          <button type="button" className="btn btn-primary btn-sm guests-picker-done" onClick={() => setOpen(false)}>
            Done
          </button>
        </div>
      )}
    </div>
  );
}

function summarize(v: GuestsRooms): string {
  const adultsLabel = `${v.adults} adult${v.adults === 1 ? "" : "s"}`;
  const childrenLabel = v.children > 0 ? `, ${v.children} child${v.children === 1 ? "" : "ren"}` : "";
  const roomsLabel = `${v.rooms} room${v.rooms === 1 ? "" : "s"}`;
  return `${adultsLabel}${childrenLabel} · ${roomsLabel}`;
}

function Stepper({
  label,
  value,
  bounds,
  onDecrement,
  onIncrement,
}: {
  label: string;
  value: number;
  bounds: { min: number; max: number };
  onDecrement: () => void;
  onIncrement: () => void;
}) {
  return (
    <div className="guests-stepper">
      <span className="guests-stepper-label">{label}</span>
      <div className="guests-stepper-controls">
        <button
          type="button"
          className="guests-stepper-btn"
          onClick={onDecrement}
          disabled={value <= bounds.min}
          aria-label={`Decrease ${label.toLowerCase()}`}
        >
          −
        </button>
        <span className="guests-stepper-value">{value}</span>
        <button
          type="button"
          className="guests-stepper-btn"
          onClick={onIncrement}
          disabled={value >= bounds.max}
          aria-label={`Increase ${label.toLowerCase()}`}
        >
          +
        </button>
      </div>
    </div>
  );
}
