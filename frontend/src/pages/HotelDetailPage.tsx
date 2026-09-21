import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { getHotel } from "../api/client";
import type { AelUnsupportedError, HotelDetail, Room } from "../api/types";
import { useDemoMode } from "../context/DemoModeContext";
import { DemoPanel } from "../components/DemoPanel";
import { AelUnsupportedBanner } from "../components/AelUnsupportedBanner";
import { BookingModal } from "../components/BookingModal";
import type { BookingContext } from "../components/BookingModal";
import { AMENITY_OPTIONS, BED_TYPE_LABEL, PROPERTY_TYPES, ROOM_AMENITY_LABEL, VIEW_LABEL } from "../lib/constants";
import { hotelPhotoUrl } from "../lib/hotelPhoto";
import { roomPhotoUrl } from "../lib/roomPhoto";
import type { RestorableSearchState } from "../lib/searchState";
import {
  formatIsoDatePretty,
  formatMoney,
  formatYmdInt,
  nightsBetween,
  ratingLabel,
  formatRating,
  stars,
} from "../lib/format";
import { addDays, formatIsoDate } from "../lib/format";
import "./HotelDetailPage.css";

const AMENITY_LABEL: Record<string, string> = Object.fromEntries(
  AMENITY_OPTIONS.map((a) => [a.value, a.label]),
);
const PROPERTY_TYPE_LABEL: Record<string, string> = Object.fromEntries(
  PROPERTY_TYPES.map((p) => [p.value, p.label]),
);

