import { useRef, useState } from "react";
import type { FormEvent } from "react";
import type { Landmark } from "../api/types";
import { DestinationSearchBox } from "./DestinationSearchBox";
import type { DestinationSearchBoxHandle } from "./DestinationSearchBox";
import { DistanceFilter } from "./DistanceFilter";
import { GuestsRoomsPicker } from "./GuestsRoomsPicker";
import type { GuestsRooms } from "./GuestsRoomsPicker";
import "./SearchBar.css";

interface SearchBarProps {
	landmarks: Landmark[];
	landmarkId: string;
	locality: string;
	city: string;
	radiusMiles: number;
	checkIn: string;
	checkOut: string;
	guests: GuestsRooms;
	onLandmarkChange: (landmarkId: string) => void;
	onCityChange: (city: string) => void;
	onRadiusChange: (miles: number) => void;
	onCheckInChange: (iso: string) => void;
	onCheckOutChange: (iso: string) => void;
	onGuestsChange: (guests: GuestsRooms) => void;
}

export function SearchBar({
	landmarks,
	landmarkId,
	locality,
	city,
	radiusMiles,
	checkIn,
	checkOut,
	guests,
	onLandmarkChange,
	onCityChange,
	onRadiusChange,
	onCheckInChange,
	onCheckOutChange,
	onGuestsChange,
}: SearchBarProps) {
	const destinationRef = useRef<DestinationSearchBoxHandle>(null);
	const [submitHint, setSubmitHint] = useState<string | null>(null);

	// Search results already update live as soon as a destination is picked (from the dropdown, or
	// now) — this button/form exists for the conventional affordance (an obvious thing to click,
	// Enter-to-submit) rather than to trigger something that wasn't already going to happen. If
	// nothing resolves a destination and one isn't already active, that's the one case worth a
	// visible nudge instead of silently doing nothing — and the nudge itself has to distinguish "you
	// didn't type anything" from "you typed something real that just isn't in this demo's data"
	// (e.g. "san francisco" — this dataset only covers Austin/Round Rock/San Marcos), since "pick a
	// destination" reads as ignoring what was actually typed.
	function showHint(message: string) {
		setSubmitHint(message);
		window.setTimeout(() => setSubmitHint(null), 3000);
	}

	function handleSubmit(e: FormEvent) {
		e.preventDefault();
		const result = destinationRef.current?.trySubmit();
		if (
			!result ||
			result.status === "selected" ||
			result.status === "city" ||
			result.status === "navigated"
		) {
			return;
		}
		if (result.status === "empty") {
			// Nothing new was typed. Only worth a nudge if there's no destination active at all;
			// otherwise this is just a no-op re-submit of whatever's already showing.
			if (!landmarkId && !locality && !city) {
				showHint("Pick a destination to search");
			}
			return;
		}
		// "no-match" and "ambiguous" describe *this* typed query, regardless of whatever destination
		// (if any) was already active before it was typed.
		showHint(
			result.status === "no-match"
				? `No destination found for "${result.query}"`
				: `Multiple destinations match "${result.query}" — pick one from the list below`,
		);
	}

	return (
		<form className="search-bar card-surface" onSubmit={handleSubmit}>
			<div className="search-bar-row">
				<div className="search-field search-field-landmark">
					<DestinationSearchBox
						ref={destinationRef}
						landmarks={landmarks}
						landmarkId={landmarkId}
						city={city}
						checkIn={checkIn}
						checkOut={checkOut}
						onLandmarkChange={onLandmarkChange}
						onCityChange={onCityChange}
					/>
					{submitHint && (
						<div className="destination-needs-selection-hint">{submitHint}</div>
					)}
					<DistanceFilter
						landmarks={landmarks}
						landmarkId={landmarkId}
						radiusMiles={radiusMiles}
						onLandmarkChange={onLandmarkChange}
						onRadiusChange={onRadiusChange}
					/>
				</div>

				<div className="search-field">
					<label className="field-label" htmlFor="checkin-input">
						Check in
					</label>
					<input
						id="checkin-input"
						className="text-input"
						type="date"
						value={checkIn}
						max={checkOut}
						onChange={(e) => onCheckInChange(e.target.value)}
					/>
				</div>

				<div className="search-field">
					<label className="field-label" htmlFor="checkout-input">
						Check out
					</label>
					<input
						id="checkout-input"
						className="text-input"
						type="date"
						value={checkOut}
						min={checkIn}
						onChange={(e) => onCheckOutChange(e.target.value)}
					/>
				</div>

				<div className="search-field search-field-guests">
					<GuestsRoomsPicker value={guests} onChange={onGuestsChange} />
				</div>

				<div className="search-field search-field-submit">
					<span className="field-label search-submit-spacer" aria-hidden="true">
						&nbsp;
					</span>
					<button type="submit" className="btn btn-primary search-submit-btn">
						Search
					</button>
				</div>
			</div>
		</form>
	);
}
