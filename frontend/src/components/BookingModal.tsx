import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { createBooking } from "../api/client";
import type { BookingResponse } from "../api/types";
import { useDemoMode } from "../context/DemoModeContext";
import { BED_TYPE_LABEL, VIEW_LABEL } from "../lib/constants";
import { formatIsoDatePretty, formatMoney, nightsBetween } from "../lib/format";
import "./BookingModal.css";

export interface BookingContext {
  hotelId: string;
  hotelName: string;
  roomId: string;
  bed: string;
  view: string;
  /**
   * The room's total price for this exact stay (sum of each night's own rate-segment price - see
   * Room#totalPrice), not a per-night rate multiplied out here. A stay routinely spans more than
   * one rate segment, so there's no single per-night price to multiply.
   */
  totalPrice: number;
  checkIn: string;
  checkOut: string;
}

interface BookingModalProps {
  context: BookingContext;
  onClose: () => void;
  onBooked: (response: BookingResponse, context: BookingContext, guestName: string) => void;
  onConflict: () => void;
}

export function BookingModal({ context, onClose, onBooked, onConflict }: BookingModalProps) {
  const { demoFlag } = useDemoMode();

  // Lock background scroll while the modal is open — a `position: fixed`
  // overlay only covers the current viewport, not the full scrollable page.
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, []);

  const [guestName, setGuestName] = useState("");
  const [breakfast, setBreakfast] = useState(false);
  const [flex, setFlex] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const nights = nightsBetween(context.checkIn, context.checkOut);
  const total = context.totalPrice;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!guestName.trim()) {
      setError("Enter a guest name to continue.");
      return;
    }
    setSubmitting(true);
    setError(null);
    const res = await createBooking({
      hotelId: context.hotelId,
      roomId: context.roomId,
      checkIn: context.checkIn,
      checkOut: context.checkOut,
      guestName: guestName.trim(),
      breakfast,
      flex,
      demo: demoFlag,
    });
    setSubmitting(false);

    if (res.ok) {
      onBooked(res.data, context, guestName.trim());
    } else if (res.kind === "conflict") {
      onConflict();
    } else if (res.kind === "ael_unsupported") {
      setError(res.info.message);
    } else {
      setError(res.message);
    }
  }

  return (
    <div className="booking-modal-overlay" onClick={onClose}>
      <div
        className="booking-modal card-surface"
        role="dialog"
        aria-modal="true"
        aria-labelledby="booking-modal-title"
        onClick={(e) => e.stopPropagation()}
      >
        <button type="button" className="booking-modal-close" onClick={onClose} aria-label="Close">
          ×
        </button>

        <h3 id="booking-modal-title">Book this room</h3>
        <p className="booking-modal-summary">
          {context.hotelName} · {BED_TYPE_LABEL[context.bed] ?? context.bed} bed,{" "}
          {VIEW_LABEL[context.view] ?? context.view} view
          <br />
          {formatIsoDatePretty(context.checkIn)} – {formatIsoDatePretty(context.checkOut)} ({nights}{" "}
          {nights === 1 ? "night" : "nights"})
        </p>

        <form onSubmit={handleSubmit} className="booking-form">
          <div className="booking-form-field">
            <label className="field-label" htmlFor="guest-name">
              Guest name
            </label>
            <input
              id="guest-name"
              className="text-input"
              type="text"
              value={guestName}
              onChange={(e) => setGuestName(e.target.value)}
              placeholder="Full name"
              autoFocus
            />
          </div>

          <div className="booking-form-toggles">
            <label className="filter-checkbox">
              <input type="checkbox" checked={breakfast} onChange={(e) => setBreakfast(e.target.checked)} />
              Add breakfast
            </label>
            <label className="filter-checkbox">
              <input type="checkbox" checked={flex} onChange={(e) => setFlex(e.target.checked)} />
              Free cancellation (flex rate)
            </label>
          </div>

          {error && <div className="booking-form-error">{error}</div>}

          <div className="booking-form-total">
            <span>Total</span>
            <strong>{formatMoney(total)}</strong>
          </div>

          <button type="submit" className="btn btn-primary booking-form-submit" disabled={submitting}>
            {submitting ? "Booking…" : "Confirm booking"}
          </button>
        </form>
      </div>
    </div>
  );
}