export function HotelDetailPage() {
  const { hotelId = "" } = useParams();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { demoFlag, setLastBooking } = useDemoMode();

  const checkIn = searchParams.get("checkIn") || formatIsoDate(addDays(new Date(), 1));
  const checkOut = searchParams.get("checkOut") || formatIsoDate(addDays(new Date(), 4));

  // Handed in by HotelCard's Link (see lib/searchState.ts) — carried through unchanged so both
  // this page's own "back to results" link and the booking confirmation page's can return to the
  // exact search the user came from instead of a blank one.
  const routerState = location.state as { restoreSearch?: RestorableSearchState; photoUrl?: string } | null;
  const restoreSearch = routerState?.restoreSearch;
  // The exact photo HotelCard just showed for this hotel, so clicking through doesn't swap to a
  // different one — undefined on a direct/bookmarked visit, where hotelPhotoUrl's id-hash fallback
  // (below) takes over instead. See lib/hotelPhoto.ts for why these are two different functions.
  const incomingPhotoUrl = routerState?.photoUrl;

  const [hotel, setHotel] = useState<HotelDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [aelInfo, setAelInfo] = useState<AelUnsupportedError | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [bookingContext, setBookingContext] = useState<BookingContext | null>(null);
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setConflictMessage(null);
    getHotel(hotelId, checkIn, checkOut, demoFlag).then((res) => {
      if (cancelled) return;
      setLoading(false);
      if (res.ok) {
        setHotel(res.data);
        setAelInfo(null);
        setErrorMsg(null);
      } else if (res.kind === "ael_unsupported") {
        setAelInfo(res.info);
        setHotel(null);
      } else {
        setErrorMsg(res.message);
        setHotel(null);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [hotelId, checkIn, checkOut, demoFlag]);

  function refetch() {
    setLoading(true);
    getHotel(hotelId, checkIn, checkOut, demoFlag).then((res) => {
      setLoading(false);
      if (res.ok) setHotel(res.data);
    });
  }

  function handleConflict() {
    setBookingContext(null);
    setConflictMessage("Someone else just booked that night — try again.");
    refetch();
  }

  const nights = nightsBetween(checkIn, checkOut);

  return (
    <div className="detail-page">
      <div className="container detail-page-inner">
        <Link to="/" state={{ restoreSearch }} className="detail-back-link">
          ← Back to results
        </Link>

        {loading && <div className="detail-loading">Loading…</div>}

        {aelInfo && <AelUnsupportedBanner info={aelInfo} />}
        {errorMsg && <div className="results-error">{errorMsg}</div>}

        {hotel && (
          <>
            <img src={incomingPhotoUrl ?? hotelPhotoUrl(hotel.hotelId)} alt={hotel.name} className="detail-hero" />

            <header className="detail-header">
              <div className="detail-header-top">
                <h1>{hotel.name}</h1>
                <span className="detail-property-type">
                  {PROPERTY_TYPE_LABEL[hotel.propertyType] ?? hotel.propertyType}
                </span>
              </div>
              <div className="detail-header-meta">
                <span className="detail-stars">{stars(hotel.stars)}</span>
                <span className="detail-rating-badge">{formatRating(hotel.rating)}</span>
                <span className="detail-rating-text">
                  {ratingLabel(hotel.rating)} · {hotel.reviewCount.toLocaleString()} reviews
                </span>
                <span className="detail-location">
                  {hotel.neighborhood}, {hotel.city}
                </span>
              </div>
              {hotel.amenities.length > 0 && (
                <div className="detail-amenities">
                  {hotel.amenities.map((a) => (
                    <span key={a} className="amenity-badge">
                      {AMENITY_LABEL[a] ?? a}
                    </span>
                  ))}
                </div>
              )}
              <p className="detail-dates">
                {formatIsoDatePretty(checkIn)} – {formatIsoDatePretty(checkOut)} · {nights}{" "}
                {nights === 1 ? "night" : "nights"}
              </p>
            </header>

            <DemoPanel title="Rooms & rates for these dates" demo={hotel.demo} />

            {conflictMessage && (
              <div className="conflict-banner">
                <strong>Not so fast —</strong> {conflictMessage}
              </div>
            )}

            <section className="room-list">
              <h2 className="room-list-title">Rooms</h2>
              {Object.keys(hotel.rooms).length === 0 && (
                <p className="results-empty-hint">No rooms match this date range.</p>
              )}
              {Object.entries(hotel.rooms).map(([roomId, room]) => (
                <RoomCard
                  key={roomId}
                  roomId={roomId}
                  room={room}
                  nights={nights}
                  onBook={(totalPrice) =>
                    setBookingContext({
                      hotelId: hotel.hotelId,
                      hotelName: hotel.name,
                      roomId,
                      bed: room.bed,
                      view: room.view,
                      totalPrice,
                      checkIn,
                      checkOut,
                    })
                  }
                />
              ))}
            </section>

            <p className="voyager-hint">
              💡 Inspect this record's raw structure in Aerospike Voyager
            </p>
          </>
        )}
      </div>

      {bookingContext && (
        <BookingModal
          context={bookingContext}
          onClose={() => setBookingContext(null)}
          onConflict={handleConflict}
          onBooked={(response, ctx, guestName) => {
            setBookingContext(null);
            setLastBooking({
              hotelId: ctx.hotelId,
              roomId: ctx.roomId,
              rateSegments: response.rateSegments,
              reservationId: response.reservationId,
            });
            navigate("/confirmation", {
              state: {
                reservationId: response.reservationId,
                demo: response.demo,
                hotelName: ctx.hotelName,
                roomBed: ctx.bed,
                roomView: ctx.view,
                checkIn: ctx.checkIn,
                checkOut: ctx.checkOut,
                // The backend's own total for this booking, not the modal's pre-submit estimate —
                // both should already agree, but this is the value actually charged/recorded.
                totalPrice: response.totalPrice,
                guestName,
                restoreSearch,
              },
            });
          }}
        />
      )}
    </div>
  );
}

interface RoomCardProps {
  roomId: string;
  room: Room;
  nights: number;
  onBook: (totalPrice: number) => void;
}

// A rate period is a contiguous weekday-only or weekend-only band (see datagen's
// RateGenerator), so a multi-night stay routinely needs nights from more than one - the room is
// bookable (or not) as a whole for these dates, not one rate row at a time. `room.available` /
// `room.totalPrice` already reflect that (computed server-side, per night, across every segment -
// see HotelRecordUtil#projectRooms's doc comment for why a per-row check almost never worked). The
// rate rows below stay purely informational: each period's own nightly price, for whichever
// segments overlap these dates.
function RoomCard({ room, nights, onBook }: RoomCardProps) {
  const photoUrl = roomPhotoUrl(room.bed);
  return (
    <div className="room-card card-surface">
      {photoUrl && (
        <img
          src={photoUrl}
          alt={`${BED_TYPE_LABEL[room.bed] ?? room.bed} bed room`}
          className="room-card-photo"
        />
      )}
      <div className="room-card-info">
        <h3>
          {BED_TYPE_LABEL[room.bed] ?? room.bed} bed · {VIEW_LABEL[room.view] ?? room.view} view
        </h3>
        <div className="room-card-specs">
          <span>{room.sqm} m²</span>
          <span>Sleeps {room.maxOccupancy}</span>
          <span>{room.nonSmoking ? "Non-smoking" : "Smoking allowed"}</span>
        </div>
        {room.amenities.length > 0 && (
          <div className="detail-amenities room-amenities">
            {room.amenities.map((a) => (
              <span key={a} className="amenity-badge amenity-badge-sm">
                {ROOM_AMENITY_LABEL[a] ?? a}
              </span>
            ))}
          </div>
        )}
      </div>

      <div className="room-card-rates">
        {room.rates.length === 0 && (
          <span className="results-empty-hint">No rate available for these dates.</span>
        )}
        {room.rates.map((rate) => (
          <div key={rate.rateId} className="rate-row">
            <div className="rate-row-info">
              <span className="rate-row-period">
                {formatYmdInt(rate.from)} – {formatYmdInt(rate.to)} rate
              </span>
              <span className="rate-row-price">{formatMoney(rate.price)} / night</span>
            </div>
          </div>
        ))}

        <div className="room-book-row">
          {room.available && room.totalPrice != null ? (
            <>
              <span className="room-book-total">
                {formatMoney(room.totalPrice)} total · {nights} {nights === 1 ? "night" : "nights"}
              </span>
              <button type="button" className="btn btn-primary btn-sm" onClick={() => onBook(room.totalPrice!)}>
                Book
              </button>
            </>
          ) : (
            <span className="rate-row-unavailable">Not available for these dates</span>
          )}
        </div>
      </div>
    </div>
  );
}
