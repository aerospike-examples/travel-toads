import { Link, useLocation, useNavigate } from "react-router-dom";
import type { DemoBlock } from "../api/types";
import { DemoPanel } from "../components/DemoPanel";
import { BED_TYPE_LABEL, VIEW_LABEL } from "../lib/constants";
import { formatIsoDatePretty, formatMoney, nightsBetween } from "../lib/format";
import type { RestorableSearchState } from "../lib/searchState";
import "./BookingConfirmationPage.css";

interface ConfirmationState {
	reservationId: string;
	demo: DemoBlock | null;
	hotelName: string;
	roomBed: string;
	roomView: string;
	checkIn: string;
	checkOut: string;
	/** The backend's own total for this booking — see BookingResponse#totalPrice. */
	totalPrice: number;
	guestName: string;
	/** Carried from the search results page through the hotel detail page (see
	 * lib/searchState.ts) so "Back to results" below returns to the same search instead of a
	 * blank one. Undefined if the flow was somehow entered without it - the Link below falls back
	 * to a plain "/" in that case. */
	restoreSearch?: RestorableSearchState;
}

function isConfirmationState(v: unknown): v is ConfirmationState {
	return !!v && typeof v === "object" && "reservationId" in v;
}

export function BookingConfirmationPage() {
	const location = useLocation();
	const navigate = useNavigate();
	const state = location.state;

	if (!isConfirmationState(state)) {
		return (
			<div className="container confirmation-page">
				<div className="confirmation-card card-surface confirmation-empty">
					<h1>No booking to show</h1>
					<p>
						Reload lost this confirmation's details — search results and
						property pages can be revisited any time.
					</p>
					<Link to="/" className="btn btn-primary">
						Back to search
					</Link>
				</div>
			</div>
		);
	}

	const nights = nightsBetween(state.checkIn, state.checkOut);
	const total = state.totalPrice;

	return (
		<div className="container confirmation-page">
			<div className="confirmation-card card-surface">
				<div className="confirmation-check">✓</div>
				<h1>Booking confirmed</h1>
				<p className="confirmation-sub">
					A confirmation has been sent for {state.guestName}.
				</p>

				<dl className="confirmation-details">
					<div>
						<dt>Reservation ID</dt>
						<dd>{state.reservationId}</dd>
					</div>
					<div>
						<dt>Hotel</dt>
						<dd>{state.hotelName}</dd>
					</div>
					<div>
						<dt>Room</dt>
						<dd>
							{BED_TYPE_LABEL[state.roomBed] ?? state.roomBed} bed,{" "}
							{VIEW_LABEL[state.roomView] ?? state.roomView} view
						</dd>
					</div>
					<div>
						<dt>Dates</dt>
						<dd>
							{formatIsoDatePretty(state.checkIn)} –{" "}
							{formatIsoDatePretty(state.checkOut)} ({nights}{" "}
							{nights === 1 ? "night" : "nights"})
						</dd>
					</div>
					<div>
						<dt>Total</dt>
						<dd>{formatMoney(total)}</dd>
					</div>
				</dl>

				<DemoPanel title="Booking" demo={state.demo} />

				<div className="confirmation-actions">
					<button
						type="button"
						className="btn btn-primary"
						onClick={() => navigate("/")}
					>
						Book another
					</button>
					<Link
						to="/"
						state={{ restoreSearch: state.restoreSearch }}
						className="btn btn-secondary"
					>
						Back to results
					</Link>
					{/* Inert, like Header's Support/Chat/Sign in — real-travel-site chrome with no
              destination yet, not a dead link. Payment is already settled by this point in the
              flow (the reservation above is confirmed); this button doesn't do anything. */}
					<button type="button" className="btn btn-secondary">
						Payment
					</button>
				</div>
			</div>
		</div>
	);
}
